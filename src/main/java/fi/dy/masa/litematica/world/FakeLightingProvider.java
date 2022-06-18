package fi.dy.masa.litematica.world;

import javax.annotation.Nullable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraft.world.lighting.WorldLightManager;

public class FakeLightingProvider extends WorldLightManager
{
    private final FakeLightingView lightingView;

    public FakeLightingProvider()
    {
        super(null, false, false);

        this.lightingView = new FakeLightingView();
    }

    @Override
    public IWorldLightListener getLightEngine(LightType type)
    {
        return this.lightingView;
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int defaultValue)
    {
        return 15;
    }

    public static class FakeLightingView implements IWorldLightListener
    {
        @Nullable
        @Override
        public NibbleArray getData(SectionPos pos)
        {
            return null;
        }

        @Override
        public int getLightFor(BlockPos pos)
        {
            return 15;
        }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean notReady)
        {
            // NO-OP
        }
    }
}
