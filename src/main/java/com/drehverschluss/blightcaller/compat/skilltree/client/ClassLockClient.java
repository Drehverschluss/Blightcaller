package com.drehverschluss.blightcaller.compat.skilltree.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.compat.skilltree.ClassLockPayload;
import com.drehverschluss.blightcaller.compat.skilltree.SkillTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the Blightcaller class lock: the last state the server sent ({@link ClassLockPayload}), read by the skill
 * screen mixin and the root tooltip, and the toast after a blocked unlock. Free of Skill Tree and Pufferfish's Skills classes.
 */
public final class ClassLockClient {
    private static volatile ClassLockPayload state = ClassLockPayload.NONE;

    private ClassLockClient() {}

    public static void init() {
        ClassLockPayload.setClientHandler(ClassLockClient::accept);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> state = ClassLockPayload.NONE);
    }

    private static void accept(ClassLockPayload payload) {
        state = payload;
        if (payload.toast() != ClassLockPayload.TOAST_NONE) {
            Minecraft mc = Minecraft.getInstance();
            String body = payload.toast() == ClassLockPayload.TOAST_BLIGHTCALLER ? "blightcaller" : "class";
            mc.execute(() -> SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("skill." + Blightcaller.MODID + ".class_lock.toast.title"),
                    Component.translatable("skill." + Blightcaller.MODID + ".class_lock.toast." + body)));
        }
    }

    public static boolean enabled() {
        return state.enabled();
    }

    /** The Blightcaller branch is in the Class Skills tab of Skill Tree. */
    public static boolean classTree() {
        return state.classTree();
    }

    /** The Blightcaller root is locked because the player has another class. */
    public static boolean ownBlocked() {
        return state.enabled() && state.ownBlocked();
    }

    /** Should the roots of this skill category be shown as locked? */
    public static boolean rootsBlocked(ResourceLocation categoryId) {
        ClassLockPayload s = state;
        if (!s.enabled()) {
            return false;
        }
        if (SkillTab.BLIGHTCALLER_CATEGORY.equals(categoryId)) {
            return s.ownBlocked();
        }
        return s.blocked().contains(categoryId);
    }
}
