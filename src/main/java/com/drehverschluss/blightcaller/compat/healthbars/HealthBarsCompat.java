package com.drehverschluss.blightcaller.compat.healthbars;

import com.drehverschluss.blightcaller.client.display.HealthBarsView;
import com.drehverschluss.blightcaller.compat.Couplings;
import com.drehverschluss.blightcaller.mixin.client.EntityRendererInvoker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Soft coupling with Health Bars (Fuzs, mod id {@code healthbars}, MPL-2.0; nothing of it is copied, only its public members
 * are read by reflection). No compile dependency and no mixin into Health Bars: Blightcaller reads Health Bars' live client
 * config ({@code HealthBars.CONFIG.get(ClientConfig.class)}, so changes in game take effect at once) and asks its own helpers
 * whether it draws a bar for a mob ({@code HealthTracker}, {@code EntityVisibilityHelper.isEntityVisible}) and which mob its
 * box shows ({@code PickEntityHandler.getCrosshairPickEntity}). Positions are worked out the same way Health Bars places its
 * bar (version 21.1.1 checked: {@code InLevelRenderingHandler}, {@code HealthBarRenderHelper}, {@code GuiRenderingHandler},
 * {@code AnchorPoint}).
 * <p>
 * If anything is missing (another version renamed it) or throws, the coupling switches itself off with one warning and the
 * curse display falls back to its own position above the head ({@link Couplings}).
 */
public final class HealthBarsCompat implements HealthBarsView {
    public static final String MOD = "Health Bars";
    private static final String FALLBACK = "using own position above the head";

    private final Object holder;
    private final Method holderGet;
    private final Class<?> clientConfigClass;
    private final Field anyRendering;
    private final Field guiRendering;
    private final Field levelRendering;
    private final Field noHealthBarMobs;
    private final Field levelField;
    private final Field guiField;
    // ClientConfig.Level
    private final Field renderScale;
    private final Field scaleWithDistance;
    private final Field levelOffsetHeight;
    private final Field renderTitleComponent;
    private final Field renderHealthComponent;
    private final Field renderSpriteComponent;
    private final Field maxRenderDistance;
    // ClientConfig.BarConfig (both)
    private final Field healthBarWidth;
    private final Field scaleBarWidthByHealth;
    // ClientConfig.Gui
    private final Field anchorPoint;
    private final Field guiOffsetWidth;
    private final Field guiOffsetHeight;
    private final Field renderEntityDisplay;
    private final Field renderAttributeComponents;
    // helpers
    private final Method isEntityAllowed;
    private final Method getHealthTracker;
    private final Method trackerData;
    private final Method dataDisplayName;
    private final Method dataMaxHealth;
    private final Method dataHealthComponent;
    private final Method isEntityVisible;
    private final Method crosshairPickEntity;
    private boolean broken;

    private HealthBarsCompat() throws ReflectiveOperationException {
        Class<?> mod = Class.forName("fuzs.healthbars.HealthBars");
        holder = mod.getField("CONFIG").get(null);
        holderGet = Class.forName("fuzs.puzzleslib.api.config.v3.ConfigHolder").getMethod("get", Class.class);
        clientConfigClass = Class.forName("fuzs.healthbars.config.ClientConfig");
        anyRendering = clientConfigClass.getField("anyRendering");
        guiRendering = clientConfigClass.getField("guiRendering");
        levelRendering = clientConfigClass.getField("levelRendering");
        isEntityAllowed = clientConfigClass.getMethod("isEntityAllowed", LivingEntity.class);
        noHealthBarMobs = clientConfigClass.getField("noHealthBarMobs");
        levelField = clientConfigClass.getField("level");
        guiField = clientConfigClass.getField("gui");
        Class<?> levelClass = Class.forName("fuzs.healthbars.config.ClientConfig$Level");
        renderScale = levelClass.getField("renderScale");
        scaleWithDistance = levelClass.getField("scaleWithDistance");
        levelOffsetHeight = levelClass.getField("offsetHeight");
        renderTitleComponent = levelClass.getField("renderTitleComponent");
        renderHealthComponent = levelClass.getField("renderHealthComponent");
        renderSpriteComponent = levelClass.getField("renderSpriteComponent");
        maxRenderDistance = levelClass.getField("maxRenderDistance");
        Class<?> barClass = Class.forName("fuzs.healthbars.config.ClientConfig$BarConfig");
        healthBarWidth = barClass.getField("healthBarWidth");
        scaleBarWidthByHealth = barClass.getField("scaleBarWidthByHealth");
        Class<?> guiClass = Class.forName("fuzs.healthbars.config.ClientConfig$Gui");
        anchorPoint = guiClass.getField("anchorPoint");
        guiOffsetWidth = guiClass.getField("offsetWidth");
        guiOffsetHeight = guiClass.getField("offsetHeight");
        renderEntityDisplay = guiClass.getField("renderEntityDisplay");
        renderAttributeComponents = guiClass.getField("renderAttributeComponents");
        Class<?> tracker = Class.forName("fuzs.healthbars.client.helper.HealthTracker");
        getHealthTracker = tracker.getMethod("getHealthTracker", LivingEntity.class, boolean.class);
        trackerData = tracker.getMethod("getData");
        Class<?> data = Class.forName("fuzs.healthbars.client.helper.HealthTracker$EntityDataCache");
        dataDisplayName = data.getMethod("displayName");
        dataMaxHealth = data.getMethod("maxHealth");
        dataHealthComponent = data.getMethod("getHealthComponent");
        isEntityVisible = Class.forName("fuzs.healthbars.client.helper.EntityVisibilityHelper").getMethod("isEntityVisible",
                Level.class, LivingEntity.class, Player.class, float.class, EntityRenderDispatcher.class, boolean.class);
        crosshairPickEntity = Class.forName("fuzs.healthbars.client.handler.PickEntityHandler").getMethod("getCrosshairPickEntity");
    }

