package dev.ryanland.dfoptimizer.rendermixin;

import dev.ryanland.dfoptimizer.PlotFilter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
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
 * Without Sodium: the same as LevelSliceMixin, but for the vanilla world copy that SectionCompiler
 * meshes a section from. Air outside the plot, plus daylight so faces on the plot edge don't turn
 * black. With Sodium this class is not used (Sodium does its own meshing).
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin implements BlockAndTintGetter {
	@Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$air(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) cir.setReturnValue(Blocks.AIR.defaultBlockState());
	}

	@Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true, require = 0)
	private void plotfilter$noFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
	}

	/** Same as BlockAndLightGetter's default method, except outside the plot. */
	@Override
	public int getBrightness(LightLayer layer, BlockPos pos) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) return layer == LightLayer.SKY ? 15 : 0;
		return getLightEngine().getLayerListener(layer).getLightValue(pos);
	}

	@Override
	public int getRawBrightness(BlockPos pos, int darkening) {
		if (PlotFilter.hidesBlock(pos.getX(), pos.getZ())) return Math.max(0, 15 - darkening);
		return getLightEngine().getRawBrightness(pos, darkening);
	}
}
