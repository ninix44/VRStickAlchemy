package org.vmstudio.stickalchemy.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
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
import org.vmstudio.stickalchemy.core.server.PotionRecipeLogic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ExampleMod implements ModInitializer {

    public static void updateCauldronGrid(Level level, BlockPos pos) {
        AABB strictInnerCauldron = new AABB(
            pos.getX() + 0.1, pos.getY(), pos.getZ() + 0.1,
            pos.getX() + 0.9, pos.getY() + 1.0, pos.getZ() + 0.9
        );

        List<ItemDisplay> items = level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));
        items.sort(Comparator.comparingInt(Entity::getId));

        int n = Math.min(items.size(), 9);
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            ItemDisplay display = items.get(i);
            double offsetX = 0;
            double offsetZ = 0;
            float scale = 0.25f; // change

            if (n == 1) {
                scale = 0.5f;
            } else if (n == 2) {
                scale = 0.35f;
                offsetX = (i == 0) ? -0.15 : 0.15;
            } else if (n == 3) {
                scale = 0.3f;
                offsetX = (i - 1) * 0.2;
            } else if (n == 4) {
                scale = 0.25f;
                offsetX = (i % 2 == 0) ? -0.15 : 0.15;
                offsetZ = (i < 2) ? -0.15 : 0.15;
            } else {
                scale = 0.2f;
                int row = i / 3;
                int col = i % 3;
                offsetX = (col - 1) * 0.2;
                offsetZ = (row - 1) * 0.2;
            }

            display.setXRot(-90f);
            display.setYRot(0f);
            display.teleportTo(pos.getX() + 0.5 + offsetX, pos.getY() + 0.95, pos.getZ() + 0.5 + offsetZ);

            CompoundTag tag = new CompoundTag();
            display.saveWithoutId(tag);

            tag.putString("item_display", "fixed");

            CompoundTag transform = tag.contains("transformation") ? tag.getCompound("transformation") : new CompoundTag();
            ListTag scaleList = new ListTag();
            scaleList.add(FloatTag.valueOf(scale));
            scaleList.add(FloatTag.valueOf(scale));
            scaleList.add(FloatTag.valueOf(scale));
            transform.put("scale", scaleList);
            tag.put("transformation", transform);

            display.load(tag);
        }
    }

    private void broadcastCauldronColor(ServerLevel level, BlockPos pos, int color) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeInt(color);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, AlchemyNetworking.SYNC_CAULDRON_COLOR_PACKET, buf);
        }
    }

    @Override
    public void onInitialize() {

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) {
                for (AlchemyServerState.CauldronData data : AlchemyServerState.BREWED_CAULDRONS.values()) {
                    BlockState state = data.level.getBlockState(data.pos);

                    if (state.is(Blocks.WATER_CAULDRON)) {
                        int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);

                        if (currentLevel > data.expectedWaterLevel) {
                            AlchemyServerState.BREWED_CAULDRONS.remove(data.pos);
                            broadcastCauldronColor(data.level, data.pos, -1);
                            data.level.playSound(null, data.pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.0f);
                            continue;
                        }

                        int color = PotionUtils.getColor(data.potion);
                        double r = (color >> 16 & 255) / 255.0;
                        double g = (color >> 8 & 255) / 255.0;
                        double b = (color & 255) / 255.0;

                        data.level.sendParticles(ParticleTypes.ENTITY_EFFECT,
                            data.pos.getX() + 0.5 + (data.level.random.nextDouble() - 0.5) * 0.5,
                            data.pos.getY() + 0.8,
                            data.pos.getZ() + 0.5 + (data.level.random.nextDouble() - 0.5) * 0.5,
                            0, r, g, b, 1.0);
                    } else {
                        AlchemyServerState.BREWED_CAULDRONS.remove(data.pos);
                        broadcastCauldronColor(data.level, data.pos, -1);
                    }
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.PLACE_INGREDIENT_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean isMainHand = buf.readBoolean();

            server.execute(() -> {
                Level level = player.level();
                InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                AABB strictInnerCauldron = new AABB(
                    pos.getX() + 0.1, pos.getY(), pos.getZ() + 0.1,
                    pos.getX() + 0.9, pos.getY() + 1.0, pos.getZ() + 0.9
                );
                List<ItemDisplay> currentItems = level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));

                if (currentItems.size() < 9 && !stack.isEmpty() && PotionBrewing.isIngredient(stack) && level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                    ItemStack placedItem = stack.copy();
                    placedItem.setCount(1);
                    stack.shrink(1);

                    ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
                    if (display != null) {
                        display.setPos(pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5);
                        display.setXRot(-90f);
                        display.addTag("alchemy_ingredient");

                        CompoundTag tag = new CompoundTag();
                        display.saveWithoutId(tag);
                        tag.put("item", placedItem.save(new CompoundTag()));
                        tag.putString("item_display", "fixed");
                        display.load(tag);

                        level.addFreshEntity(display);
                        updateCauldronGrid(level, pos);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.EXTRACT_INGREDIENT_PACKET, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            boolean isMainHand = buf.readBoolean();
            server.execute(() -> {
                Entity entity = player.level().getEntity(entityId);
                if (entity instanceof ItemDisplay display && display.getTags().contains("alchemy_ingredient")) {
                    InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

                    ItemStack recoveredItem = ItemStack.EMPTY;
                    CompoundTag tag = new CompoundTag();
                    display.saveWithoutId(tag);
                    if (tag.contains("item")) {
                        recoveredItem = ItemStack.of(tag.getCompound("item"));
                    }

                    if (!recoveredItem.isEmpty()) {
                        if (player.getItemInHand(hand).isEmpty()) {
                            player.setItemInHand(hand, recoveredItem);
                        } else {
                            player.getInventory().add(recoveredItem);
                        }
                    }
                    BlockPos pos = display.blockPosition();
                    display.discard();
                    updateCauldronGrid(player.level(), pos);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AlchemyNetworking.FINISH_STIR_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                Level level = player.level();
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.WATER_CAULDRON)) {
                    AABB strictInnerCauldron = new AABB(
                        pos.getX() + 0.1, pos.getY(), pos.getZ() + 0.1,
                        pos.getX() + 0.9, pos.getY() + 1.0, pos.getZ() + 0.9
                    );
                    List<ItemDisplay> items = level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));

                    if (!items.isEmpty()) {
                        List<ItemStack> ingredients = new ArrayList<>();

                        for(ItemDisplay display : items) {
                            CompoundTag tag = new CompoundTag();
                            display.saveWithoutId(tag);
                            if (tag.contains("item")) {
                                ingredients.add(ItemStack.of(tag.getCompound("item")));
                            }
                            display.discard();
                        }

                        ItemStack resultPotion = PotionRecipeLogic.calculateResult(ingredients);

                        if (resultPotion != null) {
                            if (level instanceof ServerLevel sl) {
                                int initialLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                                AlchemyServerState.BREWED_CAULDRONS.put(pos, new AlchemyServerState.CauldronData(sl, pos, resultPotion, initialLevel));
                                sl.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.2, 0.2, 0.2, 0.05);

                                int color = PotionUtils.getColor(resultPotion);
                                broadcastCauldronColor(sl, pos, color);
                            }
                            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
                        } else {
                            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.0f);
                            if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
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

                if (state.is(Blocks.WATER_CAULDRON)) {
                    InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.is(Items.GLASS_BOTTLE)) {
                        stack.shrink(1);
                        ItemStack potionToGive;
                        int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);

                        if (AlchemyServerState.BREWED_CAULDRONS.containsKey(pos)) {
                            AlchemyServerState.CauldronData data = AlchemyServerState.BREWED_CAULDRONS.get(pos);
                            potionToGive = data.potion.copy();

                            if (waterLevel > 1) {
                                level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
                                data.expectedWaterLevel = waterLevel - 1;
                            } else {
                                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                                AlchemyServerState.BREWED_CAULDRONS.remove(pos);
                                broadcastCauldronColor((ServerLevel) level, pos, -1);
                            }
                        }
                        else {
                            potionToGive = new ItemStack(Items.POTION);
                            PotionUtils.setPotion(potionToGive, Potions.WATER);

                            if (waterLevel > 1) {
                                level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
                            } else {
                                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            }
                        }

                        if (player.getItemInHand(hand).isEmpty()) {
                            player.setItemInHand(hand, potionToGive);
                        } else {
                            player.getInventory().add(potionToGive);
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

            ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> {
                if (pos != null && StickAlchemyLogic.CAULDRON_COLORS.containsKey(pos)) {
                    return StickAlchemyLogic.CAULDRON_COLORS.get(pos);
                }
                return view != null ? BiomeColors.getAverageWaterColor(view, pos) : -1;
            }, Blocks.WATER_CAULDRON);

            ClientPlayNetworking.registerGlobalReceiver(AlchemyNetworking.SYNC_CAULDRON_COLOR_PACKET, (client, handler, buf, responseSender) -> {
                BlockPos pos = buf.readBlockPos();
                int color = buf.readInt();
                client.execute(() -> {
                    if (color == -1) {
                        StickAlchemyLogic.CAULDRON_COLORS.remove(pos);
                    } else {
                        StickAlchemyLogic.CAULDRON_COLORS.put(pos, color);
                    }

                    if (client.level != null) {
                        BlockState state = client.level.getBlockState(pos);
                        client.level.sendBlockUpdated(pos, state, state, 8);
                        client.levelRenderer.blockChanged(client.level, pos, state, state, 8);
                    }
                });
            });

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
