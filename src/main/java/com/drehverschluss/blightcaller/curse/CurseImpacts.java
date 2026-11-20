package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

/**
 * Curse marker: a custom spell impact that turns the curse effect of a spell into a curse entry.
 * <p>
 * A curse spell has two impacts, in this order:
 * <pre>
 * {"action": {"type": "STATUS_EFFECT", "status_effect": {"effect_id": "blightcaller:soulburn", "duration": 4}}},
 * {"action": {"type": "CUSTOM", "custom": {"intent": "HARMFUL", "handler": "blightcaller:curse/0.25"}}}
 * </pre>
 * <ul>
 *     <li><b>Which curse:</b> the last {@code STATUS_EFFECT} impact before the marker whose effect is in
 *     {@code #blightcaller:curses} (impacts added by modifier spells count, in Spell Engine's order).</li>
 *     <li><b>Factor:</b> the number after {@code curse/} (damage per tick = power x factor; {@code blightcaller:curse}
 *     alone means 1.0). Spell Engine reads spells into fixed classes, a custom impact has only {@code intent} and
 *     {@code handler}, and the handler is not told which impact called it. So the handler id carries the factor (same
 *     pattern as {@code stormcaller:shock/15}). Handlers for the values used by the loaded spells are registered at
 *     server start.</li>
 *     <li><b>Duration:</b> computed like Spell Engine does for the status effect ({@code duration} plus
 *     {@code effect_duration_add} of the caster's modifier spells), then extended by Curse Duration. Never read from the
 *     effect instance on the target: with several casters that is the longest duration of someone else.</li>
 *     <li><b>Power:</b> the power Spell Engine hands to the marker (spell power with all modifiers of the impact) times
 *     the channel/distance/charge multiplier of the cast, as for damage impacts. Critical is rolled once here.</li>
 *     <li><b>Pestilence:</b> every cast starts a new chain (carrier budget, {@link Pestilence}).</li>
 * </ul>
 */
public final class CurseImpacts {
    public static final String PREFIX = Blightcaller.MODID + ":curse/";
    public static final ResourceLocation PLAIN = Blightcaller.id("curse");

    private static final Set<String> warned = new HashSet<>();

    private CurseImpacts() {}

