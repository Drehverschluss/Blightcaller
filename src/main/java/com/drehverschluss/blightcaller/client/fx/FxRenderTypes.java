package com.drehverschluss.blightcaller.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Untextured glow of the Drain Soul band: the vanilla lightning shader (position + colour, full bright), additive blending,
 * no depth write (glow layers add up) and no backface culling. Same set-up as the lightning arcs of Stormcaller (own code).
 */
public final class FxRenderTypes extends RenderType {
    public static final RenderType GLOW = create("blightcaller_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                          boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
