package com.drehverschluss.blightcaller.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config of Blightcaller ({@code config/blightcaller-client.toml}), editable in game in the mod list (NeoForge
 * configuration screen). Only changes what this player sees and hears; the curses work the same either way.
 * <p>
 * Plain {@link ModConfigSpec} without client classes: the damage hook that reads it sits in common code
 * ({@code mixin.LivingEntityMixin}) and only runs on the client.
 */
public final class BlightcallerClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue CURSE_TICK_FLASH;
    public static final ModConfigSpec.BooleanValue CURSE_TICK_SOUND;
    public static final ModConfigSpec.BooleanValue DRAIN_SOUL_GHOST;


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Feedback of curse ticks (the damage curses deal every second). Real hits always flash and sound.")
                .translation("blightcaller.configuration.curse_ticks")
                .push("curse_ticks");
        CURSE_TICK_FLASH = builder
                .comment("A cursed mob flashes red on every curse tick.")
                .translation("blightcaller.configuration.curse_tick_flash")
                .define("flash", true);
        CURSE_TICK_SOUND = builder
                .comment("A cursed mob plays its hurt sound on every curse tick.")
                .translation("blightcaller.configuration.curse_tick_sound")
                .define("hurt_sound", true);
        builder.pop();
        builder.comment("Temporary: Drain Soul with and without the torn soul, to compare in game. Will be removed once decided.")
                .translation("blightcaller.configuration.drain_soul_preview")
                .push("drain_soul_preview");
        DRAIN_SOUL_GHOST = builder
                .comment("A see-through copy of the target is torn out of its body at every pulse.")
                .translation("blightcaller.configuration.drain_soul_ghost")
                .define("ghost", true);
        builder.pop();
        SPEC = builder.build();
    }

    private BlightcallerClientConfig() {}

    /** Red flash on curse ticks (default on, also while the config is not loaded). */
    public static boolean curseTickFlash() {
        return !SPEC.isLoaded() || CURSE_TICK_FLASH.get();
    }

    /** Ghost of the drained target (temporary comparison, default on). */
    public static boolean drainSoulGhost() {
        return !SPEC.isLoaded() || DRAIN_SOUL_GHOST.get();
    }

    /** Hurt sound on curse ticks (default on, also while the config is not loaded). */
    public static boolean curseTickSound() {
        return !SPEC.isLoaded() || CURSE_TICK_SOUND.get();
    }
}
