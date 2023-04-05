package fi.dy.masa.litematica.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.data.worldgen.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagContainer;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.EmptyTickList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Scoreboard;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.Reference;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

public class WorldSchematic extends Level
{
    private static final ResourceKey<Level> REGISTRY_KEY = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(Reference.MOD_ID, "schematic_world"));

    private final Minecraft mc;
    private final WorldRendererSchematic worldRenderer;
    private final ChunkManagerSchematic chunkManagerSchematic;
    private int nextEntityId;
    private int entityCount;

    protected WorldSchematic(WritableLevelData mutableWorldProperties, DimensionType dimensionType, Supplier<ProfilerFiller> supplier)
    {
        super(mutableWorldProperties, REGISTRY_KEY, dimensionType, supplier, true, true, 0L);

        this.mc = Minecraft.getInstance();
        this.worldRenderer = LitematicaRenderer.getInstance().getWorldRenderer();
        this.chunkManagerSchematic = new ChunkManagerSchematic(this);
    }

    public ChunkManagerSchematic getChunkProvider()
    {
        return this.chunkManagerSchematic;
    }

    @Override
    public ChunkManagerSchematic getChunkSource()
    {
        return this.chunkManagerSchematic;
    }

    @Override
    public TickList<Block> getBlockTicks()
    {
        return EmptyTickList.empty();
    }

    @Override
    public TickList<Fluid> getLiquidTicks()
    {
        return EmptyTickList.empty();
    }

    public int getRegularEntityCount()
    {
        return this.entityCount;
    }

    @Override
    public LevelChunk getChunkAt(BlockPos pos)
    {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public ChunkSchematic getChunk(int chunkX, int chunkZ)
    {
        return this.chunkManagerSchematic.getChunkForLighting(chunkX, chunkZ);
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean required)
    {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public Biome getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ)
    {
        return Biomes.PLAINS;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState newState, int flags)
    {
        if (pos.getY() < this.getMinBuildHeight() || pos.getY() >= this.getMaxBuildHeight())
        {
            return false;
        }
        else
        {
            return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4).setBlockState(pos, newState, false) != null;
        }
    }

    @Override
    public boolean addFreshEntity(Entity entity)
    {
        int chunkX = Mth.floor(entity.getX() / 16.0D);
        int chunkZ = Mth.floor(entity.getZ() / 16.0D);

        if (this.chunkManagerSchematic.hasChunk(chunkX, chunkZ) == false)
        {
            return false;
        }
        else
        {
            entity.setId(this.nextEntityId++);
            this.chunkManagerSchematic.getChunkForLighting(chunkX, chunkZ).addEntity(entity);
            ++this.entityCount;

            return true;
        }
    }

    public void unloadedEntities(int count)
    {
        this.entityCount -= count;
    }

    @Nullable
    @Override
    public Entity getEntity(int id)
    {
        // This shouldn't be used for anything in the mod, so just return null here
        return null;
    }

    @Override
    public List<? extends Player> players()
    {
        return ImmutableList.of();
    }

    @Override
    public long getGameTime()
    {
        return this.mc.level != null ? this.mc.level.getGameTime() : 0;
    }

    @Override
    @Nullable
    public MapItemSavedData getMapData(String id)
    {
        return null;
    }

    @Override
    public void setMapData(String name, MapItemSavedData mapState)
    {
        // NO-OP
    }

    @Override
    public int getFreeMapId()
    {
        return 0;
    }

    @Override
    public Scoreboard getScoreboard()
    {
        return this.mc.level != null ? this.mc.level.getScoreboard() : null;
    }

    @Override
    public RecipeManager getRecipeManager()
    {
        return this.mc.level != null ? this.mc.level.getRecipeManager() : null;
    }

    @Override
    public TagContainer getTagManager()
    {
        return this.mc.level != null ? this.mc.level.getTagManager() : null;
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities()
    {
        // This is not used in the mod
        return null;
    }

    @Override
    public List<Entity> getEntities(@Nullable final Entity except, final AABB box, Predicate<? super Entity> predicate)
    {
        final int minY = Mth.floor(box.minY / 16.0);
        final int maxY = Mth.floor(box.maxY / 16.0);
        final List<Entity> entities = new ArrayList<>();
        List<ChunkSchematic> chunks = this.getChunksWithinBox(box);

        for (ChunkSchematic chunk : chunks)
        {
            for (int cy = minY; cy <= maxY; ++cy)
            {
                chunk.getEntityListForSectionIfExists(cy).forEach((e) -> {
                    if (e != except && box.intersects(e.getBoundingBox()) && predicate.test(e)) {
                        entities.add(e);
                    }
                });
            }
        }

        return entities;
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> arg, AABB box, Predicate<? super T> predicate)
    {
        // This is not used in the mod, so just return an empty list...
        return Collections.emptyList();
    }

    public List<ChunkSchematic> getChunksWithinBox(AABB box)
    {
        final int minX = Mth.floor(box.minX / 16.0);
        final int minZ = Mth.floor(box.minZ / 16.0);
        final int maxX = Mth.floor(box.maxX / 16.0);
        final int maxZ = Mth.floor(box.maxZ / 16.0);

        List<ChunkSchematic> chunks = new ArrayList<>();

        for (int cx = minX; cx <= maxX; ++cx)
        {
            for (int cz = minZ; cz <= maxZ; ++cz)
            {
                ChunkSchematic chunk = this.chunkManagerSchematic.getChunkIfExists(cx, cz);

                if (chunk != null)
                {
                    chunks.add(chunk);
                }
            }
        }

        return chunks;
    }

    @Override
    public void setBlocksDirty(BlockPos pos, BlockState stateOld, BlockState stateNew)
    {
        this.scheduleBlockRenders(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public void scheduleBlockRenders(int chunkX, int chunkY, int chunkZ)
    {
        this.worldRenderer.scheduleChunkRenders(chunkX, chunkY, chunkZ);
    }

    public void scheduleChunkRenders(int chunkX, int chunkZ)
    {
        int startChunkY = this.getMinSection();
        int endChunkY = this.getMaxSection() - 1;

        for (int chunkY = startChunkY; chunkY <= endChunkY; ++chunkY)
        {
            this.worldRenderer.scheduleChunkRenders(chunkX, chunkY, chunkZ);
        }
    }

    public void scheduleChunkRenders(int minBlockX, int minBlockY, int minBlockZ, int maxBlockX, int maxBlockY, int maxBlockZ)
    {
        minBlockY = Math.max(minBlockY, this.getMinBuildHeight());
        maxBlockY = Math.min(maxBlockY, this.getMaxBuildHeight() - 1);

        final int minChunkX = Math.min(minBlockX, maxBlockX) >> 4;
        final int minChunkY = Math.min(minBlockY, maxBlockY) >> 4;
        final int minChunkZ = Math.min(minBlockZ, maxBlockZ) >> 4;
        final int maxChunkX = Math.max(minBlockX, maxBlockX) >> 4;
        final int maxChunkY = Math.max(minBlockY, maxBlockY) >> 4;
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
    public int getMinBuildHeight()
    {
        return this.mc.level != null ? this.mc.level.getMinBuildHeight() : -64;
    }

    @Override
    public int getHeight()
    {
        return this.mc.level != null ? this.mc.level.getHeight() : 384;
    }

    // The following HeightLimitView overrides are to work around an incompatibility with Lithium 0.7.4+

    @Override
    public int getMaxBuildHeight()
    {
        return this.getMinBuildHeight() + this.getHeight();
    }

    @Override
    public int getMinSection()
    {
        return this.getMinBuildHeight() >> 4;
    }

    @Override
    public int getMaxSection()
    {
        return this.getMaxBuildHeight() >> 4;
    }

    @Override
    public int getSectionsCount()
    {
        return this.getMaxSection() - this.getMinSection();
    }

    @Override
    public boolean isOutsideBuildHeight(BlockPos pos)
    {
        return this.isOutsideBuildHeight(pos.getY());
    }

    @Override
    public boolean isOutsideBuildHeight(int y)
    {
        return (y < this.getMinBuildHeight()) || (y >= this.getMaxBuildHeight());
    }

    @Override
    public int getSectionIndex(int y)
    {
        return (y >> 4) - (this.getMinBuildHeight() >> 4);
    }

    @Override
    public int getSectionIndexFromSectionY(int coord)
    {
        return coord - (this.getMinBuildHeight() >> 4);
    }

    @Override
    public int getSectionYFromSectionIndex(int index)
    {
        return index + (this.getMinBuildHeight() >> 4);
    }

    @Override
    public float getShade(Direction direction, boolean shaded)
    {
        return 0;
    }

    @Override
    public int getBrightness(LightLayer type, BlockPos pos)
    {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int defaultValue)
    {
        return 15;
    }

    @Override
    public void sendBlockUpdated(BlockPos blockPos_1, BlockState blockState_1, BlockState blockState_2, int flags)
    {
        // NO-OP
    }

    @Override
    public void destroyBlockProgress(int entityId, BlockPos pos, int progress)
    {
        // NO-OP
    }

    @Override
    public void globalLevelEvent(int eventId, BlockPos pos, int data)
    {
        // NO-OP
    }
    
    @Override
    public void levelEvent(@Nullable Player entity, int id, BlockPos pos, int data)
    {
    }

    @Override
    public void gameEvent(@Nullable Entity entity, GameEvent event, BlockPos pos)
    {
        // NO-OP
    }

    @Override
    public void addParticle(ParticleOptions particleParameters_1, double double_1, double double_2, double double_3, double double_4, double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void addParticle(ParticleOptions particleParameters_1, boolean boolean_1, double double_1, double double_2, double double_3, double double_4, double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions particleParameters_1, double double_1, double double_2, double double_3, double double_4,   double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions particleParameters_1, boolean boolean_1, double double_1, double double_2, double double_3,     double double_4, double double_5, double double_6)
    {
        // NO-OP
    }

    @Override
    public void playLocalSound(double x, double y, double z, SoundEvent soundIn, SoundSource category, float volume, float pitch, boolean distanceDelay)
    {
        // NO-OP
    }

    @Override
    public void playSound(Player player, BlockPos pos, SoundEvent soundIn, SoundSource category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public void playSound(Player player, double x, double y, double z, SoundEvent soundIn, SoundSource category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public void playSound(@Nullable Player player, Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public RegistryAccess registryAccess()
    {
        return this.mc.level.registryAccess();
    }

    @Override
    public String gatherChunkSourceStats()
    {
        return "Chunks[SCH] W: " + this.getChunkSource().gatherStats() + " E: " + this.getRegularEntityCount();
    }
}
