package com.drehverschluss.blightcaller.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

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
    public static final ModConfigSpec.BooleanValue DRAIN_SOUL_TORN_SOUL;
    public static final ModConfigSpec.BooleanValue CURSE_VEIL;
    public static final ModConfigSpec.EnumValue<IconMode> CURSE_ICONS;
    public static final ModConfigSpec.IntValue CURSE_ICON_RANGE;
    public static final ModConfigSpec.BooleanValue CURSE_ICONS_WITH_JADE;
    public static final ModConfigSpec.EnumValue<HudRow> CURSE_HUD_ROW;

    /** Which mobs show the curse icons above their head. */
    public enum IconMode implements TranslatableEnum {
        /** No icons above the head. */
        OFF,
        /** Only the mob you look at. */
        LOOKED_AT,
        /** The mob you look at and every mob with one of your own curses in range. */
        OWN_TARGETS;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("blightcaller.configuration.curse_icons." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Where the row of curses goes at the mob box of Health Bars. */
    public enum HudRow implements TranslatableEnum {
        /** On the side with room: below a box at the top, above a box in the middle or at the bottom. */
        AUTO,
        ABOVE,
        BELOW,
        OFF;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("blightcaller.configuration.curse_hud_row." + name().toLowerCase(Locale.ROOT));
        }
    }

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

        builder.comment("How you see curses on mobs, also without Jade.")
                .translation("blightcaller.configuration.curse_display")
                .push("curse_display");
        CURSE_VEIL = builder
                .comment("A faint violet veil over cursed mobs, stronger with every curse level (1 to 3, pulsing at 3).")
                .translation("blightcaller.configuration.curse_veil")
                .define("veil", true);
        CURSE_ICONS = builder
                .comment("Small curse icons above the head, each with a ring that runs down with its remaining time.",
                        "OFF: none. LOOKED_AT: only the mob you look at. OWN_TARGETS: also every mob with one of your own curses in range.")
                .translation("blightcaller.configuration.curse_icons")
                .defineEnum("icons", IconMode.OWN_TARGETS);
        CURSE_ICON_RANGE = builder
                .comment("Range in blocks for the curse icons (the mob you look at and your own targets).")
                .translation("blightcaller.configuration.curse_icon_range")
                .defineInRange("icon_range", 16, 4, 64);
        CURSE_ICONS_WITH_JADE = builder
                .comment("Keep the curse icons above the head while Jade is installed (Jade shows the details of one mob, the icons an overview).")
                .translation("blightcaller.configuration.curse_icons_with_jade")
                .define("icons_with_jade", true);
        CURSE_HUD_ROW = builder
                .comment("Row of curses with seconds at the mob box of Health Bars (only with Health Bars).",
                        "AUTO: below a box at the top of the screen, above a box in the middle or at the bottom.")
                .translation("blightcaller.configuration.curse_hud_row")
                .defineEnum("hud_row", HudRow.AUTO);
        builder.pop();

        builder.comment("Look of Drain Soul.")
                .translation("blightcaller.configuration.drain_soul")
                .push("drain_soul");
        DRAIN_SOUL_TORN_SOUL = builder
                .comment("A see-through copy of the target is torn out of its body at every pulse and drawn to you.")
                .translation("blightcaller.configuration.drain_soul_torn_soul")
                .define("torn_soul", true);
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

    /** The torn soul of Drain Soul (default on). */
    public static boolean drainSoulTornSoul() {
        return !SPEC.isLoaded() || DRAIN_SOUL_TORN_SOUL.get();
    }

    /** The veil over cursed mobs (default on). */
    public static boolean curseVeil() {
        return !SPEC.isLoaded() || CURSE_VEIL.get();
    }

    public static IconMode curseIcons() {
        return SPEC.isLoaded() ? CURSE_ICONS.get() : IconMode.OWN_TARGETS;
    }

    public static int curseIconRange() {
        return SPEC.isLoaded() ? CURSE_ICON_RANGE.get() : 16;
    }

    public static boolean curseIconsWithJade() {
        return !SPEC.isLoaded() || CURSE_ICONS_WITH_JADE.get();
    }

    public static HudRow curseHudRow() {
        return SPEC.isLoaded() ? CURSE_HUD_ROW.get() : HudRow.AUTO;
    }
}
