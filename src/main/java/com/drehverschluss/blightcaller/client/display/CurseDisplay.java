package com.drehverschluss.blightcaller.client.display;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.compat.healthbars.HealthBarsCompat;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Curses visible without Jade (concept 8.1, Phase 6.2), three levels that add up:
 * <ol>
 *     <li>the veil over cursed mobs, stronger with the curse level ({@link CurseVeil});</li>
 *     <li>curse icons with a running ring above the head, placed on top of Health Bars' floating bar when it is there
 *     ({@link CurseIcons}), and a row of curses with seconds at Health Bars' mob box ({@link CurseHudRow});</li>
 *     <li>Jade's tooltip (own plugin): every curse, or only the curse level while the icons have priority (client option
 *     {@code priority}, phase I.1).</li>
 * </ol>
 * Health Bars is optional and only read by reflection ({@link HealthBarsCompat}); without it the icons have their own place
 * above the head and there is no box row. Everything is client only and catches its own errors (a display must never break
 * the game).
 */
public final class CurseDisplay {
    private static final Set<String> WARNED = new HashSet<>();
    @Nullable
    private static HealthBarsView healthBars;
    private static boolean jade;
    private static long frame;
    /** The mob looked at (own pick in the icon range), kept for half a second after looking away. */
    private static WeakReference<LivingEntity> picked = new WeakReference<>(null);
    private static int pickHold;

    private CurseDisplay() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(CurseDisplay::setup));
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, event -> frame++);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderNameTagEvent.class, CurseDisplay::onNameTag);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, CurseDisplay::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, CurseDisplay::onGui);
    }

    private static void setup() {
        jade = ModList.get().isLoaded("jade");
        if (ModList.get().isLoaded("healthbars")) {
            healthBars = HealthBarsCompat.create();
        }
        BlightcallerClientConfig.setHealthBarsCoupled(healthBars != null);
    }

    static boolean jade() {
        return jade;
    }

    /** Jade is installed and shows every curse (client option {@code priority}); then Jade's crosshair target gets no icons. */
    static boolean jadeShowsAllCurses() {
        return jade && BlightcallerClientConfig.jadeShowsAllCurses();
    }

    @Nullable
    public static HealthBarsView healthBars() {
        return healthBars;
    }

    private static void onNameTag(RenderNameTagEvent event) {
        try {
            CurseIcons.onNameTag(event, frame, healthBars);
        } catch (Exception e) {
            warn(e);
        }
    }

    /** Veil and icons, in the pass after the translucent blocks (called by FxClient). */
    public static void renderLevel(PoseStack.Pose base, Vec3 cam, Frustum frustum, MultiBufferSource.BufferSource buffers,
                                   float partialTick, boolean icons) {
        try {
            if (icons) {
                CurseIcons.render(base, cam, buffers, partialTick, frame, picked.get());
            } else {
                CurseVeil.render(base, cam, frustum, buffers, partialTick);
            }
        } catch (Exception e) {
            warn(e);
        }
    }

    private static void onGui(RenderGuiEvent.Post event) {
        try {
            CurseHudRow.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false), healthBars);
        } catch (Exception e) {
            warn(e);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        try {
            pick();
        } catch (Exception e) {
            warn(e);
        }
    }

    /** The living entity on the line of sight within the icon range (blocks stop it). */
    private static void pick() {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (mc.level == null || camera == null || BlightcallerClientConfig.curseIcons() == BlightcallerClientConfig.IconMode.OFF) {
            picked = new WeakReference<>(null);
            return;
        }
        double range = BlightcallerClientConfig.curseIconRange();
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 to = eye.add(look.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, to, new AABB(eye, to).inflate(1.0),
                e -> (e instanceof LivingEntity || e instanceof EnderDragonPart) && !e.isSpectator() && e.isPickable() && e != camera,
                reach * reach);
        LivingEntity found = null;
        if (hit != null) {
            Entity e = hit.getEntity();
            if (e instanceof EnderDragonPart part) {
                e = part.parentMob;
            }
            found = e instanceof LivingEntity living ? living : null;
        }
        if (found != null) {
            picked = new WeakReference<>(found);
            pickHold = 10;
        } else if (pickHold > 0) {
            pickHold--;
        } else {
            picked = new WeakReference<>(null);
        }
    }

    @Nullable
    static LivingEntity picked() {
        return picked.get();
    }

    private static void warn(Exception e) {
        StackTraceElement[] trace = e.getStackTrace();
        String key = e.getClass().getName() + (trace.length > 0 ? "@" + trace[0] : "");
        if (WARNED.size() < 20 && WARNED.add(key)) {
            Blightcaller.LOGGER.error("Blightcaller: curse display failed (reported once)", e);
        }
    }
}
