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
    public static final ModConfigSpec.DoubleValue ENFEEBLE_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue GRAVEBIND_SLOW_PER_LEVEL;
    public static final ModConfigSpec.IntValue PESTILENCE_SPREAD_INTERVAL;
    public static final ModConfigSpec.DoubleValue PESTILENCE_SPREAD_RADIUS;
    public static final ModConfigSpec.IntValue PESTILENCE_MAX_CARRIERS;

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
        ENFEEBLE_BONUS_PER_LEVEL = builder
                .comment("Enfeeble: extra damage the target takes from all sources, per effect level, as a fraction.",
                        "The spell gives level 2 (amplifier 1) to normal targets and level 1 (amplifier 0) to targets in the",
                        "entity tag #blightcaller:curse_resistant (bosses). 0.05 = +10% on normal targets, +5% on bosses.",
                        "Only one Enfeeble effect is on a target, so several casters do not stack it.")
                .defineInRange("enfeeble_bonus_per_level", 0.05, 0.0, 1.0);
        GRAVEBIND_SLOW_PER_LEVEL = builder
                .comment("Gravebind: movement speed the target loses per effect level, as a fraction of its total speed.",
                        "The spell gives level 2 (amplifier 1) to normal targets and level 1 (amplifier 0) to targets in the",
                        "entity tag #blightcaller:curse_resistant (bosses). 0.3 = 60% slower, bosses 30% slower (at most 95%).",
                        "Spell Engine skips movement impairing effects on #spell_engine:bosses; Gravebind does not count as one",
                        "for that check, so bosses get the half slow instead of none.")
                .defineInRange("gravebind_slow_per_level", 0.3, 0.0, 0.95);
        builder.pop();

        builder.comment("Pestilence: a curse with damage over time that spreads. Every carrier passes a copy to one enemy",
                        "nearby that has no Pestilence yet. The copy keeps the snapshot and the remaining time of its source",
                        "and is never extended by Curse Duration again. Damage always counts for the original caster.",
                        "Only enemies: never players, never tamed pets, never the caster.")
                .push("pestilence");
        PESTILENCE_SPREAD_INTERVAL = builder
                .comment("Game ticks between two spreading attempts of one carrier (40 = every 2 seconds).")
                .defineInRange("spread_interval", 40, 1, 1200);
        PESTILENCE_SPREAD_RADIUS = builder
                .comment("Blocks around a carrier in which Pestilence looks for a new carrier (needs a line of sight).")
                .defineInRange("spread_radius", 5.0, 0.0, 32.0);
        PESTILENCE_MAX_CARRIERS = builder
                .comment("Carriers per cast, the first target included (4 = the target and three more).",
                        "The amplifier of the Pestilence status effect in the spell adds to it, so skill tree modifiers with",
                        "effect_amplifier_add raise it per player without changing this value.")
                .defineInRange("max_carriers", 4, 1, 64);
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
