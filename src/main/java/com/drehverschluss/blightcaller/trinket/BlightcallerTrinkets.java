package com.drehverschluss.blightcaller.trinket;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.compat.curios.CuriosCompat;
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
 *   <li>Rotheart Signet / Amulet (spell haste) and Witchbone Band / Pendant (spell critical damage): unique jewelry (slots
 *   {@code ring} / {@code necklace}), Soul Spell Power and Curse Duration on both pairs, attributes from {@link #CONFIG}.</li>
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

    // Defaults of trinkets.json (start values, measured in phase 9)
    private static final float UNIQUE_SOUL = 0.08F;            // like the school uniques of Jewelry
    private static final float UNIQUE_CURSE_DURATION = 0.03F;  // on both pairs, like the shock chance on both Stormcaller pairs
    private static final float UNIQUE_HASTE = 0.03F;           // Rotheart: more casts, more curses
    private static final float UNIQUE_CRIT_DAMAGE = 0.1F;      // Witchbone: burst, like the Fire/Frost uniques of Jewelry
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
        public String _comment = "items: attributes of the rings and amulets, keyed by item id (operation ADD_MULTIPLIED_BASE,"
                + " 0.08 means +8%). effects: attributes of the relic effects Grave Ember (Grave Candle) and Blight Surge"
                + " (Blight Orb). Chances, durations and cooldowns of the relics are in their spell files"
                + " (data/blightcaller/spell/grave_ember.json, blight_surge.json, wailing_scream.json), change them with a data pack."
                + " Read once at startup, changes need a restart. Keep the file of server and client equal (tooltips read the client file).";
        public LinkedHashMap<String, EffectConfig> items = new LinkedHashMap<>();
        public LinkedHashMap<String, EffectConfig> effects = new LinkedHashMap<>();
    }

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
                AttributeModifier.multiply(SpellPowerMechanics.HASTE.id, UNIQUE_HASTE)));
    }

    private static EffectConfig witchboneDefaults() {
        return new EffectConfig(List.of(
                AttributeModifier.multiply(SpellSchools.SOUL.id, UNIQUE_SOUL),
                AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), UNIQUE_CURSE_DURATION),
                AttributeModifier.multiply(SpellPowerMechanics.CRITICAL_DAMAGE.id, UNIQUE_CRIT_DAMAGE)));
    }

    /** Mod constructor: reads trinkets.json and adds missing item entries (jewelry only). */
    public static void init() {
        CONFIG.refresh();
        if (CONFIG.value.items == null) {
            CONFIG.value.items = new LinkedHashMap<>();
        }
        if (CONFIG.value.effects == null) {
            CONFIG.value.effects = new LinkedHashMap<>();
        }
        for (Def def : ITEMS) {
            if (def.kind() == Kind.JEWELRY) {
                CONFIG.value.items.putIfAbsent(def.id().toString(), def.defaults());
            }
        }
        CONFIG.save();
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
