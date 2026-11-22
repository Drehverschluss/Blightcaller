package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.weapon.WeaponPassives;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client side of the tier 4 weapons (phase 8C): the patch of Grave Mould has no renderer of its own (its looks are the particles of
 * the spell), and the two unique weapons get their lore line right under the name (gold, italic, like the unique jewelry).
 */
public final class WeaponClient {
    /** Items with a lore line {@code item.blightcaller.<path>.lore}. */
    private static final Set<ResourceLocation> LORE = Set.of(Blightcaller.id("wand_gravemother"), Blightcaller.id("grimoire_gravemother"));

    private WeaponClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(WeaponPassives.GRAVE_MOULD, NoopRenderer::new)); // registered by then
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, WeaponClient::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!LORE.contains(id) || event.getToolTip().isEmpty()) {
            return;
        }
        event.getToolTip().add(1, Component.translatable("item." + id.getNamespace() + "." + id.getPath() + ".lore")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD));
    }
}
