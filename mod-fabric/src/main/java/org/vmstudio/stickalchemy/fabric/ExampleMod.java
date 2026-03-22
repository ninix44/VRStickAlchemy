package org.vmstudio.stickalchemy.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.stickalchemy.core.client.ExampleAddonClient;
import org.vmstudio.stickalchemy.core.client.StickAlchemyLogic;
import org.vmstudio.stickalchemy.core.common.AlchemyNetworking;
import org.vmstudio.stickalchemy.core.server.AlchemyServerState;
import org.vmstudio.stickalchemy.core.server.ExampleAddonServer;

import java.util.List;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.PLACE_INGREDIENT_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean isMainHand = buf.readBoolean();

            server.execute(() -> {
                Level level = player.level();
                InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if (!stack.isEmpty() && level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                    ItemStack placedItem = stack.copy();
                    placedItem.setCount(1);
                    stack.shrink(1);

                    ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5, placedItem);
                    itemEntity.setPickUpDelay(32767);
                    itemEntity.setNoGravity(true);
                    itemEntity.setDeltaMovement(0, 0, 0);

                    level.addFreshEntity(itemEntity);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.EXTRACT_INGREDIENT_PACKET, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            boolean isMainHand = buf.readBoolean();
            server.execute(() -> {
                Entity entity = player.level().getEntity(entityId);
                if (entity instanceof ItemEntity itemEntity && itemEntity.isNoGravity()) {
                    InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

                    if (player.getItemInHand(hand).isEmpty()) {
                        player.setItemInHand(hand, itemEntity.getItem().copy());
                    } else {
                        player.getInventory().add(itemEntity.getItem().copy());
                    }
                    itemEntity.discard();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.FINISH_STIR_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                Level level = player.level();
                if (level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                    AABB cauldronBounds = new AABB(pos).inflate(0.2);
                    List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, cauldronBounds, Entity::isNoGravity);
                    if (!items.isEmpty()) {
                        items.forEach(Entity::discard);
                        AlchemyServerState.BREWED_CAULDRONS.add(pos);
                        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
                        if (level instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.WITCH,
                                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.SCOOP_POTION_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean isMainHand = buf.readBoolean();
            server.execute(() -> {
                Level level = player.level();
                BlockState state = level.getBlockState(pos);

                if (AlchemyServerState.BREWED_CAULDRONS.contains(pos) && state.is(Blocks.WATER_CAULDRON)) {
                    InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.is(Items.GLASS_BOTTLE)) {
                        stack.shrink(1);
                        ItemStack potion = new ItemStack(Items.POTION);
                        PotionUtils.setPotion(potion, Potions.HEALING);
                        if (player.getItemInHand(hand).isEmpty()) {
                            player.setItemInHand(hand, potion);
                        } else {
                            player.getInventory().add(potion);
                        }

                        int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                        if (waterLevel > 1) {
                            level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
                        } else {
                            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            AlchemyServerState.BREWED_CAULDRONS.remove(pos);
                        }
                        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                }
            });
        });

        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new ExampleAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );

            StickAlchemyLogic.bridge = new StickAlchemyLogic.NetworkBridge() {

                @Override
                public void sendFinishStir(BlockPos pos) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBlockPos(pos);
                    ClientPlayNetworking.send(AlchemyNetworking.FINISH_STIR_PACKET, buf);
                }

                @Override
                public void sendScoopPotion(BlockPos pos, boolean isMainHand) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBlockPos(pos);
                    buf.writeBoolean(isMainHand);
                    ClientPlayNetworking.send(AlchemyNetworking.SCOOP_POTION_PACKET, buf);
                }

                @Override
                public void sendPlaceIngredient(BlockPos pos, boolean isMainHand) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBlockPos(pos);
                    buf.writeBoolean(isMainHand);
                    ClientPlayNetworking.send(AlchemyNetworking.PLACE_INGREDIENT_PACKET, buf);
                }

                @Override
                public void sendExtractIngredient(int entityId, boolean isMainHand) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(entityId);
                    buf.writeBoolean(isMainHand);
                    ClientPlayNetworking.send(AlchemyNetworking.EXTRACT_INGREDIENT_PACKET, buf);
                }
            };

            ClientTickEvents.END_CLIENT_TICK.register(client -> StickAlchemyLogic.tick());
        }
    }
}
