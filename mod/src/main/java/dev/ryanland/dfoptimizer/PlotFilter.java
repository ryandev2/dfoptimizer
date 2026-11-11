package dev.ryanland.dfoptimizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * On DiamondFire, only render your own plot: not other plots (or their codespaces), and your own
 * codespace only in dev mode.
 *
 * Why: an F3+L profile next to a codespace showed that the terrain itself costs almost nothing
 * (under 1%), but ~15,000 signs and chests do: drawing sign text, extracting render states and
 * client-side ticking together take well over half of the frame time. Most of them are on the
 * surrounding plots. Whatever falls outside the visible box:
 * - Sodium doesn't visit the section in its visibility search (OcclusionCullerMixin), so no
 *   terrain, no block entities and no rebuilds of that section;
 * - block entities get no renderer and don't tick client-side (block-precise, also in a section
 *   that is half in view).
 *
 * Plot data comes from CodeClient (see CodeClientBridge), the world border or PlotDetect, and is
 * remembered per plot in config/dfoptimizer.json. Safety: if the player is outside the computed
 * box (stale origin, wrong size), nothing is hidden. Drawing too much beats hiding your own plot.
 */
public final class PlotFilter {
	/**
	 * Margin around the plot. 0: the neighbouring plot's codespace (green glass) starts right next
	 * to the edge, and with a 2 block margin a strip of it was visible.
	 */
	static final int PAD = 0;

	/** Sizes as CodeClient knows them (Plot.Size): plot, codespace length (z), codespace width (-x). */
	record Dims(int size, int codeLength, int codeWidth) {}

	static final Map<String, Dims> SIZES = Map.of(
			"BASIC", new Dims(50, 50, 20),
			"LARGE", new Dims(100, 100, 20),
			"MASSIVE", new Dims(300, 300, 20),
			"MEGA", new Dims(1000, 300, 300));

	/** Visible area in world coordinates, x and z inclusive; y is ignored. */
	public record View(int minX, int maxX, int minZ, int maxZ) {
		public boolean containsBlock(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		/** A section stays visible as soon as even one of its block columns is inside the box. */
		public boolean overlapsChunk(int chunkX, int chunkZ) {
			int x0 = chunkX << 4, z0 = chunkZ << 4;
			return x0 + 15 >= minX && x0 <= maxX && z0 + 15 >= minZ && z0 <= maxZ;
		}
	}

	private record Saved(int x, int z, String size) {}

	private static volatile View view;
	/** Set by OcclusionCullerMixin; this is how we notice a Sodium version where the hook no longer applies. */
	public static volatile boolean sodiumHookSeen;

	private static boolean enabled = true;
	private static final Map<String, Saved> plots = new LinkedHashMap<>();
	/** Current DF node, from "Sending you to Node N" (each node has its own coordinates). */
	private static volatile String node = "?";
	/** Last detection that did not contain the player: logged only once. */
	private static Object lastRejected;
	private static final java.util.regex.Pattern NODE = java.util.regex.Pattern.compile("Sending you to (Private )?Node ([A-Za-z0-9_-]+)");
	private static String lastState = "";
	/** Short, user-facing version of the state for chat (the full state goes into the log). */
	private static volatile String summary = "";
	private static int ticksWithView;
	private static boolean warnedHook, warnedNoSodium;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Lazy: the offline checks call viewFor without a running Fabric Loader. */
	private static Path config() {
		return FabricLoader.getInstance().getConfigDir().resolve("dfoptimizer.json");
	}

	private PlotFilter() {}

	// ---- per frame / per block: just a volatile read and a few comparisons ----

	public static boolean hidesSection(int chunkX, int chunkZ) {
		View v = view;
		return v != null && !v.overlapsChunk(chunkX, chunkZ);
	}

	public static boolean hidesBlock(BlockPos pos) {
		return hidesBlock(pos.getX(), pos.getZ());
	}

	/** Also called from Sodium's mesh threads (LevelSliceMixin): just a volatile read. */
	public static boolean hidesBlock(int x, int z) {
		View v = view;
		return v != null && !v.containsBlock(x, z);
	}

	/** Entities on other plots; never yourself or what you are riding. */
	public static boolean hides(net.minecraft.world.entity.Entity e) {
		View v = view;
		if (v == null || v.containsBlock(e.getBlockX(), e.getBlockZ())) return false;
		Minecraft mc = Minecraft.getInstance();
		return e != mc.player && (mc.player == null || !e.hasIndirectPassenger(mc.player));
	}

