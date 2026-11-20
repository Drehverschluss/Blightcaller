package com.drehverschluss.blightcaller;

import com.drehverschluss.blightcaller.client.CurseParticles;
import com.drehverschluss.blightcaller.client.CurseTooltips;
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
        modEventBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(CurseParticles::register));
        CurseTooltips.register();
        FxClient.register(modEventBus);
        CurseDisplay.register(modEventBus);
    }
}
