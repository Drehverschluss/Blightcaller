package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Server hooks of the curse core. Every listener catches its own exceptions.
 * <ul>
 *     <li><b>Tick:</b> {@link EntityTickEvent.Post} for every living entity with curse entries runs {@link Curses#tickAll}:
 *     the one place where curses tick. Fired for riders too ({@code rideTick}) and only while the entity ticks, so curses
 *     pause in chunks that do not tick, like effects do.</li>
 *     <li><b>Removing the display effect</b> (milk, totem, {@code /effect clear}, {@code removeAllEffects}, cleansing
 *     spells) removes the curse. Lowest priority and only if no other mod cancelled the removal. {@code Expired} only
 *     happens when something shortened the effect: the curse then goes on without it.</li>
 *     <li><b>{@code #blightcaller:curse_immune}:</b> the display effect is refused, so spells cannot even show a curse
 *     there ({@link Curses#apply} refuses the entries).</li>
 *     <li><b>Loading:</b> modifiers and client view are rebuilt from the saved entries (modifiers are never saved).</li>
 *     <li><b>Death:</b> the modifiers go; the entries stay on the dead entity (later phases read them).</li>
 * </ul>
 */
public final class CurseEvents {
    private CurseEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, CurseEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Remove.class, CurseEvents::onRemove);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Expired.class, CurseEvents::onExpired);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, MobEffectEvent.Applicable.class, CurseEvents::onApplicable);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, CurseEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, CurseEvents::onLeave);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, CurseEvents::onDeath);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living) || entity.level().isClientSide() || !CurseData.present(entity)) {
            return;
        }
        try {
            Curses.tickAll(living);
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: curse tick on {} failed", entity, e);
        }
    }

    private static void onRemove(MobEffectEvent.Remove event) {
        Holder<MobEffect> effect = event.getEffect();
        LivingEntity entity = event.getEntity();
        try {
            if (!event.isCanceled() && !entity.level().isClientSide() && Curses.isCurse(effect)) {
                Curses.dropEntries(entity, effect);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: clean-up of curse {} on {} failed", effect, entity, e);
        }
    }

    private static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        try {
            if (instance != null && !event.isCanceled() && !entity.level().isClientSide() && Curses.isCurse(instance.getEffect())) {
                Curses.displayExpired(entity, instance.getEffect());
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: expiry of curse effect on {} failed", entity, e);
        }
    }

    private static void onApplicable(MobEffectEvent.Applicable event) {
        try {
            MobEffectInstance instance = event.getEffectInstance();
            if (instance != null && Curses.isCurse(instance.getEffect()) && Curses.isImmune(event.getEntity())) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: curse immunity check on {} failed", event.getEntity(), e);
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || event.getLevel().isClientSide()) {
            return;
        }
        try {
            Curses.onLoaded(living);
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: restoring curses of {} failed", living, e);
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living && !event.getLevel().isClientSide()) {
            Curses.forget(living);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        try {
            if (!event.isCanceled() && !entity.level().isClientSide() && CurseData.present(entity)) {
                CurseModifiers.removeAll(entity);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: removing curse modifiers of {} failed", entity, e);
        }
    }
}
