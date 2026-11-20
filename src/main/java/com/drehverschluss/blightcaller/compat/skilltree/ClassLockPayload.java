package com.drehverschluss.blightcaller.compat.skilltree;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to client: state of the Blightcaller class lock ({@link ClassLock}), for the skill screen (locked roots, tooltip
 * line) and a toast after a blocked unlock.
 *
 * @param enabled  class lock switched on in the server config and the own Blightcaller tab loaded
 * @param ownBlocked the player has a class in another class tab, so the Blightcaller root is locked
 * @param blocked  tabs whose roots are locked because the player follows the Blightcaller path
 * @param classTree the Blightcaller branch is in the Class Skills tab (no own tab, no class lock)
 * @param toast    0 = none, 1 = the Blightcaller root was blocked, 2 = a root of another class was blocked
 */
public record ClassLockPayload(boolean enabled, boolean ownBlocked, List<ResourceLocation> blocked, boolean classTree, int toast)
        implements CustomPacketPayload {
    public static final Type<ClassLockPayload> TYPE = new Type<>(Blightcaller.id("class_lock"));
    public static final StreamCodec<FriendlyByteBuf, ClassLockPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.enabled);
                buf.writeBoolean(p.ownBlocked);
                buf.writeVarInt(p.blocked.size());
                for (ResourceLocation id : p.blocked) {
                    buf.writeResourceLocation(id);
                }
                buf.writeBoolean(p.classTree);
                buf.writeByte(p.toast);
            },
            buf -> {
                boolean enabled = buf.readBoolean();
                boolean ownBlocked = buf.readBoolean();
                int n = buf.readVarInt();
                List<ResourceLocation> blocked = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    blocked.add(buf.readResourceLocation());
                }
                return new ClassLockPayload(enabled, ownBlocked, List.copyOf(blocked), buf.readBoolean(), buf.readByte());
            });

    public static final int TOAST_NONE = 0;
    public static final int TOAST_BLIGHTCALLER = 1;
    public static final int TOAST_CLASS = 2;
    public static final ClassLockPayload NONE = new ClassLockPayload(false, false, List.of(), false, TOAST_NONE);

    private static Consumer<ClassLockPayload> clientHandler = payload -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }

    /** Called once by the client mod class. */
    public static void setClientHandler(Consumer<ClassLockPayload> handler) {
        clientHandler = handler;
    }

    /** The same state without a toast (to see whether it changed). */
    public ClassLockPayload withoutToast() {
        return toast == TOAST_NONE ? this : new ClassLockPayload(enabled, ownBlocked, blocked, classTree, TOAST_NONE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
