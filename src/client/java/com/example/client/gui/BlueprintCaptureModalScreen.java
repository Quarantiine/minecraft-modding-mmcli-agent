package com.example.client.gui;

import com.example.blueprint.BuildingCategory;
import com.example.blueprint.StructureBlueprint;
import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.ClientDesignCaptureTracker;
import com.example.item.custom.CommandScepterItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal GUI screen prompting the user to name, describe, and categorize a custom blueprint
 * captured from an in-world spatial region in DESIGN mode (or compiled from structure geometry).
 *
 * Features:
 * - Real-time spatial dimension, coordinate, and voxel volume validation (<= 64x64x64, <= 65536 voxels).
 * - Blueprint name and description input with sanitization.
 * - Archetype categorization (Home, Watchtower, Barricade, Workshop, Supply Depot, Obelisk).
 * - Client-to-server compilation dispatch via {@link ModClientNetworking#sendCaptureSpatialBlueprint}.
 */
public class BlueprintCaptureModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 320;
	public static final int WINDOW_HEIGHT = 260;

	private BlockPos pos1;
	private BlockPos pos2;
	private final StructureBlueprint precompiledBlueprint;
	private final ItemStack scepterStack;
	private final Screen parentScreen;

	private TextFieldWidget nameField;
	private TextFieldWidget descField;
	private BuildingCategory selectedCategory = BuildingCategory.WATCHTOWER;
	private final List<ButtonWidget> categoryButtons = new ArrayList<>();
	private ButtonWidget saveButton;
	private ButtonWidget cancelButton;
	private ButtonWidget clearCornersButton;

	private boolean closed = false;
	private boolean initializedOpenState = false;

	public BlueprintCaptureModalScreen(BlockPos pos1, BlockPos pos2, StructureBlueprint precompiledBlueprint, ItemStack scepterStack, Screen parentScreen) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.capture_modal.title"));
		this.pos1 = pos1 != null ? pos1.toImmutable() : ClientDesignCaptureTracker.getPos1();
		this.pos2 = pos2 != null ? pos2.toImmutable() : ClientDesignCaptureTracker.getPos2();
		this.precompiledBlueprint = precompiledBlueprint;
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
	}

	public BlueprintCaptureModalScreen(BlockPos pos1, BlockPos pos2, ItemStack scepterStack, Screen parentScreen) {
		this(pos1, pos2, null, scepterStack, parentScreen);
	}

	public BlueprintCaptureModalScreen(StructureBlueprint blueprint, ItemStack scepterStack, Screen parentScreen) {
		this(null, null, blueprint, scepterStack, parentScreen);
	}

	public BlueprintCaptureModalScreen(ItemStack scepterStack, Screen parentScreen) {
		this(ClientDesignCaptureTracker.getPos1(), ClientDesignCaptureTracker.getPos2(), null, scepterStack, parentScreen);
	}

	public BlueprintCaptureModalScreen() {
		this(null, null, null, null, null);
	}

	public BlockPos getPos1() {
		return this.pos1;
	}

	public BlockPos getPos2() {
		return this.pos2;
	}

	public StructureBlueprint getPrecompiledBlueprint() {
		return this.precompiledBlueprint;
	}

	public BuildingCategory getSelectedCategory() {
		return this.selectedCategory;
	}

	public void setSelectedCategory(BuildingCategory category) {
		this.selectedCategory = category != null ? category : BuildingCategory.WATCHTOWER;
		refreshCategoryButtonStyles();
	}

	public String getBlueprintName() {
		return this.nameField != null ? this.nameField.getText().trim() : "";
	}

	public String getBlueprintDescription() {
		return this.descField != null ? this.descField.getText().trim() : "";
	}

	public boolean hasValidGeometry() {
		if (this.precompiledBlueprint != null) {
			return this.precompiledBlueprint.getBlockCount() > 0;
		}
		if (this.pos1 == null || this.pos2 == null) {
			return false;
		}
		int sx = Math.abs(this.pos1.getX() - this.pos2.getX()) + 1;
		int sy = Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
		int sz = Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
		long vol = (long) sx * sy * sz;
		return sx <= ClientDesignCaptureTracker.MAX_DIMENSION
			&& sy <= ClientDesignCaptureTracker.MAX_HEIGHT
			&& sz <= ClientDesignCaptureTracker.MAX_DIMENSION
			&& vol <= ClientDesignCaptureTracker.MAX_VOLUME;
	}

	public int getDimensionX() {
		if (this.precompiledBlueprint != null) return this.precompiledBlueprint.getSizeX();
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getX() - this.pos2.getX()) + 1;
	}

	public int getDimensionY() {
		if (this.precompiledBlueprint != null) return this.precompiledBlueprint.getSizeY();
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
	}

	public int getDimensionZ() {
		if (this.precompiledBlueprint != null) return this.precompiledBlueprint.getSizeZ();
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
	}

	public long getVolume() {
		if (this.precompiledBlueprint != null) return this.precompiledBlueprint.getBlockCount();
		if (this.pos1 == null || this.pos2 == null) return 0L;
		return (long) getDimensionX() * getDimensionY() * getDimensionZ();
	}

	/**
	 * Computes the exact number of non-air blocks enclosed within [pos1, pos2].
	 *
	 * @return Count of non-air structure blocks, or precompiled block count.
	 */
	public int getNonAirBlockCount() {
		if (this.precompiledBlueprint != null) {
			return this.precompiledBlueprint.getBlockCount();
		}
		if (this.pos1 == null || this.pos2 == null) {
			return 0;
		}
		MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
		if (mc != null && mc.world != null) {
			int minX = Math.min(this.pos1.getX(), this.pos2.getX());
			int maxX = Math.max(this.pos1.getX(), this.pos2.getX());
			int minY = Math.min(this.pos1.getY(), this.pos2.getY());
			int maxY = Math.max(this.pos1.getY(), this.pos2.getY());
			int minZ = Math.min(this.pos1.getZ(), this.pos2.getZ());
			int maxZ = Math.max(this.pos1.getZ(), this.pos2.getZ());
			int count = 0;
			BlockPos.Mutable m = new BlockPos.Mutable();
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {
						m.set(x, y, z);
						net.minecraft.block.BlockState state = mc.world.getBlockState(m);
						if (state != null && !state.isAir()) {
							count++;
						}
					}
				}
			}
			return count;
		}
		return (int) Math.min(Integer.MAX_VALUE, getVolume());
	}

	@Override
	protected void init() {
		super.init();
		this.categoryButtons.clear();

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// 1. Name text input field
		int fieldX = startX + 20;
		int fieldWidth = WINDOW_WIDTH - 40;
		this.nameField = new TextFieldWidget(this.textRenderer, fieldX, startY + 74, fieldWidth, 18, Text.literal("Blueprint Name"));
		this.nameField.setMaxLength(32);
		this.nameField.setText(this.precompiledBlueprint != null ? this.precompiledBlueprint.getName() : "Custom " + this.selectedCategory.getDisplayName().replace("🏡 ", "").replace("🗼 ", "").replace("🛡 ", "").replace("⚒ ", "").replace("📦 ", "").replace("🔮 ", ""));
		this.nameField.setChangedListener(text -> updateSaveButtonState());
		this.addSelectableChild(this.nameField);

		// 2. Description text input field (optional, empty by default)
		this.descField = new TextFieldWidget(this.textRenderer, fieldX, startY + 114, fieldWidth, 18, Text.literal("Description"));
		this.descField.setMaxLength(64);
		this.descField.setText("");
		this.addSelectableChild(this.descField);

		// 3. Category selector buttons (2 rows of 3 buttons)
		BuildingCategory[] categories = BuildingCategory.values();
		int catWidth = (fieldWidth - 8) / 3;
		int catHeight = 18;
		for (int i = 0; i < categories.length; i++) {
			BuildingCategory cat = categories[i];
			int row = i / 3;
			int col = i % 3;
			int bx = fieldX + col * (catWidth + 4);
			int by = startY + 154 + row * (catHeight + 4);

			ButtonWidget btn = ButtonWidget.builder(getCategoryButtonText(cat), b -> setSelectedCategory(cat))
				.dimensions(bx, by, catWidth, catHeight)
				.tooltip(Tooltip.of(Text.literal(cat.getDescription())))
				.build();
			this.categoryButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// 4. Action buttons at bottom tray
		int bottomY = startY + 224;
		int btnWidth = 88;

		this.saveButton = ButtonWidget.builder(Text.literal("§a✔ Save"), b -> saveAndCapture())
			.dimensions(startX + 20, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§aSave blueprint and compile into active catalog")))
			.build();
		this.addDrawableChild(this.saveButton);

		this.clearCornersButton = ButtonWidget.builder(Text.literal("§6⌫ Reset"), b -> clearSelection())
			.dimensions(startX + 116, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§6Clear Pos1 and Pos2 corner selections")))
			.build();
		this.addDrawableChild(this.clearCornersButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> this.close())
			.dimensions(startX + 212, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§cCancel and return")))
			.build();
		this.addDrawableChild(this.cancelButton);

		// 5. Height adjustment buttons in header info bar
		if (this.pos1 != null && this.pos2 != null) {
			int hBtnY = startY + 34;
			ButtonWidget minus5 = ButtonWidget.builder(Text.literal("§c-5"), b -> adjustModalHeight(-5))
				.dimensions(startX + WINDOW_WIDTH - 104, hBtnY, 22, 16)
				.tooltip(Tooltip.of(Text.literal("Decrease height by 5 blocks")))
				.build();
			ButtonWidget minus1 = ButtonWidget.builder(Text.literal("§c-1"), b -> adjustModalHeight(-1))
				.dimensions(startX + WINDOW_WIDTH - 80, hBtnY, 18, 16)
				.tooltip(Tooltip.of(Text.literal("Decrease height by 1 block")))
				.build();
			ButtonWidget plus1 = ButtonWidget.builder(Text.literal("§a+1"), b -> adjustModalHeight(1))
				.dimensions(startX + WINDOW_WIDTH - 60, hBtnY, 18, 16)
				.tooltip(Tooltip.of(Text.literal("Increase height by 1 block")))
				.build();
			ButtonWidget plus5 = ButtonWidget.builder(Text.literal("§a+5"), b -> adjustModalHeight(5))
				.dimensions(startX + WINDOW_WIDTH - 40, hBtnY, 22, 16)
				.tooltip(Tooltip.of(Text.literal("Increase height by 5 blocks")))
				.build();

			this.addDrawableChild(minus5);
			this.addDrawableChild(minus1);
			this.addDrawableChild(plus1);
			this.addDrawableChild(plus5);
		}

		updateSaveButtonState();
	}

	private void adjustModalHeight(int delta) {
		if (this.pos1 == null || this.pos2 == null) return;
		ClientDesignCaptureTracker.adjustHeight(delta);
		this.pos1 = ClientDesignCaptureTracker.getPos1();
		this.pos2 = ClientDesignCaptureTracker.getPos2();
		updateSaveButtonState();
	}

	private Text getCategoryButtonText(BuildingCategory cat) {
		boolean isSelected = cat == this.selectedCategory;
		String prefix = isSelected ? "§6▶ " : "§7";
		return Text.literal(prefix + cat.getDisplayName());
	}

	private void refreshCategoryButtonStyles() {
		BuildingCategory[] categories = BuildingCategory.values();
		for (int i = 0; i < categories.length && i < this.categoryButtons.size(); i++) {
			this.categoryButtons.get(i).setMessage(getCategoryButtonText(categories[i]));
		}
	}

	private void updateSaveButtonState() {
		if (this.saveButton != null) {
			String name = getBlueprintName();
			boolean validName = !name.isBlank();
			boolean validGeom = hasValidGeometry();
			this.saveButton.active = validName && validGeom;
		}
	}

	public void clearSelection() {
		ClientDesignCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearDesignCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearDesignCorners(held);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Cleared DESIGN corner selections (Pos1 & Pos2) - Ready for Pos1!§r"), true);
		}
		this.close();
	}

	public void saveAndCapture() {
		String rawName = getBlueprintName();
		if (rawName.isBlank()) {
			return;
		}

		String cleanName = rawName.trim();
		String rawDesc = getBlueprintDescription();
		String cleanDesc = (rawDesc != null && !rawDesc.isBlank()) ? rawDesc.trim() : "";
		String idSuffix = cleanName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
		if (idSuffix.isBlank()) {
			idSuffix = "blueprint";
		}
		String cleanId = "custom_" + this.selectedCategory.getId() + "_" + idSuffix + "_" + (System.currentTimeMillis() % 10000);

		if (this.precompiledBlueprint != null) {
			StructureBlueprint.Builder builder = StructureBlueprint.builder(cleanId, cleanName)
				.description(cleanDesc);
			for (com.example.blueprint.BlueprintBlock block : this.precompiledBlueprint.getBlocks()) {
				builder.addBlock(block.offset(), block.state());
			}
			StructureBlueprint compiled = builder.build();
			ModClientNetworking.sendCreateCustomBlueprint(compiled);
		} else if (this.pos1 != null && this.pos2 != null) {
			ModClientNetworking.sendCaptureSpatialBlueprint(cleanId, cleanName, cleanDesc, this.pos1, this.pos2);
		}

		// Update scepter to point to the newly captured blueprint
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBlueprintId(this.scepterStack, cleanId);
		}

		// Audio feedback
		if (this.client != null && this.client.world != null && this.client.player != null) {
			int capturedBlockCount = getNonAirBlockCount();
			this.client.world.playSound(
				null,
				this.client.player.getX(),
				this.client.player.getY(),
				this.client.player.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
				SoundCategory.PLAYERS,
				1.2F,
				1.4F
			);
			this.client.player.sendMessage(
				Text.literal("§d✦ Captured Blueprint: §b" + cleanName + " §8[" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ() + " §e" + capturedBlockCount + "b§8]§r"),
				true
			);
		}

		// Clear capture tracker and scepter corners, then close modal
		ClientDesignCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearDesignCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearDesignCorners(held);
			}
		}
		this.close();
	}

	@Override
	protected void applyBlur(float delta) {
		// Disable background world blur post-processing shader while keeping screen darkening and UI elements crisp
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Modal background plate & gold border
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Framed Header
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 28, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§d✦ CAPTURE CUSTOM BLUEPRINT ✦"), this.width / 2, startY + 6, 0xFFFFFF);

		Text escText = Text.literal("§e[Esc] §7Close");
		context.drawTextWithShadow(this.textRenderer, escText, startX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, startY + 6, 0xE0E0E0);

		// Spatial Dimension & Volume info bar
		int infoY = startY + 34;
		if (this.pos1 != null && this.pos2 != null) {
			String p1Str = "P1: [" + this.pos1.getX() + ", " + this.pos1.getY() + ", " + this.pos1.getZ() + "]";
			String p2Str = "P2: [" + this.pos2.getX() + ", " + this.pos2.getY() + ", " + this.pos2.getZ() + "]";
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + p1Str + "  " + p2Str), startX + 20, infoY, 0xAAAAAA);

			boolean valid = hasValidGeometry();
			int structureBlocks = getNonAirBlockCount();
			String geomText;
			if (!valid) {
				geomText = "§c⚠ Exceeds limit (max 64x96x64, 393216b)";
			} else if (structureBlocks == 0) {
				geomText = "§c⚠ 0 structure blocks in selection (all air voxels)";
			} else {
				geomText = "§aSize: §f" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ() + " §8| §e" + structureBlocks + " structure blocks §8(§7" + getVolume() + " voxels§8)";
			}
			context.drawTextWithShadow(this.textRenderer, Text.literal(geomText), startX + 20, infoY + 12, valid && structureBlocks > 0 ? 0x55FF55 : 0xFF5555);
		} else if (this.precompiledBlueprint != null) {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§aPrecompiled: §f" + this.precompiledBlueprint.getName()), startX + 20, infoY, 0x55FF55);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Blocks: §e" + this.precompiledBlueprint.getBlockCount() + "b §8| §7Size: §f" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ()), startX + 20, infoY + 12, 0xAAAAAA);
		} else {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§c⚠ Incomplete corner selection (Set Pos1 & Pos2)"), startX + 20, infoY, 0xFF5555);
		}

		// Input field labels
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eBlueprint Name:"), startX + 20, startY + 62, 0xFFD700);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eDescription: §7(Optional)"), startX + 20, startY + 102, 0xFFD700);
		context.drawTextWithShadow(this.textRenderer, Text.literal("§eArchetype Category:"), startX + 20, startY + 142, 0xFFD700);

		// Render text fields
		this.nameField.render(context, mouseX, mouseY, delta);
		this.descField.render(context, mouseX, mouseY, delta);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.nameField != null && this.nameField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				if (this.saveButton != null && this.saveButton.active) {
					saveAndCapture();
					return true;
				}
			}
			return this.nameField.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (this.descField != null && this.descField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				if (this.saveButton != null && this.saveButton.active) {
					saveAndCapture();
					return true;
				}
			}
			return this.descField.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		this.closed = true;
		if (this.client != null) {
			if (this.parentScreen != null) {
				this.client.setScreen(this.parentScreen);
			} else {
				super.close();
			}
		}
	}

	public boolean isClosed() {
		return this.closed;
	}

	public ButtonWidget getSaveButton() {
		return this.saveButton;
	}

	public ButtonWidget getCancelButton() {
		return this.cancelButton;
	}

	public ButtonWidget getClearCornersButton() {
		return this.clearCornersButton;
	}

	public List<ButtonWidget> getCategoryButtons() {
		return this.categoryButtons;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
