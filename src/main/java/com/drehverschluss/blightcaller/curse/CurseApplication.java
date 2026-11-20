package com.drehverschluss.blightcaller.curse;

import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

/**
 * Everything {@link Curses#apply} needs to put a curse on a target. All ways of cursing use it: the spell marker
 * ({@link CurseImpacts}), the command, and later Soul Migration and Pestilence (which pass the old entry's values with
 * {@code transferred = true}).
 *
 * @param curse          the curse effect (must be in {@code #blightcaller:curses})
 * @param caster         UUID of the caster, {@code null} for none
 * @param power          soul spell power snapshot with all modifiers
 * @param critical       result of the critical roll (rolled once, see {@link #rollCritical})
 * @param criticalDamage critical damage multiplier of the caster
 * @param factor         damage per tick = power x factor
 * @param durationTicks  duration before Curse Duration, in game ticks (fractions allowed, e.g. a remaining duration)
 * @param extend         apply the caster's Curse Duration bonus (ignored for transferred curses)
 * @param jumps          jump counter (Soul Migration)
 * @param chain          chain id (Pestilence)
 * @param transferred    moved from another target: keeps its duration, never extended again
 * @param amplifier      strength of the curse ({@link #DEFAULT_AMPLIFIER}: the curse's default for the target, see
 *                       {@link CurseEffect#defaultAmplifier}). Spells pass the amplifier of their status effect impact.
 */
public record CurseApplication(Holder<MobEffect> curse, @Nullable UUID caster, double power, boolean critical,
                               double criticalDamage, double factor, double durationTicks, boolean extend, int jumps,
                               @Nullable UUID chain, boolean transferred, int amplifier) {
    /** No amplifier given: the curse picks its default for the target. */
    public static final int DEFAULT_AMPLIFIER = -1;

    /** Without an amplifier (the curse's default for the target). */
    public CurseApplication(Holder<MobEffect> curse, @Nullable UUID caster, double power, boolean critical,
                            double criticalDamage, double factor, double durationTicks, boolean extend, int jumps,
                            @Nullable UUID chain, boolean transferred) {
        this(curse, caster, power, critical, criticalDamage, factor, durationTicks, extend, jumps, chain, transferred,
                DEFAULT_AMPLIFIER);
    }

    /** A fresh curse cast by {@code caster} (may be {@code null}) with the given power; the critical roll happens here. */
    public static CurseApplication cast(Holder<MobEffect> curse, @Nullable LivingEntity caster, SpellPower.Result power,
                                        double factor, double durationTicks, RandomSource random) {
        return new CurseApplication(curse, caster == null ? null : caster.getUUID(), power.baseValue(),
                rollCritical(power.criticalChance(), random), power.criticalDamage(), factor, durationTicks, true, 0, null, false);
    }

    /** One critical roll with the given chance (0..1, more than 1 always crits). */
    public static boolean rollCritical(double chance, RandomSource random) {
        return chance > 0.0 && random.nextDouble() < chance;
    }

    public CurseApplication withDuration(double ticks) {
        return new CurseApplication(curse, caster, power, critical, criticalDamage, factor, ticks, extend, jumps, chain,
                transferred, amplifier);
    }

    public CurseApplication withAmplifier(int value) {
        return new CurseApplication(curse, caster, power, critical, criticalDamage, factor, durationTicks, extend, jumps, chain,
                transferred, value);
    }

    public CurseApplication withChain(@Nullable UUID id) {
        return new CurseApplication(curse, caster, power, critical, criticalDamage, factor, durationTicks, extend, jumps, id,
                transferred, amplifier);
    }

    public CurseApplication asTransferred() {
        return new CurseApplication(curse, caster, power, critical, criticalDamage, factor, durationTicks, false, jumps, chain,
                true, amplifier);
    }
}
