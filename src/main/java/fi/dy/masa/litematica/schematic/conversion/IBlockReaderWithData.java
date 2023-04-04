package fi.dy.masa.litematica.schematic.conversion;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;

public interface IBlockReaderWithData extends BlockGetter
{
    @Nullable
    CompoundTag getBlockEntityData(BlockPos pos);

    // TODO 1.17: These shouldn't matter here... right?
    @Override
    default int getHeight()
    {
        return 384;
    }

    @Override
    default int getMinBuildHeight()
    {
        return -64;
    }
}
