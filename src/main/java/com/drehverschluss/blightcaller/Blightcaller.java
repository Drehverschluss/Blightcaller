package com.drehverschluss.blightcaller;

import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.command.BlightcallerCommands;
import com.drehverschluss.blightcaller.compat.skilltree.ClassLockPayload;
import com.drehverschluss.blightcaller.compat.skilltree.SkillTab;
import com.drehverschluss.blightcaller.compat.skilltree.SkillTreeCompat;
import com.drehverschluss.blightcaller.compat.skilltree.SkillTreePack;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.curse.Blightbloom;
import com.drehverschluss.blightcaller.curse.CurseData;
import com.drehverschluss.blightcaller.curse.CurseEffects;
import com.drehverschluss.blightcaller.curse.CurseEvents;
import com.drehverschluss.blightcaller.curse.CurseImpacts;
import com.drehverschluss.blightcaller.curse.CursePredicates;
import com.drehverschluss.blightcaller.curse.InfectiousCurses;
import com.drehverschluss.blightcaller.curse.LastRites;
import com.drehverschluss.blightcaller.curse.Pestilence;
import com.drehverschluss.blightcaller.curse.SkillMarkers;
import com.drehverschluss.blightcaller.curse.SoulMigration;
import com.drehverschluss.blightcaller.curse.WailingScream;
import com.drehverschluss.blightcaller.fx.BlightcallerParticles;
import com.drehverschluss.blightcaller.item.BlightcallerItems;
import com.drehverschluss.blightcaller.trinket.BlightcallerTrinkets;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
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
        BlightcallerParticles.register(modEventBus);
        BlightcallerItems.init();
        BlightcallerTrinkets.init();
        modEventBus.addListener(RegisterEvent.class, BlightcallerItems::onRegister);
        modEventBus.addListener(RegisterEvent.class, BlightcallerTrinkets::onRegister);
        CurseEvents.register();
        CurseImpacts.register();
        CursePredicates.register();
        Pestilence.register();
        LastRites.register();
        SkillMarkers.register();
        SoulMigration.register();
        Blightbloom.register();
        InfectiousCurses.register();
        WailingScream.register();
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, ClassLockPayload::register);
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, com.drehverschluss.blightcaller.fx.GraveWave::register);
        com.drehverschluss.blightcaller.fx.GraveWave.init();
        if (SkillTab.enabled()) {
            // Blightcaller skill tab and branch: built-in data pack, respec, class lock (Skill Tree classes only behind this check)
            modEventBus.addListener(AddPackFindersEvent.class, SkillTreePack::onAddPackFinders);
            SkillTreeCompat.init();
        }
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, BlightcallerCommands::onRegisterCommands);
        LOGGER.info("Blightcaller {} loaded", modContainer.getModInfo().getVersion());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
