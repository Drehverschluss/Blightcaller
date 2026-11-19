package com.drehverschluss.blightcaller;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Blightcaller.MODID)
public class Blightcaller {
    public static final String MODID = "blightcaller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Blightcaller(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Blightcaller {} loaded", modContainer.getModInfo().getVersion());
    }
}
