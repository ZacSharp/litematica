package fi.dy.masa.litematica.mixin;

import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;

@Mixin(ClientLevel.class)
public abstract class MixinClientWorld extends Level
{
    protected MixinClientWorld(WritableLevelData properties, ResourceKey<Level> registryKey, DimensionType dimensionType, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l)
    {
        super(properties, registryKey, dimensionType, supplier, bl, bl2, l);
    }

    @Inject(method = "setKnownState", at = @At("HEAD"))
    private void onSetBlockStateWithoutNeighborUpdates(BlockPos pos, BlockState state, CallbackInfo ci)
    {
        SchematicVerifier.markVerifierBlockChanges(pos);

        if (Configs.Visuals.ENABLE_RENDERING.getBooleanValue() &&
            Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue())
        {
            SchematicWorldRefresher.INSTANCE.markSchematicChunkForRenderUpdate(pos);
        }
    }
}
