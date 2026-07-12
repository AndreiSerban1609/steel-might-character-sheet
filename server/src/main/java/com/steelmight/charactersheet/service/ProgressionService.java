package com.steelmight.charactersheet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.steelmight.charactersheet.dto.ActionResponse;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.LevelUpRequest;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.CasterType;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Level-up (M6-B) + talent/feat progression (M6-C). The schedule
 * (Specializations PDF p.1): stat increases at 6/12/18; general talent picks at
 * 3/7/11/15/19 (the specialization's 2 additional talents join the pool from
 * level 5 onward); spec feat picks at 5/9/13 (active/passive/modification, any
 * order, each once); level 17 grants the 2nd spec talent (auto when one is
 * already owned, otherwise a required pick). All-or-nothing.
 */
@Service
@Transactional
public class ProgressionService {

    private static final Set<Integer> TALENT_LEVELS = Set.of(3, 7, 11, 15, 19);
    private static final Set<Integer> FEAT_LEVELS = Set.of(5, 9, 13);
    private static final Set<String> FEAT_SLOTS = Set.of("active", "passive", "modification");

    private final CharacterRepository repo;
    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;
    private final CharacterService characterService;

    public ProgressionService(CharacterRepository repo, GameDataProvider gameData,
                              StatDerivationEngine statEngine, CharacterService characterService) {
        this.repo = repo;
        this.gameData = gameData;
        this.statEngine = statEngine;
        this.characterService = characterService;
    }

    public ActionResponse<CombatSnapshot> levelUp(String playerId, LevelUpRequest req) {
        var c = characterService.getCharacter(playerId);
        int newLevel = c.getLevel() + 1;
        if (newLevel > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already at the level cap (20)");
        }
        var choices = req != null && req.choices() != null
                ? req.choices() : new LevelUpRequest.Choices(null, null, null, null);
        var creation = gameData.getCharacterCreation();
        var spec = c.getSpecializationId() != null
                ? gameData.findSpecialization(c.getClassId(), c.getSpecializationId()) : null;

        // ---- validate everything before mutating anything ----

        // Stat increases (Q39): required at 6/12/18, rejected elsewhere.
        boolean statLevel = false;
        for (var lvl : creation.path("statIncreaseLevels")) {
            if (lvl.asInt() == newLevel) statLevel = true;
        }
        if (statLevel) {
            characterService.validateBonusAllocation(choices.statIncreases(),
                    creation.path("bonusPoints").asInt(5),
                    creation.path("maxBonusPerStat").asInt(2));
        } else if (choices.statIncreases() != null && !choices.statIncreases().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "level " + newLevel + " grants no stat increase (levels: "
                            + creation.path("statIncreaseLevels") + ")");
        }

        // Spells per the progression arrays — never hardcoded counts.
        var newSpells = choices.newSpells() != null ? choices.newSpells() : List.<String>of();
        validateNewSpells(c, newLevel, newSpells);

        // Talents & spec feats (M6-C).
        String talentToGrant = validateTalentChoice(c, newLevel, spec, choices.talentId());
        String featToGrant = validateFeatChoice(c, newLevel, spec, choices.featId());

        // ---- mutate ----

        var result = new ResolutionResult();
        int hpBefore = statEngine.computeMaxHP(c);
        int manaBefore = statEngine.computeMaxMana(c);

        c.setLevel(newLevel);
        result.addStep("level-up", c.getName() + " reaches level " + newLevel,
                newLevel - 1, newLevel);

        if (statLevel) {
            for (var e : choices.statIncreases().entrySet()) {
                int before = c.getStats().get(e.getKey());
                c.getStats().set(e.getKey(), before + e.getValue());
                result.addStep("stat-increase", e.getKey() + " +" + e.getValue(),
                        before, before + e.getValue());
            }
        }

        // Q40: current HP rises by the max-HP delta (stat increases included).
        int hpAfter = statEngine.computeMaxHP(c);
        if (hpAfter != hpBefore) {
            int current = c.getHp().getCurrent();
            c.getHp().setCurrent(current + (hpAfter - hpBefore));
            result.addStep("hp-growth", "Max HP " + hpBefore + " → " + hpAfter
                    + "; current rises by the delta", current, c.getHp().getCurrent());
        }
        int manaAfter = statEngine.computeMaxMana(c);
        if (manaAfter != manaBefore) {
            result.addStep("mana-growth", "Max mana " + manaBefore + " → " + manaAfter,
                    manaBefore, manaAfter);
        }

        // Class resource max recomputes (M3 Part A); builders stay unbounded/at-zero.
        if (c.getResource() != null) {
            Integer derived = statEngine.computeClassResourceMax(c);
            if (derived != null && derived != StatDerivationEngine.UNBOUNDED_RESOURCE
                    && derived != c.getResource().getMax()) {
                result.addStep("resource-growth", c.getResource().getType() + " max recomputed",
                        c.getResource().getMax(), derived);
                c.getResource().setMax(derived);
            }
        }

        if (!newSpells.isEmpty()) {
            c.getKnownSpells().addAll(newSpells);
            result.addStep("spells-learned", "Learned " + String.join(", ", newSpells),
                    0, newSpells.size());
        }
        if (talentToGrant != null) {
            c.getTalents().add(talentToGrant);
            result.addStep("talent", "Gained talent " + talentToGrant, 0, 1);
        }
        if (featToGrant != null) {
            c.getSpecFeats().add(featToGrant);
            result.addStep("spec-feat", "Gained specialization " + featToGrant + " feat", 0, 1);
        }

