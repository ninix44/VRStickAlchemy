package org.vmstudio.stickalchemy.forge;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.stickalchemy.core.client.ExampleAddonClient;
import org.vmstudio.stickalchemy.core.client.StickAlchemyLogic;
import org.vmstudio.stickalchemy.core.common.AlchemyNetworking;
import org.vmstudio.stickalchemy.core.common.VisorExample;
import org.vmstudio.stickalchemy.core.server.AlchemyServerState;
import org.vmstudio.stickalchemy.core.server.ExampleAddonServer;

import java.util.List;
import java.util.function.Supplier;

@Mod(VisorExample.MOD_ID)
public class ExampleMod {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        AlchemyNetworking.FINISH_STIR_PACKET,
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

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

        if (!ModLoader.get().isDedicatedServer()) {
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

                if (!stack.isEmpty() && level.getBlockState(msg.pos).is(Blocks.WATER_CAULDRON)) {
                    ItemStack placedItem = stack.copy(); placedItem.setCount(1); stack.shrink(1);
                    ItemEntity itemEntity = new ItemEntity(level, msg.pos.getX() + 0.5, msg.pos.getY() + 0.75, msg.pos.getZ() + 0.5, placedItem);
                    itemEntity.setPickUpDelay(32767); itemEntity.setNoGravity(true); itemEntity.setDeltaMovement(0, 0, 0);
                    level.addFreshEntity(itemEntity);
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
                if (entity instanceof ItemEntity itemEntity && itemEntity.isNoGravity()) {
                    InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, itemEntity.getItem().copy());
                    else player.getInventory().add(itemEntity.getItem().copy());
                    itemEntity.discard();
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
                if (level.getBlockState(msg.pos).is(Blocks.WATER_CAULDRON)) {
                    AABB cauldronBounds = new AABB(msg.pos).inflate(0.2);
                    List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, cauldronBounds, Entity::isNoGravity);
                    if (!items.isEmpty()) {
                        items.forEach(Entity::discard);
                        AlchemyServerState.BREWED_CAULDRONS.add(msg.pos);
                        level.playSound(null, msg.pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
                        if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.WITCH, msg.pos.getX() + 0.5, msg.pos.getY() + 0.8, msg.pos.getZ() + 0.5, 20, 0.2, 0.2, 0.2, 0.05);
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
                if (AlchemyServerState.BREWED_CAULDRONS.contains(msg.pos) && state.is(Blocks.WATER_CAULDRON)) {
                    InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.is(Items.GLASS_BOTTLE)) {
                        stack.shrink(1);
                        ItemStack potion = new ItemStack(Items.POTION); PotionUtils.setPotion(potion, Potions.HEALING);
                        if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, potion);
                        else player.getInventory().add(potion);
                        int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);

                        if (waterLevel > 1) level.setBlock(msg.pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
                        else { level.setBlock(msg.pos, Blocks.CAULDRON.defaultBlockState(), 3); AlchemyServerState.BREWED_CAULDRONS.remove(msg.pos); }
                        level.playSound(null, msg.pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
