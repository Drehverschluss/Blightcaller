package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.attribute.BlightcallerAttributes;
import com.drehverschluss.blightcaller.config.BlightcallerConfig;
import com.drehverschluss.blightcaller.curse.CurseImpacts;
import com.drehverschluss.blightcaller.curse.CursePredicates;
import com.drehverschluss.blightcaller.curse.Curses;
import com.drehverschluss.blightcaller.curse.EnfeebleEffect;
import com.drehverschluss.blightcaller.curse.GravebindEffect;
import com.drehverschluss.blightcaller.curse.LastRites;
import com.drehverschluss.blightcaller.curse.Pestilence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.tooltip.TooltipTokens;
import net.spell_engine.internals.SpellModifiers;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

/**
 * Own lines in the description of every spell with a curse marker ({@code blightcaller:curse/<factor>}, see
 * {@link CurseImpacts}). Spell Engine only shows the base duration of a status effect; these lines show what the curse
 * really does for the player looking at the tooltip:
 * <ul>
 *     <li>curse with damage: soul damage per second with the player's current soul spell power, the duration with the
 *     player's Curse Duration (and {@code effect_duration_add} of their modifiers), and the total;</li>
 *     <li>curse without damage: Enfeeble the extra damage taken, Gravebind the slow (normal targets and bosses, from the
 *     amplifiers of the curse's status effect impacts and the server config), others (Soul Rend) only how long they last
 *     after the last hit;</li>
 *     <li>Pestilence: also how it spreads (interval, radius, carriers per cast);</li>
 *     <li>damage and Last Rites impacts with target modifiers on {@code blightcaller:curse_level}: bonus per curse level;</li>
 *     <li>Last Rites ({@link LastRites}): the base damage (x soul power of the player, every cursed enemy or only the one with the most
 *     curses) and the rule (own curses with damage, damage of the next seconds x factor).</li>
 * </ul>
 * Uses Spell Engine's custom tooltip tokens (one resolver per spell id), registered after joining a world (spells come
 * from the server). Spells that already have a resolver from another mod are left alone.
 * <p>
 * Percent signs: Spell Engine reads the description with {@code I18n.get} (so {@code %%} in the lang file becomes
 * {@code %}) and shows it with {@code Component.translatable}, which shows a text with a single {@code %} as it is. The
 * lines here therefore contain a single {@code %} only, never directly followed by a letter.
 */
public final class CurseTooltips {
    private static final String DAMAGE_KEY = "spell.tooltip.blightcaller.curse_damage";
    private static final String WEAKEN_KEY = "spell.tooltip.blightcaller.curse_weaken";
    private static final String LEVEL_KEY = "spell.tooltip.blightcaller.curse_level_bonus";
    private static final String SLOW_KEY = "spell.tooltip.blightcaller.curse_slow";
    private static final String MARK_KEY = "spell.tooltip.blightcaller.curse_mark";
    private static final String SPREAD_KEY = "spell.tooltip.blightcaller.curse_spread";
    private static final String LAST_RITES_KEY = "spell.tooltip.blightcaller.last_rites";
    private static final String LAST_RITES_BASE_KEY = "spell.tooltip.blightcaller.last_rites_base";
    private static final String LAST_RITES_BASE_SINGLE_KEY = "spell.tooltip.blightcaller.last_rites_base_single";
    private static final String CURSE_LEVEL_PREDICATE = Blightcaller.id("curse_level").toString();
    private static final String CASTER_SPELL_PREDICATE = Blightcaller.id("caster_has_spell").toString();
    private static final Set<ResourceLocation> OWN = new HashSet<>();

