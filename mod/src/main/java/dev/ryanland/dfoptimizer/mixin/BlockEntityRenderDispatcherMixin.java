package dev.ryanland.dfoptimizer.mixin;

import dev.ryanland.dfoptimizer.DFOptimizerClient;
import dev.ryanland.dfoptimizer.PlotFilter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	/** Set every frame in prepare(): the camera position this dispatcher renders from. */
	@Shadow private Vec3 cameraPos;

	/**
	 * No renderer = tryExtractRenderState returns null and the block entity costs nothing per frame.
	 * Used for code chests (they are in the chunk mesh) and for everything outside the plot
	 * (PlotFilter, block-precise; the cheap check first).
	 */
	@Inject(method = "getRenderer(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;",
			at = @At("HEAD"), cancellable = true)
	private void chests$skip(BlockEntity be, CallbackInfoReturnable<BlockEntityRenderer<?, ?>> cir) {
		if (PlotFilter.hides(be) || DFOptimizerClient.skip(be)) cir.setReturnValue(null);
	}

	/**
	 * Code sign text beyond the configured distance: no render state this frame. This must not live in
	 * getRenderer: chunk meshing (vanilla and Sodium) also asks getRenderer whether a block entity goes
	 * into the section's render list, so a sign that was far away while its chunk was built would never
	 * show its text again, not even up close.
	 */
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void chests$signDistance(BlockEntity be, float partialTick,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag,
			CallbackInfoReturnable<BlockEntityRenderState> cir) {
		if (DFOptimizerClient.hideSignText(be, cameraPos)) cir.setReturnValue(null);
	}

	@Inject(method = "onResourceManagerReload", at = @At("HEAD"))
	private void chests$reload(ResourceManager rm, CallbackInfo ci) {
		DFOptimizerClient.refresh(rm);
	}
}
