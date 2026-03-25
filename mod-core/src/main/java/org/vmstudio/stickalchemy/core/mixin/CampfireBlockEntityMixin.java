package org.vmstudio.stickalchemy.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireBlock.class)
public class CampfireBlockEntityMixin {

    @Inject(method = "makeParticles", at = @At("HEAD"), cancellable = true)
    private static void stopSmokeUnderCauldron(Level level, BlockPos pos, boolean signalFire, boolean spawnExtraSmoke, CallbackInfo ci) {
        if (level.getBlockState(pos.above()).getBlock() instanceof AbstractCauldronBlock) {
            ci.cancel();
        }
    }
}
