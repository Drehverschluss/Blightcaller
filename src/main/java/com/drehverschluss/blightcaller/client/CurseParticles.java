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
 * violet skulls and green sparks, Soul Rot green drips and sculk souls, Enfeeble grey violet haze sinking down, Gravebind
 * pale hands reaching up from dark dust at the feet, Soul Rend cyan shreds torn upwards out of the body, Pestilence an
 * olive haze with yellow green spores.
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
    /** Pale bone violet of Gravebind #B8A9D9, opaque (RGBA). */
    public static final long GRAVE_PALE = 0xB8A9D9FFL;
    /** Dark violet, half transparent (RGBA): grave dust at the feet. */
    public static final long GRAVE_DUST = 0x3A2A5099L;
    /** Soul cyan of Soul Rend #2DD4DA, opaque (RGBA). */
    public static final long SOUL_CYAN = 0x2DD4DAFFL;
    /** Sickly olive of Pestilence #A8B53A, opaque (RGBA). */
    public static final long PLAGUE_OLIVE = 0xA8B53AFFL;
    /** Sickly olive, half transparent (RGBA): plague haze. */
    public static final long PLAGUE_HAZE = 0x8A9A2A88L;

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

        // Gravebind: pale ghostly hands reaching up at the feet, dark grave dust on the ground (no glow, slow)
        ParticleGroup hands = ParticleGroupBuilder.magic(SpellEngineParticles.sign_fist, ParticleGroup.Motion.ASCEND)
                .color(GRAVE_PALE)
                .scale(0.9F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.5F).speed(0.02F, 0.04F).verticalOrigin(0.0F));
        ParticleGroup dust = ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
                .color(GRAVE_DUST)
                .scale(1.0F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.5F).speed(0.0F, 0.01F).verticalOrigin(0.0F));
        CustomParticleStatusEffect.register(CurseEffects.GRAVEBIND.get(),
                new BuffParticleSpawner(hands, dust).withFrequency(4).scaleWithAmplifier(false));

        // Soul Rend: cyan soul shreds torn upwards out of the chest, now and then a vanilla soul
        ParticleGroup shreds = ParticleGroupBuilder.magic(SpellEngineParticles.magic_stripe, ParticleGroup.Motion.ASCEND)
                .color(SOUL_CYAN)
                .scale(1.4F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(4.0F).speed(0.05F, 0.1F).verticalOrigin(0.5F));
        ParticleGroup rendSouls = ParticleGroupBuilder.of("minecraft:soul")
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(0.8F).speed(0.01F, 0.03F).verticalOrigin(0.7F));
        CustomParticleStatusEffect.register(CurseEffects.SOUL_REND.get(),
                new BuffParticleSpawner(shreds, rendSouls).withFrequency(4).scaleWithAmplifier(false));

        // Pestilence: olive plague haze around the body, yellow green spores floating off it
        ParticleGroup plague = ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
                .color(PLAGUE_HAZE)
                .scale(1.1F)
                .motion(ParticleGroup.Motion.FLOAT)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.5F).speed(0.01F, 0.02F).verticalOrigin(0.5F));
        ParticleGroup spores = ParticleGroupBuilder.magic(SpellEngineParticles.magic_spark, ParticleGroup.Motion.FLOAT)
                .color(PLAGUE_OLIVE)
                .scale(0.9F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.SPHERE).count(3.0F).speed(0.02F, 0.05F).verticalOrigin(0.6F));
        CustomParticleStatusEffect.register(CurseEffects.PESTILENCE.get(),
                new BuffParticleSpawner(plague, spores).withFrequency(4).scaleWithAmplifier(false));
    }
}
