package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellDamageSource;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

/**
 * Last Rites / Letzte Riten (concept 5, decided 20.11.): a custom impact that detonates the caster's <b>own</b> curses with
 * damage on a target.
 * <ul>
 *     <li><b>Damage</b> = sum over the caster's own entries of curses with damage: damage per second of the entry (its
 *     snapshot, critical roll and the target's soul vulnerability, {@link Curses#damagePerSecond}) x {@code seconds} x
 *     {@code factor}. Then x the power multiplier of the impact's target modifiers (three {@code blightcaller:curse_level}
 *     modifiers with {@code power_multiplier 0.15} give +15 % per curse level; Spell Engine evaluates them before this
 *     handler runs, so the level is counted before anything is detonated) and x the area multiplier of the cast.</li>
 *     <li><b>Afterwards</b> the caster's detonated entries are gone. Entries of other players and curses without damage
 *     (Enfeeble, Gravebind, Soul Rend) stay. The effect itself goes only when no entry is left.</li>
 *     <li><b>One hit per target</b>, a normal soul spell hit with the caster as attacker
 *     ({@code SpellDamageSource.create(SOUL, caster)}): it may trigger like any spell hit. The target's invulnerability
 *     window neither swallows it nor is touched by it (same pattern as the curse ticks). Targets without own curses with
 *     damage get nothing (the impact fails, no visuals).</li>
 * </ul>
 * Handler ids: {@code blightcaller:last_rites} (4 s x 1.5) or {@code blightcaller:last_rites/<seconds>/<factor>}, e.g.
 * {@code blightcaller:last_rites/4/1.5}. Handlers for the values used by the loaded spells are registered at server start.
 */
public final class LastRites {
    public static final String PREFIX = Blightcaller.MODID + ":last_rites/";
    public static final ResourceLocation PLAIN = Blightcaller.id("last_rites");
    public static final double DEFAULT_SECONDS = 4.0;
    public static final double DEFAULT_FACTOR = 1.5;

    /** Seconds and factor of a handler id. */
    public record Values(double seconds, double factor) {}

    /** Last detonation per target entity id (Soul Echo asks right after, in the same tick). */
    private record Detonation(UUID caster, long tick) {}

    private static final java.util.Map<Integer, Detonation> DETONATED = new java.util.HashMap<>();

    /** Whether Last Rites of this caster detonated curses on the target in this game tick (Soul Echo). */
    public static boolean detonatedNow(LivingEntity caster, LivingEntity target) {
        Detonation d = DETONATED.get(target.getId());
        return d != null && d.caster().equals(caster.getUUID()) && d.tick() == target.level().getGameTime();
    }

    private LastRites() {}

