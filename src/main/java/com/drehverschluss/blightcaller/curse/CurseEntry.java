package com.drehverschluss.blightcaller.curse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

/**
 * One caster's share of one curse on one target. Several casters on the same curse have one entry each and tick
 * separately; the same caster casting again replaces their entry ({@link Curses#apply}).
 * <p>
 * Times are {@code level.getGameTime()} values, so refreshing a curse never shifts its tick rhythm, and a server
 * restart continues where it stopped. Mutable on purpose: the tick updates {@link #nextTick} and {@link #ticks} in place.
 */
public final class CurseEntry {
    public static final Codec<CurseEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("caster").forGetter(e -> Optional.ofNullable(e.caster)),
            Codec.DOUBLE.fieldOf("power").forGetter(e -> e.power),
            Codec.DOUBLE.fieldOf("factor").forGetter(e -> e.factor),
            Codec.BOOL.optionalFieldOf("critical", false).forGetter(e -> e.critical),
            Codec.DOUBLE.optionalFieldOf("critical_damage", 1.0).forGetter(e -> e.criticalDamage),
            Codec.LONG.fieldOf("next_tick").forGetter(e -> e.nextTick),
            Codec.DOUBLE.fieldOf("end").forGetter(e -> e.end),
            Codec.INT.optionalFieldOf("jumps", 0).forGetter(e -> e.jumps),
            UUIDUtil.CODEC.optionalFieldOf("chain").forGetter(e -> Optional.ofNullable(e.chain)),
            Codec.INT.optionalFieldOf("ticks", 0).forGetter(e -> e.ticks),
            Codec.BOOL.optionalFieldOf("transferred", false).forGetter(e -> e.transferred),
            Codec.LONG.optionalFieldOf("next_spread", 0L).forGetter(e -> e.nextSpread),
            Codec.DOUBLE.optionalFieldOf("enervation", 0.0).forGetter(e -> e.enervation),
            Codec.DOUBLE.optionalFieldOf("full", 0.0).forGetter(e -> e.full)
    ).apply(i, (caster, power, factor, critical, criticalDamage, nextTick, end, jumps, chain, ticks, transferred, nextSpread,
                enervation, full) -> {
        CurseEntry entry = new CurseEntry(caster.orElse(null), power, factor, critical, criticalDamage, nextTick, end, jumps,
                chain.orElse(null), ticks, transferred);
        entry.nextSpread = nextSpread;
        entry.enervation = enervation;
        entry.full = full;
        return entry;
    }));

    /** UUID of the caster, {@code null} for curses without a caster (e.g. from a command block). */
    @Nullable
    public final UUID caster;
    /** Snapshot of the caster's soul spell power with all modifiers, taken when the curse was cast. */
    public final double power;
    /** Damage per tick = power x factor (from the spell, e.g. {@code blightcaller:curse/0.25}). */
    public final double factor;
    /** Critical roll, made once when the curse was cast. */
    public final boolean critical;
    /** Critical damage multiplier of the caster at that moment (applied only if {@link #critical}). */
    public final double criticalDamage;
    /** Game time of the next damage tick. */
    public long nextTick;
    /**
     * Exact game time at which the entry ends, with fractions of a game tick (4 s x 1.08 = 86.4 ticks). A full tick due
     * at or before this time still happens; if the end falls between two ticks, the last tick deals the matching
     * fraction of the damage ({@link Curses}). Only Lingering Rend moves it later ({@link Curses#extendOwn}).
     */
    public double end;
    /** Times this curse has moved to a new target (Soul Migration). */
    public final int jumps;
    /** Shared id of all copies made by one cast of Pestilence ({@link Pestilence}): their common carrier budget. */
    @Nullable
    public final UUID chain;
    /** Damage ticks this entry has dealt so far (Deepening Rot). Kept when the same caster refreshes. */
    public int ticks;
    /** Moved here from another target: keeps its remaining duration and is never extended by Curse Duration again. */
    public final boolean transferred;
    /** Game time of the next spreading attempt (Pestilence), 0 for curses that do not spread. */
    public long nextSpread;
    /**
     * Enervation (skill tree, Enfeeble): the target deals this much less damage while the entry lasts (0.1 = 10 %, bosses
     * half). Set by the marker {@code blightcaller:enervation/<fraction>} right after the curse; a refresh without it clears it.
     */
    public double enervation;
    /**
     * Full duration in game ticks the entry had when it was put on (after Curse Duration; for a moved or copied curse the
     * remaining time when it arrived; for a refresh the time left after the refresh). The cap for Lingering Rend
     * ({@link Curses#extendOwn}); 0 = unknown (saved before phase 9A), then never extended.
     */
    public double full;
    /**
     * Client view only, not saved: game time from which the ring of the curse icon runs for a curse that moved here (Soul
     * Migration keeps the ring where it was). -1 = none.
     */
    public long viewSince = -1;

    public CurseEntry(@Nullable UUID caster, double power, double factor, boolean critical, double criticalDamage,
                      long nextTick, double end, int jumps, @Nullable UUID chain, int ticks, boolean transferred) {
        this.caster = caster;
        this.power = power;
        this.factor = factor;
        this.critical = critical;
        this.criticalDamage = criticalDamage;
        this.nextTick = nextTick;
        this.end = end;
        this.jumps = jumps;
        this.chain = chain;
        this.ticks = ticks;
        this.transferred = transferred;
    }

    /** Game tick in which the entry ends (the final, possibly partial tick happens in this game tick). */
    public long expires() {
        return (long) Math.floor(end);
    }

    /** Same caster (both without a caster counts as the same). */
    public boolean sameCaster(@Nullable UUID other) {
        return caster == null ? other == null : caster.equals(other);
    }

    /** Damage per tick before the target's vulnerability: power x factor, x critical damage if critical. */
    public double baseDamage() {
        return power * factor * (critical ? criticalDamage : 1.0);
    }

    @Override
    public String toString() {
        return "CurseEntry{caster=" + caster + ", power=" + power + ", factor=" + factor + ", critical=" + critical
                + ", criticalDamage=" + criticalDamage + ", nextTick=" + nextTick + ", end=" + end + ", jumps=" + jumps
                + ", chain=" + chain + ", ticks=" + ticks + ", transferred=" + transferred + ", nextSpread=" + nextSpread
                + ", enervation=" + enervation + ", full=" + full + "}";
    }
}
