package com.drehverschluss.blightcaller.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.fx.ParticleGroupType;
import org.jetbrains.annotations.Nullable;

/**
 * A particle type of Blightcaller's own particle set (phase 14A). It is a Spell Engine {@link ParticleGroupType}, so a
 * spell file can name it like any Spell Engine particle ({@code "id": "blightcaller:rot_smoke"}) and its
 * {@code appearance} (colour, scale, glow, motion) reaches the particle. Unlike Spell Engine's own types it also carries
 * colour, scale and glow over the network and in the {@code /particle} command, so server code and data packs can
 * colour it as well:
 * <pre>/particle blightcaller:rot_smoke{color:[0.37,0.75,0.35,1.0],scale:1.5} ~ ~1 ~</pre>
 * Without a colour the particle keeps its own default colour (drawn on the client by {@code client.fx.BlightParticle}).
 */
public final class BlightParticleType extends ParticleGroupType {
    private final MapCodec<ParticleGroupType> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, ParticleGroupType> streamCodec;

    public BlightParticleType() {
        super();
        this.codec = RecordCodecBuilder.mapCodec(i -> i.group(
                ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("color").forGetter(t -> argb(t.payload())),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(t -> t.payload() == null ? 1.0F : t.payload().scale),
                Codec.BOOL.optionalFieldOf("glow").forGetter(t -> t.payload() == null ? Optional.empty() : Optional.ofNullable(t.payload().glow))
        ).apply(i, (color, scale, glow) -> with(color.map(BlightParticleType::rgba).orElse(-1L), scale, glow.orElse(null))));
        this.streamCodec = StreamCodec.of(BlightParticleType::write, this::read);
    }

    /**
     * This type with a look, for code that spawns the particle itself.
     *
     * @param rgba  colour as Spell Engine writes it ({@code 0xRRGGBBAA}), -1 for the particle's own colour
     * @param scale size factor (1 = normal)
     * @param glow  full bright or lit by the world, null for the particle's own choice
     */
    public ParticleGroupType with(long rgba, float scale, @Nullable Boolean glow) {
        ParticleGroup.Appearance look = new ParticleGroup.Appearance();
        look.color = rgba;
        look.scale = scale;
        look.glow = glow;
        return spawnable(look, null);
    }

    @Override
    public MapCodec<ParticleGroupType> codec() {
        return codec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, ParticleGroupType> streamCodec() {
        return streamCodec;
    }

    private static void write(RegistryFriendlyByteBuf buf, ParticleGroupType type) {
        ParticleGroup.Appearance look = type.payload();
        buf.writeLong(look == null ? -1L : look.color);
        buf.writeFloat(look == null ? 1.0F : look.scale);
        buf.writeByte(look == null || look.glow == null ? 0 : look.glow ? 2 : 1);
    }

    private ParticleGroupType read(RegistryFriendlyByteBuf buf) {
        long color = buf.readLong();
        float scale = buf.readFloat();
        byte glow = buf.readByte();
        return with(color, scale, glow == 0 ? null : glow == 2);
    }

    private static Optional<Integer> argb(@Nullable ParticleGroup.Appearance look) {
        if (look == null || look.color < 0L) {
            return Optional.empty();
        }
        long c = look.color;
        return Optional.of((int) (((c & 0xFFL) << 24) | ((c >>> 8) & 0xFFFFFFL)));
    }

    private static long rgba(int argb) {
        return ((((long) argb) & 0xFFFFFFL) << 8) | ((argb >>> 24) & 0xFFL);
    }
}
