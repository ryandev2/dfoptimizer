package dev.ryanland.dfoptimizer;

import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Recognises a chest in a DiamondFire codespace: a parameter chest sits directly on top of a
 * code block, and every code block has its sign as a wall sign directly to the west (x-1).
 *
 * All three must match, and the client must be connected to mcdiamondfire.com. Code block
 * materials such as oak_planks, cobblestone and iron_block are ordinary building blocks, so the
 * material alone is too loose. With the sign as well, and only on DF, chests in builds, in
 * singleplayer and on other servers stay vanilla (with their lid animation).
 */
public final class Codespace {
	/** The 19 code blocks of DiamondFire (the material of each code block item). */
	public static final Set<Block> CODEBLOCKS = Set.of(
			Blocks.DIAMOND_BLOCK,      // player event
			Blocks.GOLD_BLOCK,         // entity event
			Blocks.NETHERITE_BLOCK,    // game event
			Blocks.LAPIS_BLOCK,        // function
			Blocks.LAPIS_ORE,          // call function
			Blocks.EMERALD_BLOCK,      // process
			Blocks.EMERALD_ORE,        // start process
			Blocks.COBBLESTONE,        // player action
			Blocks.MOSSY_COBBLESTONE,  // entity action
			Blocks.NETHERRACK,         // game action
			Blocks.IRON_BLOCK,         // set variable
			Blocks.OAK_PLANKS,         // if player
			Blocks.BRICKS,             // if entity
			Blocks.RED_NETHER_BRICKS,  // if game
			Blocks.OBSIDIAN,           // if variable
			Blocks.END_STONE,          // else
			Blocks.PRISMARINE,         // repeat
			Blocks.PURPUR_BLOCK,       // select object
			Blocks.COAL_BLOCK);        // control

	/** For testing outside DF (e.g. ./gradlew :mod:runClient in singleplayer): -Ddfoptimizer.everywhere=true */
	static final boolean EVERYWHERE = Boolean.getBoolean("dfoptimizer.everywhere");

	private record Host(ServerData data, boolean df) {}
	private static volatile Host host;

	private Codespace() {}

	public static boolean isCodeChest(BlockGetter level, BlockPos chestPos) {
		return isCodeChest(level, chestPos, onDiamondFire());
	}

	/** Pure variant (no Minecraft instance needed), also used by the offline checks. */
	public static boolean isCodeChest(BlockGetter level, BlockPos chestPos, boolean onDf) {
		if (!onDf) return false;
		BlockPos block = chestPos.below();
		if (!CODEBLOCKS.contains(level.getBlockState(block).getBlock())) return false;
		return level.getBlockState(block.west()).getBlock() instanceof WallSignBlock;
	}

	/** A code block's sign: a wall sign with a code block directly to its east (the sign is at x-1 of the block). */
	public static boolean isCodeSign(BlockGetter level, BlockPos signPos, BlockState signState) {
		return isCodeSign(level, signPos, signState, onDiamondFire());
	}

	/** Pure variant, also used by the offline checks. */
	public static boolean isCodeSign(BlockGetter level, BlockPos signPos, BlockState signState, boolean onDf) {
		if (!onDf || !(signState.getBlock() instanceof WallSignBlock)) return false;
		return CODEBLOCKS.contains(level.getBlockState(signPos.east()).getBlock());
	}

	/** Only reads a field, so it is also safe from the section compile threads. */
	public static boolean onDiamondFire() {
		if (EVERYWHERE) return true;
		Minecraft mc = Minecraft.getInstance();
		ServerData data = mc == null ? null : mc.getCurrentServer();
		if (data == null) return false;
		Host h = host;
		if (h == null || h.data != data) host = h = new Host(data, isDfAddress(data.ip));
		return h.df;
	}

	public static boolean isDfAddress(String ip) {
		if (ip == null) return false;
		String s = ip.trim().toLowerCase(Locale.ROOT);
		int colon = s.lastIndexOf(':');
		if (colon >= 0 && s.indexOf(':') == colon) s = s.substring(0, colon);
		if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		return s.equals("mcdiamondfire.com") || s.endsWith(".mcdiamondfire.com");
	}
}
