package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The links of the ghostly chains (Gravebind), from model files, and the code that sets them along a path.
 * <p>
 * Every link is a small real 3D model (decided 20.11.: flat crossed planes read as squares from the side or as a ring):
 * {@code assets/blightcaller/models/fx/chain_link.json} near the camera, {@code chain_link_far.json} (fewer faces) far
 * away. Both are plain block models (Blockbench JSON) with the vanilla chain texture by reference, loaded with
 * {@link ModelEvent.RegisterAdditional}: a resource pack or the graphics phase can replace them without code. The link's
 * long axis is the model's Y axis; the distance between two links is taken from the model itself (two thirds of its
 * length along Y), so a longer or shorter link still hooks into the next one.
 * <p>
 * Along a path, the links sit between joints spaced one link distance apart: a link always reaches from one joint to the
 * next, so the chain stays closed on curves; every second link is turned by 90 degrees around its axis. Two passes: the
 * metal (vanilla entity cutout shader, lit by the world, a little light even at night) and an additive ghostly sheen on
 * the same faces.
 */
public final class ChainLinks {
    public static final ModelResourceLocation NEAR = ModelResourceLocation.standalone(Blightcaller.id("fx/chain_link"));
    public static final ModelResourceLocation FAR = ModelResourceLocation.standalone(Blightcaller.id("fx/chain_link_far"));

    private static List<BakedQuad> nearQuads;
    private static List<BakedQuad> farQuads;
    /** The faces of the models as plain vertex data (x, y, z, u, v, nx, ny, nz per vertex), written straight to the buffer. */
    private static float[] nearMesh;
    private static float[] farMesh;
    /** Distance between two links in model units (1 = one block at scale 1), from the near model. */
    private static float pitch = 6.0F / 16.0F;
    /** Width of a link across its open side, from the near model. */
    private static float width = 5.0F / 16.0F;

    private ChainLinks() {}

