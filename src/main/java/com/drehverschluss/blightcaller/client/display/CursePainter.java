package com.drehverschluss.blightcaller.client.display;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Draws one curse in a flat 2D space with y down: the world (camera facing, above a mob's head) and the GUI (row at the
 * Health Bars box) use the same code. Two looks (client option {@code icon_style}, phase I after the choice of 21.11.):
 * <ul>
 *     <li><b>tile</b> (DETAILED, and always in the row at the box): the effect icon with its dark tile; the expired part turns
 *     grey from the top down (the faded version of the same icon, so the curse stays recognisable), smoothly with the partial
 *     tick, and is in full colour again at once when the curse is renewed. A thin bright frame (violet for own curses, pale for
 *     other players' curses) with a dark line outside keeps the tile from vanishing as a dark square in the night sky.
 *     Above the head the remaining seconds stand small in the bottom right corner with a dark outline, like an item count;</li>
 *     <li><b>ring</b> (SIMPLE): the small symbol with a ring around it that runs down clockwise from the top.</li>
 * </ul>
 * Own curses bright, curses of other players pale (like Jade), and in the last two seconds everything blinks.
 */
final class CursePainter {
    /** Violet ring of own curses, grey of other players' curses, dark track below the ring. */
    static final int OWN_RING = 0xFFC77DFF;
    static final int FOREIGN_RING = 0xFFA0A0A0;
    static final int TRACK = 0x99180A24;
    /** Curses of other players: almost opaque (a see-through tile washes out on the sky and vanishes at night), but dimmed. */
    static final float FOREIGN_ALPHA = 0.9F;
    static final float FOREIGN_BRIGHT = 0.65F;
    /** Last seconds in which the icon blinks. */
    static final float BLINK_TICKS = 40.0F;
    /** The dark edge around icons without a background (black violet, a little see-through). */
    static final float EDGE_ALPHA = 0.85F;
    static final float EDGE_R = 0.05F;
    static final float EDGE_G = 0.02F;
    static final float EDGE_B = 0.09F;
    /** The icon lies this far in front of its edge (units of the row; the vanilla text lies 0.03 in front of its shadow). */
    static final float ICON_Z = 0.03F;
    /** Width of the bright frame and of the dark line outside it, as a share of the tile edge (about one texel of 18). */
    static final float FRAME = 0.065F;
    /** The faded part when a curse has no faded picture: the normal icon this dark. */
    static final float FALLBACK_FADED = 0.4F;
    /** Height of the seconds' digits as a share of the tile edge (a little smaller than an item count, 7 of 16). */
    static final float SECONDS_HEIGHT = 0.36F;
    /** The seconds are hidden when one pixel of their font would be smaller than this many screen pixels. */
    static final float SECONDS_MIN_PIXEL = 0.9F;
    static final int SECONDS_OWN = 0xFFFFFF;
    static final int SECONDS_FOREIGN = 0xB8B8B8;
    static final int SECONDS_OUTLINE = 0x0D0517;
    /** The ring of the symbol encloses the whole square symbol (half its diagonal plus a gap). */
    static final float RING_INNER = 0.5F * Mth.SQRT_OF_TWO + 0.05F;
    private static final int SEGMENTS = 32;

    private CursePainter() {}

    /** Opacity of an icon: blinking (2.5 times a second) in the last two seconds, pale for other players' curses. */
    static float alpha(CurseLines.Shown shown, float now) {
        float left = shown.end() - now;
        float a = shown.own() ? 1.0F : FOREIGN_ALPHA;
        if (left < BLINK_TICKS) {
            float blink = 0.5F + 0.5F * Mth.cos(now * Mth.TWO_PI / 8.0F);
            a *= 0.25F + 0.75F * blink;
        }
        return a;
    }

    /** Edge of the whole cell of a ring symbol (the ring included) for a symbol of this size. */
    static float ringCell(float size) {
        return 2.0F * (size * RING_INNER + ringWidth(size));
    }

    private static float ringWidth(float size) {
        return Math.max(1.0F, size * 0.16F);
    }

    /** Edge of the whole cell of a tile (frame and dark line included) for a tile of this size. */
    static float tileCell(float size) {
        return size * (1.0F + 4.0F * FRAME);
    }

    /** Ring and dark track of a curse, centred at cx/cy, size = icon edge (into a FLAT buffer). */
    static void rings(VertexConsumer flat, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity) {
        float inner = size * RING_INNER;
        float outer = inner + ringWidth(size);
        ring(flat, m, cx, cy, inner, outer, 0.0F, 1.0F, scaleAlpha(TRACK, opacity * (shown.own() ? 1.0F : 0.7F)));
        ring(flat, m, cx, cy, inner, outer, 0.0F, shown.fraction(now), scaleAlpha(shown.own() ? OWN_RING : FOREIGN_RING,
                opacity * (shown.own() ? 1.0F : 0.75F)));
    }

    /**
     * The icon of a curse centred at cx/cy (into an ICON buffer). {@code simple}: the simplified symbol (above the head).
     */
    static void icon(VertexConsumer icon, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity,
                     boolean simple) {
        TextureAtlasSprite sprite = CurseSprites.get(shown.curse(), simple);
        float h = size * 0.5F;
        float bright = shown.own() ? 1.0F : FOREIGN_BRIGHT;
        quad(icon, m, cx - h, cy - h, cx + h, cy + h, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                FastColor.ARGB32.colorFromFloat(alpha(shown, now) * opacity, bright, bright, bright), ICON_Z);
    }

    /** The bright frame of a tile and the dark line outside it (into a FLAT buffer); size = edge of the tile itself. */
    static void frame(VertexConsumer flat, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity) {
        float a = alpha(shown, now) * opacity;
        float h = size * 0.5F;
        float f = size * FRAME;
        int bright = scaleAlpha(shown.own() ? OWN_RING : FOREIGN_RING, a * (shown.own() ? 1.0F : 0.75F));
        int dark = FastColor.ARGB32.colorFromFloat(a * EDGE_ALPHA, EDGE_R, EDGE_G, EDGE_B);
        border(flat, m, cx, cy, h, h + f, bright);
        border(flat, m, cx, cy, h + f, h + 2.0F * f, dark);
    }

    /** A square border from half edge {@code in} to {@code out} around cx/cy, four strips that do not overlap. */
    private static void border(VertexConsumer vc, Matrix4f m, float cx, float cy, float in, float out, int argb) {
        rect(vc, m, cx - out, cy - out, cx + out, cy - in, argb);
        rect(vc, m, cx - out, cy + in, cx + out, cy + out, argb);
        rect(vc, m, cx - out, cy - in, cx - in, cy + in, argb);
        rect(vc, m, cx + in, cy - in, cx + out, cy + in, argb);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        vc.addVertex(m, x0, y0, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x0, y1, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y1, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y0, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
    }

    /**
     * The tile of a curse centred at cx/cy (into an ICON buffer): the expired share from the top in the faded picture, the
     * rest in full colour. The border between the two moves smoothly, since {@code now} carries the partial tick.
     */
    static void tile(VertexConsumer icon, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity) {
        TextureAtlasSprite sprite = CurseSprites.get(shown.curse(), false);
        @Nullable TextureAtlasSprite grey = CurseSprites.faded(shown.curse());
        float h = size * 0.5F;
        float a = alpha(shown, now) * opacity;
        float bright = shown.own() ? 1.0F : FOREIGN_BRIGHT;
        float cut = 1.0F - shown.fraction(now);
        float split = cy - h + size * cut;
        if (cut > 0.0F) {
            if (grey != null) {
                quad(icon, m, cx - h, cy - h, cx + h, split, grey.getU0(), grey.getV0(), grey.getU1(), Mth.lerp(cut, grey.getV0(), grey.getV1()),
                        FastColor.ARGB32.colorFromFloat(a, bright, bright, bright), ICON_Z);
            } else {
                float dim = bright * FALLBACK_FADED;
                quad(icon, m, cx - h, cy - h, cx + h, split, sprite.getU0(), sprite.getV0(), sprite.getU1(),
                        Mth.lerp(cut, sprite.getV0(), sprite.getV1()), FastColor.ARGB32.colorFromFloat(a, dim, dim, dim), ICON_Z);
            }
        }
        if (cut < 1.0F) {
            quad(icon, m, cx - h, split, cx + h, cy + h, sprite.getU0(), Mth.lerp(cut, sprite.getV0(), sprite.getV1()), sprite.getU1(),
                    sprite.getV1(), FastColor.ARGB32.colorFromFloat(a, bright, bright, bright), ICON_Z);
        }
    }

    /**
     * The remaining whole seconds in the bottom right corner of a tile, with a dark outline like an item count. Returns
     * false (and draws nothing) when the tile is too small on the screen for readable digits: {@code pixelsPerUnit} = screen
     * pixels per unit of {@code m}.
     */
    static boolean seconds(Font font, MultiBufferSource buffers, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size,
                           float now, float opacity, float pixelsPerUnit) {
        float scale = size * SECONDS_HEIGHT / 7.0F;
        if (scale * pixelsPerUnit < SECONDS_MIN_PIXEL) {
            return false;
        }
        drawSeconds(font, buffers, m, shown, cx, cy, size, now, opacity, scale);
        return true;
    }

    /** The seconds of a tile with a fixed font scale (units of {@code m} per font pixel). */
    static void drawSeconds(Font font, MultiBufferSource buffers, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size,
                            float now, float opacity, float scale) {
        // whole seconds; from 100 s on whole minutes ("5m"), so the text never gets wider than two digits and a letter
        int seconds = Math.max(1, Mth.ceil((shown.end() - now) / 20.0F));
        String text = seconds < 100 ? Integer.toString(seconds) : Mth.ceil(seconds / 60.0F) + "m";
        float h = size * 0.5F;
        int a = Mth.clamp(Math.round(alpha(shown, now) * opacity * 255.0F), 8, 255) << 24;
        Matrix4f at = new Matrix4f(m).translate(cx + h - (font.width(text) - 1) * scale, cy + h - 7.0F * scale, 2.0F * ICON_Z)
                .scale(scale, scale, 1.0F);
        font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(), 0.0F, 0.0F,
                a | (shown.own() ? SECONDS_OWN : SECONDS_FOREIGN), a | SECONDS_OUTLINE, at, buffers, LightTexture.FULL_BRIGHT);
    }

    /**
     * A thin dark edge one texel wide around an icon (copies of its sprite shifted in eight directions, dark), so the icon
     * reads on sky, grass and at night without a background box. Must be drawn in its own batch before the icons: in the
     * world the batch is sorted by distance and a copy would otherwise land on top of the icon.
     */
    static void edge(VertexConsumer icon, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity,
                     boolean simple) {
        TextureAtlasSprite sprite = CurseSprites.get(shown.curse(), simple);
        float h = size * 0.5F;
        float t = size / Math.max(1, sprite.contents().width());
        int dark = FastColor.ARGB32.colorFromFloat(alpha(shown, now) * opacity * EDGE_ALPHA, EDGE_R, EDGE_G, EDGE_B);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    quad(icon, m, cx - h + dx * t, cy - h + dy * t, cx + h + dx * t, cy + h + dy * t, sprite.getU0(), sprite.getV0(),
                            sprite.getU1(), sprite.getV1(), dark);
                }
            }
        }
    }

    /** A ring piece from {@code from} to {@code to} (fractions of a full turn, clockwise from the top). */
    static void ring(VertexConsumer vc, Matrix4f m, float cx, float cy, float inner, float outer, float from, float to, int argb) {
        if (to - from <= 0.001F || FastColor.ARGB32.alpha(argb) == 0) {
            return;
        }
        int pieces = Math.max(1, Mth.ceil(SEGMENTS * (to - from)));
        for (int i = 0; i < pieces; i++) {
            float a0 = Mth.TWO_PI * (from + (to - from) * i / pieces);
            float a1 = Mth.TWO_PI * (from + (to - from) * (i + 1) / pieces);
            float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
            vc.addVertex(m, cx + s0 * inner, cy + c0 * inner, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(m, cx + s0 * outer, cy + c0 * outer, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(m, cx + s1 * outer, cy + c1 * outer, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(m, cx + s1 * inner, cy + c1 * inner, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
                             float v1, int argb) {
        quad(vc, m, x0, y0, x1, y1, u0, v0, u1, v1, argb, 0.0F);
    }

    /** z: a little towards the viewer (like the vanilla text in front of its shadow), so the icon never fights its edge. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
                             float v1, int argb, float z) {
        vc.addVertex(m, x0, y0, z).setColor(argb).setUv(u0, v0).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x0, y1, z).setColor(argb).setUv(u0, v1).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y1, z).setColor(argb).setUv(u1, v1).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y0, z).setColor(argb).setUv(u1, v0).setLight(LightTexture.FULL_BRIGHT);
    }

    static int scaleAlpha(int argb, float factor) {
        int a = Mth.clamp(Math.round(FastColor.ARGB32.alpha(argb) * factor), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
