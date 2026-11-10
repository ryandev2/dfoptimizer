package dev.ryanland.dfoptimizer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Plot data from CodeClient (dev.dfonline.codeclient) via reflection: no hard dependency, and
 * without CodeClient the plot filter falls back to the world border and PlotDetect.
 *
 * CodeClient.location is Spawn, Play, Build or Dev; the last three are a Plot with an id, an
 * origin (getX/getZ, only known after dev mode) and a size (getSize, BASIC..MEGA, sometimes null).
 */
final class CodeClientBridge {
	/** handle = the Location object itself: CodeClient creates a new one per plot visit/mode. */
	record Info(String mode, Integer id, Integer x, Integer z, String size, Object handle) {}

	private static boolean initialized, ok;
	private static Field location, id;
	private static Class<?> plot;
	private static Method getX, getZ, getSize;

	private CodeClientBridge() {}

	static boolean available() {
		if (!initialized) {
			initialized = true;
			// test switch: pretend CodeClient is not installed (mode from chat, plot from PlotDetect)
			if (System.getenv("DFOPTIMIZER_NO_CODECLIENT") != null) {
				DFOptimizerClient.LOG.info("Ignoring CodeClient (DFOPTIMIZER_NO_CODECLIENT is set)");
				return ok = false;
			}
			try {
				Class<?> cc = Class.forName("dev.dfonline.codeclient.CodeClient");
				location = cc.getField("location");
				plot = Class.forName("dev.dfonline.codeclient.location.Plot");
				id = plot.getDeclaredField("id");
				id.setAccessible(true);
				getX = plot.getMethod("getX");
				getZ = plot.getMethod("getZ");
				getSize = plot.getMethod("getSize");
				ok = true;
			} catch (ClassNotFoundException e) {
				ok = false;
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				ok = false;
				DFOptimizerClient.LOG.warn("CodeClient found but not readable (different version?): {}", e.toString());
			}
		}
		return ok;
	}

	/** null = not on a plot (spawn, menu) or CodeClient not usable. */
	static Info read() {
		if (!available()) return null;
		try {
			Object loc = location.get(null);
			if (loc == null || !plot.isInstance(loc)) return null;
			Object size = getSize.invoke(loc);
			return new Info(loc.getClass().getSimpleName(), (Integer) id.get(loc),
					(Integer) getX.invoke(loc), (Integer) getZ.invoke(loc),
					size instanceof Enum<?> e ? e.name() : null, loc);
		} catch (ReflectiveOperationException | RuntimeException e) {
			ok = false;
			DFOptimizerClient.LOG.warn("Could not read the CodeClient location, falling back to plot detection: {}", e.toString());
			return null;
		}
	}
}
