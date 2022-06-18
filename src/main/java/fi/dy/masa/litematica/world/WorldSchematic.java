package fi.dy.masa.litematica.world;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.particles.IParticleData;
import net.minecraft.profiler.IProfiler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.tags.ITagCollectionSupplier;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EmptyTickList;
import net.minecraft.world.ITickList;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.storage.ISpawnWorldInfo;
import net.minecraft.world.storage.MapData;
import fi.dy.masa.litematica.Reference;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class WorldSchematic extends World
{
    private static final RegistryKey<World> REGISTRY_KEY = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, new ResourceLocation(Reference.MOD_ID, "schematic_world"));

    private final Minecraft mc;
    private final WorldRendererSchematic worldRenderer;
    private final ChunkManagerSchematic chunkManagerSchematic;
    private final Int2ObjectOpenHashMap<Entity> regularEntities = new Int2ObjectOpenHashMap<>();
    private int nextEntityId;

    protected WorldSchematic(ISpawnWorldInfo mutableWorldProperties, DimensionType dimensionType, Supplier<IProfiler> profiler)
    {
        super(mutableWorldProperties, REGISTRY_KEY, dimensionType, profiler, true, true, 0L);

        this.mc = Minecraft.getInstance();
        this.worldRenderer = LitematicaRenderer.getInstance().getWorldRenderer();
        this.chunkManagerSchematic = new ChunkManagerSchematic(this);
    }

    @Override
    public ChunkManagerSchematic getChunkProvider()
    {
        return this.chunkManagerSchematic;
    }

    @Override
    public ITickList<Block> getPendingBlockTicks()
    {
        return EmptyTickList.get();
    }

    @Override
    public ITickList<Fluid> getPendingFluidTicks()
    {
        return EmptyTickList.get();
    }

    public int getRegularEntityCount()
    {
        return this.regularEntities.size();
    }

    @Override
    public Chunk getChunkAt(BlockPos pos)
    {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ)
    {
        return this.chunkManagerSchematic.getChunkForLight(chunkX, chunkZ);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean required)
    {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public Biome getNoiseBiomeRaw(int biomeX, int biomeY, int biomeZ)
    {
        return BiomeRegistry.PLAINS;
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState newState, int flags)
    {
        if (pos.getY() < 0 || pos.getY() >= 256)
        {
            return false;
        }
        else
        {
            return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4).setBlockState(pos, newState, false) != null;
        }
    }

    public boolean addEntity(Entity entityIn)
    {
        return this.spawnEntityBase(entityIn);
    }

    private boolean spawnEntityBase(Entity entity)
    {
        int cx = MathHelper.floor(entity.getPosX() / 16.0D);
        int cz = MathHelper.floor(entity.getPosZ() / 16.0D);

        if (this.chunkManagerSchematic.chunkExists(cx, cz) == false)
        {
            return false;
        }
        else
        {
            entity.setEntityId(this.nextEntityId++);

            int id = entity.getEntityId();
            this.removeEntity(id);

            this.regularEntities.put(id, entity);
            this.chunkManagerSchematic.getChunkForLight(MathHelper.floor(entity.getPosX() / 16.0D), MathHelper.floor(entity.getPosZ() / 16.0D)).addEntity(entity);

            return true;
        }
    }

    public void removeEntity(int id)
    {
        Entity entity = this.regularEntities.remove(id);

        if (entity != null)
        {
            entity.remove();
            entity.detach();

            if (entity.addedToChunk)
            {
                this.getChunk(entity.chunkCoordX, entity.chunkCoordZ).removeEntity(entity);
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntityByID(int id)
    {
        return this.regularEntities.get(id);
    }

    @Override
    public List<? extends PlayerEntity> getPlayers()
    {
        return ImmutableList.of();
    }

    public void unloadBlockEntities(Collection<TileEntity> blockEntities)
    {
        Set<TileEntity> remove = Collections.newSetFromMap(new IdentityHashMap<>());
        remove.addAll(blockEntities);
        this.tickableTileEntities.removeAll(remove);
        this.loadedTileEntityList.removeAll(remove);
    }

    @Override
    public long getGameTime()
    {
        return this.mc.world != null ? this.mc.world.getGameTime() : 0;
    }

    @Override
    @Nullable
    public MapData getMapData(String id)
    {
        return null;
    }

    @Override
    public void registerMapData(MapData mapState)
    {
        // NO-OP
    }

    @Override
    public int getNextMapId()
    {
        return 0;
    }

    @Override
    public Scoreboard getScoreboard()
    {
        return this.mc.world != null ? this.mc.world.getScoreboard() : null;
    }

    @Override
    public RecipeManager getRecipeManager()
    {
        return this.mc.world != null ? this.mc.world.getRecipeManager() : null;
    }

    @Override
    public ITagCollectionSupplier getTags()
    {
        return this.mc.world != null ? this.mc.world.getTags() : null;
    }

    @Override
    public void markBlockRangeForRenderUpdate(BlockPos pos, BlockState stateOld, BlockState stateNew)
    {
        this.scheduleBlockRenders(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public void scheduleBlockRenders(int chunkX, int chunkY, int chunkZ)
    {
        if (chunkY >= 0 && chunkY < 16)
        {
            this.worldRenderer.scheduleChunkRenders(chunkX, chunkY, chunkZ);
        }
    }

    public void scheduleChunkRenders(int chunkX, int chunkZ)
    {
        for (int chunkY = 0; chunkY < 16; ++chunkY)
        {
            this.worldRenderer.scheduleChunkRenders(chunkX, chunkY, chunkZ);
        }
    }

    public void scheduleChunkRenders(int minBlockX, int minBlockY, int minBlockZ, int maxBlockX, int maxBlockY, int maxBlockZ)
    {
        final int minChunkX = Math.min(minBlockX, maxBlockX) >> 4;
        final int minChunkY = MathHelper.clamp(Math.min(minBlockY, maxBlockY) >> 4, 0, 15);
        final int minChunkZ = Math.min(minBlockZ, maxBlockZ) >> 4;
        final int maxChunkX = Math.max(minBlockX, maxBlockX) >> 4;
        final int maxChunkY = MathHelper.clamp(Math.max(minBlockY, maxBlockY) >> 4, 0, 15);
        final int maxChunkZ = Math.max(minBlockZ, maxBlockZ) >> 4;

        for (int cz = minChunkZ; cz <= maxChunkZ; ++cz)
        {
            for (int cx = minChunkX; cx <= maxChunkX; ++cx)
            {
                for (int cy = minChunkY; cy <= maxChunkY; ++cy)
                {
                    this.worldRenderer.scheduleChunkRenders(cx, cy, cz);
                }
            }
        }
    }

    @Override
    public float func_230487_a_(Direction direction, boolean shaded)
    {
        return 0;
    }

    @Override
    public int getLightFor(LightType type, BlockPos pos)
    {
        return 15;
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int defaultValue)
    {
        return 15;
    }

    @Override
    public void notifyBlockUpdate(BlockPos blockPos_1, BlockState blockState_1, BlockState blockState_2, int flags)
    {
        // NO-OP
    }

    @Override
    public void sendBlockBreakProgress(int entityId, BlockPos pos, int progress)
    {
        // NO-OP
    }

    @Override
    public void playBroadcastSound(int eventId, BlockPos pos, int data)
    {
        // NO-OP
    }
    
    @Override
    public void playEvent(@Nullable PlayerEntity playerEntity, int id, BlockPos pos, int data)
    {
    }

    @Override
    public void addParticle(IParticleData particleParameters_1, double double_1, double double_2, double double_3, double double_4, double     double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void addParticle(IParticleData particleParameters_1, boolean boolean_1, double double_1, double double_2, double double_3, double   double_4, double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void addOptionalParticle(IParticleData particleParameters_1, double double_1, double double_2, double double_3, double double_4,   double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void addOptionalParticle(IParticleData particleParameters_1, boolean boolean_1, double double_1, double double_2, double double_3,     double double_4, double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void playSound(double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume, float pitch, boolean distanceDelay)
    {
        // NO-OP
    }

    @Override
    public void playSound(PlayerEntity player, BlockPos pos, SoundEvent soundIn, SoundCategory category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public void playSound(PlayerEntity player, double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public void playMovingSound(@Nullable PlayerEntity player, Entity entity, SoundEvent sound, SoundCategory category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public DynamicRegistries func_241828_r() // getRegistryManager
    {
        return this.mc.world.func_241828_r(); // getRegistryManager
    }
}
