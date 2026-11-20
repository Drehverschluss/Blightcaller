package com.drehverschluss.blightcaller.mixin.client;

import com.drehverschluss.blightcaller.client.fx.EntityGhost;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While Blightcaller draws a copy of an entity's model ({@link EntityGhost}: the torn soul of Drain Soul, the curse veil),
 * the base {@code EntityRenderer.render} is skipped. It holds nothing of the model, only the name tag part: NeoForge's
 * {@code RenderNameTagEvent} and the vanilla name tag. Without this, mods that draw in that event straight into the main
 * buffers (Health Bars draws its floating health bar there) would draw a second bar at the copy.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void blightcaller$skipNameTagOfGhosts(Entity entity, float yaw, float partialTick, PoseStack poseStack,
                                                  MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (EntityGhost.isDrawing()) {
            ci.cancel();
        }
    }
}
