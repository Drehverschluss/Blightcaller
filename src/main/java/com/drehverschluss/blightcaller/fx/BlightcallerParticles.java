package com.drehverschluss.blightcaller.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Particle types of Blightcaller (drawn on the client by {@code client.fx}). */
public final class BlightcallerParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Blightcaller.MODID);

    /**
     * Cast of Gravebind: starts a wave of chain that runs over the ground away from the caster (drawn by the client, the
     * particle itself is invisible). Used once by the spell's release particles ({@code shape NONE}, {@code anchor GROUND}).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAVE_WAVE =
            PARTICLES.register("grave_wave", () -> new SimpleParticleType(true));

    private BlightcallerParticles() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
