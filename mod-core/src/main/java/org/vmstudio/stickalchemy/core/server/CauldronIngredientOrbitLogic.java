package org.vmstudio.stickalchemy.core.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class CauldronIngredientOrbitLogic {

    private static final double MAX_ANGULAR_VELOCITY = 0.28;
    private static final double MIN_ORBIT_ACTIVITY = 0.015;
    private static final double MIN_ANGULAR_VELOCITY = 0.003;
    private static final float DEFAULT_HEIGHT = 0.95f;

    private CauldronIngredientOrbitLogic() {
    }

    public static void updateCauldronLayout(Level level, BlockPos pos) {
        List<ItemDisplay> items = getIngredientDisplays(level, pos);
        if (items.isEmpty()) {
            clearOrbit(pos);
            return;
        }

        AlchemyServerState.CauldronOrbitData orbit = AlchemyServerState.CAULDRON_ORBITS.get(pos);
        applyLayout(level, pos, items, orbit);
    }

    public static void applyStirImpulse(ServerLevel level, BlockPos pos, float direction) {
        if (Math.abs(direction) < 0.01f) {
            return;
        }

        List<ItemDisplay> items = getIngredientDisplays(level, pos);
        if (items.isEmpty()) {
            clearOrbit(pos);
            return;
        }

        AlchemyServerState.CauldronOrbitData orbit = AlchemyServerState.CAULDRON_ORBITS.computeIfAbsent(
            pos,
            ignored -> new AlchemyServerState.CauldronOrbitData(level, pos)
        );

        orbit.angularVelocity = Mth.clamp(orbit.angularVelocity + direction * 0.18, -MAX_ANGULAR_VELOCITY, MAX_ANGULAR_VELOCITY);
        orbit.orbitStrength = Mth.clamp(Math.max(orbit.orbitStrength, Math.abs(direction) * 1.8f + 0.22f), 0.0, 1.0);
        applyLayout(level, pos, items, orbit);
    }

    public static void tickOrbits() {
        Iterator<AlchemyServerState.CauldronOrbitData> iterator = AlchemyServerState.CAULDRON_ORBITS.values().iterator();

        while (iterator.hasNext()) {
            AlchemyServerState.CauldronOrbitData orbit = iterator.next();
            ServerLevel level = orbit.level;

            if (!level.isLoaded(orbit.pos) || !level.getBlockState(orbit.pos).is(Blocks.WATER_CAULDRON)) {
                iterator.remove();
                continue;
            }

            List<ItemDisplay> items = getIngredientDisplays(level, orbit.pos);
            if (items.isEmpty()) {
                iterator.remove();
                continue;
            }

            orbit.orbitAngle += orbit.angularVelocity;
            orbit.angularVelocity *= 0.86;
            orbit.orbitStrength = Math.max(Math.abs(orbit.angularVelocity) * 1.8, orbit.orbitStrength * 0.90);

            if (orbit.orbitStrength < MIN_ORBIT_ACTIVITY && Math.abs(orbit.angularVelocity) < MIN_ANGULAR_VELOCITY) {
                orbit.orbitStrength = 0.0;
                orbit.angularVelocity = 0.0;
                applyLayout(level, orbit.pos, items, orbit);
                iterator.remove();
                continue;
            }

            applyLayout(level, orbit.pos, items, orbit);
        }
    }

    public static void clearOrbit(BlockPos pos) {
        AlchemyServerState.CAULDRON_ORBITS.remove(pos);
    }

    private static List<ItemDisplay> getIngredientDisplays(Level level, BlockPos pos) {
        AABB strictInnerCauldron = new AABB(
            pos.getX() + 0.1, pos.getY(), pos.getZ() + 0.1,
            pos.getX() + 0.9, pos.getY() + 1.0, pos.getZ() + 0.9
        );

        List<ItemDisplay> items = level.getEntitiesOfClass(ItemDisplay.class, strictInnerCauldron, e -> e.getTags().contains("alchemy_ingredient"));
        items.sort(Comparator.comparingInt(Entity::getId));
        return items;
    }

    private static void applyLayout(Level level, BlockPos pos, List<ItemDisplay> items, AlchemyServerState.CauldronOrbitData orbit) {
        int n = Math.min(items.size(), 9);
        if (n == 0) {
            return;
        }

        double angle = orbit != null ? orbit.orbitAngle : 0.0;
        float orbitMix = orbit != null ? Mth.clamp((float) orbit.orbitStrength, 0.0f, 1.0f) : 0.0f;
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        for (int i = 0; i < n; i++) {
            ItemDisplay display = items.get(i);
            LayoutEntry base = getBaseLayout(n, i, orbitMix);

            double rotatedX = base.offsetX * cos - base.offsetZ * sin;
            double rotatedZ = base.offsetX * sin + base.offsetZ * cos;

            display.setXRot(-90f);
            display.setYRot((float) Math.toDegrees(-angle));
            display.teleportTo(
                pos.getX() + 0.5 + rotatedX,
                pos.getY() + DEFAULT_HEIGHT,
                pos.getZ() + 0.5 + rotatedZ
            );
            applyScale(display, base.scale);
        }
    }

    private static LayoutEntry getBaseLayout(int n, int index, float orbitMix) {
        double offsetX = 0.0;
        double offsetZ = 0.0;
        float scale = 0.25f;

        if (n == 1) {
            scale = 0.5f;
            offsetX = 0.14f * orbitMix;
        } else if (n == 2) {
            scale = 0.35f;
            offsetX = index == 0 ? -0.15 : 0.15;
        } else if (n == 3) {
            scale = 0.3f;
            offsetX = (index - 1) * 0.2;
        } else if (n == 4) {
            scale = 0.25f;
            offsetX = index % 2 == 0 ? -0.15 : 0.15;
            offsetZ = index < 2 ? -0.15 : 0.15;
        } else {
            scale = 0.2f;
            int row = index / 3;
            int col = index % 3;
            offsetX = (col - 1) * 0.2;
            offsetZ = (row - 1) * 0.2;
        }

        return new LayoutEntry(offsetX, offsetZ, scale);
    }

    private static void applyScale(ItemDisplay display, float scale) {
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

    private record LayoutEntry(double offsetX, double offsetZ, float scale) {
    }
}
