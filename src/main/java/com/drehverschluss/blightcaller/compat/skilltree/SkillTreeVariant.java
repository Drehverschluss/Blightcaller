package com.drehverschluss.blightcaller.compat.skilltree;

import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import net.neoforged.fml.ModList;

/**
 * Which variant of the Blightcaller skills the config asks for ({@code [skill_tree] mode}). Whether the branch really is in
 * the Class Skills tab decides {@link ClassBranchHook} while Pufferfish's Skills loads (fallback: own tab). Free of Skill Tree
 * and Pufferfish's Skills classes.
 */
public final class SkillTreeVariant {
    /** More RPG Classes - Skill Tree: replaces the Class Skills tab of Skill Tree with its own circle. */
    public static final String MORE_RPG_CLASSES = "mrpgc_skill_tree";

    /**
     * @param classTree try the Class Skills tab
     * @param mode      the configured mode, for the log
     * @param reason    for the log
     * @param respace   layout respace (otherwise gap)
     */
    public record Choice(boolean classTree, String mode, String reason, boolean respace) {
        public String layout() {
            return respace ? "respace" : "gap";
        }
    }

    private SkillTreeVariant() {}

    public static boolean moreRpgClasses() {
        return ModList.get().isLoaded(MORE_RPG_CLASSES);
    }

    public static Choice choose() {
        String mode = BlightcallerConfig.get(BlightcallerConfig.SKILL_TREE_MODE);
        boolean respace = !"gap".equals(BlightcallerConfig.get(BlightcallerConfig.SKILL_TREE_CLASS_TREE_LAYOUT));
        if ("own_tab".equals(mode)) {
            return new Choice(false, mode, "mode own_tab", respace);
        }
        if ("class_tree".equals(mode)) {
            return new Choice(true, mode, moreRpgClasses() ? "circle of More RPG Classes - Skill Tree" : "circle of Skill Tree", respace);
        }
        if (moreRpgClasses()) {
            return new Choice(false, "auto", "mode auto, More RPG Classes - Skill Tree is installed", respace);
        }
        return new Choice(true, "auto", "circle of Skill Tree", respace);
    }
}
