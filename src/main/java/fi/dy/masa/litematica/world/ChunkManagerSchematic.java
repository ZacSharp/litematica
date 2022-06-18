package fi.dy.masa.litematica.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.lighting.WorldLightManager;

public class ChunkManagerSchematic extends AbstractChunkProvider
{
    private final WorldSchematic world;
    private final Long2ObjectMap<ChunkSchematic> loadedChunks = new Long2ObjectOpenHashMap<>(8192);
    private final ChunkSchematic blankChunk;
    private final WorldLightManager lightingProvider;

    public ChunkManagerSchematic(WorldSchematic world)
    {
        this.world = world;
        this.blankChunk = new ChunkSchematic(world, new ChunkPos(0, 0));
        this.lightingProvider = new WorldLightManager(this, true, world.getDimensionType().hasSkyLight());
    }

    @Override
    public WorldSchematic getWorld()
    {
        return this.world;
    }

    public void loadChunk(int chunkX, int chunkZ)
    {
        ChunkSchematic chunk = new ChunkSchematic(this.world, new ChunkPos(chunkX, chunkZ));
        this.loadedChunks.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ)
    {
        return this.loadedChunks.containsKey(ChunkPos.asLong(chunkX, chunkZ));
    }

    public String makeString()
    {
        return "Schematic Chunk Cache: " + this.getLoadedChunkCount();
    }

    public int getLoadedChunkCount()
    {
        return this.loadedChunks.size();
    }

    public Long2ObjectMap<ChunkSchematic> getLoadedChunks()
    {
        return this.loadedChunks;
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean fallbackToEmpty)
    {
        ChunkSchematic chunk = this.getChunkForLight(chunkX, chunkZ);
        return chunk == null && fallbackToEmpty ? this.blankChunk : chunk;
    }

    @Override
    public ChunkSchematic getChunkForLight(int chunkX, int chunkZ)
    {
        ChunkSchematic chunk = this.loadedChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        return chunk == null ? this.blankChunk : chunk;
    }

    public void unloadChunk(int chunkX, int chunkZ)
    {
        ChunkSchematic chunk = this.loadedChunks.remove(ChunkPos.asLong(chunkX, chunkZ));

        if (chunk != null)
        {
            this.world.unloadBlockEntities(chunk.getTileEntityMap().values());

            for (ClassInheritanceMultiMap<Entity> list : chunk.getEntityLists())
            {
                for (Entity entity : list.getByClass(Entity.class))
                {
                    this.world.removeEntity(entity.getEntityId());
                }
            }
        }
    }

    @Override
    public WorldLightManager getLightManager()
    {
        return this.lightingProvider;
    }
}
