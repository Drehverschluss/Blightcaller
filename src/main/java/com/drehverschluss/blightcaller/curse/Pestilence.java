package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Spreading of Pestilence (concept 5, decided 20.11.): every {@code spread_interval} ticks each carrier passes a copy of
 * its entry to the nearest enemy within {@code spread_radius} blocks (line of sight) that has no Pestilence yet. The old
 * carrier keeps its curse. The copy keeps power snapshot, critical roll and the remaining time of its source, and is marked
 * {@code transferred} (never extended by Curse Duration again). Damage always counts for the original caster.
 * <p>
 * <b>Carrier budget per cast:</b> every cast starts a chain with its own id ({@link CurseEntry#chain}); all copies of that
 * cast share it. A chain allows at most {@code max_carriers} carriers, the first target included (default 4), plus the
 * amplifier of the Pestilence status effect impact of the spell, including {@code effect_amplifier_add} of the caster's
 * modifier spells. So the skill tree raises it per player ("more carriers") with a modifier spell, without code. Casting
 * again starts a new chain with a budget of its own.
 * <p>
 * Chains live in memory only (server side). After a restart, entries of old chains keep ticking but no longer spread.
 * <p>
 * <b>Targets:</b> only enemies, with the same rules as Blight Burst (Spell Engine's area relations for the caster); never
 * players, never tamed pets, never the caster. If the caster is not online, only monsters ({@link Enemy}).
 */
public final class Pestilence {
    /** Height of the arc drawn from the old to the new carrier, in blocks. */
    private static final double ARC_HEIGHT = 1.2;
    private static final int ARC_POINTS = 16;
    /** Bright yellow green: dust is drawn darker than its colour (the olive of the curse looked brown). */
    private static final Vector3f ARC_COLOR = new Vector3f(0.85F, 1.0F, 0.3F);

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();

    /** The carrier budget of one cast. */
    public static final class Chain {
        public final int max;
        public int carriers;
        double expires;

        Chain(int max) {
            this.max = max;
            this.carriers = 1;
        }
    }

    private Pestilence() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Pestilence::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> CHAINS.clear());
    }

    public static boolean isPestilence(Holder<MobEffect> curse) {
        return curse.value() instanceof PestilenceEffect;
    }

    /** The chain of a chain id, if it still exists (read only, for tests and Jade). */
    public static Optional<Chain> chain(@Nullable UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(CHAINS.get(id));
    }

    /**
     * Carriers a cast may reach: {@code max_carriers} from the config plus the amplifier of the Pestilence status effect
     * impact and {@code effect_amplifier_add} of the caster's modifier spells for that impact.
     */
    public static int maxCarriers(@Nullable LivingEntity caster, @Nullable Holder<Spell> spell, @Nullable Spell.Impact statusImpact,
                                  @Nullable Spell.Modifier chargeModifier) {
        int extra = 0;
        if (statusImpact != null && statusImpact.action != null && statusImpact.action.status_effect != null) {
            extra += statusImpact.action.status_effect.amplifier;
            if (caster != null && spell != null) {
                for (Spell.Modifier modifier : SpellModifiers.ofImpact(caster, spell, statusImpact, chargeModifier)) {
                    extra += modifier.effect_amplifier_add;
                }
            }
        }
        return Math.max(1, BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_MAX_CARRIERS) + extra);
    }

    /** A new chain for a fresh cast. Returns the application with its chain id. */
    public static CurseApplication startChain(CurseApplication app, int maxCarriers) {
        UUID id = UUID.randomUUID();
        CHAINS.put(id, new Chain(maxCarriers));
        return app.withChain(id);
    }

    /** A Pestilence entry moved to a new carrier (Soul Migration): no new place in its chain, it spreads on from there. */
    static void afterMigration(LivingEntity target, CurseEntry entry) {
        entry.nextSpread = target.level().getGameTime() + interval();
        Chain chain = entry.chain == null ? null : CHAINS.get(entry.chain);
        if (chain != null) {
            chain.expires = Math.max(chain.expires, entry.end);
        }
    }

    /** After a cast: first spreading attempt after {@code first_spread} ticks; a failed cast drops its chain again. */
    public static void afterCast(LivingEntity target, CurseApplication app, @Nullable CurseEntry entry) {
        if (app.chain() == null) {
            return;
        }
        if (entry == null) {
            CHAINS.remove(app.chain());
            return;
        }
        entry.nextSpread = target.level().getGameTime() + BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_FIRST_SPREAD);
        Chain chain = CHAINS.get(app.chain());
        if (chain != null) {
            chain.expires = Math.max(chain.expires, entry.end);
        }
    }

    // ------------------------------------------------------------------------------------------------ spreading

    /** One game tick of the spreading on a carrier (server side, after its damage tick). */
    static void spread(LivingEntity carrier) {
        if (!(carrier.level() instanceof ServerLevel level) || !carrier.isAlive()) {
            return;
        }
        Holder<MobEffect> curse = CurseEffects.PESTILENCE;
        List<CurseEntry> entries = Curses.entries(carrier, curse);
        if (entries.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        int interval = interval();
        for (CurseEntry entry : entries) {
            try {
                if (entry.chain == null) {
                    continue;
                }
                if (entry.nextSpread <= 0) {
                    entry.nextSpread = now + interval;
                    continue;
                }
                if (now < entry.nextSpread) {
                    continue;
                }
                entry.nextSpread = now + interval;
                Chain chain = CHAINS.get(entry.chain);
                if (chain == null || chain.carriers >= chain.max) {
                    continue;
                }
                double remaining = entry.end - now;
                if (remaining < 1.0) {
                    continue;
                }
                LivingEntity caster = CurseDamage.resolveCaster(level, entry.caster);
                LivingEntity target = findTarget(level, carrier, caster, entry.caster);
                if (target == null) {
                    continue;
                }
                CurseApplication copy = new CurseApplication(curse, entry.caster, entry.power, entry.critical, entry.criticalDamage,
                        entry.factor, remaining, false, entry.jumps, entry.chain, true, Curses.amplifier(carrier, curse));
                CurseEntry placed = Curses.apply(target, copy);
                if (placed == null) {
                    continue;
                }
                placed.nextSpread = now + interval;
                chain.carriers++;
                chain.expires = Math.max(chain.expires, placed.end);
                effects(level, carrier, target);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: Pestilence spreading from {} failed", carrier, e);
            }
        }
    }

    /** Nearest valid new carrier within the radius (monsters first, phase 7A), or {@code null}. */
    @Nullable
    static LivingEntity findTarget(ServerLevel level, LivingEntity carrier, @Nullable LivingEntity caster, @Nullable UUID casterId) {
        double radius = BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_SPREAD_RADIUS);
        return SkillMarkers.nearest(level, carrier, radius, caster, casterId, e -> !Curses.hasCurse(e, CurseEffects.PESTILENCE));
    }

    /** Whether an entity may become a new carrier: an enemy of the caster without Pestilence. */
    public static boolean valid(LivingEntity e, @Nullable LivingEntity caster, @Nullable UUID casterId) {
        return validTarget(e, caster, casterId) && !Curses.hasCurse(e, CurseEffects.PESTILENCE);
    }

    /**
     * The target rules of Pestilence, Soul Migration and Spreading Rot: alive, attackable, no player, no armor stand, no tamed
     * pet, not the caster, not curse immune, and Spell Engine's area relations for the caster (only monsters if offline).
     */
    public static boolean validTarget(LivingEntity e, @Nullable LivingEntity caster, @Nullable UUID casterId) {
        if (!e.isAlive() || e.isDeadOrDying() || e.isSpectator() || !e.isAttackable() || e instanceof Player || e instanceof ArmorStand) {
            return false;
        }
        if (casterId != null && casterId.equals(e.getUUID())) {
            return false;
        }
        if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return false;
        }
        if (Curses.isImmune(e)) {
            return false;
        }
        if (caster != null) {
            return EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL, caster, e);
        }
        return e instanceof Enemy;
    }

    /** A short arc of sickly spores from the old to the new carrier, a puff at the new one and a wet sound. */
    private static void effects(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 a = from.position().add(0.0, from.getBbHeight() * 0.6, 0.0);
        Vec3 b = to.position().add(0.0, to.getBbHeight() * 0.6, 0.0);
        DustParticleOptions dust = new DustParticleOptions(ARC_COLOR, 1.6F);
        for (int i = 0; i <= ARC_POINTS; i++) {
            double t = i / (double) ARC_POINTS;
            Vec3 p = a.lerp(b, t).add(0.0, ARC_HEIGHT * 4.0 * t * (1.0 - t), 0.0);
            level.sendParticles(dust, p.x, p.y, p.z, 2, 0.04, 0.04, 0.04, 0.0);
        }
        level.sendParticles(ParticleTypes.SNEEZE, b.x, b.y, b.z, 10, 0.25, 0.3, 0.25, 0.02);
        level.playSound(null, b.x, b.y, b.z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private static int interval() {
        return BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_SPREAD_INTERVAL);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (CHAINS.isEmpty() || event.getServer().getTickCount() % 200 != 0) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        CHAINS.values().removeIf(chain -> chain.expires > 0.0 && chain.expires + 20.0 < now);
    }
}
