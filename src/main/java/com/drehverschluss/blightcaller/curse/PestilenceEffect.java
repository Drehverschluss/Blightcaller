package com.drehverschluss.blightcaller.curse;

import net.minecraft.world.entity.LivingEntity;

/**
 * Pestilence / Seuche: soul damage over time like Soul Rot, and it spreads: every carrier passes a copy to one enemy
 * nearby ({@link Pestilence}). Damage ticks come from the curse core ({@link Curses#tick}), spreading runs right after.
 */
public class PestilenceEffect extends CurseEffect {
    public PestilenceEffect(int color) {
        super(color, true);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return true;
        }
        boolean goesOn = Curses.tick(entity, this);
        if (goesOn) {
            Pestilence.spread(entity);
        }
        return goesOn;
    }
}
