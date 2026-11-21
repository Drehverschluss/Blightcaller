package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Marker impacts of the skill tree nodes (phase 7A). Every value is in the handler id of the marker in the node's spell file,
 * so pack makers change the numbers with a data pack and tooltips show them ({@code client.SkillNodeTooltips}):
 * <ul>
 *     <li>{@code blightcaller:soul_migration} in a PASSIVE spell without triggers: the caster has Soul Migration
 *     ({@link SoulMigration}). The marker never runs, it only marks the passive.</li>
 *     <li>{@code blightcaller:deepening_rot/<per tick>/<max>} in a PASSIVE spell without triggers: Deepening Rot
 *     ({@link DeepeningRot}).</li>
 *     <li>{@code blightcaller:spreading_rot/<radius>}, appended to Soul Rot by a MODIFIER spell (Spreading Rot): the caster's
 *     fresh Soul Rot entry on the target is copied to the nearest other enemy within the radius (same values, own entry, does
 *     not jump on).</li>
 *     <li>{@code blightcaller:enervation/<fraction>}, appended to Enfeeble by a MODIFIER spell (Enervation): while the caster's
 *     Enfeeble lasts, the target deals that much less damage (bosses half). Several casters do not stack.</li>
 * </ul>
 * Handlers for the values used by the loaded spells are registered at server start (like the curse markers).
 */
public final class SkillMarkers {
    public static final String SOUL_MIGRATION = Blightcaller.MODID + ":soul_migration";
    public static final String DEEPENING_ROT = Blightcaller.MODID + ":deepening_rot/";
    public static final String SPREADING_ROT = Blightcaller.MODID + ":spreading_rot/";
    public static final String ENERVATION = Blightcaller.MODID + ":enervation/";
    /** Passive: {@code blightbloom/<radius>/<seconds>/<factor>} ({@link Blightbloom}). */
    public static final String BLIGHTBLOOM = Blightcaller.MODID + ":blightbloom/";
    /** Passive: {@code infectious_curses/<chance>/<radius>} ({@link InfectiousCurses}). */
    public static final String INFECTIOUS_CURSES = Blightcaller.MODID + ":infectious_curses/";
    /** Passive: {@code coup_de_grace/<health below>/<bonus>} ({@link #coupDeGrace}). */
    public static final String COUP_DE_GRACE = Blightcaller.MODID + ":coup_de_grace/";
    /** Impact of the passive Vengeful Hex: a plain Enfeeble that keeps the caster's Enervation on the target. */
    public static final String VENGEFUL_HEX = Blightcaller.MODID + ":vengeful_hex";
    /** Appended to Last Rites (Soul Echo): {@code soul_echo/<seconds>/<factor>}, a fresh Soulburn on every detonated target. */
    public static final String SOUL_ECHO = Blightcaller.MODID + ":soul_echo/";
    /** Appended to Pestilence (Rapid Contagion): {@code rapid_contagion/<seconds between spreads>}. */
    public static final String RAPID_CONTAGION = Blightcaller.MODID + ":rapid_contagion/";
    /** Appended to Pestilence (Wide Contagion): {@code wide_contagion/<extra blocks>}. */
    public static final String WIDE_CONTAGION = Blightcaller.MODID + ":wide_contagion/";
    /** Passive markers that never run, they only mark the node. */
    private static final List<String> PASSIVE_MARKERS = List.of(DEEPENING_ROT, BLIGHTBLOOM, INFECTIOUS_CURSES, COUP_DE_GRACE);
    private static final SpellHandlers.ImpactResult NONE = new SpellHandlers.ImpactResult(false, false);
    private static final Vector3f ROT_COLOR = new Vector3f(0.55F, 0.8F, 0.3F);

