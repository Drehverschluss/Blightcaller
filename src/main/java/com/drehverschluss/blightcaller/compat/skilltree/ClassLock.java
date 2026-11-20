package com.drehverschluss.blightcaller.compat.skilltree;

import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.api.SkillsAPI;

/**
 * Class lock of the own Blightcaller tab: the Blightcaller tab counts as a class next to the tabs in {@code class_lock_tabs}
 * (default: Class Skills of Skill Tree, the own Stormcaller tab). A player with a class in one of them cannot take the
 * Blightcaller root, and a player on the Blightcaller path cannot take a root in them. Only pairs with the Blightcaller tab:
 * the lock between Stormcaller and the Class Skills tab stays Stormcaller's own (with its own switch). Both directions with
 * Stormcaller's own tab are covered here, since Stormcaller does not know the Blightcaller tab.
 * <p>
 * Pufferfish's Skills has no event that can cancel an unlock. So the unlock event undoes it right away through the public API
 * ({@code Skill.lock}): the point comes back, the rewards are removed again. The player gets a toast. The skill screen shows the
 * blocked roots as locked ({@code ClientCategoryDataMixin}).
 * <p>
 * "Has a class" = has an unlocked node in that tab (the first node of a tab is always its root). Our own nodes kept in another
 * tab (Blightcaller nodes saved in the Class Skills tab during a fallback) are no other class; Stormcaller nodes there are.
 */
public final class ClassLock {
    /** Last state sent per player. */
    private static final Map<UUID, ClassLockPayload> SENT = new HashMap<>();

    private ClassLock() {}

    static void init() {
        SkillsAPI.registerSkillUnlockEvent(ClassLock::onUnlock);
        SkillsAPI.registerSkillLockEvent((player, categoryId, skillId) -> {
            if (involved(categoryId)) {
                sync(player, ClassLockPayload.TOAST_NONE, true);
            }
        });
        // login, config changes, commands, other mods: compare once per second, send on change
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
                sync(player, ClassLockPayload.TOAST_NONE, false);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SENT.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> SENT.clear());
    }

    /** Switched on in the config and the own Blightcaller tab is loaded (in the Class Skills tab exclusive_root does it). */
    public static boolean enabled() {
        return BlightcallerConfig.get(BlightcallerConfig.SKILL_TREE_CLASS_LOCK) && BranchTransition.ownTabActive();
    }

    /** The other class tabs of the lock (config, without our own tab). */
    static List<ResourceLocation> otherTabs() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (String text : BlightcallerConfig.classLockTabs()) {
            ResourceLocation id = ResourceLocation.tryParse(text);
            if (id != null && !id.equals(SkillTab.BLIGHTCALLER_CATEGORY)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    private static boolean involved(ResourceLocation categoryId) {
        return SkillTab.BLIGHTCALLER_CATEGORY.equals(categoryId) || otherTabs().contains(categoryId);
    }

    private static void onUnlock(ServerPlayer player, ResourceLocation categoryId, String skillId) {
        if (!involved(categoryId)) {
            return;
        }
        int toast = ClassLockPayload.TOAST_NONE;
        if (enabled() && unlockedCount(player, categoryId) == 1 && blockedBy(player, categoryId)) {
            // the first node of this tab (its root) while a tab on the other side already has a class: undo
            Skill skill = SkillsAPI.getCategory(categoryId).flatMap(category -> category.getSkill(skillId)).orElse(null);
            if (skill != null && skill.getState(player) == Skill.State.UNLOCKED) {
                skill.lock(player);
                toast = SkillTab.BLIGHTCALLER_CATEGORY.equals(categoryId) ? ClassLockPayload.TOAST_BLIGHTCALLER : ClassLockPayload.TOAST_CLASS;
            }
        }
        sync(player, toast, true);
    }

    /** Whether a root in this tab is blocked by a class on the other side of the lock. */
    private static boolean blockedBy(ServerPlayer player, ResourceLocation categoryId) {
        if (SkillTab.BLIGHTCALLER_CATEGORY.equals(categoryId)) {
            return otherTabs().stream().anyMatch(other -> unlockedCount(player, other) > 0);
        }
        return unlockedCount(player, SkillTab.BLIGHTCALLER_CATEGORY) > 0;
    }

    /** Sends the lock state if it changed (or always with a toast or {@code force}). */
    private static void sync(ServerPlayer player, int toast, boolean force) {
        boolean enabled = enabled();
        List<ResourceLocation> others = otherTabs();
        boolean ownBlocked = others.stream().anyMatch(other -> unlockedCount(player, other) > 0);
        List<ResourceLocation> blocked = unlockedCount(player, SkillTab.BLIGHTCALLER_CATEGORY) > 0 ? others : List.of();
        ClassLockPayload state = new ClassLockPayload(enabled, ownBlocked, blocked, BranchTransition.classBranchActive(), toast);
        ClassLockPayload last = SENT.get(player.getUUID());
        if (!force && toast == ClassLockPayload.TOAST_NONE && state.equals(last)) {
            return;
        }
        SENT.put(player.getUUID(), state.withoutToast());
        PacketDistributor.sendToPlayer(player, state);
    }

    /** Unlocked nodes of a tab; our own nodes saved in another tab (during a fallback) are no other class. */
    static long unlockedCount(ServerPlayer player, ResourceLocation categoryId) {
        boolean own = SkillTab.BLIGHTCALLER_CATEGORY.equals(categoryId);
        return SkillsAPI.getCategory(categoryId).map(category -> countUnlocked(category, player, own)).orElse(0L);
    }

    private static long countUnlocked(Category category, ServerPlayer player, boolean own) {
        return category.streamUnlockedSkills(player).filter(skill -> own || !skill.getId().startsWith(ClassBranchHook.PREFIX)).count();
    }

    /** For tests: the tabs that block the Blightcaller root for this player right now. */
    public static List<ResourceLocation> blockingTabs(ServerPlayer player) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation other : otherTabs()) {
            if (unlockedCount(player, other) > 0) {
                out.add(other);
            }
        }
        return out;
    }
}
