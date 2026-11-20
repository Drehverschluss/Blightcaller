package com.drehverschluss.blightcaller.curse;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Attribute modifiers of curses (Enfeeble, Gravebind), always derived from the entries: a curse with at least one entry
 * has its modifier with the curse's amplifier, a curse without entries has none. {@link #sync} is called after every change
 * of the curses of an entity, when it is loaded and when it dies, so no modifier can outlive its curse (ending, death,
 * {@code clear}, milk, a restart in the middle of a curse).
 * <p>
 * The modifiers are transient (never saved). When an entity is loaded, {@link #sync} first removes any modifier of a curse
 * (also permanent ones that versions before Phase 6 saved through the effect) and puts back what the entries say.
 */
public final class CurseModifiers {
    private static List<Holder<MobEffect>> withModifiers;

    private CurseModifiers() {}

    /** Puts on or removes the modifiers of all curses with modifiers so they match the entries. */
    public static void sync(LivingEntity entity, @Nullable CurseData data) {
        for (Holder<MobEffect> curse : cursesWithModifiers()) {
            if (!(curse.value() instanceof CurseEffect effect)) {
                continue;
            }
            var id = Curses.id(curse);
            if (data != null && !data.entries(id).isEmpty()) {
                effect.applyModifiers(entity, data.amplifier(id));
            } else {
                effect.removeModifiers(entity);
            }
        }
    }

    /** Removes the modifiers of all curses (death). */
    public static void removeAll(LivingEntity entity) {
        sync(entity, null);
    }

    private static List<Holder<MobEffect>> cursesWithModifiers() {
        if (withModifiers == null) {
            List<Holder<MobEffect>> list = new ArrayList<>();
            BuiltInRegistries.MOB_EFFECT.holders().forEach(holder -> {
                if (holder.value() instanceof CurseEffect effect && effect.hasModifiers()) {
                    list.add(holder);
                }
            });
            withModifiers = List.copyOf(list);
        }
        return withModifiers;
    }
}
