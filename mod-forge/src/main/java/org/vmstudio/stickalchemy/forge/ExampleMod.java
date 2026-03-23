package org.vmstudio.stickalchemy.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.stickalchemy.core.client.ExampleAddonClient;
import org.vmstudio.stickalchemy.core.client.StickAlchemyLogic;
import org.vmstudio.stickalchemy.core.common.AlchemyNetworking;
import org.vmstudio.stickalchemy.core.common.VisorExample;
import org.vmstudio.stickalchemy.core.server.AlchemyServerState;
import org.vmstudio.stickalchemy.core.server.ExampleAddonServer;
import org.vmstudio.stickalchemy.core.server.PotionRecipeLogic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@Mod(VisorExample.MOD_ID)
public class ExampleMod {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        AlchemyNetworking.FINISH_STIR_PACKET,
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int serverTicks = 0;

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
            float scale = 0.25f; /// change

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

    public ExampleMod() {
        CHANNEL.registerMessage(0, FinishStirPacket.class,
            FinishStirPacket::encode,
            FinishStirPacket::decode,
            FinishStirPacket::handle);

        CHANNEL.registerMessage(1, ScoopPotionPacket.class,
            ScoopPotionPacket::encode,
            ScoopPotionPacket::decode,
            ScoopPotionPacket::handle);

        CHANNEL.registerMessage(2, PlaceIngredientPacket.class,
            PlaceIngredientPacket::encode,
            PlaceIngredientPacket::decode,
            PlaceIngredientPacket::handle);

        CHANNEL.registerMessage(3, ExtractIngredientPacket.class,
            ExtractIngredientPacket::encode,
            ExtractIngredientPacket::decode,
            ExtractIngredientPacket::handle);

        CHANNEL.registerMessage(4, SyncCauldronColorPacket.class,
            SyncCauldronColorPacket::encode,
            SyncCauldronColorPacket::decode,
            SyncCauldronColorPacket::handle);

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);