    static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(NEAR);
        event.register(FAR);
    }

    static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        nearQuads = null;
        farQuads = null;
    }

    static float pitch() {
        quads(false);
        return pitch;
    }

    static float width() {
        quads(false);
        return width;
    }

    static List<BakedQuad> quads(boolean far) {
        if (nearQuads == null) {
            nearQuads = load(NEAR);
            farQuads = load(FAR);
            measure(nearQuads);
            nearMesh = mesh(nearQuads);
            farMesh = mesh(farQuads);
        }
        return far ? farQuads : nearQuads;
    }

    private static List<BakedQuad> load(ModelResourceLocation location) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        List<BakedQuad> quads = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(null, direction, random, ModelData.EMPTY, null));
        }
        quads.addAll(model.getQuads(null, null, random, ModelData.EMPTY, null));
        return quads;
    }

    static float[] mesh(boolean far) {
        quads(far);
        return far ? farMesh : nearMesh;
    }

    /** Vertex data of the baked faces (block vertex format: position, colour, uv, light, normal per vertex). */
    private static float[] mesh(List<BakedQuad> quads) {
        float[] out = new float[quads.size() * 4 * 8];
        int o = 0;
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            net.minecraft.core.Vec3i n = quad.getDirection().getNormal();
            for (int v = 0; v < 4; v++) {
                int b = v * stride;
                out[o++] = Float.intBitsToFloat(data[b]);
                out[o++] = Float.intBitsToFloat(data[b + 1]);
                out[o++] = Float.intBitsToFloat(data[b + 2]);
                out[o++] = Float.intBitsToFloat(data[b + 4]);
                out[o++] = Float.intBitsToFloat(data[b + 5]);
                out[o++] = n.getX();
                out[o++] = n.getY();
                out[o++] = n.getZ();
            }
        }
        return out;
    }

    /** Link distance (two thirds of the length along Y) and width (along X) from the model's vertices. */
    private static void measure(List<BakedQuad> quads) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            for (int v = 0; v < 4; v++) {
                float x = Float.intBitsToFloat(data[v * stride]);
                float y = Float.intBitsToFloat(data[v * stride + 1]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxY > minY && maxX > minX) {
            pitch = (maxY - minY) * 2.0F / 3.0F;
            width = maxX - minX;
        }
    }

    /**
     * Draws links. One drawer per frame and render pass pair; {@link #chain} sets links along a path.
     */
    static final class Drawer {
        private final VertexConsumer metal;
        private final VertexConsumer sheen;
        private final PoseStack.Pose base;
        private final PoseStack pose = new PoseStack();
        private final Matrix3f basis = new Matrix3f();
        private final Matrix4f rotation = new Matrix4f();
        int links;

        /** {@code base}: the pose of the render pass (camera relative world space); {@code sheen} may be null. */
        Drawer(PoseStack.Pose base, VertexConsumer metal, VertexConsumer sheen) {
            this.base = base;
            this.metal = metal;
            this.sheen = sheen;
        }

        /** Styling of one chain: size, light, colours and how links fade, shrink or appear along it. */
        static final class Style {
            float scale = 1.0F;
            boolean far;
            int light;
            float metalR = 1.0F, metalG = 1.0F, metalB = 1.0F;
            float sheenR, sheenG, sheenB;
            /** Shown share of the chain from its start (0..1). */
            float shown = 1.0F;
            /** From this share on the links get smaller towards the end (hanging chains run out), 1 = never. */
            float taperFrom = 1.0F;
            /** Phase of the sheen wave running along the chain (game ticks). */
            float sheenTime;
            /** Brightness of the sheen (0 = none, also skips the pass). */
            float sheenStrength = 1.0F;
        }

        /**
         * Links along a path (camera relative points). {@code closed}: a ring, the last joint is the first one. {@code roll}:
         * the direction the open side of every second link faces (e.g. up for a ring lying on the ground).
         */
        void chain(Vec3[] points, boolean closed, Vec3 roll, Style style) {
            int count = points.length;
            if (count < 2) {
                return;
            }
            int segments = closed ? count : count - 1;
            double[] length = new double[segments + 1];
            for (int i = 0; i < segments; i++) {
                length[i + 1] = length[i] + points[(i + 1) % count].distanceTo(points[i]);
            }
            double total = length[segments];
            float step = pitch * style.scale;
            if (total < step * 0.5) {
                return;
            }
            int n = Math.max(1, (int) Math.round(total / step));
            if (closed && n % 2 == 1) {
                n++;   // even count: the turned links alternate all the way round
            }
            double spacing = total / n;
            int shownLinks = closed ? n : Mth.clamp(Math.round(style.shown * n), 0, n);
            Vec3 joint = points[0];
            int seg = 0;
            for (int k = 0; k < shownLinks; k++) {
                double at = (k + 1) * spacing;
                while (seg < segments - 1 && length[seg + 1] < at) {
                    seg++;
                }
                double segLength = length[seg + 1] - length[seg];
                double f = segLength < 1.0E-9 ? 0.0 : (at - length[seg]) / segLength;
                Vec3 next = points[seg].lerp(points[(seg + 1) % count], Mth.clamp(f, 0.0, 1.0));
                float along = (k + 0.5F) / n;
                float size = style.scale;
                if (along > style.taperFrom) {
                    size *= 1.0F - 0.6F * (along - style.taperFrom) / (1.0F - style.taperFrom);
                }
                float wave = 0.5F + 0.5F * Mth.sin(Mth.TWO_PI * (along * 1.4F - style.sheenTime * 0.045F));
                link(joint, next, roll, k % 2 == 1, size, style, wave * wave);
                joint = next;
            }
        }

        /** One link between two joints. */
        private void link(Vec3 a, Vec3 b, Vec3 roll, boolean turned, float size, Style style, float sheenWave) {
            Vec3 axis = b.subtract(a);
            double len = axis.length();
            if (len < 1.0E-6) {
                return;
            }
            axis = axis.scale(1.0 / len);
            Vec3 x = roll.subtract(axis.scale(roll.dot(axis)));
            if (x.lengthSqr() < 1.0E-6) {
                x = axis.cross(Math.abs(axis.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0));
            }
            x = x.normalize();
            Vec3 z = x.cross(axis);
            if (turned) {
                Vec3 t = x;
                x = z;
                z = t.scale(-1.0);
            }
            Vec3 center = a.add(b).scale(0.5);
            basis.set((float) x.x, (float) x.y, (float) x.z, (float) axis.x, (float) axis.y, (float) axis.z,
                    (float) z.x, (float) z.y, (float) z.z);
            Matrix4f m = pose.last().pose();
            m.set(base.pose())
                    .translate((float) center.x, (float) center.y, (float) center.z)
                    .mul(rotation.set(basis))
                    .scale(size)
                    .translate(-0.5F, -0.5F, -0.5F);
            pose.last().normal().set(base.normal()).mul(basis);
            float[] mesh = mesh(style.far);
            Matrix4f mat = pose.last().pose();
            Matrix3f nor = pose.last().normal();
            int metalColor = FastColor.ARGB32.colorFromFloat(1.0F, style.metalR, style.metalG, style.metalB);
            boolean shine = sheen != null && style.sheenStrength > 0.01F;
            int sheenColor = 0;
            if (shine) {
                float s = style.sheenStrength * (0.35F + 0.65F * sheenWave);
                sheenColor = FastColor.ARGB32.colorFromFloat(1.0F, Math.min(1.0F, style.sheenR * s), Math.min(1.0F, style.sheenG * s),
                        Math.min(1.0F, style.sheenB * s));
            }
            for (int i = 0; i < mesh.length; i += 8) {
                float mx = mesh[i], my = mesh[i + 1], mz = mesh[i + 2];
                float px = mat.m00() * mx + mat.m10() * my + mat.m20() * mz + mat.m30();
                float py = mat.m01() * mx + mat.m11() * my + mat.m21() * mz + mat.m31();
                float pz = mat.m02() * mx + mat.m12() * my + mat.m22() * mz + mat.m32();
                float ax = mesh[i + 5], ay = mesh[i + 6], az = mesh[i + 7];
                float nx = nor.m00() * ax + nor.m10() * ay + nor.m20() * az;
                float ny = nor.m01() * ax + nor.m11() * ay + nor.m21() * az;
                float nz = nor.m02() * ax + nor.m12() * ay + nor.m22() * az;
                metal.addVertex(px, py, pz, metalColor, mesh[i + 3], mesh[i + 4], OverlayTexture.NO_OVERLAY, style.light, nx, ny, nz);
                if (shine) {
                    sheen.addVertex(px, py, pz, sheenColor, mesh[i + 3], mesh[i + 4], OverlayTexture.NO_OVERLAY, 0xF000F0, nx, ny, nz);
                }
            }
            links++;
        }
    }
}
