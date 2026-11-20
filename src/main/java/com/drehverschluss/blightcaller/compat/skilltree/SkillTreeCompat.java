package com.drehverschluss.blightcaller.compat.skilltree;

import net.skill_tree_rpgs.utils.SkillHelper;

/**
 * Common part of the Skill Tree (RPG Series) support. Only called when Skill Tree is loaded ({@link SkillTab#enabled}).
 * <ul>
 *     <li>Respec: the Orb of Oblivion of Skill Tree resets the categories in {@code SkillHelper.RESET_CATEGORIES};
 *     the Blightcaller tab is added there (the Class Skills tab is there already).</li>
 *     <li>Class lock with the other class tabs ({@link ClassLock}).</li>
 *     <li>Moving the nodes when the variant changes ({@link BranchTransition}).</li>
 * </ul>
 * The tab itself is data ({@link SkillTreePack}), the branch in the Class Skills tab comes from {@link ClassBranchHook}, the
 * node tooltips from the client ({@code compat.skilltree.client.SkillTreeClient}).
 */
public final class SkillTreeCompat {
    private SkillTreeCompat() {}

    public static void init() {
        if (!SkillHelper.RESET_CATEGORIES.contains(SkillTab.BLIGHTCALLER_CATEGORY)) {
            SkillHelper.RESET_CATEGORIES.add(SkillTab.BLIGHTCALLER_CATEGORY);
        }
        ClassLock.init();
        BranchTransition.init();
    }
}
