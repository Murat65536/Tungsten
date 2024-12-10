package kaptainwutax.tungsten.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Renderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    @Inject(method = "render", at = @At("RETURN"))
    public void render(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder;

        RenderSystem.lineWidth(2.0F);

        builder = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        Cuboid goal = new Cuboid(TungstenMod.TARGET.subtract(0.5D, 0D, 0.5D), new Vec3d(1.0D, 2.0D, 1.0D), Color.GREEN);
        goal.render(builder);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        if (TungstenMod.RUNNING_PATH_RENDERER.size() > 0)
        	TungstenMod.RUNNING_PATH_RENDERER.forEach(r -> render(r, tessellator));
        
        if (TungstenMod.BLOCK_PATH_RENDERER.size() > 0)
        	TungstenMod.BLOCK_PATH_RENDERER.forEach(r -> render(r, tessellator));
        
        if (TungstenMod.RENDERERS.size() > 0)
        	TungstenMod.RENDERERS.forEach(r -> render(r, tessellator));
        
        if (TungstenMod.TEST.size() > 0)
        	TungstenMod.TEST.forEach(r -> render(r, tessellator));
        RenderSystem.enableBlend();
    }
    
    private static void render(Renderer r, Tessellator tessellator) {
    	try {
        	BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            r.render(builder);
            BufferRenderer.drawWithGlobalProgram(builder.end());
		} catch (Exception e) {
			// Ignored
		}
	}

}
