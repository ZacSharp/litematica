package fi.dy.masa.litematica.schematic;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TickNextTickData;
import net.minecraft.world.level.TickPriority;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.conversion.SchematicConversionFixers;
import fi.dy.masa.litematica.schematic.conversion.SchematicConversionMaps;
import fi.dy.masa.litematica.schematic.conversion.SchematicConverter;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.BlockUtils;
import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.litematica.util.NbtUtils;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.ReplaceBehavior;
import fi.dy.masa.litematica.util.SchematicPlacingUtils;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.util.Constants;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.IntBoundingBox;
import fi.dy.masa.malilib.util.NBTUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class LitematicaSchematic
{
    public static final String FILE_EXTENSION = ".litematic";
    public static final int SCHEMATIC_VERSION_1_13_2 = 5;
    public static final int MINECRAFT_DATA_VERSION_1_13_2 = 1631; // MC 1.13.2

    public static final int SCHEMATIC_VERSION = 5;
    public static final int MINECRAFT_DATA_VERSION = SharedConstants.getCurrentVersion().getWorldVersion();

    private final Map<String, LitematicaBlockStateContainer> blockContainers = new HashMap<>();
    private final Map<String, Map<BlockPos, CompoundTag>> tileEntities = new HashMap<>();
    private final Map<String, Map<BlockPos, TickNextTickData<Block>>> pendingBlockTicks = new HashMap<>();
    private final Map<String, Map<BlockPos, TickNextTickData<Fluid>>> pendingFluidTicks = new HashMap<>();
    private final Map<String, List<EntityInfo>> entities = new HashMap<>();
    private final Map<String, BlockPos> subRegionPositions = new HashMap<>();
    private final Map<String, BlockPos> subRegionSizes = new HashMap<>();
    private final SchematicMetadata metadata = new SchematicMetadata();
    private final SchematicConverter converter;
    private int totalBlocksReadFromWorld;
    @Nullable private final File schematicFile;
    private final FileType schematicType;

    private LitematicaSchematic(@Nullable File file)
    {
        this(file, FileType.LITEMATICA_SCHEMATIC);
    }

    private LitematicaSchematic(@Nullable File file, FileType schematicType)
    {
        this.schematicFile = file;
        this.schematicType = schematicType;
        this.converter = SchematicConverter.createForLitematica();
    }

    @Nullable
    public File getFile()
    {
        return this.schematicFile;
    }

    public Vec3i getTotalSize()
    {
        return this.metadata.getEnclosingSize();
    }

    public int getTotalBlocksReadFromWorld()
    {
        return this.totalBlocksReadFromWorld;
    }

    public SchematicMetadata getMetadata()
    {
        return this.metadata;
    }

    public int getSubRegionCount()
    {
        return this.blockContainers.size();
    }

    @Nullable
    public BlockPos getSubRegionPosition(String areaName)
    {
        return this.subRegionPositions.get(areaName);
    }

    public Map<String, BlockPos> getAreaPositions()
    {
        ImmutableMap.Builder<String, BlockPos> builder = ImmutableMap.builder();

        for (String name : this.subRegionPositions.keySet())
        {
            BlockPos pos = this.subRegionPositions.get(name);
            builder.put(name, pos);
        }

        return builder.build();
    }

    public Map<String, BlockPos> getAreaSizes()
    {
        ImmutableMap.Builder<String, BlockPos> builder = ImmutableMap.builder();

        for (String name : this.subRegionSizes.keySet())
        {
            BlockPos pos = this.subRegionSizes.get(name);
            builder.put(name, pos);
        }

        return builder.build();
    }

    @Nullable
    public BlockPos getAreaSize(String regionName)
    {
        return this.subRegionSizes.get(regionName);
    }

    public Map<String, Box> getAreas()
    {
        ImmutableMap.Builder<String, Box> builder = ImmutableMap.builder();

        for (String name : this.subRegionPositions.keySet())
        {
            BlockPos pos = this.subRegionPositions.get(name);
            BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(this.subRegionSizes.get(name));
            Box box = new Box(pos, pos.offset(posEndRel), name);
            builder.put(name, box);
        }

        return builder.build();
    }

    @Nullable
    public static LitematicaSchematic createFromWorld(Level world, AreaSelection area, SchematicSaveInfo info,
                                                      String author, IStringConsumer feedback)
    {
        List<Box> boxes = PositionUtils.getValidBoxes(area);

        if (boxes.isEmpty())
        {
            feedback.setString(StringUtils.translate("litematica.error.schematic.create.no_selections"));
            return null;
        }

        LitematicaSchematic schematic = new LitematicaSchematic(null);
        long time = System.currentTimeMillis();

        BlockPos origin = area.getEffectiveOrigin();
        schematic.setSubRegionPositions(boxes, origin);
        schematic.setSubRegionSizes(boxes);

        schematic.takeBlocksFromWorld(world, boxes, info);

        if (info.ignoreEntities == false)
        {
            schematic.takeEntitiesFromWorld(world, boxes, origin);
        }

        schematic.metadata.setAuthor(author);
        schematic.metadata.setName(area.getName());
        schematic.metadata.setTimeCreated(time);
        schematic.metadata.setTimeModified(time);
        schematic.metadata.setRegionCount(boxes.size());
        schematic.metadata.setTotalVolume(PositionUtils.getTotalVolume(boxes));
        schematic.metadata.setEnclosingSize(PositionUtils.getEnclosingAreaSize(boxes));
        schematic.metadata.setTotalBlocks(schematic.totalBlocksReadFromWorld);

        return schematic;
    }

    /**
     * Creates an empty schematic with all the maps and lists and containers already created.
     * This is intended to be used for the chunk-wise schematic creation.
     * @param area
     * @param author
     * @return
     */
    public static LitematicaSchematic createEmptySchematic(AreaSelection area, String author)
    {
        List<Box> boxes = PositionUtils.getValidBoxes(area);

        if (boxes.isEmpty())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, StringUtils.translate("litematica.error.schematic.create.no_selections"));
            return null;
        }

        LitematicaSchematic schematic = new LitematicaSchematic(null);
        schematic.setSubRegionPositions(boxes, area.getEffectiveOrigin());
        schematic.setSubRegionSizes(boxes);
        schematic.metadata.setAuthor(author);
        schematic.metadata.setName(area.getName());
        schematic.metadata.setRegionCount(boxes.size());
        schematic.metadata.setTotalVolume(PositionUtils.getTotalVolume(boxes));
        schematic.metadata.setEnclosingSize(PositionUtils.getEnclosingAreaSize(boxes));

        for (Box box : boxes)
        {
            String regionName = box.getName();
            BlockPos size = box.getSize();
            final int sizeX = Math.abs(size.getX());
            final int sizeY = Math.abs(size.getY());
            final int sizeZ = Math.abs(size.getZ());
            LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(sizeX, sizeY, sizeZ);
            schematic.blockContainers.put(regionName, container);
            schematic.tileEntities.put(regionName, new HashMap<>());
            schematic.entities.put(regionName, new ArrayList<>());
            schematic.pendingBlockTicks.put(regionName, new HashMap<>());
            schematic.pendingFluidTicks.put(regionName, new HashMap<>());
        }

        return schematic;
    }

    public void takeEntityDataFromSchematicaSchematic(SchematicaSchematic schematic, String subRegionName)
    {
        this.tileEntities.put(subRegionName, schematic.getTiles());
        this.entities.put(subRegionName, schematic.getEntities());
    }

    public boolean placeToWorld(Level world, SchematicPlacement schematicPlacement, boolean notifyNeighbors)
    {
        return this.placeToWorld(world, schematicPlacement, notifyNeighbors, false);
    }

    public boolean placeToWorld(Level world, SchematicPlacement schematicPlacement, boolean notifyNeighbors, boolean ignoreEntities)
    {
        WorldUtils.setShouldPreventBlockUpdates(world, true);

        ImmutableMap<String, SubRegionPlacement> relativePlacements = schematicPlacement.getEnabledRelativeSubRegionPlacements();
        BlockPos origin = schematicPlacement.getOrigin();

        for (String regionName : relativePlacements.keySet())
        {
            SubRegionPlacement placement = relativePlacements.get(regionName);

            if (placement.isEnabled())
            {
                BlockPos regionPos = placement.getPos();
                BlockPos regionSize = this.subRegionSizes.get(regionName);
                LitematicaBlockStateContainer container = this.blockContainers.get(regionName);
                Map<BlockPos, CompoundTag> tileMap = this.tileEntities.get(regionName);
                List<EntityInfo> entityList = this.entities.get(regionName);
                Map<BlockPos, TickNextTickData<Block>> scheduledBlockTicks = this.pendingBlockTicks.get(regionName);
                Map<BlockPos, TickNextTickData<Fluid>> scheduledFluidTicks = this.pendingFluidTicks.get(regionName);

                if (regionPos != null && regionSize != null && container != null && tileMap != null)
                {
                    this.placeBlocksToWorld(world, origin, regionPos, regionSize, schematicPlacement, placement, container, tileMap, scheduledBlockTicks, scheduledFluidTicks, notifyNeighbors);
                }
                else
                {
                    Litematica.logger.warn("Invalid/missing schematic data in schematic '{}' for sub-region '{}'", this.metadata.getName(), regionName);
                }

                if (ignoreEntities == false && schematicPlacement.ignoreEntities() == false &&
                    placement.ignoreEntities() == false && entityList != null)
                {
                    this.placeEntitiesToWorld(world, origin, regionPos, regionSize, schematicPlacement, placement, entityList);
                }
            }
        }

        WorldUtils.setShouldPreventBlockUpdates(world, false);

        return true;
    }

    private boolean placeBlocksToWorld(Level world, BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement,
            LitematicaBlockStateContainer container, Map<BlockPos, CompoundTag> tileMap,
            @Nullable Map<BlockPos, TickNextTickData<Block>> scheduledBlockTicks,
            @Nullable Map<BlockPos, TickNextTickData<Fluid>> scheduledFluidTicks, boolean notifyNeighbors)
    {
        // These are the untransformed relative positions
        BlockPos posEndRelSub = PositionUtils.getRelativeEndPositionFromAreaSize(regionSize);
        BlockPos posEndRel = posEndRelSub.offset(regionPos);
        BlockPos posMinRel = PositionUtils.getMinCorner(regionPos, posEndRel);

        BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        //BlockPos posEndAbs = PositionUtils.getTransformedBlockPos(posEndRelSub, placement.getMirror(), placement.getRotation()).add(regionPosTransformed).add(origin);
        BlockPos regionPosAbs = regionPosTransformed.offset(origin);

        /*
        if (PositionUtils.arePositionsWithinWorld(world, regionPosAbs, posEndAbs) == false)
        {
            return false;
        }
        */

        final int sizeX = Math.abs(regionSize.getX());
        final int sizeY = Math.abs(regionSize.getY());
        final int sizeZ = Math.abs(regionSize.getZ());
        final BlockState barrier = Blocks.BARRIER.defaultBlockState();
        final boolean ignoreInventories = Configs.Generic.PASTE_IGNORE_INVENTORY.getBooleanValue();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();
        ReplaceBehavior replace = (ReplaceBehavior) Configs.Generic.PASTE_REPLACE_BEHAVIOR.getOptionListValue();

        final Rotation rotationCombined = schematicPlacement.getRotation().getRotated(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        Mirror mirrorSub = placement.getMirror();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
             schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        int bottomY = world.getMinBuildHeight();
        int topY = world.getMaxBuildHeight();
        int tmp = posMinRel.getY() - regionPos.getY() + regionPosTransformed.getY() + origin.getY();
        int startY = 0;
        int endY = sizeY;

        if (tmp < bottomY)
        {
            startY += (bottomY - tmp);
        }

        tmp = posMinRel.getY() - regionPos.getY() + regionPosTransformed.getY() + origin.getY() + (endY - 1);

        if (tmp > topY)
        {
            endY -= (tmp - topY);
        }

        for (int y = startY; y < endY; ++y)
        {
            for (int z = 0; z < sizeZ; ++z)
            {
                for (int x = 0; x < sizeX; ++x)
                {
                    BlockState state = container.get(x, y, z);

                    if (state.getBlock() == Blocks.STRUCTURE_VOID)
                    {
                        continue;
                    }

                    posMutable.set(x, y, z);
                    CompoundTag teNBT = tileMap.get(posMutable);

                    posMutable.set( posMinRel.getX() + x - regionPos.getX(),
                                    posMinRel.getY() + y - regionPos.getY(),
                                    posMinRel.getZ() + z - regionPos.getZ());

                    BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement);
                    pos = pos.offset(regionPosTransformed).offset(origin);

                    BlockState stateOld = world.getBlockState(pos);

                    if ((replace == ReplaceBehavior.NONE && stateOld.isAir() == false) ||
                        (replace == ReplaceBehavior.WITH_NON_AIR && state.isAir()))
                    {
                        continue;
                    }

                    if (mirrorMain != Mirror.NONE) { state = state.mirror(mirrorMain); }
                    if (mirrorSub != Mirror.NONE)  { state = state.mirror(mirrorSub); }
                    if (rotationCombined != Rotation.NONE) { state = state.rotate(rotationCombined); }

                    if (stateOld == state && state.hasBlockEntity() == false)
                    {
                        continue;
                    }

                    BlockEntity teOld = world.getBlockEntity(pos);

                    if (teOld != null)
                    {
                        if (teOld instanceof Container)
                        {
                            ((Container) teOld).clearContent();
                        }

                        world.setBlock(pos, barrier, 0x14);
                    }

                    if (world.setBlock(pos, state, 0x12) && teNBT != null)
                    {
                        BlockEntity te = world.getBlockEntity(pos);

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
                                te.load(teNBT);

                                if (ignoreInventories && te instanceof Container)
                                {
                                    ((Container) te).clearContent();
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
            for (int y = 0; y < sizeY; ++y)
            {
                for (int z = 0; z < sizeZ; ++z)
                {
                    for (int x = 0; x < sizeX; ++x)
                    {
                        posMutable.set( posMinRel.getX() + x - regionPos.getX(),
                                        posMinRel.getY() + y - regionPos.getY(),
                                        posMinRel.getZ() + z - regionPos.getZ());
                        BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement).offset(origin);
                        world.blockUpdated(pos, world.getBlockState(pos).getBlock());
                    }
                }
            }
        }

        if (world instanceof ServerLevel)
        {
            ServerLevel serverWorld = (ServerLevel) world;

            if (scheduledBlockTicks != null && scheduledBlockTicks.isEmpty() == false)
            {
                for (Map.Entry<BlockPos, TickNextTickData<Block>> entry : scheduledBlockTicks.entrySet())
                {
                    BlockPos pos = entry.getKey().offset(regionPosAbs);
                    TickNextTickData<Block> tick = entry.getValue();
                    serverWorld.getBlockTicks().scheduleTick(pos, tick.getType(), (int) tick.triggerTick, tick.priority);
                }
            }

            if (scheduledFluidTicks != null && scheduledFluidTicks.isEmpty() == false)
            {
                for (Map.Entry<BlockPos, TickNextTickData<Fluid>> entry : scheduledFluidTicks.entrySet())
                {
                    BlockPos pos = entry.getKey().offset(regionPosAbs);
                    BlockState state = world.getBlockState(pos);

                    if (state.getFluidState().isEmpty() == false)
                    {
                        TickNextTickData<Fluid> tick = entry.getValue();
                        serverWorld.getLiquidTicks().scheduleTick(pos, tick.getType(), (int) tick.triggerTick, tick.priority);
                    }
                }
            }
        }

        return true;
    }

    private void placeEntitiesToWorld(Level world, BlockPos origin, BlockPos regionPos, BlockPos regionSize, SchematicPlacement schematicPlacement, SubRegionPlacement placement, List<EntityInfo> entityList)
    {
        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        final int offX = regionPosRelTransformed.getX() + origin.getX();
        final int offY = regionPosRelTransformed.getY() + origin.getY();
        final int offZ = regionPosRelTransformed.getZ() + origin.getZ();

        final Rotation rotationCombined = schematicPlacement.getRotation().getRotated(placement.getRotation());
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
                Vec3 pos = info.posVec;
                pos = PositionUtils.getTransformedPosition(pos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
                pos = PositionUtils.getTransformedPosition(pos, placement.getMirror(), placement.getRotation());
                double x = pos.x + offX;
                double y = pos.y + offY;
                double z = pos.z + offZ;

                SchematicPlacingUtils.rotateEntity(entity, x, y, z, rotationCombined, mirrorMain, mirrorSub);
                EntityUtils.spawnEntityAndPassengersInWorld(entity, world);
            }
        }
    }

    private void takeEntitiesFromWorld(Level world, List<Box> boxes, BlockPos origin)
    {
        for (Box box : boxes)
        {
            net.minecraft.world.phys.AABB bb = PositionUtils.createEnclosingAABB(box.getPos1(), box.getPos2());
            BlockPos regionPosAbs = box.getPos1();
            List<EntityInfo> list = new ArrayList<>();
            List<Entity> entities = world.getEntities(null, bb, EntityUtils.NOT_PLAYER);

            for (Entity entity : entities)
            {
                CompoundTag tag = new CompoundTag();

                if (entity.save(tag))
                {
                    Vec3 posVec = new Vec3(entity.getX() - regionPosAbs.getX(), entity.getY() - regionPosAbs.getY(), entity.getZ() - regionPosAbs.getZ());
                    NBTUtils.writeEntityPositionToTag(posVec, tag);
                    list.add(new EntityInfo(posVec, tag));
                }
            }

            this.entities.put(box.getName(), list);
        }
    }

    public void takeEntitiesFromWorldWithinChunk(Level world, int chunkX, int chunkZ,
            ImmutableMap<String, IntBoundingBox> volumes, ImmutableMap<String, Box> boxes,
            Set<UUID> existingEntities, BlockPos origin)
    {
        for (Map.Entry<String, IntBoundingBox> entry : volumes.entrySet())
        {
            String regionName = entry.getKey();
            List<EntityInfo> list = this.entities.get(regionName);
            Box box = boxes.get(regionName);

            if (box == null || list == null)
            {
                continue;
            }

            net.minecraft.world.phys.AABB bb = PositionUtils.createAABBFrom(entry.getValue());
            List<Entity> entities = world.getEntities(null, bb, EntityUtils.NOT_PLAYER);
            BlockPos regionPosAbs = box.getPos1();

            for (Entity entity : entities)
            {
                UUID uuid = entity.getUUID();
                /*
                if (entity.posX >= bb.minX && entity.posX < bb.maxX &&
                    entity.posY >= bb.minY && entity.posY < bb.maxY &&
                    entity.posZ >= bb.minZ && entity.posZ < bb.maxZ)
                */
                if (existingEntities.contains(uuid) == false)
                {
                    CompoundTag tag = new CompoundTag();

                    if (entity.save(tag))
                    {
                        Vec3 posVec = new Vec3(entity.getX() - regionPosAbs.getX(), entity.getY() - regionPosAbs.getY(), entity.getZ() - regionPosAbs.getZ());
                        NBTUtils.writeEntityPositionToTag(posVec, tag);
                        list.add(new EntityInfo(posVec, tag));
                        existingEntities.add(uuid);
                    }
                }
            }
        }
    }

    private void takeBlocksFromWorld(Level world, List<Box> boxes, SchematicSaveInfo info)
    {
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos(0, 0, 0);

        for (Box box : boxes)
        {
            BlockPos size = box.getSize();
            final int sizeX = Math.abs(size.getX());
            final int sizeY = Math.abs(size.getY());
            final int sizeZ = Math.abs(size.getZ());
            LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(sizeX, sizeY, sizeZ);
            Map<BlockPos, CompoundTag> tileEntityMap = new HashMap<>();
            Map<BlockPos, TickNextTickData<Block>> blockTickMap = new HashMap<>();
            Map<BlockPos, TickNextTickData<Fluid>> fluidTickMap = new HashMap<>();

            // We want to loop nice & easy from 0 to n here, but the per-sub-region pos1 can be at
            // any corner of the area. Thus we need to offset from the total area origin
            // to the minimum/negative corner (ie. 0,0 in the loop) corner here.
            final BlockPos minCorner = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            final int startX = minCorner.getX();
            final int startY = minCorner.getY();
            final int startZ = minCorner.getZ();
            final boolean visibleOnly = info.visibleOnly;

            for (int y = 0; y < sizeY; ++y)
            {
                for (int z = 0; z < sizeZ; ++z)
                {
                    for (int x = 0; x < sizeX; ++x)
                    {
                        posMutable.set(x + startX, y + startY, z + startZ);

                        if (visibleOnly && isExposed(world, posMutable) == false)
                        {
                            continue;
                        }

                        BlockState state = world.getBlockState(posMutable);
                        container.set(x, y, z, state);

                        if (state.isAir() == false)
                        {
                            this.totalBlocksReadFromWorld++;
                        }

                        if (state.hasBlockEntity())
                        {
                            BlockEntity te = world.getBlockEntity(posMutable);

                            if (te != null)
                            {
                                // TODO Add a TileEntity NBT cache from the Chunk packets, to get the original synced data (too)
                                BlockPos pos = new BlockPos(x, y, z);
                                CompoundTag tag = te.save(new CompoundTag());
                                NBTUtils.writeBlockPosToTag(pos, tag);
                                tileEntityMap.put(pos, tag);
                            }
                        }
                    }
                }
            }

            if (world instanceof ServerLevel)
            {
                IntBoundingBox tickBox = IntBoundingBox.createProper(
                        startX,         startY,         startZ,
                        startX + sizeX, startY + sizeY, startZ + sizeZ);
                List<TickNextTickData<Block>> blockTicks = ((ServerLevel) world).getBlockTicks().fetchTicksInArea(tickBox.toVanillaBox(), false, false);

                if (blockTicks != null)
                {
                    this.getPendingTicksFromWorld(blockTickMap, blockTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }

                List<TickNextTickData<Fluid>> fluidTicks = ((ServerLevel) world).getLiquidTicks().fetchTicksInArea(tickBox.toVanillaBox(), false, false);

                if (fluidTicks != null)
                {
                    this.getPendingTicksFromWorld(fluidTickMap, fluidTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }
            }

            this.blockContainers.put(box.getName(), container);
            this.tileEntities.put(box.getName(), tileEntityMap);
            this.pendingBlockTicks.put(box.getName(), blockTickMap);
            this.pendingFluidTicks.put(box.getName(), fluidTickMap);
        }
    }

    private <T> void getPendingTicksFromWorld(Map<BlockPos, TickNextTickData<T>> map, List<TickNextTickData<T>> list,
            BlockPos minCorner, int startY, int maxY, final long currentTime)
    {
        final int listSize = list.size();

        for (int i = 0; i < listSize; ++i)
        {
            TickNextTickData<T> entry = list.get(i);

            // The getPendingBlockUpdates() method doesn't check the y-coordinate... :-<
            if (entry.pos.getY() >= startY && entry.pos.getY() < maxY)
            {
                // Store the delay, ie. relative time
                BlockPos posRelative = new BlockPos(
                        entry.pos.getX() - minCorner.getX(),
                        entry.pos.getY() - minCorner.getY(),
                        entry.pos.getZ() - minCorner.getZ());
                TickNextTickData<T> newEntry = new TickNextTickData<>(posRelative, entry.getType(), entry.triggerTick - currentTime, entry.priority);

                map.put(posRelative, newEntry);
            }
        }
    }

    public static boolean isExposed(Level world, BlockPos pos)
    {
        for (Direction dir : fi.dy.masa.malilib.util.PositionUtils.ALL_DIRECTIONS)
        {
            BlockPos posAdj = pos.relative(dir);
            BlockState stateAdj = world.getBlockState(posAdj);

            if (stateAdj.canOcclude() == false ||
                stateAdj.isFaceSturdy(world, posAdj, dir) == false)
            {
                return true;
            }
        }

        return false;
    }

    public void takeBlocksFromWorldWithinChunk(Level world, ImmutableMap<String, IntBoundingBox> volumes,
                                               ImmutableMap<String, Box> boxes, SchematicSaveInfo info)
    {
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos(0, 0, 0);

        for (Map.Entry<String, IntBoundingBox> volumeEntry : volumes.entrySet())
        {
            String regionName = volumeEntry.getKey();
            IntBoundingBox bb = volumeEntry.getValue();
            Box box = boxes.get(regionName);

            if (box == null)
            {
                Litematica.logger.error("null Box for sub-region '{}' while trying to save chunk-wise schematic", regionName);
                continue;
            }

            LitematicaBlockStateContainer container = this.blockContainers.get(regionName);
            Map<BlockPos, CompoundTag> tileEntityMap = this.tileEntities.get(regionName);
            Map<BlockPos, TickNextTickData<Block>> blockTickMap = this.pendingBlockTicks.get(regionName);
            Map<BlockPos, TickNextTickData<Fluid>> fluidTickMap = this.pendingFluidTicks.get(regionName);

            if (container == null || tileEntityMap == null || blockTickMap == null || fluidTickMap == null)
            {
                Litematica.logger.error("null map(s) for sub-region '{}' while trying to save chunk-wise schematic", regionName);
                continue;
            }

            // We want to loop nice & easy from 0 to n here, but the per-sub-region pos1 can be at
            // any corner of the area. Thus we need to offset from the total area origin
            // to the minimum/negative corner (ie. 0,0 in the loop) corner here.
            final BlockPos minCorner = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            final int offsetX = minCorner.getX();
            final int offsetY = minCorner.getY();
            final int offsetZ = minCorner.getZ();
            // Relative coordinates within the sub-region container:
            final int startX = bb.minX - minCorner.getX();
            final int startY = bb.minY - minCorner.getY();
            final int startZ = bb.minZ - minCorner.getZ();
            final int endX = startX + (bb.maxX - bb.minX);
            final int endY = startY + (bb.maxY - bb.minY);
            final int endZ = startZ + (bb.maxZ - bb.minZ);
            final boolean visibleOnly = info.visibleOnly;

            for (int y = startY; y <= endY; ++y)
            {
                for (int z = startZ; z <= endZ; ++z)
                {
                    for (int x = startX; x <= endX; ++x)
                    {
                        posMutable.set(x + offsetX, y + offsetY, z + offsetZ);

                        if (visibleOnly && isExposed(world, posMutable) == false)
                        {
                            continue;
                        }

                        BlockState state = world.getBlockState(posMutable);
                        container.set(x, y, z, state);

                        if (state.isAir() == false)
                        {
                            this.totalBlocksReadFromWorld++;
                        }

                        if (state.hasBlockEntity())
                        {
                            BlockEntity te = world.getBlockEntity(posMutable);

                            if (te != null)
                            {
                                // TODO Add a TileEntity NBT cache from the Chunk packets, to get the original synced data (too)
                                BlockPos pos = new BlockPos(x, y, z);
                                CompoundTag tag = te.save(new CompoundTag());
                                NBTUtils.writeBlockPosToTag(pos, tag);
                                tileEntityMap.put(pos, tag);
                            }
                        }
                    }
                }
            }

            if (world instanceof ServerLevel)
            {
                IntBoundingBox tickBox = IntBoundingBox.createProper(
                        offsetX + startX  , offsetY + startY  , offsetZ + startZ  ,
                        offsetX + endX + 1, offsetY + endY + 1, offsetZ + endZ + 1);
                List<TickNextTickData<Block>> blockTicks = ((ServerLevel) world).getBlockTicks().fetchTicksInArea(tickBox.toVanillaBox(), false, false);

                if (blockTicks != null)
                {
                    this.getPendingTicksFromWorld(blockTickMap, blockTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }

                List<TickNextTickData<Fluid>> fluidTicks = ((ServerLevel) world).getLiquidTicks().fetchTicksInArea(tickBox.toVanillaBox(), false, false);

                if (fluidTicks != null)
                {
                    this.getPendingTicksFromWorld(fluidTickMap, fluidTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }
            }
        }
    }

    private void setSubRegionPositions(List<Box> boxes, BlockPos areaOrigin)
    {
        for (Box box : boxes)
        {
            this.subRegionPositions.put(box.getName(), box.getPos1().subtract(areaOrigin));
        }
    }

    private void setSubRegionSizes(List<Box> boxes)
    {
        for (Box box : boxes)
        {
            this.subRegionSizes.put(box.getName(), box.getSize());
        }
    }

    @Nullable
    public LitematicaBlockStateContainer getSubRegionContainer(String regionName)
    {
        return this.blockContainers.get(regionName);
    }

    @Nullable
    public Map<BlockPos, CompoundTag> getBlockEntityMapForRegion(String regionName)
    {
        return this.tileEntities.get(regionName);
    }

    @Nullable
    public List<EntityInfo> getEntityListForRegion(String regionName)
    {
        return this.entities.get(regionName);
    }

    @Nullable
    public Map<BlockPos, TickNextTickData<Block>> getScheduledBlockTicksForRegion(String regionName)
    {
        return this.pendingBlockTicks.get(regionName);
    }

    @Nullable
    public Map<BlockPos, TickNextTickData<Fluid>> getScheduledFluidTicksForRegion(String regionName)
    {
        return this.pendingFluidTicks.get(regionName);
    }

    private CompoundTag writeToNBT()
    {
        CompoundTag nbt = new CompoundTag();

        nbt.putInt("Version", SCHEMATIC_VERSION);
        nbt.putInt("MinecraftDataVersion", MINECRAFT_DATA_VERSION);
        nbt.put("Metadata", this.metadata.writeToNBT());
        nbt.put("Regions", this.writeSubRegionsToNBT());

        return nbt;
    }

    private CompoundTag writeSubRegionsToNBT()
    {
        CompoundTag wrapper = new CompoundTag();

        if (this.blockContainers.isEmpty() == false)
        {
            for (String regionName : this.blockContainers.keySet())
            {
                LitematicaBlockStateContainer blockContainer = this.blockContainers.get(regionName);
                Map<BlockPos, CompoundTag> tileMap = this.tileEntities.get(regionName);
                List<EntityInfo> entityList = this.entities.get(regionName);
                Map<BlockPos, TickNextTickData<Block>> pendingBlockTicks = this.pendingBlockTicks.get(regionName);
                Map<BlockPos, TickNextTickData<Fluid>> pendingFluidTicks = this.pendingFluidTicks.get(regionName);

                CompoundTag tag = new CompoundTag();

                tag.put("BlockStatePalette", blockContainer.getPalette().writeToNBT());
                tag.put("BlockStates", new LongArrayTag(blockContainer.getBackingLongArray()));
                tag.put("TileEntities", this.writeTileEntitiesToNBT(tileMap));

                if (pendingBlockTicks != null)
                {
                    tag.put("PendingBlockTicks", this.writePendingTicksToNBT(pendingBlockTicks));
                }

                if (pendingFluidTicks != null)
                {
                    tag.put("PendingFluidTicks", this.writePendingTicksToNBT(pendingFluidTicks));
                }

                // The entity list will not exist, if takeEntities is false when creating the schematic
                if (entityList != null)
                {
                    tag.put("Entities", this.writeEntitiesToNBT(entityList));
                }

                BlockPos pos = this.subRegionPositions.get(regionName);
                tag.put("Position", NBTUtils.createBlockPosTag(pos));

                pos = this.subRegionSizes.get(regionName);
                tag.put("Size", NBTUtils.createBlockPosTag(pos));

                wrapper.put(regionName, tag);
            }
        }

        return wrapper;
    }

    private ListTag writeEntitiesToNBT(List<EntityInfo> entityList)
    {
        ListTag tagList = new ListTag();

        if (entityList.isEmpty() == false)
        {
            for (EntityInfo info : entityList)
            {
                tagList.add(info.nbt);
            }
        }

        return tagList;
    }

    private <T> ListTag writePendingTicksToNBT(Map<BlockPos, TickNextTickData<T>> tickMap)
    {
        ListTag tagList = new ListTag();

        if (tickMap.isEmpty() == false)
        {
            for (TickNextTickData<T> entry : tickMap.values())
            {
                T target = entry.getType();
                String tagName;
                ResourceLocation rl;

                if (target instanceof Block)
                {
                    rl = Registry.BLOCK.getKey((Block) target);
                    tagName = "Block";
                }
                else
                {
                    rl = Registry.FLUID.getKey((Fluid) target);
                    tagName = "Fluid";
                }

                if (rl != null)
                {
                    CompoundTag tag = new CompoundTag();

                    tag.putString(tagName, rl.toString());
                    tag.putInt("Priority", entry.priority.getValue());
                    tag.putInt("Time", (int) entry.triggerTick);
                    tag.putInt("x", entry.pos.getX());
                    tag.putInt("y", entry.pos.getY());
                    tag.putInt("z", entry.pos.getZ());

                    tagList.add(tag);
                }
            }
        }

        return tagList;
    }

    private ListTag writeTileEntitiesToNBT(Map<BlockPos, CompoundTag> tileMap)
    {
        ListTag tagList = new ListTag();

        if (tileMap.isEmpty() == false)
        {
            for (CompoundTag tag : tileMap.values())
            {
                tagList.add(tag);
            }
        }

        return tagList;
    }

    private boolean readFromNBT(CompoundTag nbt)
    {
        this.blockContainers.clear();
        this.tileEntities.clear();
        this.entities.clear();
        this.pendingBlockTicks.clear();
        this.subRegionPositions.clear();
        this.subRegionSizes.clear();
        //this.metadata.clearModifiedSinceSaved();

        if (nbt.contains("Version", Constants.NBT.TAG_INT))
        {
            final int version = nbt.getInt("Version");
            final int minecraftDataVersion = nbt.getInt("MinecraftDataVersion");

            // Version 6 is used for 1.18 schematics, with the slightly different scheduled tick data.
            // It's still fine to load in 1.17 though... for the most part at least.
            if (version >= 1 && version <= 6)
            {
                this.metadata.readFromNBT(nbt.getCompound("Metadata"));
                this.readSubRegionsFromNBT(nbt.getCompound("Regions"), version, minecraftDataVersion);

                return true;
            }
            else
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_load.unsupported_schematic_version", version);
            }
        }
        else
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_load.no_schematic_version_information");
        }

        return false;
    }

    private void readSubRegionsFromNBT(CompoundTag tag, int version, int minecraftDataVersion)
    {
        for (String regionName : tag.getAllKeys())
        {
            if (tag.get(regionName).getId() == Constants.NBT.TAG_COMPOUND)
            {
                CompoundTag regionTag = tag.getCompound(regionName);
                BlockPos regionPos = NBTUtils.readBlockPos(regionTag.getCompound("Position"));
                BlockPos regionSize = NBTUtils.readBlockPos(regionTag.getCompound("Size"));
                Map<BlockPos, CompoundTag> tiles = null;

                if (regionPos != null && regionSize != null)
                {
                    this.subRegionPositions.put(regionName, regionPos);
                    this.subRegionSizes.put(regionName, regionSize);

                    if (version >= 2)
                    {
                        tiles = this.readTileEntitiesFromNBT(regionTag.getList("TileEntities", Constants.NBT.TAG_COMPOUND));
                        this.tileEntities.put(regionName, tiles);
                        this.entities.put(regionName, this.readEntitiesFromNBT(regionTag.getList("Entities", Constants.NBT.TAG_COMPOUND)));
                    }
                    else if (version == 1)
                    {
                        tiles = this.readTileEntitiesFromNBT_v1(regionTag.getList("TileEntities", Constants.NBT.TAG_COMPOUND));
                        this.tileEntities.put(regionName, tiles);
                        this.entities.put(regionName, this.readEntitiesFromNBT_v1(regionTag.getList("Entities", Constants.NBT.TAG_COMPOUND)));
                    }

                    if (version >= 3)
                    {
                        ListTag list = regionTag.getList("PendingBlockTicks", Constants.NBT.TAG_COMPOUND);
                        this.pendingBlockTicks.put(regionName, this.readPendingTicksFromNBT(list, Blocks.AIR));
                    }

                    if (version >= 5)
                    {
                        ListTag list = regionTag.getList("PendingFluidTicks", Constants.NBT.TAG_COMPOUND);
                        this.pendingFluidTicks.put(regionName, this.readPendingTicksFromNBT(list, Fluids.EMPTY));
                    }

                    Tag nbtBase = regionTag.get("BlockStates");

                    // There are no convenience methods in NBTTagCompound yet in 1.12, so we'll have to do it the ugly way...
                    if (nbtBase != null && nbtBase.getId() == Constants.NBT.TAG_LONG_ARRAY)
                    {
                        ListTag palette = regionTag.getList("BlockStatePalette", Constants.NBT.TAG_COMPOUND);
                        long[] blockStateArr = ((LongArrayTag) nbtBase).getAsLongArray();

                        BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(regionSize).offset(regionPos);
                        BlockPos posMin = PositionUtils.getMinCorner(regionPos, posEndRel);
                        BlockPos posMax = PositionUtils.getMaxCorner(regionPos, posEndRel);
                        BlockPos size = posMax.subtract(posMin).offset(1, 1, 1);

                        palette = this.convertBlockStatePalette_1_12_to_1_13_2(palette, version, minecraftDataVersion);

                        LitematicaBlockStateContainer container = LitematicaBlockStateContainer.createFrom(palette, blockStateArr, size);

                        if (minecraftDataVersion < MINECRAFT_DATA_VERSION)
                        {
                            this.postProcessContainerIfNeeded(palette, container, tiles);
                        }

                        this.blockContainers.put(regionName, container);
                    }
                }
            }
        }
    }

    public static boolean isSizeValid(@Nullable Vec3i size)
    {
        return size != null && size.getX() > 0 && size.getY() > 0 && size.getZ() > 0;
    }

    @Nullable
    private static Vec3i readSizeFromTagImpl(CompoundTag tag)
    {
        if (tag.contains("size", Constants.NBT.TAG_LIST))
        {
            ListTag tagList = tag.getList("size", Constants.NBT.TAG_INT);

            if (tagList.size() == 3)
            {
                return new Vec3i(tagList.getInt(0), tagList.getInt(1), tagList.getInt(2));
            }
        }

        return null;
    }

    @Nullable
    public static BlockPos readBlockPosFromNbtList(CompoundTag tag, String tagName)
    {
        if (tag.contains(tagName, Constants.NBT.TAG_LIST))
        {
            ListTag tagList = tag.getList(tagName, Constants.NBT.TAG_INT);

            if (tagList.size() == 3)
            {
                return new BlockPos(tagList.getInt(0), tagList.getInt(1), tagList.getInt(2));
            }
        }

        return null;
    }

    protected boolean readPaletteFromLitematicaFormatTag(ListTag tagList, ILitematicaBlockStatePalette palette)
    {
        final int size = tagList.size();
        List<BlockState> list = new ArrayList<>(size);

        for (int id = 0; id < size; ++id)
        {
            CompoundTag tag = tagList.getCompound(id);
            BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(tag);
            list.add(state);
        }

        return palette.setMapping(list);
    }

    public static boolean isValidSpongeSchematic(CompoundTag tag)
    {
        if (tag.contains("Width", Constants.NBT.TAG_ANY_NUMERIC) &&
            tag.contains("Height", Constants.NBT.TAG_ANY_NUMERIC) &&
            tag.contains("Length", Constants.NBT.TAG_ANY_NUMERIC) &&
            tag.contains("Version", Constants.NBT.TAG_INT) &&
            tag.contains("Palette", Constants.NBT.TAG_COMPOUND) &&
            tag.contains("BlockData", Constants.NBT.TAG_BYTE_ARRAY))
        {
            return isSizeValid(readSizeFromTagSponge(tag));
        }

        return false;
    }

    public static Vec3i readSizeFromTagSponge(CompoundTag tag)
    {
        return new Vec3i(tag.getInt("Width"), tag.getInt("Height"), tag.getInt("Length"));
    }

    protected boolean readSpongePaletteFromTag(CompoundTag tag, ILitematicaBlockStatePalette palette)
    {
        final int size = tag.getAllKeys().size();
        List<BlockState> list = new ArrayList<>(size);
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int i = 0; i < size; ++i)
        {
            list.add(air);
        }

        for (String key : tag.getAllKeys())
        {
            int id = tag.getInt(key);
            Optional<BlockState> stateOptional = BlockUtils.getBlockStateFromString(key);
            BlockState state;

            if (stateOptional.isPresent())
            {
                state = stateOptional.get();
            }
            else
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "Unknown block in the Sponge schematic palette: '" + key + "'");
                state = LitematicaBlockStateContainer.AIR_BLOCK_STATE;
            }

            if (id < 0 || id >= size)
            {
                String msg = "Invalid ID in the Sponge schematic palette: '" + id + "'";
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, msg);
                Litematica.logger.error(msg);
                return false;
            }

            list.set(id, state);
        }

        return palette.setMapping(list);
    }

    protected boolean readSpongeBlocksFromTag(CompoundTag tag, String schematicName, Vec3i size)
    {
        if (tag.contains("Palette", Constants.NBT.TAG_COMPOUND) &&
            tag.contains("BlockData", Constants.NBT.TAG_BYTE_ARRAY))
        {
            CompoundTag paletteTag = tag.getCompound("Palette");
            byte[] blockData = tag.getByteArray("BlockData");
            int paletteSize = paletteTag.getAllKeys().size();
            LitematicaBlockStateContainer container = LitematicaBlockStateContainer.createContainer(paletteSize, blockData, size);

            if (container == null)
            {
                String msg = "Failed to read blocks from Sponge schematic";
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, msg);
                Litematica.logger.error(msg);
                return false;
            }

            this.blockContainers.put(schematicName, container);

            return this.readSpongePaletteFromTag(paletteTag, container.getPalette());
        }

        return false;
    }

    protected Map<BlockPos, CompoundTag> readSpongeBlockEntitiesFromTag(CompoundTag tag)
    {
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();

        int version = tag.getInt("Version");
        String tagName = version == 1 ? "TileEntities" : "BlockEntities";
        ListTag tagList = tag.getList(tagName, Constants.NBT.TAG_COMPOUND);

        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag beTag = tagList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPosFromArrayTag(beTag, "Pos");

            if (pos != null && beTag.isEmpty() == false)
            {
                beTag.putString("id", beTag.getString("Id"));

                // Remove the Sponge tags from the data that is kept in memory
                beTag.remove("Id");
                beTag.remove("Pos");

                if (version == 1)
                {
                    beTag.remove("ContentVersion");
                }

                blockEntities.put(pos, beTag);
            }
        }

        return blockEntities;
    }

    protected List<EntityInfo> readSpongeEntitiesFromTag(CompoundTag tag)
    {
        List<EntityInfo> entities = new ArrayList<>();
        ListTag tagList = tag.getList("Entities", Constants.NBT.TAG_COMPOUND);
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag entityData = tagList.getCompound(i);
            Vec3 pos = NbtUtils.readVec3dFromListTag(entityData);

            if (pos != null && entityData.isEmpty() == false)
            {
                entityData.putString("id", entityData.getString("Id"));

                // Remove the Sponge tags from the data that is kept in memory
                entityData.remove("Id");

                entities.add(new EntityInfo(pos, entityData));
            }
        }

        return entities;
    }

    public boolean readFromSpongeSchematic(String name, CompoundTag tag)
    {
        if (isValidSpongeSchematic(tag) == false)
        {
            return false;
        }

        Vec3i size = readSizeFromTagSponge(tag);

        if (this.readSpongeBlocksFromTag(tag, name, size) == false)
        {
            return false;
        }

        this.tileEntities.put(name, this.readSpongeBlockEntitiesFromTag(tag));
        this.entities.put(name, this.readSpongeEntitiesFromTag(tag));

        if (tag.contains("author", Constants.NBT.TAG_STRING))
        {
            this.getMetadata().setAuthor(tag.getString("author"));
        }

        this.subRegionPositions.put(name, BlockPos.ZERO);
        this.subRegionSizes.put(name, new BlockPos(size));
        this.metadata.setName(name);
        this.metadata.setRegionCount(1);
        this.metadata.setTotalVolume(size.getX() * size.getY() * size.getZ());
        this.metadata.setEnclosingSize(size);
        this.metadata.setTimeCreated(System.currentTimeMillis());
        this.metadata.setTimeModified(this.metadata.getTimeCreated());
        this.metadata.setTotalBlocks(this.totalBlocksReadFromWorld);

        return true;
    }

    public boolean readFromVanillaStructure(String name, CompoundTag tag)
    {
        Vec3i size = readSizeFromTagImpl(tag);

        if (tag.contains("palette", Constants.NBT.TAG_LIST) &&
            tag.contains("blocks", Constants.NBT.TAG_LIST) &&
            isSizeValid(size))
        {
            ListTag paletteTag = tag.getList("palette", Constants.NBT.TAG_COMPOUND);

            Map<BlockPos, CompoundTag> tileMap = new HashMap<>();
            this.tileEntities.put(name, tileMap);

            BlockState air = Blocks.AIR.defaultBlockState();
            int paletteSize = paletteTag.size();
            List<BlockState> list = new ArrayList<>(paletteSize);

            for (int id = 0; id < paletteSize; ++id)
            {
                CompoundTag t = paletteTag.getCompound(id);
                BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(t);
                list.add(state);
            }

            BlockState zeroState = list.get(0);
            int airId = -1;

            // If air is not ID 0, then we need to re-map the palette such that air is ID 0,
            // due to how it's currently handled in the Litematica container.
            for (int i = 0; i < paletteSize; ++i)
            {
                if (list.get(i) == air)
                {
                    airId = i;
                    break;
                }
            }

            if (airId != 0)
            {
                // No air in the palette, insert it
                if (airId == -1)
                {
                    list.add(0, air);
                    ++paletteSize;
                }
                // Air as some other ID, swap the entries
                else
                {
                    list.set(0, air);
                    list.set(airId, zeroState);
                }
            }

            int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
            LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(size.getX(), size.getY(), size.getZ(), bits, null);
            ILitematicaBlockStatePalette palette = container.getPalette();
            palette.setMapping(list);
            this.blockContainers.put(name, container);

            if (tag.contains("author", Constants.NBT.TAG_STRING))
            {
                this.getMetadata().setAuthor(tag.getString("author"));
            }

            this.subRegionPositions.put(name, BlockPos.ZERO);
            this.subRegionSizes.put(name, new BlockPos(size));
            this.metadata.setName(name);
            this.metadata.setRegionCount(1);
            this.metadata.setTotalVolume(size.getX() * size.getY() * size.getZ());
            this.metadata.setEnclosingSize(size);
            this.metadata.setTimeCreated(System.currentTimeMillis());
            this.metadata.setTimeModified(this.metadata.getTimeCreated());

            ListTag blockList = tag.getList("blocks", Constants.NBT.TAG_COMPOUND);
            final int count = blockList.size();
            int totalBlocks = 0;

            for (int i = 0; i < count; ++i)
            {
                CompoundTag blockTag = blockList.getCompound(i);
                BlockPos pos = readBlockPosFromNbtList(blockTag, "pos");

                if (pos == null)
                {
                    InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "Failed to read block position for vanilla structure");
                    return false;
                }

                int id = blockTag.getInt("state");
                BlockState state;

                // Air was inserted as ID 0, so the other IDs need to shift
                if (airId == -1)
                {
                    state = palette.getBlockState(id + 1);
                }
                else if (airId != 0)
                {
                    // re-mapping air and ID 0 state
                    if (id == 0)
                    {
                        state = zeroState;
                    }
                    else if (id == airId)
                    {
                        state = air;
                    }
                    else
                    {
                        state = palette.getBlockState(id);
                    }
                }
                else
                {
                    state = palette.getBlockState(id);
                }

                if (state == null)
                {
                    state = air;
                }
                else if (state != air)
                {
                    ++totalBlocks;
                }

                container.set(pos.getX(), pos.getY(), pos.getZ(), state);

                if (blockTag.contains("nbt", Constants.NBT.TAG_COMPOUND))
                {
                    tileMap.put(pos, blockTag.getCompound("nbt"));
                }
            }

            this.metadata.setTotalBlocks(totalBlocks);
            this.entities.put(name, this.readEntitiesFromVanillaStructure(tag));

            return true;
        }

        return false;
    }

    protected List<EntityInfo> readEntitiesFromVanillaStructure(CompoundTag tag)
    {
        List<EntityInfo> entities = new ArrayList<>();
        ListTag tagList = tag.getList("entities", Constants.NBT.TAG_COMPOUND);
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag entityData = tagList.getCompound(i);
            Vec3 pos = readVec3dFromNbtList(entityData, "pos");

            if (pos != null && entityData.contains("nbt", Constants.NBT.TAG_COMPOUND))
            {
                entities.add(new EntityInfo(pos, entityData.getCompound("nbt")));
            }
        }

        return entities;
    }

    @Nullable
    public static Vec3 readVec3dFromNbtList(@Nullable CompoundTag tag, String tagName)
    {
        if (tag != null && tag.contains(tagName, Constants.NBT.TAG_LIST))
        {
            ListTag tagList = tag.getList(tagName, Constants.NBT.TAG_DOUBLE);

            if (tagList.getElementType() == Constants.NBT.TAG_DOUBLE && tagList.size() == 3)
            {
                return new Vec3(tagList.getDouble(0), tagList.getDouble(1), tagList.getDouble(2));
            }
        }

        return null;
    }

    private void postProcessContainerIfNeeded(ListTag palette, LitematicaBlockStateContainer container, @Nullable Map<BlockPos, CompoundTag> tiles)
    {
        List<BlockState> states = getStatesFromPaletteTag(palette);

        if (this.converter.createPostProcessStateFilter(states))
        {
            IdentityHashMap<BlockState, SchematicConversionFixers.IStateFixer> postProcessingFilter = this.converter.getPostProcessStateFilter();
            SchematicConverter.postProcessBlocks(container, tiles, postProcessingFilter);
        }
    }

    public static List<BlockState> getStatesFromPaletteTag(ListTag palette)
    {
        List<BlockState> states = new ArrayList<>();
        final int size = palette.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag tag = palette.getCompound(i);
            BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(tag);

            if (i > 0 || state != LitematicaBlockStateContainer.AIR_BLOCK_STATE)
            {
                states.add(state);
            }
        }

        return states;
    }

    private ListTag convertBlockStatePalette_1_12_to_1_13_2(ListTag oldPalette, int version, int minecraftDataVersion)
    {
        // The Minecraft data version didn't yet exist in the first 1.13.2 builds, so only
        // consider it if it actually exists in the file, ie. is larger than the default value of 0.
        if (version < SCHEMATIC_VERSION_1_13_2 || (minecraftDataVersion < MINECRAFT_DATA_VERSION_1_13_2 && minecraftDataVersion > 0))
        {
            ListTag newPalette = new ListTag();
            final int count = oldPalette.size();

            for (int i = 0; i < count; ++i)
            {
                newPalette.add(SchematicConversionMaps.get_1_13_2_StateTagFor_1_12_Tag(oldPalette.getCompound(i)));
            }

            return newPalette;
        }

        return oldPalette;
    }

    private List<EntityInfo> readEntitiesFromNBT(ListTag tagList)
    {
        List<EntityInfo> entityList = new ArrayList<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag entityData = tagList.getCompound(i);
            Vec3 posVec = NBTUtils.readEntityPositionFromTag(entityData);

            if (posVec != null && entityData.isEmpty() == false)
            {
                entityList.add(new EntityInfo(posVec, entityData));
            }
        }

        return entityList;
    }

    private Map<BlockPos, CompoundTag> readTileEntitiesFromNBT(ListTag tagList)
    {
        Map<BlockPos, CompoundTag> tileMap = new HashMap<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag tag = tagList.getCompound(i);
            BlockPos pos = NBTUtils.readBlockPos(tag);

            if (pos != null && tag.isEmpty() == false)
            {
                tileMap.put(pos, tag);
            }
        }

        return tileMap;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<BlockPos, TickNextTickData<T>> readPendingTicksFromNBT(ListTag tagList, T clazz)
    {
        Map<BlockPos, TickNextTickData<T>> tickMap = new HashMap<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag tag = tagList.getCompound(i);

            if (tag.contains("Time", Constants.NBT.TAG_ANY_NUMERIC)) // XXX these were accidentally saved as longs in version 3
            {
                T target = null;

                // Don't crash on invalid ResourceLocation in 1.13+
                try
                {
                    if (clazz instanceof Block && tag.contains("Block", Constants.NBT.TAG_STRING))
                    {
                        target = (T) Registry.BLOCK.get(new ResourceLocation(tag.getString("Block")));

                        if (target == null || target == Blocks.AIR)
                        {
                            continue;
                        }
                    }
                    else if (clazz instanceof Fluid && tag.contains("Fluid", Constants.NBT.TAG_STRING))
                    {
                        target = (T) Registry.FLUID.get(new ResourceLocation(tag.getString("Fluid")));

                        if (target == null || target == Fluids.EMPTY)
                        {
                            continue;
                        }
                    }
                }
                catch (Exception e)
                {
                }

                if (target != null)
                {
                    BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                    // Note: the time is a relative delay at this point
                    int scheduledTime = tag.getInt("Time");
                    TickPriority priority = TickPriority.byValue(tag.getInt("Priority"));

                    TickNextTickData<T> entry = new TickNextTickData<>(pos, target, scheduledTime, priority);

                    tickMap.put(pos, entry);
                }
            }
        }

        return tickMap;
    }

    private List<EntityInfo> readEntitiesFromNBT_v1(ListTag tagList)
    {
        List<EntityInfo> entityList = new ArrayList<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag tag = tagList.getCompound(i);
            Vec3 posVec = NBTUtils.readVec3d(tag);
            CompoundTag entityData = tag.getCompound("EntityData");

            if (posVec != null && entityData.isEmpty() == false)
            {
                // Update the correct position to the TileEntity NBT, where it is stored in version 2
                NBTUtils.writeEntityPositionToTag(posVec, entityData);
                entityList.add(new EntityInfo(posVec, entityData));
            }
        }

        return entityList;
    }

    private Map<BlockPos, CompoundTag> readTileEntitiesFromNBT_v1(ListTag tagList)
    {
        Map<BlockPos, CompoundTag> tileMap = new HashMap<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            CompoundTag tag = tagList.getCompound(i);
            CompoundTag tileNbt = tag.getCompound("TileNBT");

            // Note: This within-schematic relative position is not inside the tile tag!
            BlockPos pos = NBTUtils.readBlockPos(tag);

            if (pos != null && tileNbt.isEmpty() == false)
            {
                // Update the correct position to the entity NBT, where it is stored in version 2
                NBTUtils.writeBlockPosToTag(pos, tileNbt);
                tileMap.put(pos, tileNbt);
            }
        }

        return tileMap;
    }

    public boolean writeToFile(File dir, String fileNameIn, boolean override)
    {
        String fileName = fileNameIn;

        if (fileName.endsWith(FILE_EXTENSION) == false)
        {
            fileName = fileName + FILE_EXTENSION;
        }

        File fileSchematic = new File(dir, fileName);

        try
        {
            if (dir.exists() == false && dir.mkdirs() == false)
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.directory_creation_failed", dir.getAbsolutePath());
                return false;
            }

            if (override == false && fileSchematic.exists())
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.exists", fileSchematic.getAbsolutePath());
                return false;
            }

            FileOutputStream os = new FileOutputStream(fileSchematic);
            NbtIo.writeCompressed(this.writeToNBT(), os);
            os.close();

            return true;
        }
        catch (Exception e)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.exception", fileSchematic.getAbsolutePath());
            Litematica.logger.error(StringUtils.translate("litematica.error.schematic_write_to_file_failed.exception", fileSchematic.getAbsolutePath()), e);
            Litematica.logger.error(e.getMessage());
        }

        return false;
    }

    public boolean readFromFile()
    {
        return this.readFromFile(this.schematicType);
    }

    private boolean readFromFile(FileType schematicType)
    {
        try
        {
            CompoundTag nbt = readNbtFromFile(this.schematicFile);

            if (nbt != null)
            {
                if (schematicType == FileType.SPONGE_SCHEMATIC)
                {
                    String name = FileUtils.getNameWithoutExtension(this.schematicFile.getName()) + " (Converted Structure)";
                    return this.readFromSpongeSchematic(name, nbt);
                }
                if (schematicType == FileType.VANILLA_STRUCTURE)
                {
                    String name = FileUtils.getNameWithoutExtension(this.schematicFile.getName()) + " (Converted Structure)";
                    return this.readFromVanillaStructure(name, nbt);
                }
                else if (schematicType == FileType.LITEMATICA_SCHEMATIC)
                {
                    return this.readFromNBT(nbt);
                }
            }
        }
        catch (Exception e)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_read_from_file_failed.exception", this.schematicFile.getAbsolutePath());
            Litematica.logger.error(e);
        }

        return false;
    }

    public static CompoundTag readNbtFromFile(File file)
    {
        if (file == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_read_from_file_failed.no_file");
            return null;
        }

        if (file.exists() == false || file.canRead() == false)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_read_from_file_failed.cant_read", file.getAbsolutePath());
            return null;
        }

        return NbtUtils.readNbtFromFile(file);
    }

    public static File fileFromDirAndName(File dir, String fileName, FileType schematicType)
    {
        if (fileName.endsWith(FILE_EXTENSION) == false && schematicType == FileType.LITEMATICA_SCHEMATIC)
        {
            fileName = fileName + FILE_EXTENSION;
        }

        return new File(dir, fileName);
    }

    @Nullable
    public static SchematicMetadata readMetadataFromFile(File dir, String fileName)
    {
        CompoundTag nbt = readNbtFromFile(fileFromDirAndName(dir, fileName, FileType.LITEMATICA_SCHEMATIC));

        if (nbt != null)
        {
            SchematicMetadata metadata = new SchematicMetadata();

            if (nbt.contains("Version", Constants.NBT.TAG_INT))
            {
                final int version = nbt.getInt("Version");

                if (version >= 1 && version <= SCHEMATIC_VERSION)
                {
                    metadata.readFromNBT(nbt.getCompound("Metadata"));
                    return metadata;
                }
            }
        }

        return null;
    }

    @Nullable
    public static LitematicaSchematic createFromFile(File dir, String fileName)
    {
        return createFromFile(dir, fileName, FileType.LITEMATICA_SCHEMATIC);
    }

    @Nullable
    public static LitematicaSchematic createFromFile(File dir, String fileName, FileType schematicType)
    {
        File file = fileFromDirAndName(dir, fileName, schematicType);
        LitematicaSchematic schematic = new LitematicaSchematic(file, schematicType);

        return schematic.readFromFile(schematicType) ? schematic : null;
    }

    public static class EntityInfo
    {
        public final Vec3 posVec;
        public final CompoundTag nbt;

        public EntityInfo(Vec3 posVec, CompoundTag nbt)
        {
            this.posVec = posVec;
            this.nbt = nbt;
        }
    }

    public static class SchematicSaveInfo
    {
        public final boolean visibleOnly;
        public final boolean ignoreEntities;
        public final boolean fromSchematicWorld;

        public SchematicSaveInfo(boolean visibleOnly, boolean ignoreEntities)
        {
            this (visibleOnly, ignoreEntities, false);
        }

        public SchematicSaveInfo(boolean visibleOnly, boolean ignoreEntities, boolean fromSchematicWorld)
        {
            this.visibleOnly = visibleOnly;
            this.ignoreEntities = ignoreEntities;
            this.fromSchematicWorld = fromSchematicWorld;
        }
    }
}
