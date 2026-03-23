package org.vmstudio.stickalchemy.core.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AlchemyServerState {

    public static class CauldronData {
        public final ServerLevel level;
        public final BlockPos pos;
        public final ItemStack potion;
        public int expectedWaterLevel;

        public CauldronData(ServerLevel level, BlockPos pos, ItemStack potion, int expectedWaterLevel) {
            this.level = level;
            this.pos = pos;
            this.potion = potion;
            this.expectedWaterLevel = expectedWaterLevel;
        }
    }

    public static final Map<BlockPos, CauldronData> BREWED_CAULDRONS = new ConcurrentHashMap<>();
}
