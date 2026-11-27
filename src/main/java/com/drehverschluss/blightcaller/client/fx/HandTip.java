package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where the tip of the item in the main hand of a caster is, so the soul smoke of Drain Soul (Phase 6.1) and the cast
 * particles ({@link CastFx}, phase 14B1) leave from the tip of the wand or the upper corner of the grimoire, in third person
 * and in first person, whatever the cast animation does with the arms.
 * <p>
 * Captured while the item is drawn ({@code ItemInHandRenderer.renderItem}, mixin): the item's display transform is applied
 * to a copy of the pose and a point of the item model is transformed.
 * <ul>
 *     <li><b>Third person</b> (also other players): the pose is camera relative world space; drawn before the visuals of
 *     the same frame.</li>
 *     <li><b>First person:</b> the hand is drawn after the world, with its own field of view, attached to the camera. The
 *     point is kept in view space and scaled from the hand's field of view to the world's when used (next frame), so it
 *     sits on the tip on screen.</li>
 * </ul>
 * With a shader pack (Iris) the shadow pass draws the items once more from the sun; it is skipped (14B1: before, the tip
 * jumped to a place in shadow space with shaders on).
 * <p>
 * The tip of the item: the upper right corner of a diagonal item sprite (the crystal of the wands, most staffs), the upper
 * middle for a grimoire (the upper corner of our tilted books). Without a fresh capture Drain Soul uses an estimate, the
 * cast particles wait for one.
 */
public final class HandTip {
    private record Tip(Vec3 value, boolean view, long frame, float handM00, float handM11) {}

    private static final Map<Integer, Tip> TIPS = new HashMap<>();
    private static long frame;
    private static boolean warned;
    /** Iris' shadow pass check ({@code IrisApi.getInstance().isRenderingShadowPass()}), looked up once; null without Iris. */
    @Nullable
    private static java.lang.reflect.Method shadowPass;
    @Nullable
    private static Object irisApi;
    private static boolean irisLooked;

    private HandTip() {}

    /** Called once per frame by the visuals' render pass. */
    static void nextFrame() {
        frame++;
        if (frame % 200 == 0) {
            TIPS.values().removeIf(t -> frame - t.frame() > 100);
        }
    }

    /** Mixin hook: an item in a hand is about to be drawn. */
    public static void capture(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose) {
        if (EntityGhost.isDrawing() || !(entity instanceof Player player) || stack.isEmpty() || stack != entity.getMainHandItem()
                || !(DrainSoulBeam.isDraining(player) || CastFx.wantsTip(player))) {
            return;
        }
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return;
        }
        if (inShadowPass()) {
            // shader packs draw every item a second time from the sun for the shadows: that pose is not the camera's (14B1)
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            BakedModel model = mc.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId() + context.ordinal());
            PoseStack copy = new PoseStack();
            copy.last().pose().set(pose.last().pose());
            copy.last().normal().set(pose.last().normal());
            ClientHooks.handleCameraTransforms(copy, model, context, leftHand);
            copy.translate(-0.5F, -0.5F, -0.5F);
            float[] tip = tipOf(stack);
            Vector3f p = copy.last().pose().transformPosition(tip[0], tip[1], 0.5F, new Vector3f());
            Camera camera = mc.gameRenderer.getMainCamera();
            if (context.firstPerson()) {
                // hand space is camera relative and world oriented; keep it in view space (it moves with the camera)
                Vector3f view = camera.rotation().conjugate(new Quaternionf()).transform(p);
                Matrix4f projection = RenderSystem.getProjectionMatrix();
                TIPS.put(entity.getId(), new Tip(new Vec3(view), true, frame, projection.m00(), projection.m11()));
            } else {
                TIPS.put(entity.getId(), new Tip(camera.getPosition().add(p.x, p.y, p.z), false, frame, 0.0F, 0.0F));
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Blightcaller.LOGGER.warn("Blightcaller: could not find the tip of the wand (reported once)", t);
            }
        }
    }

    /** Whether Iris is drawing its shadow pass right now (by reflection, Iris is no dependency). */
    private static boolean inShadowPass() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shadowPass = api.getMethod("isRenderingShadowPass");
            } catch (Throwable t) {
                shadowPass = null;
            }
        }
        if (shadowPass == null) {
            return false;
        }
        try {
            return (Boolean) shadowPass.invoke(irisApi);
        } catch (Throwable t) {
            shadowPass = null;
            return false;
        }
    }

    /** Whether the item is a book (grimoire, tome): its tip is the upper corner, not the end of a staff. */
    static boolean isBook(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.contains("grimoire") || path.contains("book") || path.contains("tome");
    }

    /** The tip in item model space (0..1), from the item's look. */
    private static float[] tipOf(ItemStack stack) {
        return isBook(stack) ? new float[]{0.5F, 0.9F} : new float[]{0.875F, 0.85F};
    }

    /**
     * World position of the tip of a caster's item for this frame, or null without a fresh capture.
     * {@code worldProjection}: the projection of the world pass (for the field of view of the first person hand).
     */
    @Nullable
    static Vec3 get(Player player, Camera camera, Matrix4f worldProjection) {
        Tip tip = TIPS.get(player.getId());
        if (tip == null || frame - tip.frame() > 2) {
            return null;
        }
        if (!tip.view()) {
            return tip.value();
        }
        if (player != Minecraft.getInstance().player || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return null;
        }
        float sx = worldProjection.m00() != 0.0F ? tip.handM00() / worldProjection.m00() : 1.0F;
        float sy = worldProjection.m11() != 0.0F ? tip.handM11() / worldProjection.m11() : 1.0F;
        Vector3f view = new Vector3f((float) tip.value().x * sx, (float) tip.value().y * sy, (float) tip.value().z);
        Vector3f world = camera.rotation().transform(view);
        return camera.getPosition().add(world.x, world.y, world.z);
    }
}
