package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.ActiveEffect;

/**
 * Shared dormancy check for the threshold system (Guide pp.8-9, M0-A R3b).
 *
 * Stack-based NEGATIVE effects accumulate dormant stacks and only fire while
 * "active": accumulated stacks have reached the character's threshold, or an
 * explicit active window (remainingRounds) is open — direct applications and
 * the player cleanse window use the latter. Everything else is active by
 * simply being present.
 */
public final class EffectActivity {

    private EffectActivity() {}

    public static boolean isActive(EffectDefinition def, ActiveEffect effect, int threshold) {
        if (def == null) return true; // unknown definition — don't silently mute the effect
        // Multi-instance effects (burning, envenomed, wounded) are the Guide's explicit
        // exceptions to the stacking system — each instance fires immediately.
        if (def.stackBased() && !def.multiInstance() && def.isNegative()) {
            return effect.getStacks() >= threshold || hasOpenWindow(effect);
        }
        return true;
    }

    public static boolean hasOpenWindow(ActiveEffect effect) {
        return effect.getRemainingRounds() != null && effect.getRemainingRounds() > 0;
    }
}
