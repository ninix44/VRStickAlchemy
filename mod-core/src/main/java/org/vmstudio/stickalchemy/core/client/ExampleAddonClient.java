package org.vmstudio.stickalchemy.core.client;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.stickalchemy.core.client.overlays.VROverlayExample;
import org.vmstudio.stickalchemy.core.client.overlays.VROverlayTemplateExample;
import org.vmstudio.stickalchemy.core.common.VisorExample;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.render.RenderPipelineStage;
import org.vmstudio.visor.api.common.addon.VisorAddon;

import java.util.List;

public class ExampleAddonClient implements VisorAddon {
    @Override
    public void onAddonLoad() {
        VisorAPI.addonManager().getRegistries()
            .overlays()
            .registerComponents(
                List.of(
                    new VROverlayExample(
                        this,
                        VROverlayExample.ID
                    )
                                /*
                                , new VROverlayTemplateExample(
                                        this,
                                        VROverlayTemplateExample.ID
                                )
                                */
                )
            );

        ModLoader.get().addToRenderPipeline(
            RenderPipelineStage.AFTER_TRANSLUCENT,
            CauldronSwirlRenderer::renderSwirls
        );
    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.stickalchemy.core.client";
    }

    @Override
    public @NotNull String getAddonId() {
        return VisorExample.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal(VisorExample.MOD_NAME);
    }

    @Override
    public String getModId() {
        return VisorExample.MOD_ID;
    }
}
