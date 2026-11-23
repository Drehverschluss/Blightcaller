package com.drehverschluss.blightcaller.client;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.item.BlightcallerItems;
import net.minecraft.resources.ResourceLocation;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.spell_engine.rpg_series.item.Armor;

/**
 * Armor looks of the Blightcaller robes, tier 1 to 4 (Armor Model API).
 * <p>
 * Tier 1 to 3 share one model, {@code blightcaller:geo/blightcaller_robe.geo.json}: a slim robe with a round hood whose
 * tip falls down the back, a short stepped shoulder cape, a thin belt and a skirt of thin plates. The tiers differ only by
 * their texture: light wool lilac (tier 1, no glow), blue violet with bone-white hems and pale green rot (tier 2), the same
 * with netherite grey edges (tier 3). Tier 2 and 3 glow through {@code <texture>_glowmask.png}: the diamond on the hood,
 * the runes on the chest, the eye on the back and the belt stone.
 * <p>
 * Tier 4 has one model per set (128 x 128 textures):
 * <ul>
 *     <li>Blightspore ({@code geo/blightspore_robe.geo.json}): plague mask of bone white leather with glowing glass eyes
 *     and a short beak under a pointed hat with a wide drooping brim, shelf fungi and pale mushrooms; moss shoulder pieces
 *     with a cluster of glowing spore balls over narrower frayed moss sleeves; a vein tree on the back; dagged blue violet
 *     panels over a wide moss skirt with mushrooms at the hem; leather boots with moss wraps. Glows pale sickly green.</li>
 *     <li>Hexbinder ({@code geo/hexbinder_robe.geo.json}): near black square topped hood around a skull mask with a large
 *     brow eye and a ring of rib spikes; a short ritual mantle with bone charms, rib spines and a skull on each shoulder;
 *     bone bead necklace, vertebrae and a ritual sash with runes; a back panel with a slit eye in a rune ring; a wide skirt
 *     with a bone bead fringe and a long tabard with runes; boots with stacked bone plates. Glows violet.</li>
 * </ul>
 * Textures {@code textures/armor/<id>.png}, glow masks {@code <id>_glowmask.png} (found by the Armor Model API). A resource
 * pack can replace models, textures and glow masks by these ids.
 */
public final class BlightcallerArmorRenderers {
    private static final ResourceLocation ROBE_MODEL = Blightcaller.id("geo/blightcaller_robe.geo.json");

    private BlightcallerArmorRenderers() {}

    public static void register() {
        register(BlightcallerItems.blightcallerRobe, ROBE_MODEL, "blightcaller_robe", false);
        register(BlightcallerItems.blightRobe, ROBE_MODEL, "blight_robe", true);
        register(BlightcallerItems.netheriteBlightRobe, ROBE_MODEL, "netherite_blight_robe", true);
        register(BlightcallerItems.blightsporeRobe, Blightcaller.id("geo/blightspore_robe.geo.json"), "blightspore_robe", true);
        register(BlightcallerItems.hexbinderRobe, Blightcaller.id("geo/hexbinder_robe.geo.json"), "hexbinder_robe", true);
    }

    private static void register(Armor.Set<?> set, ResourceLocation model, String texture, boolean glow) {
        if (set == null) {
            Blightcaller.LOGGER.error("Armor set {} is not registered, no armor renderer", texture);
            return;
        }
        GeoArmorRenderer renderer = GeoArmorRenderer.of(model, Blightcaller.id("textures/armor/" + texture + ".png"));
        if (glow) {
            // the glow layer draws the whole model a second time: only for the tiers that have glowing parts
            renderer = renderer.glow();
        }
        ArmorRenderers.register(renderer, set.head, set.chest, set.legs, set.feet);
    }
}
