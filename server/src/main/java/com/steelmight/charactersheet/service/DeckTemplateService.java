package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.Card;
import com.steelmight.charactersheet.dto.CardType;
import com.steelmight.charactersheet.dto.DeckCard;
import com.steelmight.charactersheet.dto.DeckTemplate;
import com.steelmight.charactersheet.dto.PlayerDeckConfig;
import com.steelmight.charactersheet.dto.PlayerDeckView;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.PlayerDeck;
import com.steelmight.charactersheet.model.RoomDeck;
import com.steelmight.charactersheet.model.TemplateCard;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.PlayerDeckRepository;
import com.steelmight.charactersheet.repository.RoomDeckRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DeckTemplateService {

    private final RoomDeckRepository roomRepo;
    private final PlayerDeckRepository playerRepo;
    private final CharacterRepository characterRepo;

    public DeckTemplateService(RoomDeckRepository roomRepo, PlayerDeckRepository playerRepo,
                               CharacterRepository characterRepo) {
        this.roomRepo = roomRepo;
        this.playerRepo = playerRepo;
        this.characterRepo = characterRepo;
    }

    // ---- Room template (GM) ----

    /** A room's saved base deck, or the Deck of Fates default if the GM hasn't set one. */
    public DeckTemplate getTemplate(String room) {
        if (room == null || room.isBlank()) return defaultTemplate();
        return roomRepo.findById(room).map(DeckTemplateService::toDto).orElseGet(DeckTemplateService::defaultTemplate);
    }

    public DeckTemplate updateTemplate(String room, DeckTemplate dto) {
        if (room == null || room.isBlank()) throw badRequest("room is required");
        validateTemplate(dto);
        var deck = roomRepo.findById(room).orElseGet(() -> new RoomDeck(room));
        deck.setStatCount(dto.statCount());
        deck.getNeutralCards().clear();
        for (var c : nullToEmpty(dto.neutralCards())) {
            deck.getNeutralCards().add(new TemplateCard(c.name(), c.modifier(), c.description()));
        }
        deck.getEncounterCards().clear();
        for (var c : nullToEmpty(dto.encounterCards())) {
            deck.getEncounterCards().add(new TemplateCard(c.name(), c.modifier(), c.description()));
        }
        return toDto(roomRepo.save(deck));
    }

    // ---- Player config ----

    public PlayerDeckConfig getPlayerConfig(String playerId) {
        return playerRepo.findById(playerId)
                .map(DeckTemplateService::toConfig)
                .orElseGet(() -> new PlayerDeckConfig(0, List.of()));
    }

    public PlayerDeckConfig updatePlayerConfig(String playerId, PlayerDeckConfig config) {
        validatePlayerConfig(config);
        var pd = playerRepo.findById(playerId).orElseGet(() -> new PlayerDeck(playerId));
        pd.setStatAdjust(config.statAdjust());
        pd.getExtraCards().clear();
        for (var c : nullToEmpty(config.extraCards())) {
            pd.getExtraCards().add(new TemplateCard(c.name(), c.modifier(), c.description(),
                    blank(c.checkType()) ? null : c.checkType(), c.redrawModifier(),
                    blank(c.removal()) ? null : c.removal(), c.consumed()));
        }
        pd.getDisabledEncounters().clear();
        config.disabledEncounters().stream()
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toLowerCase())
                .distinct()
                .forEach(pd.getDisabledEncounters()::add);
        return toConfig(playerRepo.save(pd));
    }

    /**
     * Apply the accepted final card's removal mechanic: "consume" marks it out until rest,
     * "burn" deletes it permanently. Returns the removal applied, or null if none.
     */
    public String applyRemoval(String playerId, int classCardIndex) {
        var pd = playerRepo.findById(playerId).orElse(null);
        if (pd == null || classCardIndex < 0 || classCardIndex >= pd.getExtraCards().size()) return null;
        var card = pd.getExtraCards().get(classCardIndex);
        String removal = card.getRemoval();
        if ("burn".equals(removal)) {
            pd.getExtraCards().remove(classCardIndex);
        } else if ("consume".equals(removal)) {
            card.setConsumed(true);
        } else {
            return null;
        }
        playerRepo.save(pd);
        return removal;
    }

    /** Any rest returns consumed cards to the deck (impl default; burn is forever). */
    public int restoreConsumedCards(String playerId) {
        var pd = playerRepo.findById(playerId).orElse(null);
        if (pd == null) return 0;
        int restored = 0;
        for (var card : pd.getExtraCards()) {
            if (card.isConsumed()) {
                card.setConsumed(false);
                restored++;
            }
        }
        if (restored > 0) playerRepo.save(pd);
        return restored;
    }

    public PlayerDeckView getPlayerDeckView(String playerId) {
        var c = character(playerId);
        var room = getTemplate(c.getRoomName());
        var config = getPlayerConfig(playerId);
        return new PlayerDeckView(room, config, buildDeck(room, config).size());
    }

    public PlayerDeckView updatePlayerDeck(String playerId, PlayerDeckConfig config) {
        character(playerId); // 404 if the character does not exist
        updatePlayerConfig(playerId, config);
        return getPlayerDeckView(playerId);
    }

    // ---- Deck build ----

    /** The concrete deck a character draws from: room base + that player's customizations. */
    public List<Card> effectiveDeck(GameCharacter c) {
        return buildDeck(getTemplate(c.getRoomName()), getPlayerConfig(c.getPlayerId()));
    }

    public List<Card> buildDeck(DeckTemplate room) {
        return buildDeck(room, null);
    }

    public List<Card> buildDeck(DeckTemplate room, PlayerDeckConfig player) {
        var cards = new ArrayList<Card>();
        cards.add(new Card(CardType.STEEL_CRITICAL, "Steel Critical", null, "The GM decides the outcome."));
        cards.add(new Card(CardType.MIGHT_CRITICAL, "Might Critical", null, "The GM decides the outcome."));
        for (var n : nullToEmpty(room.neutralCards())) {
            cards.add(new Card(CardType.NEUTRAL, blank(n.name()) ? "Neutral" : n.name(), n.modifier(), n.description()));
        }
        int statCount = room.statCount() + (player != null ? player.statAdjust() : 0);
        for (int i = 0; i < Math.max(0, statCount); i++) {
            cards.add(new Card(CardType.STAT, "Stat", null, "Your ability shapes the outcome."));
        }
        // Normalize here, not just at save time — raw configs must match too.
        var disabledNames = player == null ? java.util.Set.<String>of()
                : player.disabledEncounters().stream()
                        .filter(n -> n != null && !n.isBlank())
                        .map(n -> n.trim().toLowerCase())
                        .collect(java.util.stream.Collectors.toSet());
        for (var e : nullToEmpty(room.encounterCards())) {
            String name = blank(e.name()) ? "Encounter" : e.name();
            if (disabledNames.contains(name.toLowerCase())) {
                continue; // player opted out (matched by card name — stable across GM deck edits)
            }
            cards.add(new Card(CardType.ENCOUNTER, name, e.modifier(), e.description()));
        }
        if (player != null) {
            var extras = nullToEmpty(player.extraCards());
            for (int i = 0; i < extras.size(); i++) {
                var x = extras.get(i);
                if (Boolean.TRUE.equals(x.consumed())) continue; // spent until rest
                cards.add(new Card(CardType.CLASS, blank(x.name()) ? "Card" : x.name(), x.modifier(),
                        x.description(), blank(x.checkType()) ? null : x.checkType(), x.redrawModifier(), i,
                        blank(x.removal()) ? null : x.removal()));
            }
        }
        return cards;
    }

    // ---- validation / helpers ----

    private void validateTemplate(DeckTemplate dto) {
        if (dto.statCount() < 0 || dto.statCount() > 30) throw badRequest("statCount out of range (0-30)");
        checkCards(dto.neutralCards(), "neutral", 30);
        checkCards(dto.encounterCards(), "encounter", 30);
    }

    private void validatePlayerConfig(PlayerDeckConfig config) {
        if (config.statAdjust() < -20 || config.statAdjust() > 20) throw badRequest("statAdjust out of range (-20..20)");
        checkCards(config.extraCards(), "extra", 20);
        for (var c : nullToEmpty(config.extraCards())) {
            if (!blank(c.removal()) && !"consume".equals(c.removal()) && !"burn".equals(c.removal())) {
                throw badRequest("removal must be \"consume\" or \"burn\"");
            }
            if (c.redrawModifier() != null && (c.redrawModifier() < -20 || c.redrawModifier() > 20)) {
                throw badRequest("redrawModifier out of range (-20..20)");
            }
        }
        if (config.disabledEncounters().size() > 30) {
            throw badRequest("too many disabled encounter cards (max 30)");
        }
        for (var name : config.disabledEncounters()) {
            if (name != null && name.length() > 80) throw badRequest("disabled card name too long (max 80)");
        }
    }

    private void checkCards(List<DeckCard> cards, String label, int max) {
        var list = nullToEmpty(cards);
        if (list.size() > max) throw badRequest("too many " + label + " cards (max " + max + ")");
        for (var c : list) {
            if (c.modifier() < -20 || c.modifier() > 20) throw badRequest("card modifier out of range (-20..20)");
        }
    }

    private GameCharacter character(String playerId) {
        return characterRepo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static DeckTemplate defaultTemplate() {
        var neutrals = new ArrayList<DeckCard>();
        for (int i = 0; i < 5; i++) neutrals.add(new DeckCard("Neutral", 0, "No twist of fate."));
        var encounters = new ArrayList<DeckCard>();
        encounters.add(new DeckCard("Stumble", -1, "You lose your footing at the worst moment."));
        encounters.add(new DeckCard("Distraction", -1, "Something pulls your focus away."));
        encounters.add(new DeckCard("Bad Luck", -1, "Fate frowns upon this attempt."));
        return new DeckTemplate(neutrals, 4, encounters);
    }

    static DeckTemplate toDto(RoomDeck d) {
        return new DeckTemplate(
                d.getNeutralCards().stream()
                        .map(c -> new DeckCard(c.getName(), c.getModifier(), c.getDescription())).toList(),
                d.getStatCount(),
                d.getEncounterCards().stream()
                        .map(c -> new DeckCard(c.getName(), c.getModifier(), c.getDescription())).toList());
    }

    static PlayerDeckConfig toConfig(PlayerDeck d) {
        return new PlayerDeckConfig(d.getStatAdjust(),
                d.getExtraCards().stream()
                        .map(c -> new DeckCard(c.getName(), c.getModifier(), c.getDescription(),
                                c.getCheckType(), c.getRedrawModifier(), c.getRemoval(),
                                c.isConsumed() ? Boolean.TRUE : null))
                        .toList(),
                List.copyOf(d.getDisabledEncounters()));
    }
}
