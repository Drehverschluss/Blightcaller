package com.drehverschluss.blightcaller.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The ghostly veil of the Gravebind chains (Phase 6.2, wish 20.11.): a soft, see-through seam of soul turquoise around every
 * chain that runs along with the wave, and a few shreds drifting along the chains. It makes the dark iron readable at night,
 * also with shaders (additive and full bright, which Iris treats as glowing), and stays faint by day.
 * <p>
 * The chains hand over their paths while they are drawn ({@link #seam}, {@link #shred}); everything is drawn in two batches
 * afterwards ({@link #flush}): the seams as camera facing ribbons with the soft smoke texture of Drain Soul flowing along
 * them, the shreds as small stretched glows.
 */
final class ChainVeil {
    /** Soul turquoise (as the sheen and Drain Soul). */
    private static final float[] COLOR = {0.22F, 0.90F, 0.95F};

    private record Seam(Vec3[] points, boolean closed, float width, float strength, float time) {}

    private record Shred(Vec3 at, Vec3 along, float length, float width, float strength) {}

    private final List<Seam> seams = new ArrayList<>();
    private final List<Shred> shreds = new ArrayList<>();

    /** A seam along a chain (camera relative points, the same as the links). */
    void seam(Vec3[] points, boolean closed, float width, float strength, float time) {
        if (strength > 0.01F && points.length >= 2) {
            seams.add(new Seam(points, closed, width, strength, time));
        }
    }

    /** A shred at {@code u} (0..1) along a chain, stretched along it. */
    void shred(Vec3[] points, float u, float length, float width, float strength) {
        if (strength <= 0.01F || points.length < 2) {
            return;
        }
        float f = Mth.clamp(u, 0.0F, 1.0F) * (points.length - 1);
        int i = Math.min(points.length - 2, (int) f);
        Vec3 a = points[i];
        Vec3 b = points[i + 1];
        Vec3 at = a.lerp(b, f - i);
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0E-8) {
            return;
        }
        // fades in and out at both ends of its way
        float fade = Mth.sin(Mth.PI * Mth.clamp(u, 0.0F, 1.0F));
        shreds.add(new Shred(at, along.normalize(), length, width, strength * fade));
    }

    boolean isEmpty() {
        return seams.isEmpty() && shreds.isEmpty();
    }

    void flush(PoseStack.Pose base, MultiBufferSource.BufferSource buffers) {
        if (!seams.isEmpty()) {
            RenderType type = RenderType.eyes(DrainSoulBeam.SMOKE_TEXTURE);
            VertexConsumer vc = buffers.getBuffer(type);
            for (Seam s : seams) {
                seam(vc, base, s);
            }
            buffers.endBatch(type);
        }
        if (!shreds.isEmpty()) {
            RenderType type = RenderType.eyes(DrainSoulBeam.GLOW_TEXTURE);
            VertexConsumer vc = buffers.getBuffer(type);
            for (Shred s : shreds) {
                shred(vc, base, s);
            }
            buffers.endBatch(type);
        }
        seams.clear();
        shreds.clear();
    }

    private static void seam(VertexConsumer vc, PoseStack.Pose base, Seam s) {
        Vec3[] p = s.points();
        int n = p.length;
        int count = s.closed() ? n + 1 : n;
        Vec3[] sides = new Vec3[count];
        float[] along = new float[count];
        double run = 0.0;
        for (int i = 0; i < count; i++) {
            Vec3 at = p[i % n];
            Vec3 prev = s.closed() ? p[(i - 1 + n) % n] : p[Math.max(0, i - 1)];
            Vec3 next = s.closed() ? p[(i + 1) % n] : p[Math.min(n - 1, i + 1)];
            Vec3 side = next.subtract(prev).cross(at);   // camera at the origin: across the view
            sides[i] = side.lengthSqr() < 1.0E-12 ? new Vec3(0.0, 1.0, 0.0) : side.normalize();
            if (i > 0) {
                run += at.distanceTo(p[(i - 1) % n]);
            }
            along[i] = (float) run;
        }
        float total = Math.max(1.0E-3F, along[count - 1]);
        Matrix4f m = base.pose();
        for (int i = 0; i < count - 1; i++) {
            Vec3 a = p[i % n];
            Vec3 b = p[(i + 1) % n];
            float ia = intensity(s, along[i] / total, i, count);
            float ib = intensity(s, along[i + 1] / total, i + 1, count);
            Vec3 sa = sides[i].scale(s.width());
            Vec3 sb = sides[i + 1].scale(s.width());
            float va = along[i] * 0.9F + s.time() * 0.05F;
            float vb = along[i + 1] * 0.9F + s.time() * 0.05F;
            vertex(vc, m, base, a.add(sa), 0.0F, va, ia);
            vertex(vc, m, base, a.subtract(sa), 1.0F, va, ia);
            vertex(vc, m, base, b.subtract(sb), 1.0F, vb, ib);
            vertex(vc, m, base, b.add(sb), 0.0F, vb, ib);
        }
    }

    /** Brighter where the wave of the chain is, faded at the open ends of a chain. */
    private static float intensity(Seam s, float u, int i, int count) {
        float wave = 0.5F + 0.5F * Mth.sin(Mth.TWO_PI * (u * 1.4F - s.time() * 0.045F));
        float v = s.strength() * (0.35F + 0.65F * wave * wave);
        if (!s.closed()) {
            v *= Mth.clamp(Math.min(u, 1.0F - u) / 0.12F, 0.0F, 1.0F);
        }
        return v;
    }

    private static void shred(VertexConsumer vc, PoseStack.Pose base, Shred s) {
        Vec3 side = s.along().cross(s.at());
        side = side.lengthSqr() < 1.0E-12 ? new Vec3(0.0, 1.0, 0.0) : side.normalize().scale(s.width() * 0.5F);
        Vec3 half = s.along().scale(s.length() * 0.5F);
        Matrix4f m = base.pose();
        float i = s.strength();
        vertex(vc, m, base, s.at().subtract(half).add(side), 0.0F, 0.0F, i);
        vertex(vc, m, base, s.at().subtract(half).subtract(side), 1.0F, 0.0F, i);
        vertex(vc, m, base, s.at().add(half).subtract(side), 1.0F, 1.0F, i);
        vertex(vc, m, base, s.at().add(half).add(side), 0.0F, 1.0F, i);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose base, Vec3 p, float u, float v, float intensity) {
        float i = Mth.clamp(intensity, 0.0F, 1.0F);
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(COLOR[0] * i, COLOR[1] * i, COLOR[2] * i, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(base, 0.0F, 1.0F, 0.0F);
    }
}
