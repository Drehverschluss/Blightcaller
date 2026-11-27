package com.drehverschluss.blightcaller.client.fx;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.config.BlightcallerClientConfig;
import com.drehverschluss.blightcaller.fx.BlightParticleType;
import com.drehverschluss.blightcaller.fx.BlightcallerParticles;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Cast particles at the tip of the weapon (phase 14B1): while a player casts one of Blightcaller's spells with a cast time,
 * small particles in the spell's colour role rise from the tip of the wand or the upper corner of the grimoire, instead of
 * Spell Engine's particles at the launch point (taken out of the spell files).
 * <ul>
 *     <li><b>Where:</b> the tip found while the item in the hand is drawn ({@link HandTip}, the same as Drain Soul's strands),
 *     in first and third person, also for other players (Spell Engine syncs their cast process to every client).</li>
 *     <li><b>Colour roles:</b> curses violet (hex embers: Hex Bolt, Enfeeble, Gravebind), rot green (rot drips and a little rot
 *     smoke: Soul Rot, Blight Burst), Pestilence spores. Drain Soul has its strands, Last Rites no cast time.</li>
 *     <li><b>First person:</b> a third as many as in third person, smaller, never a skull and nothing near the middle of the
 *     screen. With a grimoire the tip is below the screen while casting (Spell Engine's animation): there every particle rises,
 *     so it drifts into the lower right of the picture (rot drips become rot smoke, drips cannot rise). Third person now and
 *     then a small hex skull for the violet spells (user at the halt of 14B1: first person as it was, third person 1.5 times).</li>
 *     <li>Client option {@code casting.weapon_particles}; the particles thin out with Minecraft's particle setting
 *     ({@link BlightParticle}).</li>
 * </ul>
 */
public final class CastFx {
    /** Colour role of a spell's cast particles. */
    enum Look {
        HEX,
        ROT,
        SPORE
    }

    private static final Map<ResourceLocation, Look> SPELLS = Map.of(
            Blightcaller.id("hex_bolt"), Look.HEX,
            Blightcaller.id("enfeeble"), Look.HEX,
            Blightcaller.id("gravebind"), Look.HEX,
            Blightcaller.id("soul_rot"), Look.ROT,
            Blightcaller.id("blight_burst"), Look.ROT,
            Blightcaller.id("pestilence"), Look.SPORE);

    private static final long VIOLET = 0x6F5BE8FFL;
    private static final long ROT_GREEN = 0x5FBF5AFFL;
    private static final long ROT_SMOKE = 0x5FBF5A99L;
    private static final long SPORE = 0xC4D445FFL;
    /** Particles per tick in third person, by kind. */
    static final float EMBERS = 1.8F;
    static final float SKULLS = 0.04F;
    static final float DRIPS = 1.05F;
    static final float SMOKE = 0.375F;
    static final float SPORES = 1.8F;
    /** Share of the third person amount in first person. */
    static final float FIRST_PERSON_SHARE = 1.0F / 3.0F;
    /** Rise of every particle in first person with a grimoire (blocks per tick at the start). */
    static final double BOOK_RISE = 0.06;
    /** Size factor in first person (the tip is less than a block from the eye). */
    static final float FIRST_PERSON_SCALE = 0.75F;
    /** No particle within this angle of the middle of the screen (first person), in degrees. */
    static final float FIRST_PERSON_FREE_ANGLE = 14.0F;
    private static final double RANGE = 48.0;

    private static final Map<Integer, Long> LAST_TICK = new HashMap<>();
    /** Particles spawned since the start (probe, report). */
    static int spawned;

    private CastFx() {}

    /** The colour role while the player casts one of our spells with a cast time, else null. */
    @Nullable
    static Look look(Player player) {
        if (!(player instanceof SpellCaster.Entity caster)) {
            return null;
        }
        SpellCast.Process process = caster.getSpellCastProcess();
        if (process == null) {
            return null;
        }
        return process.spell().unwrapKey().map(k -> SPELLS.get(k.location())).orElse(null);
    }

    /** Whether the tip of this player's item is needed this frame ({@link HandTip}). */
    static boolean wantsTip(Player player) {
        return BlightcallerClientConfig.castWeaponParticles() && look(player) != null;
    }

    /** Called once per frame from the visuals' render pass; spawns once per game tick and caster. */
    static void render(Camera camera, Matrix4f projection, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !BlightcallerClientConfig.castWeaponParticles()) {
            LAST_TICK.clear();
            return;
        }
        long tick = level.getGameTime();
        if (tick % 200 == 0) {
            LAST_TICK.values().removeIf(t -> tick - t > 100);
        }
        for (Player player : level.players()) {
            Look look = look(player);
            if (look == null || player.isInvisible() || player.position().distanceToSqr(camera.getPosition()) > RANGE * RANGE) {
                continue;
            }
            Long last = LAST_TICK.get(player.getId());
            if (last != null && last == tick) {
                continue;
            }
            LAST_TICK.put(player.getId(), tick);
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 tip = HandTip.get(player, camera, projection);
            if (tip == null) {
                // no capture yet (first frame of the cast) or the item is not drawn: skip rather than guess
                continue;
            }
            boolean rise = firstPerson && HandTip.isBook(player.getMainHandItem());
            spawn(level, look, tip, firstPerson, rise, player, partialTick);
        }
    }

    private static void spawn(ClientLevel level, Look look, Vec3 tip, boolean firstPerson, boolean rise, Player player, float partialTick) {
        RandomSource random = level.random;
        float share = firstPerson ? FIRST_PERSON_SHARE : 1.0F;
        float scale = firstPerson ? FIRST_PERSON_SCALE : 1.0F;
        double up = rise ? BOOK_RISE : 0.0;
        switch (look) {
            case HEX -> {
                if (chance(random, EMBERS * share)) {
                    add(level, BlightcallerParticles.HEX_EMBER.get(), VIOLET, 0.7F * scale, tip, random, 0.012, 0.012 + up, 0.025 + up, firstPerson,
                            player, partialTick);
                }
                if (!firstPerson && chance(random, SKULLS)) {
                    add(level, BlightcallerParticles.HEX_SKULL.get(), VIOLET, 0.45F, tip, random, 0.006, 0.01, 0.02, false, player, partialTick);
                }
            }
            case ROT -> {
                if (rise) {
                    // drips cannot rise: rot smoke only, as many as drips and smoke together
                    if (chance(random, (DRIPS + SMOKE) * share)) {
                        add(level, BlightcallerParticles.ROT_SMOKE.get(), ROT_SMOKE, 0.45F * scale, tip, random, 0.008, 0.008 + up, 0.015 + up,
                                true, player, partialTick);
                    }
                    return;
                }
                if (chance(random, DRIPS * share)) {
                    add(level, BlightcallerParticles.ROT_DRIP.get(), ROT_GREEN, 1.1F * scale, tip, random, 0.006, 0.0, 0.0, firstPerson, player,
                            partialTick);
                }
                if (chance(random, SMOKE * share)) {
                    add(level, BlightcallerParticles.ROT_SMOKE.get(), ROT_SMOKE, 0.45F * scale, tip, random, 0.008, 0.008, 0.015, firstPerson, player,
                            partialTick);
                }
            }
            case SPORE -> {
                if (chance(random, SPORES * share)) {
                    add(level, BlightcallerParticles.SPORE.get(), SPORE, 1.1F * scale, tip, random, 0.012, -0.004 + up, 0.008 + up, firstPerson,
                            player, partialTick);
                }
            }
        }
    }

    private static boolean chance(RandomSource random, float perTick) {
        int whole = (int) perTick;
        return whole > 0 || random.nextFloat() < perTick - whole;
    }

    /** One particle a little around the tip, moving sideways by up to {@code side} and upwards by {@code upMin..upMax}. */
    private static void add(ClientLevel level, BlightParticleType type, long color, float scale, Vec3 tip, RandomSource random, double side,
                            double upMin, double upMax, boolean firstPerson, Player player, float partialTick) {
        double j = firstPerson ? 0.015 : 0.04;
        Vec3 at = tip.add((random.nextDouble() - 0.5) * 2 * j, (random.nextDouble() - 0.5) * 2 * j, (random.nextDouble() - 0.5) * 2 * j);
        if (firstPerson) {
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 look = player.getViewVector(partialTick);
            Vec3 to = at.subtract(eye);
            double len = to.length();
            if (len < 1.0E-4 || to.dot(look) / len > Math.cos(Math.toRadians(FIRST_PERSON_FREE_ANGLE))) {
                return;
            }
        }
        double vx = (random.nextDouble() - 0.5) * 2 * side;
        double vz = (random.nextDouble() - 0.5) * 2 * side;
        double vy = upMin + random.nextDouble() * (upMax - upMin);
        level.addParticle(type.with(color, scale, null), true, at.x, at.y, at.z, vx, vy, vz);
        spawned++;
    }
}