        if (!ModLoader.get().isDedicatedServer()) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterColorHandlersEvent.Block event) -> {
                event.register((state, view, pos, tintIndex) -> {
                    if (pos != null && StickAlchemyLogic.CAULDRON_COLORS.containsKey(pos)) {
                        return StickAlchemyLogic.CAULDRON_COLORS.get(pos);
                    }
                    return view != null ? BiomeColors.getAverageWaterColor(view, pos) : -1;
                }, Blocks.WATER_CAULDRON);
            });

            StickAlchemyLogic.bridge = new StickAlchemyLogic.NetworkBridge() {

                @Override
                public void sendFinishStir(BlockPos pos) {
                    CHANNEL.sendToServer(new FinishStirPacket(pos));
                }

                @Override
                public void sendScoopPotion(BlockPos pos, boolean isMainHand) {
                    CHANNEL.sendToServer(new ScoopPotionPacket(pos, isMainHand));
                }

                @Override

                public void sendPlaceIngredient(BlockPos pos, boolean isMainHand) {
                    CHANNEL.sendToServer(new PlaceIngredientPacket(pos, isMainHand));
                }

                @Override
                public void sendExtractIngredient(int entityId, boolean isMainHand) {
                    CHANNEL.sendToServer(new ExtractIngredientPacket(entityId, isMainHand));
                }
            };

            MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        }

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

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) StickAlchemyLogic.tick();
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            serverTicks++;
            if (serverTicks % 10 == 0) {
                for (AlchemyServerState.CauldronData data : AlchemyServerState.BREWED_CAULDRONS.values()) {
                    BlockState state = data.level.getBlockState(data.pos);

                    if (state.is(Blocks.WATER_CAULDRON)) {
                        int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);

                        if (currentLevel > data.expectedWaterLevel) {
                            AlchemyServerState.BREWED_CAULDRONS.remove(data.pos);
                            CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncCauldronColorPacket(data.pos, -1));
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
                        CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncCauldronColorPacket(data.pos, -1));
                    }
                }
            }
        }
    }

    public static class SyncCauldronColorPacket {
        private final BlockPos pos;
        private final int color;

        public SyncCauldronColorPacket(BlockPos pos, int color) {
            this.pos = pos; this.color = color;
        }
        public static void encode(SyncCauldronColorPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos); buf.writeInt(msg.color);
        }
        public static SyncCauldronColorPacket decode(FriendlyByteBuf buf) {
            return new SyncCauldronColorPacket(buf.readBlockPos(), buf.readInt());
        }
        public static void handle(SyncCauldronColorPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (msg.color == -1) {
                    StickAlchemyLogic.CAULDRON_COLORS.remove(msg.pos);
                } else {
                    StickAlchemyLogic.CAULDRON_COLORS.put(msg.pos, msg.color);
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    BlockState state = mc.level.getBlockState(msg.pos);
                    mc.level.sendBlockUpdated(msg.pos, state, state, 8);
                    mc.levelRenderer.blockChanged(mc.level, msg.pos, state, state, 8);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class PlaceIngredientPacket {
        private final BlockPos pos;
        private final boolean isMainHand;

        public PlaceIngredientPacket(BlockPos pos, boolean isMainHand) {
            this.pos = pos; this.isMainHand = isMainHand;
        }

        public static void encode(PlaceIngredientPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeBoolean(msg.isMainHand);
        }

        public static PlaceIngredientPacket decode(FriendlyByteBuf buf) {
            return new PlaceIngredientPacket(
                buf.readBlockPos(),
                buf.readBoolean());
        }

        public static void handle(PlaceIngredientPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;
                Level level = player.level();
                InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                AABB strictInnerCauldron = new AABB(
                    msg.pos.getX() + 0.1, msg.pos.getY(), msg.pos.getZ() + 0.1,
                    msg.pos.getX() + 0.9, msg.pos.getY() + 1.0, msg.pos.getZ() + 0.9
                );
                List<ItemDisplay> currentItems = level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));

                if (currentItems.size() < 9 && !stack.isEmpty() && PotionBrewing.isIngredient(stack) && level.getBlockState(msg.pos).is(Blocks.WATER_CAULDRON)) {
                    ItemStack placedItem = stack.copy(); placedItem.setCount(1); stack.shrink(1);
                    ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
                    if (display != null) {
                        display.setPos(msg.pos.getX() + 0.5, msg.pos.getY() + 0.95, msg.pos.getZ() + 0.5);
                        display.setXRot(-90f);
                        display.addTag("alchemy_ingredient");

                        CompoundTag tag = new CompoundTag();
                        display.saveWithoutId(tag);
                        tag.put("item", placedItem.save(new CompoundTag()));
                        tag.putString("item_display", "fixed");
                        display.load(tag);

                        level.addFreshEntity(display);
                        updateCauldronGrid(level, msg.pos);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ExtractIngredientPacket {
        private final int entityId;
        private final boolean isMainHand;

        public ExtractIngredientPacket(int entityId, boolean isMainHand) {
            this.entityId = entityId;
            this.isMainHand = isMainHand;
        }

        public static void encode(ExtractIngredientPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeBoolean(msg.isMainHand);
        }

        public static ExtractIngredientPacket decode(FriendlyByteBuf buf) {
            return new ExtractIngredientPacket(
                buf.readInt(),
                buf.readBoolean());
        }

        public static void handle(ExtractIngredientPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;
                Entity entity = player.level().getEntity(msg.entityId);
                if (entity instanceof ItemDisplay display && display.getTags().contains("alchemy_ingredient")) {
                    InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack recoveredItem = ItemStack.EMPTY;
                    CompoundTag tag = new CompoundTag();
                    display.saveWithoutId(tag);
                    if (tag.contains("item")) { recoveredItem = ItemStack.of(tag.getCompound("item")); }
                    if (!recoveredItem.isEmpty()) {
                        if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, recoveredItem);
                        else player.getInventory().add(recoveredItem);
                    }
                    BlockPos pos = display.blockPosition();
                    display.discard();
                    updateCauldronGrid(player.level(), pos);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class FinishStirPacket {
        private final BlockPos pos;

        public FinishStirPacket(BlockPos pos) {
            this.pos = pos;
        }

        public static void encode(FinishStirPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
        }

        public static FinishStirPacket decode(FriendlyByteBuf buf) {
            return new FinishStirPacket(buf.readBlockPos());
        }

        public static void handle(FinishStirPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;
                Level level = player.level();
                BlockState state = level.getBlockState(msg.pos);
                if (state.is(Blocks.WATER_CAULDRON)) {
                    AABB strictInnerCauldron = new AABB(msg.pos.getX() + 0.1, msg.pos.getY(), msg.pos.getZ() + 0.1, msg.pos.getX() + 0.9, msg.pos.getY() + 1.0, msg.pos.getZ() + 0.9);
                    List<ItemDisplay> items = level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));
                    if (!items.isEmpty()) {
                        List<ItemStack> ingredients = new ArrayList<>();
                        for(ItemDisplay display : items) {
                            CompoundTag tag = new CompoundTag(); display.saveWithoutId(tag);
                            if (tag.contains("item")) ingredients.add(ItemStack.of(tag.getCompound("item")));
                            display.discard();
                        }

                        ItemStack resultPotion = PotionRecipeLogic.calculateResult(ingredients);

                        if (resultPotion != null) {
                            if (level instanceof ServerLevel sl) {
                                int initialLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                                AlchemyServerState.BREWED_CAULDRONS.put(msg.pos, new AlchemyServerState.CauldronData(sl, msg.pos, resultPotion, initialLevel));
                                sl.sendParticles(ParticleTypes.WITCH, msg.pos.getX() + 0.5, msg.pos.getY() + 0.8, msg.pos.getZ() + 0.5, 20, 0.2, 0.2, 0.2, 0.05);

                                int color = PotionUtils.getColor(resultPotion);
                                CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncCauldronColorPacket(msg.pos, color));
                            }
                            level.playSound(null, msg.pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
                        } else {
                            level.setBlock(msg.pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            level.playSound(null, msg.pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.0f);
                            if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.LARGE_SMOKE, msg.pos.getX() + 0.5, msg.pos.getY() + 0.8, msg.pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ScoopPotionPacket {
        private final BlockPos pos;
        private final boolean isMainHand;

        public ScoopPotionPacket(BlockPos pos, boolean isMainHand) {
            this.pos = pos;
            this.isMainHand = isMainHand;
        }

        public static void encode(ScoopPotionPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeBoolean(msg.isMainHand);
        }

        public static ScoopPotionPacket decode(FriendlyByteBuf buf) {
            return new ScoopPotionPacket(
                buf.readBlockPos(),
                buf.readBoolean());
        }

        public static void handle(ScoopPotionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;
                Level level = player.level();
                BlockState state = level.getBlockState(msg.pos);
                if (AlchemyServerState.BREWED_CAULDRONS.containsKey(msg.pos) && state.is(Blocks.WATER_CAULDRON)) {
                    AlchemyServerState.CauldronData data = AlchemyServerState.BREWED_CAULDRONS.get(msg.pos);
                    InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.is(Items.GLASS_BOTTLE)) {
                        stack.shrink(1);
                        ItemStack potionToGive = data.potion.copy();
                        if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, potionToGive);
                        else player.getInventory().add(potionToGive);

                        int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                        if (waterLevel > 1) {
                            level.setBlock(msg.pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
                            data.expectedWaterLevel = waterLevel - 1;
                        } else {
                            level.setBlock(msg.pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            AlchemyServerState.BREWED_CAULDRONS.remove(msg.pos);
                            CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncCauldronColorPacket(msg.pos, -1));
                        }
                        level.playSound(null, msg.pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
