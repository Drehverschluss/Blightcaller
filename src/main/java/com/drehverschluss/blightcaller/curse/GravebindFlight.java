package com.drehverschluss.blightcaller.curse;

import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gravebind in the air. Flyers mostly ignore the movement speed attribute: the Wither, Ghast, Phantom and the Ender Dragon
 * steer their velocity themselves, and {@code FlyingMoveControl} reads {@code minecraft:generic.flying_speed} instead. A
 * flying speed modifier would only reach part of them, and Spell Engine would then count Gravebind as movement impairing
 * and skip it on {@code #spell_engine:bosses} (Phase 5 report 4.1).
 * <p>
 * So for a bound target in the air the way it covered this tick is shortened instead: after its tick it is moved back
 * towards where it started, to {@code factor} of the way. The factor is the same share of the way a bound mob still walks on
 * the ground, (1 - slow)^2 (mobs walk with the square of the speed attribute, Phase 5 report 4.1): 16 % for normal
 * targets, 49 % for bosses. Its velocity is left alone, so it keeps trying and moves on normally once the curse ends.
 * <p>
 * Only for entities that fly (flying mobs, flying animals, {@code FlyingMoveControl}, the Ender Dragon, entities without
 * gravity) while they are in the air; never players (they move on their own client), riders or vehicles. The point it is
 * moved back to lies on the straight way it just took; if that box would collide, nothing happens in that tick.
 */
public final class GravebindFlight {
    private GravebindFlight() {}

    static void tick(LivingEntity entity, CurseData data) {
        if (!entity.isAlive() || entity instanceof Player || entity.isPassenger() || entity.isVehicle()) {
            return;
        }
        var id = Curses.id(CurseEffects.GRAVEBIND);
        if (data.entries(id).isEmpty() || !flying(entity)) {
            return;
        }
        double factor = factor(data.amplifier(id));
        Vec3 from = new Vec3(entity.xo, entity.yo, entity.zo);
        Vec3 to = entity.position();
        Vec3 way = to.subtract(from);
        if (way.lengthSqr() < 1.0E-8 || way.lengthSqr() > 64.0) {
            // standing still, or teleported
            return;
        }
        Vec3 target = from.add(way.scale(factor));
        AABB box = entity.getBoundingBox().move(target.subtract(to));
        if (!(entity instanceof EnderDragon) && !entity.level().noCollision(entity, box)) {
            return;
        }
        entity.setPos(target.x, target.y, target.z);
    }

    /** Share of the way a bound flyer still covers per tick with this Gravebind amplifier. */
    public static double factor(int amplifier) {
        double keep = 1.0 - GravebindEffect.slow(amplifier);
        return keep * keep;
    }

    /** Whether the entity flies right now (in the air and steering itself there). */
    public static boolean flying(LivingEntity entity) {
        if (entity.onGround() || entity.isInWater() || entity.isInLava()) {
            return false;
        }
        if (entity instanceof EnderDragon || entity instanceof FlyingMob || entity instanceof FlyingAnimal || entity.isNoGravity()) {
            return true;
        }
        return entity instanceof Mob mob && mob.getMoveControl() instanceof FlyingMoveControl;
    }
}