        // Newly unlocked class abilities — display data for the UI.
        var unlocked = new ArrayList<String>();
        var classData = gameData.getClassAbilities().path(c.getClassId());
        for (var ability : classData.path("abilities")) {
            if (ability.path("level").asInt(-1) == newLevel) {
                unlocked.add(ability.path("name").asText());
            }
        }
        if (!unlocked.isEmpty()) {
            result.putPayload("newAbilities", unlocked);
        }
        result.putPayload("newLevel", newLevel);

        repo.save(c);
        return new ActionResponse<>(result, characterService.getCombatSnapshot(playerId));
    }

    /** M6-B step 3: exactly cumulative[new-1] − cumulative[new-2] spells, access-checked. */
    private void validateNewSpells(GameCharacter c, int newLevel, List<String> newSpells) {
        var casterType = statEngine.getCasterType(c);
        int required = 0;
        if (casterType != CasterType.NONE) {
            var cumulative = gameData.getSpellcasting().path("spellsKnownProgression")
                    .path(casterType.name().toLowerCase()).path("cumulative");
            if (cumulative.isArray() && newLevel <= cumulative.size()) {
                required = cumulative.get(newLevel - 1).asInt() - cumulative.get(newLevel - 2).asInt();
            }
        }
        if (newSpells.size() != required || newSpells.stream().distinct().count() != newSpells.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "level " + newLevel + " requires exactly " + required + " distinct new spell(s)");
        }
        int maxLevel = spellLevelAccess(casterType, newLevel);
        for (var spellId : newSpells) {
            var spell = gameData.getSpell(spellId);
            if (spell == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown spell: " + spellId);
            }
            if (!spell.classId().equals(c.getClassId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "spell '" + spellId + "' belongs to class '" + spell.classId() + "'");
            }
            if (spell.level() > maxLevel) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "no access to level-" + spell.level() + " spells at character level " + newLevel);
            }
            if (c.getKnownSpells().contains(spellId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        spellId + " is already known");
            }
        }
    }

    /** M6-C: general talent picks at 3/7/11/15/19; the 17th-level spec-talent rule. */
    private String validateTalentChoice(GameCharacter c, int newLevel, JsonNode spec, String talentId) {
        var specTalentSlugs = specTalentSlugs(spec);

        if (TALENT_LEVELS.contains(newLevel)) {
            if (talentId == null || talentId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "level " + newLevel + " grants a talent — talentId is required");
            }
            boolean general = gameData.getTalent(talentId) != null;
            // the spec's 2 additional talents join the pool from level 5 onward
            boolean fromSpec = newLevel >= 5 && specTalentSlugs.contains(talentId);
            if (!general && !fromSpec) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + talentId + "' is not in the eligible talent pool at level " + newLevel);
            }
            if (c.getTalents().contains(talentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "talent '" + talentId + "' is already owned");
            }
            return talentId;
        }

        if (newLevel == 17 && spec != null && !specTalentSlugs.isEmpty()) {
            var owned = specTalentSlugs.stream().filter(c.getTalents()::contains).toList();
            var missing = specTalentSlugs.stream().filter(s -> !c.getTalents().contains(s)).toList();
            if (missing.isEmpty()) return null; // both already owned
            if (!owned.isEmpty()) {
                return missing.get(0); // one owned → the other is granted free
            }
            // none owned → the player picks one of the two now
            if (talentId == null || !specTalentSlugs.contains(talentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "level 17 grants a specialization talent — pick one of " + specTalentSlugs);
            }
            return talentId;
        }

        if (talentId != null && !talentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "level " + newLevel + " grants no talent pick");
        }
        return null;
    }

    /** M6-C: spec feat picks at 5/9/13 — active/passive/modification, each once. */
    private String validateFeatChoice(GameCharacter c, int newLevel, JsonNode spec, String featId) {
        if (!FEAT_LEVELS.contains(newLevel)) {
            if (featId != null && !featId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "level " + newLevel + " grants no specialization feat");
            }
            return null;
        }
        if (spec == null) {
            // N8: classes without specialization content skip feat grants.
            return null;
        }
        if (featId == null || !FEAT_SLOTS.contains(featId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "level " + newLevel + " grants a specialization feat — featId must be one of "
                            + FEAT_SLOTS);
        }
        if (c.getSpecFeats().contains(featId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "the " + featId + " feat is already taken");
        }
        return featId;
    }

    private List<String> specTalentSlugs(JsonNode spec) {
        var slugs = new ArrayList<String>();
        if (spec != null) {
            for (var talent : spec.path("additionalTalents")) {
                slugs.add(GameDataProvider.slug(talent.path("name").asText()));
            }
        }
        return slugs;
    }

    private int spellLevelAccess(CasterType casterType, int characterLevel) {
        var access = gameData.getSpellcasting().path("spellLevelAccess")
                .path(casterType.name().toLowerCase());
        if (access.isArray() && characterLevel >= 1 && characterLevel <= access.size()) {
            return access.get(characterLevel - 1).asInt(1);
        }
        return 1;
    }
}
