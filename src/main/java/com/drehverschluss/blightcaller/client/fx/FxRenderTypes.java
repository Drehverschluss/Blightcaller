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

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                          boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
