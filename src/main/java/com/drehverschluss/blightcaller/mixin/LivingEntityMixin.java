package com.drehverschluss.blightcaller.mixin;

import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.drehverschluss.blightcaller.curse.CurseDamage;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feedback of curse ticks per player (concept 3.1, client option).
 * <p>
 * In vanilla a hit gives two things: the server sends a damage event to every client tracking the target (the client sets
 * {@code hurtTime}: the red flash) and the server plays the hurt sound for everyone nearby. One server sound cannot follow
 * the choice of each player, so for the curse damage type ({@code blightcaller:curse}) only:
 * <ul>
 *     <li>the server plays <b>no</b> hurt sound ({@link #blightcaller$curseTickSound});</li>
 *     <li>each client handles the damage event itself ({@link #blightcaller$curseTickFeedback}): red flash only if
 *     {@code flash} is on, the hurt sound only if {@code hurt_sound} is on, played locally at the target. Everything else
 *     of vanilla's handling (last damage source, invulnerability counter) stays.</li>
 * </ul>
 * Real hits and the death sound are untouched. Clients without Blightcaller cannot join (the mod is required on both sides).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow @Final public WalkAnimationState walkAnimation;
    @Shadow public int hurtTime;
    @Shadow public int hurtDuration;
    @Shadow @Nullable private DamageSource lastDamageSource;
    @Shadow private long lastDamageStamp;

    @Shadow @Nullable protected abstract SoundEvent getHurtSound(DamageSource damageSource);

    @Shadow protected abstract float getSoundVolume();

    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
    private void blightcaller$curseTickSound(DamageSource source, CallbackInfo ci) {
        if (source.is(CurseDamage.CURSE)) {
            // each client plays it itself if it wants to (see blightcaller$curseTickFeedback)
            ci.cancel();
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true)
    private void blightcaller$curseTickFeedback(DamageSource source, CallbackInfo ci) {
        if (!source.is(CurseDamage.CURSE)) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) {
            return;
        }
        ci.cancel();
        self.invulnerableTime = 20;
        if (BlightcallerClientConfig.curseTickFlash()) {
            this.walkAnimation.setSpeed(1.5F);
            this.hurtDuration = 10;
            this.hurtTime = this.hurtDuration;
        }
        if (BlightcallerClientConfig.curseTickSound() && !self.isSilent() && !self.isDeadOrDying()) {
            SoundEvent sound = this.getHurtSound(source);
            if (sound != null) {
                // volume and pitch like the server's hurt sound (makeSound)
                self.level().playLocalSound(self.getX(), self.getY(), self.getZ(), sound, self.getSoundSource(),
                        this.getSoundVolume(), self.getVoicePitch(), false);
            }
        }
        this.lastDamageSource = source;
        this.lastDamageStamp = self.level().getGameTime();
    }
}
