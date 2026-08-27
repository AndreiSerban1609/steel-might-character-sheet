package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.ActiveEffect;
import com.steelmight.charactersheet.model.Combatant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects mechanics of a type from a character's ACTIVE effects — dormant
 * threshold effects are inert (M0-A R3b), application-based effects contribute
 * only their acquired tiers (M2-B), and COMPOSITE mechanics expand one level
 * deep (M2-C: a stunned character counts as exposed+poisoned without those
 * rows existing). Nested composites are not in the data — logged and skipped.
 */
public final class ActiveMechanics {

    private static final Logger log = LoggerFactory.getLogger(ActiveMechanics.class);

    private ActiveMechanics() {}

    /**
     * @param effect the row on the character (for composite-included mechanics this is
     *               the HOST row, e.g. stunned — its stacks feed valueFromStacks)
     * @param def    the definition the mechanic came from (for included mechanics the
     *               included definition, e.g. exposed)
     */
    public record Hit(ActiveEffect effect, EffectDefinition def, EffectMechanic mechanic) {}

    public static List<Hit> collect(Combatant character, GameDataProvider gameData,
                                    int threshold, MechanicType type) {
        var hits = new ArrayList<Hit>();
        for (var active : character.getActiveEffects()) {
            var def = gameData.getEffect(active.getEffectId());
            if (def == null) continue;
            if (!EffectActivity.isActive(def, active, threshold)) continue;
            collectFrom(def, active, gameData, type, hits, true);
        }
        return hits;
    }

    private static void collectFrom(EffectDefinition def, ActiveEffect host, GameDataProvider gameData,
                                    MechanicType type, List<Hit> hits, boolean expandComposites) {
        for (var mech : def.mechanicsAtStacks(host.getStacks())) {
            if (mech.type() == type) {
                hits.add(new Hit(host, def, mech));
            } else if (mech.type() == MechanicType.COMPOSITE) {
                if (!expandComposites) {
                    log.warn("Nested composite inside '{}' — not supported, skipping", def.id());
                    continue;
                }
                if (mech.includes() == null) continue;
                for (var includedId : mech.includes()) {
                    var included = gameData.getEffect(includedId);
                    if (included == null) {
                        log.warn("Composite '{}' includes unknown effect '{}'", def.id(), includedId);
                        continue;
                    }
                    collectFrom(included, host, gameData, type, hits, false); // one level only
                }
            }
        }
    }
}
