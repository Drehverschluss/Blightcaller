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
 * Row of curses at the mob box of Health Bars (concept 8.1): while the box shows a cursed mob, a row with every curse's icon
 * and remaining seconds sits close to the box, without a background box. The icons are tiles that turn grey from the top as
 * the curse runs out, with a thin bright frame, and the remaining seconds in their bottom right corner, like above the head
 * ({@link CursePainter#tile}, {@link CursePainter#drawSeconds}). The box can be anywhere on the screen, so the row follows its position and
 * size (client option {@code curse_display.hud_row}): NAME right above the mob's name and TEXT right under the health line,
 * both in line with the name (phase I, the user's sketch of 21.11.), ABOVE and BELOW outside the whole box; AUTO takes TEXT for a
 * box at the top of the screen and NAME otherwise.
 */
final class CurseHudRow {
    /** Edge of a tile and of its cell (frame included) in GUI pixels, gap between two cells. */
    static final int ICON = 12;
    static final int CELL = 15;
    static final int ITEM_GAP = 2;
    static final int PAD = 2;
    /** Distance between the row and Health Bars' box. */
    static final int MARGIN = 1;
    /** Distance between the row and the mob's name below it (the letters need a little more air than the box edge). */
    static final int NAME_GAP = 2;

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
        int width = shown.size() * CELL + (shown.size() - 1) * ITEM_GAP;
        int height = CELL;
        BlightcallerClientConfig.HudRow place = mode != BlightcallerClientConfig.HudRow.AUTO ? mode
                : box.anchorY() < 0 ? BlightcallerClientConfig.HudRow.TEXT : BlightcallerClientConfig.HudRow.NAME;
        if (place == BlightcallerClientConfig.HudRow.NAME && box.titleTop() - NAME_GAP - height < PAD) {
            place = BlightcallerClientConfig.HudRow.TEXT;   // no room above the name (box at the top edge): under the health line
        } else if (place == BlightcallerClientConfig.HudRow.TEXT && box.textBottom() + MARGIN + height > graphics.guiHeight() - PAD) {
            place = BlightcallerClientConfig.HudRow.NAME;   // no room under the health line (box at the bottom edge): above the name
        }
        boolean below = place == BlightcallerClientConfig.HudRow.TEXT || place == BlightcallerClientConfig.HudRow.BELOW;
        int x;
        int y;
        if (place == BlightcallerClientConfig.HudRow.NAME || place == BlightcallerClientConfig.HudRow.TEXT) {
            // in the box, in line with the name: above it, or under the health line; right-aligned for a box on the right side
            x = box.anchorX() > 0 ? box.textX1() - width : box.textX();
            y = place == BlightcallerClientConfig.HudRow.NAME ? box.titleTop() - NAME_GAP - height : box.textBottom() + MARGIN;
        } else {
            y = below ? box.y1() + MARGIN + PAD : box.y0() - MARGIN - PAD - height;
            x = box.anchorX() < 0 ? box.x0() + PAD : box.anchorX() > 0 ? box.x1() - PAD - width : (box.x0() + box.x1() - width) / 2;
        }
        x = Mth.clamp(x, PAD, Math.max(PAD, graphics.guiWidth() - PAD - width));
        y = Mth.clamp(y, PAD, Math.max(PAD, graphics.guiHeight() - PAD - height));
        last = new int[]{x, y, width, height, below ? 1 : -1};

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 3100.0F);   // in front of Health Bars' box (3000)
        Matrix4f m = graphics.pose().last().pose();
        // no background box: frames for the whole row, then the tiles, then the seconds
        com.mojang.blaze3d.vertex.VertexConsumer flat = graphics.bufferSource().getBuffer(FxRenderTypes.FLAT);
        int[] at = new int[shown.size()];
        int cx = x;
        for (int i = 0; i < shown.size(); i++) {
            at[i] = cx;
            CursePainter.frame(flat, m, shown.get(i), cx + CELL * 0.5F, y + CELL * 0.5F, ICON, now, 1.0F);
            cx += CELL + ITEM_GAP;
        }
        graphics.bufferSource().endBatch(FxRenderTypes.FLAT);
        com.mojang.blaze3d.vertex.VertexConsumer icons = graphics.bufferSource().getBuffer(FxRenderTypes.ICON);
        for (int i = 0; i < shown.size(); i++) {
            CursePainter.tile(icons, m, shown.get(i), at[i] + CELL * 0.5F, y + CELL * 0.5F, ICON, now, 1.0F);
        }
        graphics.bufferSource().endBatch(FxRenderTypes.ICON);
        // the seconds in the tile's corner: a whole number of screen pixels per font pixel, so the digits stay sharp
        double guiScale = mc.getWindow().getGuiScale();
        float scale = (float) (Math.max(1, Math.round(ICON * guiScale * CursePainter.SECONDS_HEIGHT / 7.0)) / guiScale);
        for (int i = 0; i < shown.size(); i++) {
            CursePainter.drawSeconds(font, graphics.bufferSource(), m, shown.get(i), at[i] + CELL * 0.5F, y + CELL * 0.5F, ICON, now, 1.0F, scale);
        }
        graphics.flush();
        graphics.pose().popPose();
    }
}
