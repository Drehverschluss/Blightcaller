package com.drehverschluss.blightcaller.mixin.skills;

import com.drehverschluss.blightcaller.compat.skilltree.ClassBranchHook;
import java.nio.file.Path;
import net.minecraft.server.packs.resources.ResourceManager;
import net.puffish.skillsmod.api.json.JsonElement;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.config.reader.PackConfigReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every file Pufferfish's Skills (and Stormcaller's hook) reads from a data pack: the files of the Class Skills tab of Skill
 * Tree come with the Blightcaller branch while it is ready ({@link ClassBranchHook#afterRead}). Everything else is untouched.
 */
@Mixin(value = PackConfigReader.class, remap = false)
public abstract class PackConfigReaderMixin {
    @Shadow
    @Final
    private ResourceManager resourceManager;
    @Shadow
    @Final
    private String namespace;

    @Inject(method = "read", at = @At("RETURN"), cancellable = true, require = 1)
    private void blightcaller$read(Path path, CallbackInfoReturnable<Result<JsonElement, Problem>> cir) {
        if (!SkillsMixinPlugin.hooksReady()) {
            return;
        }
        Result<JsonElement, Problem> original = cir.getReturnValue();
        Result<JsonElement, Problem> result = ClassBranchHook.afterRead(resourceManager, namespace, path, original);
        if (result != original) {
            cir.setReturnValue(result);
        }
    }
}
