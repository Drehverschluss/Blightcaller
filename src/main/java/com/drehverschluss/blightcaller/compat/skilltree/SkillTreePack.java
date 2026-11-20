package com.drehverschluss.blightcaller.compat.skilltree;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The Blightcaller skill tab is a built-in data pack ({@code resourcepacks/skill_tree} in the jar): category
 * {@code blightcaller:blightcaller} for Pufferfish's Skills with its definitions, nodes and connections, and the place of
 * the branch in the Class Skills tab ({@code blightcaller_skill_tree/class_branch.json}).
 * <p>
 * It is only added when Skill Tree (RPG Series) is loaded. Without Skill Tree there is no category at all, so
 * Pufferfish's Skills alone shows no empty tab. The pack is always active and sits at the bottom, so world data packs
 * (or KubeJS) can still replace single files of it.
 */
public final class SkillTreePack {
    private SkillTreePack() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addPackFinders(Blightcaller.id("resourcepacks/skill_tree"), PackType.SERVER_DATA,
                Component.literal("Blightcaller Skill Tab"), PackSource.BUILT_IN, true, Pack.Position.BOTTOM);
    }
}
