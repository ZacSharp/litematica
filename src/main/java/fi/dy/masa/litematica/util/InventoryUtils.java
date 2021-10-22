package fi.dy.masa.litematica.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.malilib.gui.GuiBase;

public class InventoryUtils
{
    private static final List<Integer> PICK_BLOCKABLE_SLOTS = new ArrayList<>();
    private static int nextPickSlotIndex;

    public static void setPickBlockableSlots(String configStr)
    {
        PICK_BLOCKABLE_SLOTS.clear();
        String[] parts = configStr.split(",");

        for (String str : parts)
        {
            try
            {
                int slotNum = Integer.parseInt(str);

                if (PlayerInventory.isValidHotbarIndex(slotNum) && PICK_BLOCKABLE_SLOTS.contains(slotNum) == false)
                {
                    PICK_BLOCKABLE_SLOTS.add(slotNum);
                }
            }
            catch (NumberFormatException e)
            {
            }
        }
    }

    public static void setPickedItemToHand(ItemStack stack, Minecraft mc)
    {
        int slotNum = mc.player.inventory.getSlotWithStack(stack);
        setPickedItemToHand(slotNum, stack, mc);
    }

    public static void setPickedItemToHand(int sourceSlot, ItemStack stack, Minecraft mc)
    {
        PlayerEntity player = mc.player;

        if (PlayerInventory.isValidHotbarIndex(sourceSlot))
        {
            player.inventory.selectedSlot = sourceSlot;
        }
        else
        {
            if (PICK_BLOCKABLE_SLOTS.size() == 0)
            {
                return;
            }

            int hotbarSlot = sourceSlot;

            if (sourceSlot == -1 || PlayerInventory.isValidHotbarIndex(sourceSlot) == false)
            {
                hotbarSlot = getEmptyPickBlockableHotbarSlot(player.inventory);
            }

            if (hotbarSlot == -1)
            {
                hotbarSlot = getPickBlockTargetSlot(player);
            }

            if (hotbarSlot != -1)
            {
                PlayerInventory inventory = player.inventory;
                inventory.selectedSlot = hotbarSlot;

                if (player.abilities.creativeMode)
                {
                    inventory.main.set(hotbarSlot, stack.copy());
                }
                else
                {
                    fi.dy.masa.malilib.util.InventoryUtils.swapItemToMainHand(stack.copy(), mc);
                }
            }
        }
    }

    public static void schematicWorldPickBlock(ItemStack stack, BlockPos pos,
                                               World schematicWorld, Minecraft mc)
    {
        if (stack.isEmpty() == false)
        {
            PlayerInventory inv = mc.player.inventory;
            stack = stack.copy();

            if (mc.player.abilities.creativeMode)
            {
                TileEntity te = schematicWorld.getBlockEntity(pos);

                // The creative mode pick block with NBT only works correctly
                // if the server world doesn't have a TileEntity in that position.
                // Otherwise it would try to write whatever that TE is into the picked ItemStack.
                if (GuiBase.isCtrlDown() && te != null && mc.world.isAir(pos))
                {
                    ItemUtils.storeTEInStack(stack, te);
                }

                setPickedItemToHand(stack, mc);
                mc.interactionManager.clickCreativeStack(mc.player.getStackInHand(Hand.MAIN_HAND), 36 + inv.selectedSlot);

                //return true;
            }
            else
            {
                int slot = inv.getSlotWithStack(stack);
                boolean shouldPick = inv.selectedSlot != slot;

                if (shouldPick && slot != -1)
                {
                    setPickedItemToHand(stack, mc);
                }
                else if (slot == -1 && Configs.Generic.PICK_BLOCK_SHULKERS.getBooleanValue())
                {
                    slot = findSlotWithBoxWithItem(mc.player.playerScreenHandler, stack, false);

                    if (slot != -1)
                    {
                        ItemStack boxStack = mc.player.playerScreenHandler.slots.get(slot).getStack();
                        setPickedItemToHand(boxStack, mc);
                    }
                }

                //return shouldPick == false || canPick;
            }
        }
    }

    private static int getPickBlockTargetSlot(PlayerEntity player)
    {
        int slotNum;

        if (PICK_BLOCKABLE_SLOTS.contains(player.inventory.selectedSlot + 1))
        {
            slotNum = player.inventory.selectedSlot;
        }
        else
        {
            if (nextPickSlotIndex >= PICK_BLOCKABLE_SLOTS.size())
            {
                nextPickSlotIndex = 0;
            }

            slotNum = PICK_BLOCKABLE_SLOTS.get(nextPickSlotIndex) - 1;

            if (++nextPickSlotIndex >= PICK_BLOCKABLE_SLOTS.size())
            {
                nextPickSlotIndex = 0;
            }
        }

        return slotNum;
    }

    private static int getEmptyPickBlockableHotbarSlot(PlayerInventory inventory)
    {
        for (int i = 0; i < PICK_BLOCKABLE_SLOTS.size(); ++i)
        {
            int slotNum = PICK_BLOCKABLE_SLOTS.get(i) - 1;

            if (slotNum >= 0 && slotNum < inventory.main.size())
            {
                ItemStack stack = inventory.main.get(slotNum);

                if (stack.isEmpty())
                {
                    return slotNum;
                }
            }
        }

        return -1;
    }

    public static boolean doesShulkerBoxContainItem(ItemStack stack, ItemStack referenceItem)
    {
        NonNullList<ItemStack> items = fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(stack);

        if (items.size() > 0)
        {
            for (ItemStack item : items)
            {
                if (fi.dy.masa.malilib.util.InventoryUtils.areStacksEqual(item, referenceItem))
                {
                    return true;
                }
            }
        }

        return false;
    }

    public static int findSlotWithBoxWithItem(Container container, ItemStack stackReference, boolean reverse)
    {
        final int startSlot = reverse ? container.slots.size() - 1 : 0;
        final int endSlot = reverse ? -1 : container.slots.size();
        final int increment = reverse ? -1 : 1;
        final boolean isPlayerInv = container instanceof PlayerContainer;

        for (int slotNum = startSlot; slotNum != endSlot; slotNum += increment)
        {
            Slot slot = container.slots.get(slotNum);

            if ((isPlayerInv == false || fi.dy.masa.malilib.util.InventoryUtils.isRegularInventorySlot(slot.id, false)) &&
                doesShulkerBoxContainItem(slot.getStack(), stackReference))
            {
                return slot.id;
            }
        }

        return -1;
    }
}
