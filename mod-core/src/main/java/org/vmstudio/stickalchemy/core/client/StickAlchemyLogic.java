package org.vmstudio.stickalchemy.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseClient;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.stickalchemy.core.common.CauldronHeatLogic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StickAlchemyLogic {

    public interface NetworkBridge {
        void sendFinishStir(BlockPos pos);
        void sendScoopPotion(BlockPos pos, boolean isMainHand);
        void sendPlaceIngredient(BlockPos pos, boolean isMainHand);
        void sendExtractIngredient(BlockPos pos, boolean isMainHand);
        void sendStirCauldron(BlockPos pos, float direction);
    }

    public static NetworkBridge bridge;

    public static final Map<BlockPos, Integer> CAULDRON_COLORS = new HashMap<>();
    public static final Map<BlockPos, StirVisualState> STIR_VISUALS = new HashMap<>();

    private static final Map<BlockPos, Integer> stirProgress = new HashMap<>();

    private static Vec3 lastMainPos = null;
    private static Vec3 lastOffPos = null;

    private static int scoopCooldown = 0;
    private static int extractCooldown = 0;
    private static int ambientBoilSoundCooldown = 0;
    private static int stirSyncCooldown = 0;

    private static int mainHandHoldTicks = 0;
    private static int offHandHoldTicks = 0;
    private static final int TARGET_HOLD_TIME = 30;
    private static final int TARGET_STIR_PROGRESS = 120;
    private static final double MIN_STIR_VISUAL_SPEED = 0.004;
    private static final boolean DEBUG_SWIRL_CHAT = false;
    private static int swirlDebugCooldown = 0;

    public static class StirVisualState {
        public double flowX;
        public double flowZ;
        public double spinVelocity;
        public double swirlAngle;
        public double energy;
        public int waterLevel;
    }

    public static void resetCauldronStirState(BlockPos pos) {
        stirProgress.remove(pos);
        STIR_VISUALS.remove(pos);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.screen != null) return;

        if (scoopCooldown > 0) scoopCooldown--;
        if (extractCooldown > 0) extractCooldown--;
        if (ambientBoilSoundCooldown > 0) ambientBoilSoundCooldown--;
        if (stirSyncCooldown > 0) stirSyncCooldown--;
        if (swirlDebugCooldown > 0) swirlDebugCooldown--;

        tickStirVisuals(mc);
        tickAmbientBoiling(mc);

        VRLocalPlayer vrPlayer = VisorAPI.client().getVRLocalPlayer();
        if (vrPlayer == null || !VisorAPI.clientState().playMode().canPlayVR()) return;

        PlayerPoseClient poseTick = vrPlayer.getPoseData(PlayerPoseType.TICK);

        Set<BlockPos> heatedCauldronsThisTick = new HashSet<>();

        lastMainPos = processHand(mc, poseTick.getMainHand(), InteractionHand.MAIN_HAND, HandType.MAIN, true, lastMainPos, heatedCauldronsThisTick);
        lastOffPos = processHand(mc, poseTick.getOffhand(), InteractionHand.OFF_HAND, HandType.OFFHAND, false, lastOffPos, heatedCauldronsThisTick);
    }

    private static void resetTimers(boolean isMain) {
        if (isMain) mainHandHoldTicks = 0;
        else offHandHoldTicks = 0;
    }

    private static Vec3 processHand(Minecraft mc, VRPose handPose, InteractionHand mcHand, HandType vrHand, boolean isMain, Vec3 lastPos, Set<BlockPos> heatedCauldronsThisTick) {
        Vec3 handPos = new Vec3(handPose.getPosition().x(), handPose.getPosition().y(), handPose.getPosition().z());

        Vector3f offset = new Vector3f(0, 0.43f, -0.25f);
        Vector3f tipJoml = handPose.getCustomVector(offset).add(handPose.getPosition());
        Vec3 stickTipPos = new Vec3(tipJoml.x(), tipJoml.y(), tipJoml.z());

        ItemStack itemInHand = mc.player.getItemInHand(mcHand);
        boolean isHoldingStick = itemInHand.is(Items.STICK);
        boolean isHoldingBottle = itemInHand.is(Items.GLASS_BOTTLE);
        boolean isEmpty = itemInHand.isEmpty();

        boolean isValidIngredient = PotionBrewing.isIngredient(itemInHand);

        boolean isHoldingIngredient = !isHoldingStick && !isHoldingBottle && !isEmpty && isValidIngredient;

        Vec3 activePos = isHoldingStick ? stickTipPos : handPos;

        if (lastPos == null) {
            return activePos;
        }

        double speed = activePos.distanceTo(lastPos);

        AABB handBox = new AABB(handPos, handPos).inflate(0.05);
        AABB stickBox = new AABB(stickTipPos, stickTipPos).inflate(0.02);

        BlockPos centerPos = BlockPos.containing(activePos);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos targetPos = centerPos.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(targetPos);

                    if (state.is(Blocks.WATER_CAULDRON)) {
                        boolean isHeated = CauldronHeatLogic.isHeated(mc.level, targetPos);
                        boolean isFullWater = CauldronHeatLogic.isFullWaterCauldron(state);

                        if (isHeated && heatedCauldronsThisTick.add(targetPos)) {
                            spawnBoilingEffects(mc, targetPos);
                        }

                        AABB cauldronWaterSurface = new AABB(
                            targetPos.getX() + 0.15, targetPos.getY() + 0.3, targetPos.getZ() + 0.15,
                            targetPos.getX() + 0.85, targetPos.getY() + 1.1, targetPos.getZ() + 0.85
                        );

                        AABB cauldronStirZone = new AABB(
                            targetPos.getX() + 0.15, targetPos.getY() + 0.1, targetPos.getZ() + 0.15,
                            targetPos.getX() + 0.85, targetPos.getY() + 0.9, targetPos.getZ() + 0.85
                        );

                        if (isEmpty && handBox.intersects(cauldronWaterSurface) && extractCooldown <= 0) {
                            if (mc.options.keyUse.isDown() || mc.options.keyJump.isDown()) {
                                if (bridge != null) bridge.sendExtractIngredient(targetPos, isMain);
                                VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 300f, 0.5f, 0.1f);
                                mc.player.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.5f);

                                for(int i = 0; i < 5; i++) {
                                    mc.level.addParticle(ParticleTypes.POOF, targetPos.getX() + 0.5, targetPos.getY() + 0.9, targetPos.getZ() + 0.5, 0, 0.05, 0);
                                }
                                extractCooldown = 20;
                                return activePos;
                            }
                        }

                        if (isHoldingIngredient && handBox.intersects(cauldronWaterSurface)) {
                            if (!isHeated || !isFullWater) {
                                resetTimers(isMain);
                                return activePos;
                            }

                            AABB strictInnerCauldron = new AABB(
                                targetPos.getX() + 0.1, targetPos.getY(), targetPos.getZ() + 0.1,
                                targetPos.getX() + 0.9, targetPos.getY() + 1.0, targetPos.getZ() + 0.9
                            );
                            List<ItemDisplay> currentItems = mc.level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron);

                            if (currentItems.size() < 9) {
                                if (speed < 0.05) {
                                    int ticks = isMain ? ++mainHandHoldTicks : ++offHandHoldTicks;

                                    if (ticks % 10 == 0) {
                                        mc.level.addParticle(ParticleTypes.ENCHANT,
                                            handPos.x + (mc.level.random.nextDouble() - 0.5) * 0.2,
                                            handPos.y + 0.15,
                                            handPos.z + (mc.level.random.nextDouble() - 0.5) * 0.2,
                                            0, 0.05, 0);
                                        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 1.5f);
                                        VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 50f, 0.1f, 0.05f);
                                    }

                                    if (ticks >= TARGET_HOLD_TIME) {
                                        resetCauldronStirState(targetPos);
                                        if (bridge != null) bridge.sendPlaceIngredient(targetPos, isMain);
                                        VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 200f, 0.5f, 0.1f);
                                        mc.player.playSound(SoundEvents.SPLASH_POTION_THROW, 0.4f, 1.2f);
                                        resetTimers(isMain);
                                    }
                                } else {
                                    resetTimers(isMain);
                                }
                            }
                            return activePos;
                        }

                        if (isHoldingBottle && handBox.intersects(cauldronWaterSurface) && scoopCooldown <= 0) {
                            AABB strictInnerCauldron = new AABB(
                                targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                                targetPos.getX() + 1.0, targetPos.getY() + 1.0, targetPos.getZ() + 1.0
                            );
                            List<ItemDisplay> currentItems = mc.level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron);

                            if (!currentItems.isEmpty()) {
                                return activePos;
                            }

                            if (bridge != null) {
                                resetCauldronStirState(targetPos);
                                bridge.sendScoopPotion(targetPos, isMain);
                                VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 200f, 0.5f, 0.1f);
                                scoopCooldown = 20;
                            }
                            return activePos;
                        }

                        if (isHoldingStick && stickBox.intersects(cauldronStirZone)) {
                            if (speed > 0.005) {
                                Vec3 motion = stickTipPos.subtract(lastPos);
                                updateStirVisual(targetPos, state, stickTipPos, motion);
                                syncStirMotion(targetPos, stickTipPos, motion);
                                int progress = stirProgress.getOrDefault(targetPos, 0) + 1;
                                stirProgress.put(targetPos, progress);

                                if (progress % 5 == 0) {
                                    VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 150f, 0.3f, 0.05f);
                                    mc.player.playSound(SoundEvents.WATER_AMBIENT, 0.5f, 1.0f + (mc.level.random.nextFloat() * 0.5f));

                                    mc.level.addParticle(ParticleTypes.SPLASH,
                                        stickTipPos.x + (mc.level.random.nextDouble() - 0.5) * 0.2,
                                        stickTipPos.y,
                                        stickTipPos.z + (mc.level.random.nextDouble() - 0.5) * 0.2,
                                        (mc.level.random.nextDouble() - 0.5) * 0.2, 0.1, (mc.level.random.nextDouble() - 0.5) * 0.2);
                                }
                                if (progress >= TARGET_STIR_PROGRESS) {
                                    VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 400f, 1.0f, 0.4f);
                                    resetCauldronStirState(targetPos);
                                    if (bridge != null) bridge.sendFinishStir(targetPos);
                                }
                            }
                            return activePos;
                        }
                    }
                }
            }
        }

        resetTimers(isMain);
        return activePos;
    }

    private static void tickAmbientBoiling(Minecraft mc) {
        BlockPos playerPos = mc.player.blockPosition();

        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos targetPos = playerPos.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(targetPos);

                    if (CauldronHeatLogic.isFullWaterCauldron(state) && CauldronHeatLogic.isHeated(mc.level, targetPos)) {
                        spawnBoilingEffects(mc, targetPos);
                    }
                }
            }
        }
    }

    private static void tickStirVisuals(Minecraft mc) {
        STIR_VISUALS.entrySet().removeIf(entry -> {
            BlockState state = mc.level.getBlockState(entry.getKey());
            if (!state.is(Blocks.WATER_CAULDRON)) {
                return true;
            }

            StirVisualState visual = entry.getValue();
            visual.flowX *= 0.9;
            visual.flowZ *= 0.9;
            visual.spinVelocity *= 0.92;
            visual.swirlAngle += visual.spinVelocity;
            visual.energy *= 0.94;
            visual.waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);

            return visual.energy < 0.025 && Math.abs(visual.spinVelocity) < 0.002
                && Math.abs(visual.flowX) < 0.002 && Math.abs(visual.flowZ) < 0.002;
        });
    }

    private static void updateStirVisual(BlockPos cauldronPos, BlockState state, Vec3 stickTipPos, Vec3 motion) {
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontalSpeed < MIN_STIR_VISUAL_SPEED) {
            return;
        }

        StirVisualState visual = STIR_VISUALS.computeIfAbsent(cauldronPos, ignored -> new StirVisualState());
        Vec3 center = new Vec3(cauldronPos.getX() + 0.5, stickTipPos.y, cauldronPos.getZ() + 0.5);
        Vec3 radial = new Vec3(
            stickTipPos.x - center.x,
            0.0,
            stickTipPos.z - center.z
        );

        double angularImpulse = radial.x * motion.z - radial.z * motion.x;
        visual.flowX = clamp(visual.flowX * 0.72 + motion.x * 6.4, -0.42, 0.42);
        visual.flowZ = clamp(visual.flowZ * 0.72 + motion.z * 6.4, -0.42, 0.42);
        visual.spinVelocity = clamp(visual.spinVelocity * 0.65 + angularImpulse * 12.0, -0.60, 0.60);
        visual.energy = clamp(Math.max(visual.energy * 0.88, horizontalSpeed * 26.0 + Math.abs(angularImpulse) * 22.0), 0.0, 2.4);
        visual.waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);

        if (DEBUG_SWIRL_CHAT && swirlDebugCooldown <= 0) {
            debugSwirlChat(String.format(
                "stir %d %d %d | fx=%.3f fz=%.3f spin=%.3f e=%.3f",
                cauldronPos.getX(),
                cauldronPos.getY(),
                cauldronPos.getZ(),
                visual.flowX,
                visual.flowZ,
                visual.spinVelocity,
                visual.energy
            ));
            swirlDebugCooldown = 20;
        }
    }

    private static void syncStirMotion(BlockPos cauldronPos, Vec3 stickTipPos, Vec3 motion) {
        if (bridge == null || stirSyncCooldown > 0) {
            return;
        }

        double direction = calculateAngularImpulse(cauldronPos, stickTipPos, motion);
        if (Math.abs(direction) < 0.0007) {
            return;
        }

        bridge.sendStirCauldron(cauldronPos, (float) clamp(direction * 20.0, -1.0, 1.0));
        stirSyncCooldown = 2;
    }

    private static double calculateAngularImpulse(BlockPos cauldronPos, Vec3 stickTipPos, Vec3 motion) {
        Vec3 center = new Vec3(cauldronPos.getX() + 0.5, stickTipPos.y, cauldronPos.getZ() + 0.5);
        Vec3 radial = new Vec3(
            stickTipPos.x - center.x,
            0.0,
            stickTipPos.z - center.z
        );
        return radial.x * motion.z - radial.z * motion.x;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static void debugSwirlChat(String message) {
        if (!DEBUG_SWIRL_CHAT) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[Alchemy] " + message), false);
        }
    }

    private static void spawnBoilingEffects(Minecraft mc, BlockPos targetPos) {
        if (mc.level == null) {
            return;
        }

        if (mc.level.random.nextInt(3) == 0) {
            mc.level.addParticle(
                ParticleTypes.BUBBLE_POP,
                targetPos.getX() + 0.25 + mc.level.random.nextDouble() * 0.5,
                targetPos.getY() + 0.9 + mc.level.random.nextDouble() * 0.08,
                targetPos.getZ() + 0.25 + mc.level.random.nextDouble() * 0.5,
                0.0,
                0.03 + mc.level.random.nextDouble() * 0.02,
                0.0
            );
        }

        if (mc.level.random.nextInt(5) == 0) {
            mc.level.addParticle(
                ParticleTypes.SPLASH,
                targetPos.getX() + 0.25 + mc.level.random.nextDouble() * 0.5,
                targetPos.getY() + 0.88,
                targetPos.getZ() + 0.25 + mc.level.random.nextDouble() * 0.5,
                (mc.level.random.nextDouble() - 0.5) * 0.02,
                0.05,
                (mc.level.random.nextDouble() - 0.5) * 0.02
            );
        }

        if (ambientBoilSoundCooldown <= 0 && mc.level.random.nextInt(8) == 0) {
            mc.level.playLocalSound(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.6,
                targetPos.getZ() + 0.5,
                SoundEvents.WATER_AMBIENT,
                mc.player.getSoundSource(),
                0.2f,
                0.75f + (mc.level.random.nextFloat() * 0.15f),
                false
            );
            ambientBoilSoundCooldown = 24;
        }
    }
}







