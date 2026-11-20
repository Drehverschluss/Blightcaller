package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.curse.CurseEffects;
import com.drehverschluss.blightcaller.curse.CurseView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The ghostly chains of Gravebind on every bound target (concept 5, decided 20.11.: chains instead of hands).
 * <p>
 * Drawn from the synced curse view ({@link CurseView}), so every player sees the same chains, also on targets that refuse
 * the effect (Wither) and on entities whose renderer is not a living entity renderer (Ender Dragon, GeckoLib mobs): one
 * pass after the translucent blocks over the entities the client draws, no render layer per entity type.
 * <ul>
 *     <li><b>Shape:</b> the vanilla chain (decided 20.11. after the first pictures: the texture {@code block/chain} by
 *     reference, two crossed planes like the chain block), laid in rings around the body, slightly tilted against each
 *     other. 1 ring on small mobs, 2 on normal ones, 3 on tall ones (Wither). Link size and ring radius follow the hitbox,
 *     so the chain fits from a silverfish to the Wither.</li>
 *     <li><b>Bound to the ground:</b> two chains run from the lowest ring to the ground; in the air they hang down and fade
 *     out.</li>
 *     <li><b>Movement:</b> when the curse lands the rings close in from wide and the links appear one after another (half a
 *     second), then they turn slowly; in the last half second they fade.</li>
 * </ul>
 * A light ghostly tint over the grey metal, full bright, slightly translucent, no depth write: reads by day and by night
 * and never hides the mob. At most {@value #MAX_ENTITIES} bound
 * entities within {@value #MAX_DISTANCE} blocks are drawn.
 */
public final class GravebindChains {
    /** The vanilla chain ({@code minecraft:block/chain}), referenced by id. */
    public static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("block/chain");
    private static final int MAX_ENTITIES = 48;
    private static final double MAX_DISTANCE = 64.0;
    private static final float WRAP_TICKS = 10.0F;
    private static final float FADE_TICKS = 10.0F;
    /** Ghostly tint over the grey vanilla chain: a light pale violet, slightly translucent. */
    private static final float RED = 0.86F;
    private static final float GREEN = 0.80F;
    private static final float BLUE = 1.0F;
    private static final float ALPHA = 0.9F;

    private GravebindChains() {}

    static RenderType renderType() {
        return RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
    }

    /** Draws the chains of all bound entities in view. Returns whether anything was drawn. */
    static boolean render(Matrix4f matrix, Vec3 cam, Frustum frustum, MultiBufferSource.BufferSource buffers, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        long now = mc.level.getGameTime();
        VertexConsumer vc = null;
        int drawn = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || drawn >= MAX_ENTITIES) {
                continue;
            }
            CurseView view = living.getExistingData(CurseView.type()).orElse(null);
            CurseView.Line line = view == null ? null : view.line(CurseEffects.GRAVEBIND);
            if (line == null || living.isRemoved() || (mc.player != null && living.isInvisibleTo(mc.player))
                    || living.position().distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE
                    || !frustum.isVisible(living.getBoundingBox().inflate(1.0 + living.getBbWidth()))) {
                continue;
            }
            if (vc == null) {
                vc = buffers.getBuffer(renderType());
            }
            draw(vc, matrix, cam, living, line, now, partialTick);
            drawn++;
        }
        return vc != null;
    }

    private static void draw(VertexConsumer vc, Matrix4f matrix, Vec3 cam, LivingEntity living, CurseView.Line line, long now,
                             float partialTick) {
        float age = (now - line.start()) + partialTick;
        float left = (line.end() - now) - partialTick;
        float alpha = ALPHA * Mth.clamp(left / FADE_TICKS, 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            return;
        }
        float wrap = Mth.clamp(age / WRAP_TICKS, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - wrap) * (1.0F - wrap) * (1.0F - wrap);
        // a short ghostly flash while the chain closes in
        float bright = 1.0F + 0.35F * (1.0F - wrap);
        TextureAtlasSprite sprite = sprite();

        double px = Mth.lerp(partialTick, living.xo, living.getX()) - cam.x;
        double py = Mth.lerp(partialTick, living.yo, living.getY()) - cam.y;
        double pz = Mth.lerp(partialTick, living.zo, living.getZ()) - cam.z;
        float width = Math.min(living.getBbWidth(), 6.0F);
        float height = Math.min(living.getBbHeight(), 8.0F);
        float scale = Mth.clamp(height / 1.8F, 0.45F, 2.6F);
        // one texture length of the vanilla chain (16 px = 1 block on the chain block), smaller on small mobs
        float unit = 0.75F * scale;
        float thickness = unit * 3.0F / 16.0F;
        float baseRadius = Math.max(width * 0.5F * 1.12F, 0.2F) + thickness * 0.5F;
        float radius = baseRadius * (1.0F + 0.9F * (1.0F - ease));

        int rings = height < 0.9F ? 1 : height < 2.6F ? 2 : 3;
        float[] heights = rings == 1 ? new float[]{0.45F} : rings == 2 ? new float[]{0.3F, 0.62F} : new float[]{0.24F, 0.5F, 0.74F};
        float spin = age * 0.012F;
        Vec3 anchorA = null;
        Vec3 anchorB = null;
        for (int r = 0; r < rings; r++) {
            double cy = py + height * heights[r];
            float tilt = (r % 2 == 0 ? 1.0F : -1.0F) * 0.22F;
            float axisAngle = r * 1.3F;
            float dir = r % 2 == 0 ? 1.0F : -1.0F;
            // segments of half a texture length: the links of the vanilla chain keep their shape
            int count = Mth.clamp(Math.round(Mth.TWO_PI * radius / (unit * 0.5F)), 6, 64);
            int visible = Math.max(1, Math.round(count * ease));
            Vec3 normal = tilt(new Vec3(0.0, 1.0, 0.0), axisAngle, tilt);
            Vec3 center = new Vec3(px, cy, pz);
            for (int i = 0; i < visible; i++) {
                float a0 = Mth.TWO_PI * i / count + spin * dir + r * 0.7F;
                float a1 = Mth.TWO_PI * (i + 1) / count + spin * dir + r * 0.7F;
                Vec3 p0 = center.add(tilt(new Vec3(Mth.cos(a0), 0.0, Mth.sin(a0)), axisAngle, tilt).scale(radius));
                Vec3 p1 = center.add(tilt(new Vec3(Mth.cos(a1), 0.0, Mth.sin(a1)), axisAngle, tilt).scale(radius));
                Vec3 radial = p0.add(p1).scale(0.5).subtract(center).normalize();
                float v0 = (i % 2) * 0.5F;
                segment(vc, matrix, sprite, p0, p1, radial, normal, v0, v0 + 0.5F, thickness, alpha, bright);
                if (r == 0 && i == 0) {
                    anchorA = p0;
                }
                if (r == 0 && i == count / 2) {
                    anchorB = p0;
                }
            }
        }
        if (wrap < 0.6F) {
            return;
        }
        float anchorAlpha = alpha * Mth.clamp((wrap - 0.6F) / 0.4F, 0.0F, 1.0F);
        boolean grounded = living.onGround();
        Vec3 center = new Vec3(px, py, pz);
        anchor(vc, matrix, sprite, anchorA, center, py, grounded, radius, scale, unit, thickness, anchorAlpha);
        anchor(vc, matrix, sprite, anchorB, center, py, grounded, radius, scale, unit, thickness, anchorAlpha);
    }

    /** A chain from a point of the lowest ring to the ground, or hanging down in the air (fading out). */
    private static void anchor(VertexConsumer vc, Matrix4f matrix, TextureAtlasSprite sprite, Vec3 start, Vec3 center, double groundY,
                               boolean grounded, float radius, float scale, float unit, float thickness, float alpha) {
        if (start == null || alpha <= 0.01F) {
            return;
        }
        Vec3 flat = new Vec3(start.x - center.x, 0.0, start.z - center.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
        Vec3 end = grounded
                ? new Vec3(start.x + flat.x * radius * 1.4, groundY + 0.02, start.z + flat.z * radius * 1.4)
                : start.add(flat.scale(0.35 * scale)).add(0.0, -2.2 * scale, 0.0);
        Vec3 way = end.subtract(start);
        double length = way.length();
        if (length < 0.05) {
            return;
        }
        Vec3 dir = way.scale(1.0 / length);
        Vec3 side1 = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (side1.lengthSqr() < 1.0E-6) {
            side1 = dir.cross(new Vec3(1.0, 0.0, 0.0));
        }
        side1 = side1.normalize();
        Vec3 side2 = dir.cross(side1).normalize();
        int count = Mth.clamp((int) Math.round(length / (unit * 0.5F)), 1, 40);
        for (int i = 0; i < count; i++) {
            Vec3 a = start.add(way.scale(i / (double) count));
            Vec3 b = start.add(way.scale((i + 1) / (double) count));
            float fade = grounded ? alpha : alpha * (1.0F - (i + 0.5F) / count * 0.85F);
            float v0 = (i % 2) * 0.5F;
            segment(vc, matrix, sprite, a, b, side1, side2, v0, v0 + 0.5F, thickness, fade, 1.0F);
        }
    }

    /** Rotates a vector around a horizontal axis (at {@code axisAngle} around Y) by {@code tilt}. */
    private static Vec3 tilt(Vec3 v, float axisAngle, float tilt) {
        double ax = Mth.cos(axisAngle);
        double az = Mth.sin(axisAngle);
        double cos = Mth.cos(tilt);
        double sin = Mth.sin(tilt);
        // Rodrigues rotation around (ax, 0, az)
        double dot = v.x * ax + v.z * az;
        double cx = -az * v.y;
        double cy = az * v.x - ax * v.z;
        double cz = ax * v.y;
        return new Vec3(
                v.x * cos + cx * sin + ax * dot * (1.0 - cos),
                v.y * cos + cy * sin,
                v.z * cos + cz * sin + az * dot * (1.0 - cos));
    }

    /**
     * One piece of vanilla chain from {@code a} to {@code b}: two crossed planes like the chain block (texture columns 0-3 and
     * 3-6 of {@code block/chain}), {@code v0}..{@code v1} = part of the texture length (0..1).
     */
    private static void segment(VertexConsumer vc, Matrix4f matrix, TextureAtlasSprite sprite, Vec3 a, Vec3 b, Vec3 side1, Vec3 side2,
                                float v0, float v1, float thickness, float alpha, float bright) {
        float r = Math.min(1.0F, RED * bright);
        float g = Math.min(1.0F, GREEN * bright);
        float bl = Math.min(1.0F, BLUE * bright);
        float h = thickness * 0.5F;
        plane(vc, matrix, sprite, a, b, side1.scale(h), 0.0F, 3.0F / 16.0F, v0, v1, r, g, bl, alpha);
        plane(vc, matrix, sprite, a, b, side2.scale(h), 3.0F / 16.0F, 6.0F / 16.0F, v0, v1, r, g, bl, alpha);
    }

    private static void plane(VertexConsumer vc, Matrix4f matrix, TextureAtlasSprite sprite, Vec3 a, Vec3 b, Vec3 half,
                              float u0, float u1, float v0, float v1, float r, float g, float bl, float alpha) {
        float su0 = sprite.getU(u0);
        float su1 = sprite.getU(u1);
        float sv0 = sprite.getV(v0);
        float sv1 = sprite.getV(v1);
        vertex(vc, matrix, a.subtract(half), su0, sv0, r, g, bl, alpha);
        vertex(vc, matrix, a.add(half), su1, sv0, r, g, bl, alpha);
        vertex(vc, matrix, b.add(half), su1, sv1, r, g, bl, alpha);
        vertex(vc, matrix, b.subtract(half), su0, sv1, r, g, bl, alpha);
    }

    /** The vanilla chain texture in the block atlas (used by reference, nothing copied). */
    static TextureAtlasSprite sprite() {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(CHAIN);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Vec3 p, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
