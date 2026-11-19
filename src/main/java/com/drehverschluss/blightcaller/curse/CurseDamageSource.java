package com.drehverschluss.blightcaller.curse;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Damage source of a curse tick: indirect, the caster is the causing entity but not the direct attacker (kill credit
 * and experience go to the caster; effects that break on a direct hit, like Frozen, stay).
 * <p>
 * Own death message, because vanilla uses the same key with one argument (no causer) and with two arguments (causer)
 * for one damage type:
 * <ul>
 *     <li>{@code death.attack.blightcaller.curse}: "%1$s ..." (no caster, nobody else hit the victim)</li>
 *     <li>{@code death.attack.blightcaller.curse.player}: "%1$s ... %2$s" (the caster, or whoever has the kill credit)</li>
 * </ul>
 */
public class CurseDamageSource extends DamageSource {
    public CurseDamageSource(Holder<DamageType> type, @Nullable Entity caster) {
        super(type, null, caster);
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        String key = "death.attack." + type().msgId();
        Entity killer = getEntity();
        if (killer == null) {
            killer = victim.getKillCredit();
        }
        return killer != null
                ? Component.translatable(key + ".player", victim.getDisplayName(), killer.getDisplayName())
                : Component.translatable(key, victim.getDisplayName());
    }
}