    private CurseTooltips() {}

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
            if (!hasCurseParts(entry.value())) {
                return;
            }
            if (OWN.contains(id) || TooltipTokens.customFor(id) == null) {
                TooltipTokens.registerCustom(id, args -> {
                    try {
                        return append(args.description(), args.spellEntry(), args.player());
                    } catch (Exception e) {
                        Blightcaller.LOGGER.error("Blightcaller: curse tooltip of {} failed", id, e);
                        return args.description();
                    }
                });
                OWN.add(id);
            }
        });
    }

    private static boolean hasCurseParts(Spell spell) {
        for (Spell.Impact impact : impactsOf(spell)) {
            if (handlerOf(impact) != null || lastRitesOf(impact) != null || levelBonus(impact) > 0.0) {
                return true;
            }
        }
        return false;
    }

    /** Appends one line per curse marker and the curse level bonus. */
    static String append(String description, Holder<Spell> entry, @Nullable Player player) {
        Spell spell = entry.value();
        List<String> lines = new ArrayList<>();

        // curse level bonus of the damage impacts (summed per level, e.g. 3 x 15 %)
        double perLevel = 0.0;
        int levels = 0;
        for (Spell.Impact impact : impactsOf(spell)) {
            if (impact.action != null && (impact.action.type == Spell.Impact.Action.Type.DAMAGE || lastRitesOf(impact) != null)) {
                Set<String> params = new HashSet<>();
                double sum = 0.0;
                for (Spell.Impact.TargetModifier modifier : impact.target_modifiers) {
                    double bonus = levelBonus(modifier, player);
                    if (bonus > 0.0) {
                        sum += bonus;
                        modifier.conditions.stream().filter(c -> CURSE_LEVEL_PREDICATE.equals(c.entity_predicate_id))
                                .forEach(c -> params.add(String.valueOf(c.entity_predicate_param)));
                    }
                }
                if (sum > 0.0) {
                    levels = Math.max(levels, params.size());
                    perLevel = Math.max(perLevel, sum / Math.max(1, params.size()));
                }
            }
        }
        if (perLevel > 0.0) {
            lines.add(I18n.get(LEVEL_KEY, percent(perLevel), Math.min(levels, Curses.MAX_CURSE_LEVEL)));
        }

        // Last Rites: the rule
        for (Spell.Impact impact : impactsOf(spell)) {
            LastRites.Values values = lastRitesOf(impact);
            if (values != null) {
                if (values.base() > 0.0) {
                    // base damage (phase 9B): base x soul power of the player right now
                    double power = player == null ? 0.0 : SpellPower.getSpellPower(SpellSchools.SOUL, player).baseValue();
                    lines.add(I18n.get(values.single() ? LAST_RITES_BASE_SINGLE_KEY : LAST_RITES_BASE_KEY, number(values.base() * power)));
                }
                lines.add(I18n.get(LAST_RITES_KEY, number(values.seconds()), number(values.factor())));
                break;
            }
        }

        // one line per curse marker
        Set<String> seen = new HashSet<>();
        for (Spell.Impact impact : impactsOf(spell)) {
            String handler = handlerOf(impact);
            if (handler == null || !seen.add(handler)) {
                continue;
            }
            String line = curseLine(entry, player, handler);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return description;
        }
        return description + " " + String.join(" ", lines);
    }

    @Nullable
    private static String curseLine(Holder<Spell> entry, @Nullable Player player, String handler) {
        Spell spell = entry.value();
        OptionalDouble factor = CurseImpacts.factorOf(handler);
        Spell.Impact statusImpact = null;
        Holder<MobEffect> curse = null;
        if (player != null) {
            CurseImpacts.CurseSource source = CurseImpacts.curseSource(player, entry, handler);
            if (source != null) {
                statusImpact = source.impact();
                curse = source.curse();
            }
        }
        if (curse == null) {
            // no player (e.g. the main menu): the last curse status effect before the marker in the spell itself
            for (Spell.Impact impact : impactsOf(spell)) {
                if (handler.equals(handlerOf(impact))) {
                    break;
                }
                Holder<MobEffect> found = curseOf(impact);
                if (found != null) {
                    statusImpact = impact;
                    curse = found;
                }
            }
        }
        if (curse == null || statusImpact == null || factor.isEmpty()) {
            return null;
        }

        // duration as the marker computes it: base + effect_duration_add of the player's modifiers, x (1 + Curse Duration)
        float seconds = statusImpact.action.status_effect.duration;
        if (player != null) {
            for (Spell.Modifier modifier : SpellModifiers.ofImpact(player, entry, statusImpact)) {
                seconds += modifier.effect_duration_add;
            }
        }
        double extended = Math.max(0.0, seconds) * (1.0 + BlightcallerAttributes.curseDurationBonus(player));
        String name = curse.value().getDisplayName().getString();

        if (!Curses.dealsDamage(curse.value())) {
            if (!(curse.value() instanceof EnfeebleEffect) && !(curse.value() instanceof GravebindEffect)) {
                return I18n.get(MARK_KEY, name, number(extended));
            }
            int normal = Integer.MIN_VALUE;
            int resistant = Integer.MAX_VALUE;
            for (Spell.Impact impact : impactsOf(spell)) {
                if (curse.equals(curseOf(impact))) {
                    normal = Math.max(normal, impact.action.status_effect.amplifier);
                    resistant = Math.min(resistant, impact.action.status_effect.amplifier);
                }
            }
            if (normal == Integer.MIN_VALUE) {
                normal = resistant = 0;
            }
            if (curse.value() instanceof GravebindEffect) {
                return I18n.get(SLOW_KEY, name, number(extended), percent(GravebindEffect.slow(normal)),
                        percent(GravebindEffect.slow(resistant)));
            }
            // bosses get half in code; modifiers (Deep Frailty) raise the amplifier of the impact
            int amplifier = normal;
            if (player != null) {
                for (Spell.Impact impact : impactsOf(spell)) {
                    if (curse.equals(curseOf(impact))) {
                        for (Spell.Modifier modifier : SpellModifiers.ofImpact(player, entry, impact, null)) {
                            amplifier += modifier.effect_amplifier_add;
                        }
                        break;
                    }
                }
            }
            return I18n.get(WEAKEN_KEY, name, number(extended), percent(EnfeebleEffect.bonus(amplifier, false)),
                    percent(EnfeebleEffect.bonus(amplifier, true)));
        }

        double power = player == null ? 0.0 : SpellPower.getSpellPower(SpellSchools.SOUL, player).baseValue();
        double interval = BlightcallerConfig.get(BlightcallerConfig.CURSE_TICK_INTERVAL);
        double perTick = power * factor.getAsDouble();
        double perSecond = perTick * 20.0 / interval;
        double total = perTick * extended * 20.0 / interval;
        String line = I18n.get(DAMAGE_KEY, name, number(perSecond), number(extended), number(total));
        if (Pestilence.isPestilence(curse)) {
            int carriers = Pestilence.maxCarriers(player, entry, statusImpact, null);
            double every = BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_SPREAD_INTERVAL) / 20.0;
            line += " " + I18n.get(SPREAD_KEY, number(every), number(BlightcallerConfig.get(BlightcallerConfig.PESTILENCE_SPREAD_RADIUS)),
                    carriers);
        }
        return line;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static List<Spell.Impact> impactsOf(Spell spell) {
        List<Spell.Impact> impacts = new ArrayList<>();
        if (spell.impacts != null) {
            impacts.addAll(spell.impacts);
        }
        if (spell.modifiers != null) {
            for (Spell.Modifier modifier : spell.modifiers) {
                if (modifier != null && modifier.impacts != null) {
                    impacts.addAll(modifier.impacts);
                }
            }
        }
        return impacts;
    }

    @Nullable
    private static String handlerOf(Spell.Impact impact) {
        if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM
                || impact.action.custom == null) {
            return null;
        }
        String handler = impact.action.custom.handler;
        return CurseImpacts.factorOf(handler).isPresent() ? handler : null;
    }

    @Nullable
    private static Holder<MobEffect> curseOf(Spell.Impact impact) {
        if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.STATUS_EFFECT
                || impact.action.status_effect == null || impact.action.status_effect.effect_id == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(impact.action.status_effect.effect_id);
        return id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).filter(Curses::isCurse).orElse(null);
    }

    @Nullable
    private static LastRites.Values lastRitesOf(Spell.Impact impact) {
        if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM
                || impact.action.custom == null) {
            return null;
        }
        return LastRites.valuesOf(impact.action.custom.handler).orElse(null);
    }

    private static double levelBonus(Spell.Impact impact) {
        if (impact == null || impact.target_modifiers == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (Spell.Impact.TargetModifier modifier : impact.target_modifiers) {
            sum += levelBonus(modifier, null);
        }
        return sum;
    }

    /**
     * Power bonus of a target modifier on the curse level. A modifier that also needs a spell of the caster
     * ({@code blightcaller:caster_has_spell}, e.g. a skill node that raises the bonus) counts only if the player has that spell.
     */
    private static double levelBonus(Spell.Impact.TargetModifier modifier, @Nullable Player player) {
        if (modifier == null || modifier.modifier == null || modifier.conditions == null) {
            return 0.0;
        }
        boolean level = false;
        for (Spell.TargetCondition condition : modifier.conditions) {
            if (condition == null) {
                continue;
            }
            if (CURSE_LEVEL_PREDICATE.equals(condition.entity_predicate_id)) {
                level = true;
            } else if (CASTER_SPELL_PREDICATE.equals(condition.entity_predicate_id)
                    && !CursePredicates.hasSpell(player, condition.entity_predicate_param)) {
                return 0.0;
            }
        }
        return level ? Math.max(0.0, modifier.modifier.power_multiplier) : 0.0;
    }

    private static String number(double value) {
        return TooltipTokens.formattedNumber((float) value);
    }

    /** 0.15 as "15%", 0.125 as "12.5%". */
    private static String percent(double fraction) {
        double percent = Math.round(fraction * 1000.0) / 10.0;
        return (percent == Math.rint(percent) ? Integer.toString((int) percent) : Double.toString(percent)) + "%";
    }
}
