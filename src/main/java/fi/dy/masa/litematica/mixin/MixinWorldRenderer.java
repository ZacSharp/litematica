package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import net.minecraft.client.renderer.RenderType;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class MixinWorldRenderer
{
    @Shadow
    private net.minecraft.client.multiplayer.ClientLevel level;

    @Inject(method = "allChanged()V", at = @At("RETURN"))
    private void onLoadRenderers(CallbackInfo ci)
    {
        // Also (re-)load our renderer when the vanilla renderer gets reloaded
        if (this.level != null && this.level == net.minecraft.client.Minecraft.getInstance().level)
        {
            LitematicaRenderer.getInstance().loadRenderers();
        }
    }

    @Inject(method = "setupRender", at = @At("TAIL"))
    private void onPostSetupTerrain(
            net.minecraft.client.Camera camera,
            net.minecraft.client.renderer.culling.Frustum frustum,
            boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci)
    {
        LitematicaRenderer.getInstance().piecewisePrepareAndUpdate(frustum);
    }

    @Inject(method = "renderChunkLayer", at = @At("TAIL"))
    private void onRenderLayer(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, Matrix4f matrix4f, CallbackInfo ci)
    {
        if (renderLayer == RenderType.solid())
        {
            LitematicaRenderer.getInstance().piecewiseRenderSolid(matrixStack, matrix4f);
        }
        else if (renderLayer == RenderType.cutoutMipped())
        {
            LitematicaRenderer.getInstance().piecewiseRenderCutoutMipped(matrixStack, matrix4f);
        }
        else if (renderLayer == RenderType.cutout())
        {
            LitematicaRenderer.getInstance().piecewiseRenderCutout(matrixStack, matrix4f);
        }
        else if (renderLayer == RenderType.translucent())
        {
            LitematicaRenderer.getInstance().piecewiseRenderTranslucent(matrixStack, matrix4f);
            LitematicaRenderer.getInstance().piecewiseRenderOverlay(matrixStack, matrix4f);
        }
    }

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE_STRING", args = "ldc=blockentities",
                     target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"))
    private void onPostRenderEntities(
            com.mojang.blaze3d.vertex.PoseStack matrices,
            float tickDelta, long limitTime, boolean renderBlockOutline,
            net.minecraft.client.Camera camera,
            net.minecraft.client.renderer.GameRenderer gameRenderer,
            net.minecraft.client.renderer.LightTexture lightmapTextureManager,
            com.mojang.math.Matrix4f matrix4f,
            CallbackInfo ci)
    {
        LitematicaRenderer.getInstance().piecewiseRenderEntities(matrices, tickDelta);
    }

    /*
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRenderWorldLast(
            net.minecraft.client.util.math.MatrixStack matrices,
            float tickDelta, long limitTime, boolean renderBlockOutline,
            net.minecraft.client.render.Camera camera,
            net.minecraft.client.render.GameRenderer gameRenderer,
            net.minecraft.client.render.LightmapTextureManager lightmapTextureManager,
            net.minecraft.client.util.math.Matrix4f matrix4f,
            CallbackInfo ci)
    {
        boolean invert = Hotkeys.INVERT_GHOST_BLOCK_RENDER_STATE.getKeybind().isKeybindHeld();

        if (Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue() != invert &&
            Configs.Generic.BETTER_RENDER_ORDER.getBooleanValue() == false)
        {
            LitematicaRenderer.getInstance().renderSchematicWorld(matrices, matrix4f, tickDelta);
        }
    }
    */
}
