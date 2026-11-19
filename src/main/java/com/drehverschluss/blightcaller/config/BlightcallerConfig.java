package com.drehverschluss.blightcaller.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of Blightcaller ({@code config/blightcaller-server.toml}).
 * <p>
 * SERVER type on purpose: the values belong to the world and its rules (all players see the same curses), and NeoForge
 * syncs the file to every client on login, so tooltips on the client can later show the same values the server uses.
 * <p>
 * One section per topic. Later phases add their own sections (curse migration, pestilence) below {@code curses}.
 */
public final class BlightcallerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_CURSE_DURATION_BONUS;
    public static final ModConfigSpec.IntValue CURSE_TICK_INTERVAL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Curses: soul damage over time. Each caster has their own entry per curse on a target, ticking with",
                        "the spell power they had when the curse was cast. Damage per tick = power x factor of the spell",
                        "(x critical damage if the cast was critical) x soul vulnerability of the target.")
                .push("curses");
        MAX_CURSE_DURATION_BONUS = builder
                .comment("Highest bonus from the Curse Duration attribute (blightcaller:curse_duration), as a fraction.",
                        "0.6 = curses last at most 60% longer. Curses that moved to another target are never extended again.")
                .defineInRange("max_curse_duration_bonus", 0.6, 0.0, 9.0);
        CURSE_TICK_INTERVAL = builder
                .comment("Game ticks between two damage ticks of a curse (20 = one tick per second).",
                        "Every tick is a hit: the target flashes red and plays its hurt sound.",
                        "If a curse ends between two ticks, it ticks once more at its end with the matching share of the damage.")
                .defineInRange("curse_tick_interval", 20, 1, 200);
        builder.pop();

        SPEC = builder.build();
    }

    private BlightcallerConfig() {}

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
