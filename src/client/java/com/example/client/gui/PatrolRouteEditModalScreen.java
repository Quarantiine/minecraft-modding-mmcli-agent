package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.ClientPatrolRouteTracker;
import com.example.patrol.PatrolRoute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal screen for creating and editing customizable patrol routes.
 * Features:
 * - Hex RGB color picker input with live preview swatch box.
 * - Quick-select preset color palette swatches.
 * - Route name editing and sanitization.
 * - Patrol traversal mode toggle (Loop vs Ping-Pong).
 * - Deletion support for existing routes.
 */
public class PatrolRouteEditModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 300;
	public static final int WINDOW_HEIGHT = 240;

	public static final int[] PRESET_COLORS = {
		0xFFD700, // Gold
		0x00E5FF, // Cyan
		0x00FF66, // Emerald
		0xB300FF, // Arcane Purple
		0xFF2244, // Crimson
		0xFF8800, // Blaze Orange
		0x3366FF, // Royal Blue
		0xFF3399  // Hot Pink
	};

	public static final String[] PRESET_NAMES = {
		"Gold", "Cyan", "Emerald", "Purple", "Crimson", "Orange", "Blue", "Pink"
	};

	private final int routeId;
	private final boolean isNewRoute;
	private final Screen parentScreen;

	private String initialName;
	private int selectedColorRgb;
	private PatrolRoute.PatrolMode selectedMode;

	private TextFieldWidget nameField;
	private TextFieldWidget hexField;
	private final List<ButtonWidget> presetButtons = new ArrayList<>();
	private ButtonWidget modeToggleBtn;
	private ButtonWidget saveBtn;
	private ButtonWidget cancelBtn;
	private ButtonWidget deleteBtn;

	public PatrolRouteEditModalScreen(PatrolRoute route, boolean isNewRoute, Screen parentScreen) {
		super(Text.translatable(isNewRoute ? "gui.modid-mmcli-agent-modding.patrol_route_modal.title_create" : "gui.modid-mmcli-agent-modding.patrol_route_modal.title_edit"));
		this.routeId = route != null ? route.routeId() : 0;
		this.isNewRoute = isNewRoute;
		this.parentScreen = parentScreen;
		this.initialName = route != null ? route.name() : ("Route " + (this.routeId + 1));
		this.selectedColorRgb = route != null ? route.colorRgb() : 0xFFD700;
		this.selectedMode = route != null ? route.patrolMode() : PatrolRoute.PatrolMode.LOOP;
	}

	public PatrolRouteEditModalScreen(int routeId, boolean isNewRoute, Screen parentScreen) {
		this(ClientPatrolRouteTracker.getRoute(routeId), isNewRoute, parentScreen);
	}

	public int getRouteId() {
		return this.routeId;
	}

	public boolean isNewRoute() {
		return this.isNewRoute;
	}

	public int getSelectedColorRgb() {
		return this.selectedColorRgb;
	}

	public String getRouteName() {
		return this.nameField != null ? this.nameField.getText().trim() : this.initialName;
	}

	public PatrolRoute.PatrolMode getSelectedMode() {
		return this.selectedMode;
	}

	@Override
	protected void init() {
		super.init();
		this.presetButtons.clear();

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		int fieldX = startX + 20;
		int fieldWidth = WINDOW_WIDTH - 40;

		// 1. Route Name Input Field
		this.nameField = new TextFieldWidget(this.textRenderer, fieldX, startY + 46, fieldWidth, 18, Text.literal("Route Name"));
		this.nameField.setMaxLength(24);
		this.nameField.setText(this.initialName);
		this.nameField.setChangedListener(text -> updateSaveButtonState());
		this.addSelectableChild(this.nameField);

		// 2. Hex Color Input Field + Swatch Box
		int hexFieldWidth = fieldWidth - 40;
		this.hexField = new TextFieldWidget(this.textRenderer, fieldX, startY + 86, hexFieldWidth, 18, Text.literal("Hex Code"));
		this.hexField.setMaxLength(7);
		this.hexField.setText(PatrolRoute.toHexCode(this.selectedColorRgb));
		this.hexField.setChangedListener(text -> {
			this.selectedColorRgb = PatrolRoute.parseHexColor(text);
			updateSaveButtonState();
		});
		this.addSelectableChild(this.hexField);

		// 3. Preset Color Swatch Buttons (8 Colors in a single row)
		int presetCount = PRESET_COLORS.length;
		int presetGap = 4;
		int presetWidth = (fieldWidth - (presetCount - 1) * presetGap) / presetCount;
		int presetHeight = 16;
		int presetY = startY + 126;

		for (int i = 0; i < presetCount; i++) {
			final int color = PRESET_COLORS[i];
			final String colorName = PRESET_NAMES[i];
			int bx = fieldX + i * (presetWidth + presetGap);

			ButtonWidget btn = ButtonWidget.builder(Text.literal("■"), b -> {
				this.selectedColorRgb = color;
				if (this.hexField != null) {
					this.hexField.setText(PatrolRoute.toHexCode(color));
				}
				updateSaveButtonState();
			})
			.dimensions(bx, presetY, presetWidth, presetHeight)
			.tooltip(Tooltip.of(Text.literal("§6Preset: §f" + colorName + "\n§7" + PatrolRoute.toHexCode(color))))
			.build();

			this.presetButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// 4. Patrol Mode Toggle Button
		this.modeToggleBtn = ButtonWidget.builder(
			getModeButtonText(),
			b -> toggleMode()
		)
		.dimensions(fieldX, startY + 162, fieldWidth, 20)
		.tooltip(Tooltip.of(Text.literal("§6✦ Patrol Mode\n§7Click to toggle between Closed Loop and Ping-Pong traversal.")))
		.build();
		this.addDrawableChild(this.modeToggleBtn);

		// 5. Bottom Action Buttons: Save, Cancel, and (optional) Delete
		int bottomY = startY + 204;
		if (!this.isNewRoute) {
			int btnW = (fieldWidth - 8) / 3;
			this.saveBtn = ButtonWidget.builder(Text.literal("§a✔ Save"), b -> saveAndClose())
				.dimensions(fieldX, bottomY, btnW, 20)
				.tooltip(Tooltip.of(Text.literal("§aSave route configuration and apply.")))
				.build();
			this.addDrawableChild(this.saveBtn);

			this.deleteBtn = ButtonWidget.builder(Text.literal("§c✕ Delete"), b -> deleteAndClose())
				.dimensions(fieldX + btnW + 4, bottomY, btnW, 20)
				.tooltip(Tooltip.of(Text.literal("§cDelete this patrol route and release assigned minions.")))
				.build();
			this.addDrawableChild(this.deleteBtn);

			this.cancelBtn = ButtonWidget.builder(Text.literal("Cancel"), b -> returnToParent())
				.dimensions(fieldX + (btnW + 4) * 2, bottomY, btnW, 20)
				.build();
			this.addDrawableChild(this.cancelBtn);
		} else {
			int btnW = (fieldWidth - 4) / 2;
			this.saveBtn = ButtonWidget.builder(Text.literal("§a✔ Create Route"), b -> saveAndClose())
				.dimensions(fieldX, bottomY, btnW, 20)
				.tooltip(Tooltip.of(Text.literal("§aCreate new patrol route.")))
				.build();
			this.addDrawableChild(this.saveBtn);

			this.cancelBtn = ButtonWidget.builder(Text.literal("Cancel"), b -> returnToParent())
				.dimensions(fieldX + btnW + 4, bottomY, btnW, 20)
				.build();
			this.addDrawableChild(this.cancelBtn);
		}

		updateSaveButtonState();
	}

	private Text getModeButtonText() {
		return Text.literal("§6Mode: §f" + (this.selectedMode == PatrolRoute.PatrolMode.LOOP ? "Loop 🔁" : "Ping-Pong 🏓"));
	}

	private void toggleMode() {
		this.selectedMode = this.selectedMode.toggle();
		if (this.modeToggleBtn != null) {
			this.modeToggleBtn.setMessage(getModeButtonText());
		}
	}

	private void updateSaveButtonState() {
		if (this.saveBtn != null) {
			String name = getRouteName();
			this.saveBtn.active = !name.isBlank();
		}
	}

	private void saveAndClose() {
		String finalName = getRouteName();
		if (finalName.isBlank()) {
			finalName = "Route " + (this.routeId + 1);
		}

		PatrolRoute existing = ClientPatrolRouteTracker.getRoute(this.routeId);
		List<net.minecraft.util.math.BlockPos> waypoints = existing != null ? existing.waypoints() : new ArrayList<>();
		PatrolRoute updated = new PatrolRoute(this.routeId, finalName, this.selectedColorRgb, waypoints, this.selectedMode);

		ClientPatrolRouteTracker.updateRoute(updated);
		ModClientNetworking.sendSavePatrolRoute(this.routeId, finalName, this.selectedColorRgb, this.selectedMode);

		MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
		if (mc != null && mc.world != null && mc.player != null) {
			mc.world.playSound(
				null,
				mc.player.getX(),
				mc.player.getY(),
				mc.player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
				SoundCategory.PLAYERS,
				1.0F,
				1.2F
			);
			mc.player.sendMessage(Text.literal("§6✦ Configured Patrol Route: §f" + finalName + " §8[" + PatrolRoute.toHexCode(this.selectedColorRgb) + "]§r"), true);
		}

		returnToParent();
	}

	private void deleteAndClose() {
		String finalName = getRouteName();
		ClientPatrolRouteTracker.removeRoute(this.routeId);
		ModClientNetworking.sendDeletePatrolRoute(this.routeId, finalName);

		MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
		if (mc != null && mc.world != null && mc.player != null) {
			mc.world.playSound(
				null,
				mc.player.getX(),
				mc.player.getY(),
				mc.player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
				SoundCategory.PLAYERS,
				0.9F,
				0.8F
			);
			mc.player.sendMessage(Text.literal("§c✦ Deleted Patrol Route: §f" + finalName + "§r"), true);
		}

		returnToParent();
	}

	private void returnToParent() {
		if (this.client != null) {
			if (this.parentScreen instanceof CommandScepterScreen commandScepterScreen) {
				commandScepterScreen.refreshButtonLabels();
				this.client.setScreen(commandScepterScreen);
			} else {
				this.client.setScreen(this.parentScreen);
			}
		}
	}

	@Override
	protected void applyBlur(float delta) {
		// Disable blur post-processing for crisp rendering
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Main background plate
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header plate
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 26, 0xDD1B2A3A);
		Text headerTitle = Text.literal(this.isNewRoute ? "§6✦ CREATE PATROL ROUTE ✦" : "§6✦ EDIT PATROL ROUTE ✦");
		context.drawCenteredTextWithShadow(this.textRenderer, headerTitle, this.width / 2, startY + 8, 0xFFFFFF);

		int fieldX = startX + 20;

		// Field Labels
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eRoute Name:"), fieldX, startY + 34, 0xFFD700);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eColor (Hex Code):"), fieldX, startY + 74, 0xFFD700);

		// Color Swatch Preview Box
		int swatchX = startX + WINDOW_WIDTH - 20 - 32;
		int swatchY = startY + 86;
		int swatchW = 32;
		int swatchH = 18;
		int displayColor = 0xFF000000 | (this.selectedColorRgb & 0xFFFFFF);
		context.fill(swatchX, swatchY, swatchX + swatchW, swatchY + swatchH, displayColor);
		context.drawBorder(swatchX, swatchY, swatchW, swatchH, 0xFFFFFFFF);

		// Presets Label
		context.drawTextWithShadow(this.textRenderer, Text.literal("§ePreset Swatches:"), fieldX, startY + 114, 0xFFD700);

		// Mode Label
		context.drawTextWithShadow(this.textRenderer, Text.literal("§ePatrol Behavior:"), fieldX, startY + 150, 0xFFD700);

		// Render text fields and widgets
		if (this.nameField != null) this.nameField.render(context, mouseX, mouseY, delta);
		if (this.hexField != null) this.hexField.render(context, mouseX, mouseY, delta);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			returnToParent();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.saveBtn != null && this.saveBtn.active) {
				saveAndClose();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		returnToParent();
	}
}
