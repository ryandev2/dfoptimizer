package dev.ryanland.dfoptimizer.sodiummixin;

import dev.ryanland.dfoptimizer.PlotFilter;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.collections.WriteQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sections outside the plot never enter Sodium's visibility search. visitNode queues a neighbouring
 * section; whatever is not queued is not drawn (terrain and block entities), not traversed and not
 * rebuilt. Built against Sodium 0.9.3-beta.1+mc26.3; require = 0 and the config is optional, so a
 * different Sodium version doesn't crash. PlotFilter logs it when the hook is not active.
 */
@Mixin(value = OcclusionCuller.class, remap = false)
public class OcclusionCullerMixin {
	@Inject(method = "visitNode", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$skipHidden(WriteQueue<RenderSection> queue, RenderSection section, int incoming,
			boolean a, boolean b, boolean c, CallbackInfo ci) {
		PlotFilter.sodiumHookSeen = true;
		if (PlotFilter.hidesSection(section.getChunkX(), section.getChunkZ())) ci.cancel();
	}

	/** Sections right around the camera take a separate path (addNearbySections). */
	@Inject(method = "isWithinNearbySectionFrustum", at = @At("HEAD"), cancellable = true, require = 0)
	private static void plotfilter$skipHiddenNearby(Viewport viewport, RenderSection section, CallbackInfoReturnable<Boolean> cir) {
		if (PlotFilter.hidesSection(section.getChunkX(), section.getChunkZ())) cir.setReturnValue(false);
	}
}
