package com.drehverschluss.blightcaller.mixin.skills;

import com.drehverschluss.blightcaller.compat.skilltree.client.ClassLockClient;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.client.config.skill.ClientSkillConfig;
import net.puffish.skillsmod.client.data.ClientCategoryData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skill screen of Pufferfish's Skills: shows the roots that the Blightcaller class lock blocks ({@code compat.skilltree.ClassLock})
 * as locked, so the player sees it before clicking. Only the look: the server enforces the lock on its own. Stormcaller has
 * the same handler for its own lock; each only sets a value when its own lock blocks the root, so they never get in each
 * other's way. {@code require = 0}: if a later version of Pufferfish's Skills changes this method, the roots just look normal.
 */
@Mixin(value = ClientCategoryData.class, remap = false)
public abstract class ClientCategoryDataMixin {
    @Inject(method = "getSkillState", at = @At("RETURN"), cancellable = true, require = 0)
    private void blightcaller$classLock(ClientSkillConfig skill, CallbackInfoReturnable<Skill.State> cir) {
        Skill.State state = cir.getReturnValue();
        if (skill.isRoot() && (state == Skill.State.AVAILABLE || state == Skill.State.AFFORDABLE)
                && ClassLockClient.rootsBlocked(((ClientCategoryData) (Object) this).getConfig().id())) {
            cir.setReturnValue(Skill.State.LOCKED);
        }
    }
}
