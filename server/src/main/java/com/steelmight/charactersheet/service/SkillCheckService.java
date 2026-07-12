package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.Card;
import com.steelmight.charactersheet.dto.CardType;
import com.steelmight.charactersheet.dto.PassedCard;
import com.steelmight.charactersheet.dto.RedrawBonus;
import com.steelmight.charactersheet.dto.SkillCheckAccepted;
import com.steelmight.charactersheet.dto.SkillCheckResult;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side port of the Deck of Fates draw for skill checks.
 *
 * Deck of Fates semantics (card-deck-modifier/src/components/CardDraw.jsx):
 * - The d10 is rolled ONCE per check and stays fixed across redraws.
 * - A redraw forfeits the current card and draws the next one from the remaining deck (no
 *   reshuffle); proficiency in the check grants proficiency-bonus-many redraws, else none.
 * - A CLASS card restricted to a different check auto-passes (wrong-check skip, costs nothing).
 * - A CLASS card with a redrawModifier is passed, its bonus accumulates for the whole check,
 *   and the draw continues (costs nothing). Wrong-check takes priority over the bonus.
 * - Either auto-pass resolves normally instead when it is the last card of the deck.
 * - Accepting the final card applies its removal mechanic: consume (out until rest) or burn.
 *
 * The in-flight check lives in memory per character (trusted table; a restart aborts open checks,
 * and a new draw replaces any previous session without applying removal — matching DoF's reset).
 */
@Service
@Transactional(readOnly = true)
public class SkillCheckService {

    private final CharacterRepository repo;
    private final GameDataProvider gameData;
    private final RandomSource random;
    private final DeckTemplateService deckTemplates;
    private final StatDerivationEngine engine;

    private final Map<String, DrawSession> sessions = new ConcurrentHashMap<>();

    public SkillCheckService(CharacterRepository repo, GameDataProvider gameData, RandomSource random,
                             DeckTemplateService deckTemplates, StatDerivationEngine engine) {
        this.repo = repo;
        this.gameData = gameData;
        this.random = random;
        this.deckTemplates = deckTemplates;
        this.engine = engine;
    }

    /** State of one in-flight skill check. */
    private static final class DrawSession {
        final String skillId;
        final AbilityScore ability;
        final int d10;
        final boolean proficient;
        final Deque<Card> remaining;
        final List<RedrawBonus> bonuses = new ArrayList<>();
        Card current;
        int redrawsUsed;
        int redrawsRemaining;

        DrawSession(String skillId, AbilityScore ability, int d10, boolean proficient,
                    Deque<Card> remaining, int redrawsRemaining) {
            this.skillId = skillId;
            this.ability = ability;
            this.d10 = d10;
            this.proficient = proficient;
            this.remaining = remaining;
            this.redrawsRemaining = redrawsRemaining;
        }
    }

    public SkillCheckResult draw(String playerId, String skillId) {
        GameCharacter c = getCharacter(playerId);
        AbilityScore ability = abilityForSkill(skillId);

        Deque<Card> deck = new ArrayDeque<>(shuffle(deckTemplates.effectiveDeck(c)));
        int d10 = random.nextInt(10) + 1;
        boolean proficient = c.getProficiencies().contains(skillId);
        int redraws = proficient ? engine.computeProficiencyBonus(c) : 0;

        DrawSession session = new DrawSession(skillId, ability, d10, proficient, deck, redraws);
        sessions.put(playerId, session);
        List<PassedCard> passed = advanceToFinalCard(session);
        return resolve(session, passed, c);
    }

    /** Deck of Fates gamble: forfeit the current card, draw the next; the d10 stays. */
    public SkillCheckResult redraw(String playerId) {
        DrawSession session = sessions.get(playerId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no skill check in progress — draw first");
        }
        if (session.redrawsRemaining <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no redraws remaining for this check");
        }
        if (session.remaining.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the deck is exhausted");
        }
        GameCharacter c = getCharacter(playerId);
        session.redrawsUsed++;
        session.redrawsRemaining--;
        List<PassedCard> passed = advanceToFinalCard(session);
        return resolve(session, passed, c);
    }

    /**
     * Accept the check's final card, applying its removal mechanic (consume/burn) and closing
     * the session. Safe to call with no session open (dismissing an already-settled banner).
     */
    @Transactional
    public SkillCheckAccepted accept(String playerId) {
        DrawSession session = sessions.remove(playerId);
        if (session == null || session.current == null) return new SkillCheckAccepted(false, null);
        Card card = session.current;
        if (card.type() != CardType.CLASS || card.classCardIndex() == null) {
            return new SkillCheckAccepted(false, null);
        }
        String removal = deckTemplates.applyRemoval(playerId, card.classCardIndex());
        return new SkillCheckAccepted(removal != null, removal);
    }

    /**
     * Pop cards until one may resolve: CLASS cards restricted to another check are passed
     * (wrong-check, priority), CLASS cards with a redraw bonus are passed and their bonus
     * accumulates. Either resolves normally as the deck's last card. Auto-passes never cost
     * player redraws. Returns the cards passed on this advance.
     */
    private List<PassedCard> advanceToFinalCard(DrawSession session) {
        List<PassedCard> passed = new ArrayList<>();
        Card card = session.remaining.pop();
        while (card.type() == CardType.CLASS && !session.remaining.isEmpty()) {
            if (card.checkType() != null && !card.checkType().equals(session.skillId)) {
                passed.add(new PassedCard(card, "wrong-check"));
            } else if (card.redrawModifier() != null) {
                session.bonuses.add(new RedrawBonus(card.name(), card.redrawModifier()));
                passed.add(new PassedCard(card, "redraw-bonus"));
            } else {
                break;
            }
            card = session.remaining.pop();
        }
        session.current = card;
        return passed;
    }

    private SkillCheckResult resolve(DrawSession session, List<PassedCard> passed, GameCharacter c) {
        Card card = session.current;
        int bonusTotal = session.bonuses.stream().mapToInt(RedrawBonus::modifier).sum();
        boolean critical = card.type() == CardType.STEEL_CRITICAL || card.type() == CardType.MIGHT_CRITICAL;
        Integer effectiveModifier;
        Integer total;
        if (critical) {
            effectiveModifier = null;
            total = null;
        } else if (card.type() == CardType.STAT) {
            int statMod = c.getStats().modifier(session.ability);
            effectiveModifier = statMod;
            total = session.d10 + statMod + bonusTotal;
        } else {
            int m = card.modifier() != null ? card.modifier() : 0;
            effectiveModifier = m;
            total = session.d10 + m + bonusTotal;
        }
        return new SkillCheckResult(session.skillId, session.ability.name(), card, session.d10,
                effectiveModifier, total, critical, session.proficient,
                session.redrawsUsed, session.redrawsRemaining,
                List.copyOf(passed), List.copyOf(session.bonuses), bonusTotal);
    }

    private GameCharacter getCharacter(String playerId) {
        return repo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    private List<Card> shuffle(List<Card> deck) {
        var s = new ArrayList<>(deck);
        for (int i = s.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card tmp = s.get(i);
            s.set(i, s.get(j));
            s.set(j, tmp);
        }
        return s;
    }

    private AbilityScore abilityForSkill(String skillId) {
        var skills = gameData.getSkills();
        if (skills != null && skills.isArray()) {
            for (var s : skills) {
                if (skillId.equals(s.path("id").asText())) {
                    return parseAbility(s.path("ability").asText());
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown skill: " + skillId);
    }

    private AbilityScore parseAbility(String key) {
        return switch (key.toLowerCase()) {
            case "str" -> AbilityScore.STR;
            case "dex" -> AbilityScore.DEX;
            case "con", "const" -> AbilityScore.CON;
            case "int" -> AbilityScore.INT;
            case "wis" -> AbilityScore.WIS;
            case "will" -> AbilityScore.WILL;
            case "cha" -> AbilityScore.CHA;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid ability for skill: " + key);
        };
    }
}
