package com.drehverschluss.blightcaller.client.display;

import com.drehverschluss.blightcaller.client.fx.EntityGhost;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.drehverschluss.blightcaller.curse.CurseView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The curse veil (concept 8.1, level 1 of the display): a faint violet shimmer over the model of every cursed mob, stronger
 * with every curse level (1 barely, 2 clearly, 3 strong and pulsing slowly), so one sees from afar which mob is ready for
 * Last Rites. The mob's own model is drawn a second time as a slightly bigger shell ({@link EntityGhost}: violet silhouette
 * plus a faint glow of its own texture, so the texture stays readable), which also works for the Wither, the Ender Dragon
 * and mobs with their own renderers.
 * <p>
 * Same for every player (from the synced curse view), client option {@code curse_display.veil}. At most {@value #MAX}
 * mobs within {@value #RANGE} blocks; farther than {@value #GLOW_RANGE} blocks only the silhouette.
 */
final class CurseVeil {
    static final int MAX = 40;
    static final double RANGE = 48.0;
    static final double GLOW_RANGE = 32.0;
    /** Violet of the veil. */
    static final float[] COLOR = {0.66F, 0.26F, 1.0F};
    /** Silhouette opacity and texture glow per curse level (index 1..3). */
    static final float[] ALPHA = {0.0F, 0.06F, 0.12F, 0.16F};
    static final float[] GLOW = {0.0F, 0.05F, 0.12F, 0.18F};
    /** Level 3 pulses: period in ticks and how much. */
    static final float PULSE_TICKS = 32.0F;
    /** The shell is a little bigger than the model, so it lies over it instead of fighting with it. */
    static final float SHELL = 1.035F;

    /** Veiled mobs in the last frame (probe, report). */
    static int lastVeiled;

    private CurseVeil() {}

    static void render(PoseStack.Pose base, Vec3 cam, Frustum frustum, MultiBufferSource.BufferSource buffers, float partialTick) {
        lastVeiled = 0;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !BlightcallerClientConfig.curseVeil()) {
            return;
        }
        List<LivingEntity> cursed = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == mc.getCameraEntity()
                    || (mc.player != null && living.isInvisibleTo(mc.player))
                    || living.position().distanceToSqr(cam) > RANGE * RANGE
                    || CurseLines.view(living) == null
                    || !EntityGhost.supported(living.getType())
                    || !frustum.isVisible(living.getBoundingBoxForCulling())) {
                continue;
            }
            cursed.add(living);
        }
        if (cursed.isEmpty()) {
            return;
        }
        cursed.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(cam)));
        float time = level.getGameTime() + partialTick;
        for (int i = 0; i < cursed.size() && i < MAX; i++) {
            LivingEntity living = cursed.get(i);
            CurseView view = CurseLines.view(living);
            if (view == null) {
                continue;
            }
            int curseLevel = CurseLines.level(view);
            float alpha = ALPHA[curseLevel];
            float glow = GLOW[curseLevel];
            if (curseLevel >= 3) {
                float pulse = 0.5F + 0.5F * Mth.sin(time * Mth.TWO_PI / PULSE_TICKS + living.getId());
                alpha += 0.06F * pulse;
                glow += 0.10F * pulse;
            }
            // big mobs keep their colours (a veil over a big model covers a lot of screen)
            float fade = com.drehverschluss.blightcaller.client.fx.DrainSoulBeam.sizeFade(living);
            alpha *= fade;
            glow *= fade;
            if (living.position().distanceToSqr(cam) > GLOW_RANGE * GLOW_RANGE) {
                glow = 0.0F;
            }
            PoseStack pose = new PoseStack();
            pose.last().pose().set(base.pose());
            pose.last().normal().set(base.normal());
            Vec3 at = lerp(living, partialTick).subtract(cam);
            float half = living.getBbHeight() * 0.5F;
            pose.translate(at.x, at.y + half, at.z);
            pose.scale(SHELL, SHELL, SHELL);
            pose.translate(0.0F, -half, 0.0F);
            if (EntityGhost.render(living, pose, partialTick, buffers, new EntityGhost.Style(COLOR[0], COLOR[1], COLOR[2], alpha, glow))) {
                lastVeiled++;
            }
        }
    }

    private static Vec3 lerp(Entity e, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()), Mth.lerp(partialTick, e.zo, e.getZ()));
    }
}
