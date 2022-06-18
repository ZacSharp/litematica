package fi.dy.masa.litematica.render.schematic;

import javax.annotation.Nullable;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.level.ColorResolver;
import fi.dy.masa.litematica.world.FakeLightingProvider;

public class ChunkCacheSchematic implements IBlockDisplayReader
{
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    protected final ClientWorld worldClient;
    protected final FakeLightingProvider lightingProvider;
    protected int chunkStartX;
    protected int chunkStartZ;
    protected Chunk[][] chunkArray;
    protected boolean empty;

    public ChunkCacheSchematic(World worldIn, ClientWorld clientWorld, BlockPos pos, int expand)
    {
        this.lightingProvider = new FakeLightingProvider();

        this.worldClient = clientWorld;
        this.chunkStartX = (pos.getX() - expand) >> 4;
        this.chunkStartZ = (pos.getZ() - expand) >> 4;
        int chunkEndX = (pos.getX() + expand + 15) >> 4;
        int chunkEndZ = (pos.getZ() + expand + 15) >> 4;
        this.chunkArray = new Chunk[chunkEndX - this.chunkStartX + 1][chunkEndZ - this.chunkStartZ + 1];
        this.empty = true;

        for (int cx = this.chunkStartX; cx <= chunkEndX; ++cx)
        {
            for (int cz = this.chunkStartZ; cz <= chunkEndZ; ++cz)
            {
                this.chunkArray[cx - this.chunkStartX][cz - this.chunkStartZ] = worldIn.getChunk(cx, cz);
            }
        }

        for (int cx = pos.getX() >> 4; cx <= (pos.getX() + 15) >> 4; ++cx)
        {
            for (int cz = pos.getZ() >> 4; cz <= (pos.getZ() + 15) >> 4; ++cz)
            {
                Chunk chunk = this.chunkArray[cx - this.chunkStartX][cz - this.chunkStartZ];

                if (chunk != null && chunk.isEmptyBetween(pos.getY(), pos.getY() + 15) == false)
                {
                    this.empty = false;
                    break;
                }
            }
        }
    }

    public boolean isEmpty()
    {
        return this.empty;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        if (pos.getY() >= 0 && pos.getY() < 256)
        {
            int cx = (pos.getX() >> 4) - this.chunkStartX;
            int cz = (pos.getZ() >> 4) - this.chunkStartZ;

            if (cx >= 0 && cx < this.chunkArray.length &&
                cz >= 0 && cz < this.chunkArray[cx].length)
            {
                Chunk chunk = this.chunkArray[cx][cz];

                if (chunk != null)
                {
                    return chunk.getBlockState(pos);
                }
            }
        }

        return AIR;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos)
    {
        return this.getTileEntity(pos, Chunk.CreateEntityType.CHECK);
    }

    @Nullable
    public TileEntity getTileEntity(BlockPos pos, Chunk.CreateEntityType type)
    {
        int i = (pos.getX() >> 4) - this.chunkStartX;
        int j = (pos.getZ() >> 4) - this.chunkStartZ;

        return this.chunkArray[i][j].getTileEntity(pos, type);
    }

    @Override
    public int getLightFor(LightType var1, BlockPos var2)
    {
        return 15;
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        // TODO change when fluids become separate
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public WorldLightManager getLightManager()
    {
        return this.lightingProvider;
    }

    @Override
    public int getBlockColor(BlockPos pos, ColorResolver colorResolver)
    {
        return colorResolver.getColor(this.worldClient.getBiome(pos), (double)pos.getX(), (double)pos.getZ());
    }

    @Override
    public float func_230487_a_(Direction direction, boolean bl) // getBrightness
    {
        return this.worldClient.func_230487_a_(direction, bl); // AO brightness on face
    }
}
