package org.vmstudio.stickalchemy.core.server;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

public class PotionRecipeLogic {

    public static ItemStack calculateResult(List<ItemStack> ingredients) {
        boolean wart = false, redstone = false, glowstone = false;
        boolean gunpowder = false, lingering = false, eye = false;
        int baseCount = 0;
        Potion basePotion = Potions.WATER;

        for (ItemStack stack : ingredients) {
            Item item = stack.getItem();
            if (item == Items.NETHER_WART) {
                wart = true;
            }

            else if (item == Items.REDSTONE) {
                redstone = true;
            }

            else if (item == Items.GLOWSTONE_DUST) {
                glowstone = true;
            }

            else if (item == Items.GUNPOWDER) {
                gunpowder = true;
            }

            else if (item == Items.DRAGON_BREATH) {
                lingering = true;
            }

            else if (item == Items.FERMENTED_SPIDER_EYE) {
                eye = true;
            }
            else {
                Potion p = getBasePotion(item);
                if (p != null) {
                    basePotion = p;
                    baseCount++;
                } else {
                    return null;
                }
            }
        }

        if (baseCount > 1) {
            return null;
        }

        if (!wart && basePotion == Potions.WATER && !eye) {
            return null;
        }

        if (wart && basePotion == Potions.WATER) {
            basePotion = Potions.AWKWARD;
        }

        if (eye) {
            basePotion = applyInversion(basePotion);
        }

        if (redstone && !glowstone) {
            basePotion = applyRedstone(basePotion);
        }

        else if (glowstone && !redstone) {
            basePotion = applyGlowstone(basePotion);
        }

        if (basePotion == null) {
            return null;
        }

        Item bottle = Items.POTION;
        if (lingering) {
            bottle = Items.LINGERING_POTION;
        }

        else if (gunpowder) {
            bottle = Items.SPLASH_POTION;
        }

        ItemStack result = new ItemStack(bottle);
        PotionUtils.setPotion(result, basePotion);
        return result;
    }

    private static Potion getBasePotion(Item item) {
        if (item == Items.SUGAR) {
            return Potions.SWIFTNESS;
        }

        if (item == Items.RABBIT_FOOT) {
            return Potions.LEAPING;
        }

        if (item == Items.BLAZE_POWDER) {
            return Potions.STRENGTH;
        }

        if (item == Items.GLISTERING_MELON_SLICE) {
            return Potions.HEALING;
        }

        if (item == Items.SPIDER_EYE) {
            return Potions.POISON;
        }

        if (item == Items.GHAST_TEAR) {
            return Potions.REGENERATION;
        }

        if (item == Items.MAGMA_CREAM) {
            return Potions.FIRE_RESISTANCE;
        }

        if (item == Items.PUFFERFISH) {
            return Potions.WATER_BREATHING;
        }

        if (item == Items.GOLDEN_CARROT) {
            return Potions.NIGHT_VISION;
        }

        if (item == Items.TURTLE_HELMET) {
            return Potions.TURTLE_MASTER;
        }

        if (item == Items.PHANTOM_MEMBRANE) {
            return Potions.SLOW_FALLING;
        }

        return null;
    }

    private static Potion applyInversion(Potion p) {
        if (p == Potions.SWIFTNESS || p == Potions.LEAPING) {
            return Potions.SLOWNESS;
        }

        if (p == Potions.HEALING || p == Potions.POISON) {
            return Potions.HARMING;
        }

        if (p == Potions.NIGHT_VISION) {
            return Potions.INVISIBILITY;
        }

        if (p == Potions.WATER) {
            return Potions.WEAKNESS;
        }

        return null;
    }

    private static Potion applyRedstone(Potion p) {
        if (p == Potions.NIGHT_VISION) {
            return Potions.LONG_NIGHT_VISION;
        }

        if (p == Potions.INVISIBILITY) {
            return Potions.LONG_INVISIBILITY;
        }

        if (p == Potions.LEAPING) {
            return Potions.LONG_LEAPING;
        }

        if (p == Potions.FIRE_RESISTANCE) {
            return Potions.LONG_FIRE_RESISTANCE;
        }

        if (p == Potions.SWIFTNESS) {
            return Potions.LONG_SWIFTNESS;
        }

        if (p == Potions.SLOWNESS) {
            return Potions.LONG_SLOWNESS;
        }

        if (p == Potions.WATER_BREATHING) {
            return Potions.LONG_WATER_BREATHING;
        }

        if (p == Potions.POISON) {
            return Potions.LONG_POISON;
        }

        if (p == Potions.REGENERATION) {
            return Potions.LONG_REGENERATION;
        }

        if (p == Potions.STRENGTH) {
            return Potions.LONG_STRENGTH;
        }

        if (p == Potions.WEAKNESS) {
            return Potions.LONG_WEAKNESS;
        }

        if (p == Potions.TURTLE_MASTER) {
            return Potions.LONG_TURTLE_MASTER;
        }

        if (p == Potions.SLOW_FALLING) {
            return Potions.LONG_SLOW_FALLING;
        }

        return p;
    }

    private static Potion applyGlowstone(Potion p) {
        if (p == Potions.LEAPING) {
            return Potions.STRONG_LEAPING;
        }

        if (p == Potions.SWIFTNESS) {
            return Potions.STRONG_SWIFTNESS;
        }

        if (p == Potions.SLOWNESS) {
            return Potions.STRONG_SLOWNESS;
        }

        if (p == Potions.HEALING) {
            return Potions.STRONG_HEALING;
        }

        if (p == Potions.HARMING) {
            return Potions.STRONG_HARMING;
        }

        if (p == Potions.POISON) {
            return Potions.STRONG_POISON;
        }

        if (p == Potions.REGENERATION) {
            return Potions.STRONG_REGENERATION;
        }

        if (p == Potions.STRENGTH) {
            return Potions.STRONG_STRENGTH;
        }

        if (p == Potions.TURTLE_MASTER) {
            return Potions.STRONG_TURTLE_MASTER;
        }

        return p;
    }
}
