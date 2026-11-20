package com.drehverschluss.blightcaller.mixin.skills;

import com.drehverschluss.blightcaller.compat.skilltree.ClassBranchHook;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Start and end of every load of Pufferfish's Skills (server start, /reload): state of {@link ClassBranchHook}. */
@Mixin(value = SkillsMod.class, remap = false)
public abstract class SkillsModMixin {
    @Inject(method = "loadModConfig", at = @At("HEAD"), require = 1)
    private void blightcaller$loadStart(MinecraftServer server, CallbackInfo ci) {
        ClassBranchHook.onLoadStart();
    }

    @Inject(method = "loadModConfig", at = @At("RETURN"), require = 1)
    private void blightcaller$loadEnd(MinecraftServer server, CallbackInfo ci) {
        ClassBranchHook.onLoadEnd();
    }
}
