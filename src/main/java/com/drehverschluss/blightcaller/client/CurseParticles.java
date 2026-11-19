package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.curse.CurseEffects;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.render.BuffParticleSpawner;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.api.spell.fx.ParticleGroupBuilder;
import net.spell_engine.fx.SpellEngineParticles;

/**
 * Particles of the curses, drawn on every cursed entity the client sees (Spell Engine spawns them for synchronized
 * effects). Placeholders until the graphics phase, one look per curse so three curses on one mob can be told apart: Soulburn
 * violet skulls and green sparks, Soul Rot green drips and sculk souls, Enfeeble grey violet haze sinking down.
 */
public final class CurseParticles {
    /** Violet #8A5BE0, opaque (RGBA). */
    public static final long VIOLET = 0x8A5BE0FFL;
    /** Pale green #99FF66, opaque (RGBA). */
    public static final long PALE_GREEN = 0x99FF66FFL;
    /** Sickly green of Soul Rot #7FB24A, opaque (RGBA). */
    public static final long ROT_GREEN = 0x7FB24AFFL;
    /** Sickly green, half transparent (RGBA): wisps of Soul Rot. */
    public static final long ROT_GREEN_SOFT = 0x7FB24A99L;
    /** Grey violet of Enfeeble #7D7088, slightly transparent (RGBA). */
    public static final long ASH_VIOLET = 0x7D7088DDL;

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

        // Soul Rot: sickly green drops dripping down the body, now and then a sculk soul rising (no skulls, no violet)
        ParticleGroup drips = ParticleGroupBuilder.of(SpellEngineParticles.dripping_blood)
                .color(ROT_GREEN)
                .scale(1.8F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(3.0F).speed(0.0F, 0.02F).verticalOrigin(0.8F));
        ParticleGroup wisps = ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
                .color(ROT_GREEN_SOFT)
                .scale(0.7F)
                .motion(ParticleGroup.Motion.ASCEND)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.0F).speed(0.01F, 0.03F).verticalOrigin(0.3F));
        ParticleGroup souls = ParticleGroupBuilder.of("minecraft:sculk_soul")
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(0.35F).speed(0.01F, 0.03F).verticalOrigin(0.5F));
        CustomParticleStatusEffect.register(CurseEffects.SOUL_ROT.get(),
                new BuffParticleSpawner(drips, wisps, souls).withFrequency(4).scaleWithAmplifier(false));

        // Enfeeble: grey violet haze sinking from the head down, slow and dull (no glow), clearly not a damage curse
        ParticleGroup haze = ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
                .color(ASH_VIOLET)
                .scale(1.3F)
                .gravity(0.02F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(2.5F).speed(0.0F, 0.01F).verticalOrigin(1.0F));
        ParticleGroup motes = ParticleGroupBuilder.magic(SpellEngineParticles.magic_stripe, ParticleGroup.Motion.DECELERATE)
                .color(ASH_VIOLET)
                .scale(1.0F)
                .gravity(0.03F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.5F).speed(0.0F, 0.01F).verticalOrigin(0.9F));
        CustomParticleStatusEffect.register(CurseEffects.ENFEEBLE.get(),
                new BuffParticleSpawner(haze, motes).withFrequency(5).scaleWithAmplifier(false));
    }
}
