package fi.dy.masa.litematica.world;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class FakeLightingProvider extends LevelLightEngine
{
    private final FakeLightingView lightingView;

    public FakeLightingProvider(LightChunkGetter chunkProvider)
    {
        super(chunkProvider, false, false);

        this.lightingView = new FakeLightingView();
    }

    @Override
    public LayerLightEventListener getLayerListener(LightLayer type)
    {
        return this.lightingView;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int defaultValue)
    {
        return 15;
    }

    public static class FakeLightingView implements LayerLightEventListener
    {
        @Nullable
        @Override
        public DataLayer getDataLayerData(SectionPos pos)
        {
            return null;
        }

        @Override
        public int getLightValue(BlockPos pos)
        {
            return 15;
        }

        @Override
        public void checkBlock(BlockPos pos)
        {
        }

        @Override
        public void onBlockEmissionIncrease(BlockPos pos, int i)
        {
        }

        @Override
        public boolean hasLightWork()
        {
            return false;
        }

        @Override
        public int runUpdates(int i, boolean bl, boolean bl2)
        {
            return 0;
        }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean notReady)
        {
            // NO-OP
        }

        @Override
        public void enableLightSources(ChunkPos chunkPos, boolean bl)
        {
        }
    }
}
