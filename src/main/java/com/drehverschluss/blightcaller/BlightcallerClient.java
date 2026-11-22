package com.drehverschluss.blightcaller;

import com.drehverschluss.blightcaller.client.BlightcallerArmorRenderers;
import com.drehverschluss.blightcaller.client.CurseParticles;
import com.drehverschluss.blightcaller.client.CurseTooltips;
import com.drehverschluss.blightcaller.client.SkillNodeTooltips;
import com.drehverschluss.blightcaller.compat.skilltree.SkillTab;
import com.drehverschluss.blightcaller.compat.skilltree.client.ClassLockClient;
import com.drehverschluss.blightcaller.compat.skilltree.client.SkillTreeClient;
import com.drehverschluss.blightcaller.client.display.CurseDisplay;
import com.drehverschluss.blightcaller.client.fx.FxClient;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only entry point. This class is never loaded on a dedicated server.
@Mod(value = Blightcaller.MODID, dist = Dist.CLIENT)
public class BlightcallerClient {
    public BlightcallerClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, BlightcallerClientConfig.SPEC);
        // in game: mod list > Blightcaller > Config (client options, and the server options in single player)
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(FMLClientSetupEvent.class, event -> {
            BlightcallerArmorRenderers.register();
            event.enqueueWork(CurseParticles::register);
        });
        CurseTooltips.register();
        SkillNodeTooltips.register();
        com.drehverschluss.blightcaller.fx.GraveWave.setClientHandler(p -> com.drehverschluss.blightcaller.client.fx.GravebindChains
                .addWave(p.x(), p.y(), p.z(), p.radius()));
        ClassLockClient.init();
        if (SkillTab.enabled()) {
            SkillTreeClient.init();
        }
        FxClient.register(modEventBus);
        CurseDisplay.register(modEventBus);
        com.drehverschluss.blightcaller.client.WeaponClient.register(modEventBus);
    }
}
