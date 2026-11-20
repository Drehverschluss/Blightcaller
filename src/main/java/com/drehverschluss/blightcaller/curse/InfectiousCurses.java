package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellParameters;

/**
 * Infectious Curses / Ansteckende Flüche (skill tree, passive 3 of side A; concept 7): the caster's curse spells put their curse with a
 * chance on one more enemy within the radius around the target, with the same values as an entry of its own that spreads no
 * further. Marker {@code blightcaller:infectious_curses/<chance>/<radius>} (default {@code 0.2/5}) in a passive spell.
 * <ul>
 *     <li><b>One roll per cast</b>, also for area and channelled spells: the first curse marker of a cast rolls (the cast = caster,
 *     spell and game tick; a channel rolls only on its first pulse). On success the target of that marker is the source; every curse
 *     this cast puts on the source is copied (Gravebind with Soulfire Chains: Gravebind and Soulburn).</li>
 *     <li>Only active spells cast by the caster count, not passives (Vengeful Hex, Blightbloom) and not copies (Spreading Rot, Soul
 *     Migration, Pestilence spreading).</li>
 *     <li>The extra enemy is chosen at the end of the server tick, after all impacts of the cast (an area spell never wastes it on an
 *     enemy it hits itself): the nearest valid enemy around the source (line of sight, Pestilence target rules) for which at least one
 *     of the curses brings something (missing there or ending sooner), like Spreading Rot.</li>
 *     <li>The copy keeps power snapshot, critical roll, strength and the remaining time of the source entry (marked transferred, not
 *     extended again). Pestilence: the extra carrier is an infection of the same chain and takes a place in it (none if it is full).</li>
 * </ul>
 */
public final class InfectiousCurses {
    /** The roll of one cast and, on success, the curses it put on the source target. */
    private static final class Roll {
        final ServerLevel level;
        final LivingEntity caster;
        final boolean success;
        final double radius;
        LivingEntity source;
        final List<Holder<MobEffect>> curses = new ArrayList<>();

        Roll(ServerLevel level, LivingEntity caster, boolean success, double radius) {
            this.level = level;
            this.caster = caster;
            this.success = success;
            this.radius = radius;
        }
    }

    private record Key(UUID caster, ResourceKey<Spell> spell, long tick) {}

    private static final Map<Key, Roll> ROLLS = new HashMap<>();

    private InfectiousCurses() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> resolve());
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> ROLLS.clear());
    }

    /** Chance and radius of the caster's Infectious Curses, or {@code null} without it. */
    static double[] values(LivingEntity caster) {
        if (!(caster instanceof Player player)) {
            return null;
        }
        double[] v = SkillMarkers.passiveValues(player, SkillMarkers.INFECTIOUS_CURSES);
        return v != null && v.length >= 2 ? v : null;
    }

    /** After a curse marker put a curse on a target (only for active spells of a player with the passive). */
    static void afterCurse(LivingEntity caster, Holder<Spell> spell, SpellExecution.ImpactContext context, LivingEntity target,
                           Holder<MobEffect> curse, CurseEntry entry) {
        try {
            if (!(target.level() instanceof ServerLevel level) || spell.value().type != Spell.Type.ACTIVE || spell.unwrapKey().isEmpty()) {
                return;
            }
            boolean channelled = SpellParameters.isChanneled(spell.value());
            if (channelled && context.channelTickIndex() > 0) {
                return; // one roll per channel: on its first pulse
            }
            Key key = new Key(caster.getUUID(), spell.unwrapKey().get(), level.getGameTime());
            Roll roll = ROLLS.get(key);
            if (roll == null) {
                double[] v = values(caster);
                if (v == null) {
                    return;
                }
                roll = new Roll(level, caster, caster.getRandom().nextDouble() < v[0], v[1]);
                ROLLS.put(key, roll);
                if (roll.success) {
                    roll.source = target;
                }
            }
            if (roll.success && roll.source == target && !roll.curses.contains(curse)) {
                roll.curses.add(curse);
            }
        } catch (Exception e) {
            Blightcaller.LOGGER.error("Blightcaller: Infectious Curses roll failed", e);
        }
    }

    /** End of the server tick: the successful rolls of this tick infect one more enemy each. */
    private static void resolve() {
        if (ROLLS.isEmpty()) {
            return;
        }
        List<Roll> rolls = new ArrayList<>(ROLLS.values());
        ROLLS.clear();
        for (Roll roll : rolls) {
            if (!roll.success || roll.source == null || roll.curses.isEmpty()) {
                continue;
            }
            try {
                infect(roll);
            } catch (Exception e) {
                Blightcaller.LOGGER.error("Blightcaller: Infectious Curses from {} failed", roll.source, e);
            }
        }
    }

    private static void infect(Roll roll) {
        LivingEntity source = roll.source;
        UUID caster = roll.caster.getUUID();
        long now = roll.level.getGameTime();
        List<CurseEntry> sources = new ArrayList<>();
        for (Holder<MobEffect> curse : roll.curses) {
            sources.add(SkillMarkers.own(source, curse, caster));
        }
        LivingEntity next = SkillMarkers.nearest(roll.level, source, roll.radius, roll.caster, caster, candidate -> {
            for (int i = 0; i < roll.curses.size(); i++) {
                CurseEntry e = sources.get(i);
                if (e != null && e.end - now >= 1.0 && SkillMarkers.benefits(candidate, roll.curses.get(i), caster, e.end)
                        && (!Pestilence.isPestilence(roll.curses.get(i)) || Pestilence.hasRoom(e.chain))) {
                    return true;
                }
            }
            return false;
        });
        if (next == null) {
            return;
        }
        boolean placedAny = false;
        for (int i = 0; i < roll.curses.size(); i++) {
            Holder<MobEffect> curse = roll.curses.get(i);
            CurseEntry e = sources.get(i);
            if (e == null || e.end - now < 1.0 || !SkillMarkers.benefits(next, curse, caster, e.end)) {
                continue;
            }
            boolean pestilence = Pestilence.isPestilence(curse);
            if (pestilence && !Pestilence.hasRoom(e.chain)) {
                continue;
            }
            CurseApplication copy = new CurseApplication(curse, caster, e.power, e.critical, e.criticalDamage, e.factor, e.end - now,
                    false, 0, pestilence ? e.chain : null, true, Curses.amplifier(source, curse));
            CurseEntry placed = Curses.apply(next, copy);
            if (placed == null) {
                continue;
            }
            placed.enervation = Math.max(placed.enervation, e.enervation);
            if (pestilence) {
                Pestilence.addCarrier(next, placed);
            }
            placedAny = true;
        }
        if (placedAny) {
            SkillMarkers.arc(roll.level, source, next);
            Blightcaller.LOGGER.debug("[Blightcaller] Infectious Curses: {} from {} to {}", roll.curses, source, next);
        }
    }
}
