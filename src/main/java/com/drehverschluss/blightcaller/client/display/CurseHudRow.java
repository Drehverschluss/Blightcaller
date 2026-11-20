package com.drehverschluss.blightcaller.client.display;

import com.drehverschluss.blightcaller.client.fx.FxRenderTypes;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.drehverschluss.blightcaller.curse.CurseView;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Row of curses at the mob box of Health Bars (concept 8.1): while the box shows a cursed mob, a row with every curse's icon,
 * ring and remaining seconds sits at the box. The box can be anywhere on the screen, so the row follows its position and
 * size and goes to the side with room (client option {@code curse_display.hud_row}): AUTO puts it below a box at the top and
 * above a box in the middle or at the bottom; it lines up with the box's left edge, right edge or centre like the box itself.
 */
final class CurseHudRow {
    static final int ICON = 9;
    static final int CELL = 13;
    static final int TEXT_GAP = 2;
    static final int ITEM_GAP = 6;
    static final int PAD = 2;
    static final int MARGIN = 3;
    static final int BACKGROUND = 0x88000000;
    static final int OWN_TEXT = 0xFFFFFFFF;
    static final int FOREIGN_TEXT = 0xFF9A9A9A;

    /** Where the row was drawn last (probe, report): x, y, width, height, side (-1 above, 1 below), or null. */
    @Nullable
    static int[] last;

    private CurseHudRow() {}

    static void render(GuiGraphics graphics, float partialTick, @Nullable HealthBarsView healthBars) {
        last = null;
        BlightcallerClientConfig.HudRow mode = BlightcallerClientConfig.curseHudRow();
        Minecraft mc = Minecraft.getInstance();
        if (healthBars == null || mode == BlightcallerClientConfig.HudRow.OFF || mc.level == null || mc.player == null
                || mc.options.hideGui) {
            return;
        }
        HealthBarsView.Box box = healthBars.box(graphics.guiWidth(), graphics.guiHeight(), partialTick);
        if (box == null) {
            return;
        }
        CurseView view = CurseLines.view(box.entity());
        if (view == null) {
            return;
        }
        long tick = mc.level.getGameTime();
        float now = tick + partialTick;
        List<CurseLines.Shown> shown = CurseLines.shown(view, mc.player.getUUID(), tick);
        if (shown.isEmpty()) {
            return;
        }
        Font font = mc.font;
        String[] texts = new String[shown.size()];
        int width = 0;
        for (int i = 0; i < shown.size(); i++) {
            texts[i] = CursePainter.time(shown.get(i).end() - now);
            width += CELL + TEXT_GAP + font.width(texts[i]) + (i > 0 ? ITEM_GAP : 0);
        }
        int height = CELL;
        boolean below = switch (mode) {
            case ABOVE -> false;
            case BELOW -> true;
            default -> box.anchorY() < 0;   // a box at the top: below it; in the middle or at the bottom: above it
        };
        int y = below ? box.y1() + MARGIN + PAD : box.y0() - MARGIN - PAD - height;
        int x = box.anchorX() < 0 ? box.x0() + PAD : box.anchorX() > 0 ? box.x1() - PAD - width : (box.x0() + box.x1() - width) / 2;
        x = Mth.clamp(x, PAD, Math.max(PAD, graphics.guiWidth() - PAD - width));
        y = Mth.clamp(y, PAD, Math.max(PAD, graphics.guiHeight() - PAD - height));
        last = new int[]{x, y, width, height, below ? 1 : -1};

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 3100.0F);   // in front of Health Bars' box (3000)
        Matrix4f m = graphics.pose().last().pose();
        CursePainter.rect(graphics.bufferSource().getBuffer(FxRenderTypes.FLAT), m, x - PAD, y - PAD, x + width + PAD,
                y + height + PAD, BACKGROUND);
        graphics.bufferSource().endBatch(FxRenderTypes.FLAT);
        // rings for the whole row, then the icons, then the seconds
        com.mojang.blaze3d.vertex.VertexConsumer flat = graphics.bufferSource().getBuffer(FxRenderTypes.FLAT);
        int[] at = new int[shown.size()];
        int cx = x;
        for (int i = 0; i < shown.size(); i++) {
            at[i] = cx;
            CursePainter.rings(flat, m, shown.get(i), cx + CELL * 0.5F, y + CELL * 0.5F, ICON, now, 1.0F);
            cx += CELL + TEXT_GAP + font.width(texts[i]) + ITEM_GAP;
        }
        graphics.bufferSource().endBatch(FxRenderTypes.FLAT);
        com.mojang.blaze3d.vertex.VertexConsumer icons = graphics.bufferSource().getBuffer(FxRenderTypes.ICON);
        for (int i = 0; i < shown.size(); i++) {
            CursePainter.icon(icons, m, shown.get(i), at[i] + CELL * 0.5F, y + CELL * 0.5F, ICON, now, 1.0F);
        }
        graphics.bufferSource().endBatch(FxRenderTypes.ICON);
        for (int i = 0; i < shown.size(); i++) {
            CurseLines.Shown s = shown.get(i);
            graphics.drawString(font, texts[i], at[i] + CELL + TEXT_GAP, y + (CELL - font.lineHeight) / 2 + 1, s.own() ? OWN_TEXT : FOREIGN_TEXT, true);
        }
        graphics.flush();
        graphics.pose().popPose();
    }
}
