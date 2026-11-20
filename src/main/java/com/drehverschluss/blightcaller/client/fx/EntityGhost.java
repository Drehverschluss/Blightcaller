package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an entity's own model a second time as a ghost: the same renderer, the same pose and animation, but every model
 * layer goes into ghost render types instead of its own (Phase 6.1: the soul torn out by Drain Soul; Phase 6.2: the curse
 * veil, and {@link #measure} measures the model for the ring of Gravebind). While a copy is drawn, the name tag part of
 * the renderer is skipped ({@code mixin.client.EntityRendererMixin}), so no name tag or health bar of other mods comes
 * along.
 * <p>
 * How: {@link #render} calls the entity's renderer ({@link EntityRenderer#render}) with a wrapping {@link MultiBufferSource}.
 * Every render type the renderer asks for is checked: model layers (vertex format {@code NEW_ENTITY} with a texture,
 * {@code entity_*}, {@code armor_*}, mod types the same way) are drawn instead with
 * <ul>
 *     <li>a <b>silhouette</b> ({@link FxRenderTypes#GHOST}: white texture, translucent, full bright, no depth write,
 *     back faces culled) in the ghost colour with the given alpha, and</li>
 *     <li>an additive <b>texture glow</b> ({@code eyes} with the layer's own texture), so the mob's texture stays readable
 *     as light inside the ghost.</li>
 * </ul>
 * Everything else (name tag text, shadow, eyes layers, energy swirl, glint, outline) is dropped. Colour, overlay (red hurt
 * flash) and light of the vertices are replaced. The two looks are drawn in two passes of the renderer, because the shared
 * buffer of Minecraft's buffer source holds one render type at a time.
 * <p>
 * The pose stack must stand at the entity's render position (camera relative, feet); the caller may add its own
 * transform (offset, stretch). Entities whose renderer draws nothing in an entity format (or fails) are remembered as
 * {@linkplain #supported unsupported}: callers use a fallback then (Drain Soul: soul shreds in the shape of the hitbox).
 */
public final class EntityGhost {
    /**
     * Look of a ghost.
     *
     * @param r     red of the ghost colour (0..1)
     * @param g     green
     * @param b     blue
     * @param alpha opacity of the silhouette (0 = no silhouette pass)
     * @param glow  strength of the additive texture glow (0 = no glow pass)
     */
    public record Style(float r, float g, float b, float alpha, float glow) {}

    private static final Set<EntityType<?>> UNSUPPORTED = new HashSet<>();
    private static boolean drawing;
    private static boolean warned;

    private EntityGhost() {}

    /** Whether the model of this entity type can be drawn as a ghost (false after a failed attempt). */
    public static boolean supported(EntityType<?> type) {
        return !UNSUPPORTED.contains(type);
    }

    /** True while a ghost is being drawn (renderers called from here must not start other effects). */
    public static boolean isDrawing() {
        return drawing;
    }

    /**
     * Draws the ghost of an entity. {@code poseStack} stands at the entity's render position (camera relative feet) with
     * any extra transform of the caller. Ends its own batches. Returns false if nothing could be drawn.
     */
    public static boolean render(Entity entity, PoseStack poseStack, float partialTick, MultiBufferSource.BufferSource buffers, Style style) {
        if (drawing || !supported(entity.getType())) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        int vertices = 0;
        if (style.alpha() > 0.003F) {
            vertices += pass(renderer, entity, poseStack, partialTick, buffers, style, false);
        }
        if (style.glow() > 0.003F && supported(entity.getType())) {
            vertices += pass(renderer, entity, poseStack, partialTick, buffers, style, true);
        }
        if (vertices == 0 && supported(entity.getType()) && !entity.isInvisible()) {
            UNSUPPORTED.add(entity.getType());
            Blightcaller.LOGGER.debug("Blightcaller: no model layers to draw as a ghost for {}", EntityType.getKey(entity.getType()));
        }
        return vertices > 0;
    }

    private static int pass(EntityRenderer<? super Entity> renderer, Entity entity, PoseStack poseStack, float partialTick,
                            MultiBufferSource.BufferSource buffers, Style style, boolean glow) {
        GhostBuffers ghost = new GhostBuffers(buffers, style, glow);
        drawing = true;
        poseStack.pushPose();
        try {
            Vec3 offset = renderer.getRenderOffset(entity, partialTick);
            poseStack.translate(offset.x, offset.y, offset.z);
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            renderer.render(entity, yaw, partialTick, poseStack, ghost, LightTexture.FULL_BRIGHT);
        } catch (Throwable t) {
            UNSUPPORTED.add(entity.getType());
            if (!warned) {
                warned = true;
                Blightcaller.LOGGER.warn("Blightcaller: could not draw {} as a ghost, using a fallback (reported once)",
                        EntityType.getKey(entity.getType()), t);
            }
        } finally {
            poseStack.popPose();
            drawing = false;
        }
        for (RenderType type : ghost.used) {
            buffers.endBatch(type);
        }
        return ghost.vertices[0];
    }

    /**
     * Size of the visible model near the ground (Phase 6.2: the ring of Gravebind lies around the legs of the model, not only
     * around the hitbox; the Netherite Monstrosity's legs stand far outside its hitbox). Runs the entity's renderer once
     * without drawing and reads the model's vertices: the horizontal distance from the feet's centre that 97 % of the
     * vertices in the lowest {@code share} of the model's height stay within, and the model's height above the feet: {radius, height},
     * -1 where nothing could be measured.
     */
    public static float[] measure(Entity entity, float partialTick, float share) {
        if (drawing || !supported(entity.getType())) {
            return new float[]{-1.0F, -1.0F};
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        Measure measure = new Measure();
        drawing = true;
        PoseStack poseStack = new PoseStack();
        try {
            Vec3 offset = renderer.getRenderOffset(entity, partialTick);
            poseStack.translate(offset.x, offset.y, offset.z);
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            renderer.render(entity, yaw, partialTick, poseStack, measure, LightTexture.FULL_BRIGHT);
        } catch (Throwable t) {
            return new float[]{-1.0F, -1.0F};
        } finally {
            drawing = false;
        }
        return new float[]{measure.footRadius(share), measure.size == 0 ? -1.0F : measure.maxY - Math.min(measure.minY, 0.0F)};
    }

    /** Collects the model's vertex positions (relative to the feet) instead of drawing. */
    private static final class Measure implements MultiBufferSource {
        private float[] points = new float[3 * 1024];
        private int size;
        private float minY = Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return modelTexture(type).isPresent() ? new Collector() : Sink.INSTANCE;
        }

        float footRadius(float share) {
            if (size == 0 || maxY <= minY) {
                return -1.0F;
            }
            float limit = minY + (maxY - minY) * share;
            float[] distances = new float[size / 3];
            int n = 0;
            for (int i = 0; i < size; i += 3) {
                if (points[i + 1] <= limit) {
                    distances[n++] = points[i] * points[i] + points[i + 2] * points[i + 2];
                }
            }
            if (n == 0) {
                return -1.0F;
            }
            // nearly the farthest, but not a single stray vertex (the Netherite Monstrosity has one 6.9 blocks out)
            java.util.Arrays.sort(distances, 0, n);
            return Mth.sqrt(distances[Math.min(n - 1, (int) (n * 0.97F))]);
        }

        private void add(float x, float y, float z) {
            if (size + 3 > points.length) {
                if (points.length >= 3 * 65536) {
                    return;
                }
                points = java.util.Arrays.copyOf(points, points.length * 2);
            }
            points[size++] = x;
            points[size++] = y;
            points[size++] = z;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        private final class Collector implements VertexConsumer {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                add(x, y, z);
                return this;
            }

            @Override
            public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                                  float nx, float ny, float nz) {
                add(x, y, z);
            }

            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        }
    }

    /** The texture of a model layer, or empty for everything that is not a textured entity model layer. */
    static Optional<ResourceLocation> modelTexture(RenderType type) {
        if (!(type instanceof RenderType.CompositeRenderType composite) || type.format() != DefaultVertexFormat.NEW_ENTITY) {
            return Optional.empty();
        }
        String name = type.name;
        if (name.equals("eyes") || name.equals("entity_shadow") || name.contains("glint") || name.contains("outline")
                || name.contains("energy_swirl") || name.contains("crumbling") || name.startsWith("blightcaller")) {
            return Optional.empty();
        }
        return composite.state().textureState.cutoutTexture();
    }

    /** Hands out ghost buffers instead of the renderer's own render types. */
    private static final class GhostBuffers implements MultiBufferSource {
        private final MultiBufferSource.BufferSource buffers;
        private final Style style;
        private final boolean glow;
        final Set<RenderType> used = new LinkedHashSet<>();
        final int[] vertices = new int[1];

        GhostBuffers(MultiBufferSource.BufferSource buffers, Style style, boolean glow) {
            this.buffers = buffers;
            this.style = style;
            this.glow = glow;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            Optional<ResourceLocation> texture = modelTexture(type);
            if (texture.isEmpty()) {
                return Sink.INSTANCE;
            }
            RenderType ghostType = glow ? RenderType.eyes(texture.get()) : FxRenderTypes.GHOST;
            used.add(ghostType);
            int color = glow
                    ? FastColor.ARGB32.colorFromFloat(1.0F, clamp(style.r() * style.glow()), clamp(style.g() * style.glow()), clamp(style.b() * style.glow()))
                    : FastColor.ARGB32.colorFromFloat(clamp(style.alpha()), style.r(), style.g(), style.b());
            return new Recolor(buffers.getBuffer(ghostType), color, vertices);
        }

        private static float clamp(float v) {
            return Mth.clamp(v, 0.0F, 1.0F);
        }
    }

    /** Replaces colour, overlay and light of every vertex. */
    private static final class Recolor implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int color;
        private final int[] vertices;

        Recolor(VertexConsumer delegate, int color, int[] vertices) {
            this.delegate = delegate;
            this.color = color;
            this.vertices = vertices;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices[0]++;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            vertices[0]++;
            delegate.addVertex(x, y, z, this.color, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, nx, ny, nz);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color),
                    FastColor.ARGB32.alpha(color));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setOverlay(OverlayTexture.NO_OVERLAY);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setLight(LightTexture.FULL_BRIGHT);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    /** Drops everything (layers that are not part of the ghost). */
    private static final class Sink implements VertexConsumer {
        static final Sink INSTANCE = new Sink();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
