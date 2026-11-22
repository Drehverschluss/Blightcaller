package com.drehverschluss.blightcaller.compat;

import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.item.BlightcallerItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.spell_engine.rpg_series.item.Weapon;
import org.jetbrains.annotations.Nullable;

/**
 * Blightcaller offers of the Wizard Merchant of Wizards ({@code wizards:wizard_merchant}), only when Wizards is installed
 * (without it the profession does not exist and this listener never matches). Made like the Frost offers of Wizards, with
 * the same price, uses, XP and price factor: Soul Rune on level 1, Blight Wand on level 2 (tier 2 like the Frost Wand),
 * an enchanted Blight Grimoire on level 5 (tier 2 and the same spell power as the Frost Staff). They are appended to the
 * pools like Elemental Wizards and Stormcaller do it; vanilla picks 2 random offers per level.
 * <p>
 * The event fires when the server data loads, before the server config is loaded. So the offers are always added and the
 * switch {@code [trades] wizard_merchant} is read when an offer is rolled: switched off, the offer returns {@code null},
 * vanilla then draws another one, and the merchant ends up exactly as without these offers.
 */
public final class WizardMerchantTrades {
    private static final ResourceLocation WIZARD_MERCHANT = ResourceLocation.fromNamespaceAndPath("wizards", "wizard_merchant");
    private static final ResourceLocation SOUL_RUNE = ResourceLocation.fromNamespaceAndPath("runes", "soul_stone");

    private WizardMerchantTrades() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!WIZARD_MERCHANT.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.getType()))) {
            return;
        }
        // Same numbers as the Frost offers of Wizards
        add(event, 1, () -> runeItem() == null ? null
                : new VillagerTrades.ItemsForEmeralds(runeItem(), 2, 8, 128, 3, 0.1F));
        add(event, 2, () -> item(BlightcallerItems.wandBlight) == null ? null
                : new VillagerTrades.ItemsForEmeralds(item(BlightcallerItems.wandBlight), 18, 1, 12, 18));
        add(event, 5, () -> item(BlightcallerItems.grimoireBlight) == null ? null
                : new VillagerTrades.EnchantedItemForEmeralds(item(BlightcallerItems.grimoireBlight), 40, 3, 30, 0.0F));
    }

    private static void add(VillagerTradesEvent event, int level, Supplier<VillagerTrades.ItemListing> listing) {
        List<VillagerTrades.ItemListing> pool = event.getTrades().get(level);
        if (pool != null) {
            pool.add(new Switched(listing));
        }
    }

    @Nullable
    private static Item runeItem() {
        Item item = BuiltInRegistries.ITEM.get(SOUL_RUNE);
        return item == Items.AIR ? null : item;
    }

    @Nullable
    private static Item item(@Nullable Weapon.Entry entry) {
        return entry == null ? null : entry.item();
    }

    /** Builds the real offer only while {@code [trades] wizard_merchant} is on; otherwise no offer (vanilla draws another). */
    private record Switched(Supplier<VillagerTrades.ItemListing> listing) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (!BlightcallerConfig.get(BlightcallerConfig.TRADES_WIZARD_MERCHANT)) {
                return null;
            }
            VillagerTrades.ItemListing real = listing.get();
            return real == null ? null : real.getOffer(trader, random);
        }
    }
}
