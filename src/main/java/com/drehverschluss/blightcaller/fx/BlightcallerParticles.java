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
     * A ghostly chain that bursts out of the ground and sinks back (cast of Gravebind). Used by the spell's release
     * particles ({@code shape CIRCLE}): the horizontal speed sets how far from the caster it comes up, 10 blocks per 1.0.
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAVE_CHAIN =
            PARTICLES.register("grave_chain", () -> new SimpleParticleType(true));

    private BlightcallerParticles() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
