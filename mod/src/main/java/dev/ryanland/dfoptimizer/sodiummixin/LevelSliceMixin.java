package dev.ryanland.dfoptimizer.sodiummixin;

import dev.ryanland.dfoptimizer.PlotFilter;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block-precise: the world copy Sodium meshes a section from returns air outside the plot.
 * OcclusionCullerMixin only skips whole sections (16x16); a section half on the plot used to stay
 * fully visible, including a strip of the neighbouring plot. Runs on the mesh threads.
 */
@Mixin(value = LevelSlice.class, remap = false)
public class LevelSliceMixin {
	@Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$air(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
		if (PlotFilter.hidesBlock(x, z)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
	}

	@Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$airPos(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) cir.setReturnValue(Blocks.AIR.defaultBlockState());
	}

	@Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$noFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
	}

	/**
	 * Light: a face on the plot edge now looks at "air", but the real light at that spot is that of
	 * a solid block (0), so it turned black. Outside the plot: full sky light, no block light.
	 */
	@Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$daylight(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) cir.setReturnValue(layer == LightLayer.SKY ? 15 : 0);
	}

	@Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$daylightRaw(BlockPos pos, int darkening, CallbackInfoReturnable<Integer> cir) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) cir.setReturnValue(Math.max(0, 15 - darkening));
	}
}
