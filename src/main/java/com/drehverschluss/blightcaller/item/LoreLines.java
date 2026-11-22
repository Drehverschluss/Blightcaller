package com.drehverschluss.blightcaller.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * Lore lines of the unique weapons and the unique jewelry (gold, italic): the key {@code <item key>.lore} and, only if the language
 * has it, a second line {@code <item key>.lore2} (phase 11), so a long lore does not stretch the tooltip.
 */
public final class LoreLines {
    private LoreLines() {}

    /** The lore lines of an item: {@code itemKey + ".lore"} and {@code itemKey + ".lore2"} if that key exists. */
    public static List<Component> of(String itemKey) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(line(itemKey + ".lore"));
        String second = itemKey + ".lore2";
        if (Language.getInstance().has(second)) {
            lines.add(line(second));
        }
        return lines;
    }

    private static Component line(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD);
    }
}
