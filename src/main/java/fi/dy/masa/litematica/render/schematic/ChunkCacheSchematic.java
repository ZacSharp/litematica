package fi.dy.masa.litematica.render.schematic;

import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import fi.dy.masa.litematica.world.FakeLightingProvider;

public class ChunkCacheSchematic implements BlockAndTintGetter, LightChunkGetter
{
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    protected final Level world;
    protected final ClientLevel worldClient;
    protected final FakeLightingProvider lightingProvider;
    protected int chunkStartX;
    protected int chunkStartZ;
    protected LevelChunk[][] chunkArray;
    protected boolean empty;

    public ChunkCacheSchematic(Level worldIn, ClientLevel clientWorld, BlockPos pos, int expand)
    {
        this.world = worldIn;
        this.lightingProvider = new FakeLightingProvider(this);

        this.worldClient = clientWorld;
        this.chunkStartX = (pos.getX() - expand) >> 4;
        this.chunkStartZ = (pos.getZ() - expand) >> 4;
        int chunkEndX = (pos.getX() + expand + 15) >> 4;
        int chunkEndZ = (pos.getZ() + expand + 15) >> 4;
        this.chunkArray = new LevelChunk[chunkEndX - this.chunkStartX + 1][chunkEndZ - this.chunkStartZ + 1];
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
                LevelChunk chunk = this.chunkArray[cx - this.chunkStartX][cz - this.chunkStartZ];

                if (chunk != null && chunk.isYSpaceEmpty(pos.getY(), pos.getY() + 15) == false)
                {
                    this.empty = false;
                    break;
                }
            }
        }
    }

    @Override
    public BlockGetter getLevel()
    {
        return this.world;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public BlockGetter getChunkForLighting(int chunkX, int chunkZ)
    {
        return null; // TODO 1.17 this shouldn't be needed since the lighting provider does nothing
    }

    public boolean isEmpty()
    {
        return this.empty;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        int cx = (pos.getX() >> 4) - this.chunkStartX;
        int cz = (pos.getZ() >> 4) - this.chunkStartZ;

        if (cx >= 0 && cx < this.chunkArray.length &&
            cz >= 0 && cz < this.chunkArray[cx].length)
        {
            ChunkAccess chunk = this.chunkArray[cx][cz];

            if (chunk != null)
            {
                return chunk.getBlockState(pos);
            }
        }

        return AIR;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType type)
    {
        int i = (pos.getX() >> 4) - this.chunkStartX;
        int j = (pos.getZ() >> 4) - this.chunkStartZ;

        return this.chunkArray[i][j].getBlockEntity(pos, type);
    }

    @Override
    public int getBrightness(LightLayer var1, BlockPos var2)
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
    public LevelLightEngine getLightEngine()
    {
        return this.lightingProvider;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver)
    {
        return colorResolver.getColor(this.worldClient.getBiome(pos), pos.getX(), pos.getZ());
    }

    @Override
    public float getShade(Direction direction, boolean bl)
    {
        return this.worldClient.getShade(direction, bl); // AO brightness on face
    }

    @Override
    public int getHeight()
    {
        return this.world.getHeight();
    }

    @Override
    public int getMinBuildHeight()
    {
        return this.world.getMinBuildHeight();
    }
}