    /**
     * Sets up the coupling (Health Bars is installed): one log line either way. Returns {@code null} if Health Bars' config
     * or helpers cannot be read.
     */
    @Nullable
    public static HealthBarsView create() {
        try {
            HealthBarsCompat compat = new HealthBarsCompat();
            compat.config();   // readable now?
            Couplings.found(MOD, "curse icons attached to its health bars");
            return compat;
        } catch (Throwable t) {
            Couplings.failed(MOD, "its config could not be read (" + Couplings.reason(t) + ")", FALLBACK);
            return null;
        }
    }

    private Object config() throws ReflectiveOperationException {
        return holderGet.invoke(holder, clientConfigClass);
    }

    private boolean on(Object config, Field field) throws ReflectiveOperationException {
        Object value = field.get(config);
        if (value instanceof Supplier<?> supplier) {   // a NeoForge config value (any_rendering)
            value = supplier.get();
        }
        return Boolean.TRUE.equals(value);
    }

    private void fail(Throwable t) {
        broken = true;
        Couplings.failed(MOD, "reading it failed (" + Couplings.reason(t) + ")", FALLBACK);
    }

    @Nullable
    @Override
    public LevelBar levelBar(LivingEntity entity, EntityRenderer<?> renderer, float partialTick) {
        if (broken) {
            return null;
        }
        try {
            Object config = config();
            // Fixed height (phase 7A): the icons stand where they would stand with a visible bar whenever Health Bars would
            // draw one for this mob in principle. Reasons that come and go (full health with hide_at_full_health, no line of
            // sight, not the picked entity with picked_entity, team visibility, being ridden) are ignored, so the icons never
            // jump when the bar appears or goes. Reasons that last (bars switched off, the mob in no_health_bar_mobs, farther
            // than max_render_distance) mean no bar: own position, as without Health Bars. F1 hides the icons themselves.
            if (!on(config, anyRendering) || !on(config, levelRendering) || !entity.isAlive()
                    || inNoHealthBarMobs(config, entity)) {
                return null;
            }
            Object tracker = getHealthTracker.invoke(null, entity, false);
            Minecraft mc = Minecraft.getInstance();
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
            Object level = levelField.get(config);
            if (tracker == null || attachment == null || dispatcher.camera == null || !(mc.getCameraEntity() instanceof LivingEntity)
                    || mc.player == null || beyondRenderDistance(level, entity, dispatcher)) {
                return null;
            }
            // the size Health Bars draws at (InLevelRenderingHandler.getRenderScale)
            float scale = (float) renderScale.getDouble(level);
            if (scaleWithDistance.getBoolean(level)) {
                double distance = dispatcher.distanceToSqr(entity);
                double range = mc.player.entityInteractionRange();
                double ratio = Mth.clamp((distance - Math.pow(range / 2.0, 2.0)) / (Math.pow(range * 2.0, 2.0) / 2.0), 0.0, 2.0);
                scale *= (float) (1.0 + ratio);
            }
            Object data = trackerData.invoke(tracker);
            Component name = (Component) dataDisplayName.invoke(data);
            int heightOffset = "deadmau5".equals(entity.getDisplayName().getString()) ? -13 : -3;
            boolean title = renderTitleComponent.getBoolean(level);
            boolean health = renderHealthComponent.getBoolean(level);
            if (!title && renderer instanceof EntityRendererInvoker invoker && showsName(invoker, entity)) {
                heightOffset -= 13;
            }
            heightOffset += levelOffsetHeight.getInt(level);
            int barY = heightOffset + 8;
            int width = barWidth(level, ((Number) dataMaxHealth.invoke(data)).floatValue());
            // the text rows above the bar (HealthBarRenderHelper.renderHealthBarDecorations): title and health in one row when
            // they fit next to each other, otherwise one row each
            Font font = mc.font;
            int titleWidth = font.width(name);
            int healthWidth = font.width((Component) dataHealthComponent.invoke(data)) + (renderSpriteComponent.getBoolean(level) ? 11 : 0);
            int rows = title && health && titleWidth < width - 2 - healthWidth - 4 ? 1 : (title ? 1 : 0) + (health ? 1 : 0);
            return new LevelBar(0.025F * scale, barY - 11 * rows, width);
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    /** The mob is in Health Bars' {@code no_health_bar_mobs}: it never gets a bar. */
    private boolean inNoHealthBarMobs(Object config, LivingEntity entity) throws ReflectiveOperationException {
        return noHealthBarMobs.get(config) instanceof java.util.Collection<?> set && set.contains(entity.getType());
    }

    /** Farther than Health Bars' {@code max_render_distance} (half of it for sneaking mobs), as EntityVisibilityHelper checks. */
    private boolean beyondRenderDistance(Object level, LivingEntity entity, EntityRenderDispatcher dispatcher)
            throws ReflectiveOperationException {
        int max = maxRenderDistance.getInt(level);
        if (entity.isDiscrete()) {
            max /= 2;
        }
        return dispatcher.distanceToSqr(entity) >= (double) max * max;
    }

    private static boolean showsName(EntityRendererInvoker invoker, Entity entity) {
        try {
            return invoker.blightcaller$shouldShowName(entity);
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public Box box(int guiWidth, int guiHeight, float partialTick) {
        if (broken) {
            return null;
        }
        try {
            Object config = config();
            if (!on(config, anyRendering) || !on(config, guiRendering)
                    || !(crosshairPickEntity.invoke(null) instanceof LivingEntity entity)
                    || !(Boolean) isEntityAllowed.invoke(config, entity)) {
                return null;
            }
            Object tracker = getHealthTracker.invoke(null, entity, false);
            Minecraft mc = Minecraft.getInstance();
            if (tracker == null || mc.player == null
                    || !(Boolean) isEntityVisible.invoke(null, mc.level, entity, mc.player, partialTick, mc.getEntityRenderDispatcher(), true)) {
                return null;
            }
            Object gui = guiField.get(config);
            Object data = trackerData.invoke(tracker);
            int width = barWidth(gui, ((Number) dataMaxHealth.invoke(data)).floatValue());
            String anchor = ((Enum<?>) anchorPoint.get(gui)).name();
            int ax = anchor.endsWith("LEFT") ? -1 : anchor.endsWith("RIGHT") ? 1 : 0;
            int ay = anchor.startsWith("TOP") ? -1 : anchor.startsWith("BOTTOM") ? 1 : 0;
            // AnchorPoint.PositionerImpl with the size Health Bars reserves (47 + bar width, 42)
            int elementWidth = 47 + width;
            int x = guiWidth / 2 + ax * (guiWidth / 2 - guiOffsetWidth.getInt(gui)) - (ax + 1) * elementWidth / 2;
            int y = guiHeight / 2 + ay * (guiHeight / 2 - guiOffsetHeight.getInt(gui)) - (ay + 1) * 42 / 2;
            boolean display = renderEntityDisplay.getBoolean(gui);
            int x1 = x + (display ? elementWidth : width);
            int y0 = display ? y : y + 4;
            boolean attributes = renderAttributeComponents.getBoolean(gui);
            int y1 = display ? y + 42 : y + (attributes ? 38 : 27);
            // the text column (measured in the game, phase I): 47 wide portrait part (on the right for a box on the right side,
            // Health Bars mirrors it), the name 5 below the top of the box, the health line with its shadow ends 37 below it
            // (26 for the bar without that line)
            int textX = display && ax <= 0 ? x + 47 : x;
            int textX1 = display && ax > 0 ? x1 - 47 : x1;
            return new Box(entity, x, y0, x1, y1, ax, ay, textX, textX1, y + 5, y + (attributes ? 37 : 26));
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    /** HealthBarHelper.getBarWidth: 45 per width step plus 2, wider for mobs with much health if set so. */
    private int barWidth(Object barConfig, float maxHealth) throws ReflectiveOperationException {
        int scale = scaleBarWidthByHealth.getBoolean(barConfig) ? Mth.ceil(maxHealth / 40.0F) - 2 : 0;
        scale = Math.max(scale, 0) + healthBarWidth.getInt(barConfig);
        return 45 * Mth.clamp(scale, 1, 4) + 2;
    }
}
