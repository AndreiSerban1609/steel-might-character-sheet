package com.steelmight.charactersheet.gamedata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4-A acceptance criterion 1: all ten spells-*.json files load, ids are unique
 * (duplicates fail startup, so a green context is itself the assertion), every
 * classId is a known class, and every referenced effect exists in effects.json.
 * Data gaps are reported in the assertion message — never fixed silently.
 */
@SpringBootTest
class GameDataProviderSpellsTest {

    @Autowired
    private GameDataProvider gameData;

    @Test
    void all817SpellsLoad() {
        assertThat(gameData.getAllSpells()).hasSize(817);
    }

    @Test
    void everySpellClassIdIsAKnownClass() {
        var knownClasses = new HashSet<String>();
        for (var path : gameData.getClasses()) {
            for (var classId : path.path("classes")) {
                knownClasses.add(classId.asText());
            }
        }
        var unknown = new ArrayList<String>();
        for (var spell : gameData.getAllSpells().values()) {
            if (!knownClasses.contains(spell.classId())) {
                unknown.add(spell.id() + " → " + spell.classId());
            }
        }
        assertThat(unknown)
                .as("spells referencing unknown class ids (data gap — report, don't fix silently)")
                .isEmpty();
    }

    @Test
    void everyReferencedEffectExistsInEffectsJson() {
        var unknown = new ArrayList<String>();
        for (var spell : gameData.getAllSpells().values()) {
            if (spell.effects() == null) continue;
            for (var effectId : spell.effects()) {
                if (gameData.getEffect(effectId) == null) {
                    unknown.add(spell.id() + " → " + effectId);
                }
            }
        }
        assertThat(unknown)
                .as("spells referencing unknown effect ids (data gap — report, don't fix silently)")
                .isEmpty();
    }

    @Test
    void spellFieldsDeserializeFully() {
        var spell = gameData.getSpell("magic-bolt");
        assertThat(spell).isNotNull();
        assertThat(spell.name()).isEqualTo("Magic bolt");
        assertThat(spell.classId()).isEqualTo("sorcerer");
        assertThat(spell.level()).isEqualTo(1);
        assertThat(spell.apCost().flat()).isEqualTo(3);
        assertThat(spell.manaCost().flat()).isEqualTo(5);
        assertThat(spell.components()).containsExactly("V", "S");
        assertThat(spell.damageType()).isEqualTo("pure");
        assertThat(spell.attackType()).isEqualTo("rangedSpellAttack");
        assertThat(spell.damage().dice()).isEqualTo(new Dice(2, 10));
        assertThat(spell.damage().flat()).isEqualTo(9);
        assertThat(spell.damage().modMultiplier()).isEqualTo(1.0);
        assertThat(spell.scaling().manaCostIncrease()).isEqualTo(25);
        assertThat(spell.scaling().damageIncrease().dice()).isEqualTo(new Dice(2, 10));
        assertThat(spell.scaling().damageIncrease().flat()).isEqualTo(8);

        // spells-disciple.json spells the same dice as {count, sides} objects.
        assertThat(gameData.getSpell("sacred-bolt").damage().dice()).isEqualTo(new Dice(2, 8));
    }

    /** The data's non-int costs deserialize into their structured forms. */
    @Test
    void nonNumericCostsAreCaptured() {
        assertThat(gameData.getSpell("radiant-aura").manaCost().percentOfMax()).isEqualTo(10);
        assertThat(gameData.getSpell("intercept").apCost().special()).isEqualTo("reaction");
        assertThat(gameData.getSpell("glyph-of-danger").apCost().special()).isEqualTo("1 or 2");
    }

    @Test
    void casterWeaponsAreIndexedAndTyped() {
        var orb = gameData.getCasterWeapon("arcane-orb-3");
        assertThat(orb).isNotNull();
        assertThat(orb.type()).isEqualTo("orb");
        assertThat(orb.spellModifier()).isEqualTo(3);

        var spellbook = gameData.getCasterWeapon("arcane-spellbook-10");
        assertThat(spellbook).isNotNull();
        assertThat(spellbook.extraSpellsKnown()).isEqualTo(4);

        assertThat(gameData.getCasterWeapon("not-a-caster-weapon")).isNull();
    }
}
