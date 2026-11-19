package com.drehverschluss.blightcaller.attribute;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Attributes of Blightcaller.
 * <p>
 * {@code blightcaller:curse_duration} follows the layout of Spell Power's critical chance: base 100, a value of 130
 * means curses last 30 % longer. Equipment uses {@code ADD_MULTIPLIED_BASE} ("multiply"), so 0.08 shows as
 * "+8% Curse Duration". The bonus is capped by the server config ({@code max_curse_duration_bonus}).
 */
public final class BlightcallerAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Blightcaller.MODID);

    public static final DeferredHolder<Attribute, Attribute> CURSE_DURATION = ATTRIBUTES.register("curse_duration",
            () -> new RangedAttribute("attribute.name.blightcaller.curse_duration", 100.0, 100.0, 1000.0).setSyncable(true));

    private BlightcallerAttributes() {}

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(EntityAttributeModificationEvent.class, BlightcallerAttributes::onAttributeModification);
    }

    private static void onAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, CURSE_DURATION)) {
            event.add(EntityType.PLAYER, CURSE_DURATION);
        }
    }

    /**
     * Curse duration bonus of an entity as a fraction (0.3 = curses last 30 % longer), capped by the server config.
     * Entities without the attribute (and {@code null}) have 0.
     */
    public static double curseDurationBonus(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0.0;
        }
        AttributeInstance instance = entity.getAttribute(CURSE_DURATION);
        if (instance == null) {
            return 0.0;
        }
        double bonus = Math.max(0.0, instance.getValue() / 100.0 - 1.0);
        return Math.min(bonus, BlightcallerConfig.get(BlightcallerConfig.MAX_CURSE_DURATION_BONUS));
    }
}
