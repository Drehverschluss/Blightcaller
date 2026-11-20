package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
 * <b>Where a curse lives</b> (Phase 6): in the entries of the {@link CurseData} attachment. They tick from one place,
 * {@link #tickAll}, called once per server tick for every entity that has curses ({@link CurseEvents}). The curse's mob
 * effect is only the display; targets that refuse every effect (Wither, Ender Dragon, bosses of other mods) are cursed the
 * same way, automatically and without a list. Entities in {@code #blightcaller:curse_immune} get no curses at all.
 * <p>
 * Rules (concept 3.1):
 * <ul>
 *     <li>Another caster, same curse: an entry of their own, ticking separately with their own damage and credit.</li>
 *     <li>Same caster, same curse: their entry is replaced (new snapshot, new critical roll, the later end time of old
 *     and new). The tick rhythm stays.</li>
 *     <li>Curse level: the number of different curses ({@code #blightcaller:curses}) with entries on the target, from
 *     anyone, at most {@value #MAX_CURSE_LEVEL}.</li>
 *     <li>No catching up: an overdue entry ticks once and then keeps a new rhythm from now; entries that missed their
 *     end are dropped without a tick.</li>
 *     <li>Broken last interval: if the end falls between two ticks, the curse ticks once more at its end with the share
 *     remaining time / tick interval of the damage. So every percent of Curse Duration adds exactly as much damage.</li>
 *     <li>Removing the display effect (milk, totem, {@code /effect clear}) removes the curse ({@link CurseEvents}).</li>
 * </ul>
 * After every change {@link #finishChange} brings the attribute modifiers ({@link CurseModifiers}) and the client view
 * ({@link CurseView}) in line with the entries, and drops the attachment once it is empty.
 */
public final class Curses {
    public static final int MAX_CURSE_LEVEL = 3;

    /** Game time a curse was first put on a target, for the client view only (not saved: after a restart it is the load time). */
    private static final Map<UUID, Map<ResourceLocation, Long>> STARTS = new HashMap<>();

    private Curses() {}

    // ------------------------------------------------------------------------------------------------ queries

    public static boolean isCurse(Holder<MobEffect> effect) {
        return effect.is(CurseEffects.CURSES);
    }

    /** Entities in {@code #blightcaller:curse_immune} never get a curse. */
    public static boolean isImmune(LivingEntity entity) {
        return entity.getType().is(CurseEffects.CURSE_IMMUNE);
    }

    /** Whether a curse deals damage over time (all but curses like Enfeeble, see {@link CurseEffect#dealsDamage()}). */
    public static boolean dealsDamage(MobEffect effect) {
        return !(effect instanceof CurseEffect curse) || curse.dealsDamage();
    }

    /** Number of different curses on the entity, capped at {@value #MAX_CURSE_LEVEL}. Server side. */
    public static int curseLevel(LivingEntity entity) {
        return Math.min(MAX_CURSE_LEVEL, curseCount(entity));
    }

    /** Number of different curses with entries on the entity, not capped. Server side. */
    public static int curseCount(LivingEntity entity) {
        return data(entity).map(CurseData::curseCount).orElse(0);
    }

    /** Whether the entity has at least one entry of this curse. */
    public static boolean hasCurse(LivingEntity entity, Holder<MobEffect> curse) {
        return !entries(entity, curse).isEmpty();
    }

    /** Whether the entity has at least one entry of this curse effect. */
    public static boolean hasEntries(LivingEntity entity, MobEffect effect) {
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id != null && data(entity).map(d -> !d.entries(id).isEmpty()).orElse(false);
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

    /** Strength of a curse on the entity (0 without entries). */
    public static int amplifier(LivingEntity entity, Holder<MobEffect> curse) {
        ResourceLocation id = id(curse);
        return data(entity).map(d -> d.amplifier(id)).orElse(0);
    }

    /** Game time at which the longest entry of the curse ends, or the current time if there is none. */
    public static long latestEnd(LivingEntity entity, Holder<MobEffect> curse) {
        long latest = entity.level().getGameTime();
        for (CurseEntry entry : entries(entity, curse)) {
            latest = Math.max(latest, entry.expires());
        }
        return latest;
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
     * Puts a curse on a target (server side). Creates or replaces the caster's entry, sets the curse's strength and shows
     * the curse effect for the longest remaining duration of all entries if the target takes it. Returns the new entry, or
     * {@code null} if the target cannot have it (not a curse, dead, client side, in {@code #blightcaller:curse_immune},
     * zero duration). A target that refuses the effect is cursed all the same.
     */
    @Nullable
    public static CurseEntry apply(LivingEntity target, CurseApplication app) {
        if (!(target.level() instanceof ServerLevel level) || target.isDeadOrDying() || !isCurse(app.curse()) || isImmune(target)) {
            return null;
        }
        LivingEntity casterEntity = CurseDamage.resolveCaster(level, app.caster());
        double duration = finalDuration(app, casterEntity);
        if (duration < 1.0) {
            return null;
        }
        long now = level.getGameTime();
        int interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
        ResourceLocation id = id(app.curse());
        CurseData data = target.getData(CurseData.TYPE);
        dropMissingDisplays(target, data);
        List<CurseEntry> entries = data.entriesForWrite(id);
        boolean fresh = entries.isEmpty();

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

        // strength: the spell's amplifier (or the curse's default for this target); the strongest one counts
        int requested = app.amplifier() >= 0 ? app.amplifier()
                : app.curse().value() instanceof CurseEffect effect ? effect.defaultAmplifier(target) : 0;
        int amplifier = fresh ? requested : Math.max(data.amplifier(id), requested);
        data.setAmplifier(id, amplifier);
        if (fresh) {
            STARTS.computeIfAbsent(target.getUUID(), k -> new HashMap<>()).put(id, now);
        }
        data.setShown(id, showEffect(target, app.curse(), entries, now, amplifier, casterEntity));
        finishChange(target, data);
        return entry;
    }

    /**
     * Shows the curse effect for the longest remaining duration of its entries, plus one tick (the effect then always
     * outlives the last damage tick), with the curse's amplifier. Returns whether the target holds the effect now; targets
     * that refuse effects return {@code false} and are cursed without it.
     */
    static boolean showEffect(LivingEntity target, Holder<MobEffect> curse, List<CurseEntry> entries, long now, int amplifier,
                              @Nullable LivingEntity source) {
        long latest = now;
        for (CurseEntry entry : entries) {
            latest = Math.max(latest, entry.expires());
        }
        int wanted = (int) Math.min(Integer.MAX_VALUE - 1, latest - now) + 1;
        MobEffectInstance current = target.getEffect(curse);
        if (current == null) {
            return target.addEffect(instance(curse, wanted, amplifier), source) && target.hasEffect(curse);
        }
        if (current.isInfiniteDuration()) {
            return true;
        }
        if (current.getAmplifier() == amplifier && current.getDuration() < wanted) {
            target.addEffect(instance(curse, wanted, amplifier), source);
        } else if (current.getAmplifier() != amplifier || current.getDuration() > wanted) {
            // other strength, or a longer effect from elsewhere (e.g. /effect give): set it to the entries, no remove event
            target.forceAddEffect(instance(curse, wanted, amplifier), source);
        }
        return target.hasEffect(curse);
    }

    private static MobEffectInstance instance(Holder<MobEffect> curse, int duration, int amplifier) {
        return new MobEffectInstance(curse, duration, amplifier, false, false, true);
    }

    // ------------------------------------------------------------------------------------------------ tick

    /**
     * One game tick of all curses on an entity (server side). The only place where curse entries tick: damage, the
     * broken last tick, the end of entries, Pestilence spreading and Gravebind in the air.
     */
    static void tickAll(LivingEntity entity) {
        Optional<CurseData> optional = data(entity);
        if (optional.isEmpty() || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        CurseData data = optional.get();
        boolean changed = dropMissingDisplays(entity, data);
        long now = entity.level().getGameTime();
        int interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
        boolean pestilence = false;
        for (ResourceLocation id : List.copyOf(data.all().keySet())) {
            List<CurseEntry> entries = data.entries(id);
            if (entries.isEmpty()) {
                continue;
            }
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
            if (holder.isEmpty() || !isCurse(holder.get())) {
                data.remove(id);
                changed = true;
                continue;
            }
            try {
                changed |= tickCurse(entity, holder.get().value(), entries, now, interval);
                pestilence |= Pestilence.isPestilence(holder.get()) && !entries.isEmpty();
            } catch (Exception e) {
                // end the curse instead of failing again on every tick
                Blightcaller.LOGGER.error("Blightcaller: curse tick of {} on {} failed, curse removed", id, entity, e);
                data.remove(id);
                changed = true;
            }
        }
        if (pestilence && entity.isAlive()) {
            Pestilence.spread(entity);
        }
        GravebindFlight.tick(entity, data);
        if (changed) {
            finishChange(entity, data);
        }
    }

    /** Settles the due entries of one curse. Returns whether an entry ended (the curse's end time may have changed). */
    private static boolean tickCurse(LivingEntity entity, MobEffect effect, List<CurseEntry> entries, long now, int interval) {
        boolean changed = false;
        // curses without damage (Enfeeble) keep their rhythm and tick count, but never hit
        boolean damaging = dealsDamage(effect);
        // a copy: a tick may kill the target, and death handlers may read or change the entries
        for (CurseEntry entry : List.copyOf(entries)) {
            if (now > entry.expires()) {
                // missed its end (chunk did not tick, server hung): dropped without catching up
                entries.remove(entry);
                changed = true;
                continue;
            }
            double share = 0.0;
            boolean full = false;
            if (now >= entry.nextTick && entry.nextTick <= entry.end) {
                share = 1.0;
                full = true;
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
                // number of this tick on the target (Deepening Rot): the broken last tick alone counts as the next one
                CurseDamage.tick(entity, entry, share, full ? entry.ticks : entry.ticks + 1);
            }
            if (ends) {
                entries.remove(entry);
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------------------------------------ remove entries

    /**
     * Removes the entries of one caster from one curse on the target (Last Rites). Entries of other casters stay; the
     * effect goes only when no entry is left, otherwise it is shortened to the longest remaining entry. Returns the
     * removed entries.
     */
    public static List<CurseEntry> removeEntries(LivingEntity target, Holder<MobEffect> curse, @Nullable UUID caster) {
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
        boolean shown = data.shown(id);
        if (entries.isEmpty()) {
            data.remove(id);
            finishChange(target, data);
            if (shown) {
                target.removeEffect(curse);
            }
        } else {
            if (shown) {
                data.setShown(id, showEffect(target, curse, entries, level.getGameTime(), data.amplifier(id), null));
            }
            finishChange(target, data);
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

    /** Removes all entries of one curse (its display effect was removed: milk, totem, clear). */
    static void dropEntries(LivingEntity entity, Holder<MobEffect> curse) {
        data(entity).ifPresent(data -> {
            if (data.all().containsKey(id(curse))) {
                data.remove(id(curse));
                finishChange(entity, data);
            }
        });
    }

    /**
     * The display effect ran out while entries were still running (something shortened it): the curse goes on without
     * the effect, like on a target that refuses effects.
     */
    static void displayExpired(LivingEntity entity, Holder<MobEffect> curse) {
        data(entity).ifPresent(data -> {
            ResourceLocation id = id(curse);
            if (data.shown(id)) {
                data.setShown(id, false);
            }
        });
    }

    /**
     * Removes the curses of an entity (all, or only one): the entries and the display effects. Returns the number of
     * curses removed.
     */
    public static int clear(LivingEntity entity, @Nullable Holder<MobEffect> only) {
        List<Holder<MobEffect>> curses = new ArrayList<>();
        data(entity).ifPresent(data -> {
            for (ResourceLocation id : data.all().keySet()) {
                BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(curses::add);
            }
        });
        for (Holder<MobEffect> effect : entity.getActiveEffectsMap().keySet()) {
            if (isCurse(effect) && curses.stream().noneMatch(c -> id(c).equals(id(effect)))) {
                curses.add(effect);
            }
        }
        int removed = 0;
        Optional<CurseData> data = data(entity);
        for (Holder<MobEffect> curse : curses) {
            if (only != null && !id(curse).equals(id(only))) {
                continue;
            }
            data.ifPresent(d -> d.remove(id(curse)));
            entity.removeEffect(curse);
            removed++;
        }
        data.ifPresent(d -> finishChange(entity, d));
        return removed;
    }

    /**
     * Drops the entries of curses whose display effect is gone without an event (a mod calling
     * {@code removeEffectNoUpdate}), as if it had been removed. Curses that were never shown are not touched. Returns
     * whether something was dropped.
     */
    private static boolean dropMissingDisplays(LivingEntity entity, CurseData data) {
        boolean dropped = false;
        for (ResourceLocation id : List.copyOf(data.all().keySet())) {
            if (!data.shown(id)) {
                continue;
            }
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
            if (holder.isEmpty() || !entity.hasEffect(holder.get())) {
                data.remove(id);
                dropped = true;
            }
        }
        return dropped;
    }

    /**
     * After every change of the curses of an entity: drops empty curses, brings the attribute modifiers and the client
     * view in line with the entries, and removes the attachment once nothing is left.
     */
    static void finishChange(LivingEntity entity, CurseData data) {
        data.compact();
        if (entity.isDeadOrDying()) {
            // a dying target keeps its entries (Soul Migration reads them), but never its modifiers
            CurseModifiers.removeAll(entity);
        } else {
            CurseModifiers.sync(entity, data);
        }
        syncView(entity, data);
        if (data.isEmpty()) {
            STARTS.remove(entity.getUUID());
            if (entity.hasData(CurseData.TYPE)) {
                entity.removeData(CurseData.TYPE);
            }
        }
    }

    /** Rebuilds the client view of the curses of an entity and sends it if it changed (server side). */
    static void syncView(LivingEntity entity, @Nullable CurseData data) {
        if (entity.level().isClientSide()) {
            return;
        }
        List<CurseView.Line> lines = new ArrayList<>();
        CurseView old = entity.getExistingData(CurseView.type()).orElse(null);
        if (data != null) {
            long now = entity.level().getGameTime();
            Map<ResourceLocation, Long> starts = STARTS.get(entity.getUUID());
            for (Map.Entry<ResourceLocation, List<CurseEntry>> e : data.all().entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(e.getKey());
                if (holder.isEmpty()) {
                    continue;
                }
                long end = now;
                CurseView.Line oldLine = old == null ? null : old.line(holder.get());
                List<CurseView.Caster> casters = new ArrayList<>();
                for (CurseEntry entry : e.getValue()) {
                    end = Math.max(end, entry.expires());
                    // when this caster's entry was put on: kept from the last view while its end stays the same (the ring of
                    // the curse icons runs from there to the end), now for new and refreshed entries
                    // a curse that moved here (Soul Migration) keeps the ring of its old target
                    long since = entry.viewSince >= 0 ? entry.viewSince : now;
                    if (oldLine != null && entry.viewSince < 0) {
                        for (CurseView.Caster c : oldLine.casters()) {
                            if (c.end() == entry.expires() && Objects.equals(c.caster().orElse(null), entry.caster)) {
                                since = c.since();
                                break;
                            }
                        }
                    }
                    casters.add(new CurseView.Caster(Optional.ofNullable(entry.caster), since, entry.expires()));
                }
                long start = starts == null ? now : starts.getOrDefault(e.getKey(), now);
                lines.add(new CurseView.Line(holder.get(), data.amplifier(e.getKey()), start, end, List.copyOf(casters)));
            }
        }
        if (lines.isEmpty()) {
            if (entity.hasData(CurseView.type())) {
                entity.removeData(CurseView.type());
            }
            return;
        }
        CurseView view = new CurseView(List.copyOf(lines));
        if (!view.equals(entity.getExistingData(CurseView.type()).orElse(null))) {
            entity.setData(CurseView.type(), view);
        }
    }

    /** After loading an entity: modifiers and view from the saved entries (modifiers are never saved). */
    static void onLoaded(LivingEntity entity) {
        Optional<CurseData> data = data(entity);
        // also clears stale permanent modifiers saved by versions before Phase 6
        CurseModifiers.sync(entity, data.orElse(null));
        data.ifPresent(d -> {
            STARTS.computeIfAbsent(entity.getUUID(), k -> new HashMap<>());
            finishChange(entity, d);
        });
    }

    /** The entity left the level for good (died or discarded): forget its start times. */
    static void forget(LivingEntity entity) {
        STARTS.remove(entity.getUUID());
    }

    public static ResourceLocation id(Holder<MobEffect> effect) {
        return effect.unwrapKey().map(ResourceKey::location)
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(effect.value()));
    }
}
