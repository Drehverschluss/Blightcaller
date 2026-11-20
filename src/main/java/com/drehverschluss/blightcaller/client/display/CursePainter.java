package com.drehverschluss.blightcaller.client.display;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws one curse icon with its ring in a flat 2D space with y down: the world (camera facing, above a mob's head) and the
 * GUI (row at the Health Bars box) use the same code. The ring runs down clockwise from the top with the remaining time,
 * own curses bright with a violet ring, curses of other players pale with a grey ring (like Jade), and in the last two
 * seconds the icon blinks.
 */
final class CursePainter {
    /** Violet ring of own curses, grey of other players' curses, dark track below the ring. */
    static final int OWN_RING = 0xFFC77DFF;
    static final int FOREIGN_RING = 0xFFA0A0A0;
    static final int TRACK = 0x99180A24;
    static final float FOREIGN_ALPHA = 0.45F;
    /** Last seconds in which the icon blinks. */
    static final float BLINK_TICKS = 40.0F;
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

    /** Ring and dark track of a curse, centred at cx/cy, size = icon edge (into a FLAT buffer). */
    static void rings(VertexConsumer flat, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity) {
        float inner = size * 0.5F + size * 0.06F;
        float outer = inner + Math.max(1.0F, size * 0.16F);
        ring(flat, m, cx, cy, inner, outer, 0.0F, 1.0F, scaleAlpha(TRACK, opacity * (shown.own() ? 1.0F : 0.7F)));
        ring(flat, m, cx, cy, inner, outer, 0.0F, shown.fraction(now), scaleAlpha(shown.own() ? OWN_RING : FOREIGN_RING,
                opacity * (shown.own() ? 1.0F : 0.75F)));
    }

    /** The icon of a curse centred at cx/cy (into an ICON buffer). */
    static void icon(VertexConsumer icon, Matrix4f m, CurseLines.Shown shown, float cx, float cy, float size, float now, float opacity) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(shown.curse());
        float h = size * 0.5F;
        float bright = shown.own() ? 1.0F : 0.8F;
        quad(icon, m, cx - h, cy - h, cx + h, cy + h, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                FastColor.ARGB32.colorFromFloat(alpha(shown, now) * opacity, bright, bright, bright));
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

    /** A plain rectangle (background of the row at the box). */
    static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        vc.addVertex(m, x0, y0, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x0, y1, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y1, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y0, 0.0F).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
                             float v1, int argb) {
        vc.addVertex(m, x0, y0, 0.0F).setColor(argb).setUv(u0, v0).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x0, y1, 0.0F).setColor(argb).setUv(u0, v1).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y1, 0.0F).setColor(argb).setUv(u1, v1).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, x1, y0, 0.0F).setColor(argb).setUv(u1, v0).setLight(LightTexture.FULL_BRIGHT);
    }

    static int scaleAlpha(int argb, float factor) {
        int a = Mth.clamp(Math.round(FastColor.ARGB32.alpha(argb) * factor), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Remaining time like the Jade tooltip: one decimal under 10 s (decimal separator from the language), whole seconds above. */
    static String time(float ticks) {
        float seconds = Math.max(0.0F, ticks / 20.0F);
        String number = seconds < 10.0F
                ? String.format(Locale.ROOT, "%.1f", seconds).replace(".", I18n.get("jade.blightcaller.decimal"))
                : Integer.toString((int) Math.ceil(seconds));
        return I18n.get("jade.blightcaller.seconds", number);
    }
}
