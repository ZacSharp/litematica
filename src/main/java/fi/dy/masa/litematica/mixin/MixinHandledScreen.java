package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import fi.dy.masa.litematica.materials.MaterialListHudRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreen extends Screen
{
    private MixinHandledScreen(Component title)
    {
        super(title);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lcom/mojang/blaze3d/vertex/PoseStack;FII)V"))
    private void renderSlotHighlights(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        MaterialListHudRenderer.renderLookedAtBlockInInventory((AbstractContainerScreen<?>) (Object) this, this.minecraft);
    }
}