	/** Only block entities in the client world; never a detached BE without a level (item rendering etc.). */
	public static boolean hides(BlockEntity be) {
		View v = view;
		if (v == null) return false;
		Level level = be.getLevel();
		return level != null && level.isClientSide() && !v.containsBlock(be.getBlockPos().getX(), be.getBlockPos().getZ());
	}

	// ---- geometry (pure, also used by the offline checks) ----

	/**
	 * Visible box for a mode, blocks inclusive. The plot is origin..origin + size (301 blocks for
	 * Massive; CodeClient Plot.isInArea), the codespace is origin - width..origin - 1 in x and
	 * origin..origin + length in z (Plot.isInDev). The codespace is only included in dev mode.
	 */
	public static View viewFor(String mode, int originX, int originZ, String size) {
		Dims d = size == null ? null : SIZES.get(size);
		if (d == null || mode == null) return null;
		boolean dev = mode.equals("Dev");
		if (!dev && !mode.equals("Play") && !mode.equals("Build")) return null;
		int minX = (dev ? originX - d.codeWidth() : originX) - PAD;
		int maxX = originX + d.size() + PAD;
		int minZ = originZ - PAD;
		int maxZ = originZ + Math.max(d.size(), dev ? d.codeLength() : 0) + PAD;
		return new View(minX, maxX, minZ, maxZ);
	}

	/** Size for a plot width (border or measurement): 50/100/300/1000, with some tolerance; otherwise null. */
	public static String sizeFromWidth(int width) {
		for (var e : SIZES.entrySet()) {
			if (Math.abs(width - e.getValue().size()) <= 3) return e.getKey();
		}
		return null;
	}

	/** The world border as a box (plus margin), only when it is plot-sized (not the default 60 million). */
	static View borderView(Level level) {
		WorldBorder wb = level.getWorldBorder();
		double w = wb.getMaxX() - wb.getMinX(), d = wb.getMaxZ() - wb.getMinZ();
		if (w < 8 || d < 8 || w > 1300 || d > 1300) return null;
		return new View((int) Math.floor(wb.getMinX()) - PAD, (int) Math.ceil(wb.getMaxX()) + PAD,
				(int) Math.floor(wb.getMinZ()) - PAD, (int) Math.ceil(wb.getMaxZ()) + PAD);
	}

	/** Chat message from DF: the node, and (without CodeClient) the mode. */
	static void onGameMessage(net.minecraft.network.chat.Component message) {
		String text = message.getString();
		var m = NODE.matcher(text);
		if (m.find()) {
			// "Private Node 34" is a different server than "Node 34"
			node = (m.group(1) != null ? "p" : "") + m.group(2);
			setChatMode(null);
		} else if (text.contains("Joined game:")) {
			setChatMode("Play");
		} else if (text.contains("You are now in dev mode")) {
			setChatMode("Dev");
		} else if (text.contains("You are now in build mode")) {
			setChatMode("Build");
		}
	}

	// Without CodeClient: mode from chat. A new world (other node, spawn) resets it.
	private static volatile String chatMode;
	private static volatile Object chatSession = new Object();
	private static Object chatLevel;

	/** Also remembers which world the message arrived in: a later world change resets the mode. */
	private static void setChatMode(String mode) {
		chatMode = mode;
		chatSession = new Object();
		chatLevel = Minecraft.getInstance().level;
	}

	private static CodeClientBridge.Info chatInfo(Minecraft mc) {
		if (mc.level != chatLevel) setChatMode(null);
		String mode = chatMode;
		return mode == null ? null : new CodeClientBridge.Info(mode, null, null, null, null, chatSession);
	}

	/** The remembered plot on this node that contains (x, z) (in dev: including the codespace), or null. */
	private static String savedKeyAt(String mode, int x, int z) {
		String prefix = node + "|";
		for (var e : plots.entrySet()) {
			if (!e.getKey().startsWith(prefix)) continue;
			Saved s = e.getValue();
			View v = viewFor(mode, s.x(), s.z(), s.size() != null ? s.size() : "MEGA");
			if (v != null && v.containsBlock(x, z)) return e.getKey();
		}
		return null;
	}

	// ---- client tick ----

	/** The last plot box the player actually stood in (reset at spawn). */
	private static View confirmed;
	/** How far outside a confirmed plot the player may be (border push-back, knockback) before it is dropped. */
	private static final int NEAR = 8;

	private static boolean near(View v, int x, int z) {
		return x >= v.minX() - NEAR && x <= v.maxX() + NEAR && z >= v.minZ() - NEAR && z <= v.maxZ() + NEAR;
	}

