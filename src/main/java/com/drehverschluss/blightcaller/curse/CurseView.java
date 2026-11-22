package com.drehverschluss.blightcaller.curse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Synced attachment {@code blightcaller:curse_view}: what clients need to draw the curses on an entity, one line per curse:
 * the curse, its strength, when it was put on, when its longest entry ends (game time) and, since Phase 6.2, every caster
 * with the end of his entry and when it was last put on (for the curse icons: own curses bright, the ring runs down with
 * the remaining time). Never saved; the server rebuilds it whenever the curses of the entity change ({@link
 * Curses#syncView}), NeoForge sends it to every player tracking the entity and again when one starts tracking it.
 * <p>
 * Clients use it for curse particles on targets that refuse the display effect (bosses like the Wither), the ghostly
 * chains of Gravebind, the curse veil and icons, and anything else that must look the same for every player. It does not
 * change per tick.
 */
public record CurseView(List<Line> lines) {
    /**
     * One caster's entry of a curse.
     *
     * @param caster UUID of the caster, empty for curses without one (command blocks)
     * @param since  game time this entry was last put on or refreshed (start of its ring)
     * @param end    game time it ends
     */
    public record Caster(Optional<UUID> caster, long since, long end) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Caster> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Caster::caster,
                ByteBufCodecs.VAR_LONG, Caster::since,
                ByteBufCodecs.VAR_LONG, Caster::end,
                Caster::new);

        public boolean is(@Nullable UUID player) {
            return player != null && caster.isPresent() && caster.get().equals(player);
        }
    }

    /**
     * One curse on the entity.
     *
     * @param curse     the curse effect
     * @param amplifier its strength (Enfeeble, Gravebind)
     * @param start     game time the curse was first put on (kept while it is refreshed)
     * @param end       game time its longest entry ends
     * @param casters   one entry per caster
     */
    public record Line(Holder<MobEffect> curse, int amplifier, long start, long end, List<Caster> casters) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT), Line::curse,
                ByteBufCodecs.VAR_INT, Line::amplifier,
                ByteBufCodecs.VAR_LONG, Line::start,
                ByteBufCodecs.VAR_LONG, Line::end,
                Caster.STREAM_CODEC.apply(ByteBufCodecs.list()), Line::casters,
                Line::new);

        /** The entry of this player, or {@code null}. */
        @Nullable
        public Caster of(@Nullable UUID player) {
            Caster best = null;
            for (Caster c : casters) {
                if (c.is(player) && (best == null || c.end() > best.end())) {
                    best = c;
                }
            }
            return best;
        }

        /** The entry that ends last (there is always one). */
        public Caster longest() {
            Caster best = null;
            for (Caster c : casters) {
                if (best == null || c.end() > best.end()) {
                    best = c;
                }
            }
            return best != null ? best : new Caster(Optional.empty(), start, end);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CurseView> STREAM_CODEC =
            Line.STREAM_CODEC.apply(ByteBufCodecs.list()).map(CurseView::new, CurseView::lines);

    public static final CurseView EMPTY = new CurseView(List.of());

    private static Supplier<AttachmentType<CurseView>> type;

    static void register(DeferredRegister<AttachmentType<?>> attachments) {
        type = attachments.register("curse_view", () -> AttachmentType.builder(() -> EMPTY).sync(STREAM_CODEC).build());
    }

    public static AttachmentType<CurseView> type() {
        return type.get();
    }

    /**
     * The synced curse view of an entity, if it has one (both sides). Code outside the curse package reads the view only through
     * this, so the storage stays in one place (concept 3.2, backport).
     */
    public static Optional<CurseView> of(net.minecraft.world.entity.Entity entity) {
        return entity.getExistingData(type());
    }

    /** The line of one curse, or {@code null}. */
    public Line line(Holder<MobEffect> curse) {
        for (Line line : lines) {
            if (Curses.id(line.curse()).equals(Curses.id(curse))) {
                return line;
            }
        }
        return null;
    }
}
