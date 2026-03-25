package org.vmstudio.stickalchemy.core.common;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class CauldronHeatLogic {

    private CauldronHeatLogic() {
    }

    public static boolean isHeated(Level level, BlockPos cauldronPos) {
        if (level == null) {
            return false;
        }

        BlockState heatSourceState = level.getBlockState(cauldronPos.below());
        return heatSourceState.getFluidState().is(FluidTags.LAVA) || isLitCampfire(heatSourceState);
    }

    public static boolean isFullWaterCauldron(BlockState state) {
        return state.is(Blocks.WATER_CAULDRON)
            && state.hasProperty(LayeredCauldronBlock.LEVEL)
            && state.getValue(LayeredCauldronBlock.LEVEL) == 3;
    }

    private static boolean isLitCampfire(BlockState state) {
        return (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
            && state.hasProperty(CampfireBlock.LIT)
            && state.getValue(CampfireBlock.LIT);
    }
}

