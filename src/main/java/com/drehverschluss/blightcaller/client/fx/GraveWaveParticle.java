package com.drehverschluss.blightcaller.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

/**
 * {@code blightcaller:grave_wave}: an invisible particle that only marks a spell whose cast starts the wave of chain over the ground
 * (Gravebind). Since phase 7B the server sends the wave with the radius of the cast ({@code fx.GraveWave}), so the particle itself
 * does nothing.
 */
public class GraveWaveParticle extends NoRenderParticle {
    protected GraveWaveParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.lifetime = 1;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new GraveWaveParticle(level, x, y, z);
        }
    }
}
