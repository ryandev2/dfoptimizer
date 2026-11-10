package dev.ryanland.dfoptimizer.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanland.dfoptimizer.DFOptimizerClient;
import dev.ryanland.dfoptimizer.Codespace;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keep code chests out of a chunk section's block entity list, so the renderer doesn't even visit
 * them per frame. Runs on the section compile threads, so the neighbours come from the section's
 * region copy (the same source as the block model in CodespaceChestModel), not from the level.
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	@WrapWithCondition(method = "compile", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;handleBlockEntity(Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
	private boolean chests$keep(SectionCompiler self, SectionCompiler.Results results, BlockEntity be,
			@Local(argsOnly = true) RenderSectionRegion region) {
		return !(DFOptimizerClient.active() && DFOptimizerClient.isStatic(be.getBlockState())
				&& Codespace.isCodeChest(region, be.getBlockPos()));
	}
}
