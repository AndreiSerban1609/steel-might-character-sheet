package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.Card;
import com.steelmight.charactersheet.dto.CardType;
import com.steelmight.charactersheet.dto.SkillCheckResult;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side port of the Deck of Fates draw for skill checks. Slice 1 uses a fixed default room deck
 * (no GM template or per-player customization yet) and resolves a single draw + d10.
 */
@Service
@Transactional(readOnly = true)
public class SkillCheckService {

    private final CharacterRepository repo;
    private final GameDataProvider gameData;
    private final RandomSource random;
    private final DeckTemplateService deckTemplates;

    public SkillCheckService(CharacterRepository repo, GameDataProvider gameData, RandomSource random,
                             DeckTemplateService deckTemplates) {
        this.repo = repo;
        this.gameData = gameData;
        this.random = random;
        this.deckTemplates = deckTemplates;
    }

    public SkillCheckResult draw(String playerId, String skillId) {
        GameCharacter c = repo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        AbilityScore ability = abilityForSkill(skillId);

        List<Card> deck = shuffle(deckTemplates.effectiveDeck(c));
        Card card = deck.get(0);
        int d10 = random.nextInt(10) + 1;
        int statMod = c.getStats().modifier(ability);
        boolean proficient = c.getProficiencies().contains(skillId);

        boolean critical = card.type() == CardType.STEEL_CRITICAL || card.type() == CardType.MIGHT_CRITICAL;
        Integer effectiveModifier;
        Integer total;
        if (critical) {
            effectiveModifier = null;
            total = null;
        } else if (card.type() == CardType.STAT) {
            effectiveModifier = statMod;
            total = d10 + statMod;
        } else {
            int m = card.modifier() != null ? card.modifier() : 0;
            effectiveModifier = m;
            total = d10 + m;
        }

        return new SkillCheckResult(skillId, ability.name(), card, d10, effectiveModifier, total, critical, proficient);
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
