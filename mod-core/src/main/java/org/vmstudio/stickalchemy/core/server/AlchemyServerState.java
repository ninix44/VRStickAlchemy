package org.vmstudio.stickalchemy.core.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

public class AlchemyServerState {
    public static final Map<BlockPos, ItemStack> BREWED_CAULDRONS = new HashMap<>();
}
