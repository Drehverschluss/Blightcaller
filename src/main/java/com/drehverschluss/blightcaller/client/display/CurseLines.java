package com.drehverschluss.blightcaller.client.display;

import com.drehverschluss.blightcaller.curse.CurseView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The curses of a mob as the curse display shows them to one player, from the synced curse view: per curse whether the
 * player has an own entry, and the end and start of the entry shown (the own one, otherwise the longest, like Jade).
 */
final class CurseLines {
    /**
     * One curse as shown.
     *
     * @param since game time the shown entry was put on (start of the ring)
     * @param end   game time it ends
     */
    record Shown(Holder<MobEffect> curse, boolean own, long since, long end) {
        /** Remaining share of the entry (1 = just put on, 0 = over). */
        float fraction(float now) {
            float total = end - since;
            return total <= 0.0F ? 0.0F : Mth.clamp((end - now) / total, 0.0F, 1.0F);
        }
    }

    private CurseLines() {}

    /** The synced view of a mob, or {@code null} if it has no curses. */
    @Nullable
    static CurseView view(LivingEntity entity) {
        CurseView view = CurseView.of(entity).orElse(null);
        return view == null || view.lines().isEmpty() ? null : view;
    }

    /** Curse level as the core counts it: different curses, at most 3. */
    static int level(CurseView view) {
        return Math.min(3, view.lines().size());
    }

    /** Whether the player has an own entry of any curse on the mob. */
    static boolean hasOwn(CurseView view, @Nullable UUID player) {
        for (CurseView.Line line : view.lines()) {
            if (line.of(player) != null) {
                return true;
            }
        }
        return false;
    }

    /** The curses still running, in a fixed order (by id). */
    static List<Shown> shown(CurseView view, @Nullable UUID player, long now) {
        List<Shown> out = new ArrayList<>(view.lines().size());
        for (CurseView.Line line : view.lines()) {
            CurseView.Caster own = line.of(player);
            CurseView.Caster shown = own != null ? own : line.longest();
            if (shown.end() > now) {
                out.add(new Shown(line.curse(), own != null, shown.since(), shown.end()));
            }
        }
        out.sort(Comparator.comparing(s -> s.curse().unwrapKey().map(k -> k.location().toString()).orElse("")));
        return out;
    }
}
