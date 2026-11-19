package com.drehverschluss.blightcaller;

import com.drehverschluss.blightcaller.client.CurseParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// Client-only entry point. This class is never loaded on a dedicated server.
@Mod(value = Blightcaller.MODID, dist = Dist.CLIENT)
public class BlightcallerClient {
    public BlightcallerClient(IEventBus modEventBus) {
        modEventBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(CurseParticles::register));
    }
}
