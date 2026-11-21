package com.drehverschluss.blightcaller.client.display;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What the curse display needs to know about Health Bars (Fuzs, optional): where its floating health bar above a mob is
 * and where its mob box on the screen is. Implemented in {@code compat.healthbars.HealthBarsCompat}, only when Health Bars
 * is installed; without it the display uses its own position above the head and no box row.
 */
public interface HealthBarsView {
    /**
     * The floating health bar of a mob as Health Bars draws it this frame, in its own units (gui pixels, y down) around the
     * name tag point half a block above the name tag attachment.
     *
     * @param unit  size of one unit in blocks (0.025 × Health Bars' render scale, grows with distance if set so)
     * @param top   top of the whole display (bar, health and title text), negative is above the point
     * @param width width of the bar, centred on the point
     */
    record LevelBar(float unit, float top, int width) {}

    /**
     * The mob box of Health Bars on the screen (gui coordinates) and the entity it shows.
     *
     * @param anchorX    -1 left, 0 centre, 1 right (Health Bars' anchor point)
     * @param anchorY    -1 top, 0 middle, 1 bottom
     * @param textX      left edge of the text column (name, bar, health line): right of the portrait if there is one
     * @param textX1     right edge of the text column (a box on the right side mirrors: portrait right, text right-aligned)
     * @param titleTop   top of the mob's name
     * @param textBottom bottom of the last text line (health and armour, shadow included, or the bar without that line)
     */
    record Box(LivingEntity entity, int x0, int y0, int x1, int y1, int anchorX, int anchorY, int textX, int textX1, int titleTop, int textBottom) {}

    /** The floating bar Health Bars draws for this mob now, or {@code null} if it draws none. */
    @Nullable
    LevelBar levelBar(LivingEntity entity, EntityRenderer<?> renderer, float partialTick);

    /** The mob box Health Bars shows now, or {@code null}. */
    @Nullable
    Box box(int guiWidth, int guiHeight, float partialTick);
}
