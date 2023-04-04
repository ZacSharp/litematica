package fi.dy.masa.litematica.interfaces;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface IMixinChunkProviderClient
{
    Long2ObjectMap<ChunkAccess> getLoadedChunks();
}
