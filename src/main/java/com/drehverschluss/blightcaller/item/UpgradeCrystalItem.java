package com.drehverschluss.blightcaller.item;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Gravemother crystal: the addition of the tier 4 smithing recipes of the robes (spore crystal for Blightspore, bone
 * crystal for Hexbinder). Shows a hint and an "Applies to:" line like a smithing template.
 * <p>
 * Lang keys: {@code item.blightcaller.upgrade_crystal.hint} and {@code item.blightcaller.upgrade_crystal.<name>.applies_to}.
 */
public class UpgradeCrystalItem extends Item {
    private static final Component APPLIES_TO = Component.translatable(
            Util.makeDescriptionId("item", ResourceLocation.withDefaultNamespace("smithing_template.applies_to")))
            .withStyle(ChatFormatting.GRAY);
    private static final Component HINT = Component.translatable(
            Util.makeDescriptionId("item", Blightcaller.id("upgrade_crystal.hint")))
            .withStyle(ChatFormatting.GRAY);

    private final Component appliesTo;

    /** @param name short name of the crystal ({@code spore} or {@code bone}), used for the "Applies to" key */
    public UpgradeCrystalItem(Properties properties, String name) {
        super(properties);
        this.appliesTo = Component.translatable(appliesToKey(name));
    }

    public static String appliesToKey(String name) {
        return Util.makeDescriptionId("item", Blightcaller.id("upgrade_crystal." + name + ".applies_to"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(HINT);
        tooltip.add(CommonComponents.EMPTY);
        tooltip.add(APPLIES_TO);
        tooltip.add(CommonComponents.space().append(appliesTo).withStyle(ChatFormatting.BLUE));
    }
}
