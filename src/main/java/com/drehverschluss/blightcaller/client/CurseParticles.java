package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.curse.CurseEffects;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.render.BuffParticleSpawner;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.api.spell.fx.ParticleGroupBuilder;
import net.spell_engine.fx.SpellEngineParticles;

/**
 * Particles of the curses, drawn on every cursed entity the client sees (Spell Engine spawns them for synchronized
 * effects). Placeholders until the graphics phase: Spell Engine's particles in violet with a little pale green.
 */
public final class CurseParticles {
    /** Violet #8A5BE0, opaque (RGBA). */
    public static final long VIOLET = 0x8A5BE0FFL;
    /** Pale green #99FF66, opaque (RGBA). */
    public static final long PALE_GREEN = 0x99FF66FFL;

    private CurseParticles() {}

    public static void register() {
        // Soulburn: glowing violet skulls rising from the body, pale green sparks around it (visible at night too)
        ParticleGroup skulls = ParticleGroupBuilder.magic(SpellEngineParticles.magic_skull, ParticleGroup.Motion.ASCEND)
                .color(VIOLET)
                .scale(1.0F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.0F).speed(0.03F, 0.06F).verticalOrigin(0.4F));
        ParticleGroup sparks = ParticleGroupBuilder.magic(SpellEngineParticles.magic_spark, ParticleGroup.Motion.FLOAT)
                .color(PALE_GREEN)
                .scale(0.7F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(2.0F).speed(0.02F, 0.05F).verticalOrigin(0.2F));
        CustomParticleStatusEffect.register(CurseEffects.SOULBURN.get(),
                new BuffParticleSpawner(skulls, sparks).withFrequency(4).scaleWithAmplifier(false));
    }
}
