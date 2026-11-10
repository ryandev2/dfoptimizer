package dev.ryanland.dfoptimizer.mixin;

import dev.ryanland.dfoptimizer.PlotFilter;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Don't render entities on other plots (armor stands, displays, players); see PlotFilter. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void plotfilter$skipHidden(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick,
			CallbackInfoReturnable<Boolean> cir) {
		if (PlotFilter.hides(entity)) cir.setReturnValue(false);
	}
}
