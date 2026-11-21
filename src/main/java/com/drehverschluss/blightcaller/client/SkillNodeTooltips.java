package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.curse.CurseImpacts;
import com.drehverschluss.blightcaller.curse.EnfeebleEffect;
import com.drehverschluss.blightcaller.curse.SkillMarkers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.tooltip.TooltipTokens;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

/**
 * Own tooltip tokens of the skill node spells (and the passive relic spells), so the texts show the numbers of the data pack and the server config instead of
 * fixed ones. Registered after joining a world (spells come from the server) for every passive and modifier spell of Blightcaller:
 * <ul>
 *     <li>{@code {migration_range}}, {@code {migration_jumps}}: Soul Migration ({@code [soul_migration]} of the server config);</li>
 *     <li>{@code {rot_per_tick}}, {@code {rot_max}}, {@code {rot_ticks}}: Deepening Rot; {@code {spread_range}}: Spreading Rot;</li>
 *     <li>{@code {enervation}}, {@code {enervation_boss}}; {@code {frailty}}, {@code {frailty_boss}}, {@code {frailty_base}};</li>
 *     <li>{@code {soulburn_damage}}, {@code {soulburn_duration}}: the Soulburn of Blightbloom, Soulfire Chains and Soul Echo (damage per
 *     second with the soul power of the player right now); {@code {bloom_range}};</li>
 *     <li>{@code {hex_duration}}, {@code {hex_cooldown}}: Vengeful Hex; {@code {infect_chance}}, {@code {infect_range}};
 *     {@code {coup_health}}, {@code {coup_bonus}}; {@code {relic_range}}: the range of a passive relic spell (Wailing Scream);</li>
 *     <li>{@code {gravebind_range}}, {@code {gravebind_base}}; {@code {carriers}}, {@code {carriers_base}};
 *     {@code {contagion_interval}}, {@code {contagion_base}}, {@code {first_spread}}, {@code {contagion_range}},
 *     {@code {spread_range_new}}, {@code {spread_range_base}};</li>
 *     <li>{@code {rites_bonus}}, {@code {rites_base}} (Improved Last Rites), {@code {focus_bonus}}, {@code {focus_base}},
 *     {@code {focus_max}}, {@code {focus_max_base}} (Hex Bolt with the wand choice): the bonus per curse level read from the target
 *     modifiers of the spell, with and without the node; {@code {burst_range}}, {@code {burst_base}}.</li>
 * </ul>
 * Percent signs: like {@code CurseTooltips}, a single {@code %} never directly followed by a letter.
 */
