package com.drehverschluss.blightcaller.curse;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Synced attachment {@code blightcaller:curse_view}: what clients need to draw the curses on an entity, one line per curse
 * (not per caster): the curse, its strength, when it was put on and when its longest entry ends (game time). Never saved;
 * the server rebuilds it whenever the curses of the entity change ({@link Curses#syncView}), NeoForge sends it to every
 * player tracking the entity and again when one starts tracking it.
 * <p>
 * Clients use it for curse particles on targets that refuse the display effect (bosses like the Wither), the ghostly
 * chains of Gravebind, and anything else that must look the same for every player. It does not change per tick.
 */
public record CurseView(List<Line> lines) {
    /**
     * One curse on the entity.
     *
     * @param curse     the curse effect
     * @param amplifier its strength (Enfeeble, Gravebind)
     * @param start     game time the curse was first put on (kept while it is refreshed)
     * @param end       game time its longest entry ends
     */
    public record Line(Holder<MobEffect> curse, int amplifier, long start, long end) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT), Line::curse,
                ByteBufCodecs.VAR_INT, Line::amplifier,
                ByteBufCodecs.VAR_LONG, Line::start,
                ByteBufCodecs.VAR_LONG, Line::end,
                Line::new);
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
