package fi.dy.masa.litematica.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.World;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic.EntityInfo;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.util.IntBoundingBox;

public class SchematicPlacingUtils
{
    public static boolean placeToWorldWithinChunk(World world,
                                                  ChunkPos chunkPos,
                                                  SchematicPlacement schematicPlacement,
                                                  ReplaceBehavior replace,
                                                  boolean notifyNeighbors)
    {
        LitematicaSchematic schematic = schematicPlacement.getSchematic();
        Set<String> regionsTouchingChunk = schematicPlacement.getRegionsTouchingChunk(chunkPos.x, chunkPos.z);
        BlockPos origin = schematicPlacement.getOrigin();
        boolean allSuccess = true;

        try
        {
            if (notifyNeighbors == false)
            {
                WorldUtils.setShouldPreventBlockUpdates(world, true);
            }

            for (String regionName : regionsTouchingChunk)
            {
                LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

                if (container == null)
                {
                    allSuccess = false;
                    continue;
                }

                SubRegionPlacement placement = schematicPlacement.getRelativeSubRegionPlacement(regionName);

                if (placement.isEnabled())
                {
                    Map<BlockPos, CompoundNBT> blockEntityMap = schematic.getBlockEntityMapForRegion(regionName);

                    if (placeBlocksWithinChunk(world, chunkPos, regionName, container, blockEntityMap,
                                               origin, schematicPlacement, placement, replace, notifyNeighbors) == false)
                    {
                        allSuccess = false;
                        Litematica.logger.warn("Invalid/missing schematic data in schematic '{}' for sub-region '{}'", schematic.getMetadata().getName(), regionName);
                    }

                    List<EntityInfo> entityList = schematic.getEntityListForRegion(regionName);

                    if (schematicPlacement.ignoreEntities() == false &&
                        placement.ignoreEntities() == false && entityList != null)
                    {
                        placeEntitiesToWorldWithinChunk(world, chunkPos, entityList, origin, schematicPlacement, placement);
                    }
                }
            }
        }
        finally
        {
            WorldUtils.setShouldPreventBlockUpdates(world, false);
        }

        return allSuccess;
    }

    public static boolean placeBlocksWithinChunk(World world, ChunkPos chunkPos, String regionName,
                                                 LitematicaBlockStateContainer container,
                                                 Map<BlockPos, CompoundNBT> blockEntityMap,
                                                 BlockPos origin,
                                                 SchematicPlacement schematicPlacement,
                                                 SubRegionPlacement placement,
                                                 ReplaceBehavior replace, boolean notifyNeighbors)
    {
        IntBoundingBox bounds = schematicPlacement.getBoxWithinChunkForRegion(regionName, chunkPos.x, chunkPos.z);
        Vector3i regionSize = schematicPlacement.getSchematic().getAreaSize(regionName);

        if (bounds == null || container == null || blockEntityMap == null || regionSize == null)
        {
            return false;
        }

        BlockPos regionPos = placement.getPos();

        // These are the untransformed relative positions
        BlockPos posEndRel = (new BlockPos(PositionUtils.getRelativeEndPositionFromAreaSize(regionSize))).add(regionPos);
        BlockPos posMinRel = PositionUtils.getMinCorner(regionPos, posEndRel);

        // The transformed sub-region origin position
        BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());

        // The relative offset of the affected region's corners, to the sub-region's origin corner
        BlockPos boxMinRel = new BlockPos(bounds.minX - origin.getX() - regionPosTransformed.getX(), 0, bounds.minZ - origin.getZ() - regionPosTransformed.getZ());
        BlockPos boxMaxRel = new BlockPos(bounds.maxX - origin.getX() - regionPosTransformed.getX(), 0, bounds.maxZ - origin.getZ() - regionPosTransformed.getZ());

