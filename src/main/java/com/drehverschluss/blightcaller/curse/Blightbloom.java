package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Blightbloom / Fäulnisblüte (skill tree, passive 2 of side A; concept 7, decided 20.11.): when an enemy with at least one curse of
 * a caster dies, it bursts and puts Soulburn on all enemies within the radius.
 * <ul>
 *     <li>Unlocked by the marker {@code blightcaller:blightbloom/<radius>/<seconds>/<factor>} in a passive spell of the caster
 *     (default {@code 3/4/0.1}: the Soulburn of Hex Bolt). The Soulburn is a fresh curse of the caster with the caster's soul power
 *     at the moment of the burst, its own critical roll and Curse Duration. Caster offline or without the passive: nothing.</li>
 *     <li><b>Chain reaction allowed:</b> an enemy that dies with such a Soulburn has a curse of the caster and bursts as well. There
 *     is no limit on the chain itself; it ends when no enemy is left in reach.</li>
 *     <li>Only technical limits: the death is noted in {@code LivingDeathEvent} (lowest priority) and the burst happens in a later
 *     server tick, after Soul Migration ({@link SoulMigration} calls {@link #process}); at most {@code [blightbloom] max_per_tick}
 *     bursts per server tick, the others wait for the next tick in the order of the deaths.</li>
 *     <li>Target rules like Pestilence ({@link Pestilence#validTarget}), no line of sight needed (like an area impact).</li>
 * </ul>
 * The burst of rot and soul shreds is a placeholder until the graphics phase.
 */
public final class Blightbloom {
    private static final Vector3f ROT_COLOR = new Vector3f(0.55F, 0.8F, 0.3F);
    private static final Vector3f DARK_COLOR = new Vector3f(0.35F, 0.2F, 0.5F);

    /** One burst to come: where, which caster, after which game tick. */
    private record Bloom(ServerLevel level, Vec3 at, UUID caster, UUID dead, long tick) {}

    private static final Deque<Bloom> PENDING = new ArrayDeque<>();

    private Blightbloom() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, Blightbloom::onDeath);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> PENDING.clear());
    }

    /** Radius, seconds and factor of the caster's Blightbloom, or {@code null} without it. */
    @Nullable
    public static double[] values(@Nullable LivingEntity caster) {
        if (!(caster instanceof Player player)) {
            return null;
        }
        double[] v = SkillMarkers.passiveValues(player, SkillMarkers.BLIGHTBLOOM);
        return v != null && v.length >= 3 ? v : null;
    }

    /** Bursts waiting for a later tick (for tests). */
    public static int pending() {
        return PENDING.size();
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        try {
            if (event.isCanceled() || !(dead.level() instanceof ServerLevel level) || !dead.hasData(CurseData.TYPE)) {
                return;
            }
            Optional<CurseData> data = Curses.data(dead);
            if (data.isEmpty()) {
                return;
            }
            long now = level.getGameTime();
            List<UUID> casters = new ArrayList<>();
            for (List<CurseEntry> entries : data.get().all().values()) {
                for (CurseEntry entry : entries) {
                    if (entry.caster != null && !casters.contains(entry.caster) && entry.end > now) {
                        casters.add(entry.caster);
                    }
                }
            }
            Vec3 at = dead.position().add(0.0, dead.getBbHeight() * 0.5, 0.0);
            for (UUID caster : casters) {
                // only casters with the passive right now (checked again at the burst)
                if (values(CurseDamage.resolveCaster(level, caster)) != null) {
                    PENDING.addLast(new Bloom(level, at, caster, dead.getUUID(), now));
                }
            }
        } catch (Exception e) {
            logError(dead, e);
        }
    }

    private static void logError(Object what, Exception e) {
        Blightcaller.LOGGER.error("Blightcaller: Blightbloom of {} failed", what, e);
    }

    /** Runs the due bursts, at most {@code max_per_tick} (called once per server tick after Soul Migration). */
    static void process(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int budget = BlightcallerConfig.get(BlightcallerConfig.BLIGHTBLOOM_MAX_PER_TICK);
        int done = 0;
        // deaths of this tick are queued behind the older ones; stop at the first one that is not due yet
        while (!PENDING.isEmpty() && done < budget) {
            Bloom bloom = PENDING.peekFirst();
            if (bloom.level().getGameTime() <= bloom.tick()) {
                break;
            }
            PENDING.pollFirst();
            done++;
            try {
                burst(bloom);
            } catch (Exception e) {
                logError(bloom.at(), e);
            }
        }
    }

    private static void burst(Bloom bloom) {
        ServerLevel level = bloom.level();
        LivingEntity caster = CurseDamage.resolveCaster(level, bloom.caster());
        double[] v = values(caster);
        if (caster == null || v == null) {
            return; // offline or passive gone meanwhile
        }
        double radius = v[0];
        int ticks = (int) Math.round(v[1] * 20.0);
        double factor = v[2];
        SpellPower.Result power = SpellPower.getSpellPower(SpellSchools.SOUL, caster);
        Vec3 at = bloom.at();
        AABB box = new AABB(at, at).inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !e.getUUID().equals(bloom.dead()) && e.getBoundingBox().getCenter().distanceTo(at) <= radius + e.getBbWidth() * 0.5
                        && Pestilence.validTarget(e, caster, bloom.caster()));
        int hit = 0;
        for (LivingEntity target : targets) {
            CurseApplication app = CurseApplication.cast(CurseEffects.SOULBURN, caster, power, factor, ticks, caster.getRandom());
            if (Curses.apply(target, app) != null) {
                hit++;
            }
        }
        effects(level, at, radius);
        Blightcaller.LOGGER.debug("[Blightcaller] Blightbloom of {} at {}: Soulburn on {} enemies", bloom.caster(), at, hit);
    }

    /** Placeholder burst: rot dust and dark spores in a ring, soul shreds rising, a wet burst sound. */
    private static void effects(ServerLevel level, Vec3 at, double radius) {
        DustParticleOptions rot = new DustParticleOptions(ROT_COLOR, 1.4F);
        DustParticleOptions dark = new DustParticleOptions(DARK_COLOR, 1.8F);
        int points = 20;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            for (double r = 0.6; r <= radius; r += radius / 3.0) {
                double x = at.x + Math.cos(a) * r;
                double z = at.z + Math.sin(a) * r;
                level.sendParticles(i % 2 == 0 ? rot : dark, x, at.y - 0.3, z, 1, 0.08, 0.15, 0.08, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.06);
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 10, 0.5, 0.3, 0.5, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.8F);
    }
}
