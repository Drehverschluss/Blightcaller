package com.drehverschluss.blightcaller.client.display;

import com.drehverschluss.blightcaller.client.fx.FxRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.jetbrains.annotations.Nullable;

/**
 * The sprites of the curse display. Besides its normal icon every curse can have two more pictures next to the effect icons,
 * so the vanilla mob effect atlas stitches them like any effect icon (directory source {@code mob_effect}):
 * <ul>
 *     <li>{@code textures/mob_effect/mini/<curse>.png}: the small symbol above the head (style SIMPLE), the icon scaled down
 *     beforehand, which stays clearer at 9 to 12 pixels than the 18 x 18 icon squeezed by the GPU;</li>
 *     <li>{@code textures/mob_effect/faded/<curse>.png}: the grey, a little darker version of the icon for the expired part
 *     of the tile (style DETAILED and the row at the Health Bars box).</li>
 * </ul>
 * A curse without them (for example from a pack) shows its normal icon, and a dark tint of it as the faded part.
 */
final class CurseSprites {
    private CurseSprites() {}

    /** The sprite of a curse: the small symbol when asked for and present, else the mob effect icon. */
    static TextureAtlasSprite get(Holder<MobEffect> curse, boolean simple) {
        if (simple) {
            TextureAtlasSprite mini = extra(curse, "mini/");
            if (mini != null) {
                return mini;
            }
        }
        return Minecraft.getInstance().getMobEffectTextures().get(curse);
    }

    /** The faded (grey) version of the icon, or {@code null} if the curse has none. */
    @Nullable
    static TextureAtlasSprite faded(Holder<MobEffect> curse) {
        return extra(curse, "faded/");
    }

    @Nullable
    private static TextureAtlasSprite extra(Holder<MobEffect> curse, String prefix) {
        ResourceLocation id = curse.unwrapKey().map(key -> key.location()).orElse(null);
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(FxRenderTypes.MOB_EFFECT_ATLAS);
        if (id != null && texture instanceof TextureAtlas atlas) {
            TextureAtlasSprite sprite = atlas.getSprite(id.withPrefix(prefix));
            if (!MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
                return sprite;
            }
        }
        return null;
    }
}
