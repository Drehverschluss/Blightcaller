package com.drehverschluss.blightcaller.curse;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.Synchronized;

/**
 * Base class of all curses. The effect carries display, saving, milk and totem; the damage comes from the entries in
 * the {@link CurseData} attachment, one per caster. The effect lasts as long as the longest entry (plus one tick, so the
 * last damage tick always happens, see {@link Curses#syncEffect}).
 * <p>
 * Curse effects are applied without vanilla particles ({@code visible=false}); each curse draws its own particles on
 * the client through Spell Engine ({@code CustomParticleStatusEffect}, registered in {@code client.CurseParticles}),
 * which only sees effects that are synchronized ({@link Synchronized}).
 * <p>
 * Every curse must also be in the mob effect tag {@code #blightcaller:curses} (curse level, commands, Jade).
 * <p>
 * <b>Curses without damage</b> ({@code damaging = false}, e.g. Enfeeble): they count for the curse level and keep one
 * entry per caster (duration, Jade, later phases), but their entries never hit the target: no 0-damage hit, no red flash,
 * no hurt sound. What they do comes from the effect itself (attribute modifiers).
 */
public class CurseEffect extends MobEffect {
    private final boolean damaging;

    /** A curse that deals soul damage over time. */
    public CurseEffect(int color) {
        this(color, true);
    }

    public CurseEffect(int color, boolean damaging) {
        super(MobEffectCategory.HARMFUL, color);
        this.damaging = damaging;
        Synchronized.configure(this, true);
    }

    /** Whether the entries of this curse deal damage on their ticks. */
    public boolean dealsDamage() {
        return damaging;
    }

    /**
     * Amplifier for a new effect of this curse on the target when no spell set one (command, later phases). Spells set
     * the amplifier themselves with their status effect impact; an existing effect always keeps its amplifier.
     */
    public int defaultAmplifier(LivingEntity target) {
        return 0;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /**
     * Settles all entries of this curse that are due. Returns {@code false} (the effect is removed) once the curse has no
     * entries left. On the client nothing happens.
     */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return true;
        }
        return Curses.tick(entity, this);
    }
}
