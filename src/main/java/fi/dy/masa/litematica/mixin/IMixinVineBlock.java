package fi.dy.masa.litematica.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VineBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VineBlock.class)
public interface IMixinVineBlock
{
    @Invoker("canSupportAtFace")
    boolean invokeShouldConnectUp(BlockGetter blockReader, BlockPos pos, Direction side);
}
