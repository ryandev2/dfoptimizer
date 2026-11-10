package dev.ryanland.dfoptimizer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole settings screen: icon, title, the three settings and Done. Deliberately without YACL or
 * Cloth Config. ModMenu opens it through ModMenuIntegration; /dfoptimizer chests|plotfilter|signtext
 * does the same from chat.
 */
public class SettingsScreen extends Screen {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(DFOptimizerClient.MOD_ID, "icon.png");
	private static final int ICON_SIZE = 48, BUTTON_WIDTH = 210;
	private static final int BRAND = 0xFF40E0E6, SUBTLE = 0xFF8A9BA8;

	private final Screen parent;
	private final String version = FabricLoader.getInstance().getModContainer(DFOptimizerClient.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	private int top;

	public SettingsScreen(Screen parent) {
		super(Component.literal("DFOptimizer"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		// icon, title, version, gap, three settings, gap, Done: centred vertically
		int header = ICON_SIZE + 6 + 9 + 5 + 9 + 16;
		int contentHeight = header + 20 + 4 + 20 + 4 + 20 + 16 + 20;
		top = Math.max(8, (height - contentHeight) / 2);
		int x = (width - BUTTON_WIDTH) / 2;
		int y = top + header;

		addRenderableWidget(toggle(DFOptimizerClient.fastChests(),
				"Draw code chests as a static block model. Big codespaces run much smoother.")
				.create(x, y, BUTTON_WIDTH, 20, Component.literal("Fast code chests"),
						(b, v) -> DFOptimizerClient.setFastChests(v)));
		addRenderableWidget(toggle(PlotFilter.enabled(),
				"Only render the plot you are on: neighbouring plots are hidden, and your codespace only shows in dev mode.")
				.create(x, y + 24, BUTTON_WIDTH, 20, Component.literal("Plot filter"),
						(b, v) -> PlotFilter.setEnabled(v)));
		List<Integer> distances = new ArrayList<>();
		for (int d : DFOptimizerClient.SIGN_TEXT_DISTANCES) distances.add(d);
		if (!distances.contains(DFOptimizerClient.signTextDistance())) distances.add(DFOptimizerClient.signTextDistance());
		addRenderableWidget(CycleButton.<Integer>builder(SettingsScreen::distanceLabel, DFOptimizerClient.signTextDistance())
				.withValues(distances)
				.withTooltip(v -> Tooltip.create(Component.literal(
						"Code sign text further away than this is not drawn; the sign itself stays. Up close you can always read it.")))
				.create(x, y + 48, BUTTON_WIDTH, 20, Component.literal("Code sign text"),
						(b, v) -> DFOptimizerClient.setSignTextDistance(v)));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(x, y + 48 + 20 + 16, BUTTON_WIDTH, 20).build());
	}

	private static CycleButton.Builder<Boolean> toggle(boolean value, String tooltip) {
		return CycleButton.booleanBuilder(
						Component.literal("ON").withStyle(ChatFormatting.GREEN),
						Component.literal("OFF").withStyle(ChatFormatting.RED),
						value)
				.withTooltip(v -> Tooltip.create(Component.literal(tooltip)));
	}

	static Component distanceLabel(int blocks) {
		return blocks <= 0 ? Component.literal("Always").withStyle(ChatFormatting.YELLOW)
				: Component.literal("Within " + blocks + " blocks").withStyle(ChatFormatting.GREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int cx = width / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, ICON, cx - ICON_SIZE / 2, top, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
		int y = top + ICON_SIZE + 6;
		g.centeredText(font, Component.literal("DFOptimizer").withStyle(ChatFormatting.BOLD), cx, y, BRAND);
		if (!version.isEmpty()) g.centeredText(font, Component.literal("v" + version), cx, y + 9 + 5, SUBTLE);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
