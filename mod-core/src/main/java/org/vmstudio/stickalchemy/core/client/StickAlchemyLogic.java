package org.vmstudio.stickalchemy.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseClient;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StickAlchemyLogic {

    public interface NetworkBridge {
        void sendFinishStir(BlockPos pos);
        void sendScoopPotion(BlockPos pos, boolean isMainHand);
        void sendPlaceIngredient(BlockPos pos, boolean isMainHand);
        void sendExtractIngredient(int entityId, boolean isMainHand);
    }

    public static NetworkBridge bridge;

    private static final Map<BlockPos, Integer> stirProgress = new HashMap<>();
    private static Vec3 lastMainPos = null;
    private static Vec3 lastOffPos = null;

    private static int scoopCooldown = 0;
    private static int extractCooldown = 0;

    private static int mainHandHoldTicks = 0;
    private static int offHandHoldTicks = 0;
    private static final int TARGET_HOLD_TIME = 30;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.screen != null) return;

        VRLocalPlayer vrPlayer = VisorAPI.client().getVRLocalPlayer();
        if (vrPlayer == null || !VisorAPI.clientState().playMode().canPlayVR()) return;

        PlayerPoseClient poseTick = vrPlayer.getPoseData(PlayerPoseType.TICK);

        if (scoopCooldown > 0) scoopCooldown--;
        if (extractCooldown > 0) extractCooldown--;

        lastMainPos = processHand(mc, poseTick.getMainHand().getPosition(), InteractionHand.MAIN_HAND, HandType.MAIN, true, lastMainPos);
        lastOffPos = processHand(mc, poseTick.getOffhand().getPosition(), InteractionHand.OFF_HAND, HandType.OFFHAND, false, lastOffPos);
    }

    private static void resetTimers(boolean isMain) {
        if (isMain) mainHandHoldTicks = 0;
        else offHandHoldTicks = 0;
    }

    private static Vec3 processHand(Minecraft mc, Vector3fc handJoml, InteractionHand mcHand, HandType vrHand, boolean isMain, Vec3 lastPos) {
        Vec3 handPos = new Vec3(
            handJoml.x(),
            handJoml.y(),
            handJoml.z());

        if (lastPos == null) {
            return handPos;
        }

        boolean isHoldingStick = mc.player.getItemInHand(mcHand).is(Items.STICK);
        boolean isHoldingBottle = mc.player.getItemInHand(mcHand).is(Items.GLASS_BOTTLE);
        boolean isEmpty = mc.player.getItemInHand(mcHand).isEmpty();
        boolean isHoldingIngredient = !isHoldingStick && !isHoldingBottle && !isEmpty;

        double speed = handPos.distanceTo(lastPos);

        if (isEmpty && (mc.options.keyUse.isDown() || mc.options.keyJump.isDown()) && extractCooldown <= 0) {
            AABB grabBox = new AABB(handPos, handPos).inflate(0.15);
            for (ItemDisplay item : mc.level.getEntitiesOfClass(ItemDisplay.class, grabBox)) {
                if (item.getTags().contains("alchemy_ingredient")) {
                    if (bridge != null) bridge.sendExtractIngredient(item.getId(), isMain);
                    VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 200f, 0.5f, 0.1f);
                    mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.5f, 1.5f);
                    extractCooldown = 15;
                    return handPos;
                }
            }
        }

        BlockPos centerPos = BlockPos.containing(handPos);
        AABB handBox = new AABB(handPos, handPos).inflate(0.05);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos targetPos = centerPos.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(targetPos);

                    if (state.is(Blocks.WATER_CAULDRON)) {
                        AABB cauldronWaterSurface = new AABB(
                            targetPos.getX() + 0.15, targetPos.getY() + 0.3, targetPos.getZ() + 0.15,
                            targetPos.getX() + 0.85, targetPos.getY() + 1.2, targetPos.getZ() + 0.85
                        );

                        if (isHoldingIngredient && handBox.intersects(cauldronWaterSurface)) {
                            AABB strictInnerCauldron = new AABB(
                                targetPos.getX() + 0.1, targetPos.getY(), targetPos.getZ() + 0.1,
                                targetPos.getX() + 0.9, targetPos.getY() + 1.0, targetPos.getZ() + 0.9
                            );
                            List<ItemDisplay> currentItems = mc.level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));

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
                                        if (bridge != null) bridge.sendPlaceIngredient(targetPos, isMain);
                                        VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 200f, 0.5f, 0.1f);
                                        mc.player.playSound(SoundEvents.SPLASH_POTION_THROW, 0.4f, 1.2f);
                                        resetTimers(isMain);
                                    }
                                } else {
                                    resetTimers(isMain);
                                }
                            }
                            return handPos;
                        }

                        AABB cauldronStirZone = new AABB(
                            targetPos.getX() - 0.2, targetPos.getY() + 0.3, targetPos.getZ() - 0.2,
                            targetPos.getX() + 1.2, targetPos.getY() + 1.5, targetPos.getZ() + 1.2
                        );

                        if (handBox.intersects(cauldronStirZone)) {
                            if (isHoldingStick) {
                                if (speed > 0.005) {
                                    int progress = stirProgress.getOrDefault(targetPos, 0) + 1;
                                    stirProgress.put(targetPos, progress);

                                    if (progress % 5 == 0) {
                                        VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 150f, 0.3f, 0.05f);
                                        mc.player.playSound(SoundEvents.WATER_AMBIENT, 0.5f, 1.0f + (mc.level.random.nextFloat() * 0.5f));
                                        mc.level.addParticle(ParticleTypes.SPLASH,
                                            targetPos.getX() + 0.5, targetPos.getY() + 0.9, targetPos.getZ() + 0.5,
                                            (mc.level.random.nextDouble() - 0.5) * 0.2, 0.1, (mc.level.random.nextDouble() - 0.5) * 0.2);
                                    }
                                    if (progress >= 35) {
                                        VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 400f, 1.0f, 0.4f);
                                        mc.player.playSound(SoundEvents.SPLASH_POTION_BREAK, 1.0f, 1.0f);
                                        stirProgress.remove(targetPos);
                                        if (bridge != null) bridge.sendFinishStir(targetPos);
                                    }
                                }
                                return handPos;
                            }

                            if (isHoldingBottle && scoopCooldown <= 0) {
                                if (bridge != null) {
                                    bridge.sendScoopPotion(targetPos, isMain);
                                    VisorAPI.client().getInputManager().triggerHapticPulse(vrHand, 200f, 0.5f, 0.1f);
                                    scoopCooldown = 20;
                                }
                                return handPos;
                            }
                        }
                    }
                }
            }
        }

        resetTimers(isMain);
        return handPos;
    }
}
