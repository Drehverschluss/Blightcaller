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
        SPEC = builder.build();
    }

    private BlightcallerClientConfig() {}

    /** Red flash on curse ticks (default on, also while the config is not loaded). */
    public static boolean curseTickFlash() {
        return !SPEC.isLoaded() || CURSE_TICK_FLASH.get();
    }

    /** Hurt sound on curse ticks (default on, also while the config is not loaded). */
    public static boolean curseTickSound() {
        return !SPEC.isLoaded() || CURSE_TICK_SOUND.get();
    }
}
