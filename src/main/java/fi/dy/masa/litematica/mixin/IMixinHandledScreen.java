package fi.dy.masa.litematica.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface IMixinHandledScreen
{
    @Accessor("leftPos")
    int litematica_getX();

    @Accessor("topPos")
    int litematica_getY();
}
