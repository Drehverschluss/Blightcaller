package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Clean-up: when a curse effect is removed (milk, totem, {@code /effect clear}, {@code removeAllEffects}) or runs out,
 * the entries of that curse go too. Lowest priority and only if no other mod cancelled the removal.
 * {@code removeEffectNoUpdate} fires no event; those leftovers are dropped on the next tick or cast ({@link Curses}).
 */
public final class CurseEvents {
    private CurseEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Remove.class, CurseEvents::onRemove);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Expired.class, CurseEvents::onExpired);
    }

    private static void onRemove(MobEffectEvent.Remove event) {
        drop(event.getEntity(), event.getEffect());
    }

    private static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null) {
            drop(event.getEntity(), instance.getEffect());
        }
    }

    private static void drop(LivingEntity entity, Holder<MobEffect> effect) {
        try {
            if (!entity.level().isClientSide() && Curses.isCurse(effect)) {
                Curses.dropEntries(entity, effect);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: clean-up of curse {} on {} failed", effect, entity, e);
        }
    }
}
