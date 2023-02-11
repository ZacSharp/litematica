package fi.dy.masa.litematica.util;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.util.Constants;
import fi.dy.masa.malilib.util.InventoryUtils;

public class EntityUtils
{
    public static final Predicate<Entity> NOT_PLAYER = entity -> (entity instanceof Player) == false;

    public static boolean isCreativeMode(Player player)
    {
        return player.getAbilities().instabuild;
    }

    public static boolean hasToolItem(LivingEntity entity)
    {
        return hasToolItemInHand(entity, InteractionHand.MAIN_HAND) ||
               hasToolItemInHand(entity, InteractionHand.OFF_HAND);
    }

    public static boolean hasToolItemInHand(LivingEntity entity, InteractionHand hand)
    {
        // If the configured tool item has NBT data, then the NBT is compared, otherwise it's ignored

        ItemStack toolItem = DataManager.getToolItem();

        if (toolItem.isEmpty())
        {
            return entity.getMainHandItem().isEmpty();
        }

        ItemStack stackHand = entity.getItemInHand(hand);

        if (ItemStack.isSame(toolItem, stackHand))
        {
            return toolItem.hasTag() == false || ItemUtils.areTagsEqualIgnoreDamage(toolItem, stackHand);
        }

        return false;
    }

    /**
     * Checks if the requested item is currently in the player's hand such that it would be used for using/placing.
     * This means, that it must either be in the main hand, or the main hand must be empty and the item is in the offhand.
     * @param player
     * @param stack
     * @return
     */
    @Nullable
    public static InteractionHand getUsedHandForItem(Player player, ItemStack stack)
    {
        InteractionHand hand = null;

        if (InventoryUtils.areStacksEqual(player.getMainHandItem(), stack))
        {
            hand = InteractionHand.MAIN_HAND;
        }
        else if (player.getMainHandItem().isEmpty() &&
                 InventoryUtils.areStacksEqual(player.getOffhandItem(), stack))
        {
            hand = InteractionHand.OFF_HAND;
        }

        return hand;
    }

    public static boolean areStacksEqualIgnoreDurability(ItemStack stack1, ItemStack stack2)
    {
        return ItemStack.isSame(stack1, stack2) && ItemStack.tagMatches(stack1, stack2);
    }

    public static Direction getHorizontalLookingDirection(Entity entity)
    {
        return Direction.fromYRot(entity.getYRot());
    }

    public static Direction getVerticalLookingDirection(Entity entity)
    {
        return entity.getXRot() > 0 ? Direction.DOWN : Direction.UP;
    }

    public static Direction getClosestLookingDirection(Entity entity)
    {
        if (entity.getXRot() > 60.0f)
        {
            return Direction.DOWN;
        }
        else if (-entity.getXRot() > 60.0f)
        {
            return Direction.UP;
        }

        return getHorizontalLookingDirection(entity);
    }

    @Nullable
    public static <T extends Entity> T findEntityByUUID(List<T> list, UUID uuid)
    {
        if (uuid == null)
        {
            return null;
        }

        for (T entity : list)
        {
            if (entity.getUUID().equals(uuid))
            {
                return entity;
            }
        }

        return null;
    }

    @Nullable
    public static String getEntityId(Entity entity)
    {
        EntityType<?> entitytype = entity.getType();
        ResourceLocation resourcelocation = EntityType.getKey(entitytype);
        return entitytype.canSerialize() && resourcelocation != null ? resourcelocation.toString() : null;
    }

    @Nullable
    private static Entity createEntityFromNBTSingle(CompoundTag nbt, Level world)
    {
        try
        {
            Optional<Entity> optional = EntityType.create(nbt, world);

            if (optional.isPresent())
            {
                Entity entity = optional.get();
                entity.setUUID(UUID.randomUUID());
                return entity;
            }
        }
        catch (Exception ignore)
        {
        }

        return null;
    }

    /**
     * Note: This does NOT spawn any of the entities in the world!
     * @param nbt
     * @param world
     * @return
     */
    @Nullable
    public static Entity createEntityAndPassengersFromNBT(CompoundTag nbt, Level world)
    {
        Entity entity = createEntityFromNBTSingle(nbt, world);

        if (entity == null)
        {
            return null;
        }
        else
        {
            if (nbt.contains("Passengers", Constants.NBT.TAG_LIST))
            {
                ListTag taglist = nbt.getList("Passengers", Constants.NBT.TAG_COMPOUND);

                for (int i = 0; i < taglist.size(); ++i)
                {
                    Entity passenger = createEntityAndPassengersFromNBT(taglist.getCompound(i), world);

                    if (passenger != null)
                    {
                        passenger.startRiding(entity, true);
                    }
                }
            }

            return entity;
        }
    }

    public static void spawnEntityAndPassengersInWorld(Entity entity, Level world)
    {
        if (world.addFreshEntity(entity) && entity.isVehicle())
        {
            for (Entity passenger : entity.getPassengers())
            {
                passenger.moveTo(
                        entity.getX(),
                        entity.getY() + entity.getPassengersRidingOffset() + passenger.getMyRidingOffset(),
                        entity.getZ(),
                        passenger.getYRot(), passenger.getXRot());
                setEntityRotations(passenger, passenger.getYRot(), passenger.getXRot());
                spawnEntityAndPassengersInWorld(passenger, world);
            }
        }
    }

    public static void setEntityRotations(Entity entity, float yaw, float pitch)
    {
        entity.setYRot(yaw);
        entity.yRotO = yaw;

        entity.setXRot(pitch);
        entity.xRotO = pitch;

        if (entity instanceof LivingEntity livingBase)
        {
            livingBase.yHeadRot = yaw;
            livingBase.yBodyRot = yaw;
            livingBase.yHeadRotO = yaw;
            livingBase.yBodyRotO = yaw;
            //livingBase.renderYawOffset = yaw;
            //livingBase.prevRenderYawOffset = yaw;
        }
    }

    public static List<Entity> getEntitiesWithinSubRegion(Level world, BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement)
    {
        // These are the untransformed relative positions
        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        BlockPos posEndAbs = PositionUtils.getTransformedPlacementPosition(regionSize.offset(-1, -1, -1), schematicPlacement, placement).offset(regionPosRelTransformed).offset(origin);
        BlockPos regionPosAbs = regionPosRelTransformed.offset(origin);
        AABB bb = PositionUtils.createEnclosingAABB(regionPosAbs, posEndAbs);

        return world.getEntities((Entity) null, bb, EntityUtils.NOT_PLAYER);
    }

    public static boolean shouldPickBlock(Player player)
    {
        return Configs.Generic.PICK_BLOCK_ENABLED.getBooleanValue() &&
                (Configs.Generic.TOOL_ITEM_ENABLED.getBooleanValue() == false ||
                hasToolItem(player) == false) &&
                Configs.Visuals.ENABLE_RENDERING.getBooleanValue() &&
                Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue();
    }
}
