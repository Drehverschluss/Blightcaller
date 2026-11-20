package com.drehverschluss.blightcaller.compat.jade;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.compat.Couplings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade plugin of Blightcaller (concept 8). Jade finds it by its annotation and only when Jade is installed; nothing else in
 * Blightcaller refers to this package, so without Jade none of these classes is ever loaded.
 * <p>
 * One provider ({@link CurseJadeProvider}, {@code blightcaller:curses}) for every living entity: the data comes from the
 * server (correct in multiplayer), the client draws it. Jade adds the on/off switch for the provider by itself; the display
 * style ({@code blightcaller:curses.style}, compact or list) is an extra option next to it in Jade's plugin settings.
 */
@WailaPlugin(Blightcaller.MODID)
public class BlightcallerJadePlugin implements IWailaPlugin {
    public static final ResourceLocation CURSES = Blightcaller.id("curses");
    public static final ResourceLocation STYLE = Blightcaller.id("curses.style");

    /** Display of the curses in the tooltip. */
    public enum Style {
        /** Curse level and one icon with its remaining time per curse, all in one line. */
        COMPACT,
        /** Curse level, then one line per curse with icon, name and remaining time. */
        LIST
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(CurseJadeProvider.INSTANCE, LivingEntity.class);
        Couplings.found("Jade", "curse tooltip registered");
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // list by default: narrower than the compact line with three curses and the names are readable (Phase 6 pictures)
        registration.addConfig(STYLE, Style.LIST);
        registration.registerEntityComponent(CurseJadeProvider.INSTANCE, LivingEntity.class);
    }
}