public final class SkillNodeTooltips {
    private static final ResourceLocation ENFEEBLE = Blightcaller.id("enfeeble");
    private static final String CURSE_LEVEL = Blightcaller.id("curse_level").toString();
    private static final String CASTER_SPELL = Blightcaller.id("caster_has_spell").toString();

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
            if (!Blightcaller.MODID.equals(id.getNamespace()) || (spell.type != Spell.Type.PASSIVE && spell.type != Spell.Type.MODIFIER)) {
                return;
            }
            TooltipTokens.registerCustom(id, args -> {
                try {
                    return fill(args.description(), id, args.spellEntry().value());
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: skill node tooltip of {} failed", id, e);
                    return args.description();
                }
            });
        });
    }

    static String fill(String text, ResourceLocation id, Spell spell) {
        if (text.indexOf('{') < 0) {
            return text;
        }
        text = text.replace("{migration_range}", number(BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_RANGE)))
                .replace("{migration_jumps}", Integer.toString(BlightcallerConfig.get(BlightcallerConfig.SOUL_MIGRATION_MAX_JUMPS)));
        List<Spell.Impact> impacts = new ArrayList<>(spell.impacts);
        int amplifierAdd = 0;
        float rangeAdd = 0.0F;
        String pattern = null;
        if (spell.modifiers != null) {
            for (Spell.Modifier modifier : spell.modifiers) {
                impacts.addAll(modifier.impacts);
                pattern = modifier.spell_pattern;
                amplifierAdd += modifier.effect_amplifier_add;
                rangeAdd += modifier.range_add;
            }
        }
        // Soulburn of an appended status effect impact (Soulfire Chains)
        float soulburnSeconds = -1.0F;
        for (Spell.Impact impact : impacts) {
            if (impact.action != null && impact.action.type == Spell.Impact.Action.Type.STATUS_EFFECT && impact.action.status_effect != null
                    && Blightcaller.id("soulburn").toString().equals(impact.action.status_effect.effect_id)) {
                soulburnSeconds = impact.action.status_effect.duration;
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
            } else if (handler.startsWith(SkillMarkers.BLIGHTBLOOM)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.BLIGHTBLOOM);
                if (v != null && v.length >= 3) {
                    text = text.replace("{bloom_range}", number(v[0])).replace("{soulburn_duration}", number(v[1]))
                            .replace("{soulburn_damage}", damage(v[2]));
                }
            } else if (handler.startsWith(SkillMarkers.SOUL_ECHO)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.SOUL_ECHO);
                if (v != null && v.length >= 2) {
                    text = text.replace("{soulburn_duration}", number(v[0])).replace("{soulburn_damage}", damage(v[1]));
                }
            } else if (handler.startsWith(SkillMarkers.INFECTIOUS_CURSES)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.INFECTIOUS_CURSES);
                if (v != null && v.length >= 2) {
                    text = text.replace("{infect_chance}", percent(v[0])).replace("{infect_range}", number(v[1]));
                }
            } else if (handler.startsWith(SkillMarkers.COUP_DE_GRACE)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.COUP_DE_GRACE);
                if (v != null && v.length >= 2) {
                    text = text.replace("{coup_health}", percent(v[0])).replace("{coup_bonus}", percent(v[1]));
                }
            } else if (handler.startsWith(SkillMarkers.RAPID_CONTAGION)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.RAPID_CONTAGION);
                if (v != null && v.length >= 1) {
                    text = text.replace("{contagion_interval}", number(v[0]))
                            .replace("{contagion_base}", number(BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_SPREAD_INTERVAL) / 20.0))
                            .replace("{first_spread}", number(BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_FIRST_SPREAD) / 20.0));
                }
            } else if (handler.startsWith(SkillMarkers.WIDE_CONTAGION)) {
                double[] v = SkillMarkers.values(handler, SkillMarkers.WIDE_CONTAGION);
                if (v != null && v.length >= 1) {
                    double base = BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_SPREAD_RADIUS);
                    text = text.replace("{contagion_range}", number(v[0])).replace("{spread_range_new}", number(base + v[0]))
                            .replace("{spread_range_base}", number(base));
                }
            } else {
                OptionalDouble factor = CurseImpacts.factorOf(handler);
                if (factor.isPresent() && soulburnSeconds >= 0.0F) {
                    text = text.replace("{soulburn_damage}", damage(factor.getAsDouble()))
                            .replace("{soulburn_duration}", number(soulburnSeconds));
                }
            }
        }
        if (ENFEEBLE.toString().equals(pattern) && amplifierAdd != 0) {
            // the amplifier of Enfeeble in its spell is 1 (see enfeeble.json)
            text = text.replace("{frailty_base}", percent(EnfeebleEffect.bonus(1, false)))
                    .replace("{frailty}", percent(EnfeebleEffect.bonus(1 + amplifierAdd, false)))
                    .replace("{frailty_boss}", percent(EnfeebleEffect.bonus(1 + amplifierAdd, true)));
        }
        // Vengeful Hex: duration of its Enfeeble and cooldown; Wailing Scream (relic): its radius (Spell Engine fills {range} only for
        // active spells)
        if (spell.type == Spell.Type.PASSIVE) {
            text = text.replace("{relic_range}", number(spell.range));
            for (Spell.Impact impact : spell.impacts) {
                if (impact.action != null && impact.action.type == Spell.Impact.Action.Type.STATUS_EFFECT && impact.action.status_effect != null) {
                    text = text.replace("{hex_duration}", number(impact.action.status_effect.duration));
                }
            }
            if (spell.cost != null && spell.cost.cooldown != null) {
                text = text.replace("{hex_cooldown}", number(spell.cost.cooldown.duration));
            }
        }
        Spell target = pattern == null ? null : spell(pattern);
        if (target != null) {
            if (rangeAdd != 0.0F) {
                text = text.replace("{gravebind_range}", number(target.range + rangeAdd)).replace("{gravebind_base}", number(target.range));
            }
            if (Blightcaller.id("pestilence").toString().equals(pattern) && amplifierAdd != 0) {
                int base = BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_MAX_CARRIERS);
                text = text.replace("{carriers}", Integer.toString(base + amplifierAdd)).replace("{carriers_base}", Integer.toString(base));
            }
            if (target.area_impact != null) {
                text = text.replace("{burst_base}", number(target.area_impact.radius));
            }
            double[] bonus = levelBonus(target, id.toString());
            if (bonus != null) {
                text = text.replace("{rites_bonus}", percent(bonus[1])).replace("{rites_base}", percent(bonus[0]))
                        .replace("{focus_bonus}", percent(bonus[1])).replace("{focus_base}", percent(bonus[0]))
                        .replace("{focus_max}", percent(bonus[1] * 3)).replace("{focus_max_base}", percent(bonus[0] * 3));
            }
        }
        if (spell.modifiers != null) {
            for (Spell.Modifier modifier : spell.modifiers) {
                // Spell Engine numbers the token when several modifiers have it ({power_multiplier_1}, ...): the first one counts
                if (modifier.power_modifier != null && modifier.power_modifier.power_multiplier != 0.0F) {
                    text = text.replace("{power_multiplier}", percent(modifier.power_modifier.power_multiplier));
                }
                if (modifier.replacing_area_impact != null) {
                    text = text.replace("{burst_range}", number(modifier.replacing_area_impact.radius));
                }
            }
        }
        return text;
    }

    /**
     * Bonus per curse level of a spell (first level): {without the node, with the node}, read from the target modifiers on
     * {@code blightcaller:curse_level} 1; those that also need {@code blightcaller:caster_has_spell} with this node count only for
     * the second value. {@code null} if the spell has none for this node.
     */
    @Nullable
    private static double[] levelBonus(Spell spell, String node) {
        double base = 0.0;
        double extra = 0.0;
        boolean found = false;
        for (Spell.Impact impact : spell.impacts) {
            if (impact.target_modifiers == null) {
                continue;
            }
            for (Spell.Impact.TargetModifier modifier : impact.target_modifiers) {
                if (modifier.modifier == null || modifier.conditions == null) {
                    continue;
                }
                boolean first = false;
                String needs = null;
                for (Spell.TargetCondition condition : modifier.conditions) {
                    if (CURSE_LEVEL.equals(condition.entity_predicate_id) && "1".equals(String.valueOf(condition.entity_predicate_param).trim())) {
                        first = true;
                    } else if (CASTER_SPELL.equals(condition.entity_predicate_id)) {
                        needs = condition.entity_predicate_param;
                    }
                }
                if (!first) {
                    continue;
                }
                if (needs == null) {
                    base += modifier.modifier.power_multiplier;
                } else if (node.equals(needs.trim())) {
                    extra += modifier.modifier.power_multiplier;
                    found = true;
                }
            }
        }
        return found ? new double[]{base, base + extra} : null;
    }

    @Nullable
    private static Spell spell(String id) {
        var level = Minecraft.getInstance().level;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (level == null || key == null) {
            return null;
        }
        return level.registryAccess().registry(SpellRegistry.KEY).map(r -> r.get(key)).orElse(null);
    }

    /** Soul damage per second of a curse factor with the player's soul power right now (without vulnerability). */
    private static String damage(double factor) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return number(factor);
        }
        double power = SpellPower.getSpellPower(SpellSchools.SOUL, player).baseValue();
        int interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
        return number(Math.round(power * factor * 20.0 / interval * 10.0) / 10.0);
    }

    private static String number(double value) {
        value = Math.round(value * 1000.0) / 1000.0;
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