	static void tick(Minecraft mc) {
		View next = null;
		String state;
		if (!enabled) {
			state = "off (/dfoptimizer plotfilter on turns it back on)";
			summary = "";
		} else if (mc.level == null || mc.player == null) {
			state = "no world";
			summary = "not in a world";
		} else if (!Codespace.onDiamondFire()) {
			state = "not on DiamondFire";
			summary = "not on DiamondFire";
		} else {
			// Mode: from CodeClient if present, otherwise from DF chat (see onGameMessage).
			CodeClientBridge.Info p = CodeClientBridge.available() ? CodeClientBridge.read() : chatInfo(mc);
			var hook = debugTick;
			if (hook != null) hook.accept(mc, p == null ? "lobby" : p.mode());
			if (p == null) {
				confirmed = null;
				state = "not on a plot";
				summary = "not on a plot";
			} else {
				boolean playOrBuild = p.mode().equals("Play") || p.mode().equals("Build");
				// Sources for origin and size, in order: CodeClient (live), a plot-sized world border,
				// the codespace strip (PlotDetect, also works without dev), memory.
				View border = borderView(mc.level);
				String borderSize = border == null ? null : sizeFromWidth(border.maxX() - border.minX() - 2 * PAD);
				Integer bx = borderSize != null ? border.minX() + PAD : null, bz = borderSize != null ? border.minZ() + PAD : null;
				PlotDetect.Result d = playOrBuild && p.x() == null && bx == null ? PlotDetect.current(mc, p.handle()) : null;
				String dSize = d == null ? null : d.size();
				Integer dx = dSize != null ? d.originX() : null, dz = dSize != null ? d.originZ() : null;

				// Key: node + origin (CodeClient often doesn't know the plot id, and nodes share
				// coordinates). Without an origin: the remembered plot on this node you are standing on.
				Integer cx = p.x() != null ? p.x() : bx != null ? bx : dx, cz = p.z() != null ? p.z() : bz != null ? bz : dz;
				String key = cx != null && cz != null ? node + "|" + cx + "," + cz : savedKeyAt(p.mode(), mc.player.getBlockX(), mc.player.getBlockZ());
				Saved saved = key == null ? null : plots.get(key);
				Integer ox = cx != null ? cx : saved != null ? saved.x() : null;
				Integer oz = cz != null ? cz : saved != null ? saved.z() : null;

				// Size: CodeClient, seen before (saved), the border, the detection.
				String size = p.size() != null ? p.size() : saved != null && saved.size() != null ? saved.size() : borderSize != null ? borderSize : dSize;
				String sizeSource = p.size() != null ? "CodeClient" : saved != null && saved.size() != null ? "saved" : borderSize != null ? "border" : dSize != null ? "detected" : null;
				if (key != null && ox != null && oz != null) {
					remember(key, ox, oz, size);
					saved = plots.get(key);
				}
				boolean dev = p.mode().equals("Dev");
				String where = p.mode() + " on " + (key != null ? "node " + key.replace("|", ", plot origin ") : "node " + node);
				View plot = ox != null && oz != null && size != null ? viewFor(p.mode(), ox, oz, size) : null;
				boolean assumed = false;
				if (plot != null) {
					next = plot;     // size known: the plot (in dev plus your own codespace)
				} else if (border != null) {
					next = border;   // non-standard size (world plot etc.): the border itself
				} else if (ox != null && oz != null && viewFor(p.mode(), ox, oz, "MEGA") != null) {
					// no border and no size: assume Mega, so your own plot is never hidden
					next = viewFor(p.mode(), ox, oz, "MEGA");
					size = "MEGA";
					assumed = true;
				}
				// Everything except CodeClient (border, detection, memory) only applies when you are really
				// standing on it. Otherwise a wrong guess hides everything around you.
				int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
				if (next != null && next.containsBlock(px, pz)) {
					confirmed = next;
				} else if (next != null && next.equals(confirmed) && near(next, px, pz)) {
					// Already stood on this plot: walking into the border (DF teleports you back) does
					// not make it a wrong guess, so keep it instead of showing everything for a moment.
				} else if (next != null && p.x() == null) {
					if (d != null && d != lastRejected) DFOptimizerClient.LOG.info("Plot filter: detected plot {},{} does not contain the player; glass per column from the strip: {}",
							d.originX(), d.originZ(), PlotDetect.glassProfile(mc.level, d.lastGlassX() - 20, mc.player.getBlockX(), mc.player.getBlockZ()));
					lastRejected = d;
					next = null;
				}
				if (next != null) {
					summary = "showing only this plot" + (dev ? " and your codespace" : "");
					state = String.format("%s, %s (%s)%s: visible x %d..%d, z %d..%d", where,
							size == null ? "size from border" : size,
							assumed ? "assumed, no border seen yet" : sizeSource == null ? "border" : sizeSource,
							border == null ? ", no border" : String.format(", border x %d..%d z %d..%d", border.minX() + PAD, border.maxX() - PAD, border.minZ() + PAD, border.maxZ() - PAD),
							next.minX(), next.maxX(), next.minZ(), next.maxZ());
				} else {
					state = p.mode() + ": plot bounds unknown, nothing hidden";
					summary = "plot not detected yet, nothing hidden";
				}
			}
		}

		if (!state.equals(lastState)) {
			DFOptimizerClient.LOG.info("Plot filter: {}", state);
			lastState = state;
		}
		if (!Objects.equals(next, view)) {
			view = next;
			// sections on the edge were meshed with the old box: rebuild them, and search again
			SodiumCompat.rebuildAround(mc);
			SodiumCompat.scheduleTerrainUpdate();
		}

		ticksWithView = next == null ? 0 : ticksWithView + 1;
		if (ticksWithView == 100) {
			if (!SodiumCompat.LOADED && !warnedNoSodium) {
				warnedNoSodium = true;
				DFOptimizerClient.LOG.info("Plot filter: Sodium not installed, hiding terrain through the vanilla renderer");
			} else if (SodiumCompat.LOADED && !sodiumHookSeen && !warnedHook) {
				warnedHook = true;
				DFOptimizerClient.LOG.warn("Plot filter: the Sodium hook (OcclusionCuller.visitNode) is not active, different Sodium version? "
						+ "Terrain outside the plot is still drawn; block entities are still hidden.");
			}
		}
	}

