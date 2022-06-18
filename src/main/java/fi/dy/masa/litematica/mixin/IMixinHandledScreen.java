package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;

@Mixin(ContainerScreen.class)
public interface IMixinHandledScreen
{
    @Accessor("guiLeft")
    int litematica_getX();

    @Accessor("guiTop")
    int litematica_getY();
}
