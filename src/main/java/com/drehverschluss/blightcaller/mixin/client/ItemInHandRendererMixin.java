package com.drehverschluss.blightcaller.mixin.client;

import com.drehverschluss.blightcaller.client.fx.HandTip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Finds the tip of the wand of a Drain Soul caster while the item in the hand is drawn (first and third person use this
 * method), so the soul smoke leaves from it ({@link HandTip}). Reads only, changes nothing.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void blightcaller$captureTip(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                         PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        HandTip.capture(entity, stack, context, leftHand, poseStack);
    }
}
