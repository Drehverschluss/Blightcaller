package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.spell_engine.api.entity.SpellEngineAttributes;
import org.jetbrains.annotations.Nullable;

/**
 * Enfeeble / Entkräftung: a curse without damage. The target takes more damage from every source (melee, spells, curse
 * ticks of everyone) through Spell Engine's attribute {@code spell_engine:damage_taken} (base 100; Spell Engine multiplies
 * every hit at the start of {@code LivingEntity.hurt} with value / 100).
 * <p>
 * <b>Amplifier = strength:</b> bonus = (amplifier + 1) x {@code enfeeble_bonus_per_level} (server config, 0.05), half of it
 * on targets in {@code #blightcaller:curse_resistant} (bosses). The spell sets amplifier 1: +10 %, bosses +5 %. Since phase 7A
 * the boss half comes from here, not from a second amplifier in the spell, so modifiers with {@code effect_amplifier_add}
 * (skill tree: Deep Frailty, +1 = +15 % / bosses +7.5 %) keep the boss rule. Pack makers change the strength in the spell
 * (amplifier) or in the config, and the boss rule in the tag.
 * <p>
 * <b>Several casters:</b> one modifier per target, so its bonus counts once, however many Blightcallers put Enfeeble on it
 * (each still has their own entry for the duration).
 * <p>
 * The modifier comes from the curse core ({@link #applyModifiers}), also on targets that refuse the effect (Wither). It is
 * still reported in {@link #createModifiers}: that is only read for display and by Spell Engine's effect classification
 * (damage taken is not movement impairing).
 */
public class EnfeebleEffect extends CurseEffect {
    public static final ResourceLocation MODIFIER_ID = Blightcaller.id("enfeeble");

    public EnfeebleEffect(int color) {
        super(color, false);
    }

    /** Damage taken bonus of an Enfeeble effect with this amplifier on a normal target, as a fraction (0.1 = +10 %). */
    public static double bonus(int amplifier) {
        return (amplifier + 1) * BlightcallerConfig.get(BlightcallerConfig.ENFEEBLE_BONUS_PER_LEVEL);
    }

    /** The bonus on a target in {@code #blightcaller:curse_resistant} (bosses): half. */
    public static double bonus(int amplifier, boolean resistant) {
        return resistant ? bonus(amplifier) * 0.5 : bonus(amplifier);
    }

    public static boolean resistant(LivingEntity target) {
        return target.getType().is(CurseEffects.CURSE_RESISTANT);
    }

    /** Amplifier 1: +10 %, bosses +5 % (the half comes from {@link #bonus(int, boolean)}). */
    @Override
    public int defaultAmplifier(LivingEntity target) {
        return 1;
    }

    @Nullable
    private static Holder<Attribute> damageTaken() {
        return SpellEngineAttributes.DAMAGE_TAKEN.entry;
    }

    private static AttributeModifier modifier(int amplifier, boolean resistant) {
        return new AttributeModifier(MODIFIER_ID, bonus(amplifier, resistant), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @Override
    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> output) {
        super.createModifiers(amplifier, output);
        Holder<Attribute> attribute = damageTaken();
        if (attribute != null) {
            output.accept(attribute, modifier(amplifier, false));
        }
    }

    @Override
    public boolean hasModifiers() {
        return true;
    }

    @Override
    public void applyModifiers(LivingEntity target, int amplifier) {
        AttributeInstance instance = instance(target);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
            instance.addTransientModifier(modifier(amplifier, resistant(target)));
        }
    }

    @Override
    public void removeModifiers(LivingEntity target) {
        AttributeInstance instance = instance(target);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
        }
    }

    @Nullable
    private static AttributeInstance instance(LivingEntity target) {
        Holder<Attribute> attribute = damageTaken();
        return attribute == null ? null : target.getAttributes().getInstance(attribute);
    }
}
