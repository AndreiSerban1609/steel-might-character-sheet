package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.steelmight.charactersheet.model.AbilityScore;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EffectMechanic(
        MechanicType type,

        // --- statModifier ---
        ModifiableStat stat,
        Integer value,
        Double multiplier,
        @JsonProperty("valueFromStacks") boolean valueFromStacks,
        @JsonProperty("override") boolean override,
        @JsonProperty("negate") boolean negate,
        String specialRule,

        // --- damageModifier ---
        DamageDirection direction,
        @JsonProperty("flat") boolean flat,
        String damageCategory,
        String damageType,
        String source,
        @JsonProperty("perAbility") boolean perAbility,
        @JsonProperty("ignoreResistance") boolean ignoreResistance,
        @JsonProperty("ignoreVulnerability") boolean ignoreVulnerability,
        String bonusDamageType,
        String valueSource,

        // --- damageAbsorb ---
        AbsorbMode mode,
        @JsonProperty("noStack") boolean noStack,
        String condition,
        Integer chance,

        // --- healingModifier ---
        @JsonProperty("convertToDamage") boolean convertToDamage,

        // --- dot / hot ---
        Timing timing,

        // --- advantage / disadvantage ---
        AdvantageTarget on,
        AbilityScore saveStat,
        Integer charges,
        List<String> against,

        // --- preventAction ---
        PreventableAction action,
        @JsonProperty("allowTeleportation") boolean allowTeleportation,

        // --- immunity ---
        Object to,
        List<String> except,
        @JsonProperty("consumeStacks") boolean consumeStacks,

        // --- triggerOnEvent / removeOnEvent ---
        TriggerEvent event,
        TriggerAction triggerAction,
        String actionEffect,
        Integer actionStacks,
        @JsonProperty("requiresSave") boolean requiresSave,
        @JsonProperty("ignoresArmor") boolean ignoresArmor,

        // --- composite ---
        List<String> includes,

        // --- forcedBehavior ---
        ForcedBehavior behavior,

        // --- standUpCost ---
        Integer baseCost,
        Integer costPerStack,

        // --- grantAbility ---
        GrantableAbility ability,

        // --- aura ---
        @JsonProperty("aura") boolean aura,
        @JsonProperty("affectsAllies") boolean affectsAllies,
        @JsonProperty("area") boolean area,

        // --- escalating (precision aura) ---
        @JsonProperty("escalateOnMiss") boolean escalateOnMiss,
        @JsonProperty("resetOnHit") boolean resetOnHit,

        // --- triggerOnEvent extras ---
        Integer duration,
        Integer count,
        String targetCategory
) {
    public int resolveValue(Integer effectValue) {
        if (valueFromStacks && effectValue != null) return effectValue;
        return value != null ? value : 0;
    }

    @SuppressWarnings("unchecked")
    public List<String> immunityTargets() {
        if (to instanceof List<?> list) return (List<String>) list;
        if (to instanceof String s) return List.of(s);
        return List.of();
    }
}
