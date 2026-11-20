package com.drehverschluss.blightcaller.curse;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Deepening Rot / Tiefe Fäule (skill tree, passive 1 of side B): the caster's curses with damage tick stronger on the same
 * target with every tick. The first tick deals its normal damage, every further one {@code per tick} more, up to {@code max}
 * (marker {@code blightcaller:deepening_rot/<per tick>/<max>} in the passive spell {@code blightcaller:deepening_rot},
 * default 0.05 / 0.5).
 * <ul>
 *     <li>The count is the tick counter of the caster's entry ({@link CurseEntry#ticks}): two casters count separately, a
 *     refresh by the same caster keeps it, an entry that ran out starts at 0 again, and a copy on a new target (Pestilence,
 *     Soul Migration, Spreading Rot) is a new entry and starts at 0.</li>
 *     <li>Checked on every tick: the caster must be online and have the passive (a respec takes effect at once). Offline casters
 *     deal their curses without the bonus, like they deal them without credit.</li>
 * </ul>
 */
public final class DeepeningRot {
    private DeepeningRot() {}

    /** Extra damage of the {@code number}-th tick (1 = the first) of this caster's curse, as a fraction (0.3 = +30 %). */
    public static double bonus(@Nullable LivingEntity caster, int number) {
        if (number <= 1 || !(caster instanceof Player player)) {
            return 0.0;
        }
        double[] values = SkillMarkers.passiveValues(player, SkillMarkers.DEEPENING_ROT);
        if (values == null || values.length < 2) {
            return 0.0;
        }
        return Math.min(values[1], values[0] * (number - 1));
    }

    /** The bonus the next tick of this entry would get (for Jade), 0 without the passive. */
    public static double nextBonus(@Nullable LivingEntity caster, CurseEntry entry) {
        return bonus(caster, entry.ticks + 1);
    }

    /** The bonus of the last tick this entry dealt (for Jade), 0 without the passive. */
    public static double currentBonus(@Nullable LivingEntity caster, CurseEntry entry) {
        return bonus(caster, entry.ticks);
    }
}
