package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Drain Soul: the soul is torn out of the target and pulled to the caster (Phase 6, wish 20.11.: not a recoloured beam).
 * <ul>
 *     <li><b>Band:</b> a pulsing, slightly winding band of light from the target's chest to the caster's hands. Brightness
 *     waves run along it <b>towards the caster</b>, and once per channel pulse a bright bulge travels the whole way and
 *     arrives at the hands when the pulse hits.</li>
 *     <li><b>Soul wisps:</b> {@value #WISPS} wisps tear out of the target's chest (they grow from nothing there), ride the
 *     band to the caster, stretched along their way, and vanish into the hands.</li>
 *     <li><b>At the hands:</b> a soft glow that flares up when a pulse arrives (the healing).</li>
 * </ul>
 * Spell Engine's own beam of the spell is switched off in the spell file (transparent texture, no width, no block hit
 * particles); its targeting ({@code BEAM}, {@code cap 1}) is untouched. Who is channelling comes from Spell Engine's cast
 * process, which every client knows for every player in view (it also drives their animation), so all players see the
 * same. The target: the caster's own client knows the spell's current target, other clients search the first living
 * entity on the caster's line of sight within the spell's range, like Spell Engine's beam targeting. Without a target only
 * a faint strand searches forward.
 * <p>
 * Light load: {@value #SEGMENTS} segments per band, {@value #WISPS} wisps, two glows, at most {@value #MAX_CASTERS}
 * channelling players. Full bright (reads at night) and additive (never darkens the scene).
 */
public final class DrainSoulBeam {
    public static final ResourceLocation SPELL = Blightcaller.id("drain_soul");
    public static final ResourceLocation WISP_TEXTURE = Blightcaller.id("textures/entity/drain_soul_wisp.png");
    public static final ResourceLocation GLOW_TEXTURE = Blightcaller.id("textures/entity/drain_soul_glow.png");
    private static final int SEGMENTS = 24;
    private static final int WISPS = 6;
    private static final int MAX_CASTERS = 16;
    /** Game ticks a wisp needs from the target to the caster. */
    private static final float WISP_TRAVEL = 26.0F;
    /** Soul cyan #2DD4DA and a lighter core. */
    private static final float[] OUTER = {0.18F, 0.83F, 0.85F};
    private static final float[] CORE = {0.75F, 1.0F, 1.0F};

    private DrainSoulBeam() {}

    /** One channelling caster and what the band connects. */
    private record Beam(LivingEntity caster, Vec3 hand, Vec3 end, boolean target, float channelTicks, int pulseInterval) {}

    static RenderType wispType() {
        return RenderType.eyes(WISP_TEXTURE);
    }

    static RenderType glowType() {
        return RenderType.eyes(GLOW_TEXTURE);
    }

    /** Collects the channelling casters in view. */
    private static List<Beam> beams(Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        List<Beam> beams = new ArrayList<>();
        if (mc.level == null) {
            return beams;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (beams.size() >= MAX_CASTERS || !(entity instanceof Player player) || !(entity instanceof SpellCaster.Entity caster)) {
                continue;
            }
            SpellCast.Process process = caster.getSpellCastProcess();
            if (process == null || !process.spell().is(SPELL) || player.position().distanceToSqr(cam) > 64.0 * 64.0) {
                continue;
            }
            Spell spell = process.spell().value();
            float range = spell.range > 0.0F ? spell.range : 16.0F;
            Vec3 hand = hand(mc, player, partialTick);
            LivingEntity target = target(mc, player, range);
            Vec3 end;
            if (target != null) {
                end = lerpPos(target, partialTick).add(0.0, target.getBbHeight() * 0.6, 0.0);
            } else {
                Vec3 look = player.getViewVector(partialTick);
                end = TargetHelper.castBeam(player, look, range).end();
            }
            float channelTicks = Math.max(0.0F, (mc.level.getGameTime() - process.startedAt()) + partialTick);
            int pulses = Math.max(1, spell.active != null && spell.active.cast != null && spell.active.cast.channelTicks() > 0
                    ? spell.active.cast.channelTicks() : 8);
            int interval = Math.max(1, Math.round(process.length() / (float) pulses));
            beams.add(new Beam(player, hand, end, target != null, channelTicks, interval));
        }
        return beams;
    }

    /** Draws the bands. Returns whether anything was drawn. */
    static boolean renderBands(Matrix4f matrix, Vec3 cam, MultiBufferSource.BufferSource buffers, float partialTick,
                               List<Object> state) {
        List<Beam> beams = beams(cam, partialTick);
        state.clear();
        state.addAll(beams);
        if (beams.isEmpty()) {
            return false;
        }
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.GLOW);
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level.getGameTime() + partialTick;
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        for (Beam beam : beams) {
            Vec3[] points = curve(beam, time);
            float pulseHead = pulseHead(beam);
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3 a = points[i];
                Vec3 b = points[i + 1];
                // s = 0 at the target, 1 at the hands; brightness waves run towards the hands
                float s0 = i / (float) SEGMENTS;
                float s1 = (i + 1) / (float) SEGMENTS;
                float i0 = intensity(s0, time, pulseHead, beam.target());
                float i1 = intensity(s1, time, pulseHead, beam.target());
                float w0 = width(s0, time, pulseHead, beam.target());
                float w1 = width(s1, time, pulseHead, beam.target());
                Vec3 side0 = side(a, b, cam, up);
                // outer glow, then the core
                strip(vc, matrix, a.subtract(cam), b.subtract(cam), side0, w0 * 2.4F, w1 * 2.4F, OUTER, i0 * 0.35F, i1 * 0.35F);
                strip(vc, matrix, a.subtract(cam), b.subtract(cam), side0, w0, w1, CORE, i0 * 0.8F, i1 * 0.8F);
            }
        }
        buffers.endBatch(FxRenderTypes.GLOW);
        return true;
    }

    /** Draws the soul wisps and the glows at both ends (after {@link #renderBands}, same frame). */
    static void renderWisps(Matrix4f matrix, Vec3 cam, MultiBufferSource.BufferSource buffers, float partialTick,
                            List<Object> state, Vec3 cameraUp, Vec3 cameraLeft) {
        if (state.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level.getGameTime() + partialTick;
        VertexConsumer wisps = buffers.getBuffer(wispType());
        for (Object o : state) {
            Beam beam = (Beam) o;
            if (!beam.target()) {
                continue;
            }
            Vec3[] points = curve(beam, time);
            for (int k = 0; k < WISPS; k++) {
                float s = frac(time / WISP_TRAVEL + k / (float) WISPS + beam.caster().getId() * 0.137F);
                // grows out of the chest, shrinks into the hands
                float size = Mth.clamp(s / 0.15F, 0.0F, 1.0F) * Mth.clamp((1.0F - s) / 0.12F, 0.0F, 1.0F);
                if (size <= 0.02F) {
                    continue;
                }
                Vec3 p = at(points, s);
                Vec3 ahead = at(points, Math.min(1.0F, s + 0.04F));
                Vec3 dir = ahead.subtract(p);
                if (dir.lengthSqr() < 1.0E-8) {
                    continue;
                }
                dir = dir.normalize();
                // the wisps spiral around the band, so they read as torn soul shreds and not as part of the band
                Vec3 n1 = dir.cross(new Vec3(0.0, 1.0, 0.0));
                n1 = n1.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : n1.normalize();
                Vec3 n2 = dir.cross(n1).normalize();
                float orbit = time * 0.35F + k * 2.1F;
                double offset = 0.16 * Mth.sin(s * Mth.PI);
                p = p.add(n1.scale(Mth.cos(orbit) * offset)).add(n2.scale(Mth.sin(orbit) * offset));
                Vec3 side = dir.cross(cam.subtract(p)).normalize();
                float length = 0.62F * size;
                float width = 0.28F * size;
                float bright = 0.75F + 0.25F * Mth.sin(time * 0.6F + k);
                quadTextured(wisps, matrix, p.subtract(cam), dir.scale(length * 0.5F), side.scale(width * 0.5F),
                        CORE[0] * bright, CORE[1] * bright, CORE[2] * bright);
            }
        }
        buffers.endBatch(wispType());

        VertexConsumer glows = buffers.getBuffer(glowType());
        for (Object o : state) {
            Beam beam = (Beam) o;
            float flare = beam.target() ? arrivalFlare(beam) : 0.0F;
            float handSize = 0.28F + 0.32F * flare;
            float handBright = 0.45F + 0.55F * flare;
            billboard(glows, matrix, beam.hand().subtract(cam), cameraUp, cameraLeft, handSize,
                    OUTER[0] * handBright + 0.3F * flare, OUTER[1] * handBright + 0.2F * flare, OUTER[2] * handBright + 0.2F * flare);
            if (beam.target()) {
                float torn = 0.35F + 0.1F * Mth.sin(time * 0.9F);
                billboard(glows, matrix, beam.end().subtract(cam), cameraUp, cameraLeft, torn, OUTER[0] * 0.6F, OUTER[1] * 0.6F,
                        OUTER[2] * 0.6F);
            }
        }
        buffers.endBatch(glowType());
    }

    // ------------------------------------------------------------------------------------------------ geometry

    /** Points of the band from the target (index 0) to the hands (last index), winding and breathing. */
    private static Vec3[] curve(Beam beam, float time) {
        Vec3 from = beam.end();
        Vec3 to = beam.hand();
        Vec3 way = to.subtract(from);
        double length = way.length();
        Vec3 dir = length < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : way.scale(1.0 / length);
        Vec3 n1 = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (n1.lengthSqr() < 1.0E-6) {
            n1 = new Vec3(1.0, 0.0, 0.0);
        }
        n1 = n1.normalize();
        Vec3 n2 = dir.cross(n1).normalize();
        float amplitude = beam.target() ? (float) Math.min(0.32, 0.06 + length * 0.018) : 0.04F;
        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = i / (float) SEGMENTS;
            // zero at both ends, widest in the middle; the winding turns slowly
            float envelope = Mth.sin(s * Mth.PI);
            float phase = s * Mth.TWO_PI * 1.3F - time * 0.11F;
            double a = amplitude * envelope * Mth.sin(phase);
            double b = amplitude * envelope * Mth.cos(phase) * 0.7;
            points[i] = from.add(way.scale(s)).add(n1.scale(a)).add(n2.scale(b));
        }
        return points;
    }

    private static Vec3 at(Vec3[] points, float s) {
        float f = Mth.clamp(s, 0.0F, 1.0F) * SEGMENTS;
        int i = Math.min(SEGMENTS - 1, (int) f);
        return points[i].lerp(points[i + 1], f - i);
    }

    /** Where along the band (0 target .. 1 hands) the bulge of the current pulse is. */
    private static float pulseHead(Beam beam) {
        float within = beam.channelTicks() % beam.pulseInterval();
        return within / beam.pulseInterval();
    }

    /** 1 right when a pulse arrives at the hands, fading over a third of the pulse interval. */
    private static float arrivalFlare(Beam beam) {
        float within = (beam.channelTicks() % beam.pulseInterval()) / beam.pulseInterval();
        return Math.max(0.0F, 1.0F - within * 3.0F);
    }

    private static float intensity(float s, float time, float pulseHead, boolean target) {
        if (!target) {
            return 0.25F * (1.0F - s * 0.7F);
        }
        // waves running towards the hands (phase falls with time at a fixed place)
        float wave = 0.55F + 0.45F * Mth.sin(Mth.TWO_PI * (s * 2.2F - time * 0.09F));
        float bulge = Math.max(0.0F, 1.0F - Math.abs(s - pulseHead) / 0.12F);
        return Mth.clamp(0.45F + 0.35F * wave + 0.7F * bulge, 0.0F, 1.4F);
    }

    private static float width(float s, float time, float pulseHead, boolean target) {
        if (!target) {
            return 0.02F;
        }
        float breathe = 0.04F + 0.012F * Mth.sin(time * 0.35F + s * 6.0F);
        float bulge = Math.max(0.0F, 1.0F - Math.abs(s - pulseHead) / 0.12F);
        return breathe + 0.05F * bulge;
    }

    /** Sideways direction of a band segment, facing the camera. */
    private static Vec3 side(Vec3 a, Vec3 b, Vec3 cam, Vec3 fallback) {
        Vec3 dir = b.subtract(a);
        Vec3 view = a.subtract(cam);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0E-10) {
            side = dir.cross(fallback);
        }
        return side.lengthSqr() < 1.0E-10 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, float[] rgb,
                              float ia, float ib) {
        color(vc, m, a.add(side.scale(wa)), rgb, ia);
        color(vc, m, a.subtract(side.scale(wa)), rgb, ia);
        color(vc, m, b.subtract(side.scale(wb)), rgb, ib);
        color(vc, m, b.add(side.scale(wb)), rgb, ib);
    }

    private static void color(VertexConsumer vc, Matrix4f m, Vec3 p, float[] rgb, float intensity) {
        float i = Mth.clamp(intensity, 0.0F, 1.0F);
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb[0] * i, rgb[1] * i, rgb[2] * i, 1.0F);
    }

    private static void quadTextured(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 halfLength, Vec3 halfWidth, float r, float g, float b) {
        textured(vc, m, c.subtract(halfLength).subtract(halfWidth), 0.0F, 1.0F, r, g, b);
        textured(vc, m, c.subtract(halfLength).add(halfWidth), 1.0F, 1.0F, r, g, b);
        textured(vc, m, c.add(halfLength).add(halfWidth), 1.0F, 0.0F, r, g, b);
        textured(vc, m, c.add(halfLength).subtract(halfWidth), 0.0F, 0.0F, r, g, b);
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 up, Vec3 left, float size, float r, float g, float b) {
        Vec3 u = up.scale(size * 0.5F);
        Vec3 l = left.scale(size * 0.5F);
        textured(vc, m, c.add(l).subtract(u), 0.0F, 1.0F, r, g, b);
        textured(vc, m, c.subtract(l).subtract(u), 1.0F, 1.0F, r, g, b);
        textured(vc, m, c.subtract(l).add(u), 1.0F, 0.0F, r, g, b);
        textured(vc, m, c.add(l).add(u), 0.0F, 0.0F, r, g, b);
    }

    private static void textured(VertexConsumer vc, Matrix4f m, Vec3 p, float u, float v, float r, float g, float b) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(b, 0.0F, 1.0F), 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    // ------------------------------------------------------------------------------------------------ ends

    /**
     * Where the band ends at the caster: in front of the chest between the hands (the two-handed channelling animation
     * holds both hands forward). In first person for the own player a little lower and closer, so it meets the hands on
     * screen.
     */
    private static Vec3 hand(Minecraft mc, Player player, float partialTick) {
        float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 base = lerpPos(player, partialTick);
        boolean firstPerson = player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
        if (firstPerson) {
            // in first person both hands are raised in front of the camera (two-handed channelling) and cover the middle of
            // the screen: the band ends just right of them, so it stays visible from the hands to the target
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 look = player.getViewVector(partialTick);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
            return eye.add(look.scale(0.75)).add(right.scale(0.42)).add(0.0, -0.26, 0.0);
        }
        return base.add(0.0, player.getBbHeight() * 0.62, 0.0).add(forward.scale(0.55));
    }

    /** The drained entity: the own spell target on the caster's client, the first living entity on the line of sight elsewhere. */
    @Nullable
    private static LivingEntity target(Minecraft mc, Player player, float range) {
        if (player == mc.player && player instanceof SpellCaster.Client client) {
            for (Entity e : client.getCurrentTargets()) {
                if (e instanceof LivingEntity living && living.isAlive()) {
                    return living;
                }
            }
            return null;
        }
        Entity hit = TargetHelper.targetFromRaycast(player, range,
                e -> e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator());
        return hit instanceof LivingEntity living ? living : null;
    }

    private static Vec3 lerpPos(Entity e, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()), Mth.lerp(partialTick, e.zo, e.getZ()));
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }
}
