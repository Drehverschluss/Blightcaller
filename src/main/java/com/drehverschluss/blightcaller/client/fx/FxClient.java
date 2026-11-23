package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.client.display.CurseDisplay;
import com.drehverschluss.blightcaller.curse.CurseEffects;
import com.drehverschluss.blightcaller.curse.CurseView;
import com.drehverschluss.blightcaller.fx.BlightcallerParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.effect.Synchronized;

/**
 * Client visuals of the curses and spells that Spell Engine does not draw by itself.
 * <ul>
 *     <li><b>Curse particles on targets without the display effect</b> (Wither, Ender Dragon, Cataclysm bosses): Spell
 *     Engine only spawns curse particles for synchronized effects; for curses that are only in the synced curse view the
 *     same particle spawner is called here, every client tick.</li>
 *     <li>Gravebind chains and the cast wave ({@link GravebindChains}, links from model files: {@link ChainLinks}) and
 *     Drain Soul ({@link DrainSoulBeam}: soul smoke, the torn soul {@link EntityGhost}, soul shreds), one pass after the
 *     translucent blocks.</li>
 *     <li>The particle that starts the cast wave ({@link GraveWaveParticle}) and the dust of crumbling chains.</li>
 *     <li>Blight Burst in flight: a ball of slime with a trail and a splash ({@link BlightBurstFx}, phase 12B.1).</li>
 * </ul>
 * Every hook catches its own exceptions: a visual must never break the game.
 */
public final class FxClient {
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    private FxClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterParticleProvidersEvent.class, FxClient::onRegisterParticles);
        modBus.addListener(ModelEvent.RegisterAdditional.class, ChainLinks::registerModels);
        modBus.addListener(ModelEvent.BakingCompleted.class, ChainLinks::onBakingCompleted);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, FxClient::onRender);
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, FxClient::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, FxClient::onClientTick);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpecial(BlightcallerParticles.GRAVE_WAVE.get(), new GraveWaveParticle.Provider());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        try {
            DrainSoulBeam.tick();
            BlightBurstFx.tick();
        } catch (Exception e) {
            warn(e);
        }
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !living.level().isClientSide() || !living.isAlive()) {
            return;
        }
        CurseView view = CurseView.of(living).orElse(null);
        if (view == null || view.lines().isEmpty()) {
            return;
        }
        try {
            List<Synchronized.Effect> shown = Synchronized.effectsOf(living);
            for (CurseView.Line line : view.lines()) {
                MobEffect effect = line.curse().value();
                if (effect == CurseEffects.GRAVEBIND.value()) {
                    GravebindChains.dust(living, line);
                }
                boolean byEngine = false;
                for (Synchronized.Effect e : shown) {
                    if (e.effect() == effect) {
                        byEngine = true;
                        break;
                    }
                }
                if (byEngine) {
                    continue;
                }
                CustomParticleStatusEffect.Spawner spawner = CustomParticleStatusEffect.spawnerOf(effect);
                if (spawner != null) {
                    spawner.spawnParticles(living, line.amplifier());
                }
            }
        } catch (Exception e) {
            warn(e);
        }
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        HandTip.nextFrame();
        try {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Camera camera = event.getCamera();
            Vec3 cam = camera.getPosition();
            PoseStack.Pose base = event.getPoseStack().last();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            // the curse veil first (translucent, no depth write), then the chains and Drain Soul, the curse icons last
            CurseDisplay.renderLevel(base, cam, event.getFrustum(), buffers, partialTick, false);
            GravebindChains.render(base, cam, event.getFrustum(), buffers, partialTick);
            DrainSoulBeam.render(base, cam, camera, event.getProjectionMatrix(), buffers, partialTick);
            BlightBurstFx.render(base, cam, camera, event.getFrustum(), buffers, partialTick);
            CurseDisplay.renderLevel(base, cam, event.getFrustum(), buffers, partialTick, true);
        } catch (Exception e) {
            warn(e);
        }
    }

    /** Logs every kind of failure once (where and what), so one failure does not hide another. */
    private static void warn(Exception e) {
        StackTraceElement[] trace = e.getStackTrace();
        String key = e.getClass().getName() + (trace.length > 0 ? "@" + trace[0] : "");
        if (WARNED.size() < 20 && WARNED.add(key)) {
            Blightcaller.LOGGER.error("Blightcaller: curse visuals failed (reported once)", e);
        }
    }
}
