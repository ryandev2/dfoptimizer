package dev.ryanland.dfoptimizer.mixin;

import dev.ryanland.dfoptimizer.DFOptimizerClient;
import dev.ryanland.dfoptimizer.PlotFilter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block entities outside the plot do not tick client-side (chests: lid, signs: editor check),
 * and neither do code chests and code signs inside it (nothing visible to tick there). With
 * ~15,000 chests and signs this was a sizeable part of the tick time in the profile. Client only:
 * PlotFilter.hides checks level.isClientSide(), and BoundTickingBlockEntity on the client only
 * exists in the client level.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BoundTickingBlockEntityMixin {
	@Shadow @Final private BlockEntity blockEntity;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void plotfilter$skipHidden(CallbackInfo ci) {
		if (PlotFilter.hides(blockEntity) || DFOptimizerClient.skipTick(blockEntity)) ci.cancel();
	}
}
