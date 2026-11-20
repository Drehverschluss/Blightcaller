package com.drehverschluss.blightcaller.client.display;

import com.drehverschluss.blightcaller.client.fx.FxRenderTypes;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.drehverschluss.blightcaller.curse.CurseView;
import com.drehverschluss.blightcaller.mixin.client.EntityRendererInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Curse icons above the head (concept 8.1, level 2 of the display): one small icon per curse, each with a ring that runs
 * down with its remaining time, blinking in the last two seconds; own curses bright, other players' curses pale; the own
 * entry's time if there is one, else the longest (like Jade).
 * <p>
 * <b>Where:</b> the mob's name tag event ({@code RenderNameTagEvent}, the same event Health Bars and the vanilla name tag
 * use) is watched at the lowest priority: it says that the mob is drawn this frame and whether a name tag is shown. The
 * icons are then drawn in one pass after the translucent blocks, around the same point the name tag uses (name tag
 * attachment plus half a block):
 * <ul>
 *     <li>with a floating bar of Health Bars: right above the whole bar (bar, health and title), in its size, which follows
 *     Health Bars' config and grows with distance like the bar, never wider than the bar;</li>
 *     <li>otherwise above the name tag if one is shown, else where the name tag would be.</li>
 * </ul>
 * <b>Which mobs</b> (client option): the mob looked at, and with {@code OWN_TARGETS} every mob with an own curse in range.
 * With Jade, the mob in the crosshair within reach (Jade's tooltip shows it in detail) gets no icons.
 */
final class CurseIcons {
    /** Icon edge in the units of Health Bars' bar (gui pixels) and in the own name tag size. */
    static final float HB_ICON = 12.0F;
    static final float OWN_ICON = 10.0F;
    static final float GAP_FACTOR = 0.45F;

    /** What the name tag event of a cursed mob said this frame. */
    record Seen(long frame, boolean name, @Nullable HealthBarsView.LevelBar bar) {}

    private static final Map<Integer, Seen> SEEN = new HashMap<>();
    /** Mobs with icons in the last frame (probe, report). */
    static int lastShown;

    private CurseIcons() {}

    /** Lowest priority: after Health Bars, Dynamic Difficulty and the others have decided about the name tag. */
    static void onNameTag(RenderNameTagEvent event, long frame, @Nullable HealthBarsView healthBars) {
        if (!(event.getEntity() instanceof LivingEntity living) || CurseLines.view(living) == null) {
            return;
        }
        EntityRenderer<?> renderer = event.getEntityRenderer();
        boolean name = event.canRender().isTrue() || event.canRender().isDefault()
                && renderer instanceof EntityRendererInvoker invoker && invoker.blightcaller$shouldShowName(living);
        HealthBarsView.LevelBar bar = healthBars == null ? null : healthBars.levelBar(living, renderer, event.getPartialTick());
        SEEN.put(living.getId(), new Seen(frame, name, bar));
    }

    static void render(PoseStack.Pose base, Vec3 cam, MultiBufferSource.BufferSource buffers, float partialTick, long frame,
                       @Nullable LivingEntity picked) {
        lastShown = 0;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlightcallerClientConfig.IconMode mode = BlightcallerClientConfig.curseIcons();
        // hidden with the GUI (F1) like the vanilla name tags and Health Bars' bars
        if (level == null || mc.player == null || mode == BlightcallerClientConfig.IconMode.OFF || !Minecraft.renderNames()
                || (CurseDisplay.jade() && !BlightcallerClientConfig.curseIconsWithJade())) {
            SEEN.clear();
            return;
        }
        if (SEEN.size() > 256) {
            SEEN.values().removeIf(s -> s.frame() != frame);
        }
        double range = BlightcallerClientConfig.curseIconRange();
        UUID me = mc.player.getUUID();
        long tick = level.getGameTime();
        float now = tick + partialTick;
        List<Placed> placed = new ArrayList<>();
        for (Map.Entry<Integer, Seen> e : SEEN.entrySet()) {
            if (e.getValue().frame() != frame || !(level.getEntity(e.getKey()) instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            CurseView view = CurseLines.view(living);
            if (view == null || living.position().distanceToSqr(cam) > range * range) {
                continue;
            }
            boolean show = living == picked || mode == BlightcallerClientConfig.IconMode.OWN_TARGETS && CurseLines.hasOwn(view, me);
            // the mob in the crosshair within reach is the one Jade shows in detail at the top of the screen: no icons there,
            // they would sit under Jade's tooltip when standing close
            if (!show || CurseDisplay.jade() && mc.crosshairPickEntity == living) {
                continue;
            }
            List<CurseLines.Shown> shown = CurseLines.shown(view, me, tick);
            if (!shown.isEmpty()) {
                placed.add(place(base, cam, partialTick, living, e.getValue(), shown));
            }
        }
        lastShown = placed.size();
        if (placed.isEmpty()) {
            return;
        }
        // two batches for all mobs: first every ring, then every icon
        VertexConsumer flat = buffers.getBuffer(FxRenderTypes.FLAT);
        for (Placed p : placed) {
            p.each((s, x, y, size) -> CursePainter.rings(flat, p.matrix(), s, x, y, size, now, 1.0F));
        }
        buffers.endBatch(FxRenderTypes.FLAT);
        VertexConsumer icons = buffers.getBuffer(FxRenderTypes.ICON);
        for (Placed p : placed) {
            p.each((s, x, y, size) -> CursePainter.icon(icons, p.matrix(), s, x, y, size, now, 1.0F));
        }
        buffers.endBatch(FxRenderTypes.ICON);
    }

    /** Where the icons of one mob go: the camera facing matrix and the row in its units. */
    private record Placed(Matrix4f matrix, List<CurseLines.Shown> shown, float x0, float cy, float size, float step) {
        void each(Cell cell) {
            float x = x0;
            for (CurseLines.Shown s : shown) {
                cell.at(s, x, cy, size);
                x += step;
            }
        }
    }

    private interface Cell {
        void at(CurseLines.Shown shown, float x, float y, float size);
    }

    private static Placed place(PoseStack.Pose base, Vec3 cam, float partialTick, LivingEntity living, Seen seen,
                                List<CurseLines.Shown> shown) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity> renderer = dispatcher.getRenderer(living);
        Vec3 offset = renderer.getRenderOffset(living, partialTick);
        Vec3 attachment = living.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, living.getViewYRot(partialTick));
        if (attachment == null) {
            attachment = new Vec3(0.0, living.getBbHeight(), 0.0);
        }
        Vec3 at = lerp(living, partialTick).subtract(cam).add(offset).add(attachment).add(0.0, 0.5, 0.0);
        PoseStack pose = new PoseStack();
        pose.last().pose().set(base.pose());
        pose.last().normal().set(base.normal());
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(dispatcher.cameraOrientation());
        int n = shown.size();
        float unit;
        float size;
        float bottom;
        float maxWidth;
        HealthBarsView.LevelBar bar = seen.bar();
        if (bar != null) {
            // right above Health Bars' bar and text, in its size (it follows its config and grows with distance like the bar)
            unit = bar.unit();
            size = HB_ICON;
            // a vanilla name tag shown at the same time sits at 0..9 in name tag units (0.025): its top is at 0 here too
            bottom = (seen.name() ? Math.min(bar.top(), 0.0F) : bar.top()) - 4.0F;
            maxWidth = bar.width();
        } else {
            // own position: above the name tag, or where it would be; a little bigger farther away to stay readable
            double distance = Math.sqrt(living.position().distanceToSqr(cam));
            unit = 0.025F * (1.0F + 0.8F * (float) Mth.clamp((distance - 6.0) / 14.0, 0.0, 1.0));
            size = OWN_ICON;
            float nameTop = "deadmau5".equals(living.getDisplayName().getString()) ? -10.0F : 0.0F;
            bottom = seen.name() ? (nameTop - 3.0F) * 0.025F / unit : 9.0F * 0.025F / unit;
            maxWidth = Float.MAX_VALUE;
        }
        float gap = size * GAP_FACTOR;
        float ring = size * 0.22F;
        float cell = size + 2.0F * ring;
        float width = n * cell + (n - 1) * gap;
        if (width > maxWidth) {
            float shrink = maxWidth / width;
            size *= shrink;
            gap *= shrink;
            ring *= shrink;
            cell *= shrink;
            width = maxWidth;
        }
        pose.scale(unit, -unit, unit);
        return new Placed(new Matrix4f(pose.last().pose()), shown, -width * 0.5F + cell * 0.5F, bottom - cell * 0.5F, size, cell + gap);
    }

    private static Vec3 lerp(Entity e, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()), Mth.lerp(partialTick, e.zo, e.getZ()));
    }
}
