package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Gravebind / Grabesfessel: a curse without damage. Ghostly hands hold the target: it loses part of its movement speed.
 * <p>
 * <b>Amplifier = strength:</b> slow = (amplifier + 1) x {@code gravebind_slow_per_level} (server config, 0.3), at most
 * {@value #MAX_SLOW}. The spell sets amplifier 1 (60 % slower) on normal targets and amplifier 0 (30 % slower) on targets
 * in {@code #blightcaller:curse_resistant} (bosses), with two status effect impacts that allow / deny that tag, the same
 * pattern as {@link EnfeebleEffect}.
 * <p>
 * <b>Bosses:</b> Spell Engine does not apply status effects that it counts as movement impairing to entities in
 * {@code #spell_engine:bosses} at all. It decides that once at server start from {@link #createModifiers} (a negative
 * modifier on movement speed, flying speed or gravity). This effect therefore adds its modifier only in
 * {@link #addAttributeModifiers} and leaves it out of {@code createModifiers}: Spell Engine applies it to bosses too, and
 * the boss rule (half the slow) comes from {@code #blightcaller:curse_resistant}, which contains {@code #spell_engine:bosses}.
 * <p>
 * <b>Several casters:</b> one effect per target, so the slow counts once (each caster keeps an entry for the duration).
 */
public class GravebindEffect extends CurseEffect {
    public static final ResourceLocation MODIFIER_ID = Blightcaller.id("gravebind");
    /** Never slower than this, whatever the config and amplifier say (a target always keeps a little movement). */
    public static final double MAX_SLOW = 0.95;

    public GravebindEffect(int color) {
        super(color, false);
    }

    /** Movement speed lost with this amplifier, as a fraction (0.6 = 60 % slower). */
    public static double slow(int amplifier) {
        return Math.min(MAX_SLOW, (amplifier + 1) * BlightcallerConfig.get(BlightcallerConfig.GRAVEBIND_SLOW_PER_LEVEL));
    }

    /** Normal targets amplifier 1 (60 %), curse resistant ones (bosses) amplifier 0 (30 %). */
    @Override
    public int defaultAmplifier(LivingEntity target) {
        return target.getType().is(CurseEffects.CURSE_RESISTANT) ? 0 : 1;
    }

    private static AttributeModifier modifier(int amplifier) {
        return new AttributeModifier(MODIFIER_ID, -slow(amplifier), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(attributeMap, amplifier);
        AttributeInstance instance = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
            instance.addPermanentModifier(modifier(amplifier));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        AttributeInstance instance = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
