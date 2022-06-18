package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

@Mixin(RedstoneWireBlock.class)
public interface IMixinRedstoneWireBlock
{
    @Invoker("getUpdatedState") // getPlacementState
    BlockState litematicaGetFullState(IBlockReader world, BlockState state, BlockPos pos);
}
