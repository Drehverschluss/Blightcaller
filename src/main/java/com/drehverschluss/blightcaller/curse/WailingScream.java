package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.resources.ResourceLocation;
import net.spell_engine.api.spell.event.SpellHandlers;

/**
 * Wailing Scream, the passive of the Wailing Skull relic ({@code data/blightcaller/spell/wailing_scream.json}): on a hit of an
 * active Blightcaller curse spell ({@code #blightcaller:curse_spells}) every enemy around the player gets Enfeeble. This is the
 * curse marker of that spell: a plain Enfeeble with the player as caster (counts for the curse level, half on bosses through
 * {@link EnfeebleEffect}, also on targets without effects), with the fixed duration of the spell (no Curse Duration bonus).
 * A longer Enfeeble of the same player is never shortened, Enervation on the target stays.
 * <p>
 * Being a passive spell, its hits trigger neither the Grave Candle nor the skull itself (their triggers only take active spells).
 */
public final class WailingScream {
    public static final String HANDLER = Blightcaller.MODID + ":wailing_scream";

    private WailingScream() {}

    public static void register() {
        SpellHandlers.registerCustomImpact(ResourceLocation.parse(HANDLER), (spell, power, caster, target, context) -> {
            try {
                return SkillMarkers.plainEnfeeble(HANDLER, false, spell, power, caster, target, context);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: Wailing Scream failed", e);
                return new SpellHandlers.ImpactResult(false, false);
            }
        });
    }
}
