package dev.ryanland.dfoptimizer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/** Chat feedback: "DFOptimizer » Label: ON – detail". */
final class Messages {
	private static final TextColor BRAND = TextColor.fromRgb(0x40E0E6);

	private Messages() {}

	static MutableComponent prefix() {
		return Component.empty()
				.append(Component.literal("DFOptimizer").withStyle(s -> s.withColor(BRAND).withBold(true)))
				.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
	}

	static MutableComponent onOff(boolean on) {
		return Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD);
	}

	/** One status line; detail and warning may be null. */
	static Component line(String label, boolean on, String detail, String warning) {
		MutableComponent m = prefix()
				.append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
				.append(onOff(on));
		if (detail != null && !detail.isEmpty()) {
			m.append(Component.literal(" – " + detail).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (warning != null && !warning.isEmpty()) {
			m.append(Component.literal(" " + warning).withStyle(ChatFormatting.YELLOW));
		}
		return m;
	}
}
