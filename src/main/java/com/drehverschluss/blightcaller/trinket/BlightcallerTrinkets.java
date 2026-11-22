package com.drehverschluss.blightcaller.trinket;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.compat.curios.CuriosCompat;
import com.drehverschluss.blightcaller.config.ConfigDefaults;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.effect.CustomStatusEffect;
import net.spell_engine.api.effect.Effects;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.EffectConfig;
import net.spell_power.api.SpellPowerMechanics;
import net.spell_power.api.SpellSchools;
import net.tiny_config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Rings, amulets and relics (phase 8B). Only registered when Curios is loaded ({@link CuriosCompat}); this class itself has
 * no Curios reference and is always loaded (ids, config, status effects).
 * <ul>
 *   <li>Rotheart Signet / Amulet (spell critical chance and damage, side A) and Witchbone Band / Pendant (spell haste, side B): unique
 *   jewelry (slots {@code ring} / {@code necklace}), Soul Spell Power and Curse Duration on both pairs, attributes from {@link #CONFIG}.
 *   Phase 9B swapped the second values of 8B: haste is worth most to side B (hex bolts and Drain Soul are bound by cast time), a critical
 *   curse carries its critical hit through every tick and every copy (side A).</li>
 *   <li>Grave Candle, Blight Orb, Wailing Skull: relics (slot {@code spell_trinket}), each with one spell in its spell container
 *   ({@code data/blightcaller/spell/<spell>.json}: chance, duration, cooldown). The status effects of the first two are
 *   registered here in any case, their modifiers come from {@link #CONFIG}. The skull uses the existing Enfeeble.</li>
 * </ul>
 * All of them are loot only (tags {@code rpg_series:loot_tier/*}), no recipes.
 */
public final class BlightcallerTrinkets {
    public static final ResourceLocation ROTHEART_RING = Blightcaller.id("rotheart_ring");
    public static final ResourceLocation ROTHEART_NECKLACE = Blightcaller.id("rotheart_necklace");
    public static final ResourceLocation WITCHBONE_RING = Blightcaller.id("witchbone_ring");
    public static final ResourceLocation WITCHBONE_NECKLACE = Blightcaller.id("witchbone_necklace");
    public static final ResourceLocation GRAVE_CANDLE = Blightcaller.id("grave_candle");
    public static final ResourceLocation BLIGHT_ORB = Blightcaller.id("blight_orb");
    public static final ResourceLocation WAILING_SKULL = Blightcaller.id("wailing_skull");

    /** Spells in the containers of the relics. */
    public static final ResourceLocation GRAVE_EMBER_SPELL = Blightcaller.id("grave_ember");
    public static final ResourceLocation BLIGHT_SURGE_SPELL = Blightcaller.id("blight_surge");
    public static final ResourceLocation WAILING_SCREAM_SPELL = Blightcaller.id("wailing_scream");

    // Defaults of trinkets.json (phase 9B: gain of jewelry and relic on tier 4 in the band of the other schools, +23 to +57 %)
    private static final float UNIQUE_SOUL = 0.12F;            // 8B: 0.08 like the school uniques of Jewelry
    private static final float UNIQUE_CURSE_DURATION = 0.03F;  // on both pairs, like the shock chance on both Stormcaller pairs
    private static final float ROTHEART_CRIT_CHANCE = 0.06F;   // Rotheart (side A): a critical curse ticks critically to its end
    private static final float ROTHEART_CRIT_DAMAGE = 0.1F;
    private static final float WITCHBONE_HASTE = 0.05F;        // Witchbone (side B): more hex bolts, faster Drain Soul
    private static final float GRAVE_EMBER_SOUL = 0.15F;       // like the lesser procs of Relics
    private static final float GRAVE_EMBER_CURSE_DURATION = 0.05F;
    private static final float BLIGHT_SURGE_SOUL = 0.2F;       // like the orbs of Relics

    public enum Kind {
        /** Ring or amulet: attributes, no spell. */
        JEWELRY,
        /** Relic: one spell in the container, cannot be taken off while that spell is on cooldown. */
        RELIC
    }

    /**
     * One trinket item.
     * @param slot Curios slot (only for the record; the slot comes from the tags {@code curios:<slot>} and
     *             {@code spell_engine:spell_trinket})
     * @param spell spell of the container, null for jewelry
     */
    public record Def(ResourceLocation id, Kind kind, String slot, Rarity rarity, boolean fireResistant,
                      ResourceLocation spell, EffectConfig defaults) {
    }

    /** All seven, in the order of the creative tab. Rarity and fire resistance like the Stormcaller trinkets. */
    public static final List<Def> ITEMS = List.of(
            new Def(ROTHEART_RING, Kind.JEWELRY, "ring", Rarity.RARE, true, null, rotheartDefaults()),
            new Def(ROTHEART_NECKLACE, Kind.JEWELRY, "necklace", Rarity.RARE, true, null, rotheartDefaults()),
            new Def(WITCHBONE_RING, Kind.JEWELRY, "ring", Rarity.RARE, true, null, witchboneDefaults()),
            new Def(WITCHBONE_NECKLACE, Kind.JEWELRY, "necklace", Rarity.RARE, true, null, witchboneDefaults()),
            new Def(GRAVE_CANDLE, Kind.RELIC, "spell_trinket", Rarity.COMMON, false, GRAVE_EMBER_SPELL, EffectConfig.EMPTY),
            new Def(BLIGHT_ORB, Kind.RELIC, "spell_trinket", Rarity.UNCOMMON, false, BLIGHT_SURGE_SPELL, EffectConfig.EMPTY),
            new Def(WAILING_SKULL, Kind.RELIC, "spell_trinket", Rarity.RARE, false, WAILING_SCREAM_SPELL, EffectConfig.EMPTY));

    /**
     * Grave Candle: Soul Spell Power and Curse Duration for a few seconds. Curse Duration is read when a curse is put on, so
     * only curses cast during the effect last longer (the cap of the server config still applies).
     */
    public static final Effects.Entry GRAVE_EMBER = new Effects.Entry(Blightcaller.id("grave_ember"), "Grave Ember",
            "Increases soul spell power and curse duration",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0xB98CFF),
            new EffectConfig(List.of(
                    AttributeModifier.multiply(SpellSchools.SOUL.id, GRAVE_EMBER_SOUL),
                    AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), GRAVE_EMBER_CURSE_DURATION))));

    /** Blight Orb: Soul Spell Power for a few seconds. */
    public static final Effects.Entry BLIGHT_SURGE = new Effects.Entry(Blightcaller.id("blight_surge"), "Blight Surge",
            "Increases soul spell power",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0x99D169),
            new EffectConfig(List.of(AttributeModifier.multiply(SpellSchools.SOUL.id, BLIGHT_SURGE_SOUL))));

    /**
     * {@code config/blightcaller/trinkets.json}: attributes of the jewelry ({@code items}, keyed by item id; relics may get
     * attributes here too) and the modifiers of the relic effects ({@code effects}). Chances, durations, cooldowns, the radius
     * of Wailing Scream and the length of its Enfeeble are in the spell files. Read once at startup, changes need a restart.
     * Keep server and client equal (the tooltip reads the client file).
     */
    public static class Config {
        /** Written into the file, explains the sections (JSON has no comments). */
        public String _comment = COMMENT;
        /**
         * Version of the defaults this file was written with (0 = before phase 9B). Older files: rings and amulets that still have
         * exactly the old defaults get the new ones, changed entries stay ({@link ConfigDefaults}).
         */
        public int defaults_version = 0;
        public LinkedHashMap<String, EffectConfig> items = new LinkedHashMap<>();
        public LinkedHashMap<String, EffectConfig> effects = new LinkedHashMap<>();
    }

    private static final String COMMENT = "items: attributes of the rings and amulets, keyed by item id (operation ADD_MULTIPLIED_BASE,"
            + " 0.08 means +8%). effects: attributes of the relic effects Grave Ember (Grave Candle) and Blight Surge"
            + " (Blight Orb). Chances, durations and cooldowns of the relics are in their spell files"
            + " (data/blightcaller/spell/grave_ember.json, blight_surge.json, wailing_scream.json), change them with a data pack."
            + " Read once at startup, changes need a restart. Keep the file of server and client equal (tooltips read the client file)."
            + " defaults_version: set by the mod. When an update changes the defaults, entries that still have the old defaults get the"
            + " new ones once, entries you changed are kept.";

    /** Version of the defaults in this code: 1 = phase 8B, 2 = phase 9B (stronger rings and amulets of tier 4). */
    public static final int DEFAULTS_VERSION = 2;

    public static final ConfigManager<Config> CONFIG = new ConfigManager<>("trinkets", new Config())
            .builder()
            .setDirectory(Blightcaller.MODID)
            .sanitize(true)
            .build();

    private BlightcallerTrinkets() {}

    private static EffectConfig rotheartDefaults() {
        return new EffectConfig(List.of(
                AttributeModifier.multiply(SpellSchools.SOUL.id, UNIQUE_SOUL),
                AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), UNIQUE_CURSE_DURATION),
                AttributeModifier.multiply(SpellPowerMechanics.CRITICAL_CHANCE.id, ROTHEART_CRIT_CHANCE),
                AttributeModifier.multiply(SpellPowerMechanics.CRITICAL_DAMAGE.id, ROTHEART_CRIT_DAMAGE)));
    }

    private static EffectConfig witchboneDefaults() {
        return new EffectConfig(List.of(
                AttributeModifier.multiply(SpellSchools.SOUL.id, UNIQUE_SOUL),
                AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), UNIQUE_CURSE_DURATION),
                AttributeModifier.multiply(SpellPowerMechanics.HASTE.id, WITCHBONE_HASTE)));
    }

    /**
     * Mod constructor: reads trinkets.json, takes over changed defaults into an older file ({@link #DEFAULTS_VERSION}) and adds
     * missing item entries (jewelry only).
     */
    public static void init() {
        CONFIG.refresh();
        if (CONFIG.value.items == null) {
            CONFIG.value.items = new LinkedHashMap<>();
        }
        if (CONFIG.value.effects == null) {
            CONFIG.value.effects = new LinkedHashMap<>();
        }
        CONFIG.value._comment = COMMENT;
        if (CONFIG.value.defaults_version < DEFAULTS_VERSION) {
            for (Def def : ITEMS) {
                EffectConfig existing = def.kind() == Kind.JEWELRY ? CONFIG.value.items.get(def.id().toString()) : null;
                if (existing == null) {
                    continue;
                }
                List<AttributeModifier> old = legacyDefaults(def);
                if (existing.conditional_attributes == null && old != null && ConfigDefaults.same(existing.attributes, old)) {
                    CONFIG.value.items.put(def.id().toString(), def.defaults());
                    Blightcaller.LOGGER.info("Blightcaller: trinkets.json: {} had the old default values, took over the new defaults",
                            def.id());
                } else if (!ConfigDefaults.same(existing.attributes, def.defaults().attributes)) {
                    Blightcaller.LOGGER.info("Blightcaller: trinkets.json: kept your values for {} (the defaults changed in this version)",
                            def.id());
                }
            }
            CONFIG.value.defaults_version = DEFAULTS_VERSION;
        }
        for (Def def : ITEMS) {
            if (def.kind() == Kind.JEWELRY) {
                CONFIG.value.items.putIfAbsent(def.id().toString(), def.defaults());
            }
        }
        CONFIG.save();
    }

    /** Defaults of the jewelry before phase 9B (version 1, phase 8B), to recognise unchanged entries of an older file. */
    private static List<AttributeModifier> legacyDefaults(Def def) {
        String id = def.id().toString();
        boolean rotheart = id.equals(ROTHEART_RING.toString()) || id.equals(ROTHEART_NECKLACE.toString());
        boolean witchbone = id.equals(WITCHBONE_RING.toString()) || id.equals(WITCHBONE_NECKLACE.toString());
        if (!rotheart && !witchbone) {
            return null;
        }
        return List.of(
                AttributeModifier.multiply(SpellSchools.SOUL.id, 0.08F),
                AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), 0.03F),
                rotheart ? AttributeModifier.multiply(SpellPowerMechanics.HASTE.id, 0.03F)
                        : AttributeModifier.multiply(SpellPowerMechanics.CRITICAL_DAMAGE.id, 0.1F));
    }

    /** Attributes of a trinket from the config (empty if none). */
    public static List<AttributeModifier> attributes(Def def) {
        EffectConfig config = CONFIG.value.items.get(def.id().toString());
        return config != null ? config.selectedAttributes() : List.of();
    }

    /** Mod bus: the two relic effects, always (harmless without Curios), values from {@link #CONFIG}. */
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.MOB_EFFECT)) {
            return;
        }
        Effects.register(List.of(GRAVE_EMBER, BLIGHT_SURGE), CONFIG.value.effects);
        CONFIG.save();
    }

    /** Called while the items are registered: only with Curios. */
    public static void registerItems() {
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.registerItems();
        }
    }
}
