package fi.dy.masa.litematica.util;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.malilib.util.Constants;

public class NbtUtils
{
    @Nullable
    public static BlockPos readBlockPosFromArrayTag(CompoundTag tag, String tagName)
    {
        if (tag.contains(tagName, Constants.NBT.TAG_INT_ARRAY))
        {
            int[] pos = tag.getIntArray("Pos");

            if (pos.length == 3)
            {
                return new BlockPos(pos[0], pos[1], pos[2]);
            }
        }

        return null;
    }

    @Nullable
    public static Vec3 readVec3dFromListTag(@Nullable CompoundTag tag)
    {
        return readVec3dFromListTag(tag, "Pos");
    }

    @Nullable
    public static Vec3 readVec3dFromListTag(@Nullable CompoundTag tag, String tagName)
    {
        if (tag != null && tag.contains(tagName, Constants.NBT.TAG_LIST))
        {
            ListTag tagList = tag.getList(tagName, Constants.NBT.TAG_DOUBLE);

            if (tagList.getElementType() == Constants.NBT.TAG_DOUBLE && tagList.size() == 3)
            {
                return new Vec3(tagList.getDouble(0), tagList.getDouble(1), tagList.getDouble(2));
            }
        }

        return null;
    }

    @Nullable
    public static CompoundTag readNbtFromFile(File file)
    {
        if (file.exists() == false || file.canRead() == false)
        {
            return null;
        }

        FileInputStream is;

        try
        {
            is = new FileInputStream(file);
        }
        catch (Exception e)
        {
            Litematica.logger.warn("Failed to read NBT data from file '{}' (failed to create the input stream)", file.getAbsolutePath());
            return null;
        }

        CompoundTag nbt = null;

        if (is != null)
        {
            try
            {
                nbt = NbtIo.readCompressed(is);
            }
            catch (Exception e)
            {
                try
                {
                    is.close();
                    is = new FileInputStream(file);
                    nbt = NbtIo.read(new DataInputStream(is));
                }
                catch (Exception ignore) {}
            }

            try
            {
                is.close();
            }
            catch (Exception ignore) {}
        }

        if (nbt == null)
        {
            Litematica.logger.warn("Failed to read NBT data from file '{}'", file.getAbsolutePath());
        }

        return nbt;
    }
}
