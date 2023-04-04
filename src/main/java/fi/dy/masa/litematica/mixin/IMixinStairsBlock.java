package fi.dy.masa.litematica.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StairBlock.class)
public interface IMixinStairsBlock
{
    @Invoker("getStairShape")
    public static StairsShape invokeGetStairShape(BlockState state, BlockGetter worldIn, BlockPos pos) { return null; }
}
