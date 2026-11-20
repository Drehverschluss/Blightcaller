package com.drehverschluss.blightcaller.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.internals.SpellParameters;

/**
 * The wave of chain that runs over the ground when Gravebind is cast (phase 7B: it shows the real radius of the cast, also with
 * Improved Gravebind). Any spell with the particle {@code blightcaller:grave_wave} in its release visuals gets the wave; the particle
 * itself only marks the spell. On the server, after the cast, the radius of the spell for this caster (range with the caster's
 * modifiers, {@code SpellParameters.getRange}) goes to the caster and everyone who sees them: other clients do not know the caster's
 * skill nodes.
 */
public final class GraveWave {
    public static final ResourceLocation PARTICLE = Blightcaller.id("grave_wave");

    /** Server to client: a wave at the caster's feet with this radius. */
    public record Payload(double x, double y, double z, float radius) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Blightcaller.id("grave_wave"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.radius);
                },
                buf -> new Payload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static Consumer<Payload> clientHandler = payload -> {};

    private GraveWave() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Payload.TYPE, Payload.STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }

    /** Called once by the client mod class. */
    public static void setClientHandler(Consumer<Payload> handler) {
        clientHandler = handler;
    }

    public static void init() {
        SpellEvents.SPELL_CAST.register(args -> {
            // a Spell Engine event listener must never throw (it would be switched off for good)
            try {
                if (args.caster() instanceof ServerPlayer player && hasWave(args.spell().value())) {
                    float radius = SpellParameters.getRange(player, args.spell());
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Payload(player.getX(), player.getY(), player.getZ(), radius));
                }
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: grave wave failed", e);
            }
        });
    }

    private static boolean hasWave(Spell spell) {
        if (spell.release == null || spell.release.visuals == null || spell.release.visuals.particles == null) {
            return false;
        }
        for (ParticleGroup group : spell.release.visuals.particles) {
            if (group != null && PARTICLE.toString().equals(group.id)) {
                return true;
            }
        }
        return false;
    }
}