        // Reverse transform that relative offset, to get the untransformed orientation's offsets
        boxMinRel = PositionUtils.getReverseTransformedBlockPos(boxMinRel, placement.getMirror(), placement.getRotation());
        boxMaxRel = PositionUtils.getReverseTransformedBlockPos(boxMaxRel, placement.getMirror(), placement.getRotation());

        boxMinRel = PositionUtils.getReverseTransformedBlockPos(boxMinRel, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        boxMaxRel = PositionUtils.getReverseTransformedBlockPos(boxMaxRel, schematicPlacement.getMirror(), schematicPlacement.getRotation());

        // Get the offset relative to the sub-region's minimum corner, instead of the origin corner (which can be at any corner)
        boxMinRel = boxMinRel.subtract(posMinRel.subtract(regionPos));
        boxMaxRel = boxMaxRel.subtract(posMinRel.subtract(regionPos));

        BlockPos posMin = PositionUtils.getMinCorner(boxMinRel, boxMaxRel);
        BlockPos posMax = PositionUtils.getMaxCorner(boxMinRel, boxMaxRel);

        final int startX = posMin.getX();
        final int startZ = posMin.getZ();
        final int endX = posMax.getX();
        final int endZ = posMax.getZ();

        final int startY = 0;
        final int endY = Math.abs(regionSize.getY()) - 1;
        BlockPos.Mutable posMutable = new BlockPos.Mutable();

        //System.out.printf("sx: %d, sy: %d, sz: %d => ex: %d, ey: %d, ez: %d\n", startX, startY, startZ, endX, endY, endZ);

        if (startX < 0 || startZ < 0 || endX >= container.getSize().getX() || endZ >= container.getSize().getZ())
        {
            System.out.printf("DEBUG ============= OUT OF BOUNDS - region: %s, sx: %d, sz: %d, ex: %d, ez: %d - size x: %d z: %d =============\n",
                              regionName, startX, startZ, endX, endZ, container.getSize().getX(), container.getSize().getZ());
            return false;
        }

        final Rotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        final BlockState barrier = Blocks.BARRIER.getDefaultState();
        Mirror mirrorSub = placement.getMirror();
        final boolean ignoreInventories = Configs.Generic.PASTE_IGNORE_INVENTORY.getBooleanValue();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
            schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        for (int y = startY; y <= endY; ++y)
        {
            for (int z = startZ; z <= endZ; ++z)
            {
                for (int x = startX; x <= endX; ++x)
                {
                    BlockState state = container.get(x, y, z);

                    if (state.getBlock() == Blocks.STRUCTURE_VOID)
                    {
                        continue;
                    }

                    posMutable.setPos(x, y, z);
                    CompoundNBT teNBT = blockEntityMap.get(posMutable);

                    posMutable.setPos(posMinRel.getX() + x - regionPos.getX(),
                                   posMinRel.getY() + y - regionPos.getY(),
                                   posMinRel.getZ() + z - regionPos.getZ());

                    BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement);
                    pos = pos.add(regionPosTransformed).add(origin);

                    BlockState stateOld = world.getBlockState(pos);

                    if ((replace == ReplaceBehavior.NONE && stateOld.getMaterial() != Material.AIR) ||
                        (replace == ReplaceBehavior.WITH_NON_AIR && state.getMaterial() == Material.AIR))
                    {
                        continue;
                    }

                    if (mirrorMain != Mirror.NONE) { state = state.mirror(mirrorMain); }
                    if (mirrorSub != Mirror.NONE)  { state = state.mirror(mirrorSub); }
                    if (rotationCombined != Rotation.NONE) { state = state.rotate(rotationCombined); }

                    TileEntity te = world.getTileEntity(pos);

                    if (te != null)
                    {
                        if (te instanceof IInventory)
                        {
                            ((IInventory) te).clear();
                        }

                        world.setBlockState(pos, barrier, 0x14);
                    }

                    if (world.setBlockState(pos, state, 0x12) && teNBT != null)
                    {
                        te = world.getTileEntity(pos);

                        if (te != null)
                        {
                            teNBT = teNBT.copy();
                            teNBT.putInt("x", pos.getX());
                            teNBT.putInt("y", pos.getY());
                            teNBT.putInt("z", pos.getZ());

                            if (ignoreInventories)
                            {
                                teNBT.remove("Items");
                            }

                            try
                            {
                                te.read(state, teNBT);

                                if (ignoreInventories && te instanceof IInventory)
                                {
                                    ((IInventory) te).clear();
                                }
                            }
                            catch (Exception e)
                            {
                                Litematica.logger.warn("Failed to load TileEntity data for {} @ {}", state, pos);
                            }
                        }
                    }
                }
            }
        }

