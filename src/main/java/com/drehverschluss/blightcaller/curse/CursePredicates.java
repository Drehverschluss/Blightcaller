package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import org.jetbrains.annotations.Nullable;

/**
 * Entity predicates for spell target conditions ({@code entity_predicate_id} in a target modifier).
 * <ul>
 *     <li>{@code blightcaller:curse_level} with {@code entity_predicate_param} n: the target's curse level is at least n
 *     (default 1). Three target modifiers with n = 1, 2, 3 and {@code power_multiplier 0.15} give +15 % per level.</li>
 *     <li>{@code blightcaller:cursed}: the target has at least one curse.</li>
 *     <li>{@code blightcaller:caster_has_spell} with {@code entity_predicate_param} a spell id: the caster (the other entity of the
 *     condition) has that spell as a passive or modifier, e.g. from a skill node. With {@code all_required} next to
 *     {@code curse_level}, a node raises the bonus per curse level (Hex Bolt, Last Rites) while the number stays in the spell.</li>
 *     <li>{@code blightcaller:enemy}: the target is not the caster and Spell Engine allows a harmful spell of the caster on it
 *     (its relation rules). In a trigger's {@code target_conditions} this keeps self buffs from counting as a hit (Grave
 *     Candle relic).</li>
 * </ul>
 */
public final class CursePredicates {
    private CursePredicates() {}

    public static void register() {
        SpellEntityPredicates.register(Blightcaller.id("curse_level"), args -> {
            try {
                LivingEntity living = Curses.living(args.entity());
                return living != null && Curses.curseLevel(living) >= parseLevel(args.param());
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: predicate curse_level failed", e);
                return false;
            }
        });
        SpellEntityPredicates.register(Blightcaller.id("caster_has_spell"), args -> {
            try {
                return hasSpell(args.other(), args.param());
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: predicate caster_has_spell failed", e);
                return false;
            }
        });
        SpellEntityPredicates.register(Blightcaller.id("enemy"), args -> {
            try {
                LivingEntity target = Curses.living(args.entity());
                return target != null && args.other() instanceof LivingEntity caster
                        && target != caster && target.isAlive()
                        && EntityRelations.actionAllowed(SpellTarget.FocusMode.DIRECT, SpellTarget.Intent.HARMFUL, caster, target);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: predicate enemy failed", e);
                return false;
            }
        });
        SpellEntityPredicates.register(Blightcaller.id("cursed"), args -> {
            try {
                LivingEntity living = Curses.living(args.entity());
                return living != null && Curses.curseCount(living) > 0;
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: predicate cursed failed", e);
                return false;
            }
        });
    }

    /** Whether the caster has the spell (passive or modifier, e.g. from a skill node). Server and client. */
    public static boolean hasSpell(@Nullable Entity caster, @Nullable String spellId) {
        if (!(caster instanceof Player player) || spellId == null || spellId.isBlank()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(spellId.trim());
        if (id == null) {
            return false;
        }
        SpellContainerSource.Result spells = SpellContainerSource.getSpellsOf(player);
        if (spells == null) {
            return false;
        }
        for (List<Holder<Spell>> list : List.of(spells.modifiers(), spells.passives())) {
            for (Holder<Spell> spell : list) {
                if (spell.unwrapKey().map(k -> k.location().equals(id)).orElse(false)) {
                    return true;
                }
            }
        }
        return false;
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
