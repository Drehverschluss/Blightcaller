package com.drehverschluss.blightcaller.weapon;

import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.curse.SkillMarkers;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellCloud;
import org.jetbrains.annotations.Nullable;

/**
 * Patch of Grave Mould on the ground (passive of Gravemother's Herbarium, spell {@code blightcaller:grave_mould}): a Spell Engine
 * {@link SpellCloud}. Timing, radius, impacts (Soulburn) and the particles all come from the spell. The only thing this class adds
 * is the limit per player: when a patch becomes active and its owner has more than {@code [grave_mould] max_patches} active
 * patches, the oldest one withers away (short despawn, no more pulses).
 * <p>
 * <b>Rot in the patch</b> (phase 9B): with the marker {@code blightcaller:mould_rot/<bonus>} in the spell's impacts, the owner's own
 * curses tick that much stronger on enemies inside an active patch ({@link #boost}): only the owner's curses, several patches do not
 * add up (the strongest counts), it ends with the patch, bosses take it in full. Last Rites are not affected (only ticks).
 * <p>
 * Spells use it with {@code "entity_type_id": "blightcaller:grave_mould"} in a CLOUD delivery.
 */
public class GraveMouldEntity extends SpellCloud {
    /** Despawn time of a patch that has to make room for a new one (ticks). */
    private static final int WITHER_TICKS = 10;

    /** Active patches per owner, oldest first. Server thread only. */
    private static final Map<UUID, ArrayDeque<GraveMouldEntity>> PATCHES = new HashMap<>();

    private boolean counted;
    /** Bonus of the owner's curse ticks inside this patch (marker {@code mould_rot/<bonus>}), read when the patch is counted. */
    private double boost;

    public GraveMouldEntity(EntityType<? extends SpellCloud> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!counted && !isRemoved() && !level().isClientSide()) {
            counted = true;
            refreshDimensions();
            count();
        }
    }

    /** Registers this patch for its owner and lets the oldest ones wither away above the limit. */
    private void count() {
        Entity owner = getOwner();
        if (owner == null) {
            return;
        }
        boost = boostOf(getSpellEntry());
        ArrayDeque<GraveMouldEntity> patches = PATCHES.computeIfAbsent(owner.getUUID(), uuid -> new ArrayDeque<>());
        patches.removeIf(patch -> patch.isRemoved() || patch.isDespawning());
        patches.addLast(this);
        int max = Math.max(1, BlightcallerConfig.get(BlightcallerConfig.GRAVE_MOULD_MAX_PATCHES));
        while (patches.size() > max) {
            GraveMouldEntity oldest = patches.pollFirst();
            if (oldest != null && oldest != this) {
                oldest.beginDespawn(WITHER_TICKS);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide()) {
            PATCHES.values().forEach(patches -> patches.remove(this));
            PATCHES.values().removeIf(ArrayDeque::isEmpty);
        }
    }

    /** The number of the first {@code mould_rot/<bonus>} marker in the spell's impacts, 0 without one. */
    private static double boostOf(@Nullable Holder<Spell> spell) {
        if (spell == null || spell.value().impacts == null) {
            return 0.0;
        }
        for (Spell.Impact impact : spell.value().impacts) {
            String handler = SkillMarkers.handler(impact);
            if (handler != null && handler.startsWith(SkillMarkers.MOULD_ROT)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.MOULD_ROT);
                if (v != null && v.length >= 1) {
                    return v[0];
                }
            }
        }
        return 0.0;
    }

    /**
     * Bonus of a curse tick of this caster on the target from the caster's own patches: the largest bonus of an active patch
     * (not spawning, not withering) whose radius reaches the target's center, the same test Spell Engine uses for the pulses.
     * 0 if there is none. Server thread only.
     */
    public static double boost(@Nullable UUID caster, LivingEntity target) {
        if (caster == null || PATCHES.isEmpty()) {
            return 0.0;
        }
        ArrayDeque<GraveMouldEntity> patches = PATCHES.get(caster);
        if (patches == null) {
            return 0.0;
        }
        Vec3 center = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
        double best = 0.0;
        for (GraveMouldEntity patch : patches) {
            if (patch.boost <= best || patch.isRemoved() || !patch.isActive() || patch.level() != target.level()) {
                continue;
            }
            double radius = patch.getBbWidth() / 2.0;
            if (center.distanceToSqr(patch.position()) <= radius * radius) {
                best = patch.boost;
            }
        }
        return best;
    }

    /** Forgets everything (server stopped). */
    static void clear() {
        PATCHES.clear();
    }
}
