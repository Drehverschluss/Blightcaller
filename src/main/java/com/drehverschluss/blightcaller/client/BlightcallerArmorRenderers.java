package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.item.BlightcallerItems;
import net.minecraft.resources.ResourceLocation;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.spell_engine.rpg_series.item.Armor;

/**
 * Armor looks of the Blightcaller robes (Armor Model API).
 * <p>
 * Tier 1 to 3 share one model, {@code blightcaller:geo/blightcaller_robe.geo.json}: a slim robe with a round hood whose
 * tip falls down the back, a short stepped shoulder cape, a thin belt and a skirt of thin plates. The tiers differ only by
 * their texture: light wool lilac (tier 1, no glow), blue violet with bone-white hems and pale green rot (tier 2), the same
 * with netherite grey edges (tier 3). Tier 2 and 3 glow through {@code <texture>_glowmask.png}: the diamond on the hood,
 * the runes on the chest, the eye on the back and the belt stone. A resource pack can replace model, textures and glow
 * masks by these ids.
 */
public final class BlightcallerArmorRenderers {
    private static final ResourceLocation ROBE_MODEL = Blightcaller.id("geo/blightcaller_robe.geo.json");

    private BlightcallerArmorRenderers() {}

    public static void register() {
        register(BlightcallerItems.blightcallerRobe, "blightcaller_robe", false);
        register(BlightcallerItems.blightRobe, "blight_robe", true);
        register(BlightcallerItems.netheriteBlightRobe, "netherite_blight_robe", true);
    }

    private static void register(Armor.Set<?> set, String texture, boolean glow) {
        if (set == null) {
            Blightcaller.LOGGER.error("Armor set {} is not registered, no armor renderer", texture);
            return;
        }
        GeoArmorRenderer renderer = GeoArmorRenderer.of(ROBE_MODEL, Blightcaller.id("textures/armor/" + texture + ".png"));
        if (glow) {
            // the glow layer draws the whole model a second time: only for the tiers that have glowing runes
            renderer = renderer.glow();
        }
        ArmorRenderers.register(renderer, set.head, set.chest, set.legs, set.feet);
    }
}
