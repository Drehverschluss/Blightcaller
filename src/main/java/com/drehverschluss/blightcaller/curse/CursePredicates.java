package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.entity.SpellEntityPredicates;

/**
 * Entity predicates for spell target conditions ({@code entity_predicate_id} in a target modifier).
 * <ul>
 *     <li>{@code blightcaller:curse_level} with {@code entity_predicate_param} n: the target's curse level is at least n
 *     (default 1). Three target modifiers with n = 1, 2, 3 and {@code power_multiplier 0.15} give +15 % per level.</li>
 *     <li>{@code blightcaller:cursed}: the target has at least one curse.</li>
 * </ul>
 */
public final class CursePredicates {
    private CursePredicates() {}

    public static void register() {
        SpellEntityPredicates.register(Blightcaller.id("curse_level"), args -> {
            try {
                return args.entity() instanceof LivingEntity living && Curses.curseLevel(living) >= parseLevel(args.param());
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: predicate curse_level failed", e);
                return false;
            }
        });
        SpellEntityPredicates.register(Blightcaller.id("cursed"), args -> {
            try {
                return args.entity() instanceof LivingEntity living && Curses.curseCount(living) > 0;
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: predicate cursed failed", e);
                return false;
            }
        });
    }

    static int parseLevel(String param) {
        if (param == null || param.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
