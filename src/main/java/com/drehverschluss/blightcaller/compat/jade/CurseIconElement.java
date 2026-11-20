package com.drehverschluss.blightcaller.compat.jade;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;

/**
 * Jade element: the icon of a curse (from the mob effect atlas, which Jade's own sprite element cannot draw) followed by a
 * text. Own curses in full colour, curses of other players dimmed (icon half transparent, text grey).
 */
public class CurseIconElement extends Element {
    static final int ICON = 9;
    private static final int GAP = 2;

    private final Holder<MobEffect> curse;
    private final Component text;
    private final boolean own;
    private final int trailing;

    /**
     * @param trailing empty space after the text (between the curses of the compact line)
     */
    public CurseIconElement(Holder<MobEffect> curse, Component text, boolean own, int trailing) {
        this.curse = curse;
        this.text = text;
        this.own = own;
        this.trailing = trailing;
    }

    @Override
    public Vec2 getSize() {
        Font font = Minecraft.getInstance().font;
        return new Vec2(ICON + GAP + font.width(text) + trailing, Math.max(ICON, font.lineHeight));
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        Minecraft mc = Minecraft.getInstance();
        float opacity = IDisplayHelper.get().opacity();
        TextureAtlasSprite sprite = mc.getMobEffectTextures().get(curse);
        RenderSystem.enableBlend();
        graphics.blit((int) x, (int) y, 0, ICON, ICON, sprite, 1.0F, 1.0F, 1.0F, (own ? 1.0F : 0.35F) * opacity);
        RenderSystem.disableBlend();
        IThemeHelper theme = IThemeHelper.get();
        // own curses bright (the theme's text colour on light themes), curses of other players grey
        int color = !own ? CurseJadeProvider.FOREIGN_COLOR : theme.isLightColorScheme() ? theme.getNormalColor() : CurseJadeProvider.OWN_COLOR;
        IDisplayHelper.get().drawText(graphics, text, x + ICON + GAP, y + 1, color);
    }

    @Override
    public String getMessage() {
        return curse.value().getDisplayName().getString() + " " + text.getString();
    }
}
