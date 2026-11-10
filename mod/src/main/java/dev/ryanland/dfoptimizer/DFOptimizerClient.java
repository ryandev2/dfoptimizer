package dev.ryanland.dfoptimizer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chests in a DiamondFire codespace as a plain block model; every other chest stays vanilla.
 *
 * In 26.3 a chest already has RenderShape.MODEL, but the vanilla block model is empty: everything
 * comes from ChestRenderer, which builds a render state per chest per frame (including a neighbour
 * combine for double chests). With thousands of chests in a DF codespace that is what grinds the
 * client down. The built-in pack 'static_chests' gives chest and trapped_chest a real block model
 * (UVs on the vanilla chest texture). CodespaceChestModel only bakes that model into the chunk
 * mesh for code chests (see Codespace), and the mixins skip the block entity renderer for exactly
 * those positions. Elsewhere: empty model + ChestRenderer, so plain vanilla with the lid animation.
 *
 * On/off: the pack (Options, Resource Packs) and the fastChests switch (config, ModMenu,
 * /dfoptimizer chests). Whether the pack is active is read on every resource reload from a marker
 * file that only exists in that pack, so model and renderer never get out of sync (otherwise:
 * invisible or doubled chests). The switch only triggers a remesh of the chunks.
 */
public class DFOptimizerClient implements ClientModInitializer {
	public static final String MOD_ID = "dfoptimizer";
	public static final Logger LOG = LoggerFactory.getLogger("DFOptimizer");
	static final Identifier PACK = Identifier.fromNamespaceAndPath(MOD_ID, "static_chests");
	static final Identifier MARKER = Identifier.fromNamespaceAndPath(MOD_ID, "static_chests.txt");

	private static volatile boolean packActive;
	private static volatile boolean fastChests = true;
	/** Code sign text is only drawn within this many blocks of the camera; 0 = always (vanilla). */
	private static volatile int signTextDistance = 32;
	public static final int[] SIGN_TEXT_DISTANCES = {0, 16, 24, 32, 48};

	@Override
	public void onInitializeClient() {
		boolean ok = ResourceLoader.registerBuiltinPack(PACK,
				FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(),
				Component.literal("DFOptimizer: Fast Code Chests"),
				PackActivationType.DEFAULT_ENABLED);
		if (!ok) LOG.warn("Could not register built-in pack {}", PACK);

		// wrap every chest/trapped_chest block state model in a per-position wrapper
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelAfterBake().register((model, c) ->
				isStatic(c.state()) && !(model instanceof CodespaceChestModel) ? new CodespaceChestModel(model) : model));

