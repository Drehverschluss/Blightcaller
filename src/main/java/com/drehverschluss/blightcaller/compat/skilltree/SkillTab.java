package com.drehverschluss.blightcaller.compat.skilltree;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Ids of the Blightcaller skill tab and of the tabs of Skill Tree (RPG Series). Free of Skill Tree and Pufferfish's
 * Skills classes, safe to use without those mods.
 */
public final class SkillTab {
    public static final String SKILL_TREE_MOD = "skill_tree_rpgs";
    public static final String PUFFISH_MOD = "puffish_skills";

    /** The Blightcaller tab (built-in data pack, see {@link SkillTreePack}). */
    public static final ResourceLocation BLIGHTCALLER_CATEGORY = Blightcaller.id("blightcaller");
    /** Class tab of Skill Tree (RPG Series); More RPG Classes - Skill Tree puts its classes there too. */
    public static final ResourceLocation CLASS_CATEGORY = ResourceLocation.fromNamespaceAndPath(SKILL_TREE_MOD, "class_skills");

    private SkillTab() {}

    /** Skill Tree is there (it requires Pufferfish's Skills). */
    public static boolean enabled() {
        return ModList.get().isLoaded(SKILL_TREE_MOD) && ModList.get().isLoaded(PUFFISH_MOD);
    }
}
