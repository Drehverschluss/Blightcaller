package com.drehverschluss.blightcaller.item;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.spell.Spell;
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
 * Robes, wands and grimoires of tier 1 to 3, registered through the RPG Series helpers of Spell Engine (like Berserker,
 * without Wizards).
 * <p>
 * Both weapons are one-handed magic weapons of the type {@code DAMAGE_WAND} (attack damage 2, attack speed -2.4, Better
 * Combat preset {@code wand}). Their Soul Spell Power is raised to the values of the staves of other classes
 * (+5 / +6 / +7), because the Blightcaller has no two-handed staff. The wand holds Hex Bolt, the grimoire Blight Burst
 * (both spells follow in phase 4).
 * <p>
 * The robes carry Soul Spell Power and Curse Duration as standard modifiers of the item (not through an event), so
 * enchantments that require a matching attribute (Soulfrost) apply to them.
 */
public final class BlightcallerItems {
    public static final ResourceLocation TAB_ID = Blightcaller.id("general");
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TAB_ID);

    /** {@code config/blightcaller/equipment.json}, same format as the equipment configs of Wizards and Berserker. */
    public static final ConfigManager<ConfigFile.Equipment> EQUIPMENT_CONFIG = new ConfigManager<>("equipment", new ConfigFile.Equipment())
            .builder()
            .setDirectory(Blightcaller.MODID)
            .sanitize(true)
            .build();

    // Spell IDs of the weapon containers. The spells themselves follow in phase 4.
    public static final ResourceLocation HEX_BOLT = Blightcaller.id("hex_bolt");
    public static final ResourceLocation BLIGHT_BURST = Blightcaller.id("blight_burst");

    // Spell book and scroll (spell tags, the items come from Spell Engine)
    public static final TagKey<Spell> SPELL_BOOK_TAG = TagKey.create(SpellRegistry.KEY, Blightcaller.id("spell_book/blightcaller"));
    public static final TagKey<Spell> SPELL_SCROLL_TAG = TagKey.create(SpellRegistry.KEY, Blightcaller.id("spell_scroll/blightcaller"));

    // Values per robe piece (defaults of equipment.json)
    private static final float SOUL_T1 = 0.2F;
    private static final float SOUL_T2 = 0.25F;
    private static final float SOUL_T3 = 0.3F;
    private static final float CURSE_DURATION_T1 = 0.025F;
    private static final float CURSE_DURATION_T2 = 0.05F;
    private static final float CURSE_DURATION_T3 = 0.075F;

    // Soul Spell Power of both weapons (defaults of equipment.json): like the staves of other classes
    private static final float WEAPON_SOUL_T1 = 5.0F;
    private static final float WEAPON_SOUL_T2 = 6.0F;
    private static final float WEAPON_SOUL_T3 = 7.0F;

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

    public static Armor.Set<?> blightcallerRobe;
    public static Armor.Set<?> blightRobe;
    public static Armor.Set<?> netheriteBlightRobe;
    public static Weapon.Entry wandBlightcaller;
    public static Weapon.Entry wandBlight;
    public static Weapon.Entry wandNetheriteBlight;
    public static Weapon.Entry grimoireBlightcaller;
    public static Weapon.Entry grimoireBlight;
    public static Weapon.Entry grimoireNetheriteBlight;

    private BlightcallerItems() {}

    /** Loads equipment.json. Called from the mod constructor, before registration. */
    public static void init() {
        EQUIPMENT_CONFIG.refresh();
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

        // Weapons first: the creative tab lists them in this order (wands, grimoires), then the robes, then book and scroll
        wandBlightcaller = wand("wand_blightcaller", Equipment.Tier.TIER_1, amethyst, WEAPON_SOUL_T1, HEX_BOLT);
        wandBlight = wand("wand_blight", Equipment.Tier.TIER_2, soulSand, WEAPON_SOUL_T2, HEX_BOLT);
        wandNetheriteBlight = wand("wand_netherite_blight", Equipment.Tier.TIER_3, netherite, WEAPON_SOUL_T3, HEX_BOLT);
        grimoireBlightcaller = wand("grimoire_blightcaller", Equipment.Tier.TIER_1, amethyst, WEAPON_SOUL_T1, BLIGHT_BURST);
        grimoireBlight = wand("grimoire_blight", Equipment.Tier.TIER_2, soulSand, WEAPON_SOUL_T2, BLIGHT_BURST);
        grimoireNetheriteBlight = wand("grimoire_netherite_blight", Equipment.Tier.TIER_3, netherite, WEAPON_SOUL_T3, BLIGHT_BURST);
        Weapon.register(config.weapons, List.of(wandBlightcaller, wandBlight, wandNetheriteBlight,
                grimoireBlightcaller, grimoireBlight, grimoireNetheriteBlight), TAB_KEY);

        Armor.Entry robeT1 = robe("blightcaller_robe", 1, DURABILITY_T1, ENCHANTABILITY_T1, amethyst, SOUL_T1, CURSE_DURATION_T1);
        Armor.Entry robeT2 = robe("blight_robe", 2, DURABILITY_T2, ENCHANTABILITY_T2, soulSand, SOUL_T2, CURSE_DURATION_T2);
        Armor.Entry robeT3 = robe("netherite_blight_robe", 3, DURABILITY_T3, ENCHANTABILITY_T3, netherite, SOUL_T3, CURSE_DURATION_T3);
        Armor.register(config.armor_sets, new ArrayList<>(List.of(robeT1, robeT2, robeT3)), TAB_KEY);
        blightcallerRobe = robeT1.armorSet();
        blightRobe = robeT2.armorSet();
        netheriteBlightRobe = robeT3.armorSet();

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

    @SuppressWarnings("unchecked") // Armor.Set.ItemFactory is generic in Spell Engine
    private static Armor.Entry robe(String name, int tier, int durability, int enchantability, Supplier<Ingredient> repair,
                                    float soul, float curseDuration) {
        Holder<ArmorMaterial> material = material(name, enchantability, repair);
        return Armor.Entry.create(material, Blightcaller.id(name), durability, Armor.CustomItem::new,
                ArmorSetConfig.with(
                        piece(ARMOR_HEAD, soul, curseDuration),
                        piece(ARMOR_CHEST, soul, curseDuration),
                        piece(ARMOR_LEGS, soul, curseDuration),
                        piece(ARMOR_FEET, soul, curseDuration)),
                Equipment.LootProperties.of(tier));
    }

    private static ArmorSetConfig.Piece piece(int armor, float soul, float curseDuration) {
        return new ArmorSetConfig.Piece(armor).addAll(List.of(
                AttributeModifier.multiply(SpellSchools.SOUL.id, soul),
                AttributeModifier.multiply(BlightcallerAttributes.CURSE_DURATION.getId(), curseDuration)));
    }

    /** Armor material like the robes of Wizards (no toughness, no knockback resistance), Vanilla layer textures. */
    private static Holder<ArmorMaterial> material(String name, int enchantability, Supplier<Ingredient> repair) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, ARMOR_HEAD);
        defense.put(ArmorItem.Type.CHESTPLATE, ARMOR_CHEST);
        defense.put(ArmorItem.Type.LEGGINGS, ARMOR_LEGS);
        defense.put(ArmorItem.Type.BOOTS, ARMOR_FEET);
        ArmorMaterial material = new ArmorMaterial(defense, enchantability, SoundEvents.ARMOR_EQUIP_LEATHER, repair,
                List.of(new ArmorMaterial.Layer(Blightcaller.id(name))), 0.0F, 0.0F);
        return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, Blightcaller.id(name), material);
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
