package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Soul Migration / Seelenwanderung (skill tree, passive 1 of side A; concept 3.1 and 7): when a target with curses of a caster
 * dies, all of that caster's curses on it move on to enemies within {@code range} blocks with a line of sight: each curse to the
 * nearest one for which it brings something (phase 7B).
 * <ul>
 *     <li>Unlocked by the marker {@code blightcaller:soul_migration} in one of the caster's passive spells (the node's spell
 *     {@code blightcaller:soul_migration}), or for everyone with {@code [soul_migration] always_on}.</li>
 *     <li>Every curse of that caster moves, also those without damage, with its remaining time, power snapshot, critical
 *     roll and strength, marked {@code transferred} (never extended by Curse Duration again). Curses detonated by Last Rites
 *     are gone before the hit and do not move. Caster offline: nothing moves.</li>
 *     <li>The new target already has the same curse from the same caster: the longer remaining time counts, nothing adds up.</li>
 *     <li>At most {@code max_migrations} moves per curse (its jump counter). Pestilence keeps its chain: a moving Pestilence
 *     takes no new place and only spreads on while its chain is not full.</li>
 *     <li>The death is only noted in {@code LivingDeathEvent} (lowest priority, the entries are still on the dying target);
 *     the curses move in the next server tick. The ring of the curse icons runs on where it was.</li>
 *     <li>Target rules like Pestilence ({@link Pestilence#validTarget}), monsters first; targets that refuse effects (Wither)
 *     are cursed all the same.</li>
 * </ul>
 * The souls flying to the new target are a placeholder until the graphics phase.
 */
public final class SoulMigration {
    /** Ticks a soul needs from the dead to the new target. */
    private static final int FLIGHT_TICKS = 10;

    /** One curse entry to move. */
    private record Moving(Holder<MobEffect> curse, CurseEntry entry, int amplifier, long since) {}

    /** The curses of one caster on one dead target. */
    private record Death(ServerLevel level, LivingEntity dead, Vec3 from, @Nullable UUID caster, List<Moving> curses, long tick) {}

    private record Flight(ServerLevel level, Vec3 from, LivingEntity to, int[] age) {}

    private static final List<Death> PENDING = new ArrayList<>();
    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private SoulMigration() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, SoulMigration::onDeath);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SoulMigration::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> {
            PENDING.clear();
            FLIGHTS.clear();
        });
    }

    /** Whether curses of this caster move on: the passive, or always_on. */
    public static boolean enabledFor(@Nullable LivingEntity caster) {
        if (caster == null) {
            return false;
        }
        if (BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_ALWAYS_ON)) {
            return true;
        }
        return caster instanceof Player player && SkillMarkers.hasPassive(player, SkillMarkers.SOUL_MIGRATION);
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        try {
            if (event.isCanceled() || !(dead.level() instanceof ServerLevel level) || !dead.hasData(CurseData.TYPE)) {
                return;
            }
            Optional<CurseData> optional = Curses.data(dead);
            if (optional.isEmpty()) {
                return;
            }
            CurseData data = optional.get();
            long now = level.getGameTime();
            int maxJumps = BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_MAX_JUMPS);
            CurseView view = dead.getExistingData(CurseView.type()).orElse(null);
            List<Death> deaths = new ArrayList<>();
            for (var e : data.all().entrySet()) {
                Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(e.getKey());
                if (holder.isEmpty() || !Curses.isCurse(holder.get())) {
                    continue;
                }
                for (CurseEntry entry : e.getValue()) {
                    if (entry.caster == null || entry.end - now < 1.0) {
                        continue;
                    }
                    LivingEntity caster = CurseDamage.resolveCaster(level, entry.caster);
                    if (!enabledFor(caster)) {
                        continue;
                    }
                    if (entry.jumps >= maxJumps) {
                        continue;
                    }
                    Death death = deaths.stream().filter(d -> entry.caster.equals(d.caster())).findFirst().orElse(null);
                    if (death == null) {
                        death = new Death(level, dead, dead.position().add(0.0, dead.getBbHeight() * 0.6, 0.0), entry.caster,
                                new ArrayList<>(), now);
                        deaths.add(death);
                    }
                    death.curses().add(new Moving(holder.get(), copy(entry), data.amplifier(e.getKey()), since(view, holder.get(), entry, now)));
                }
            }
            PENDING.addAll(deaths);
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: Soul Migration from {} failed", dead, e);
        }
    }

    /** When the ring of this entry started on the dead target (from the client view), so it runs on where it was. */
    private static long since(@Nullable CurseView view, Holder<MobEffect> curse, CurseEntry entry, long now) {
        if (view != null) {
            CurseView.Line line = view.line(curse);
            if (line != null) {
                for (CurseView.Caster c : line.casters()) {
                    if (c.caster().isPresent() && c.caster().get().equals(entry.caster)) {
                        return c.since();
                    }
                }
            }
        }
        return now;
    }

    private static CurseEntry copy(CurseEntry e) {
        CurseEntry out = new CurseEntry(e.caster, e.power, e.factor, e.critical, e.criticalDamage, e.nextTick, e.end, e.jumps, e.chain,
                e.ticks, e.transferred);
        out.enervation = e.enervation;
        return out;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            List<Death> due = new ArrayList<>();
            for (Iterator<Death> it = PENDING.iterator(); it.hasNext(); ) {
                Death d = it.next();
                if (d.level().getGameTime() > d.tick()) {
                    due.add(d);
                    it.remove();
                }
            }
            for (Death d : due) {
                try {
                    move(d);
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: Soul Migration from {} failed", d.dead(), e);
                }
            }
        }
        // Blightbloom after the curses moved (decided 20.11.: first migrate, then bloom)
        Blightbloom.process(event.getServer());
        if (!FLIGHTS.isEmpty()) {
            FLIGHTS.removeIf(SoulMigration::fly);
        }
    }

    /**
     * Moves the curses of one caster from a dead target (phase 7B): every curse on its own goes to the nearest valid enemy for which
     * it brings something, i.e. the caster's same curse is missing there or runs out sooner. A curse that brings nothing anywhere
     * stays behind. So the curses of one dead target may spread over several enemies; each of them gets its own arc of souls.
     */
    private static void move(Death d) {
        ServerLevel level = d.level();
        LivingEntity caster = CurseDamage.resolveCaster(level, d.caster());
        if (caster == null) {
            return; // went offline in between
        }
        double range = BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_RANGE);
        long now = level.getGameTime();
        List<LivingEntity> reached = new ArrayList<>();
        for (Moving m : d.curses()) {
            double remaining = m.entry().end - now;
            if (remaining < 1.0) {
                continue;
            }
            CurseEntry e = m.entry();
            LivingEntity target = SkillMarkers.nearest(level, d.dead(), range, caster, d.caster(),
                    candidate -> candidate.isAlive() && SkillMarkers.benefits(candidate, m.curse(), d.caster(), e.end));
            if (target == null) {
                continue; // brings nothing anywhere: it ends with the dead target
            }
            CurseEntry existing = SkillMarkers.own(target, m.curse(), d.caster());
            CurseApplication app = new CurseApplication(m.curse(), e.caster, e.power, e.critical, e.criticalDamage, e.factor, remaining,
                    false, e.jumps + 1, e.chain, true, m.amplifier());
            CurseEntry placed = Curses.apply(target, app);
            if (placed == null) {
                continue;
            }
            placed.enervation = Math.max(placed.enervation, e.enervation);
            if (existing == null) {
                placed.viewSince = m.since();
            }
            if (Pestilence.isPestilence(m.curse())) {
                Pestilence.afterMigration(target, placed);
            }
            if (!reached.contains(target)) {
                reached.add(target);
            }
        }
        for (LivingEntity target : reached) {
            Curses.data(target).ifPresent(data -> Curses.syncView(target, data));
            FLIGHTS.add(new Flight(level, d.from(), target, new int[]{0}));
        }
    }

    /** One tick of a flying soul (placeholder look): turquoise soul faces from the dead to the new target. Returns true when done. */
    private static boolean fly(Flight f) {
        int age = ++f.age()[0];
        LivingEntity to = f.to();
        if (!to.isAlive()) {
            return true;
        }
        Vec3 end = to.position().add(0.0, to.getBbHeight() * 0.6, 0.0);
        double t = Math.min(1.0, age / (double) FLIGHT_TICKS);
        Vec3 p = f.from().lerp(end, t).add(0.0, 1.0 * 4.0 * t * (1.0 - t), 0.0);
        f.level().sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
        if (age == 1) {
            f.level().playSound(null, f.from().x, f.from().y, f.from().z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        if (t >= 1.0) {
            f.level().sendParticles(ParticleTypes.SOUL, end.x, end.y, end.z, 6, 0.25, 0.3, 0.25, 0.02);
            f.level().playSound(null, end.x, end.y, end.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
            return true;
        }
        return false;
    }
}
