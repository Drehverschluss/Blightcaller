package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.curse.CurseView;
import com.drehverschluss.blightcaller.fx.BlightcallerParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.effect.Synchronized;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client visuals of the curses and spells that Spell Engine does not draw by itself.
 * <ul>
 *     <li><b>Curse particles on targets without the display effect</b> (Wither, Ender Dragon, Cataclysm bosses): Spell
 *     Engine only spawns curse particles for synchronized effects; for curses that are only in the synced curse view the
 *     same particle spawner is called here, every client tick.</li>
 *     <li>Gravebind chains ({@link GravebindChains}) and the Drain Soul band ({@link DrainSoulBeam}), one pass after the
 *     translucent blocks.</li>
 *     <li>The particle of the chains bursting from the ground ({@link GraveChainParticle}).</li>
 * </ul>
 * Every hook catches its own exceptions: a visual must never break the game.
 */
public final class FxClient {
    private static final List<Object> BEAMS = new ArrayList<>();
    private static boolean warned;

    private FxClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterParticleProvidersEvent.class, FxClient::onRegisterParticles);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, FxClient::onRender);
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, FxClient::onEntityTick);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpecial(BlightcallerParticles.GRAVE_CHAIN.get(), new GraveChainParticle.Provider());
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !living.level().isClientSide() || !living.isAlive()) {
            return;
        }
        CurseView view = living.getExistingData(CurseView.type()).orElse(null);
        if (view == null || view.lines().isEmpty()) {
            return;
        }
        try {
            List<Synchronized.Effect> shown = Synchronized.effectsOf(living);
            for (CurseView.Line line : view.lines()) {
                MobEffect effect = line.curse().value();
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
        try {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Camera camera = event.getCamera();
            Vec3 cam = camera.getPosition();
            Matrix4f matrix = event.getPoseStack().last().pose();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            if (GravebindChains.render(matrix, cam, event.getFrustum(), buffers, partialTick)) {
                buffers.endBatch(GravebindChains.renderType());
            }
            if (DrainSoulBeam.renderBands(matrix, cam, buffers, partialTick, BEAMS)) {
                Vector3f up = camera.getUpVector();
                Vector3f left = camera.getLeftVector();
                DrainSoulBeam.renderWisps(matrix, cam, buffers, partialTick, BEAMS, new Vec3(up), new Vec3(left));
            }
        } catch (Exception e) {
            warn(e);
        }
    }

    private static void warn(Exception e) {
        if (!warned) {
            warned = true;
            Blightcaller.LOGGER.error("Blightcaller: curse visuals failed (reported once)", e);
        }
    }
}
