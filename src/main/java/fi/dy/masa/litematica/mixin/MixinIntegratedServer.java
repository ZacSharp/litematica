package fi.dy.masa.litematica.mixin;

import java.net.Proxy;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import fi.dy.masa.litematica.scheduler.TaskScheduler;

@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServer extends MinecraftServer
{
    public MixinIntegratedServer(Thread thread, RegistryAccess.RegistryHolder impl, LevelStorageSource.LevelStorageAccess session, WorldData saveProperties, PackRepository resourcePackManager, Proxy proxy, DataFixer dataFixer, ServerResources serverResourceManager, MinecraftSessionService minecraftSessionService, GameProfileRepository gameProfileRepository, GameProfileCache userCache, ChunkProgressListenerFactory worldGenerationProgressListenerFactory)
    {
        super(thread, impl, session, saveProperties, resourcePackManager, proxy, dataFixer, serverResourceManager, minecraftSessionService, gameProfileRepository, userCache, worldGenerationProgressListenerFactory);
    }

    @Inject(method = "tickServer", at = @At(value = "INVOKE", shift = Shift.AFTER,
            target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void onPostTick(BooleanSupplier supplier, CallbackInfo ci)
    {
        TaskScheduler.getInstanceServer().runTasks();
    }
}
