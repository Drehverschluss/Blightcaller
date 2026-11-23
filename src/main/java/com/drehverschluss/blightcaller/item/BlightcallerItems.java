package com.drehverschluss.blightcaller.item;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.ConfigDefaults;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.trinket.BlightcallerTrinkets;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.item.UniversalSpellBookItem;
import net.spell_engine.rpg_series.config.ArmorSetConfig;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.ConfigFile;
import net.spell_engine.rpg_series.item.Armor;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.rpg_series.item.Weapon;
import net.spell_engine.rpg_series.item.Weapons;
import net.spell_power.api.SpellSchools;
import net.tiny_config.ConfigManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Robes, wands and grimoires of tier 1 to 3 and the tier 4 robes, registered through the RPG Series helpers of Spell
 * Engine (like Berserker, without Wizards).
 * <p>
 * Both weapons are one-handed magic weapons of the type {@code DAMAGE_WAND} (attack damage 2, attack speed -2.4, Better
 * Combat preset {@code wand}). Their Soul Spell Power is raised to the values of the staves of other classes
 * (+5 / +6 / +7), because the Blightcaller has no two-handed staff. The wand holds Hex Bolt, the grimoire Blight Burst
 * (both spells follow in phase 4).
 * <p>
 * The robes carry Soul Spell Power and Curse Duration as standard modifiers of the item (not through an event), so
 * enchantments that require a matching attribute (Soulfrost) apply to them.
 * <p>
 * Tier 4 (like Armory and Stormcaller): two robe sets with set bonuses, Blightspore and Hexbinder
 * ({@code data/blightcaller/equipment_set/*.json}, linked through the data component {@code spell_engine:equipment_set}),
 * made at the smithing table from a tier 2 or 3 robe piece with the Gravemother Upgrade template and one of the two
 * Gravemother crystals. Template and crystals drop as loot ({@code rpg_series:loot_tier/tier_5_armors}).
 * <p>
 * Tier 4 weapons (phase 8C, like Wizards and Arsenal): the Dragonbone Blight Wand and Grimoire (+8, only with Better End or the
 * switch {@code ignore_items_required_mods} in {@code config/blightcaller/tweaks.json}, crafted from Better End items) and two
 * unique weapons of the Gravemother that only drop as loot (tier 5, rare, +8): Gravemother's Fingerbone (Hex Bolt and the
 * passive Bone Spike) and Gravemother's Herbarium (Blight Burst and the passive Grave Mould).
 */
public final class BlightcallerItems {
    public static final ResourceLocation TAB_ID = Blightcaller.id("general");
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TAB_ID);

    /** {@code config/blightcaller/tweaks.json}, read once at startup (before the items are registered). */
    public static final ConfigManager<Tweaks> TWEAKS_CONFIG = new ConfigManager<>("tweaks", new Tweaks())
            .builder()
            .setDirectory(Blightcaller.MODID)
            .sanitize(true)
            .build();

    private static final String TWEAKS_COMMENT = "ignore_items_required_mods: true registers items that need another mod even without that"
            + " mod (Dragonbone Blight Wand and Dragonbone Blight Grimoire: Better End). Default false. Server and client"
            + " must use the same value. Needs a restart. equipment_defaults_version: set by the mod. When an update changes the"
            + " defaults of equipment.json, entries that still have the old defaults get the new ones once, entries you changed are kept.";

    /** Version of the equipment.json defaults in this code: 1 = phase 9B (unique weapons +10 instead of +8 Soul Spell Power). */
    public static final int EQUIPMENT_DEFAULTS_VERSION = 1;

    /** Startup switches, like {@code config/wizards/tweaks.json}. */
    public static class Tweaks {
        /** Written into the file, explains the option (JSON has no comments). */
        public String _comment = TWEAKS_COMMENT;
        /** Registers items that need another mod (the dragonbone weapons: Better End) even without that mod. */
        public boolean ignore_items_required_mods = false;
        /**
         * Version of the defaults equipment.json was last brought up to (0 = before phase 9B). equipment.json has Spell Engine's fixed
         * format without room for a version, so it is kept here.
         */
        public int equipment_defaults_version = 0;
    }

    /** {@code config/blightcaller/equipment.json}, same format as the equipment configs of Wizards and Berserker. */
    public static final ConfigManager<ConfigFile.Equipment> EQUIPMENT_CONFIG = new ConfigManager<>("equipment", new ConfigFile.Equipment())
            .builder()
            .setDirectory(Blightcaller.MODID)
            .sanitize(true)
            .build();

    // Spell IDs of the weapon containers. The spells themselves follow in phase 4.
    public static final ResourceLocation HEX_BOLT = Blightcaller.id("hex_bolt");
    public static final ResourceLocation BLIGHT_BURST = Blightcaller.id("blight_burst");
    // Passives of the two unique weapons (phase 8C, spell.BoneSpike / spell.GraveMould)
    public static final ResourceLocation BONE_SPIKE = Blightcaller.id("bone_spike");
    public static final ResourceLocation GRAVE_MOULD = Blightcaller.id("grave_mould");
    /** Mod id of Better End: the dragonbone weapons need its items (recipe, repair). */
    public static final String BETTER_END = "betterend";

    // Spell book and scroll (spell tags, the items come from Spell Engine)
    public static final TagKey<Spell> SPELL_BOOK_TAG = TagKey.create(SpellRegistry.KEY, Blightcaller.id("spell_book/blightcaller"));
    public static final TagKey<Spell> SPELL_SCROLL_TAG = TagKey.create(SpellRegistry.KEY, Blightcaller.id("spell_scroll/blightcaller"));

    // Tier 4 sets (equipment_set ids) and smithing items
    public static final ResourceLocation BLIGHTSPORE_SET = Blightcaller.id("blightspore");
    public static final ResourceLocation HEXBINDER_SET = Blightcaller.id("hexbinder");
    public static final ResourceLocation TEMPLATE_ID = Blightcaller.id("gravemother_upgrade_smithing_template");
    public static final ResourceLocation SPORE_CRYSTAL_ID = Blightcaller.id("gravemothers_spore_crystal");
    public static final ResourceLocation BONE_CRYSTAL_ID = Blightcaller.id("gravemothers_bone_crystal");

    // Values per robe piece (defaults of equipment.json)
    private static final float SOUL_T1 = 0.2F;
    private static final float SOUL_T2 = 0.25F;
    private static final float SOUL_T3 = 0.3F;
    private static final float CURSE_DURATION_T1 = 0.025F;
    private static final float CURSE_DURATION_T2 = 0.05F;
    private static final float CURSE_DURATION_T3 = 0.075F;
    private static final float SOUL_T4 = 0.35F;            // like the Armory and Stormcaller robes
    private static final float CURSE_DURATION_T4 = 0.10F;  // series 2.5 / 5 / 7.5 continued

    // Soul Spell Power of both weapons (defaults of equipment.json): like the staves of other classes
    private static final float WEAPON_SOUL_T1 = 5.0F;
    private static final float WEAPON_SOUL_T2 = 6.0F;
    private static final float WEAPON_SOUL_T3 = 7.0F;
    // tier 4 (dragonbone): +8 like the tier 4 staves of Wizards and the unique staves of Arsenal
    private static final float WEAPON_SOUL_T4 = 8.0F;
    // the uniques (tier 5): +10 like the Stormbringer Staff (decided 22.11. after phase 9A, was +8 in phase 8C)
    private static final float WEAPON_SOUL_UNIQUE = 10.0F;
    private static final float WEAPON_SOUL_UNIQUE_OLD = 8.0F;
    private static final List<String> UNIQUE_WEAPONS = List.of("wand_gravemother", "grimoire_gravemother");

    // Armor values like the Wizard Robe (tier 1), Frost Robe (tier 2) and Netherite Frost Robe (tier 3) of Wizards
    private static final int ARMOR_HEAD = 1;
    private static final int ARMOR_CHEST = 3;
    private static final int ARMOR_LEGS = 2;
    private static final int ARMOR_FEET = 1;
    private static final int DURABILITY_T1 = 10;
    private static final int DURABILITY_T2 = 20;
    private static final int DURABILITY_T3 = 30;
    private static final int ENCHANTABILITY_T1 = 9;
    private static final int ENCHANTABILITY_T2 = 10;
    private static final int ENCHANTABILITY_T3 = 15;
    private static final int DURABILITY_T4 = 40;           // like the Armory and Stormcaller robes
    private static final int ENCHANTABILITY_T4 = 18;

    public static Armor.Set<?> blightcallerRobe;
    public static Armor.Set<?> blightRobe;
    public static Armor.Set<?> netheriteBlightRobe;
    public static Armor.Set<?> blightsporeRobe;
    public static Armor.Set<?> hexbinderRobe;
    public static Item gravemotherTemplate;
    public static Item sporeCrystal;
    public static Item boneCrystal;
    public static Weapon.Entry wandBlightcaller;
    public static Weapon.Entry wandBlight;
    public static Weapon.Entry wandNetheriteBlight;
    public static Weapon.Entry grimoireBlightcaller;
    public static Weapon.Entry grimoireBlight;
    public static Weapon.Entry grimoireNetheriteBlight;
    /** Null without Better End (unless {@link Tweaks#ignore_items_required_mods}). */
    public static Weapon.Entry wandDragonboneBlight;
    /** Null without Better End (unless {@link Tweaks#ignore_items_required_mods}). */
    public static Weapon.Entry grimoireDragonboneBlight;
    public static Weapon.Entry wandGravemother;
    public static Weapon.Entry grimoireGravemother;

    private BlightcallerItems() {}

    /** Loads tweaks.json and equipment.json. Called from the mod constructor, before registration. */
    public static void init() {
        TWEAKS_CONFIG.refresh();
        TWEAKS_CONFIG.value._comment = TWEAKS_COMMENT;
        EQUIPMENT_CONFIG.refresh();
        if (TWEAKS_CONFIG.value.equipment_defaults_version < EQUIPMENT_DEFAULTS_VERSION) {
            migrateEquipment(EQUIPMENT_CONFIG.value);
            TWEAKS_CONFIG.value.equipment_defaults_version = EQUIPMENT_DEFAULTS_VERSION;
            EQUIPMENT_CONFIG.save();
        }
        TWEAKS_CONFIG.save();
    }

    /**
     * Takes over the defaults of phase 9B into an older equipment.json: the unique weapons get +10 instead of +8 Soul Spell Power
     * when they still have exactly the old +8 (changed values stay). A new file gets the new defaults on registration anyway.
     */
    private static void migrateEquipment(ConfigFile.Equipment config) {
        if (config == null || config.weapons == null) {
            return;
        }
        String soul = SpellSchools.SOUL.id.toString();
        for (String name : UNIQUE_WEAPONS) {
            var weapon = config.weapons.get(name);
            if (weapon == null || weapon.attributes == null || weapon.conditional_attributes != null) {
                continue;
            }
            List<AttributeModifier> old = List.of(AttributeModifier.bonus(SpellSchools.SOUL.id, WEAPON_SOUL_UNIQUE_OLD));
            if (ConfigDefaults.same(weapon.attributes, old)) {
                for (AttributeModifier attribute : weapon.attributes) {
                    if (soul.equals(attribute.attribute)) {
                        attribute.value = WEAPON_SOUL_UNIQUE;
                    }
                }
                Blightcaller.LOGGER.info("Blightcaller: equipment.json: {} had the old default +{} Soul Spell Power, took over +{}", name,
                        WEAPON_SOUL_UNIQUE_OLD, WEAPON_SOUL_UNIQUE);
            } else {
                Blightcaller.LOGGER.info("Blightcaller: equipment.json: kept your values for {} (the default changed to +{} Soul Spell"
                        + " Power in this version)", name, WEAPON_SOUL_UNIQUE);
            }
        }
    }

    /** True if the dragonbone weapons are registered: Better End is loaded or the switch in tweaks.json is on. */
    public static boolean dragonboneWeapons() {
        return ModList.get().isLoaded(BETTER_END) || TWEAKS_CONFIG.value.ignore_items_required_mods;
    }

    public static void onRegister(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(TAB_ID, CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.blightcaller.general"))
                .icon(() -> new ItemStack(grimoireBlight != null && grimoireBlight.item() != null ? grimoireBlight.item() : Items.BOOK))
                .build()));
        event.register(Registries.ITEM, helper -> registerItems());
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Armor.Set is generic in Spell Engine
    private static void registerItems() {
        ConfigFile.Equipment config = EQUIPMENT_CONFIG.value;

        Supplier<Ingredient> amethyst = () -> Ingredient.of(Items.AMETHYST_SHARD);
        Supplier<Ingredient> soulSand = () -> Ingredient.of(Items.SOUL_SAND, Items.SOUL_SOIL);
        Supplier<Ingredient> netherite = () -> Ingredient.of(Items.NETHERITE_INGOT);

        // Tier 4 (phase 8C) like the tier 4 staves of Wizards: only with Better End, repaired with aeternium (netherite without
        // Better End), normal rarity, fire resistant (loot tier 4), crafted from Better End items (recipe condition), no passive.
        boolean betterEnd = ModList.get().isLoaded(BETTER_END);
        Supplier<Ingredient> aeternium = betterEnd
                ? () -> Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BETTER_END, "aeternium_ingot")))
                : netherite;
        boolean dragonbone = dragonboneWeapons();

        // Weapons first: the creative tab lists them in this order (wands of tier 1 to 4 and the unique wand, then the
        // grimoires the same way), then the robes, then book and scroll
        List<Weapon.Entry> weapons = new ArrayList<>();
        wandBlightcaller = wand("wand_blightcaller", Equipment.Tier.TIER_1, amethyst, WEAPON_SOUL_T1, HEX_BOLT);
        wandBlight = wand("wand_blight", Equipment.Tier.TIER_2, soulSand, WEAPON_SOUL_T2, HEX_BOLT);
        wandNetheriteBlight = wand("wand_netherite_blight", Equipment.Tier.TIER_3, netherite, WEAPON_SOUL_T3, HEX_BOLT);
        weapons.addAll(List.of(wandBlightcaller, wandBlight, wandNetheriteBlight));
        if (dragonbone) {
            wandDragonboneBlight = wand("wand_dragonbone_blight", Equipment.Tier.TIER_4, aeternium, WEAPON_SOUL_T4, HEX_BOLT);
            weapons.add(wandDragonboneBlight);
        }
        wandGravemother = unique("wand_gravemother", netherite, HEX_BOLT, BONE_SPIKE);
        weapons.add(wandGravemother);
        grimoireBlightcaller = wand("grimoire_blightcaller", Equipment.Tier.TIER_1, amethyst, WEAPON_SOUL_T1, BLIGHT_BURST);
        grimoireBlight = wand("grimoire_blight", Equipment.Tier.TIER_2, soulSand, WEAPON_SOUL_T2, BLIGHT_BURST);
        grimoireNetheriteBlight = wand("grimoire_netherite_blight", Equipment.Tier.TIER_3, netherite, WEAPON_SOUL_T3, BLIGHT_BURST);
        weapons.addAll(List.of(grimoireBlightcaller, grimoireBlight, grimoireNetheriteBlight));
        if (dragonbone) {
            grimoireDragonboneBlight = wand("grimoire_dragonbone_blight", Equipment.Tier.TIER_4, aeternium, WEAPON_SOUL_T4, BLIGHT_BURST);
            weapons.add(grimoireDragonboneBlight);
        }
        grimoireGravemother = unique("grimoire_gravemother", netherite, BLIGHT_BURST, GRAVE_MOULD);
        weapons.add(grimoireGravemother);
        Weapon.register(config.weapons, weapons, TAB_KEY);

        Armor.Entry robeT1 = robe("blightcaller_robe", 1, DURABILITY_T1, ENCHANTABILITY_T1, amethyst, SOUL_T1, CURSE_DURATION_T1);
        Armor.Entry robeT2 = robe("blight_robe", 2, DURABILITY_T2, ENCHANTABILITY_T2, soulSand, SOUL_T2, CURSE_DURATION_T2);
        Armor.Entry robeT3 = robe("netherite_blight_robe", 3, DURABILITY_T3, ENCHANTABILITY_T3, netherite, SOUL_T3, CURSE_DURATION_T3);
        // Tier 4 like the Armory and Stormcaller robes: rare, loot tier 5 (only makes them fire resistant; the robes are in
        // no loot tag, only template and crystals are), set through the equipment_set component
        Armor.Entry robeBlightspore = robe("blightspore_robe", 5, DURABILITY_T4, ENCHANTABILITY_T4, netherite, SOUL_T4,
                CURSE_DURATION_T4, BLIGHTSPORE_SET);
        Armor.Entry robeHexbinder = robe("hexbinder_robe", 5, DURABILITY_T4, ENCHANTABILITY_T4, netherite, SOUL_T4,
                CURSE_DURATION_T4, HEXBINDER_SET);
        Armor.register(config.armor_sets, new ArrayList<>(List.of(robeT1, robeT2, robeT3, robeBlightspore, robeHexbinder)), TAB_KEY);
        blightcallerRobe = robeT1.armorSet();
        blightRobe = robeT2.armorSet();
        netheriteBlightRobe = robeT3.armorSet();
        blightsporeRobe = robeBlightspore.armorSet();
        hexbinderRobe = robeHexbinder.armorSet();

        // creative tab: robes, then template and crystals, then (with Curios) jewelry and relics, then book and scrolls
        registerSmithingItems();
        // rings, amulets and relics, only with Curios (phase 8B)
        BlightcallerTrinkets.registerItems();
        PlatformEvents.onItemGroupModify(TAB_KEY, BlightcallerItems::addBookAndScrolls);

        EQUIPMENT_CONFIG.save();
    }

    /**
     * Wand-type weapon (one-handed, Better Combat preset {@code wand}) with the given Soul Spell Power. Only the default
     * is changed: an equipment.json that already has the weapon keeps its value (Spell Engine reads the file first).
     */
    private static Weapon.Entry wand(String name, Equipment.Tier tier, Supplier<Ingredient> repair, float soulPower, ResourceLocation spell) {
        Weapon.Entry entry = Weapons.damageWand(Blightcaller.MODID, name, tier, repair, List.of(SpellSchools.SOUL.id))
                .spellContainer(SpellContainers.forMagicWeapon().withSpellId(spell));
        String soul = SpellSchools.SOUL.id.toString();
        for (AttributeModifier attribute : entry.defaults().attributes) {
            if (soul.equals(attribute.attribute)) {
                attribute.value = soulPower;
            }
        }
        return entry;
    }

    /**
     * Unique weapon of the Gravemother (phase 8C), like the Stormbringer Staff and the unique staves of Arsenal: tier 5 (loot
     * tier 5 via tag, fire resistant), rare, no recipe. Its passive is part of the item's spell container (Spell Engine shows it
     * in the tooltip and only fires it with the item in the main hand, {@code equipment_condition MAINHAND}).
     */
    private static Weapon.Entry unique(String name, Supplier<Ingredient> repair, ResourceLocation spell, ResourceLocation passive) {
        Weapon.Entry entry = wand(name, Equipment.Tier.TIER_5, repair, WEAPON_SOUL_UNIQUE, spell).withAdditionalSpell(passive.toString());
        entry.rarity = Rarity.RARE;
        return entry;
    }

    private static Armor.Entry robe(String name, int tier, int durability, int enchantability, Supplier<Ingredient> repair,
                                    float soul, float curseDuration) {
        return robe(name, tier, durability, enchantability, repair, soul, curseDuration, null);
    }

    /** @param equipmentSet tier 4: the set of the pieces (component {@code spell_engine:equipment_set}, rarity RARE), else null */
    @SuppressWarnings("unchecked") // Armor.Set.ItemFactory is generic in Spell Engine
    private static Armor.Entry robe(String name, int tier, int durability, int enchantability, Supplier<Ingredient> repair,
                                    float soul, float curseDuration, ResourceLocation equipmentSet) {
        Holder<ArmorMaterial> material = material(name, enchantability, repair);
        ArmorSetConfig defaults = ArmorSetConfig.with(
                piece(ARMOR_HEAD, soul, curseDuration),
                piece(ARMOR_CHEST, soul, curseDuration),
                piece(ARMOR_LEGS, soul, curseDuration),
                piece(ARMOR_FEET, soul, curseDuration));
        if (equipmentSet == null) {
            return Armor.Entry.create(material, Blightcaller.id(name), durability, Armor.CustomItem::new, defaults,
                    Equipment.LootProperties.of(tier));
        }
        return Armor.Entry.create(material, Blightcaller.id(name), durability, Armor.CustomItem::new, defaults,
                Equipment.LootProperties.of(tier), Armor.ItemSettingsTweaker.standard(settings -> settings
                        .component(SpellDataComponents.EQUIPMENT_SET, equipmentSet)
                        .component(DataComponents.RARITY, Rarity.RARE)));
    }

    /**
     * Gravemother Upgrade smithing template and the two Gravemother crystals for the tier 4 robes (like Armory and
     * Stormcaller: crystals epic and fire resistant, the template a Vanilla {@link SmithingTemplateItem}).
     */
    private static void registerSmithingItems() {
        gravemotherTemplate = Registry.register(BuiltInRegistries.ITEM, TEMPLATE_ID, new SmithingTemplateItem(
                templateText("applies_to", ChatFormatting.BLUE),
                templateText("ingredients", ChatFormatting.BLUE),
                Component.translatable(Util.makeDescriptionId("upgrade", Blightcaller.id("gravemother_upgrade"))).withStyle(ChatFormatting.GRAY),
                templateText("base_slot_description", null),
                templateText("additions_slot_description", null),
                List.of(
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots")),
                List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_amethyst_shard"))));
        sporeCrystal = Registry.register(BuiltInRegistries.ITEM, SPORE_CRYSTAL_ID,
                new UpgradeCrystalItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "spore"));
        boneCrystal = Registry.register(BuiltInRegistries.ITEM, BONE_CRYSTAL_ID,
                new UpgradeCrystalItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "bone"));
        PlatformEvents.onItemGroupModify(TAB_KEY, (content, context) -> {
            content.accept(gravemotherTemplate);
            content.accept(sporeCrystal);
            content.accept(boneCrystal);
        });
    }

    /** {@code item.blightcaller.smithing_template.gravemother_upgrade.<part>}, like the Vanilla netherite template. */
    private static Component templateText(String part, ChatFormatting style) {
        Component text = Component.translatable(Util.makeDescriptionId("item", Blightcaller.id("smithing_template.gravemother_upgrade." + part)));
        return style == null ? text : text.copy().withStyle(style);
    }

    private static ArmorSetConfig.Piece piece(int armor, float soul, float curseDuration) {
        return new ArmorSetConfig.Piece(armor).addAll(List.of(
                AttributeModifier.multiply(SpellSchools.SOUL.id, soul),
                AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), curseDuration)));
    }

    /** Armor material like the robes of Wizards (no toughness, no knockback resistance). The look comes from the Armor
     * Model API (client/BlightcallerArmorRenderers), the material layer has no texture of its own. */
    private static Holder<ArmorMaterial> material(String name, int enchantability, Supplier<Ingredient> repair) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, ARMOR_HEAD);
        defense.put(ArmorItem.Type.CHESTPLATE, ARMOR_CHEST);
        defense.put(ArmorItem.Type.LEGGINGS, ARMOR_LEGS);
        defense.put(ArmorItem.Type.BOOTS, ARMOR_FEET);
        ArmorMaterial material = new ArmorMaterial(defense, enchantability, equipSound(name), repair,
                List.of(new ArmorMaterial.Layer(Blightcaller.id(name))), 0.0F, 0.0F);
        return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, Blightcaller.id(name), material);
    }

    private static final Map<String, Holder<SoundEvent>> EQUIP_SOUNDS = new java.util.HashMap<>();

    /**
     * Equip sound of a robe (A4 no. 15, phase 12B): cloth instead of leather, Blightspore with something soft and mossy, Hexbinder
     * with a quiet rattle of bones. Only entries in {@code assets/blightcaller/sounds.json} that point to Vanilla sound files, so a
     * resource pack can change them. Registered here, before the armor materials that use them (both sides, like the materials).
     */
    private static Holder<SoundEvent> equipSound(String robe) {
        String key = robe.startsWith("blightspore") ? "item.armor.equip_blightspore"
                : robe.startsWith("hexbinder") ? "item.armor.equip_hexbinder" : "item.armor.equip_robe";
        return EQUIP_SOUNDS.computeIfAbsent(key, k -> Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, Blightcaller.id(k),
                SoundEvent.createVariableRangeEvent(Blightcaller.id(k))));
    }

    /**
     * Tome of Blight and the scrolls of its spells, after the robes. Spell Engine builds both from the spell tags
     * (one book per {@code spell_book/} tag, one scroll per spell in a {@code spell_scroll/} tag); they also appear in the
     * Spell Engine tab.
     */
    private static void addBookAndScrolls(CreativeModeTab.Output content, CreativeModeTab.ItemDisplayParameters context) {
        Optional<HolderLookup.RegistryLookup<Spell>> spells = context.holders().lookup(SpellRegistry.KEY);
        if (spells.isEmpty()) {
            return;
        }
        if (spells.get().get(SPELL_BOOK_TAG).isPresent()) {
            ItemStack book = new ItemStack(SpellEngineItems.SPELL_BOOK.get());
            if (UniversalSpellBookItem.applyFromTag(book, SPELL_BOOK_TAG)) {
                content.accept(book);
            }
        }
        Optional<HolderSet.Named<Spell>> scrolls = spells.get().get(SPELL_SCROLL_TAG);
        if (scrolls.isPresent()) {
            for (Holder<Spell> spell : scrolls.get()) {
                ItemStack scroll = new ItemStack(SpellEngineItems.SCROLL.get());
                ScrollItem.applySpell(scroll, spell, SPELL_SCROLL_TAG);
                content.accept(scroll);
            }
        }
    }
}