    private SkillMarkers() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, SkillMarkers::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, SkillMarkers::onIncomingDamage);
        SpellHandlers.registerCustomImpact(ResourceLocation.parse(SOUL_MIGRATION), (spell, power, caster, target, context) -> NONE);
        SpellHandlers.registerCustomImpact(ResourceLocation.parse(VENGEFUL_HEX), (spell, power, caster, target, context) -> {
            try {
                return vengefulHex(spell, power, caster, target, context);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: Vengeful Hex failed", e);
                return NONE;
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ reading markers

    /**
     * The numbers of the first marker with this prefix in the passive spells of the player (e.g. {@code deepening_rot/0.05/0.5}
     * gives {0.05, 0.5}), or {@code null} if the player has none. Server side.
     */
    @Nullable
    public static double[] passiveValues(Player player, String prefix) {
        for (Holder<Spell> spell : SpellContainerSource.passiveSpellsOf(player)) {
            for (Spell.Impact impact : spell.value().impacts) {
                String handler = handler(impact);
                if (handler != null && handler.startsWith(prefix)) {
                    double[] values = values(handler, prefix);
                    if (values != null) {
                        return values;
                    }
                }
            }
        }
        return null;
    }

    /** Whether one of the player's passive spells has this marker. Server side. */
    public static boolean hasPassive(Player player, String handlerId) {
        for (Holder<Spell> spell : SpellContainerSource.passiveSpellsOf(player)) {
            for (Spell.Impact impact : spell.value().impacts) {
                if (handlerId.equals(handler(impact))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The numbers after the prefix of a marker id, split at '/', or {@code null} if one is not a number. */
    @Nullable
    public static double[] values(String handler, String prefix) {
        String[] parts = handler.substring(prefix.length()).split("/");
        double[] out = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i].trim().toLowerCase(Locale.ROOT));
                if (Double.isNaN(out[i]) || Double.isInfinite(out[i]) || out[i] < 0.0) {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    @Nullable
    public static String handler(@Nullable Spell.Impact impact) {
        if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM || impact.action.custom == null) {
            return null;
        }
        return impact.action.custom.handler;
    }

    // ------------------------------------------------------------------------------------------------ handlers

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Registry<Spell> spells = event.getServer().registryAccess().registry(SpellRegistry.KEY).orElse(null);
            if (spells == null) {
                return;
            }
            TreeSet<String> added = new TreeSet<>();
            for (Spell spell : spells) {
                List<Spell.Impact> impacts = new ArrayList<>(spell.impacts);
                if (spell.modifiers != null) {
                    for (Spell.Modifier modifier : spell.modifiers) {
                        if (modifier != null && modifier.impacts != null) {
                            impacts.addAll(modifier.impacts);
                        }
                    }
                }
                for (Spell.Impact impact : impacts) {
                    String handler = handler(impact);
                    if (handler == null || SpellHandlers.customImpact.containsKey(handler)) {
                        continue;
                    }
                    if (registerMarker(handler)) {
                        added.add(handler);
                    }
                }
            }
            if (!added.isEmpty()) {
                Blightcaller.LOGGER.info("Blightcaller: registered skill markers {}", added);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: registering skill markers failed", e);
        }
    }

    private static boolean registerMarker(String handler) {
        ResourceLocation id = ResourceLocation.tryParse(handler);
        if (id == null) {
            return false;
        }
        for (String passive : PASSIVE_MARKERS) {
            if (handler.startsWith(passive)) {
                // only marks the passive, never runs
                SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> NONE);
                return true;
            }
        }
        if (handler.startsWith(SOUL_ECHO)) {
            double[] v = values(handler, SOUL_ECHO);
            if (v == null || v.length < 2) {
                Blightcaller.LOGGER.warn("Blightcaller: invalid marker '{}', expected {}<seconds>/<factor>", handler, SOUL_ECHO);
                return false;
            }
            SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
                try {
                    return soulEcho(spell, power, caster, target, context, v[0], v[1]);
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: Soul Echo failed", e);
                    return NONE;
                }
            });
            return true;
        }
        if (handler.startsWith(RAPID_CONTAGION) || handler.startsWith(WIDE_CONTAGION)) {
            boolean rapid = handler.startsWith(RAPID_CONTAGION);
            String prefix = rapid ? RAPID_CONTAGION : WIDE_CONTAGION;
            double[] v = values(handler, prefix);
            if (v == null || v.length < 1 || (rapid && v[0] <= 0.0)) {
                Blightcaller.LOGGER.warn("Blightcaller: invalid marker '{}', expected {}<number>", handler, prefix);
                return false;
            }
            int interval = rapid ? Math.max(1, (int) Math.round(v[0] * 20.0)) : 0;
            double radius = rapid ? 0.0 : v[0];
            SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
                try {
                    boolean ok = target instanceof LivingEntity living && !living.level().isClientSide()
                            && Pestilence.tune(living, caster.getUUID(), interval, radius);
                    return new SpellHandlers.ImpactResult(ok, false);
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: {} failed", handler, e);
                    return NONE;
                }
            });
            return true;
        }
        if (handler.startsWith(SPREADING_ROT)) {
            double[] v = values(handler, SPREADING_ROT);
            if (v == null || v.length < 1) {
                Blightcaller.LOGGER.warn("Blightcaller: invalid marker '{}', expected {}<radius>", handler, SPREADING_ROT);
                return false;
            }
            SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
                try {
                    return spreadingRot(caster, target, v[0]);
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: Spreading Rot failed", e);
                    return NONE;
                }
            });
            return true;
        }
        if (handler.startsWith(ENERVATION)) {
            double[] v = values(handler, ENERVATION);
            if (v == null || v.length < 1) {
                Blightcaller.LOGGER.warn("Blightcaller: invalid marker '{}', expected {}<fraction>", handler, ENERVATION);
                return false;
            }
            SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
                try {
                    return enervation(caster, target, Math.min(1.0, v[0]));
                } catch (Exception e) {
                    Blightcaller.LOGGER.error("Blightcaller: Enervation failed", e);
                    return NONE;
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Vengeful Hex (passive 2 of side B): the plain Enfeeble of the passive spell on the attacker, through the normal curse marker.
     * The modifiers of the Enfeeble spell do not apply (they match only {@code blightcaller:enfeeble}). If the caster's own Enfeeble
     * with Enervation is on the attacker already, the refresh keeps its Enervation (a refresh would clear it otherwise).
     */
    private static SpellHandlers.ImpactResult vengefulHex(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                          @Nullable Entity target, SpellExecution.ImpactContext context) {
        return plainEnfeeble(VENGEFUL_HEX, true, spell, power, caster, target, context);
    }

    /**
     * A plain Enfeeble through the curse marker {@code handler} (the status effect impact before it), used by Vengeful Hex and the
     * Wailing Skull relic. Keeps the caster's Enervation on the target (a refresh would clear it); never shortens a longer
     * Enfeeble of the same caster ({@link Curses#apply} keeps the later end). {@code extend = false}: no Curse Duration bonus.
     */
    static SpellHandlers.ImpactResult plainEnfeeble(String handler, boolean extend, Holder<Spell> spell, SpellPower.Result power,
                                                    LivingEntity caster, @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (!(target instanceof LivingEntity living) || living.level().isClientSide()) {
            return NONE;
        }
        CurseEntry before = own(living, CurseEffects.ENFEEBLE, caster.getUUID());
        double enervation = before != null && before.end > living.level().getGameTime() ? before.enervation : 0.0;
        SpellHandlers.ImpactResult result = CurseImpacts.curse(handler, 1.0, spell, power, caster, target, context, extend);
        CurseEntry after = own(living, CurseEffects.ENFEEBLE, caster.getUUID());
        if (after != null && enervation > 0.0) {
            after.enervation = Math.max(after.enervation, enervation);
        }
        return result;
    }

    /** Soul Echo (Last Rites): a fresh Soulburn on a target that Last Rites detonated just now. */
    private static SpellHandlers.ImpactResult soulEcho(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                       @Nullable Entity target, SpellExecution.ImpactContext context,
                                                       double seconds, double factor) {
        if (!(target instanceof LivingEntity living) || living.level().isClientSide() || living.isDeadOrDying()
                || !LastRites.detonatedNow(caster, living)) {
            return NONE;
        }
        SpellPower.Result snapshot = new SpellPower.Result(power.school(), power.baseValue() * context.total(spell),
                power.criticalChance(), power.criticalDamage());
        CurseApplication app = CurseApplication.cast(CurseEffects.SOULBURN, caster, snapshot, factor, Math.round(seconds * 20.0),
                caster.getRandom());
        return new SpellHandlers.ImpactResult(Curses.apply(living, app) != null, false);
    }

    /**
     * Coup de Grace (passive 3 of side B): extra damage of the caster's curses on a target below the health threshold, as a
     * fraction (0.2 = +20 %), bosses included; 0 without the passive. Checked on every tick and at Last Rites.
     */
    public static double coupDeGrace(@Nullable LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof Player player) || target.getMaxHealth() <= 0.0F) {
            return 0.0;
        }
        double[] v = passiveValues(player, COUP_DE_GRACE);
        if (v == null || v.length < 2) {
            return 0.0;
        }
        return target.getHealth() / target.getMaxHealth() < v[0] ? v[1] : 0.0;
    }

    /** Whether a copy of the caster's curse with this end brings the target something: the curse is missing there or ends sooner. */
    static boolean benefits(LivingEntity target, Holder<net.minecraft.world.effect.MobEffect> curse, @Nullable UUID caster, double end) {
        CurseEntry existing = own(target, curse, caster);
        return existing == null || existing.end < end;
    }

    /** Spreading Rot: copies the caster's Soul Rot entry on the target to the nearest other enemy in the radius. */
    private static SpellHandlers.ImpactResult spreadingRot(LivingEntity caster, @Nullable Entity target, double radius) {
        if (!(target instanceof LivingEntity living) || !(living.level() instanceof ServerLevel level)) {
            return NONE;
        }
        Holder<net.minecraft.world.effect.MobEffect> curse = CurseEffects.SOUL_ROT;
        CurseEntry source = own(living, curse, caster.getUUID());
        if (source == null) {
            return NONE;
        }
        long now = level.getGameTime();
        double remaining = source.end - now;
        if (remaining < 1.0) {
            return NONE;
        }
        LivingEntity next = nearest(level, living, radius, caster, caster.getUUID(), e -> benefits(e, curse, caster.getUUID(), source.end));
        if (next == null) {
            return NONE;
        }
        CurseApplication copy = new CurseApplication(curse, caster.getUUID(), source.power, source.critical, source.criticalDamage,
                source.factor, remaining, false, 0, null, true, Curses.amplifier(living, curse));
        CurseEntry placed = Curses.apply(next, copy);
        if (placed != null) {
            arc(level, living, next);
        }
        return new SpellHandlers.ImpactResult(placed != null, false);
    }

    /** Enervation: marks the caster's fresh Enfeeble entry on the target. */
    private static SpellHandlers.ImpactResult enervation(LivingEntity caster, @Nullable Entity target, double fraction) {
        if (!(target instanceof LivingEntity living) || living.level().isClientSide()) {
            return NONE;
        }
        CurseEntry entry = own(living, CurseEffects.ENFEEBLE, caster.getUUID());
        if (entry == null) {
            return NONE;
        }
        entry.enervation = Math.max(entry.enervation, fraction);
        return new SpellHandlers.ImpactResult(true, false);
    }

    /** Damage dealt by a target under an Enfeeble with Enervation: less by the strongest Enervation on it, bosses half. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide()
                    || !attacker.hasData(CurseData.TYPE)) {
                return;
            }
            double weaken = weakening(attacker);
            if (weaken > 0.0) {
                event.setAmount((float) (event.getAmount() * (1.0 - weaken)));
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: Enervation on damage failed", e);
        }
    }

    /** How much less damage this entity deals right now (Enervation), 0 without. */
    public static double weakening(LivingEntity entity) {
        double strongest = 0.0;
        for (CurseEntry entry : Curses.entries(entity, CurseEffects.ENFEEBLE)) {
            strongest = Math.max(strongest, entry.enervation);
        }
        return EnfeebleEffect.resistant(entity) ? strongest * 0.5 : strongest;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    @Nullable
    static CurseEntry own(LivingEntity target, Holder<net.minecraft.world.effect.MobEffect> curse, @Nullable UUID caster) {
        for (CurseEntry entry : Curses.data(target).map(d -> d.entries(Curses.id(curse))).orElse(List.of())) {
            if (entry.sameCaster(caster)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Nearest valid enemy of the caster around {@code from} within the radius, with a line of sight from {@code from}: the
     * Pestilence target rules (no players, no pets, Spell Engine's area relations), monsters first. {@code extra} narrows it.
     */
    @Nullable
    static LivingEntity nearest(ServerLevel level, LivingEntity from, double radius, @Nullable LivingEntity caster, @Nullable UUID casterId,
                                java.util.function.Predicate<LivingEntity> extra) {
        if (radius <= 0.0) {
            return null;
        }
        AABB box = from.getBoundingBox().inflate(radius);
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != from && e.distanceTo(from) <= radius && Pestilence.validTarget(e, caster, casterId) && extra.test(e)));
        candidates.sort(Comparator.<LivingEntity>comparingInt(e -> e instanceof Enemy ? 0 : 1)
                .thenComparingDouble(e -> e.distanceToSqr(from)));
        for (LivingEntity candidate : candidates) {
            if (TargetHelper.isInLineOfSight(from, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** A short arc of rot from one target to the next (placeholder until the graphics phase). */
    static void arc(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 a = from.position().add(0.0, from.getBbHeight() * 0.6, 0.0);
        Vec3 b = to.position().add(0.0, to.getBbHeight() * 0.6, 0.0);
        DustParticleOptions dust = new DustParticleOptions(ROT_COLOR, 1.2F);
        for (int i = 0; i <= 12; i++) {
            double t = i / 12.0;
            Vec3 p = a.lerp(b, t).add(0.0, 0.8 * 4.0 * t * (1.0 - t), 0.0);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, b.x, b.y, b.z, 4, 0.2, 0.2, 0.2, 0.01);
        level.playSound(null, b.x, b.y, b.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 0.6F);
    }
}
