package org.vmstudio.stickalchemy.forge;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.stickalchemy.core.client.ExampleAddonClient;
import org.vmstudio.stickalchemy.core.common.VisorExample;
import org.vmstudio.stickalchemy.core.server.ExampleAddonServer;
import net.minecraftforge.fml.common.Mod;

@Mod(VisorExample.MOD_ID)
public class ExampleMod {
    public ExampleMod() {
        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new ExampleAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );
        }
    }
}
