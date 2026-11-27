package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.curse.CurseEffects;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.render.BuffParticleSpawner;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.api.spell.fx.ParticleGroupBuilder;
import net.spell_engine.fx.SpellEngineParticles;

/**
 * Particles of the curses, drawn on every cursed entity the client sees (Spell Engine spawns them for synchronized
 * effects). One look per curse so three curses on one mob can be told apart: Soulburn burning skulls (bone that catches a
 * violet soul flame) and hex embers, Soul Rot green rot drips, rot smoke and sculk souls, Enfeeble grey violet haze sinking
 * down with runes, Gravebind dark dust at the feet and pale motes (its chains: client.fx.GravebindChains), Soul Rend cyan
 * shreds torn upwards out of the body, Pestilence an olive rot smoke with matte spores.
 * <p>
 * Phase 14B1: Soulburn, Soul Rot, Enfeeble (runes) and Pestilence use Blightcaller's own particles (client.fx.BlightParticle);
 * the haze of Enfeeble stays Spell Engine's smoke (user 27.11.), Gravebind and Soul Rend are unchanged.
 */
public final class CurseParticles {
    // colour plan of phase 12B: violet and the greens a little bluer, so that Complementary does not turn the violet pink
    // (next to Arcane) and the greens yellow (like Pestilence)
    /** Blue violet #6F5BE8, opaque (RGBA; until 12B #8A5BE0). */
    public static final long VIOLET = 0x6F5BE8FFL;
    /** Sickly green of Soul Rot #5FBF5A, opaque (RGBA; until 12B #7FB24A). */
    public static final long ROT_GREEN = 0x5FBF5AFFL;
    /** Sickly green, half transparent (RGBA): wisps of Soul Rot. */
    public static final long ROT_GREEN_SOFT = 0x5FBF5A99L;
    /** Bright plague spores #C4D445 (RGBA, phase 12B). */
    public static final long PLAGUE_SPORE = 0xC4D445FFL;
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

    private static final String BURNING_SKULL = "blightcaller:burning_skull";
    private static final String HEX_EMBER = "blightcaller:hex_ember";
    private static final String ROT_DRIP = "blightcaller:rot_drip";
    private static final String ROT_SMOKE = "blightcaller:rot_smoke";
    private static final String RUNE = "blightcaller:rune";
    private static final String SPORE = "blightcaller:spore";

    private CurseParticles() {}

    public static void register() {
        // Soulburn: bone skulls rising from the body that catch fire and end as glowing violet skulls (the soul burns), violet
        // hex embers around it (no. 45 and 46 of the 14A list)
        ParticleGroup skulls = ParticleGroupBuilder.of(BURNING_SKULL)
                .motion(ParticleGroup.Motion.ASCEND)
                .color(VIOLET)
                .scale(1.0F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.0F).speed(0.03F, 0.06F).verticalOrigin(0.4F));
        ParticleGroup sparks = ParticleGroupBuilder.of(HEX_EMBER)
                .motion(ParticleGroup.Motion.FLOAT)
                .color(VIOLET)
                .scale(0.7F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(2.0F).speed(0.02F, 0.05F).verticalOrigin(0.2F));
        CustomParticleStatusEffect.register(CurseEffects.SOULBURN.get(),
                new BuffParticleSpawner(skulls, sparks).withFrequency(4).scaleWithAmplifier(false));

        // Soul Rot: green rot drips running down the body (they hang, fall and splash), glowing green rot smoke, now and then a
        // sculk soul rising (no skulls, no violet; no. 47 and 48)
        ParticleGroup drips = ParticleGroupBuilder.of(ROT_DRIP)
                .color(ROT_GREEN)
                .scale(1.8F)
                .glow(true)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(3.0F).speed(0.0F, 0.02F).verticalOrigin(0.8F));
        ParticleGroup wisps = ParticleGroupBuilder.of(ROT_SMOKE)
                .color(ROT_GREEN_SOFT)
                .scale(0.7F)
                .glow(true)
                .motion(ParticleGroup.Motion.ASCEND)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.0F).speed(0.01F, 0.03F).verticalOrigin(0.3F));
        ParticleGroup souls = ParticleGroupBuilder.of("minecraft:sculk_soul")
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(0.35F).speed(0.01F, 0.03F).verticalOrigin(0.5F));
        CustomParticleStatusEffect.register(CurseEffects.SOUL_ROT.get(),
                new BuffParticleSpawner(drips, wisps, souls).withFrequency(4).scaleWithAmplifier(false));

        // Enfeeble: grey violet haze sinking from the head down, slow and dull (no glow), clearly not a damage curse; the haze
        // stays Spell Engine's smoke (looks better, user 27.11.), the shreds are runes in their own violet (no. 49 and 50)
        ParticleGroup haze = ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
                .color(ASH_VIOLET)
                .scale(1.3F)
                .gravity(0.02F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(2.5F).speed(0.0F, 0.01F).verticalOrigin(1.0F));
        ParticleGroup motes = ParticleGroupBuilder.of(RUNE)
                .motion(ParticleGroup.Motion.DECELERATE)
                .scale(1.6F)
                .glow(true)
                .gravity(0.03F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(2.0F).speed(0.0F, 0.01F).verticalOrigin(0.9F));
        CustomParticleStatusEffect.register(CurseEffects.ENFEEBLE.get(),
                new BuffParticleSpawner(haze, motes).withFrequency(5).scaleWithAmplifier(false));

        // Gravebind: dark grave dust at the feet and a few pale motes sinking down the chains (the chains themselves are
        // drawn by client.fx.GravebindChains; no hands any more, decided 20.11.)
        ParticleGroup graveMotes = ParticleGroupBuilder.magic(SpellEngineParticles.magic_spark, ParticleGroup.Motion.FLOAT)
                .color(GRAVE_PALE)
                .scale(0.6F)
                .gravity(0.02F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(0.8F).speed(0.0F, 0.01F).verticalOrigin(0.6F));
        ParticleGroup dust = ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
                .color(GRAVE_DUST)
                .scale(1.0F)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.5F).speed(0.0F, 0.01F).verticalOrigin(0.0F));
        CustomParticleStatusEffect.register(CurseEffects.GRAVEBIND.get(),
                new BuffParticleSpawner(graveMotes, dust).withFrequency(4).scaleWithAmplifier(false));

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

        // Pestilence: olive rot smoke around the body (not glowing: user at the halt of 14B1, it turned yellow at night with a
        // shader), matte yellow green spores drifting off it (no. 54 and 55)
        ParticleGroup plague = ParticleGroupBuilder.of(ROT_SMOKE)
                .color(PLAGUE_HAZE)
                .scale(1.1F)
                .glow(false)
                .motion(ParticleGroup.Motion.FLOAT)
                .batch(b -> b.shape(ParticleGroup.Shape.PIPE).count(1.5F).speed(0.01F, 0.02F).verticalOrigin(0.5F));
        ParticleGroup spores = ParticleGroupBuilder.of(SPORE)
                .motion(ParticleGroup.Motion.FLOAT)
                .color(PLAGUE_SPORE)
                .scale(1.15F)
                .batch(b -> b.shape(ParticleGroup.Shape.SPHERE).count(3.0F).speed(0.02F, 0.05F).verticalOrigin(0.6F));
        CustomParticleStatusEffect.register(CurseEffects.PESTILENCE.get(),
                new BuffParticleSpawner(plague, spores).withFrequency(4).scaleWithAmplifier(false));
    }
}
