package com.drehverschluss.blightcaller.curse;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.spell_engine.api.effect.Synchronized;

/**
 * Base class of all curses.
 * <p>
 * <b>The curse itself is the entries in the {@link CurseData} attachment</b>, one per caster. They tick from the curse core
 * ({@link Curses#tickAll}, one server hook per entity), whether or not the target took this effect: bosses like the Wither
 * refuse every mob effect and are cursed all the same.
 * <p>
 * <b>The mob effect is the display</b> where the target takes it: particles through Spell Engine (it only sees
 * {@link Synchronized} effects), the effect icon on players, milk and totem (removing the effect removes the curse), and
 * other mods that look for harmful effects. It lasts as long as the longest entry plus one tick and goes by itself once the
 * curse has no entries left. It never ticks damage and never adds attribute modifiers: what a curse does to its target
 * (Enfeeble, Gravebind) comes from {@link #applyModifiers}, called by the core from the entries, so it works on targets
 * without the effect too and is never applied twice.
 * <p>
 * Curse effects are applied without vanilla particles ({@code visible=false}); each curse draws its own particles on the
 * client ({@code client.CurseParticles}). Every curse must also be in the mob effect tag {@code #blightcaller:curses}.
 * <p>
 * <b>Curses without damage</b> ({@code damaging = false}, e.g. Enfeeble): they count for the curse level and keep one entry
 * per caster, but their entries never hit the target: no 0-damage hit, no red flash, no hurt sound.
 */
public class CurseEffect extends MobEffect {
    private final boolean damaging;

    /** A curse that deals soul damage over time. */
    public CurseEffect(int color) {
        this(color, true);
    }

    public CurseEffect(int color, boolean damaging) {
        super(MobEffectCategory.HARMFUL, color);
        this.damaging = damaging;
        Synchronized.configure(this, true);
    }

    /** Whether the entries of this curse deal damage on their ticks. */
    public boolean dealsDamage() {
        return damaging;
    }

    /**
     * Amplifier of this curse on the target when no spell gave one (command, copies of old saves). Spells pass the
     * amplifier of their status effect impact.
     */
    public int defaultAmplifier(LivingEntity target) {
        return 0;
    }

    /** Whether this curse changes attributes of its target ({@link #applyModifiers}). */
    public boolean hasModifiers() {
        return false;
    }

    /**
     * Puts this curse's attribute modifiers on the target with the given strength, replacing older ones of this curse.
     * Transient modifiers only: they are not saved, the core puts them on again from the entries when the entity is loaded.
     * Called by the core only ({@link CurseModifiers}).
     */
    public void applyModifiers(LivingEntity target, int amplifier) {
    }

    /** Removes this curse's attribute modifiers from the target (also stale permanent ones from older versions). */
    public void removeModifiers(LivingEntity target) {
    }

    // display only: the vanilla effect hooks must not change attributes, the core does that from the entries

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(attributeMap, amplifier);
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /**
     * Display only: keeps the effect while the curse has entries on this target, removes it ({@code false}) once it has
     * none. The damage ticks run in {@link Curses#tickAll}. On the client nothing happens.
     */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return true;
        }
        return Curses.hasEntries(entity, this);
    }
}
