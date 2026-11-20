package com.drehverschluss.blightcaller.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Whether a renderer would show the vanilla name tag of an entity (protected in vanilla); for the curse icons. */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker {
    @Invoker("shouldShowName")
    boolean blightcaller$shouldShowName(Entity entity);
}
