package com.drehverschluss.blightcaller.curse;

/**
 * Pestilence / Seuche: soul damage over time like Soul Rot, and it spreads: every carrier passes a copy to one enemy
 * nearby ({@link Pestilence}). Damage ticks and spreading both run in the curse core ({@link Curses#tickAll}).
 */
public class PestilenceEffect extends CurseEffect {
    public PestilenceEffect(int color) {
        super(color, true);
    }
}
