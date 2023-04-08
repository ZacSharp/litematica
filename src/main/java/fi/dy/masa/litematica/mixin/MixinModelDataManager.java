package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.model.ModelDataManager;
import fi.dy.masa.litematica.world.WorldSchematic;

@Mixin(ModelDataManager.class)
public abstract class MixinModelDataManager
{
    @Inject(method = "requestModelDataRefresh", at = @At("HEAD"), cancellable=true, remap=false)
    private static void requestModelDataRefresh(BlockEntity te, CallbackInfo cir)
    {
        // if we don't catch this Forge does stupid things
        // it calls requestModelData on any client world when adding a te
        // but if it's not mc.world it crashes because model data may only
        // be used on the current client world
        if (te.getLevel() instanceof WorldSchematic)
        {
            cir.cancel();
        }
    }
}
