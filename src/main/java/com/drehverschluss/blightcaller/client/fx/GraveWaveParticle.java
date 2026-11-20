package com.drehverschluss.blightcaller.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

/**
 * Cast of Gravebind ({@code blightcaller:grave_wave}): an invisible particle that only starts the wave of chain running over
 * the ground away from the caster ({@link GravebindChains#addWave}). The spell's release spawns one at the caster's feet;
 * Spell Engine sends the release visuals to every player in view, so everyone sees the wave.
 */
public class GraveWaveParticle extends NoRenderParticle {
    protected GraveWaveParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.lifetime = 1;
        GravebindChains.addWave(x, y, z);
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