	public static String summary() {
		return summary;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean on) {
		if (enabled == on) return;
		enabled = on;
		save();
	}

	/** Only set by a local debug build (src/debug): receives the mode every tick. */
	public static volatile java.util.function.BiConsumer<Minecraft, String> debugTick;

	// ---- config ----

	static void load() {
		Path file = config();
		boolean migrate = false;
		if (!Files.exists(file)) {
			// one-time: the config file of the pre-release name, including the remembered plots
			Path old = file.resolveSibling("blockdrift-chests.json");
			if (!Files.exists(old)) return;
			DFOptimizerClient.LOG.info("Migrating settings and saved plots from {}", old.getFileName());
			file = old;
			migrate = true;
		}
		try (Reader r = Files.newBufferedReader(file)) {
			JsonObject o = GSON.fromJson(r, JsonObject.class);
			if (o == null) return;
			if (o.has("plotFilter")) enabled = o.get("plotFilter").getAsBoolean();
			if (o.has("fastChests")) DFOptimizerClient.loadFastChests(o.get("fastChests").getAsBoolean());
			if (o.has("signTextDistance")) DFOptimizerClient.loadSignTextDistance(o.get("signTextDistance").getAsInt());
			if (o.has("plots")) {
				for (var e : o.getAsJsonObject("plots").entrySet()) {
					if (!e.getKey().contains("|")) continue; // from before the node-based keys
					JsonObject p = e.getValue().getAsJsonObject();
					String size = p.has("size") && !p.get("size").isJsonNull() ? p.get("size").getAsString() : null;
					plots.put(e.getKey(), new Saved(p.get("x").getAsInt(), p.get("z").getAsInt(), size));
				}
			}
		} catch (Exception e) {
			DFOptimizerClient.LOG.warn("Could not read {}, using defaults: {}", file, e.toString());
		}
		if (migrate) save();
	}

	private static void remember(String key, int x, int z, String size) {
		Saved now = new Saved(x, z, size);
		if (now.equals(plots.put(key, now))) return;
		save();
	}

	static void save() {
		JsonObject o = new JsonObject();
		o.addProperty("plotFilter", enabled);
		o.addProperty("fastChests", DFOptimizerClient.fastChests());
		o.addProperty("signTextDistance", DFOptimizerClient.signTextDistance());
		JsonObject ps = new JsonObject();
		plots.forEach((id, s) -> {
			JsonObject p = new JsonObject();
			p.addProperty("x", s.x());
			p.addProperty("z", s.z());
			p.addProperty("size", s.size());
			ps.add(id, p);
		});
		o.add("plots", ps);
		Path file = config();
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file)) {
				GSON.toJson(o, w);
			}
		} catch (Exception e) {
			DFOptimizerClient.LOG.warn("Could not write {}: {}", file, e.toString());
		}
	}
}
