package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Drain Soul: the soul is torn out of the target and pulled to the caster (Phase 6.1, decided 20.11.).
 * <ul>
 *     <li><b>Soul smoke:</b> three thin strands of soul smoke between the tip of the caster's wand and the target, each
 *     with an arc of its own (wide, middle, narrow), its own direction and pace, wandering irregularly (noise), meeting at
 *     both ends: a soul turquoise core (additive) in a dark violet haze, soft smoky edges from a texture, drawn as one
 *     continuous ribbon per strand (no segment edges). The smoke flows towards the caster. The way rises a little (over the
 *     caster's shoulder), so the stream stays visible behind the caster in third person.</li>
 *     <li><b>The torn soul:</b> a see-through soul turquoise copy of the target's own model ({@link EntityGhost}). A faint
 *     copy stays in the body; at every channel pulse a copy is torn out and travels along the strands to the tip of the
 *     wand, stretched at first and smaller and smaller on its way. Entities whose model cannot be drawn get soul shreds in
 *     the shape of their hitbox instead.</li>
 *     <li><b>Soul shreds:</b> at every pulse ghostly shreds (the vanilla soul faces, {@link SoulShredParticle}) are torn out
 *     of the target and pulled along the strands, faster near the caster, and taken in at the tip of the wand: a small
 *     flash there is the healing.</li>
 *     <li><b>First person:</b> the strands leave from the tip of the wand (found while the item is drawn, {@link HandTip})
 *     and get thin near the camera. The standard channelling animation stays.</li>
 * </ul>
 * Spell Engine's own beam of the spell is switched off in the spell file; its targeting ({@code BEAM}, {@code cap 1}) is
 * untouched. Who is channelling comes from Spell Engine's cast process, which every client knows for every player in
 * view, so all players see the same. The target: the caster's own client knows the spell's target, other clients search
 * the first living entity on the caster's line of sight within the spell's range, like Spell Engine's beam targeting.
 * At most {@value #MAX_CASTERS} channelling players are drawn.
 */
public final class DrainSoulBeam {
    public static final ResourceLocation SPELL = Blightcaller.id("drain_soul");
    public static final ResourceLocation SMOKE_TEXTURE = Blightcaller.id("textures/entity/drain_soul_smoke.png");
    public static final ResourceLocation GLOW_TEXTURE = Blightcaller.id("textures/entity/drain_soul_glow.png");
    private static final int MAX_CASTERS = 16;
    /** Soul turquoise (soul fire) of the core and the ghost, dark violet of the haze. */
    private static final float[] CORE = {0.22F, 0.90F, 0.95F};
    private static final float[] HAZE = {0.17F, 0.04F, 0.26F};
    private static final float[] GHOST = {0.36F, 0.95F, 1.0F};
    /** How much the way of the strands rises (over the caster's shoulder): a tenth of the length, at most 0.8 blocks. */
    private static final float LIFT = 1.0F;

    /** One channelling caster, in world coordinates, as last drawn. */
    record Beam(Player caster, @Nullable LivingEntity target, Path path, float channelTicks, int interval, long startedAt,
                boolean firstPerson, long frameTick) {}

    /** The way of the strands: from the tip of the wand (u = 0) to the target's (torn) chest (u = 1). */
    record Path(Vec3 start, Vec3 end, double rise) {
        Vec3 point(float u) {
            float h = Mth.sin(Mth.PI * (float) Math.pow(Mth.clamp(u, 0.0F, 1.0F), 0.75));
            return start.lerp(end, u).add(0.0, rise * h, 0.0);
        }

        Vec3 tangent(float u) {
            float d = 0.01F;
            Vec3 t = point(Math.min(1.0F, u + d)).subtract(point(Math.max(0.0F, u - d)));
            return t.lengthSqr() < 1.0E-10 ? end.subtract(start).normalize() : t.normalize();
        }

        double length() {
            return start.distanceTo(end);
        }
    }

    private static final Map<Integer, Beam> LAST = new HashMap<>();
    private static final Map<Integer, Float> ARRIVAL = new HashMap<>();
    private static final Map<Integer, Integer> PULSE_SEEN = new HashMap<>();
    /** Beams and ghosts drawn in the last frame (probe, report). */
    static int lastBeams;
    static int lastGhosts;

    private DrainSoulBeam() {}

    /** Whether a player is channelling Drain Soul right now (Spell Engine's cast process, known on every client). */
    static boolean isDraining(Player player) {
        if (!(player instanceof SpellCaster.Entity caster)) {
            return false;
        }
        SpellCast.Process process = caster.getSpellCastProcess();
        return process != null && process.spell().is(SPELL);
    }

    @Nullable
    static Beam beam(int casterId) {
        return LAST.get(casterId);
    }

    /** A soul shred reached the caster: the tip flashes (the healing). */
    static void arrived(int casterId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ARRIVAL.put(casterId, (float) mc.level.getGameTime());
        }
    }

    // ------------------------------------------------------------------------------------------------ pulses

    /**
     * Where in the pulse cycle the channel is: pulse number (0 before the first pulse) and progress since it (0..1). Spell
     * Engine releases the pulses of a channel half an interval after the start and then every interval (Phase 5 log:
     * channel ticks 5, 15, 25 ... with 8 pulses in 4 s).
     */
    static float[] cycle(float channelTicks, int interval) {
        float shifted = channelTicks - interval * 0.5F;
        if (shifted < 0.0F) {
            return new float[]{0.0F, channelTicks / (interval * 0.5F)};
        }
        int pulse = (int) Math.floor(shifted / interval) + 1;
        float p = (shifted - (pulse - 1) * interval) / interval;
        return new float[]{pulse, p};
    }

    /** Share of the pulse cycle the torn soul needs from the target to the tip of the wand. */
    private static final float TRAVEL = 0.9F;

    /**
     * Progress of the soul torn out at the last pulse on its way to the caster (0 at the target .. 1 at the wand), or -1
     * when none is under way. Slow at first (it is torn out), then faster.
     */
    static float travel(float pulse, float p) {
        if (pulse < 1 || p > TRAVEL) {
            return -1.0F;
        }
        float q = p / TRAVEL;
        return (float) Math.pow(q, 1.7);
    }

    /** Smooth value noise in 0..1 (no fixed pattern: the strands change slowly and irregularly). */
    static float noise(float x, float y, int seed) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        fx = fx * fx * (3.0F - 2.0F * fx);
        fy = fy * fy * (3.0F - 2.0F * fy);
        float a = hash(ix, iy, seed);
        float b = hash(ix + 1, iy, seed);
        float c = hash(ix, iy + 1, seed);
        float d = hash(ix + 1, iy + 1, seed);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    // ------------------------------------------------------------------------------------------------ render

    /** Draws ghosts, strands and the flash at the tips. */
    static void render(PoseStack.Pose base, Vec3 cam, Camera camera, Matrix4f projection, MultiBufferSource.BufferSource buffers,
                       float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long tick = level.getGameTime();
        float time = tick + partialTick;
        List<Beam> beams = collect(mc, level, cam, camera, projection, partialTick, tick);
        LAST.values().removeIf(b -> tick - b.frameTick() > 5);
        for (Beam b : beams) {
            LAST.put(b.caster().getId(), b);
        }
        lastBeams = beams.size();
        lastGhosts = 0;
        if (beams.isEmpty()) {
            return;
        }
        // 1. the torn soul (the target's own model as a ghost)
        for (Beam b : beams) {
            if (b.target() != null && ghost(base, cam, buffers, partialTick, b, time)) {
                lastGhosts++;
            }
        }
        // 2. the strands: dark haze first (alpha blended), then the turquoise core (additive)
        List<Strand> strands = new ArrayList<>();
        for (Beam b : beams) {
            strands(strands, b, cam, time);
        }
        RenderType haze = RenderType.entityTranslucentEmissive(SMOKE_TEXTURE);
        VertexConsumer vc = buffers.getBuffer(haze);
        for (Strand s : strands) {
            ribbon(vc, base, s, cam, true, time);
        }
        buffers.endBatch(haze);
        RenderType core = RenderType.eyes(SMOKE_TEXTURE);
        vc = buffers.getBuffer(core);
        for (Strand s : strands) {
            ribbon(vc, base, s, cam, false, time);
        }
        buffers.endBatch(core);
        // 3. the flash at the tip when a shred arrives (the healing)
        RenderType glow = RenderType.eyes(GLOW_TEXTURE);
        vc = buffers.getBuffer(glow);
        for (Beam b : beams) {
            Float arrival = ARRIVAL.get(b.caster().getId());
            float flare = arrival == null ? 0.0F : Mth.clamp(1.0F - (time - arrival) / 6.0F, 0.0F, 1.0F);
            float size = (b.firstPerson() ? 0.07F : 0.15F) + (b.firstPerson() ? 0.12F : 0.3F) * flare;
            float bright = 0.4F + 0.6F * flare;
            billboard(vc, base, b.path().start().subtract(cam), camera, size, CORE[0] * bright + 0.25F * flare,
                    CORE[1] * bright, CORE[2] * bright);
        }
        buffers.endBatch(glow);
    }

    /** The channelling casters in view. */
    private static List<Beam> collect(Minecraft mc, ClientLevel level, Vec3 cam, Camera camera, Matrix4f projection,
                                      float partialTick, long tick) {
        List<Beam> beams = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (beams.size() >= MAX_CASTERS || !(entity instanceof Player player) || !(entity instanceof SpellCaster.Entity caster)) {
                continue;
            }
            SpellCast.Process process = caster.getSpellCastProcess();
            if (process == null || !process.spell().is(SPELL) || player.position().distanceToSqr(cam) > 64.0 * 64.0) {
                continue;
            }
            Spell spell = process.spell().value();
            float range = spell.range > 0.0F ? spell.range : 16.0F;
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 tip = HandTip.get(player, camera, projection);
            if (tip == null) {
                tip = estimateTip(mc, player, partialTick, firstPerson);
            }
            LivingEntity target = target(mc, player, range);
            float channelTicks = Math.max(0.0F, (tick - process.startedAt()) + partialTick);
            int pulses = spell.active != null && spell.active.cast != null && spell.active.cast.channelTicks() > 0
                    ? spell.active.cast.channelTicks() : 8;
            int interval = Math.max(1, Math.round(process.length() / (float) pulses));
            Vec3 end;
            if (target != null) {
                end = chest(target, partialTick);
            } else {
                end = TargetHelper.castBeam(player, player.getViewVector(partialTick), range).end();
            }
            double length = tip.distanceTo(end);
            double rise = LIFT * Math.min(0.1 * length, 0.8);
            beams.add(new Beam(player, target, new Path(tip, end, rise), channelTicks, interval, process.startedAt(), firstPerson, tick));
        }
        return beams;
    }

    private static Vec3 chest(LivingEntity target, float partialTick) {
        return GravebindChains.lerpPos(target, partialTick).add(0.0, target.getBbHeight() * 0.6, 0.0);
    }

    /**
     * Draws the soul of the target: a faint copy stays in the body, and at every pulse a copy is torn out and travels along
     * the strands to the tip of the wand, stretched at first and smaller and smaller on its way.
     */
    private static boolean ghost(PoseStack.Pose base, Vec3 cam, MultiBufferSource.BufferSource buffers, float partialTick, Beam b,
                                 float time) {
        LivingEntity target = b.target();
        if (!BlightcallerClientConfig.drainSoulGhost() || !EntityGhost.supported(target.getType())) {
            return false;
        }
        Path path = b.path();
        Vec3 chest = chest(target, partialTick);
        Vec3 toCaster = path.start().subtract(chest);
        if (toCaster.lengthSqr() < 1.0E-6) {
            return false;
        }
        float height = target.getBbHeight() * 0.6F;
        float[] c = cycle(b.channelTicks(), b.interval());
        // the soul left in the body: faint, wavering a little towards the caster
        float idle = 0.16F * Mth.clamp(b.channelTicks() / (b.interval() * 0.5F), 0.0F, 1.0F);
        Vec3 sway = toCaster.normalize().scale(0.05F + 0.03F * Mth.sin(time * 0.3F));
        boolean drawn = drawGhost(base, cam, buffers, partialTick, target, chest.add(sway), toCaster, height, 1.0F, 1.0F, idle);
        // the soul torn out at the last pulse, on its way to the wand
        float q = travel(c[0], c[1]);
        if (q >= 0.0F) {
            float u = 1.0F - q;
            Vec3 at = path.point(u);
            // small at the wand whatever the size of the target (at most about a third of a block)
            float smallest = Math.min(0.15F, 0.35F / Math.max(0.1F, target.getBbHeight()));
            float size = Mth.lerp(q, 1.0F, smallest);
            float stretch = 1.0F + 0.6F * Mth.sin(Mth.PI * Math.min(1.0F, q / 0.35F));
            float alpha = 0.55F * (1.0F - 0.45F * q) * Mth.clamp(c[1] / 0.05F, 0.0F, 1.0F);
            if (b.firstPerson()) {
                // the own first person view: it fades before it reaches the eye
                alpha *= (float) Mth.clamp((at.distanceTo(cam) - 0.8) / 2.5, 0.0, 1.0);
            }
            drawn |= drawGhost(base, cam, buffers, partialTick, target, at, path.tangent(u).scale(-1.0), height, size, stretch, alpha);
        }
        return drawn;
    }

    /** One ghost of the target with its chest at {@code chest}, scaled and stretched along {@code towards}. */
    private static boolean drawGhost(PoseStack.Pose base, Vec3 cam, MultiBufferSource.BufferSource buffers, float partialTick,
                                     LivingEntity target, Vec3 chest, Vec3 towards, float chestHeight, float size, float stretch,
                                     float alpha) {
        if (alpha <= 0.01F || size <= 0.01F) {
            return false;
        }
        float yaw = (float) Mth.atan2(towards.x, towards.z);
        PoseStack pose = new PoseStack();
        pose.last().pose().set(base.pose());
        pose.last().normal().set(base.normal());
        Vec3 at = chest.subtract(cam);
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.scale(size / (float) Math.sqrt(stretch), size, size * stretch);
        pose.mulPose(Axis.YP.rotation(-yaw));
        pose.translate(0.0F, -chestHeight, 0.0F);
        // the soul left in the body glows only faintly (on big bosses it would cover the whole model)
        float glow = 0.1F + 0.9F * Mth.clamp(alpha / 0.55F, 0.0F, 1.0F);
        return EntityGhost.render(target, pose, partialTick, buffers,
                new EntityGhost.Style(GHOST[0], GHOST[1], GHOST[2], alpha, glow * 0.6F));
    }

    // ------------------------------------------------------------------------------------------------ strands

    private record Strand(Vec3[] points, float[] width, float[] intensity, float[] v, float strength) {}

    /** Width of the arc, base direction, turning speed and where the arc is widest, per strand (wide, middle, narrow). */
    private static final float[] ARC = {1.0F, 0.6F, 0.28F};
    private static final float[] DIRECTION = {0.4F, 2.0F, 4.6F};
    private static final float[] SPIN = {0.05F, -0.03F, 0.08F};
    private static final float[] PEAK = {0.85F, 1.15F, 0.7F};

    /**
     * Three strands of soul smoke between the wand and the target, each with its own arc: one wide, one middle, one narrow,
     * in their own directions (not 120 degrees apart), turning at their own pace, and wandering slowly and irregularly
     * (value noise over place and time, so no pattern repeats); they may cross. Thickness and brightness vary along each
     * strand. All three meet at the wand and at the target.
     */
    private static void strands(List<Strand> out, Beam b, Vec3 cam, float time) {
        Path path = b.path();
        double length = path.length();
        int segments = Mth.clamp((int) (length * 7.0), 28, 84);
        boolean target = b.target() != null;
        int count = target ? 3 : 1;
        float arc = target ? (float) Mth.clamp(0.3 + 0.1 * length, 0.3, 1.4) : 0.0F;
        // in the own first person view the arcs are seen along the way and would ring the target: flatter there
        if (b.firstPerson()) {
            arc *= 0.4F;
        }
        float width = target ? (float) Mth.clamp(0.035 + 0.004 * length, 0.035, 0.08) : 0.02F;
        float[] c = cycle(b.channelTicks(), b.interval());
        // a bright knot travels with the torn soul from the target to the caster
        float q = travel(c[0], c[1]);
        float knot = q >= 0.0F ? 1.0F - q : -1.0F;
        int seedBase = b.caster().getId() * 31;
        for (int k = 0; k < count; k++) {
            int seed = seedBase + k * 7;
            Vec3[] points = new Vec3[segments + 1];
            float[] w = new float[segments + 1];
            float[] intensity = new float[segments + 1];
            float[] v = new float[segments + 1];
            double run = 0.0;
            for (int i = 0; i <= segments; i++) {
                float u = i / (float) segments;
                Vec3 p = path.point(u);
                Vec3 t = path.tangent(u);
                Vec3 n1 = t.cross(new Vec3(0.0, 1.0, 0.0));
                n1 = n1.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : n1.normalize();
                Vec3 n2 = t.cross(n1).normalize();
                // zero at the wand and at the target, widest at a place of its own
                float env = Mth.sin(Mth.PI * (float) Math.pow(u, PEAK[k]));
                float wander = (noise(u * 1.7F, time * 0.02F, seed) - 0.5F) * 2.0F;
                float reach = arc * ARC[k] * env * (0.65F + 0.7F * noise(u * 1.3F + 5.0F, time * 0.015F, seed + 3));
                float angle = DIRECTION[k] + SPIN[k] * time + 2.4F * wander + Mth.PI * u;
                Vec3 offset = n1.scale(Mth.cos(angle) * reach).add(n2.scale(Mth.sin(angle) * reach));
                // thin near the camera (first person: the strands leave the wand tip right in front of the eye)
                float near = (float) Mth.clamp(p.distanceTo(cam) / (b.firstPerson() ? 3.5 : 1.8), 0.1, 1.0);
                points[i] = p.add(offset.scale(near));
                w[i] = width * near * (0.75F + 0.25F * env) * (0.6F + 0.8F * noise(u * 3.0F, time * 0.04F, seed + 7));
                float bright = 0.45F + 0.4F * noise(u * 2.5F + 11.0F, time * 0.05F, seed + 9);
                if (knot >= 0.0F) {
                    float d = (u - knot) / 0.08F;
                    bright += 0.8F * (float) Math.exp(-d * d);
                }
                // fades in out of the tip, never cut at the target end
                bright *= Mth.clamp(u / 0.05F, 0.0F, 1.0F);
                intensity[i] = target ? bright : bright * 0.4F;
                if (i > 0) {
                    run += points[i].distanceTo(points[i - 1]);
                }
                v[i] = (float) (run * 0.8) + time * 0.07F + k * 0.33F;
            }
            out.add(new Strand(points, w, intensity, v, 1.0F));
        }
    }

    /** One strand as a continuous camera facing ribbon (the sides are shared between neighbouring pieces). */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose base, Strand s, Vec3 cam, boolean haze, float time) {
        Vec3[] p = s.points();
        int n = p.length;
        Vec3[] sides = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 t = p[Math.min(n - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            Vec3 side = t.cross(p[i].subtract(cam));
            sides[i] = side.lengthSqr() < 1.0E-12 ? new Vec3(0.0, 1.0, 0.0) : side.normalize();
        }
        float factor = haze ? 3.2F : 1.0F;
        Matrix4f m = base.pose();
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = p[i].subtract(cam);
            Vec3 b = p[i + 1].subtract(cam);
            Vec3 sa = sides[i].scale(s.width()[i] * factor);
            Vec3 sb = sides[i + 1].scale(s.width()[i + 1] * factor);
            float ia = s.intensity()[i];
            float ib = s.intensity()[i + 1];
            if (haze) {
                ia *= sideways(p, i, cam);
                ib *= sideways(p, i + 1, cam);
            }
            vertex(vc, m, base, a.add(sa), 0.0F, s.v()[i], ia, haze);
            vertex(vc, m, base, a.subtract(sa), 1.0F, s.v()[i], ia, haze);
            vertex(vc, m, base, b.subtract(sb), 1.0F, s.v()[i + 1], ib, haze);
            vertex(vc, m, base, b.add(sb), 0.0F, s.v()[i + 1], ib, haze);
        }
    }

    /** 1 where a strand runs across the view, towards 0 where it runs along the line of sight. */
    private static float sideways(Vec3[] p, int i, Vec3 cam) {
        Vec3 t = p[Math.min(p.length - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
        Vec3 view = p[i].subtract(cam);
        if (t.lengthSqr() < 1.0E-10 || view.lengthSqr() < 1.0E-10) {
            return 0.0F;
        }
        float along = (float) Math.abs(t.normalize().dot(view.normalize()));
        return Mth.clamp((1.0F - along) * 6.0F - 0.2F, 0.0F, 1.0F);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose base, Vec3 p, float u, float v, float intensity, boolean haze) {
        float r, g, b, a;
        if (haze) {
            r = HAZE[0];
            g = HAZE[1];
            b = HAZE[2];
            a = Mth.clamp(0.5F * intensity, 0.0F, 1.0F);
        } else {
            float i = Mth.clamp(intensity, 0.0F, 1.0F);
            r = CORE[0] * i;
            g = CORE[1] * i;
            b = CORE[2] * i;
            a = 1.0F;
        }
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(base, 0.0F, 1.0F, 0.0F);
    }

    private static void billboard(VertexConsumer vc, PoseStack.Pose base, Vec3 c, Camera camera, float size, float r, float g, float b) {
        Vec3 up = new Vec3(camera.getUpVector()).scale(size * 0.5F);
        Vec3 left = new Vec3(camera.getLeftVector()).scale(size * 0.5F);
        Matrix4f m = base.pose();
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

    // ------------------------------------------------------------------------------------------------ client tick

    /** Tears soul shreds out of the targets at every pulse (client tick). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        long tick = level.getGameTime();
        PULSE_SEEN.keySet().removeIf(id -> !LAST.containsKey(id));
        for (Beam b : List.copyOf(LAST.values())) {
            LivingEntity target = b.target();
            if (target == null || tick - b.frameTick() > 2) {
                continue;
            }
            int casterId = b.caster().getId();
            int pulse = (int) cycle(tick - b.startedAt(), b.interval())[0];
            RandomSource random = level.getRandom();
            boolean fallback = BlightcallerClientConfig.drainSoulGhost() && !EntityGhost.supported(target.getType());
            Integer seen = PULSE_SEEN.get(casterId);
            if (pulse >= 1 && (seen == null || seen != pulse)) {
                PULSE_SEEN.put(casterId, pulse);
                int shreds = fallback ? 8 : 3;
                for (int n = 0; n < shreds; n++) {
                    Vec3 offset = fallback ? inBox(target, random) : new Vec3((random.nextFloat() - 0.5F) * target.getBbWidth() * 0.4F,
                            target.getBbHeight() * (0.45F + random.nextFloat() * 0.3F), (random.nextFloat() - 0.5F) * target.getBbWidth() * 0.4F);
                    mc.particleEngine.add(new SoulShredParticle(level, casterId, target, offset, 2 + n % 3, 9 + random.nextInt(3),
                            0.4F + random.nextFloat() * 0.08F, random.nextFloat() * Mth.TWO_PI));
                }
            } else if (tick % 5 == casterId % 5) {
                Vec3 offset = fallback ? inBox(target, random) : new Vec3(0.0, target.getBbHeight() * 0.6, 0.0);
                mc.particleEngine.add(new SoulShredParticle(level, casterId, target, offset, 0, 11 + random.nextInt(3),
                        0.26F + random.nextFloat() * 0.06F, random.nextFloat() * Mth.TWO_PI));
            }
        }
    }

    /** A point inside the hitbox (relative to the feet): the fallback silhouette of entities without a ghost. */
    private static Vec3 inBox(LivingEntity target, RandomSource random) {
        float w = Math.min(target.getBbWidth(), 6.0F);
        float h = Math.min(target.getBbHeight(), 8.0F);
        return new Vec3((random.nextFloat() - 0.5F) * w * 0.8F, random.nextFloat() * h * 0.9F + h * 0.05F, (random.nextFloat() - 0.5F) * w * 0.8F);
    }

    // ------------------------------------------------------------------------------------------------ ends

    /** The tip without a capture: the caster's right hand, held forward (two-handed channelling). */
    private static Vec3 estimateTip(Minecraft mc, Player player, float partialTick, boolean firstPerson) {
        if (firstPerson) {
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 look = player.getViewVector(partialTick);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
            return eye.add(look.scale(0.8)).add(right.scale(0.3)).add(0.0, -0.12, 0.0);
        }
        float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        return GravebindChains.lerpPos(player, partialTick).add(0.0, player.getBbHeight() * 0.72, 0.0)
                .add(forward.scale(0.7)).add(right.scale(0.3));
    }

    /** The drained entity: the own spell target on the caster's client, the first living entity on the line of sight elsewhere. */
    @Nullable
    private static LivingEntity target(Minecraft mc, Player player, float range) {
        if (player == mc.player && player instanceof SpellCaster.Client client) {
            for (Entity e : client.getCurrentTargets()) {
                LivingEntity living = living(e);
                if (living != null && living.isAlive()) {
                    return living;
                }
            }
            return null;
        }
        Entity hit = TargetHelper.targetFromRaycast(player, range, e -> e != player && !e.isSpectator() && living(e) != null
                && living(e).isAlive() && living(e) != player);
        return living(hit);
    }

    /** The living entity behind an entity or a part of one (Ender Dragon). */
    @Nullable
    private static LivingEntity living(@Nullable Entity e) {
        if (e instanceof LivingEntity living) {
            return living;
        }
        if (e instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    private static float easeOut(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }
}
