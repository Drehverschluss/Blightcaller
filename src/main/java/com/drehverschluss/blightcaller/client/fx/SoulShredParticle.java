package com.drehverschluss.blightcaller.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A soul shred of Drain Soul: a ghostly soul face (the vanilla soul particle's sprites, by reference) torn out of the
 * target and pulled along the strands to the caster ({@link DrainSoulBeam}). It first clings to the target for a few ticks
 * (being torn out, it grows), then flies along the path, faster near the caster, spiralling around the strands, and is
 * taken in at the tip of the wand ({@link DrainSoulBeam#arrived}). Spawned by the client itself, never sent over the
 * network. Without the beam (the channel ended) it fades out where it is.
 */
public class SoulShredParticle extends TextureSheetParticle {
    private final int casterId;
    private final LivingEntity target;
    private final Vec3 offset;
    private final int hold;
    private final int travel;
    private final float size;
    private final float spin;
    private float progress;
    private float cameraScale = 1.0F;
    private boolean lost;

    SoulShredParticle(ClientLevel level, int casterId, LivingEntity target, Vec3 offset, int hold, int travel, float size, float spin) {
        super(level, target.getX() + offset.x, target.getY() + offset.y, target.getZ() + offset.z);
        this.casterId = casterId;
        this.target = target;
        this.offset = offset;
        this.hold = hold;
        this.travel = travel;
        this.size = size;
        this.spin = spin;
        this.lifetime = hold + travel + 1;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.rCol = 0.75F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 0.0F;
        this.quadSize = size;
        this.setSprite(frame(0.0F));
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
    }

    /** The vanilla soul sprite for a share of the life (looked up each time: stays right after a resource reload). */
    private static TextureAtlasSprite frame(float share) {
        // the vanilla soul starts as a brown flake (frames 0-6) and ends as the cyan face: only the face frames, the flake
        // turning into a face while it is torn out
        ResourceLocation id = ResourceLocation.withDefaultNamespace("soul_" + Mth.clamp(7 + (int) (share * 8), 7, 10));
        return Minecraft.getInstance().particleEngine.textureAtlas.getSprite(id);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float grow = hold > 0 ? Mth.clamp((age + partialTick) / hold, 0.4F, 1.0F) : Mth.clamp((age + partialTick) / 3.0F, 0.3F, 1.0F);
        return quadSize * grow * (1.0F - 0.55F * progress) * cameraScale;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // small near the camera (first person: the shreds end at the wand tip right in front of the eye)
        double distance = camera.getPosition().distanceTo(new Vec3(x, y, z));
        cameraScale = (float) Mth.clamp(distance / 2.5, 0.15, 1.0);
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        setSprite(frame(age / (float) lifetime));
        DrainSoulBeam.Beam beam = DrainSoulBeam.beam(casterId);
        if (lost || beam == null || beam.target() != target || !target.isAlive()) {
            // the channel ended: fade where it is
            lost = true;
            alpha = Math.max(0.0F, alpha - 0.2F);
            yd = 0.02;
            setPos(x, y + yd, z);
            if (alpha <= 0.0F) {
                remove();
            }
            return;
        }
        DrainSoulBeam.Path path = beam.path();
        Vec3 stuck = target.position().add(offset);
        Vec3 pull = path.start().subtract(stuck);
        pull = pull.lengthSqr() < 1.0E-6 ? Vec3.ZERO : pull.normalize();
        Vec3 p;
        if (age <= hold) {
            // being torn out: clings to the body and is dragged a little towards the caster
            float t = hold == 0 ? 1.0F : age / (float) hold;
            p = stuck.add(pull.scale(0.25 * t));
            alpha = 0.9F * t;
        } else {
            float t = Mth.clamp((age - hold) / (float) travel, 0.0F, 1.0F);
            progress = t * t;   // faster near the caster
            Vec3 from = stuck.add(pull.scale(0.25));
            float u = 1.0F - progress;
            Vec3 onPath = path.point(u);
            // starts where it was torn out and joins the strands, spiralling around them
            Vec3 drift = from.subtract(path.end()).scale((1.0F - progress) * (1.0F - progress));
            Vec3 tangent = path.tangent(u);
            Vec3 n1 = tangent.cross(new Vec3(0.0, 1.0, 0.0));
            n1 = n1.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : n1.normalize();
            Vec3 n2 = tangent.cross(n1).normalize();
            float angle = spin + progress * 9.0F;
            double radius = 0.2 * Mth.sin(Mth.PI * progress) * (1.0 - 0.5 * progress);
            p = onPath.add(drift).add(n1.scale(Mth.cos(angle) * radius)).add(n2.scale(Mth.sin(angle) * radius));
            alpha = 0.95F;
            if (t >= 1.0F) {
                DrainSoulBeam.arrived(casterId);
                remove();
            }
        }
        setPos(p.x, p.y, p.z);
    }
}
