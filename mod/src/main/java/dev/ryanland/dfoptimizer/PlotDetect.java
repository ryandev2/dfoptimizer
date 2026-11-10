package dev.ryanland.dfoptimizer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Finds the plot bounds without CodeClient, for plots where you don't have dev: from the codespace strip.
 *
 * Measured in play mode (Basic, Large and Massive plots, compared against CodeClient): the
 * codespace lies directly west of the plot and has a glass column every 3 blocks (one glass block
 * per code layer); the easternmost one is at origin - 2. The strip runs in z from origin to
 * origin + size, so its length is the plot size + 1 (51/101/301). Mega: the codespace is 300 wide,
 * so the pattern continues much further west.
 *
 * Searches westwards from the player, loaded chunks only. PlotFilter only uses the result when
 * the player stands inside the detected plot.
 */
final class PlotDetect {
	record Result(int lastGlassX, int stripMinZ, int stripMaxZ, boolean minComplete, boolean maxComplete, int columnsWest) {
		boolean zComplete() {
			return minComplete && maxComplete;
		}

		int originX() {
			return lastGlassX + 2;
		}

		/** North edge found: use it; otherwise the south edge minus the codespace length (Mega: 300, not 1000). */
		int originZ() {
			String size = size();
			return minComplete || size == null ? stripMinZ : stripMaxZ - PlotFilter.SIZES.get(size).codeLength();
		}

		int stripLength() {
			return stripMaxZ - stripMinZ + 1;
		}

		/** null when not certain (strip edge not loaded, or not a standard length). */
		String size() {
			if (columnsWest > 12) return "MEGA";
			if (zComplete()) return PlotFilter.sizeFromWidth(stripLength() - 1);
			// One end not loaded (distant chunks), but the strip is already longer than Large and the
			// pattern is 20 wide (not Mega): then it is Massive (seen without Sodium).
			if ((minComplete || maxComplete) && stripLength() > 101 + 3) return "MASSIVE";
			return null;
		}
	}

	private static final int GLASS_PER_COLUMN = 1, PERIOD = 3, COLUMNS = 6, MAX_SEARCH = 1100;
	/**
	 * First every 2 s for a minute (chunks still loading). After that only once the player has moved
	 * 32 blocks: on a Mega plot the server only loads ~8 chunks around you, so the codespace only
	 * comes into view when you walk towards the west side (spawn can be 1000 blocks away).
	 */
	private static final int MAX_TRIES = 30, MOVE = 32;

	private static Object session;
	private static Result result;
	private static int wait, tries, lastX, lastZ;

	private PlotDetect() {}

	/** Restarts per plot visit (every 2 s until certain, at most MAX_TRIES); logs the result or the failure. */
	static Result current(Minecraft mc, Object session) {
		if (session != PlotDetect.session) {
			PlotDetect.session = session;
			result = null;
			tries = 0;
			wait = 40;
		}
		if (result != null || mc.level == null || mc.player == null) return result;
		if (--wait > 0) return null;
		wait = 40;
		int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
		if (tries >= MAX_TRIES && Math.abs(px - lastX) < MOVE && Math.abs(pz - lastZ) < MOVE) return null;
		lastX = px;
		lastZ = pz;
		tries++;
		Result r = detect(mc.level, px, pz);
		// Only lock it in once it is certain: without Sodium distant chunks load more slowly, and a
		// strip with one end not loaded yet gives no size.
		if (r != null && r.size() != null) {
			result = r;
			DFOptimizerClient.LOG.info("Plot detection (player {},{}): origin {},{}, {} (strip z {}..{}, length {}, {} glass columns, attempt {})",
					px, pz, r.originX(), r.originZ(), r.size(), r.stripMinZ(), r.stripMaxZ(), r.stripLength(), r.columnsWest(), tries);
		} else if (tries == MAX_TRIES) {
			DFOptimizerClient.LOG.info("Plot detection: after {} attempts no complete codespace strip west of {},{}{}", tries, px, pz,
					r == null ? "" : String.format(" (found strip z %d..%d, but its edge is not loaded)", r.stripMinZ(), r.stripMaxZ()));
		}
		return result;
	}

	/** For the log when a detection does not contain the player: glass per column between strip and player. */
	static String glassProfile(Level level, int fromX, int toX, int z) {
		Map<Long, Integer> cache = new HashMap<>();
		StringBuilder sb = new StringBuilder();
		for (int x = fromX; x <= toX && x - fromX < 200; x++) {
			if (!loaded(level, x, z)) { sb.append(' '); continue; }
			int g = glass(level, cache, x, z);
			sb.append(g == 0 ? '.' : g > 9 ? '+' : Character.forDigit(g, 10));
		}
		return sb.toString();
	}

	static Result detect(Level level, int px, int pz) {
		Map<Long, Integer> cache = new HashMap<>();
		Integer last = null;
		for (int x = px; x >= px - MAX_SEARCH; x--) {
			if (!loaded(level, x, pz)) return null;
			if (isStripEnd(level, cache, x, pz)) {
				last = x;
				break;
			}
		}
		if (last == null) return null;

		// how far the glass pattern continues to the west (20 wide: 6 columns, Mega: ~100)
		int columns = 0;
		while (columns < 120 && loaded(level, last - columns * PERIOD, pz)
				&& glass(level, cache, last - columns * PERIOD, pz) >= GLASS_PER_COLUMN) columns++;

		// z ends of the strip along the easternmost glass column
		int minZ = pz, maxZ = pz;
		boolean minComplete = true, maxComplete = true;
		while (true) {
			if (!loaded(level, last, minZ - 1)) { minComplete = false; break; }
			if (glass(level, cache, last, minZ - 1) < GLASS_PER_COLUMN) break;
			minZ--;
		}
		while (true) {
			if (!loaded(level, last, maxZ + 1)) { maxComplete = false; break; }
			if (glass(level, cache, last, maxZ + 1) < GLASS_PER_COLUMN) break;
			maxZ++;
		}
		return new Result(last, minZ, maxZ, minComplete, maxComplete, columns);
	}

	/**
	 * x is the easternmost glass column of a strip: six glass columns at x, x-3, ..., x-15, with
	 * columns holding less glass in between. One glass layer is enough (a codespace with a single
	 * layer has one); the in-between columns keep a glass floor in a build from matching.
	 */
	private static boolean isStripEnd(Level level, Map<Long, Integer> cache, int x, int z) {
		for (int i = 0; i < COLUMNS; i++) {
			int gx = x - i * PERIOD;
			if (!loaded(level, gx - 2, z) || !loaded(level, gx, z)) return false;
			int g = glass(level, cache, gx, z);
			if (g < GLASS_PER_COLUMN) return false;
			if (glass(level, cache, gx - 1, z) >= g || glass(level, cache, gx - 2, z) >= g) return false;
		}
		return true;
	}

	private static boolean loaded(Level level, int x, int z) {
		return level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) != null;
	}

	/** Number of glass blocks (not panes) in the column. */
	private static int glass(Level level, Map<Long, Integer> cache, int x, int z) {
		return cache.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
			int n = 0;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int y = level.getMinY(); y < level.getMaxY(); y++) {
				BlockState st = level.getBlockState(p.set(x, y, z));
				if (st.isAir()) continue;
				String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
				if (id.endsWith("glass") && !id.contains("pane")) n++;
			}
			return n;
		});
	}
}
