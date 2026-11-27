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

    // ---- own particle set (phase 14A): registered on both sides, drawn by client.fx.BlightParticle. Not used anywhere
    // in the game yet (phase 14B puts them into the spells); Spell Engine files can name them like its own particles.

    /** Rot smoke: thick, uneven puffs with a dark rim, green by default (meant to replace Spell Engine's smoke_medium). */
    public static final DeferredHolder<ParticleType<?>, BlightParticleType> ROT_SMOKE = PARTICLES.register("rot_smoke", BlightParticleType::new);
    /** Rot drip: a thick drop that hangs, falls and splashes on the ground (meant to replace dripping_blood). */
    public static final DeferredHolder<ParticleType<?>, BlightParticleType> ROT_DRIP = PARTICLES.register("rot_drip", BlightParticleType::new);
    /** Spore: a small glowing mote drifting and flickering, yellow green (meant to replace magic_spark and sneeze). */
    public static final DeferredHolder<ParticleType<?>, BlightParticleType> SPORE = PARTICLES.register("spore", BlightParticleType::new);
    /** Hex ember: a small flickering violet ember that burns down (meant to replace magic_spark). */
    public static final DeferredHolder<ParticleType<?>, BlightParticleType> HEX_EMBER = PARTICLES.register("hex_ember", BlightParticleType::new);
    /** Hex skull: a small bone-white skull, only its eyes glow violet (meant to replace magic_skull). */
    public static final DeferredHolder<ParticleType<?>, BlightParticleType> HEX_SKULL = PARTICLES.register("hex_skull", BlightParticleType::new);
    /** Rune: one of several angular carved runes, violet (meant to replace magic_stripe). */
    public static final DeferredHolder<ParticleType<?>, BlightParticleType> RUNE = PARTICLES.register("rune", BlightParticleType::new);

    private BlightcallerParticles() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
