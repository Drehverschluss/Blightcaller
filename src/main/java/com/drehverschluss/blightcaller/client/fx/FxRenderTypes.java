package com.drehverschluss.blightcaller.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Own render types of the visuals. Everything else uses vanilla types: the chain links {@code entityCutoutNoCull} on the
 * block atlas, their sheen and the soul smoke core {@code eyes} (additive, full bright), the dark haze of the soul smoke
 * {@code entityTranslucentEmissive}.
 */
public final class FxRenderTypes extends RenderType {
    /** A plain white texture (vanilla, by reference). */
    public static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /**
     * Silhouette of a ghost ({@link EntityGhost}): the entity's model in one colour, white texture tinted by the vertex
     * colour, translucent, full bright (emissive shader), no depth write, back faces culled (no doubled inner faces).
     */
    public static final RenderType GHOST = create("blightcaller_ghost", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(WHITE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    /** Mob effect icons (the curse icons above the head and at the Health Bars box): the vanilla effect atlas, by reference. */
    public static final ResourceLocation MOB_EFFECT_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/mob_effects.png");

    /**
     * Flat coloured shapes of the curse display (rings, backgrounds): the shader of the name tag background (so shader packs
     * treat them like name tags), translucent, both sides, no depth write, so they work the same in the world (camera facing)
     * and in the GUI. Not sorted by distance: the ring must lie over its dark track in the order it is drawn.
     */
    public static final RenderType FLAT = create("blightcaller_flat", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS,
            1536, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_BACKGROUND_SHADER)
                    .setTextureState(NO_TEXTURE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * The curse icons: vanilla text on the mob effect atlas, the same render type as the letters of a name tag (shader
     * packs draw it like name tags; Phase 6.2: an own type with the text shader went dark at night with Complementary).
     */
    public static final RenderType ICON = RenderType.text(MOB_EFFECT_ATLAS);

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                          boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
