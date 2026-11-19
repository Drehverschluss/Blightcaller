package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The curse effects of Blightcaller. */
public final class CurseEffects {
    /** Violet of the class (Shadowy cut of Jewelry): Soulburn. */
    public static final int VIOLET = 0x8A5BE0;
    /** Sickly pale green: Soul Rot. */
    public static final int ROT_GREEN = 0x7FB24A;
    /** Grey violet: Enfeeble. */
    public static final int ASH_VIOLET = 0x7D7088;

    /** All curses. The curse level of a target counts the different effects of this tag on it. */
    public static final TagKey<MobEffect> CURSES = TagKey.create(Registries.MOB_EFFECT, Blightcaller.id("curses"));

    /**
     * Entities that resist the weakening part of curses (Enfeeble only gives half its bonus, later also the slow of
     * Gravebind). Damage over time is never reduced. Contains {@code #c:bosses} (optional), the Warden and the Elder
     * Guardian.
     */
    public static final TagKey<EntityType<?>> CURSE_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, Blightcaller.id("curse_resistant"));

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Blightcaller.MODID);

    /** Soulburn / Seelenbrand: the short curse of Hex Bolt and Blight Burst. */
    public static final DeferredHolder<MobEffect, MobEffect> SOULBURN = EFFECTS.register("soulburn", () -> new CurseEffect(VIOLET));
    /** Soul Rot / Seelenfäule: the long core curse (spell book, tier 2). */
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_ROT = EFFECTS.register("soul_rot", () -> new CurseEffect(ROT_GREEN));
    /** Enfeeble / Entkräftung: curse without damage, the target takes more damage from all sources. */
    public static final DeferredHolder<MobEffect, MobEffect> ENFEEBLE = EFFECTS.register("enfeeble", () -> new EnfeebleEffect(ASH_VIOLET));

    private CurseEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
