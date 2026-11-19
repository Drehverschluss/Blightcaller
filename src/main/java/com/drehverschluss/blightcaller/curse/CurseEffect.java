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
 */
public class CurseEffect extends MobEffect {
    public CurseEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
        Synchronized.configure(this, true);
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
