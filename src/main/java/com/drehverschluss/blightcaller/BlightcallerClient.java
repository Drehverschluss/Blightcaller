package com.drehverschluss.blightcaller;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

// Client-only entry point. This class is never loaded on a dedicated server.
@Mod(value = Blightcaller.MODID, dist = Dist.CLIENT)
public class BlightcallerClient {
    public BlightcallerClient() {
    }
}
