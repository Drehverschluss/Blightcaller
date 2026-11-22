package com.drehverschluss.blightcaller.weapon;

import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.spell_engine.entity.SpellCloud;

/**
 * Patch of Grave Mould on the ground (passive of Gravemother's Herbarium, spell {@code blightcaller:grave_mould}): a Spell Engine
 * {@link SpellCloud}. Timing, radius, impacts (Soulburn) and the particles all come from the spell. The only thing this class adds
 * is the limit per player: when a patch becomes active and its owner has more than {@code [grave_mould] max_patches} active
 * patches, the oldest one withers away (short despawn, no more pulses).
 * <p>
 * Spells use it with {@code "entity_type_id": "blightcaller:grave_mould"} in a CLOUD delivery.
 */
public class GraveMouldEntity extends SpellCloud {
    /** Despawn time of a patch that has to make room for a new one (ticks). */
    private static final int WITHER_TICKS = 10;

    /** Active patches per owner, oldest first. Server thread only. */
    private static final Map<UUID, ArrayDeque<GraveMouldEntity>> PATCHES = new HashMap<>();

    private boolean counted;

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

    /** Active patches of a player (for tests and the command line). */
    public static int activeCount(UUID owner) {
        ArrayDeque<GraveMouldEntity> patches = PATCHES.get(owner);
        if (patches == null) {
            return 0;
        }
        return (int) patches.stream().filter(patch -> !patch.isRemoved() && !patch.isDespawning()).count();
    }

    /** Forgets everything (server stopped). */
    static void clear() {
        PATCHES.clear();
    }
}
