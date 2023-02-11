package fi.dy.masa.litematica.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RedStoneWireBlock.class)
public interface IMixinRedstoneWireBlock
{
    @Invoker("getConnectionState")
    BlockState litematicaGetPlacementState(BlockGetter world, BlockState state, BlockPos pos);
}
