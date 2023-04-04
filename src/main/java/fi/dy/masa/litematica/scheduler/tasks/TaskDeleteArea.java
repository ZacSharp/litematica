package fi.dy.masa.litematica.scheduler.tasks;

import java.util.List;
import net.minecraft.world.level.block.Blocks;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;

public class TaskDeleteArea extends TaskFillArea
{
    public TaskDeleteArea(List<Box> boxes, boolean removeEntities)
    {
        super(boxes, Blocks.AIR.defaultBlockState(), null, removeEntities, "litematica.gui.label.task_name.delete");
    }

    @Override
    protected void printCompletionMessage()
    {
        if (this.finished)
        {
            if (this.printCompletionMessage)
            {
                InfoUtils.showGuiMessage(MessageType.SUCCESS, "litematica.message.area_cleared");
            }
        }
        else
        {
            InfoUtils.showGuiMessage(MessageType.ERROR, "litematica.message.error.area_deletion_aborted");
        }
    }
}
