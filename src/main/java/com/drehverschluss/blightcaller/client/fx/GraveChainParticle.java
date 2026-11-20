package com.drehverschluss.blightcaller.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cast of Gravebind: a ghostly vanilla chain ({@code block/chain}, by reference) bursts out of the ground, stands for a
 * moment and sinks back. Drawn like a hanging chain block, as a strip that turns only around the vertical axis towards the
 * camera; 1 block of height = one length of the chain texture, as on the chain block. The visible part grows from the
 * ground up (the top end of the chain first), like a chain pulled out of the earth.
 * <p>
 * Spawned by the spell's release particles with {@code shape CIRCLE}: the particle does not fly, the horizontal speed it
 * gets only says where it comes up (10 blocks per 1.0 of speed from the caster), so a ring of chains breaks out around the
 * caster and never at the caster's feet. Full bright with a light pale violet tint, so it reads at night too.
 */
public class GraveChainParticle extends TextureSheetParticle {
    private static final float SPREAD = 10.0F;
    private static final int RISE_TICKS = 5;
    private static final int SINK_TICKS = 10;
    /** Width of the vanilla chain plane: 3 of 16 pixels. */
    private static final float HALF_WIDTH = 3.0F / 32.0F;

    private final float height;

    protected GraveChainParticle(ClientLevel level, double x, double y, double z, double vx, double vz) {
        super(level, x + vx * SPREAD, y, z + vz * SPREAD);
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 26 + this.random.nextInt(10);
        this.height = 1.5F + this.random.nextFloat() * 1.0F;
        this.rCol = 0.86F;
        this.gCol = 0.80F;
        this.bCol = 1.0F;
        this.alpha = 1.0F;
        this.setSprite(GravebindChains.sprite());
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    /** Share of the chain that is out of the ground (0..1). */
    private float grown(float age) {
        if (age < RISE_TICKS) {
            float t = age / RISE_TICKS;
            return 1.0F - (1.0F - t) * (1.0F - t);
        }
        float left = this.lifetime - age;
        if (left < SINK_TICKS) {
            return Math.max(0.0F, left / SINK_TICKS);
        }
        return 1.0F;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float shown = this.height * grown(this.age + partialTicks);
        if (shown <= 0.01F) {
            return;
        }
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        float len = Mth.sqrt(x * x + z * z);
        if (len < 1.0E-4F) {
            return;
        }
        // perpendicular to the view, horizontal
        float rx = -z / len * HALF_WIDTH;
        float rz = x / len * HALF_WIDTH;
        int light = this.getLightColor(partialTicks);
        float u0 = this.sprite.getU(0.0F);
        float u1 = this.sprite.getU(3.0F / 16.0F);
        // c = length along the chain from its top end; one texture length per block, the top end comes out first
        float top = y + shown;
        float c = 0.0F;
        while (c < shown - 1.0E-4F) {
            float next = Math.min(shown, (float) Math.floor(c) + 1.0F);
            float v0 = this.sprite.getV(c - (float) Math.floor(c));
            float v1 = this.sprite.getV(next - (float) Math.floor(c));
            float yTop = top - c;
            float yBottom = top - next;
            quad(buffer, x, z, rx, rz, yBottom, yTop, u0, u1, v0, v1, light);
            c = next;
        }
    }

    private void quad(VertexConsumer buffer, float x, float z, float rx, float rz, float y0, float y1,
                      float u0, float u1, float vTop, float vBottom, int light) {
        // both windings: visible from every side whatever the culling of the sheet
        vertex(buffer, x - rx, y0, z - rz, u0, vBottom, light);
        vertex(buffer, x - rx, y1, z - rz, u0, vTop, light);
        vertex(buffer, x + rx, y1, z + rz, u1, vTop, light);
        vertex(buffer, x + rx, y0, z + rz, u1, vBottom, light);
        vertex(buffer, x + rx, y0, z + rz, u1, vBottom, light);
        vertex(buffer, x + rx, y1, z + rz, u1, vTop, light);
        vertex(buffer, x - rx, y1, z - rz, u0, vTop, light);
        vertex(buffer, x - rx, y0, z - rz, u0, vBottom, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    /** Creates the particle from the spell's particle batch (no sprite set: the chain comes from the block atlas). */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new GraveChainParticle(level, x, y, z, vx, vz);
        }
    }
}
