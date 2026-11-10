package dev.ryanland.dfoptimizer;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wraps the block state model of chest/trapped_chest: in the chunk mesh it only emits geometry for
 * a code chest (see Codespace), and nothing elsewhere, just like vanilla (where ChestRenderer draws
 * the chest). Fabric API routes the terrain mesh through emitQuads (with region and position);
 * without that context (collectParts: block displays, falling blocks) the chest behaves like vanilla too.
 */
public class CodespaceChestModel extends WrapperBlockStateModel {
	public CodespaceChestModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	public BlockStateModel wrapped() {
		return wrapped;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
		if (DFOptimizerClient.active() && Codespace.isCodeChest(level, pos)) {
			wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
		}
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
		// vanilla chest: no block geometry
	}

	@Override
	public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null; // depends on the neighbours, so not cacheable
	}
}