    public static void register() {
        register(PLAIN.toString(), new Values(DEFAULT_SECONDS, DEFAULT_FACTOR));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, event -> DETONATED.clear());
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, LastRites::onServerAboutToStart);
    }

    private static void register(String handler, Values values) {
        ResourceLocation id = ResourceLocation.tryParse(handler);
        if (id == null) {
            return;
        }
        SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
            try {
                return onImpact(values, spell, power, caster, target, context);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: Last Rites impact {} of {} failed", handler, spell.getRegisteredName(), e);
                return new SpellHandlers.ImpactResult(false, false);
            }
        });
    }

    private static SpellHandlers.ImpactResult onImpact(Values values, Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                       @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (!(target instanceof LivingEntity living) || living.level().isClientSide() || living.isDeadOrDying() || target == caster) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        UUID casterId = caster.getUUID();
        double sum = 0.0;
        List<Holder<MobEffect>> detonate = new ArrayList<>();
        Optional<CurseData> data = Curses.data(living);
        if (data.isEmpty()) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        for (Map.Entry<ResourceLocation, List<CurseEntry>> e : List.copyOf(data.get().all().entrySet())) {
            Optional<Holder.Reference<MobEffect>> curse = BuiltInRegistries.MOB_EFFECT.getHolder(e.getKey());
            if (curse.isEmpty() || !Curses.dealsDamage(curse.get().value()) || e.getValue().isEmpty()) {
                continue;
            }
            boolean own = false;
            for (CurseEntry entry : List.copyOf(e.getValue())) {
                if (entry.sameCaster(casterId)) {
                    // Deepening Rot: the bonus the entry has reached, without growing further (decided 20.11.)
                    double deepening = DeepeningRot.currentBonus(caster, entry);
                    double part = Curses.damagePerSecond(living, curse.get(), entry) * values.seconds() * values.factor() * (1.0 + deepening);
                    sum += part;
                    own = true;
                    if (Blightcaller.LOGGER.isDebugEnabled()) {
                        Blightcaller.LOGGER.debug("[Blightcaller] Last Rites on {}: {} {}/s x {} s x {} x (1 + Deepening Rot {}) = {}",
                                living.getName().getString(), curse.get().getRegisteredName(),
                                Curses.damagePerSecond(living, curse.get(), entry), values.seconds(), values.factor(), deepening, part);
                    }
                }
            }
            if (own) {
                detonate.add(curse.get());
            }
        }
        if (detonate.isEmpty()) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        // Coup de Grace on top (target below the health threshold before the hit)
        double coup = SkillMarkers.coupDeGrace(caster, living);
        double level = levelMultiplier(power, caster, context);
        double amount = sum * level * context.total(spell) * (1.0 + coup);
        if (Blightcaller.LOGGER.isDebugEnabled()) {
            Blightcaller.LOGGER.debug("[Blightcaller] Last Rites on {}: sum {} x curse level {} x area {} x (1 + Coup de Grace {}) = {}",
                    living.getName().getString(), sum, level, context.total(spell), coup, amount);
        }
        DETONATED.put(living.getId(), new Detonation(casterId, living.level().getGameTime()));
        // remove first: the hit may kill the target, and death handlers must not see the detonated entries
        for (Holder<MobEffect> curse : detonate) {
            Curses.removeEntries(living, curse, casterId);
        }
        if (amount > 0.0) {
            hurt(living, caster, (float) amount);
        }
        return new SpellHandlers.ImpactResult(true, false);
    }

    /**
     * Power multiplier from the impact's target modifiers and power modifiers: the power Spell Engine hands to the handler
     * divided by the caster's plain power of the spell's school.
     */
    static double levelMultiplier(SpellPower.Result power, LivingEntity caster, SpellExecution.ImpactContext context) {
        SpellPower.Result plain = context.power() != null && context.power().school() == power.school()
                ? context.power() : SpellPower.getSpellPower(power.school(), caster);
        if (plain == null || plain.baseValue() <= 1.0E-9) {
            return 1.0;
        }
        return Math.max(0.0, power.baseValue() / plain.baseValue());
    }

    private static void hurt(LivingEntity target, LivingEntity caster, float amount) {
        int invulnerableTime = target.invulnerableTime;
        float lastHurt = target.lastHurt;
        target.invulnerableTime = 0;
        target.lastHurt = 0.0F;
        try {
            caster.setLastHurtMob(target);
            target.hurt(SpellDamageSource.create(SpellSchools.SOUL, caster), amount);
        } finally {
            target.invulnerableTime = invulnerableTime;
            target.lastHurt = lastHurt;
        }
    }

    // ------------------------------------------------------------------------------------------------ handler ids

    /** Seconds and factor of a Last Rites handler id, empty if it is none. */
    public static Optional<Values> valuesOf(@Nullable String handler) {
        if (handler == null) {
            return Optional.empty();
        }
        if (handler.equals(PLAIN.toString())) {
            return Optional.of(new Values(DEFAULT_SECONDS, DEFAULT_FACTOR));
        }
        if (!handler.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = handler.substring(PREFIX.length()).trim().toLowerCase(Locale.ROOT).split("/");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            double seconds = Double.parseDouble(parts[0]);
            double factor = Double.parseDouble(parts[1]);
            if (!Double.isFinite(seconds) || !Double.isFinite(factor) || seconds < 0.0 || factor < 0.0) {
                return Optional.empty();
            }
            return Optional.of(new Values(seconds, factor));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Registry<Spell> spells = event.getServer().registryAccess().registry(SpellRegistry.KEY).orElse(null);
            if (spells == null) {
                return;
            }
            TreeSet<String> added = new TreeSet<>();
            for (Spell spell : spells) {
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
                for (Spell.Impact impact : impacts) {
                    if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM
                            || impact.action.custom == null) {
                        continue;
                    }
                    String handler = impact.action.custom.handler;
                    if (handler == null || !handler.startsWith(PREFIX) || SpellHandlers.customImpact.containsKey(handler)) {
                        continue;
                    }
                    Optional<Values> values = valuesOf(handler);
                    if (values.isPresent()) {
                        register(handler, values.get());
                        added.add(handler);
                    } else {
                        Blightcaller.LOGGER.warn("Blightcaller: invalid Last Rites impact '{}', expected {}<seconds>/<factor>", handler, PREFIX);
                    }
                }
            }
            if (!added.isEmpty()) {
                Blightcaller.LOGGER.info("Blightcaller: registered Last Rites impacts {}", added);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: registering Last Rites impacts failed", e);
        }
    }
}
