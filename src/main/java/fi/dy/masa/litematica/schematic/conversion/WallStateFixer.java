package fi.dy.masa.litematica.schematic.conversion;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallHeight;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;

public class WallStateFixer implements SchematicConversionFixers.IStateFixer
{
    public static final WallStateFixer INSTANCE = new WallStateFixer();

    private static final VoxelShape SHAPE_PILLAR = Block.makeCuboidShape(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);
    private static final VoxelShape SHAPE_NORTH = Block.makeCuboidShape(7.0D, 0.0D, 0.0D, 9.0D, 16.0D, 9.0D);
    private static final VoxelShape SHAPE_SOUTH = Block.makeCuboidShape(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_WEST = Block.makeCuboidShape(0.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);
    private static final VoxelShape SHAPE_EAST = Block.makeCuboidShape(7.0D, 0.0D, 7.0D, 16.0D, 16.0D, 9.0D);

    @Override
    public BlockState fixState(IBlockReaderWithData reader, BlockState state, BlockPos pos)
    {
        IBlockReader world = reader;
        FluidState fluidState = state.getFluidState(); // FIXME
        BlockPos posNorth = pos.north();
        BlockPos posEast = pos.east();
        BlockPos posSouth = pos.south();
        BlockPos posWest = pos.west();
        BlockPos posUp = pos.up();
        BlockState stateNorth = world.getBlockState(posNorth);
        BlockState stateEast = world.getBlockState(posEast);
        BlockState stateSouth = world.getBlockState(posSouth);
        BlockState stateWest = world.getBlockState(posWest);
        BlockState stateUp = world.getBlockState(posUp);

        boolean connectNorth = this.shouldConnectTo(stateNorth, stateNorth.isSolidSide(world, posNorth, Direction.SOUTH), Direction.SOUTH);
        boolean connectEast  = this.shouldConnectTo(stateEast,  stateEast .isSolidSide(world, posEast, Direction.WEST), Direction.WEST);
        boolean connectSouth = this.shouldConnectTo(stateSouth, stateSouth.isSolidSide(world, posSouth, Direction.NORTH), Direction.NORTH);
        boolean connectWest  = this.shouldConnectTo(stateWest,  stateWest .isSolidSide(world, posWest, Direction.EAST), Direction.EAST);
        BlockState baseState = state.getBlock().getDefaultState().with(WallBlock.WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        return this.getWallStateWithConnections(world, baseState, posUp, stateUp, connectNorth, connectEast, connectSouth, connectWest);
    }

    private BlockState getWallStateWithConnections(IBlockReader worldView,
                                                   BlockState baseState,
                                                   BlockPos pos,
                                                   BlockState stateUp,
                                                   boolean canConnectNorth,
                                                   boolean canConnectEast,
                                                   boolean canConnectSouth,
                                                   boolean canConnectWest)
    {
        VoxelShape shapeAbove = stateUp.getCollisionShapeUncached(worldView, pos).project(Direction.DOWN);
        BlockState stateWithSides = this.getWallSideConnections(baseState, canConnectNorth, canConnectEast, canConnectSouth, canConnectWest, shapeAbove);

        return stateWithSides.with(WallBlock.UP, this.shouldConnectUp(stateWithSides, stateUp, shapeAbove));
    }

    private BlockState getWallSideConnections(BlockState blockState,
                                                     boolean canConnectNorth,
                                                     boolean canConnectEast,
                                                     boolean canConnectSouth,
                                                     boolean canConnectWest,
                                                     VoxelShape shapeAbove)
    {
        return blockState
                       .with(WallBlock.WALL_HEIGHT_NORTH, this.getConnectionShape(canConnectNorth, shapeAbove, SHAPE_NORTH))
                       .with(WallBlock.WALL_HEIGHT_EAST,  this.getConnectionShape(canConnectEast, shapeAbove, SHAPE_EAST))
                       .with(WallBlock.WALL_HEIGHT_SOUTH, this.getConnectionShape(canConnectSouth, shapeAbove, SHAPE_SOUTH))
                       .with(WallBlock.WALL_HEIGHT_WEST,  this.getConnectionShape(canConnectWest, shapeAbove, SHAPE_WEST));
    }

    private boolean shouldConnectTo(BlockState state, boolean faceFullSquare, Direction side)
    {
        Block block = state.getBlock();

        return state.isIn(BlockTags.WALLS) ||
               Block.cannotAttach(block) == false && faceFullSquare ||
               block instanceof PaneBlock ||
               block instanceof FenceGateBlock && FenceGateBlock.isParallel(state, side);
    }

    private boolean shouldConnectUp(BlockState blockState, BlockState stateUp, VoxelShape shapeAbove)
    {
        boolean isUpConnectedWallAbove = stateUp.getBlock() instanceof WallBlock && stateUp.get(WallBlock.UP);

        if (isUpConnectedWallAbove)
        {
            return true;
        }
        else
        {
            WallHeight shapeNorth = blockState.get(WallBlock.WALL_HEIGHT_NORTH);
            WallHeight shapeSouth = blockState.get(WallBlock.WALL_HEIGHT_SOUTH);
            WallHeight shapeEast  = blockState.get(WallBlock.WALL_HEIGHT_EAST);
            WallHeight shapeWest  = blockState.get(WallBlock.WALL_HEIGHT_WEST);
            boolean unconnectedNorth = shapeNorth == WallHeight.NONE;
            boolean unconnectedSouth = shapeSouth == WallHeight.NONE;
            boolean unconnectedEast  = shapeEast == WallHeight.NONE;
            boolean unconnectedWest  = shapeWest == WallHeight.NONE;
            boolean isPillarOrWallEnd = unconnectedNorth && unconnectedSouth && unconnectedWest && unconnectedEast ||
                                        unconnectedNorth != unconnectedSouth || unconnectedWest != unconnectedEast;

            if (isPillarOrWallEnd)
            {
                return true;
            }
            else
            {
                boolean inTallLine = shapeNorth == WallHeight.TALL && shapeSouth == WallHeight.TALL ||
                                     shapeEast == WallHeight.TALL && shapeWest == WallHeight.TALL;

                if (inTallLine)
                {
                    return false;
                }
                else
                {
                    return stateUp.getBlock().isIn(BlockTags.WALL_POST_OVERRIDE) || this.shapesDoNotIntersect(shapeAbove, SHAPE_PILLAR);
                }
            }
        }
    }

    private WallHeight getConnectionShape(boolean canConnect, VoxelShape shapeAbove, VoxelShape shapeSideClearance)
    {
        if (canConnect)
        {
            return this.shapesDoNotIntersect(shapeAbove, shapeSideClearance) ? WallHeight.TALL : WallHeight.LOW;
        }
        else
        {
            return WallHeight.NONE;
        }
    }

    private boolean shapesDoNotIntersect(VoxelShape voxelShape, VoxelShape voxelShape2)
    {
        return VoxelShapes.compare(voxelShape2, voxelShape, IBooleanFunction.ONLY_FIRST) == false;
    }
}
