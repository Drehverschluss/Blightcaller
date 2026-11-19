package com.drehverschluss.blightcaller;

import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.command.BlightcallerCommands;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.curse.CurseData;
import com.drehverschluss.blightcaller.curse.CurseEffects;
import com.drehverschluss.blightcaller.curse.CurseEvents;
import com.drehverschluss.blightcaller.curse.CurseImpacts;
import com.drehverschluss.blightcaller.curse.CursePredicates;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(Blightcaller.MODID)
public class Blightcaller {
    public static final String MODID = "blightcaller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Blightcaller(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, BlightcallerConfig.SPEC);
        BlightcallerAttributes.register(modEventBus);
        CurseEffects.register(modEventBus);
        CurseData.register(modEventBus);
        CurseEvents.register();
        CurseImpacts.register();
        CursePredicates.register();
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, BlightcallerCommands::onRegisterCommands);
        LOGGER.info("Blightcaller {} loaded", modContainer.getModInfo().getVersion());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