    public static void register() {
        register(PLAIN.toString(), 1.0);
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, CurseImpacts::onServerAboutToStart);
    }

    private static boolean register(String handler, double factor) {
        ResourceLocation id = ResourceLocation.tryParse(handler);
        if (id == null) {
            return false;
        }
        SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
            try {
                return onImpact(handler, factor, spell, power, caster, target, context);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: curse impact {} of {} failed", handler, spell.getRegisteredName(), e);
                return new SpellHandlers.ImpactResult(false, false);
            }
        });
        return true;
    }

    private static SpellHandlers.ImpactResult onImpact(String handler, double factor, Holder<Spell> spell, SpellPower.Result power,
                                                       LivingEntity caster, @Nullable net.minecraft.world.entity.Entity target,
                                                       SpellExecution.ImpactContext context) {
        if (!(target instanceof LivingEntity living) || living.level().isClientSide() || living.isDeadOrDying()) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        CurseSource source = curseSource(caster, spell, handler, living);
        if (source == null) {
            if (warned.add(spell.getRegisteredName() + "|" + handler)) {
                Blightcaller.LOGGER.warn("Blightcaller: spell {} has the curse impact {} without a curse status effect"
                        + " (effect in #blightcaller:curses) before it", spell.getRegisteredName(), handler);
            }
            return new SpellHandlers.ImpactResult(false, false);
        }
        int ticks = baseDurationTicks(caster, spell, source.impact(), context);
        SpellPower.Result snapshot = new SpellPower.Result(power.school(), power.baseValue() * context.total(spell),
                power.criticalChance(), power.criticalDamage());
        CurseApplication app = CurseApplication.cast(source.curse(), caster, snapshot, factor, ticks, caster.getRandom())
                .withAmplifier(amplifier(caster, spell, source.impact(), context));
        if (Pestilence.isPestilence(source.curse())) {
            // every cast starts its own chain with its own carrier budget
            app = Pestilence.startChain(app, Pestilence.maxCarriers(caster, spell, source.impact(), context.chargeModifier()));
        }
        CurseEntry entry = Curses.apply(living, app);
        Pestilence.afterCast(living, app, entry);
        // never reported as critical: a critical flag here would trigger "on spell crit" passives a second time
        return new SpellHandlers.ImpactResult(entry != null, false);
    }

    /** The curse status effect impact this marker belongs to, and its effect. */
    public record CurseSource(Spell.Impact impact, Holder<MobEffect> curse) {}

    /**
     * Finds the curse of a marker: the last {@code STATUS_EFFECT} impact with a curse before the first impact with this
     * handler, in the impact list Spell Engine runs for this caster (with impacts of modifier spells).
     */
    @Nullable
    public static CurseSource curseSource(LivingEntity caster, Holder<Spell> spell, String handler) {
        return curseSource(caster, spell, handler, null);
    }

    /**
     * Like {@link #curseSource(LivingEntity, Holder, String)}, but with a target: among the curse impacts before the marker,
     * the last one whose target conditions allow this target wins (the one Spell Engine applied or tried to apply, e.g. the
     * boss variant of Enfeeble and Gravebind). If none allows it, the last one at all.
     */
    @Nullable
    public static CurseSource curseSource(LivingEntity caster, Holder<Spell> spell, String handler, @Nullable LivingEntity target) {
        List<Spell.Impact> impacts = SpellModifiers.extendedImpactsOf(caster, spell).impacts();
        CurseSource last = null;
        CurseSource allowed = null;
        for (Spell.Impact impact : impacts) {
            if (impact == null || impact.action == null) {
                continue;
            }
            if (impact.action.type == Spell.Impact.Action.Type.CUSTOM && impact.action.custom != null
                    && handler.equals(impact.action.custom.handler)) {
                return allowed != null ? allowed : last;
            }
            Holder<MobEffect> curse = curseOf(impact);
            if (curse != null) {
                last = new CurseSource(impact, curse);
                if (target != null && SpellImpacts.evaluateImpactConditions(target, caster, impact.target_modifiers).allowed()) {
                    allowed = last;
                }
            }
        }
        return null;
    }

    /**
     * Amplifier of the curse status effect impact, the way Spell Engine computes it: {@code amplifier} plus
     * {@code effect_amplifier_add} of the caster's modifier spells, capped by {@code amplifier_cap} if set. Targets that
     * refuse the effect get the curse with this strength all the same.
     */
    public static int amplifier(LivingEntity caster, Holder<Spell> spell, Spell.Impact statusImpact, SpellExecution.ImpactContext context) {
        Spell.Impact.Action.StatusEffect status = statusImpact.action.status_effect;
        int amplifier = status.amplifier;
        int cap = status.amplifier_cap;
        for (Spell.Modifier modifier : SpellModifiers.ofImpact(caster, spell, statusImpact, context.chargeModifier())) {
            amplifier += modifier.effect_amplifier_add;
            cap += modifier.effect_amplifier_cap_add;
        }
        if (status.amplifier_cap > 0) {
            amplifier = Math.min(amplifier, cap);
        }
        return Math.max(0, amplifier);
    }

    @Nullable
    private static Holder<MobEffect> curseOf(Spell.Impact impact) {
        if (impact.action.type != Spell.Impact.Action.Type.STATUS_EFFECT || impact.action.status_effect == null
                || impact.action.status_effect.effect_id == null || impact.action.status_effect.remove != null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(impact.action.status_effect.effect_id);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(id).filter(Curses::isCurse).orElse(null);
    }

    /**
     * Duration of the curse status effect in ticks, the way Spell Engine computes it: {@code duration} of the impact
     * plus {@code effect_duration_add} of the caster's modifier spells for that impact. Curse Duration comes on top.
     */
    public static int baseDurationTicks(LivingEntity caster, Holder<Spell> spell, Spell.Impact statusImpact,
                                        SpellExecution.ImpactContext context) {
        float seconds = statusImpact.action.status_effect.duration;
        for (Spell.Modifier modifier : SpellModifiers.ofImpact(caster, spell, statusImpact, context.chargeModifier())) {
            seconds += modifier.effect_duration_add;
        }
        return Math.max(0, Math.round(seconds * 20.0F));
    }

    // ------------------------------------------------------------------------------------------------ handler ids

    /** Factor of a curse handler id ({@code blightcaller:curse} = 1.0), empty if it is none. */
    public static OptionalDouble factorOf(@Nullable String handler) {
        if (handler == null) {
            return OptionalDouble.empty();
        }
        if (handler.equals(PLAIN.toString())) {
            return OptionalDouble.of(1.0);
        }
        if (!handler.startsWith(PREFIX)) {
            return OptionalDouble.empty();
        }
        try {
            double factor = Double.parseDouble(handler.substring(PREFIX.length()).trim().toLowerCase(Locale.ROOT));
            return Double.isNaN(factor) || Double.isInfinite(factor) || factor < 0.0 ? OptionalDouble.empty() : OptionalDouble.of(factor);
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** Registers the curse handlers used by the loaded spells ({@code blightcaller:curse/<factor>}). */
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Registry<Spell> spells = event.getServer().registryAccess().registry(SpellRegistry.KEY).orElse(null);
            if (spells == null) {
                return;
            }
            TreeSet<String> added = new TreeSet<>();
            for (Spell spell : spells) {
                for (Spell.Impact impact : impactsOf(spell)) {
                    if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM
                            || impact.action.custom == null) {
                        continue;
                    }
                    String handler = impact.action.custom.handler;
                    if (handler == null || !handler.startsWith(PREFIX) || SpellHandlers.customImpact.containsKey(handler)) {
                        continue;
                    }
                    OptionalDouble factor = factorOf(handler);
                    if (factor.isPresent() && register(handler, factor.getAsDouble())) {
                        added.add(handler);
                    } else {
                        Blightcaller.LOGGER.warn("Blightcaller: invalid curse impact '{}', expected {}<factor>", handler, PREFIX);
                    }
                }
            }
            if (!added.isEmpty()) {
                Blightcaller.LOGGER.info("Blightcaller: registered curse impacts {}", added);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: registering curse impacts failed", e);
        }
    }

    private static List<Spell.Impact> impactsOf(Spell spell) {
        List<Spell.Impact> impacts = new java.util.ArrayList<>();
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
}
