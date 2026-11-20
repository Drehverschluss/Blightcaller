package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.curse.EnfeebleEffect;
import com.drehverschluss.blightcaller.curse.SkillMarkers;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.tooltip.TooltipTokens;

/**
 * Own tooltip tokens of the skill node spells (phase 7A), so the texts show the numbers of the data pack and the server
 * config instead of fixed ones. Registered after joining a world (spells come from the server), only for spells with one of
 * the skill markers ({@link SkillMarkers}) or a modifier of Enfeeble with {@code effect_amplifier_add}:
 * <ul>
 *     <li>{@code {migration_range}}, {@code {migration_jumps}}: Soul Migration ({@code [soul_migration]} of the server config);</li>
 *     <li>{@code {rot_per_tick}}, {@code {rot_max}}, {@code {rot_ticks}}: Deepening Rot;</li>
 *     <li>{@code {spread_range}}: Spreading Rot;</li>
 *     <li>{@code {enervation}}, {@code {enervation_boss}}: Enervation;</li>
 *     <li>{@code {frailty}}, {@code {frailty_boss}}, {@code {frailty_base}}: Deep Frailty (bonus with and without the node).</li>
 * </ul>
 * Percent signs: like {@code CurseTooltips}, a single {@code %} never directly followed by a letter.
 */
public final class SkillNodeTooltips {
    private static final ResourceLocation ENFEEBLE = Blightcaller.id("enfeeble");

    private SkillNodeTooltips() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> scan());
    }

    private static void scan() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        SpellRegistry.stream(level).forEach(entry -> {
            ResourceLocation id = entry.key().location();
            Spell spell = entry.value();
            if (!Blightcaller.MODID.equals(id.getNamespace()) || !relevant(spell)) {
                return;
            }
            TooltipTokens.registerCustom(id, args -> {
                try {
                    return fill(args.description(), args.spellEntry().value());
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: skill node tooltip of {} failed", id, e);
                    return args.description();
                }
            });
        });
    }

    private static boolean relevant(Spell spell) {
        if (spell.type == Spell.Type.MODIFIER && spell.modifiers != null) {
            for (Spell.Modifier modifier : spell.modifiers) {
                if (ENFEEBLE.toString().equals(modifier.spell_pattern) && modifier.effect_amplifier_add != 0) {
                    return true;
                }
                for (Spell.Impact impact : modifier.impacts) {
                    if (ours(SkillMarkers.handler(impact))) {
                        return true;
                    }
                }
            }
        }
        for (Spell.Impact impact : spell.impacts) {
            if (ours(SkillMarkers.handler(impact))) {
                return true;
            }
        }
        return false;
    }

    private static boolean ours(String handler) {
        return handler != null && (handler.equals(SkillMarkers.SOUL_MIGRATION) || handler.startsWith(SkillMarkers.DEEPENING_ROT)
                || handler.startsWith(SkillMarkers.SPREADING_ROT) || handler.startsWith(SkillMarkers.ENERVATION));
    }

    static String fill(String text, Spell spell) {
        text = text.replace("{migration_range}", number(BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_RANGE)))
                .replace("{migration_jumps}", Integer.toString(BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_MAX_JUMPS)));
        java.util.List<Spell.Impact> impacts = new java.util.ArrayList<>(spell.impacts);
        int amplifierAdd = 0;
        if (spell.modifiers != null) {
            for (Spell.Modifier modifier : spell.modifiers) {
                impacts.addAll(modifier.impacts);
                if (ENFEEBLE.toString().equals(modifier.spell_pattern)) {
                    amplifierAdd += modifier.effect_amplifier_add;
                }
            }
        }
        for (Spell.Impact impact : impacts) {
            String handler = SkillMarkers.handler(impact);
            if (handler == null) {
                continue;
            }
            if (handler.startsWith(SkillMarkers.DEEPENING_ROT)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.DEEPENING_ROT);
                if (v != null && v.length >= 2 && v[0] > 0.0) {
                    text = text.replace("{rot_per_tick}", percent(v[0])).replace("{rot_max}", percent(v[1]))
                            .replace("{rot_ticks}", Integer.toString((int) Math.ceil(v[1] / v[0] - 1.0E-9) + 1));
                }
            } else if (handler.startsWith(SkillMarkers.SPREADING_ROT)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.SPREADING_ROT);
                if (v != null && v.length >= 1) {
                    text = text.replace("{spread_range}", number(v[0]));
                }
            } else if (handler.startsWith(SkillMarkers.ENERVATION)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.ENERVATION);
                if (v != null && v.length >= 1) {
                    text = text.replace("{enervation}", percent(v[0])).replace("{enervation_boss}", percent(v[0] * 0.5));
                }
            }
        }
        if (amplifierAdd != 0) {
            // the amplifier of Enfeeble in its spell is 1 (see enfeeble.json)
            text = text.replace("{frailty_base}", percent(EnfeebleEffect.bonus(1, false)))
                    .replace("{frailty}", percent(EnfeebleEffect.bonus(1 + amplifierAdd, false)))
                    .replace("{frailty_boss}", percent(EnfeebleEffect.bonus(1 + amplifierAdd, true)));
        }
        return text;
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.1f", value);
        // decimal comma in German, like the curse tooltips
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        return language != null && language.startsWith("de") ? text.replace('.', ',') : text;
    }

    private static String percent(double fraction) {
        return number(fraction * 100.0) + "%";
    }
}
