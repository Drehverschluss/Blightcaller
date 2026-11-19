package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.spell_engine.api.entity.SpellEngineAttributes;
import org.jetbrains.annotations.Nullable;

/**
 * Enfeeble / Entkräftung: a curse without damage. The target takes more damage from every source (melee, spells, curse
 * ticks of everyone) through Spell Engine's attribute {@code spell_engine:damage_taken} (base 100; Spell Engine multiplies
 * every hit at the start of {@code LivingEntity.hurt} with value / 100).
 * <p>
 * <b>Amplifier = strength:</b> bonus = (amplifier + 1) x {@code enfeeble_bonus_per_level} (server config, 0.05). The spell
 * sets amplifier 1 (+10 %) on normal targets and amplifier 0 (+5 %) on targets in {@code #blightcaller:curse_resistant}
 * (bosses), with two status effect impacts that allow / deny that tag. Pack makers change the strength in the spell
 * (amplifier) or in the config, and the boss rule in the tag.
 * <p>
 * <b>Several casters:</b> there is only one effect per target, so its bonus counts once, however many Blightcallers put
 * Enfeeble on it (each still has their own entry for the duration).
 * <p>
 * The modifier is added and removed here instead of with {@code addAttributeModifier} in the constructor: Spell Engine
 * registers its attributes after the mob effects, so the attribute holder does not exist yet when this effect is created.
 */
public class EnfeebleEffect extends CurseEffect {
    public static final ResourceLocation MODIFIER_ID = Blightcaller.id("enfeeble");

    public EnfeebleEffect(int color) {
        super(color, false);
    }

    /** Damage taken bonus of an Enfeeble effect with this amplifier, as a fraction (0.1 = +10 %). */
    public static double bonus(int amplifier) {
        return (amplifier + 1) * BlightcallerConfig.get(BlightcallerConfig.ENFEEBLE_BONUS_PER_LEVEL);
    }

    /** Normal targets amplifier 1 (+10 %), curse resistant ones (bosses) amplifier 0 (+5 %). */
    @Override
    public int defaultAmplifier(LivingEntity target) {
        return target.getType().is(CurseEffects.CURSE_RESISTANT) ? 0 : 1;
    }

    @Nullable
    private static Holder<Attribute> damageTaken() {
        return SpellEngineAttributes.DAMAGE_TAKEN.entry;
    }

    private static AttributeModifier modifier(int amplifier) {
        return new AttributeModifier(MODIFIER_ID, bonus(amplifier), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @Override
    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> output) {
        super.createModifiers(amplifier, output);
        Holder<Attribute> attribute = damageTaken();
        if (attribute != null) {
            output.accept(attribute, modifier(amplifier));
        }
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(attributeMap, amplifier);
        Holder<Attribute> attribute = damageTaken();
        AttributeInstance instance = attribute == null ? null : attributeMap.getInstance(attribute);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
            instance.addPermanentModifier(modifier(amplifier));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        Holder<Attribute> attribute = damageTaken();
        AttributeInstance instance = attribute == null ? null : attributeMap.getInstance(attribute);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
