package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

/**
 * Damage of curse ticks.
 * <ul>
 *     <li>Own damage type {@code blightcaller:curse}, only in {@code #spell_power:all} (inherits the magic tags: armor and
 *     shields do not help, spell resistance and magic protection do) and {@code #minecraft:no_knockback}. Listeners that
 *     react to a spell school's damage type, Spell Engine passives, Relics and spell leech do not see the ticks.</li>
 *     <li>Indirect source with the caster as causer ({@link CurseDamageSource}). The caster is looked up by UUID on every
 *     tick (player list first, then the target's level), never kept as a reference. Not found: the tick deals its
 *     damage without a causer.</li>
 *     <li>Invulnerability: {@code invulnerableTime} and {@code lastHurt} are saved, set to 0 and restored after the hit.
 *     So a tick never swallows a hit inside the invulnerability window and is never swallowed by one.</li>
 * </ul>
 */
public final class CurseDamage {
    public static final ResourceKey<DamageType> CURSE = ResourceKey.create(Registries.DAMAGE_TYPE, Blightcaller.id("curse"));

    private CurseDamage() {}

    /**
     * Damage one tick of this entry deals to the target right now: power x factor (x critical damage if the cast was
     * critical) x soul vulnerability of the target, read fresh on every call. Also used by Last Rites (later phase).
     */
    public static double amount(LivingEntity target, CurseEntry entry) {
        SpellPower.Vulnerability vulnerability = SpellPower.getVulnerability(target, SpellSchools.SOUL);
        double value = entry.power * entry.factor * (1.0 + vulnerability.powerBaseMultiplier());
        if (entry.critical) {
            value *= entry.criticalDamage + vulnerability.criticalDamageBonus();
        }
        return Math.max(0.0, value);
    }

    /**
     * Deals one tick of this entry to the target, scaled by {@code share} (1 = a full tick, less for the broken last
     * interval, up to 2 when a full tick and the last share fall into the same game tick). Returns whether the hit landed.
     */
    public static boolean tick(LivingEntity target, CurseEntry entry, double share) {
        return tick(target, entry, share, 1);
    }

    /**
     * Like {@link #tick(LivingEntity, CurseEntry, double)}, as the {@code number}-th tick of the entry on this target: with
     * Deepening Rot (skill tree) the caster's ticks grow stronger with every tick ({@link DeepeningRot}), with Coup de Grace they hit
     * harder on targets with low health ({@link SkillMarkers#coupDeGrace}).
     */
    public static boolean tick(LivingEntity target, CurseEntry entry, double share, int number) {
        if (!(target.level() instanceof ServerLevel level) || target.isDeadOrDying()) {
            return false;
        }
        LivingEntity caster = resolveCaster(level, entry.caster);
        // Deepening Rot and Coup de Grace multiply (decided 20.11.)
        float amount = (float) (amount(target, entry) * share * (1.0 + DeepeningRot.bonus(caster, number))
                * (1.0 + SkillMarkers.coupDeGrace(caster, target)));
        if (amount <= 0.0F) {
            return false;
        }
        return hurt(target, new CurseDamageSource(type(level), caster), amount);
    }

    /** Hurts the target without touching its invulnerability window (save, reset, hit, restore). */
    public static boolean hurt(LivingEntity target, CurseDamageSource source, float amount) {
        int invulnerableTime = target.invulnerableTime;
        float lastHurt = target.lastHurt;
        target.invulnerableTime = 0;
        target.lastHurt = 0.0F;
        try {
            return target.hurt(source, amount);
        } finally {
            target.invulnerableTime = invulnerableTime;
            target.lastHurt = lastHurt;
        }
    }

    public static Holder<DamageType> type(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CURSE);
    }

    /**
     * The caster as an entity, or {@code null} if there is none or they are not online / not loaded. Players are found
     * in every dimension and again after death and respawn.
     */
    @Nullable
    public static LivingEntity resolveCaster(ServerLevel level, @Nullable UUID caster) {
        if (caster == null) {
            return null;
        }
        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(caster);
        if (player != null) {
            return player.isRemoved() ? null : player;
        }
        Entity entity = level.getEntity(caster);
        return entity instanceof LivingEntity living && !living.isRemoved() ? living : null;
    }
}
