package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.curse.CurseEffects;
import com.drehverschluss.blightcaller.curse.CurseView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The ghostly chains of Gravebind (concept 5, new direction decided 20.11., Phase 6.1).
 * <ul>
 *     <li><b>Taut from the ground:</b> 3, 4 or 6 chains (by size) shoot at a slant out of anchor points in the ground around
 *     the bound entity and hold the ring at its feet, as if the grave held it (decided 20.11. during Phase 6.1). A small dark crack marks each anchor. The chains
 *     are taut and tremble a little, more when the entity tries to move.</li>
 *     <li><b>At the feet:</b> one chain circles the entity and rises and falls in a wave that runs around it. Its radius
 *     follows the hitbox, so it does not cut through the model.</li>
 *     <li><b>Links:</b> real 3D links from a model file ({@link ChainLinks}), hooked into each other, dark iron (the vanilla
 *     chain) with a faint soul turquoise sheen running along the chains.</li>
 *     <li><b>Size by hitbox:</b> number of anchors, their distance, link size and ring radius grow with the entity
 *     (silverfish to Netherite Monstrosity). Entities with parts (Ender Dragon) are measured by their biggest part, the
 *     body, not by the 16 block wide box around all parts.</li>
 *     <li><b>Flyers:</b> close above the ground (up to 2.5 blocks plus half a block per size step) the chains still come from the
 *     ground; higher up (Wither, Ghast, Ender Dragon in the air) only the ring at the bottom of the body is left.</li>
 *     <li><b>Cast:</b> a wave of chain runs over the ground away from the caster, from 1.5 to 5 blocks ({@link #addWave},
 *     started by the particle {@code blightcaller:grave_wave}); the chains of a target shoot out of the ground when the
 *     wave reaches it. Nothing at the caster.</li>
 *     <li><b>End:</b> in the last half second the chains crumble to dust and sink into the ground.</li>
 * </ul>
 * Drawn from the synced curse view ({@link CurseView}), so every player sees the same, also on entities that refuse the
 * effect and on entities whose renderer is not a living entity renderer: one pass after the translucent blocks. Far
 * entities get fewer, simpler links ({@value #NEAR_LOD} / {@value #MID_LOD} blocks), and with many bound entities only the
 * {@value #FULL_DETAIL} nearest get the full links and the sheen; at most {@value #MAX_ENTITIES} bound entities within
 * {@value #MAX_DISTANCE} blocks.
 */
public final class GravebindChains {
    public static final ResourceLocation CRACK = Blightcaller.id("textures/entity/grave_crack.png");
    /** A plain white texture for the additive sheen (vanilla, by reference). */
    public static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int MAX_ENTITIES = 48;
    private static final double MAX_DISTANCE = 64.0;
    static final double NEAR_LOD = 24.0;
    static final double MID_LOD = 44.0;
    /** Bound entities nearest to the camera that get the full links and the sheen; the others are drawn simpler. */
    static final int FULL_DETAIL = 12;
    private static final float SHOOT_TICKS = 6.0F;
    private static final float FADE_TICKS = 10.0F;
    /** Cast wave: from 1.5 to 5 blocks around the caster (the spell's range) in 9 ticks, then it sinks in 5 ticks. */
    private static final float WAVE_FROM = 1.5F;
    private static final float WAVE_TO = 5.0F;
    private static final float WAVE_TICKS = 9.0F;
    private static final float WAVE_SINK = 5.0F;
    /** Dark iron (the vanilla chain is dark blue grey already) and the faint soul turquoise sheen. */
    private static final float[] METAL = {0.70F, 0.69F, 0.64F};
    private static final float[] SHEEN = {0.025F, 0.07F, 0.075F};
    /**
     * Thickness of the links across (width and bars) relative to the model, length unchanged (Phase 6.2: the links still
     * looked thick; compared at 1.0, 0.75 and 0.6: 0.6 still reads clearly as a chain up close and from afar).
     */
    static float slimness = 0.6F;
    /** Ghostly veil of the chains: strength by day and at night (by the light where the entity stands). */
    private static final float VEIL_DAY = 0.07F;
    private static final float VEIL_NIGHT = 0.85F;
    /** The visible model of an entity near the ground, measured now and then (Phase 6.2): entity id -> {tick, radius, height}. */
    private static final Map<Integer, float[]> MODEL = new HashMap<>();
    private static final int MODEL_REFRESH = 40;
    /** From this size step on (about a ravager) the visible model is measured; the foot radius counts up to this many hitbox halves. */
    private static final float BIG = 1.5F;
    private static final float MAX_FOOT = 2.2F;

    private record Wave(Vec3 origin, long start) {}

    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Map<Integer, Ground> GROUND = new HashMap<>();
    /** Bound entities drawn in the last frame and links drawn (for the probe and the report). */
    static int lastEntities;
    static int lastLinks;

    private GravebindChains() {}

    /** The links' metal: vanilla entity cutout on the block atlas, a fixed buffer of the buffer source (Sheets). */
    static RenderType metalType() {
        return Sheets.cutoutBlockSheet();
    }

    static RenderType sheenType() {
        return RenderType.eyes(WHITE);
    }

    static RenderType crackType() {
        return RenderType.entityTranslucent(CRACK);
    }

    /** A cast wave starting now at a caster's feet (called by the particle {@code blightcaller:grave_wave}). */
    static void addWave(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            WAVES.add(new Wave(new Vec3(x, y, z), mc.level.getGameTime()));
        }
    }

    // ------------------------------------------------------------------------------------------------ shape of one entity

    /** Where and how big the chains of one entity are (world coordinates, feet height). */
    /** {@code foot}: radius of the visible model at the feet of big mobs, -1 for the others (hitbox only). */
    private record Body(double x, double feet, double z, float half, float foot, float height, float scale, float linkScale) {}

    /** Ground under an entity: whether it stands in reach of the ground, and the ground height at every anchor. */
    private record Ground(long tick, boolean grounded, double[] anchorY) {}

    private static Body body(LivingEntity living, float partialTick) {
        AABB box = living.getBoundingBox();
        boolean dragon = living instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
        if (dragon && living.getParts() != null) {
            // the Ender Dragon: its own box spans all parts (16 blocks); the biggest part is the body. Its size counts; parts
            // that are not placed on this client (no AI) keep the entity's own position. Other entities with parts (Netherite
            // Monstrosity) keep their own box and are measured like every big mob (Phase 6.2).
            AABB part = null;
            double best = -1.0;
            for (PartEntity<?> p : living.getParts()) {
                AABB b = p.getBoundingBox();
                double volume = b.getXsize() * b.getYsize() * b.getZsize();
                if (volume > best) {
                    best = volume;
                    part = b;
                }
            }
            if (part != null) {
                Vec3 c = part.getCenter();
                double reach = Math.max(living.getBbWidth(), living.getBbHeight());
                boolean placed = c.distanceToSqr(living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ()) <= reach * reach;
                box = placed ? part : AABB.ofSize(new Vec3(living.getX(), living.getY() + part.getYsize() * 0.5, living.getZ()),
                        part.getXsize(), part.getYsize(), part.getZsize());
            }
        }
        Vec3 lerp = lerpPos(living, partialTick).subtract(living.position());
        box = box.move(lerp);
        float half = (float) Math.min(Math.max(box.getXsize(), box.getZsize()) * 0.5, 5.0);
        float height = (float) Math.min(box.getYsize(), 12.0);
        float scale = Mth.clamp(Mth.sqrt(2.0F * half * height / (0.6F * 1.95F)), 0.5F, 3.2F);
        float foot = -1.0F;
        if ((scale >= BIG || living.isMultipartEntity()) && !dragon) {
            // big mobs: the ring lies around the feet of the visible model, not only around the hitbox (Phase 6.2: the Netherite
            // Monstrosity is drawn much bigger than its hitbox and its chains vanished under its feet). Only for big mobs and at
            // most a little over twice the hitbox: small and long mobs (silverfish, tails) keep the ring of their hitbox.
            float[] model = model(living, partialTick);
            if (model != null) {
                foot = Math.min(model[1], half * MAX_FOOT);
                height = Math.min(Math.max(height, model[2]), 12.0F);
                scale = Mth.clamp(Mth.sqrt(2.0F * Math.max(half, foot * 0.8F) * height / (0.6F * 1.95F)), 0.5F, 3.2F);
            }
        }
        // a third smaller than first built (wish 20.11.: the links looked very thick)
        float linkScale = 0.4F * (float) Math.pow(scale, 0.85);
        return new Body((box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5, half, foot, height, scale, linkScale);
    }

    /** Foot radius and height of the visible model (cached), or null if it cannot be measured. */
    @org.jetbrains.annotations.Nullable
    private static float[] model(LivingEntity living, float partialTick) {
        long now = living.level().getGameTime();
        float[] cached = MODEL.get(living.getId());
        if (cached == null || now - (long) cached[0] > MODEL_REFRESH || now < (long) cached[0]) {
            float[] measured = EntityGhost.measure(living, partialTick, 0.1F);
            cached = new float[]{now, measured[0], measured[1]};
            MODEL.put(living.getId(), cached);
        }
        return cached[1] > 0.0F ? cached : null;
    }

    private static int anchorCount(Body body) {
        return body.scale() < 0.75F ? 3 : body.scale() < 2.0F ? 4 : 6;
    }

    private static float anchorAngle(LivingEntity living, int i, int count) {
        float base = (living.getId() * 0.618034F) % 1.0F * Mth.TWO_PI;
        float jitter = (((living.getId() * 31 + i * 17) % 11) / 11.0F - 0.5F) * 0.5F;
        return base + Mth.TWO_PI * i / count + jitter;
    }

    private static float ringRadius(Body body) {
        return Math.max(body.half() * 1.3F, body.foot() * 1.08F) + ChainLinks.width() * body.linkScale() * 0.6F;
    }

    /**
     * Anchors on the ground close outside the ring: short, steep chains that hold the ring down (Phase 6.2: far anchors read
     * like a spider of chains from afar and hid the ring).
     */
    private static double anchorDistance(Body body) {
        return ringRadius(body) + 0.12 + 0.14 * body.scale();
    }

    /**
     * Flyers close above the ground: the chains run together to the ground under the flyer, so they tie it down like a stake
     * instead of carrying it on legs (Phase 6.2: the low Wither looked like it stood on stilts).
     */
    private static double flyerAnchorDistance(Body body) {
        return ringRadius(body) * 0.25;
    }

    private static Ground ground(ClientLevel level, LivingEntity living, Body body) {
        long tick = level.getGameTime();
        Ground cached = GROUND.get(living.getId());
        if (cached != null && cached.tick() == tick) {
            return cached;
        }
        int count = anchorCount(body);
        // flyers close above the ground are still bound to it; higher up there are no chains from the ground
        double reach = 2.5 + 0.5 * body.scale();
        double center = living.onGround() ? body.feet() : groundBelow(level, body.x(), body.z(), body.feet() + 0.25, reach);
        boolean grounded = !Double.isNaN(center);
        double[] ys = new double[count];
        double distance = living.onGround() ? anchorDistance(body) : flyerAnchorDistance(body);
        for (int i = 0; i < count; i++) {
            float a = anchorAngle(living, i, count);
            double y = groundBelow(level, body.x() + Mth.cos(a) * distance, body.z() + Mth.sin(a) * distance,
                    body.feet() + 0.6 * body.scale(), reach + 1.0 + body.scale());
            ys[i] = Double.isNaN(y) ? (grounded ? center : body.feet()) : y;
        }
        Ground ground = new Ground(tick, grounded, ys);
        GROUND.put(living.getId(), ground);
        return ground;
    }

    /** Top of the highest solid block at x/z not above {@code from}, at most {@code reach} below it; NaN if none. */
    private static double groundBelow(ClientLevel level, double x, double z, double from, double reach) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(from);
        int bottom = Mth.floor(from - reach);
        for (int y = top; y >= bottom; y--) {
            pos.set(Mth.floor(x), y, Mth.floor(z));
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double surface = y + shape.max(Direction.Axis.Y);
                if (surface <= from + 0.01) {
                    return surface;
                }
            }
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /** Draws the chains of all bound entities in view and the cast waves. */
    static void render(PoseStack.Pose base, Vec3 cam, Frustum frustum, MultiBufferSource.BufferSource buffers, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        WAVES.removeIf(w -> now - w.start() > 100);
        if (now % 40 == 0) {
            GROUND.entrySet().removeIf(e -> now - e.getValue().tick() > 40);
            MODEL.entrySet().removeIf(e -> now - (long) e.getValue()[0] > 2L * MODEL_REFRESH);
        }
        List<LivingEntity> bound = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (bound.size() >= MAX_ENTITIES) {
                break;
            }
            if (!(entity instanceof LivingEntity living) || living.isRemoved()
                    || (mc.player != null && living.isInvisibleTo(mc.player))
                    || living.position().distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            CurseView view = living.getExistingData(CurseView.type()).orElse(null);
            if (view == null || view.line(CurseEffects.GRAVEBIND) == null) {
                continue;
            }
            float reach = Math.min(living.getBbWidth(), 16.0F) + 8.0F;
            if (!frustum.isVisible(living.getBoundingBox().inflate(reach, 2.0, reach))) {
                continue;
            }
            bound.add(living);
        }
        boolean waves = false;
        for (Wave w : WAVES) {
            waves |= now - w.start() <= WAVE_TICKS + WAVE_SINK;
        }
        lastEntities = bound.size();
        lastLinks = 0;
        if (bound.isEmpty() && !waves) {
            return;
        }
        ChainLinks.Drawer drawer = new ChainLinks.Drawer(base, buffers.getBuffer(metalType()), buffers.getBuffer(sheenType()));
        ChainVeil veil = new ChainVeil();
        List<float[]> cracks = new ArrayList<>();
        // many bound entities at once: only the nearest get the full links and the sheen
        bound.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(cam)));
        for (int rank = 0; rank < bound.size(); rank++) {
            LivingEntity living = bound.get(rank);
            CurseView.Line line = living.getExistingData(CurseView.type()).map(v -> v.line(CurseEffects.GRAVEBIND)).orElse(null);
            if (line != null) {
                drawEntity(level, drawer, veil, cracks, cam, living, line, now, partialTick, rank >= FULL_DETAIL);
            }
        }
        for (Wave w : WAVES) {
            drawWave(level, drawer, cam, w, now, partialTick);
        }
        buffers.endBatch(metalType());
        buffers.endBatch(sheenType());
        if (!veil.isEmpty()) {
            veil.flush(base, buffers);
        }
        if (!cracks.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(crackType());
            for (float[] c : cracks) {
                crack(vc, base, c);
            }
            buffers.endBatch(crackType());
        }
        lastLinks = drawer.links;
    }

    private static void drawEntity(ClientLevel level, ChainLinks.Drawer drawer, ChainVeil veil, List<float[]> cracks, Vec3 cam,
                                   LivingEntity living, CurseView.Line line, long now, float partialTick, boolean crowded) {
        float age = (now - line.start()) + partialTick - arrivalDelay(living, line.start());
        float left = (line.end() - now) - partialTick;
        float fade = Mth.clamp(left / FADE_TICKS, 0.0F, 1.0F);
        if (age < 0.0F || fade <= 0.0F) {
            return;
        }
        Body body = body(living, partialTick);
        Ground ground = ground(level, living, body);
        double distance = Math.sqrt(living.position().distanceToSqr(cam));
        boolean far = distance >= NEAR_LOD || crowded;
        float lodScale = distance >= MID_LOD ? 1.8F : crowded ? 1.5F : far ? 1.35F : 1.0F;
        float sheenLod = distance >= MID_LOD || crowded ? 0.0F : far ? 0.6F : 1.0F;
        float time = now + partialTick;
        // sinking into the ground at the end
        float sink = (1.0F - fade) * (0.35F * body.scale() + 0.2F);
        float size = body.linkScale() * (0.55F + 0.45F * fade);
        // how much the entity tries to move: the bound chains tremble more
        float want = Mth.clamp(living.walkAnimation.speed(partialTick) * 2.0F
                + (float) living.getDeltaMovement().horizontalDistance() * 8.0F, 0.0F, 1.0F);

        // the ghostly veil: faint by day, strong at night (by the light at the entity); no shreds far away or in crowds
        float veilStrength = distance >= MID_LOD ? 0.0F : veilStrength(level, body, partialTick) * fade * (crowded ? 0.6F : 1.0F);
        boolean shreds = !far && veilStrength > 0.0F;
        float veilWidth = ChainLinks.width() * size * slimness * 1.6F;

        // ---- the wave ring at the feet (rises out of the ground with the chains)
        float rise = easeOut(Mth.clamp(age / SHOOT_TICKS, 0.0F, 1.0F));
        Ring ring = ring(body, size, rise, sink, time);
        if (rise > 0.0F) {
            int samples = Mth.clamp(Math.round(Mth.TWO_PI * ring.radius() / (ChainLinks.pitch() * size * 0.5F)), 16, 96);
            Vec3[] points = new Vec3[samples];
            for (int k = 0; k < samples; k++) {
                points[k] = ring.at(body, Mth.TWO_PI * k / samples).subtract(cam);
            }
            ChainLinks.Drawer.Style style = style(size * (far ? 1.2F : 1.0F), far, light(level, body.x(), body.feet() + 0.3, body.z()),
                    sheenLod, time);
            drawer.chain(points, true, new Vec3(0.0, 1.0, 0.0), style);
            veil.seam(points, true, veilWidth, veilStrength * rise, time);
            if (shreds) {
                for (int k = 0; k < 3; k++) {
                    float u = ((time * 0.006F + k / 3.0F + living.getId() * 0.13F) % 1.0F + 1.0F) % 1.0F;
                    float start = u * samples;
                    int i0 = (int) start % samples;
                    veil.shred(new Vec3[]{points[i0], points[(i0 + 1) % samples]}, start - (int) start,
                            0.32F * body.scale(), 0.16F * body.scale(), veilStrength * 0.8F);
                }
            }
        }

        // ---- anchors: from the ground to the ring (decided 20.11.: the chains hold the ring, the ring holds the entity). High in
        // the air (Ender Dragon, Ghast, flying Wither) there are no chains from the ground, only the ring (decided 20.11.).
        int count = ground.grounded() ? anchorCount(body) : 0;
        double dist = living.onGround() ? anchorDistance(body) : flyerAnchorDistance(body);
        int light = light(level, body.x(), body.feet() + 0.5, body.z());
        for (int i = 0; i < count; i++) {
            float angle = anchorAngle(living, i, count);
            double cos = Mth.cos(angle);
            double sin = Mth.sin(angle);
            float grow = easeOut(Mth.clamp((age - i * 1.0F) / SHOOT_TICKS, 0.0F, 1.0F));
            if (grow <= 0.0F) {
                continue;
            }
            float snap = Mth.clamp(1.0F - (age - i * 1.0F - SHOOT_TICKS) / 8.0F, 0.0F, 1.0F);
            Vec3 ringPoint = ring.at(body, angle);
            Vec3 side = new Vec3(-sin, 0.0, cos);
            ChainLinks.Drawer.Style style = style(size * lodScale, far, light, sheenLod * (1.0F + 1.5F * (1.0F - grow)), time + i * 7.0F);
            Vec3 anchor = new Vec3(body.x() + cos * dist, ground.anchorY()[i] - 0.08 * size - sink, body.z() + sin * dist);
            float tremble = (0.008F + 0.05F * want + 0.07F * snap) * body.scale();
            Vec3[] points = taut(anchor, ringPoint, side, tremble, time, i);
            style.shown = Math.min(grow, fade);
            if (distance < 32.0) {
                cracks.add(new float[]{(float) (anchor.x - cam.x), (float) (ground.anchorY()[i] + 0.015 - cam.y),
                        (float) (anchor.z - cam.z), (0.35F + 0.18F * body.scale()) * grow, angle, 0.85F * fade,
                        light(level, anchor.x, ground.anchorY()[i] + 0.2, anchor.z)});
            }
            relative(points, cam);
            drawer.chain(points, false, side, style);
            float shown = Math.min(grow, fade);
            veil.seam(points, false, veilWidth, veilStrength * shown, time + i * 7.0F);
            if (shreds && grow >= 1.0F) {
                // a shred drifting up along the chain, from the anchor to the ring
                float u = ((time * 0.025F + i * 0.37F + living.getId() * 0.21F) % 1.0F + 1.0F) % 1.0F;
                veil.shred(points, u, 0.26F * body.scale(), 0.13F * body.scale(), veilStrength * 0.9F);
            }
        }
    }

    /** Strength of the ghostly veil: weak in full daylight, strong in the dark (light where the entity stands). */
    private static float veilStrength(ClientLevel level, Body body, float partialTick) {
        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(body.x(), body.feet() + 0.5, body.z()));
        float sky = LightTexture.sky(packed) / 15.0F * level.getSkyDarken(partialTick);
        float light = Math.max(LightTexture.block(packed) / 15.0F, sky);
        return Mth.lerp(Mth.clamp(light, 0.0F, 1.0F), VEIL_NIGHT, VEIL_DAY);
    }

    /** The wave ring at the feet: radius, height and wave (the wave runs once around the entity in 3 s). */
    private record Ring(float radius, double baseY, float amplitude, int waves, float time) {
        Vec3 at(Body body, float theta) {
            double y = baseY + amplitude * Mth.sin(waves * (theta - time * Mth.TWO_PI / 60.0F));
            return new Vec3(body.x() + Mth.cos(theta) * radius, y, body.z() + Mth.sin(theta) * radius);
        }
    }

    private static Ring ring(Body body, float size, float rise, float sink, float time) {
        float amplitude = 0.07F + 0.07F * body.scale();
        float lift = ChainLinks.width() * size * 0.55F + amplitude + 0.02F + Math.min(0.1F * body.height(), 0.6F);
        double baseY = body.feet() + lift - (1.0F - rise) * (lift + 0.25F * body.scale()) - sink;
        return new Ring(ringRadius(body), baseY, amplitude, body.scale() < 0.75F ? 2 : 3, time);
    }

    /** A taut chain from the ground anchor to the body, trembling a little sideways (zero at both ends). */
    private static Vec3[] taut(Vec3 from, Vec3 to, Vec3 side, float tremble, float time, int i) {
        int n = 10;
        Vec3[] points = new Vec3[n + 1];
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n;
            float env = Mth.sin(t * Mth.PI);
            double s = tremble * env * Mth.sin(time * 2.7F + i * 1.9F + t * 3.0F);
            double u = tremble * 0.6 * env * Mth.cos(time * 3.1F + i * 1.3F);
            points[k] = from.lerp(to, t).add(side.scale(s)).add(up.scale(u));
        }
        return points;
    }

    private static ChainLinks.Drawer.Style style(float scale, boolean far, int light, float sheen, float time) {
        ChainLinks.Drawer.Style style = new ChainLinks.Drawer.Style();
        style.scale = scale;
        style.slim = slimness;
        style.far = far;
        style.light = light;
        style.metalR = METAL[0];
        style.metalG = METAL[1];
        style.metalB = METAL[2];
        style.sheenR = SHEEN[0];
        style.sheenG = SHEEN[1];
        style.sheenB = SHEEN[2];
        style.sheenStrength = sheen;
        style.sheenTime = time;
        return style;
    }

    // ------------------------------------------------------------------------------------------------ cast wave

    private static void drawWave(ClientLevel level, ChainLinks.Drawer drawer, Vec3 cam, Wave wave,
                                 long now, float partialTick) {
        float a = (now - wave.start()) + partialTick;
        if (a < 0.0F || a > WAVE_TICKS + WAVE_SINK) {
            return;
        }
        float radius = WAVE_FROM + (WAVE_TO - WAVE_FROM) * easeOut(Math.min(a / WAVE_TICKS, 1.0F));
        float sink = a > WAVE_TICKS ? (a - WAVE_TICKS) / WAVE_SINK : 0.0F;
        float size = 0.4F * (1.0F - 0.3F * sink);
        float lift = ChainLinks.width() * size * 0.5F;
        int samples = Mth.clamp(Math.round(Mth.TWO_PI * radius / 0.12F), 24, 160);
        Vec3[] ring = new Vec3[samples];
        Vec3 o = wave.origin();
        for (int k = 0; k < samples; k++) {
            float theta = Mth.TWO_PI * k / samples;
            double x = o.x + Mth.cos(theta) * radius;
            double z = o.z + Mth.sin(theta) * radius;
            double gy = groundBelow(level, x, z, o.y + 1.0, 3.0);
            double y = (Double.isNaN(gy) ? o.y : gy) + lift + 0.06 * Mth.sin(5.0F * theta + a * 0.8F) - sink * 0.35;
            ring[k] = new Vec3(x - cam.x, y - cam.y, z - cam.z);
        }
        boolean far = o.distanceTo(cam) >= NEAR_LOD;
        ChainLinks.Drawer.Style style = style(size, far, light(level, o.x, o.y + 0.5, o.z), 1.6F * (1.0F - sink), a * 4.0F);
        drawer.chain(ring, true, new Vec3(0.0, 1.0, 0.0), style);
    }

    /** Ticks after the curse landed until the cast wave reaches this entity (0 without a wave). */
    private static float arrivalDelay(LivingEntity living, long start) {
        for (Wave w : WAVES) {
            if (Math.abs(w.start() - start) <= 3) {
                double dx = living.getX() - w.origin().x;
                double dz = living.getZ() - w.origin().z;
                double d = Math.sqrt(dx * dx + dz * dz);
                float x = Mth.clamp((float) (d - WAVE_FROM) / (WAVE_TO - WAVE_FROM), 0.0F, 1.0F);
                float t = WAVE_TICKS * (1.0F - (float) Math.cbrt(1.0F - x));
                return Math.max(0.0F, t + (w.start() - start));
            }
        }
        return 0.0F;
    }

    // ------------------------------------------------------------------------------------------------ crack and dust

    /** A dark crack lying on the ground at an anchor: {x, y, z (camera relative), size, angle, alpha, light}. */
    private static void crack(VertexConsumer vc, PoseStack.Pose base, float[] c) {
        float size = c[3];
        if (size <= 0.01F || c[5] <= 0.01F) {
            return;
        }
        Matrix4f m = base.pose();
        float cos = Mth.cos(c[4]) * size * 0.5F;
        float sin = Mth.sin(c[4]) * size * 0.5F;
        int light = (int) c[6];
        float[][] corners = {{-cos + sin, -sin - cos}, {cos + sin, sin - cos}, {cos - sin, sin + cos}, {-cos - sin, -sin + cos}};
        float[][] uv = {{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};
        for (int k = 3; k >= 0; k--) {
            vc.addVertex(m, c[0] + corners[k][0], c[1], c[2] + corners[k][1])
                    .setColor(1.0F, 1.0F, 1.0F, c[5])
                    .setUv(uv[k][0], uv[k][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(base, 0.0F, 1.0F, 0.0F);
        }
    }

    /** End of the curse (last half second, client tick): the chains crumble to dark dust along their way. */
    static void dust(LivingEntity living, CurseView.Line line) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || living.position().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 32.0 * 32.0) {
            return;
        }
        long now = level.getGameTime();
        float left = line.end() - now;
        if (left <= 0.0F || left > FADE_TICKS) {
            return;
        }
        Body body = body(living, 1.0F);
        Ground ground = ground(level, living, body);
        RandomSource random = living.getRandom();
        int count = anchorCount(body);
        double dist = living.onGround() ? anchorDistance(body) : flyerAnchorDistance(body);
        Ring ring = ring(body, body.linkScale(), 1.0F, 0.0F, now);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.20F, 0.21F, 0.26F), 0.9F + 0.4F * body.scale());
        int particles = ground.grounded() ? 2 + count / 2 : 0;
        for (int n = 0; n < particles; n++) {
            int i = random.nextInt(count);
            float angle = anchorAngle(living, i, count);
            double cos = Mth.cos(angle);
            double sin = Mth.sin(angle);
            Vec3 top = ring.at(body, angle);
            Vec3 bottom = new Vec3(body.x() + cos * dist, ground.anchorY()[i], body.z() + sin * dist);
            Vec3 p = bottom.lerp(top, random.nextFloat() * (left / FADE_TICKS));
            level.addParticle(dust, p.x, p.y, p.z, 0.0, -0.03, 0.0);
        }
        for (int n = ground.grounded() ? 1 : 3; n > 0; n--) {
            Vec3 r = ring.at(body, random.nextFloat() * Mth.TWO_PI);
            level.addParticle(dust, r.x, r.y, r.z, 0.0, -0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static int light(ClientLevel level, double x, double y, double z) {
        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        // a little light even at night: the iron stays readable, but darker than by day
        return LightTexture.pack(Math.max(LightTexture.block(packed), 6), LightTexture.sky(packed));
    }

    private static void relative(Vec3[] points, Vec3 cam) {
        for (int k = 0; k < points.length; k++) {
            points[k] = points[k].subtract(cam);
        }
    }

    static Vec3 lerpPos(Entity e, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()), Mth.lerp(partialTick, e.zo, e.getZ()));
    }

    private static float easeOut(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    static void clearWaves() {
        WAVES.clear();
    }
}
