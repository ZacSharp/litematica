package fi.dy.masa.litematica.util;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.state.properties.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public class ItemUtils
{
    private static final IdentityHashMap<BlockState, ItemStack> ITEMS_FOR_STATES = new IdentityHashMap<>();

    public static boolean areTagsEqualIgnoreDamage(ItemStack stackReference, ItemStack stackToCheck)
    {
        CompoundNBT tagReference = stackReference.getTag();
        CompoundNBT tagToCheck = stackToCheck.getTag();

        if (tagReference != null && tagToCheck != null)
        {
            Set<String> keysReference = new HashSet<>(tagReference.keySet());

            for (String key : keysReference)
            {
                if (key.equals("Damage"))
                {
                    continue;
                }

                if (tagReference.get(key).equals(tagToCheck.get(key)) == false)
                {
                    return false;
                }
            }

            return true;
        }

        return (tagReference == null) && (tagToCheck == null);
    }

    public static ItemStack getItemForState(BlockState state)
    {
        ItemStack stack = ITEMS_FOR_STATES.get(state);
        return stack != null ? stack : ItemStack.EMPTY;
    }

    public static void setItemForBlock(World world, BlockPos pos, BlockState state)
    {
        if (ITEMS_FOR_STATES.containsKey(state) == false)
        {
            ITEMS_FOR_STATES.put(state, getItemForBlock(world, pos, state, false));
        }
    }

    public static ItemStack getItemForBlock(World world, BlockPos pos, BlockState state, boolean checkCache)
    {
        if (checkCache)
        {
            ItemStack stack = ITEMS_FOR_STATES.get(state);

            if (stack != null)
            {
                return stack;
            }
        }

        if (state.isAir())
        {
            return ItemStack.EMPTY;
        }

        ItemStack stack = getStateToItemOverride(state);

        if (stack.isEmpty())
        {
            stack = state.getBlock().getItem(world, pos, state);
        }

        if (stack.isEmpty())
        {
            stack = ItemStack.EMPTY;
        }
        else
        {
            overrideStackSize(state, stack);
        }

        ITEMS_FOR_STATES.put(state, stack);

        return stack;
    }

    public static ItemStack getStateToItemOverride(BlockState state)
    {
        if (state.getBlock() == Blocks.LAVA)
        {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        else if (state.getBlock() == Blocks.WATER)
        {
            return new ItemStack(Items.WATER_BUCKET);
        }

        return ItemStack.EMPTY;
    }

    private static void overrideStackSize(BlockState state, ItemStack stack)
    {
        if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.DOUBLE)
        {
            stack.setCount(2);
        }
    }

    public static ItemStack storeTEInStack(ItemStack stack, TileEntity te)
    {
        CompoundNBT nbt = te.write(new CompoundNBT());

        if (nbt.contains("Owner") && stack.getItem() instanceof BlockItem &&
            ((BlockItem) stack.getItem()).getBlock() instanceof AbstractSkullBlock)
        {
            CompoundNBT tagOwner = nbt.getCompound("Owner");
            CompoundNBT tagSkull = new CompoundNBT();

            tagSkull.put("SkullOwner", tagOwner);
            stack.setTag(tagSkull);

            return stack;
        }
        else
        {
            CompoundNBT tagLore = new CompoundNBT();
            ListNBT tagList = new ListNBT();

            tagList.add(StringNBT.valueOf("(+NBT)"));
            tagLore.put("Lore", tagList);
            stack.setTagInfo("display", tagLore);
            stack.setTagInfo("TileEntityTag", nbt);

            return stack;
        }
    }

    public static String getStackString(ItemStack stack)
    {
        if (stack.isEmpty() == false)
        {
            ResourceLocation rl = Registry.ITEM.getKey(stack.getItem());

            return String.format("[%s - display: %s - NBT: %s] (%s)",
                    rl != null ? rl.toString() : "null", stack.getDisplayName().getString(),
                    stack.getTag() != null ? stack.getTag().toString() : "<no NBT>", stack.toString());
        }

        return "<empty>";
    }
}
