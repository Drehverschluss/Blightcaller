package com.drehverschluss.blightcaller.weapon;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.spell.event.SpellHandlers;
import org.jetbrains.annotations.Nullable;

/**
 * Passives of the two unique weapons of the Gravemother (phase 8C). Both are Spell Engine passive spells in the item's spell
 * container ({@code data/blightcaller/spell/bone_spike.json}, {@code grave_mould.json}); chance, damage, cooldown, radius and
 * duration are data. This class only adds what data cannot do:
 * <ul>
 *     <li>{@code blightcaller:fx_bone_spike}: visual impact of Bone Spike, bone splinters bursting up from the ground under the
 *     target (Vanilla block particles of the bone block, sent from the server). Does nothing else.</li>
 *     <li>the entity type {@code blightcaller:grave_mould} ({@link GraveMouldEntity}, the patch of Grave Mould with the limit per
 *     player).</li>
 * </ul>
 */
public final class WeaponPassives {
    public static final ResourceLocation FX_BONE_SPIKE = Blightcaller.id("fx_bone_spike");
    public static final ResourceLocation GRAVE_MOULD_ID = Blightcaller.id("grave_mould");

    /** Built in the RegisterEvent (an entity type needs the registries open, the mod constructor runs before that). */
    public static EntityType<GraveMouldEntity> GRAVE_MOULD;

    private static final SpellHandlers.ImpactResult NO_EFFECT = new SpellHandlers.ImpactResult(false, false);

    private WeaponPassives() {}

    public static void init() {
        SpellHandlers.registerCustomImpact(FX_BONE_SPIKE, (spell, power, caster, target, context) -> boneSpike(target));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> GraveMouldEntity.clear());
    }

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            GRAVE_MOULD = EntityType.Builder
                    .<GraveMouldEntity>of(GraveMouldEntity::new, MobCategory.MISC)
                    .sized(5.0F, 0.5F)
                    .fireImmune()
                    .clientTrackingRange(64)
                    .updateInterval(20)
                    .build("grave_mould");
            Registry.register(BuiltInRegistries.ENTITY_TYPE, GRAVE_MOULD_ID, GRAVE_MOULD);
        }
    }

    private static SpellHandlers.ImpactResult boneSpike(@Nullable Entity target) {
        try {
            if (target != null && target.level() instanceof ServerLevel level) {
                double width = Math.max(0.2, target.getBbWidth() * 0.25);
                // splinters thrown up from the feet, a short column of bone crumbs and a few bits of white ash
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        target.getX(), target.getY() + 0.1, target.getZ(), 18, width, 0.05, width, 0.25);
                for (int i = 0; i < 4; i++) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                            target.getX(), target.getY() + 0.25 + i * 0.3, target.getZ(), 3, 0.08, 0.1, 0.08, 0.05);
                }
                level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + 0.4, target.getZ(), 10, width, 0.4, width, 0.02);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: Bone Spike visuals failed", e);
        }
        return NO_EFFECT;
    }
}
