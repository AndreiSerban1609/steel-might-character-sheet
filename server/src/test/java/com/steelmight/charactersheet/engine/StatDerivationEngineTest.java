package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StatDerivationEngineTest {

    @Autowired
    private StatDerivationEngine engine;

    @Autowired
    private GameDataProvider gameData;

    private GameCharacter character;

    @BeforeEach
    void setUp() {
        character = new GameCharacter("test-player");
        character.setName("Test");
        character.setLevel(5);
        character.setClassId("bard");
        character.setPathId("musician");
        character.setSpeed(30);
        character.setStats(new Stats(10, 14, 12, 10, 10, 10, 16));
        character.setHp(new HitPoints(100, 100, 0));
        character.setMana(new ManaPool(200, 200));
        character.setAp(new ActionPoints(6, 6, 10));
    }

    @Nested
    class ProficiencyBonus {
        @Test
        void level1Returns2() {
            character.setLevel(1);
            assertThat(engine.computeProficiencyBonus(character)).isEqualTo(2);
        }

        @Test
        void level5Returns3() {
            assertThat(engine.computeProficiencyBonus(character)).isEqualTo(3);
        }
    }

    @Nested
    class SpellcastingAttribute {
        @Test
        void bardUsesCha() {
            assertThat(engine.getSpellcastingAttribute(character)).isEqualTo(AbilityScore.CHA);
        }

        @Test
        void nullForNonCaster() {
            character.setClassId("barbarian");
            assertThat(engine.getSpellcastingAttribute(character)).isNull();
        }
    }

    @Nested
    class MaxHP {
        @Test
        void computesFromFormula() {
            // bard: hpPerLevel=25, CON=12 → mod=1, level=5
            // (25 + 3*1) * 5 = 140
            assertThat(engine.computeMaxHP(character)).isEqualTo(140);
        }

        @Test
        void negativeCONReducesHP() {
            character.setStats(new Stats(10, 14, 8, 10, 10, 10, 16));
            // CON=8 → mod=-1, (25 + 3*(-1)) * 5 = 110
            assertThat(engine.computeMaxHP(character)).isEqualTo(110);
        }
    }

    @Nested
    class ArmorClass {
        @Test
        void unarmoredUses10PlusDex() {
            // DEX=14 → mod=+2, unarmored AC = 12
            assertThat(engine.computeAC(character)).isEqualTo(12);
        }

        @Test
        void withLightArmor() {
            character.addItem(new InventoryEntry("light-armor", 1, 0, true));
            // light armor: base=11, dexMod=true, mult=1, DEX mod=+2 → 13
            // level 5 → 1 acBonusLevel hit (lvl 5) → +1 = 14
            assertThat(engine.computeAC(character)).isEqualTo(14);
        }
    }

    @Nested
    class PhysicalArmor {
        @Test
        void zeroWithoutArmor() {
            assertThat(engine.computePA(character)).isEqualTo(0);
        }

        @Test
        void scalesWithLevel() {
            character.addItem(new InventoryEntry("light-armor", 1, 0, true));
            // light armor: pa=1, paScaling=1, level=5 → 1 + 1*4 = 5
            assertThat(engine.computePA(character)).isEqualTo(5);
        }
    }

    @Nested
    class MagicArmor {
        @Test
        void scalesWithLevel() {
            character.addItem(new InventoryEntry("light-armor", 1, 0, true));
            // light armor: ma=4, maScaling=4, level=5 → 4 + 4*4 = 20
            assertThat(engine.computeMA(character)).isEqualTo(20);
        }
    }

    @Nested
    class SpeedWithEffects {
        @Test
        void baseSpeedWithNoEffects() {
            assertThat(engine.computeSpeed(character)).isEqualTo(30);
        }

        @Test
        void hasteAdds10() {
            character.addEffect(new ActiveEffect("haste", "spell", 1, null, 3, 0));
            // haste: { stat: "speed", value: 10 }
            assertThat(engine.computeSpeed(character)).isEqualTo(40);
        }

        @Test
        void difficultTerrainHalves() {
            character.addEffect(new ActiveEffect("difficult-terrain", "env", 1, null, null, 0));
            // difficult-terrain: { stat: "speed", multiplier: 0.5 }
            assertThat(engine.computeSpeed(character)).isEqualTo(15);
        }

        @Test
        void hasteAddsThenDifficultTerrainHalves() {
            character.addEffect(new ActiveEffect("haste", "spell", 1, null, 3, 0));
            character.addEffect(new ActiveEffect("difficult-terrain", "env", 1, null, null, 0));
            // 30 + 10 = 40, then × 0.5 = 20
            assertThat(engine.computeSpeed(character)).isEqualTo(20);
        }

        @Test
        void movementSpeedBonusUsesStacks() {
            character.addEffect(new ActiveEffect("ms-bonus", "ability", 1, null, 1, 0));
            // ms-bonus: { stat: "speed", valueFromStacks: true } → 1 * 1 stack = +1
            assertThat(engine.computeSpeed(character)).isEqualTo(31);
        }
    }

    @Nested
    class APRecoveryWithEffects {
        @Test
        void baseRecoveryWithNoEffects() {
            assertThat(engine.computeAPRecovery(character)).isEqualTo(6);
        }

        @Test
        void hasteAdds1() {
            character.addEffect(new ActiveEffect("haste", "spell", 1, null, 3, 0));
            // haste: { stat: "apRecovery", value: 1 }
            assertThat(engine.computeAPRecovery(character)).isEqualTo(7);
        }

        @Test
        void dazedHalves() {
            character.addEffect(new ActiveEffect("dazed", "attack", 1, null, 1, 0));
            // dazed: { stat: "apRecovery", multiplier: 0.5 }
            assertThat(engine.computeAPRecovery(character)).isEqualTo(3);
        }

        @Test
        void stunnedOverridesToZero() {
            character.addEffect(new ActiveEffect("stunned", "attack", 1, null, 1, 0));
            // stunned: { stat: "apRecovery", value: 0, override: true }
            assertThat(engine.computeAPRecovery(character)).isEqualTo(0);
        }

        @Test
        void stunnedOverridesEvenWithHaste() {
            character.addEffect(new ActiveEffect("haste", "spell", 1, null, 3, 0));
            character.addEffect(new ActiveEffect("stunned", "attack", 1, null, 1, 0));
            assertThat(engine.computeAPRecovery(character)).isEqualTo(0);
        }
    }

    @Nested
    class SpellDC {
        @Test
        void computesCorrectly() {
            // bard → CHA=16 (mod=+3), level 5 → prof 3
            // DC = 8 + 3 + 3 = 14
            assertThat(engine.computeSpellSaveDC(character)).isEqualTo(14);
        }
    }

    @Nested
    class MaxMana {
        @Test
        void computesFromSubclass() {
            // bard: manaPerLevel=50, level=5 → 250
            // manaIncreases at proficiency milestone 5 → +25
            assertThat(engine.computeMaxMana(character)).isEqualTo(275);
        }

        @Test
        void zeroForNonCaster() {
            character.setClassId("barbarian");
            assertThat(engine.computeMaxMana(character)).isEqualTo(0);
        }
    }

    @Nested
    class WeaponApCost {
        @Test
        void defaultWithoutWeapon() {
            assertThat(engine.computeWeaponApCost(character)).isEqualTo(3);
        }

        @Test
        void fromEquippedWeapon() {
            character.addItem(new InventoryEntry("shortbow", 1, 0, true));
            // shortbow: apCost=2
            assertThat(engine.computeWeaponApCost(character)).isEqualTo(2);
        }

        @Test
        void negateEffectReducesCost() {
            character.addItem(new InventoryEntry("greataxe", 1, 0, true));
            // greataxe: apCost=3
            // reduced-weapon-ap-cost: valueFromStacks=true, negate=true
            // stacks=1 → raw=1, negated→-1, base 3+(-1)=2
            character.addEffect(new ActiveEffect("reduced-weapon-ap-cost", "buff", 1, null, 3, 0));
            assertThat(engine.computeWeaponApCost(character)).isEqualTo(2);
        }

        @Test
        void minimumCostIs1() {
            character.addItem(new InventoryEntry("shortbow", 1, 0, true));
            // shortbow: apCost=2, reduce by 5 stacks → -5, 2+(-5)=-3 → clamped to 1
            character.addEffect(new ActiveEffect("reduced-weapon-ap-cost", "buff", 5, null, 3, 0));
            assertThat(engine.computeWeaponApCost(character)).isEqualTo(1);
        }
    }

    @Nested
    class EquipmentLookups {
        @Test
        void findsEquippedWeaponId() {
            character.addItem(new InventoryEntry("greataxe", 1, 0, true));
            assertThat(engine.findEquippedWeaponId(character)).isEqualTo("greataxe");
        }

        @Test
        void findsEquippedArmorId() {
            character.addItem(new InventoryEntry("light-armor", 1, 0, true));
            assertThat(engine.findEquippedArmorId(character)).isEqualTo("light-armor");
        }

        @Test
        void nullWhenNothingEquipped() {
            assertThat(engine.findEquippedWeaponId(character)).isNull();
            assertThat(engine.findEquippedArmorId(character)).isNull();
        }
    }
}
