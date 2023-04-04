package fi.dy.masa.litematica.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface IMixinHandledScreen
{
    @Accessor("x")
    int litematica_getX();

    @Accessor("y")
    int litematica_getY();
}
