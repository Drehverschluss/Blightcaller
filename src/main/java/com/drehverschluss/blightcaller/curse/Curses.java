package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The curse system: putting curses on targets, ticking them, curse level and clean-up.
 * <p>
 * Rules (concept 3.1):
 * <ul>
 *     <li>Another caster, same curse: an entry of their own, ticking separately with their own damage and credit.</li>
 *     <li>Same caster, same curse: their entry is replaced (new snapshot, new critical roll, the later end time of old
 *     and new). The tick rhythm stays.</li>
 *     <li>Curse level: the number of different curses ({@code #blightcaller:curses}) on the target, from anyone, at most
 *     {@value #MAX_CURSE_LEVEL}.</li>
 *     <li>No catching up: an overdue entry ticks once and then keeps a new rhythm from now; entries that missed their
 *     end are dropped without a tick.</li>
 *     <li>Broken last interval: if the end falls between two ticks, the curse ticks once more at its end with the share
 *     remaining time / tick interval of the damage. So every percent of Curse Duration adds exactly as much damage.</li>
 * </ul>
 */
public final class Curses {
    public static final int MAX_CURSE_LEVEL = 3;

    private Curses() {}

    // ------------------------------------------------------------------------------------------------ queries

    public static boolean isCurse(Holder<MobEffect> effect) {
        return effect.is(CurseEffects.CURSES);
    }

    /** Whether a curse deals damage over time (all but curses like Enfeeble, see {@link CurseEffect#dealsDamage()}). */
    public static boolean dealsDamage(MobEffect effect) {
        return !(effect instanceof CurseEffect curse) || curse.dealsDamage();
    }

    /** Number of different curses on the entity, capped at {@value #MAX_CURSE_LEVEL}. Server side. */
    public static int curseLevel(LivingEntity entity) {
        return Math.min(MAX_CURSE_LEVEL, curseCount(entity));
    }

    /** Number of different curses on the entity, not capped. */
    public static int curseCount(LivingEntity entity) {
        int count = 0;
        for (Holder<MobEffect> effect : entity.getActiveEffectsMap().keySet()) {
            if (isCurse(effect)) {
                count++;
            }
        }
        return count;
    }

    /** The curse entries of an entity, if it has any. */
    public static Optional<CurseData> data(LivingEntity entity) {
        return entity.getExistingData(CurseData.TYPE);
    }

    /** Entries of one curse on the entity (a copy), empty if none. */
    public static List<CurseEntry> entries(LivingEntity entity, Holder<MobEffect> curse) {
        ResourceLocation id = id(curse);
        return data(entity).map(d -> List.copyOf(d.entries(id))).orElse(List.of());
    }

    /**
     * Exact duration in game ticks after Curse Duration: {@code ticks x (1 + bonus of the caster)}, unless not extended.
     * Not rounded: 4 s with +8 % are 86.4 ticks, the last tick then deals 0.32 of the damage.
     */
    public static double finalDuration(CurseApplication app, @Nullable LivingEntity casterEntity) {
        if (!app.extend() || app.transferred()) {
            return app.durationTicks();
        }
        double bonus = BlightcallerAttributes.curseDurationBonus(casterEntity);
        return app.durationTicks() * (1.0 + bonus);
    }

    // ------------------------------------------------------------------------------------------------ apply

    /**
     * Puts a curse on a target (server side). Creates or replaces the caster's entry and sets the curse effect to the
     * longest remaining duration of all entries. Returns the new entry, or {@code null} if the target cannot have it
     * (not a curse, dead, client side, immune to the effect, zero duration).
     */
    @Nullable
    public static CurseEntry apply(LivingEntity target, CurseApplication app) {
        if (!(target.level() instanceof ServerLevel level) || target.isDeadOrDying() || !isCurse(app.curse())) {
            return null;
        }
        LivingEntity casterEntity = CurseDamage.resolveCaster(level, app.caster());
        double duration = finalDuration(app, casterEntity);
        if (duration < 1.0) {
            return null;
        }
        sweep(target);
        long now = level.getGameTime();
        int interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
        ResourceLocation id = id(app.curse());
        CurseData data = target.getData(CurseData.TYPE);
        List<CurseEntry> entries = data.entriesForWrite(id);

        CurseEntry old = null;
        for (CurseEntry entry : entries) {
            if (entry.sameCaster(app.caster())) {
                old = entry;
                break;
            }
        }
        double end = now + duration;
        CurseEntry entry;
        if (old != null) {
            // refresh: new snapshot and roll, the later end, same rhythm, tick count kept
            entry = new CurseEntry(app.caster(), app.power(), app.factor(), app.critical(), app.criticalDamage(),
                    old.nextTick, Math.max(old.end, end), app.jumps(), app.chain(), old.ticks, app.transferred());
            entry.nextSpread = old.nextSpread;
            entries.set(entries.indexOf(old), entry);
        } else {
            entry = new CurseEntry(app.caster(), app.power(), app.factor(), app.critical(), app.criticalDamage(),
                    now + interval, end, app.jumps(), app.chain(), 0, app.transferred());
            entries.add(entry);
        }

        if (!syncEffect(target, app.curse(), entries, now, casterEntity)) {
            entries.remove(entry);
            if (old != null) {
                entries.add(old);
            }
            cleanUp(target, data);
            return null;
        }
        return entry;
    }

    /**
     * Sets the curse effect to the longest remaining duration of its entries, plus one tick: the effect then always
     * outlives the last damage tick (that tick removes it by returning {@code false}), no matter in which order the
     * target and the caster's projectile tick. Keeps the amplifier of an existing effect. Returns {@code false} if the
     * target does not take the effect.
     */
    static boolean syncEffect(LivingEntity target, Holder<MobEffect> curse, List<CurseEntry> entries, long now,
                              @Nullable LivingEntity source) {
        long latest = now;
        for (CurseEntry entry : entries) {
            latest = Math.max(latest, entry.expires());
        }
        int wanted = (int) Math.min(Integer.MAX_VALUE - 1, latest - now) + 1;
        MobEffectInstance current = target.getEffect(curse);
        if (current == null) {
            int amplifier = curse.value() instanceof CurseEffect effect ? effect.defaultAmplifier(target) : 0;
            return target.addEffect(instance(curse, wanted, amplifier), source) && target.hasEffect(curse);
        }
        if (current.getDuration() < wanted) {
            target.addEffect(instance(curse, wanted, current.getAmplifier()), source);
        } else if (current.getDuration() > wanted && !current.isInfiniteDuration()) {
            // a longer effect from elsewhere (e.g. /effect give): shorten to the entries without a remove event
            target.forceAddEffect(instance(curse, wanted, current.getAmplifier()), source);
        }
        return target.hasEffect(curse);
    }

    private static MobEffectInstance instance(Holder<MobEffect> curse, int duration, int amplifier) {
        return new MobEffectInstance(curse, duration, amplifier, false, false, true);
    }

    // ------------------------------------------------------------------------------------------------ tick

    /** One game tick of a curse effect on an entity (server side). Returns whether the curse goes on. */
    static boolean tick(LivingEntity entity, MobEffect effect) {
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        Optional<CurseData> optional = data(entity);
        if (id == null || optional.isEmpty()) {
            return false;
        }
        CurseData data = optional.get();
        try {
            sweepExcept(entity, data, id);
            List<CurseEntry> entries = data.entries(id);
            if (entries.isEmpty()) {
                cleanUp(entity, data);
                return false;
            }
            long now = entity.level().getGameTime();
            int interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
            // curses without damage (Enfeeble) keep their rhythm and tick count, but never hit
            boolean damaging = dealsDamage(effect);
            // a copy: a tick may kill the target, and death handlers may read the entries
            for (CurseEntry entry : List.copyOf(entries)) {
                if (now > entry.expires()) {
                    // missed its end (chunk did not tick, server hung): dropped without catching up
                    entries.remove(entry);
                    continue;
                }
                double share = 0.0;
                if (now >= entry.nextTick && entry.nextTick <= entry.end) {
                    share = 1.0;
                    entry.ticks++;
                    long next = entry.nextTick + interval;
                    entry.nextTick = next > now ? next : now + interval;
                }
                boolean ends = now >= entry.expires();
                if (ends) {
                    // the end falls between two ticks: a last tick with the share of the started interval
                    double lastTick = entry.nextTick - interval;
                    double fraction = (entry.end - lastTick) / interval;
                    if (fraction > 1.0E-6 && fraction < 1.0) {
                        share += fraction;
                    }
                }
                if (damaging && share > 0.0 && entity.isAlive()) {
                    CurseDamage.tick(entity, entry, share);
                }
                if (ends) {
                    entries.remove(entry);
                }
            }
            boolean goesOn = !entries.isEmpty() && entity.isAlive();
            cleanUp(entity, data);
            return goesOn;
        } catch (Exception e) {
            // end the curse instead of failing again on every tick
            Blightcaller.LOGGER.error("Blightcaller: curse tick of {} on {} failed, curse removed", id, entity, e);
            data.remove(id);
            cleanUp(entity, data);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------ remove entries

    /**
     * Removes the entries of one caster from one curse on the target (Last Rites). Entries of other casters stay; the
     * effect goes only when no entry is left, otherwise it is shortened to the longest remaining entry. Returns the
     * removed entries.
     */
    public static List<CurseEntry> removeEntries(LivingEntity target, Holder<MobEffect> curse, @Nullable java.util.UUID caster) {
        Optional<CurseData> optional = data(target);
        if (optional.isEmpty() || !(target.level() instanceof ServerLevel level)) {
            return List.of();
        }
        CurseData data = optional.get();
        ResourceLocation id = id(curse);
        List<CurseEntry> entries = data.entries(id);
        List<CurseEntry> removed = new ArrayList<>();
        for (CurseEntry entry : List.copyOf(entries)) {
            if (entry.sameCaster(caster)) {
                entries.remove(entry);
                removed.add(entry);
            }
        }
        if (removed.isEmpty()) {
            return removed;
        }
        if (entries.isEmpty()) {
            data.remove(id);
            cleanUp(target, data);
            target.removeEffect(curse);
        } else {
            syncEffect(target, curse, entries, level.getGameTime(), null);
        }
        return removed;
    }

    /**
     * Damage per second one entry deals to the target right now (power x factor x critical x soul vulnerability, per tick
     * interval). Curses without damage give 0.
     */
    public static double damagePerSecond(LivingEntity target, Holder<MobEffect> curse, CurseEntry entry) {
        if (!dealsDamage(curse.value())) {
            return 0.0;
        }
        int interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
        return CurseDamage.amount(target, entry) * 20.0 / interval;
    }

    // ------------------------------------------------------------------------------------------------ clean-up

    /** Removes the entries of one curse (its effect was removed or ran out). */
    static void dropEntries(LivingEntity entity, Holder<MobEffect> curse) {
        data(entity).ifPresent(data -> {
            data.remove(id(curse));
            cleanUp(entity, data);
        });
    }

    /**
     * Removes the curses of an entity (all, or only one): the effects and all entries. Returns the number of curses
     * removed.
     */
    public static int clear(LivingEntity entity, @Nullable Holder<MobEffect> only) {
        int removed = 0;
        List<Holder<MobEffect>> effects = new ArrayList<>();
        for (Holder<MobEffect> effect : entity.getActiveEffectsMap().keySet()) {
            if (isCurse(effect) && (only == null || effect.equals(only))) {
                effects.add(effect);
            }
        }
        for (Holder<MobEffect> effect : effects) {
            if (entity.removeEffect(effect)) {
                removed++;
            }
            dropEntries(entity, effect);
        }
        if (only == null) {
            data(entity).ifPresent(data -> {
                data.all().clear();
                cleanUp(entity, data);
            });
        } else {
            dropEntries(entity, only);
        }
        return removed;
    }

    /** Drops entries of curses whose effect is gone (removed without an event, e.g. {@code removeEffectNoUpdate}). */
    static void sweep(LivingEntity entity) {
        data(entity).ifPresent(data -> {
            sweepExcept(entity, data, null);
            cleanUp(entity, data);
        });
    }

    private static void sweepExcept(LivingEntity entity, CurseData data, @Nullable ResourceLocation keep) {
        Iterator<Map.Entry<ResourceLocation, List<CurseEntry>>> it = data.all().entrySet().iterator();
        while (it.hasNext()) {
            ResourceLocation curse = it.next().getKey();
            if (curse.equals(keep)) {
                continue;
            }
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(curse);
            if (holder.isEmpty() || !entity.hasEffect(holder.get())) {
                it.remove();
            }
        }
    }

    /** Drops empty lists, and the attachment itself once nothing is left. */
    static void cleanUp(LivingEntity entity, CurseData data) {
        data.compact();
        if (data.isEmpty() && entity.hasData(CurseData.TYPE)) {
            entity.removeData(CurseData.TYPE);
        }
    }

    public static ResourceLocation id(Holder<MobEffect> effect) {
        return effect.unwrapKey().map(ResourceKey::location)
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(effect.value()));
    }
}
