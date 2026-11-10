package dev.ryanland.dfoptimizer;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Sodium is optional: its classes are only touched when it is loaded. */
final class SodiumCompat {
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded("sodium");

	private SodiumCompat() {}

	/** After the visible box changes, rerun the visibility search, even if the camera is not moving. */
	static void scheduleTerrainUpdate() {
		if (LOADED) Impl.schedule();
	}

	/**
	 * Remesh every section within render distance: LevelSliceMixin leaves out blocks outside the
	 * box, so a section on the plot edge looks different per box. Only when the box changes (mode
	 * switch, other plot), never per frame.
	 */
	static void rebuildAround(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		if (LOADED) {
			Impl.rebuild(mc);
		} else {
			// vanilla: all sections again (RenderSectionRegionMixin shows air outside the plot)
			mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
		}
	}

	private static final class Impl {
		static void schedule() {
			SodiumWorldRenderer r = SodiumWorldRenderer.instanceNullable();
			if (r != null) r.scheduleTerrainUpdate();
		}

		static void rebuild(Minecraft mc) {
			SodiumWorldRenderer r = SodiumWorldRenderer.instanceNullable();
			if (r == null) return;
			int rd = mc.options.getEffectiveRenderDistance() + 1;
			int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
			r.scheduleRebuildForChunks(cx - rd, mc.level.getMinSectionY(), cz - rd,
					cx + rd, mc.level.getMaxSectionY(), cz + rd, false);
		}
	}
}
