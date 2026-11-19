package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The curse effects of Blightcaller. */
public final class CurseEffects {
    /** Violet of the class (Shadowy cut of Jewelry), also the effect color of all curses for now. */
    public static final int VIOLET = 0x8A5BE0;

    /** All curses. The curse level of a target counts the different effects of this tag on it. */
    public static final TagKey<MobEffect> CURSES = TagKey.create(Registries.MOB_EFFECT, Blightcaller.id("curses"));

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Blightcaller.MODID);

    /** Soulburn / Seelenbrand: the short curse of Hex Bolt. */
    public static final DeferredHolder<MobEffect, MobEffect> SOULBURN = EFFECTS.register("soulburn", () -> new CurseEffect(VIOLET));

    private CurseEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
