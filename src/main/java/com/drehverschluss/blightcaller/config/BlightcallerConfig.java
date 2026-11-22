package com.drehverschluss.blightcaller.config;

import java.util.Arrays;
import java.util.List;
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
    public static final ModConfigSpec.IntValue PESTILENCE_FIRST_SPREAD;
    public static final ModConfigSpec.DoubleValue PESTILENCE_SPREAD_RADIUS;
    public static final ModConfigSpec.IntValue PESTILENCE_MAX_CARRIERS;
    public static final ModConfigSpec.BooleanValue SOUL_MIGRATION_ALWAYS_ON;
    public static final ModConfigSpec.IntValue SOUL_MIGRATION_MAX_JUMPS;
    public static final ModConfigSpec.DoubleValue SOUL_MIGRATION_RANGE;
    public static final ModConfigSpec.IntValue BLIGHTBLOOM_MAX_PER_TICK;
    public static final ModConfigSpec.IntValue GRAVE_MOULD_MAX_PATCHES;
    public static final ModConfigSpec.ConfigValue<String> SKILL_TREE_MODE;
    public static final ModConfigSpec.BooleanValue SKILL_TREE_WEAPON_SKILLS;
    public static final ModConfigSpec.ConfigValue<String> SKILL_TREE_CLASS_TREE_LAYOUT;
    public static final ModConfigSpec.BooleanValue SKILL_TREE_CLASS_LOCK;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SKILL_TREE_CLASS_LOCK_TABS;

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
                        "The spell gives level 2 (amplifier 1); targets in the entity tag #blightcaller:curse_resistant (bosses)",
                        "get half of it. 0.05 = +10% on normal targets, +5% on bosses.",
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
        PESTILENCE_FIRST_SPREAD = builder
                .comment("Game ticks from the cast to the first spreading attempt of the first target (20 = after one second).",
                        "Later attempts follow every spread_interval ticks. Copies on new carriers wait one spread_interval.")
                .defineInRange("first_spread", 20, 1, 1200);
        PESTILENCE_SPREAD_RADIUS = builder
                .comment("Blocks around a carrier in which Pestilence looks for a new carrier (needs a line of sight).")
                .defineInRange("spread_radius", 5.0, 0.0, 32.0);
        PESTILENCE_MAX_CARRIERS = builder
                .comment("Carriers per cast, the first target included (4 = the target and three more).",
                        "The amplifier of the Pestilence status effect in the spell adds to it, so skill tree modifiers with",
                        "effect_amplifier_add raise it per player without changing this value.")
                .defineInRange("max_carriers", 4, 1, 64);
        builder.pop();

        builder.comment("Soul Migration: when a target with curses of a caster dies, those curses move on to the nearest enemy",
                        "nearby (line of sight, the same target rules as Pestilence), with their remaining time and their snapshot,",
                        "never extended by Curse Duration again. All curses of that caster move, also those without damage;",
                        "curses detonated by Last Rites are gone before the hit and do not move. Nothing moves while the caster",
                        "is offline. A moving Pestilence takes no new place in its chain and only spreads on while the chain",
                        "is not full. In the skill tree it is the first passive of side A (spell blightcaller:soul_migration).")
                .push("soul_migration");
        SOUL_MIGRATION_ALWAYS_ON = builder
                .comment("true: every caster has Soul Migration, also without the skill tree (for packs without Skill Tree).")
                .define("always_on", false);
        SOUL_MIGRATION_MAX_JUMPS = builder
                .comment("How often one curse may move on (a curse that already moved this often ends with its target).",
                        "Every chain ends by itself anyway, because only the remaining time moves.")
                .defineInRange("max_migrations", 3, 0, 64);
        SOUL_MIGRATION_RANGE = builder
                .comment("Blocks around the dead target in which the new target is searched (needs a line of sight).")
                .defineInRange("range", 6.0, 0.0, 32.0);
        builder.pop();

        builder.comment("Blightbloom: when an enemy with curses of a caster dies, it bursts and puts Soulburn on the enemies around it",
                        "(skill tree, second passive of side A, spell blightcaller:blightbloom; radius, duration and damage are in",
                        "that spell). An enemy that dies with such a Soulburn bursts as well, so blooms can run through a horde.",
                        "Blooms happen in the server tick after the death, after Soul Migration. Nothing happens while the caster",
                        "is offline.")
                .push("blightbloom");
        BLIGHTBLOOM_MAX_PER_TICK = builder
                .comment("Most blooms in one server tick, against lag spikes in big hordes. Further blooms wait for the next tick",
                        "(none is lost). It only spreads the work over ticks, it is no limit on the chain itself.")
                .defineInRange("max_per_tick", 3, 1, 1000);
        builder.pop();

        builder.comment("Grave Mould: passive of Gravemother's Herbarium. Where Blight Burst bursts, a patch of mould stays on the ground",
                        "and puts Soulburn on the enemies in it every second (spell blightcaller:grave_mould: radius, duration, interval",
                        "and Soulburn are in that spell).")
                .push("grave_mould");
        GRAVE_MOULD_MAX_PATCHES = builder
                .comment("Most patches of one player at a time. A new patch makes the oldest one wither away.")
                .defineInRange("max_patches", 2, 1, 64);
        builder.pop();

        builder.comment("Blightcaller skills (only with Skill Tree (RPG Series) installed).")
                .push("skill_tree");
        SKILL_TREE_MODE = builder
                .comment("Where the Blightcaller branch goes.",
                        "class_tree: into the Class Skills tab of Skill Tree, next to the other classes, also with More RPG Classes -",
                        "  Skill Tree (then into its circle).",
                        "auto: like class_tree, but with More RPG Classes - Skill Tree installed into an own Blightcaller tab instead.",
                        "own_tab: always into the own Blightcaller tab.",
                        "If the branch cannot be added to the Class Skills tab (unknown layout, data errors), the own tab is used",
                        "and the log says why; nodes taken in the Class Skills tab stay saved and come back after the fix.",
                        "Changing this setting moves the branch: nodes taken in the old place are reset and their points can be",
                        "spent again. Takes effect on /reload or restart. Works together with the Stormcaller branch.")
                // Arrays.asList: the check must accept null (List.of throws when a new config file is made)
                .defineInList("mode", "class_tree", Arrays.asList("auto", "own_tab", "class_tree"));
        SKILL_TREE_WEAPON_SKILLS = builder
                .comment("true: three nodes for Hex Bolt and Blight Burst (the spells of the wand and the grimoire) in the Weapon Skills",
                        "tab of Skill Tree, like the staves of the other schools: one fixed node and a choice of two. With More RPG",
                        "Classes - Skill Tree installed only with mode class_tree (then in its Weapon Skills tab). If the nodes",
                        "cannot be added, only they are missing (the log says why) and taken nodes stay saved. Turning this off",
                        "resets taken nodes there; their points can be spent again. The place is data:",
                        "data/blightcaller/blightcaller_skill_tree/weapon_branch.json")
                .define("weapon_skills", true);
        SKILL_TREE_CLASS_TREE_LAYOUT = builder
                .comment("How the branch gets its place in the Class Skills tab.",
                        "respace: all branches of the circle are spread out evenly again, the Blightcaller root sits on the same",
                        "  circle as the others (all branches move a little further out if needed). Only positions change.",
                        "gap: the other branches stay where they are, the Blightcaller branch starts further out between two of them.",
                        "respace falls back to gap if the circle is not regular. The place and the spacing are data:",
                        "data/blightcaller/blightcaller_skill_tree/class_branch.json")
                .defineInList("class_tree_layout", "respace", Arrays.asList("respace", "gap"));
        SKILL_TREE_CLASS_LOCK = builder
                .comment("Own Blightcaller tab only: a player with a class (a root) in one of the class_lock_tabs cannot take the",
                        "Blightcaller root, and a player on the Blightcaller path cannot take a root in those tabs. Players who",
                        "already have both keep them. In the Class Skills tab itself only one class can be taken anyway.")
                .define("class_lock", true);
        SKILL_TREE_CLASS_LOCK_TABS = builder
                .comment("Skill tabs that count as a class for class_lock (Pufferfish's Skills category ids). Nodes of a mod's own",
                        "branch kept in the Class Skills tab (ids starting with <mod id>_, e.g. after a fallback) do not count as",
                        "another class for that mod's own tab.")
                .defineListAllowEmpty("class_lock_tabs",
                        Arrays.asList("skill_tree_rpgs:class_skills", "stormcaller:stormcaller", "blightcaller:blightcaller"),
                        () -> "skill_tree_rpgs:class_skills",
                        o -> o instanceof String text && net.minecraft.resources.ResourceLocation.tryParse(text) != null);
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

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static String get(ModConfigSpec.ConfigValue<String> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** {@code class_lock_tabs}, or its default while the server config is not loaded. */
    public static List<? extends String> classLockTabs() {
        return SPEC.isLoaded() ? SKILL_TREE_CLASS_LOCK_TABS.get() : SKILL_TREE_CLASS_LOCK_TABS.getDefault();
    }
}
