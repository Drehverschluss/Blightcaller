package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Gravebind / Grabesfessel: a curse without damage. Ghostly chains hold the target: it loses part of its movement speed.
 * <p>
 * <b>Amplifier = strength:</b> slow = (amplifier + 1) x {@code gravebind_slow_per_level} (server config, 0.3), at most
 * {@value #MAX_SLOW}. The spell sets amplifier 1 (60 % slower) on normal targets and amplifier 0 (30 % slower) on targets
 * in {@code #blightcaller:curse_resistant} (bosses), with two status effect impacts that allow / deny that tag, the same
 * pattern as {@link EnfeebleEffect}.
 * <p>
 * <b>On the ground</b> the modifier on {@code minecraft:generic.movement_speed} does it (mobs walk with the square of that
 * value, Phase 5 report 4.1). <b>In the air</b> most flyers ignore attributes (Wither, Ghast, Phantom and the Ender Dragon
 * steer their velocity themselves), so {@link GravebindFlight} shortens the way they cover each tick instead.
 * <p>
 * <b>Bosses:</b> Spell Engine does not apply status effects that it counts as movement impairing to entities in
 * {@code #spell_engine:bosses} at all. It decides that once at server start from {@code createModifiers} (a negative
 * modifier on movement speed, flying speed or gravity). This effect reports nothing there; the modifier comes from the
 * curse core ({@link #applyModifiers}), so bosses get the half slow from {@code #blightcaller:curse_resistant} instead of
 * none, also when they refuse the effect itself (Wither).
 * <p>
 * <b>Several casters:</b> one modifier per target, so the slow counts once (each caster keeps an entry for the duration).
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
    public boolean hasModifiers() {
        return true;
    }

    @Override
    public void applyModifiers(LivingEntity target, int amplifier) {
        AttributeInstance instance = target.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
            instance.addTransientModifier(modifier(amplifier));
        }
    }

    @Override
    public void removeModifiers(LivingEntity target) {
        AttributeInstance instance = target.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
