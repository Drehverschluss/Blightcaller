package com.drehverschluss.blightcaller.mixin.skills;

import com.drehverschluss.blightcaller.compat.skilltree.ClassBranchHook;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.puffish.skillsmod.api.config.ConfigContext;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.reader.ConfigReader;
import net.puffish.skillsmod.config.reader.PackConfigReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The category list of the Blightcaller data pack (read before the Skill Tree pack): prepares the Class Skills branch and
 * leaves out the own tab if the branch is there ({@link ClassBranchHook}). Only the {@code blightcaller} namespace; Stormcaller
 * has a handler at the same place for its own namespace, both pass everything else on untouched.
 */
@Mixin(value = ConfigReader.class, remap = false)
public abstract class ConfigReaderMixin {
    @Inject(method = "readCategories", at = @At("HEAD"), cancellable = true, require = 1)
    private void blightcaller$filterCategories(String namespace, List<String> ids, int position, ConfigContext context,
                                               CallbackInfoReturnable<Result<Map<ResourceLocation, CategoryConfig>, Problem>> cir) {
        if (!SkillsMixinPlugin.hooksReady() || !((Object) this instanceof PackConfigReader pack)) {
            return;
        }
        List<String> filtered = ClassBranchHook.filterOwnTab(((PackConfigReaderAccessor) pack).blightcaller$getResourceManager(),
                namespace, ids);
        if (filtered != ids) {
            cir.setReturnValue(((ConfigReader) (Object) this).readCategories(namespace, filtered, position, context));
        }
    }
}