        if (notifyNeighbors)
        {
            for (int y = startY; y <= endY; ++y)
            {
                for (int z = startZ; z <= endZ; ++z)
                {
                    for (int x = startX; x <= endX; ++x)
                    {
                        posMutable.setPos(posMinRel.getX() + x - regionPos.getX(),
                                       posMinRel.getY() + y - regionPos.getY(),
                                       posMinRel.getZ() + z - regionPos.getZ());
                        BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement).add(origin);
                        world.updateBlock(pos, world.getBlockState(pos).getBlock());
                    }
                }
            }
        }

        return true;
    }

    public static void placeEntitiesToWorldWithinChunk(World world, ChunkPos chunkPos,
                                                       List<EntityInfo> entityList,
                                                       BlockPos origin,
                                                       SchematicPlacement schematicPlacement,
                                                       SubRegionPlacement placement)
    {
        BlockPos regionPos = placement.getPos();

        if (entityList == null)
        {
            return;
        }

        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        final int offX = regionPosRelTransformed.getX() + origin.getX();
        final int offY = regionPosRelTransformed.getY() + origin.getY();
        final int offZ = regionPosRelTransformed.getZ() + origin.getZ();
        final double minX = (chunkPos.x << 4);
        final double minZ = (chunkPos.z << 4);
        final double maxX = (chunkPos.x << 4) + 16;
        final double maxZ = (chunkPos.z << 4) + 16;

        final Rotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        Mirror mirrorSub = placement.getMirror();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
            schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        for (EntityInfo info : entityList)
        {
            Entity entity = EntityUtils.createEntityAndPassengersFromNBT(info.nbt, world);

            if (entity != null)
            {
                Vector3d pos = info.posVec;
                pos = PositionUtils.getTransformedPosition(pos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
                pos = PositionUtils.getTransformedPosition(pos, placement.getMirror(), placement.getRotation());
                double x = pos.x + offX;
                double y = pos.y + offY;
                double z = pos.z + offZ;

                if (x >= minX && x < maxX && z >= minZ && z < maxZ)
                {
                    rotateEntity(entity, x, y, z, rotationCombined, mirrorMain, mirrorSub);
                    //System.out.printf("post: %.1f - rot: %s, mm: %s, ms: %s\n", rotationYaw, rotationCombined, mirrorMain, mirrorSub);
                    EntityUtils.spawnEntityAndPassengersInWorld(entity, world);
                }
            }
        }
    }

    public static void rotateEntity(Entity entity, double x, double y, double z,
                                    Rotation rotationCombined, Mirror mirrorMain, Mirror mirrorSub)
    {
        float rotationYaw = entity.rotationYaw;

        if (mirrorMain != Mirror.NONE)         { rotationYaw = entity.getMirroredYaw(mirrorMain); }
        if (mirrorSub != Mirror.NONE)          { rotationYaw = entity.getMirroredYaw(mirrorSub); }
        if (rotationCombined != Rotation.NONE) { rotationYaw += entity.rotationYaw - entity.getRotatedYaw(rotationCombined); }

        entity.setLocationAndAngles(x, y, z, rotationYaw, entity.rotationPitch);
        EntityUtils.setEntityRotations(entity, rotationYaw, entity.rotationPitch);
    }
}
