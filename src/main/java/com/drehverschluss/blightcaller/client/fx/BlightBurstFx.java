package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.entity.SpellProjectile;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Blight Burst in flight (phase 12B.1, wish of the user): an own look instead of a Spell Engine model; Hex Bolt gets the
 * same kind of trail behind its skull model, as a violet soul flame. The spell has no
 * {@code composite_model}, so Spell Engine draws nothing for the projectile; this class draws, in the pass after the translucent
 * blocks ({@link FxClient}):
 * <ul>
 *     <li>a ball of slime: a round, slightly wobbling mesh, stretched along the flight, translucent green with a darker rim, a
 *     pale glowing core seen through it and a glossy spot;</li>
 *     <li>a trail: a tapering ribbon through the last positions (at most {@link #TRAIL_LENGTH} blocks), dark slime outside and a
 *     pale core, fading out, with a slime drop falling now and then (vanilla particle, follows the particle setting);</li>
 *     <li>at the end of the flight (the projectile is gone: hit or out of range) a splash: the ball swells and fades, drops of
 *     slime fly apart and fall, and a few vanilla slime particles.</li>
 * </ul>
 * Only the client; the spell (damage, area, timing) is unchanged. Every visual is drawn emissive, like the other visuals of the
 * mod, so it reads at night and with shader packs.
 */
public final class BlightBurstFx {
    /** The spell whose projectiles are drawn here. */
    public static final ResourceLocation SPELL = Blightcaller.id("blight_burst");
    /**
     * Hex Bolt keeps its skull model (Spell Engine draws it) and gets only the trail here, as a violet soul flame (12B.1: the
     * flame particles of Spell Engine cannot be tinted and stayed orange like fire).
     */
    public static final ResourceLocation HEX_BOLT = Blightcaller.id("hex_bolt");
    private static final ResourceLocation WHITE = FxRenderTypes.WHITE;
    private static final ResourceLocation TRAIL_TEXTURE = Blightcaller.id("textures/entity/slime_trail.png");

    /** Radius of the ball (blocks) and how much it stretches along the flight. */
    private static final float RADIUS = 0.27F;
    private static final float STRETCH = 0.22F;
    /** Length of the trail (blocks) and its width at the ball. */
    private static final double TRAIL_LENGTH = 2.4;
    private static final float TRAIL_WIDTH = 0.36F;
    /** Mesh of the ball: rings from pole to pole and segments around. */
    private static final int RINGS = 10;
    private static final int SEGMENTS = 16;
    /** Ticks of the splash and number of drops. */
    private static final int SPLASH_TICKS = 12;
    private static final int DROPS = 9;
    private static final int MAX_TRAILS = 32;

    // slime colours (0..1): body, rim, core, the dark outside of the trail
    private static final float[] BODY = {0.42F, 0.78F, 0.30F};
    private static final float[] RIM = {0.16F, 0.36F, 0.12F};
    private static final float[] CORE = {0.80F, 1.00F, 0.62F};
    private static final float[] TRAIL_DARK = {0.20F, 0.40F, 0.14F};
    /** Hex Bolt: the soul flame trail (length, width; dark violet outside, violet, pale core; the colour plan's #6F5BE8). */
    private static final double SOUL_LENGTH = 1.3;
    private static final float SOUL_WIDTH = 0.8F;
    private static final float[] SOUL_DARK = {0.18F, 0.12F, 0.42F};
    private static final float[] SOUL = {0.44F, 0.36F, 0.91F};
    private static final float[] SOUL_CORE = {0.55F, 0.50F, 0.80F};
    /** Yellower patches of the slime and the darker lumps floating in it. */
    private static final float[] YELLOW = {0.70F, 0.88F, 0.30F};
    private static final float[] LUMP = {0.14F, 0.30F, 0.10F};

    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        /** true: Blight Burst (ball, beads, drops, splash); false: Hex Bolt (only the violet trail). */
        boolean slime = true;
        Vec3 velocity = Vec3.ZERO;
        long lastSeen;
        int fading = -1;
    }

    private record Drop(Vec3 from, Vec3 velocity, float size) {}

    private record Splash(Vec3 at, long start, List<Drop> drops) {}

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final List<Splash> SPLASHES = new ArrayList<>();
    /** Projectiles drawn in the last frame (probe, report). */
    static int lastBalls;

    private BlightBurstFx() {}

    static boolean isOurs(Entity e) {
        return e instanceof SpellProjectile p && p.getSpellEntry() != null && p.getSpellEntry().is(SPELL);
    }

    static boolean isHexBolt(Entity e) {
        return e instanceof SpellProjectile p && p.getSpellEntry() != null && p.getSpellEntry().is(HEX_BOLT);
    }

    private static Vec3 center(Entity e, float partialTick) {
        return e.getPosition(partialTick).add(0.0, e.getBbHeight() * 0.5, 0.0);
    }

    // ------------------------------------------------------------------------------------------------ client tick

    /** Records the positions of the flying balls, starts the splash of balls that are gone, drips slime from the trails. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            TRAILS.clear();
            SPLASHES.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        for (Entity e : level.entitiesForRendering()) {
            boolean slime = isOurs(e);
            if (!(slime || isHexBolt(e)) || !e.isAlive()) {
                continue;
            }
            if (TRAILS.size() >= MAX_TRAILS && !TRAILS.containsKey(e.getId())) {
                continue;
            }
            Trail t = TRAILS.computeIfAbsent(e.getId(), k -> new Trail());
            t.slime = slime;
            Vec3 c = center(e, 1.0F);
            t.points.addFirst(c);
            t.velocity = e.getDeltaMovement();
            t.lastSeen = now;
            trim(t.points, TRAIL_LENGTH + 1.5);
            // now and then a drop of slime falls from the trail
            if (slime && t.points.size() > 2 && level.random.nextFloat() < 0.55F) {
                Vec3 p = t.points.stream().skip(1 + level.random.nextInt(t.points.size() - 1)).findFirst().orElse(c);
                level.addParticle(ParticleTypes.ITEM_SLIME, p.x, p.y - 0.1, p.z, 0.0, -0.05, 0.0);
            }
        }
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Trail> entry = it.next();
            Trail t = entry.getValue();
            if (t.lastSeen == now) {
                continue;
            }
            if (t.fading < 0) {
                // the ball is gone: a splash where it was last, a little further along its flight
                t.fading = 0;
                Vec3 at = t.points.isEmpty() ? null : t.points.peekFirst().add(t.velocity.scale(0.5));
                if (at != null && t.slime) {
                    splash(level, at, t.velocity, now);
                }
            }
            // the trail shrinks towards the splash and is gone after a few ticks
            t.fading++;
            if (!t.points.isEmpty()) {
                t.points.pollLast();
            }
            if (t.points.size() < 2 || t.fading > 6) {
                it.remove();
            }
        }
        SPLASHES.removeIf(s -> now - s.start() > SPLASH_TICKS);
    }

    private static void splash(ClientLevel level, Vec3 at, Vec3 velocity, long now) {
        RandomSource r = level.random;
        List<Drop> drops = new ArrayList<>();
        Vec3 back = velocity.lengthSqr() > 1.0E-6 ? velocity.normalize().scale(-0.06) : Vec3.ZERO;
        for (int i = 0; i < DROPS; i++) {
            double a = Mth.TWO_PI * i / DROPS + r.nextFloat() * 0.6;
            double up = 0.10 + r.nextFloat() * 0.16;
            double out = 0.10 + r.nextFloat() * 0.12;
            drops.add(new Drop(at, new Vec3(Math.cos(a) * out, up, Math.sin(a) * out).add(back), 0.06F + r.nextFloat() * 0.06F));
        }
        SPLASHES.add(new Splash(at, now, drops));
        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, (r.nextFloat() - 0.5) * 0.3, r.nextFloat() * 0.25,
                    (r.nextFloat() - 0.5) * 0.3);
        }
    }

    private static void trim(ArrayDeque<Vec3> points, double length) {
        double run = 0.0;
        Vec3 prev = null;
        int keep = 0;
        for (Vec3 p : points) {
            if (prev != null) {
                run += p.distanceTo(prev);
            }
            keep++;
            if (run > length) {
                break;
            }
            prev = p;
        }
        while (points.size() > keep) {
            points.pollLast();
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    static void render(PoseStack.Pose base, Vec3 cam, Camera camera, Frustum frustum, MultiBufferSource.BufferSource buffers,
                       float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        List<Entity> balls = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (isOurs(e) && e.isAlive() && frustum.isVisible(new AABB(e.position(), e.position()).inflate(TRAIL_LENGTH + 1.0))) {
                balls.add(e);
            }
        }
        lastBalls = balls.size();
        if (balls.isEmpty() && TRAILS.isEmpty() && SPLASHES.isEmpty()) {
            return;
        }
        Matrix4f m = base.pose();

        // 1. trails: dark slime (alpha blended), then the pale core (additive)
        List<Vec3[]> paths = new ArrayList<>();
        List<Float> fades = new ArrayList<>();
        List<Vec3[]> souls = new ArrayList<>();
        List<Float> soulFades = new ArrayList<>();
        for (Map.Entry<Integer, Trail> entry : TRAILS.entrySet()) {
            Trail t = entry.getValue();
            Entity e = level.getEntity(entry.getKey());
            List<Vec3> pts = new ArrayList<>();
            if (e != null && e.isAlive() && nearCamera(e, cam)) {
                // right at the launch close to the camera (first person) no trail in front of the eye
                continue;
            }
            boolean flying = e != null && e.isAlive() && t.fading < 0;
            if (flying) {
                // the ball drawn between its last two positions: the newest stored point lies ahead of it, so it is left out
                pts.add(center(e, partialTick));
            }
            if (flying) {
                // right after the launch the ball is drawn behind stored positions: points ahead of the ball are left out, so the
                // trail never pokes out in front of it
                Vec3 head = pts.get(0);
                Vec3 dir = e.getDeltaMovement().lengthSqr() > 1.0E-6 ? e.getDeltaMovement().normalize() : Vec3.ZERO;
                t.points.stream().skip(1).filter(q -> q.subtract(head).dot(dir) <= 0.02).forEach(pts::add);
            } else {
                pts.addAll(t.points);
            }
            if (pts.size() < 2) {
                continue;
            }
            float fade = t.fading < 0 ? 1.0F : Mth.clamp(1.0F - t.fading / 6.0F, 0.0F, 1.0F);
            if (t.slime) {
                paths.add(cut(pts, TRAIL_LENGTH));
                fades.add(fade);
            } else {
                souls.add(cut(pts, SOUL_LENGTH));
                soulFades.add(fade);
            }
        }
        RenderType haze = RenderType.entityTranslucentEmissive(TRAIL_TEXTURE);
        VertexConsumer vc = buffers.getBuffer(haze);
        for (int i = 0; i < paths.size(); i++) {
            ribbon(vc, base, m, paths.get(i), cam, TRAIL_WIDTH, TRAIL_DARK, 0.85F * fades.get(i), time);
        }
        for (int i = 0; i < souls.size(); i++) {
            ribbon(vc, base, m, souls.get(i), cam, SOUL_WIDTH, SOUL_DARK, 0.8F * soulFades.get(i), time * 1.6F, 1.6F, 0.35F, 0.0F);
        }
        buffers.endBatch(haze);
        RenderType core = RenderType.eyes(TRAIL_TEXTURE);
        vc = buffers.getBuffer(core);
        for (int i = 0; i < paths.size(); i++) {
            float f = fades.get(i);
            ribbon(vc, base, m, paths.get(i), cam, TRAIL_WIDTH * 0.45F, new float[]{BODY[0] * f, BODY[1] * f, BODY[2] * f}, 1.0F, time);
        }
        for (int i = 0; i < souls.size(); i++) {
            float f = soulFades.get(i);
            ribbon(vc, base, m, souls.get(i), cam, SOUL_WIDTH * 0.75F, new float[]{SOUL[0] * f, SOUL[1] * f, SOUL[2] * f}, 1.0F, time * 1.6F,
                    1.4F, 0.4F, 2.1F);
            ribbon(vc, base, m, souls.get(i), cam, SOUL_WIDTH * 0.35F, new float[]{SOUL_CORE[0] * f, SOUL_CORE[1] * f, SOUL_CORE[2] * f}, 1.0F,
                    time * 1.6F, 1.8F, 0.3F, 4.0F);
        }
        buffers.endBatch(core);

        // 2. the cores and halos (additive, drawn first so the translucent body lies over them)
        RenderType glow = RenderType.eyes(DrainSoulBeam.GLOW_TEXTURE);
        vc = buffers.getBuffer(glow);
        for (Entity e : balls) {
            if (nearCamera(e, cam)) {
                continue;
            }
            Vec3 c = center(e, partialTick).subtract(cam);
            billboard(vc, base, m, c, camera, RADIUS * 4.2F, 0.10F, 0.22F, 0.07F);
            billboard(vc, base, m, c, camera, RADIUS * 1.5F, CORE[0] * 0.8F, CORE[1] * 0.8F, CORE[2] * 0.8F);
        }
        long now = level.getGameTime();
        for (Splash s : SPLASHES) {
            float a = (now - s.start()) + partialTick;
            float k = Mth.clamp(1.0F - a / 6.0F, 0.0F, 1.0F);
            if (k > 0.0F) {
                billboard(vc, base, m, s.at().subtract(cam), camera, RADIUS * (3.0F + 5.0F * (1.0F - k)), 0.25F * k, 0.5F * k, 0.15F * k);
            }
        }
        buffers.endBatch(glow);

        // 3. the bodies (alpha blended, emissive): first the lumps floating inside the ball and the beads of slime along the trail,
        // then the ball (it lies over its lumps), the swelling ball of a splash and the drops
        RenderType body = RenderType.entityTranslucentEmissive(WHITE);
        vc = buffers.getBuffer(body);
        for (Entity e : balls) {
            if (nearCamera(e, cam)) {
                continue;
            }
            Vec3 c = center(e, partialTick).subtract(cam);
            if (c.lengthSqr() < 16.0) {
                // close to the camera (first person, just launched) the lumps would sit big in the middle of the view
                continue;
            }
            float seed = e.getId() * 1.7F;
            for (int k = 0; k < 4; k++) {
                float a = time * (0.08F + 0.03F * k) + seed + k * 1.9F;
                Vec3 off = new Vec3(Mth.cos(a), Mth.sin(a * 1.3F) * 0.7, Mth.sin(a)).scale(RADIUS * (0.3 + 0.12 * k));
                blob(vc, base, m, c.add(off), RADIUS * (0.16F + 0.05F * (k % 2)), LUMP, 0.95F);
            }
        }
        for (Vec3[] path : paths) {
            // beads of slime along the trail, smaller to its end, sagging a little
            for (int i = 1; i < path.length; i++) {
                float u = i / (float) path.length;
                float size = RADIUS * 0.42F * (1.0F - u);
                if (size > 0.02F) {
                    blob(vc, base, m, path[i].subtract(cam).add(0.0, -0.08 * i, 0.0), size, BODY, 0.75F * (1.0F - u));
                }
            }
        }
        for (Entity e : balls) {
            if (nearCamera(e, cam)) {
                continue;
            }
            Vec3 c = center(e, partialTick);
            Vec3 dir = e.getDeltaMovement().lengthSqr() > 1.0E-6 ? e.getDeltaMovement().normalize() : new Vec3(0.0, 0.0, 1.0);
            sphere(vc, base, m, c.subtract(cam), c.subtract(cam).normalize(), dir, RADIUS, STRETCH, time + e.getId() * 3.1F, 0.82F, true);
        }
        for (Splash s : SPLASHES) {
            float a = (now - s.start()) + partialTick;
            float k = Mth.clamp(1.0F - a / 5.0F, 0.0F, 1.0F);
            Vec3 c = s.at().subtract(cam);
            if (k > 0.0F) {
                sphere(vc, base, m, c, c.normalize(), new Vec3(0.0, 1.0, 0.0), RADIUS * (1.0F + 0.9F * (1.0F - k)), -0.35F * (1.0F - k),
                        time * 2.0F, 0.7F * k, false);
            }
            float life = Mth.clamp(1.0F - a / SPLASH_TICKS, 0.0F, 1.0F);
            for (Drop d : s.drops()) {
                Vec3 p = d.from().add(d.velocity().scale(a)).add(0.0, -0.5 * 0.04 * a * a, 0.0).subtract(cam);
                Vec3 v = d.velocity().add(0.0, -0.04 * a, 0.0);
                sphere(vc, base, m, p, p.normalize(), v.lengthSqr() > 1.0E-6 ? v.normalize() : new Vec3(0, 1, 0), d.size() * (0.4F + 0.6F * life),
                        0.5F, time, 0.85F * Math.min(1.0F, life * 2.0F), false);
            }
        }
        buffers.endBatch(body);

        // 4. wet look: a sharp glossy spot and a small second one on the side towards the camera (additive)
        vc = buffers.getBuffer(glow);
        Vec3 camUp = new Vec3(camera.getUpVector());
        Vec3 camLeft = new Vec3(camera.getLeftVector());
        for (Entity e : balls) {
            if (nearCamera(e, cam)) {
                continue;
            }
            Vec3 c = center(e, partialTick).subtract(cam);
            Vec3 toCam = c.normalize().scale(-RADIUS * 0.95);
            billboard(vc, base, m, c.add(toCam).add(camUp.scale(RADIUS * 0.42)).add(camLeft.scale(RADIUS * 0.34)), camera, RADIUS * 0.42F,
                    0.85F, 0.95F, 0.8F);
            billboard(vc, base, m, c.add(toCam).add(camUp.scale(-RADIUS * 0.35)).add(camLeft.scale(-RADIUS * 0.4)), camera, RADIUS * 0.2F,
                    0.35F, 0.45F, 0.3F);
        }
        buffers.endBatch(glow);
    }

    /** A small round lump (the same mesh as the ball, without stretch and drip, in one colour). */
    private static void blob(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3 c, float radius, float[] rgb, float alpha) {
        sphereColored(vc, base, m, c, c.normalize(), new Vec3(0.0, 1.0, 0.0), radius, 0.0F, 0.0F, alpha, false, rgb);
    }

    /** Spell Engine hides projectile models for the first ticks close to the camera (first person); the ball does the same. */
    private static boolean nearCamera(Entity e, Vec3 cam) {
        return e.tickCount < 2 && e.position().distanceToSqr(cam) < 12.25;
    }

    /** The points of a path cut to {@code length} blocks from its start (the last piece shortened). */
    private static Vec3[] cut(List<Vec3> pts, double length) {
        List<Vec3> out = new ArrayList<>();
        out.add(pts.get(0));
        double run = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            Vec3 a = pts.get(i - 1);
            Vec3 b = pts.get(i);
            double d = a.distanceTo(b);
            if (run + d >= length) {
                out.add(a.lerp(b, d > 1.0E-6 ? (length - run) / d : 0.0));
                break;
            }
            run += d;
            out.add(b);
        }
        return out.toArray(new Vec3[0]);
    }

    /** A camera facing ribbon through the points (start = the ball), tapering and fading to the end. */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3[] pts, Vec3 cam, float width, float[] rgb,
                               float alpha, float time) {
        ribbon(vc, base, m, pts, cam, width, rgb, alpha, time, 0.7F, 0.15F, 0.0F);
    }

    /**
     * {@code taper}: how fast the ribbon gets thin (higher = wide at the start, then quickly pointed); {@code flicker}: how much
     * its width flickers (tongues of flame); {@code phase}: shifts the flicker (two layers with different tongues).
     */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3[] pts, Vec3 cam, float width, float[] rgb,
                               float alpha, float time, float taper, float flicker, float phase) {
        int n = pts.length;
        double total = 0.0;
        for (int i = 1; i < n; i++) {
            total += pts[i].distanceTo(pts[i - 1]);
        }
        if (total < 1.0E-3) {
            return;
        }
        Vec3[] left = new Vec3[n];
        Vec3[] right = new Vec3[n];
        float[] a = new float[n];
        float[] v = new float[n];
        double run = 0.0;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                run += pts[i].distanceTo(pts[i - 1]);
            }
            Vec3 t = (i < n - 1 ? pts[i + 1].subtract(pts[i]) : pts[i].subtract(pts[i - 1]));
            Vec3 view = pts[i].subtract(cam);
            Vec3 side = t.cross(view);
            side = side.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : side.normalize();
            float u = (float) (run / total);
            // thick at the ball, thin at the end, a slow swell running along it
            float w = width * (float) Math.pow(1.0F - u, taper)
                    * (1.0F - flicker + flicker * (0.5F + 0.5F * Mth.sin(u * 9.0F - time * 0.5F + phase)));
            // a flame licks: towards its tip it sways sideways (the slime trail does not, flicker 0.15)
            Vec3 p = pts[i].subtract(cam).add(side.scale(flicker > 0.2F ? 0.22F * width * u * Mth.sin(u * 7.0F - time * 0.9F + phase) : 0.0F));
            left[i] = p.add(side.scale(w * 0.5F));
            right[i] = p.subtract(side.scale(w * 0.5F));
            a[i] = alpha * (1.0F - u) * (1.0F - u * 0.3F);
            v[i] = (float) (run * 0.6 - time * 0.05);
        }
        for (int i = 0; i < n - 1; i++) {
            vertex(vc, base, m, left[i], 0.0F, v[i], rgb, a[i]);
            vertex(vc, base, m, right[i], 1.0F, v[i], rgb, a[i]);
            vertex(vc, base, m, right[i + 1], 1.0F, v[i + 1], rgb, a[i + 1]);
            vertex(vc, base, m, left[i + 1], 0.0F, v[i + 1], rgb, a[i + 1]);
        }
    }

    /**
     * A ball of slime around {@code c} (camera relative): stretched by {@code stretch} along {@code dir} (negative: squashed),
     * wobbling, lit from above, its rim darker (the edge towards the camera's view), translucent.
     */
    private static void sphere(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3 c, Vec3 view, Vec3 dir, float radius,
                               float stretch, float time, float alpha, boolean drip) {
        sphereColored(vc, base, m, c, view, dir, radius, stretch, time, alpha, drip, null);
    }

    /**
     * {@code drip}: the back of the ball draws out into a hanging drop that sags (slime pulled along by the flight);
     * {@code flat}: one colour (lumps, beads) instead of the slime shading.
     */
    private static void sphereColored(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3 c, Vec3 view, Vec3 dir, float radius,
                                      float stretch, float time, float alpha, boolean drip, float[] flat) {
        if (alpha <= 0.01F || radius <= 0.001F) {
            return;
        }
        Vec3 ax = dir.normalize();
        Vec3 helper = Math.abs(ax.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 bx = ax.cross(helper).normalize();
        Vec3 cx = ax.cross(bx).normalize();
        Vec3[][] pos = new Vec3[RINGS + 1][SEGMENTS + 1];
        float[][][] col = new float[RINGS + 1][SEGMENTS + 1][];
        Vec3 light = new Vec3(-0.3, 1.0, 0.2).normalize();
        for (int i = 0; i <= RINGS; i++) {
            float th = Mth.PI * i / RINGS;
            for (int j = 0; j <= SEGMENTS; j++) {
                float ph = Mth.TWO_PI * j / SEGMENTS;
                Vec3 nrm = ax.scale(Mth.cos(th)).add(bx.scale(Mth.sin(th) * Mth.cos(ph))).add(cx.scale(Mth.sin(th) * Mth.sin(ph)));
                // lumpy and restless: three waves of different size and speed run over the surface
                float wobble = 1.0F + 0.08F * Mth.sin(3.0F * th + time * 0.55F) * Mth.sin(2.0F * ph + time * 0.4F)
                        + 0.05F * Mth.sin(5.0F * th - time * 0.9F + ph * 3.0F) + 0.035F * Mth.sin(7.0F * ph + time * 1.3F);
                float along = 1.0F + stretch * (Mth.cos(th) > 0 ? 1.0F : 0.6F);
                Vec3 p = ax.scale(Mth.cos(th) * radius * wobble * along)
                        .add(bx.scale(Mth.sin(th) * Mth.cos(ph) * radius * wobble))
                        .add(cx.scale(Mth.sin(th) * Mth.sin(ph) * radius * wobble));
                if (drip && Mth.cos(th) < 0.0F) {
                    // the back draws out into a drop that hangs down (the further back, the more it sags)
                    float back = Mth.cos(th) * Mth.cos(th);
                    p = p.add(ax.scale(-radius * 0.75F * back)).add(0.0, -radius * 0.45F * back * back, 0.0);
                }
                pos[i][j] = c.add(p);
                float lit = 0.55F + 0.45F * (float) Math.max(0.0, nrm.dot(light));
                // rim: where the surface turns away from the camera the slime looks thicker and darker
                float facing = (float) Math.abs(nrm.dot(view));
                float rim = Mth.clamp(1.0F - facing, 0.0F, 1.0F);
                rim = rim * rim;
                float[] rgb = new float[3];
                // mottled slime: patches of yellower green drifting slowly over it
                float mottle = 0.5F + 0.5F * Mth.sin(4.0F * th + 3.0F * ph + time * 0.15F) * Mth.sin(2.0F * ph - time * 0.1F);
                for (int k = 0; k < 3; k++) {
                    float bodyK = flat != null ? flat[k] : Mth.lerp(mottle * 0.6F, BODY[k], YELLOW[k]);
                    rgb[k] = flat != null ? bodyK * (0.75F + 0.25F * lit) : Mth.lerp(rim, bodyK * lit, RIM[k]);
                }
                col[i][j] = new float[]{rgb[0], rgb[1], rgb[2], alpha * (0.72F + 0.28F * rim)};
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                quadVertex(vc, base, m, pos[i][j], col[i][j]);
                quadVertex(vc, base, m, pos[i + 1][j], col[i + 1][j]);
                quadVertex(vc, base, m, pos[i + 1][j + 1], col[i + 1][j + 1]);
                quadVertex(vc, base, m, pos[i][j + 1], col[i][j + 1]);
            }
        }
    }

    private static void quadVertex(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3 p, float[] c) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(Mth.clamp(c[0], 0.0F, 1.0F), Mth.clamp(c[1], 0.0F, 1.0F), Mth.clamp(c[2], 0.0F, 1.0F), Mth.clamp(c[3], 0.0F, 1.0F))
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(base, 0.0F, 1.0F, 0.0F);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3 p, float u, float v, float[] rgb, float alpha) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(Mth.clamp(rgb[0], 0.0F, 1.0F), Mth.clamp(rgb[1], 0.0F, 1.0F), Mth.clamp(rgb[2], 0.0F, 1.0F), Mth.clamp(alpha, 0.0F, 1.0F))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(base, 0.0F, 1.0F, 0.0F);
    }

    private static void billboard(VertexConsumer vc, PoseStack.Pose base, Matrix4f m, Vec3 c, Camera camera, float size, float r, float g,
                                  float b) {
        Vec3 up = new Vec3(camera.getUpVector()).scale(size * 0.5F);
        Vec3 left = new Vec3(camera.getLeftVector()).scale(size * 0.5F);
        float[][] uv = {{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}};
        Vec3[] corners = {c.add(left).subtract(up), c.subtract(left).subtract(up), c.subtract(left).add(up), c.add(left).add(up)};
        for (int k = 0; k < 4; k++) {
            vc.addVertex(m, (float) corners[k].x, (float) corners[k].y, (float) corners[k].z)
                    .setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(b, 0.0F, 1.0F), 1.0F)
                    .setUv(uv[k][0], uv[k][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(base, 0.0F, 1.0F, 0.0F);
        }
    }
}