		// plot filter: only render your own plot (see PlotFilter)
		PlotFilter.load();
		ClientTickEvents.END_CLIENT_TICK.register(PlotFilter::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) PlotFilter.onGameMessage(message);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommands.literal("dfoptimizer")
						.executes(c -> feedback(c.getSource(), chestsStatus(), plotFilterStatus(), signTextStatus()))
						.then(ClientCommands.literal("signtext")
								.executes(c -> feedback(c.getSource(), signTextStatus()))
								.then(ClientCommands.argument("blocks", IntegerArgumentType.integer(0, 128)).executes(c -> {
									setSignTextDistance(IntegerArgumentType.getInteger(c, "blocks"));
									return feedback(c.getSource(), signTextStatus());
								})))
						.then(ClientCommands.literal("plotfilter")
								.executes(c -> feedback(c.getSource(), plotFilterStatus()))
								.then(ClientCommands.literal("on").executes(c -> {
									PlotFilter.setEnabled(true);
									return feedback(c.getSource(), plotFilterStatus());
								}))
								.then(ClientCommands.literal("off").executes(c -> {
									PlotFilter.setEnabled(false);
									return feedback(c.getSource(), plotFilterStatus());
								})))
						.then(ClientCommands.literal("chests")
								.executes(c -> feedback(c.getSource(), chestsStatus()))
								.then(ClientCommands.literal("on").executes(c -> {
									setFastChests(true);
									return feedback(c.getSource(), chestsStatus());
								}))
								.then(ClientCommands.literal("off").executes(c -> {
									setFastChests(false);
									return feedback(c.getSource(), chestsStatus());
								})))));

		// Only present in a local (never published) debug build: see src/debug.
		try {
			Class.forName("dev.ryanland.dfoptimizer.DebugTools").getMethod("init").invoke(null);
			LOG.info("Debug tools loaded");
		} catch (ClassNotFoundException e) {
			// regular build
		} catch (ReflectiveOperationException e) {
			LOG.warn("Debug tools failed to load: {}", e.toString());
		}
	}

	private static int feedback(FabricClientCommandSource source, Component... lines) {
		for (Component line : lines) source.sendFeedback(line);
		return 1;
	}

	static Component chestsStatus() {
		boolean packOff = fastChests && !packActive;
		return Messages.line("Fast code chests", fastChests, null, packOff ? "(resource pack disabled)" : null);
	}

	static Component signTextStatus() {
		int d = signTextDistance;
		return Messages.line("Code sign text", d > 0, d > 0 ? "within " + d + " blocks" : "always drawn", null);
	}

	static Component plotFilterStatus() {
		boolean on = PlotFilter.enabled();
		return Messages.line("Plot filter", on, on ? PlotFilter.summary() : null, null);
	}

	/** Called on every resource reload (via the block entity render dispatcher). */
	public static void refresh(ResourceManager rm) {
		boolean now = rm.getResource(MARKER).isPresent();
		if (now != packActive) LOG.info("Fast code chests resource pack: {}{}", now ? "enabled" : "disabled",
				Codespace.EVERYWHERE ? " (everywhere, -Ddfoptimizer.everywhere)" : " (DiamondFire only)");
		packActive = now;
	}

	public static boolean fastChests() {
		return fastChests;
	}

	/** From the config; no rebuild, the world is not loaded yet at that point. */
	static void loadFastChests(boolean on) {
		fastChests = on;
	}

	public static void setFastChests(boolean on) {
		if (fastChests == on) return;
		fastChests = on;
		PlotFilter.save();
		// chests are (or are no longer) part of the chunk mesh: remesh
		Minecraft mc = Minecraft.getInstance();
		SodiumCompat.rebuildAround(mc);
	}

	public static boolean active() {
		return packActive && fastChests;
	}

	/** Only regular and trapped chests; ender and copper chests keep their renderer. */
	public static boolean isStatic(BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
	}

	public static int signTextDistance() {
		return signTextDistance;
	}

	static void loadSignTextDistance(int blocks) {
		signTextDistance = Math.max(0, blocks);
	}

	public static void setSignTextDistance(int blocks) {
		if (signTextDistance == blocks) return;
		signTextDistance = Math.max(0, blocks);
		PlotFilter.save();
	}

	/**
	 * Code sign text far away: no renderer, so no render state and no text. The board itself is part
	 * of the chunk mesh in 26.3 (signs have a block model), so it stays visible. Distance first: the
	 * few signs close to the camera never pay for the code sign lookup.
	 */
	public static boolean hideSignText(BlockEntity be, net.minecraft.world.phys.Vec3 cam) {
		int d = signTextDistance;
		if (d <= 0 || cam == null || !(be instanceof SignBlockEntity) || be.getLevel() == null) return false;
		var p = be.getBlockPos();
		double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
		if (dx * dx + dy * dy + dz * dz <= (double) d * d) return false;
		return Codespace.isCodeSign(be.getLevel(), p, be.getBlockState());
	}

	/**
	 * Client-side ticks that do nothing visible: a baked code chest has no lid to animate, and a code
	 * sign's tick only concerns the server-driven editor check.
	 */
	public static boolean skipTick(BlockEntity be) {
		if (be.getLevel() == null || !be.getLevel().isClientSide()) return false;
		if (be instanceof ChestBlockEntity) return skip(be);
		if (be instanceof SignBlockEntity) return Codespace.isCodeSign(be.getLevel(), be.getBlockPos(), be.getBlockState());
		return false;
	}

	/** Render thread: called per chest per frame, so the cheap checks go first. */
	public static boolean skip(BlockEntity be) {
		return active() && isStatic(be.getBlockState()) && be.getLevel() != null
				&& Codespace.isCodeChest(be.getLevel(), be.getBlockPos());
	}
}
