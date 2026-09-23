package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.ClientMiningCaptureTracker;
import com.example.item.custom.CommandScepterItem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Client modal GUI screen prompting the commander to confirm, inspect, adjust height,
 * or reset the 3D spatial mining / quarry excavation area before initiating minion demolition.
 *
 * Features:
 * - Real-time 3D spatial dimension, coordinate, and voxel volume validation (<= 64x96x64, <= 393216 voxels).
 * - Real-time destructible block scan excluding bedrock and air.
 * - Quick-step vertical height adjustment controls ([-5], [-1], [+1], [+5]).
 * - Actions: [Start Mining], [Reset Corners], [Cancel].
 * - Background blur suppression to preserve battlefield tactical visibility.
 */
public class MiningConfirmModalScreen extends Screen {

	public static final int WINDOW_WIDTH = 300;
	public static final int WINDOW_HEIGHT = 170;

	private BlockPos pos1;
	private BlockPos pos2;
	private final ItemStack scepterStack;
	private final Screen parentScreen;

	private ButtonWidget startButton;
	private ButtonWidget resetButton;
	private ButtonWidget cancelButton;

	private boolean closed = false;

	public MiningConfirmModalScreen(BlockPos pos1, BlockPos pos2, ItemStack scepterStack, Screen parentScreen) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.mining_confirm_modal.title"));
		this.pos1 = pos1 != null ? pos1.toImmutable() : ClientMiningCaptureTracker.getPos1();
		this.pos2 = pos2 != null ? pos2.toImmutable() : ClientMiningCaptureTracker.getPos2();
		this.scepterStack = scepterStack;
		this.parentScreen = parentScreen;
	}

	public MiningConfirmModalScreen(ItemStack scepterStack, Screen parentScreen) {
		this(ClientMiningCaptureTracker.getPos1(), ClientMiningCaptureTracker.getPos2(), scepterStack, parentScreen);
	}

	public MiningConfirmModalScreen() {
		this(null, null, null, null);
	}

	public BlockPos getPos1() {
		return this.pos1;
	}

	public BlockPos getPos2() {
		return this.pos2;
	}

	public boolean hasValidGeometry() {
		if (this.pos1 == null || this.pos2 == null) {
			return false;
		}
		int sx = Math.abs(this.pos1.getX() - this.pos2.getX()) + 1;
		int sy = Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
		int sz = Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
		long vol = (long) sx * sy * sz;
		return sx <= ClientMiningCaptureTracker.MAX_DIMENSION
			&& sy <= ClientMiningCaptureTracker.MAX_HEIGHT
			&& sz <= ClientMiningCaptureTracker.MAX_DIMENSION
			&& vol <= ClientMiningCaptureTracker.MAX_VOLUME;
	}

	public int getDimensionX() {
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getX() - this.pos2.getX()) + 1;
	}

	public int getDimensionY() {
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
	}

	public int getDimensionZ() {
		if (this.pos1 == null || this.pos2 == null) return 0;
		return Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
	}

	public long getVolume() {
		if (this.pos1 == null || this.pos2 == null) return 0L;
		return (long) getDimensionX() * getDimensionY() * getDimensionZ();
	}

	/**
	 * Computes the exact number of destructible (non-air, hardness >= 0) blocks enclosed within [pos1, pos2].
	 *
	 * @return Count of destructible blocks in client world.
	 */
	public int getDestructibleBlockCount() {
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
						BlockState state = mc.world.getBlockState(m);
						if (state != null && !state.isAir()) {
							float hardness = state.getHardness(mc.world, m);
							if (hardness >= 0.0F) {
								count++;
							}
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

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// 1. Height adjustment buttons in dedicated row below spatial info
		if (this.pos1 != null && this.pos2 != null) {
			int hBtnY = startY + 64;
			ButtonWidget minus5 = ButtonWidget.builder(Text.literal("§c-5"), b -> adjustModalHeight(-5))
				.dimensions(startX + 104, hBtnY, 24, 18)
				.tooltip(Tooltip.of(Text.literal("Decrease height by 5 blocks")))
				.build();
			ButtonWidget minus1 = ButtonWidget.builder(Text.literal("§c-1"), b -> adjustModalHeight(-1))
				.dimensions(startX + 132, hBtnY, 20, 18)
				.tooltip(Tooltip.of(Text.literal("Decrease height by 1 block")))
				.build();
			ButtonWidget plus1 = ButtonWidget.builder(Text.literal("§a+1"), b -> adjustModalHeight(1))
				.dimensions(startX + 156, hBtnY, 20, 18)
				.tooltip(Tooltip.of(Text.literal("Increase height by 1 block")))
				.build();
			ButtonWidget plus5 = ButtonWidget.builder(Text.literal("§a+5"), b -> adjustModalHeight(5))
				.dimensions(startX + 180, hBtnY, 24, 18)
				.tooltip(Tooltip.of(Text.literal("Increase height by 5 blocks")))
				.build();

			this.addDrawableChild(minus5);
			this.addDrawableChild(minus1);
			this.addDrawableChild(plus1);
			this.addDrawableChild(plus5);
		}

		// 2. Action buttons at bottom tray
		int bottomY = startY + WINDOW_HEIGHT - 32;
		int btnWidth = 84;

		this.startButton = ButtonWidget.builder(Text.literal("§a✔ Mine Area"), b -> startMiningArea())
			.dimensions(startX + 16, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§aStart Top-to-Bottom 3D Quarry Mining Excavation")))
			.build();
		this.addDrawableChild(this.startButton);

		this.resetButton = ButtonWidget.builder(Text.literal("§6⌫ Reset"), b -> clearSelection())
			.dimensions(startX + 108, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§6Clear Pos1 and Pos2 corner selections")))
			.build();
		this.addDrawableChild(this.resetButton);

		this.cancelButton = ButtonWidget.builder(Text.literal("§c✖ Cancel"), b -> this.close())
			.dimensions(startX + 200, bottomY, btnWidth, 20)
			.tooltip(Tooltip.of(Text.literal("§cCancel and return")))
			.build();
		this.addDrawableChild(this.cancelButton);

		updateStartButtonState();
	}

	private void adjustModalHeight(int delta) {
		if (this.pos1 == null || this.pos2 == null) return;
		ClientMiningCaptureTracker.adjustHeight(delta);
		this.pos1 = ClientMiningCaptureTracker.getPos1();
		this.pos2 = ClientMiningCaptureTracker.getPos2();
		updateStartButtonState();
	}

	private void updateStartButtonState() {
		if (this.startButton != null) {
			this.startButton.active = hasValidGeometry();
		}
	}

	public void clearSelection() {
		ClientMiningCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearMineCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearMineCorners(held);
			}
			this.client.player.sendMessage(Text.literal("§6✦ Cleared MINE corner selections (Pos1 & Pos2) - Ready for Pos1!§r"), true);
		}
		this.close();
	}

	public void startMiningArea() {
		if (!hasValidGeometry() || this.pos1 == null || this.pos2 == null) {
			return;
		}

		BlockPos p1 = this.pos1;
		BlockPos p2 = this.pos2;

		// Dispatch C2S StartMiningAreaPayload
		ModClientNetworking.sendStartMiningArea(p1, p2);

		// Audio and chat feedback
		if (this.client != null && this.client.world != null && this.client.player != null) {
			int destructibleCount = getDestructibleBlockCount();
			this.client.world.playSound(
				null,
				this.client.player.getX(),
				this.client.player.getY(),
				this.client.player.getZ(),
				SoundEvents.BLOCK_ANVIL_USE,
				SoundCategory.PLAYERS,
				1.0F,
				1.2F
			);
			this.client.player.sendMessage(
				Text.literal("§6✦ Initiating Area Quarry Mining: §e" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ() + " §8(§f" + destructibleCount + " blocks§8)§r"),
				true
			);
		}

		// Clear local tracker and scepter corners, then close
		ClientMiningCaptureTracker.clear();
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.clearMineCorners(this.scepterStack);
		}
		if (this.client != null && this.client.player != null) {
			ItemStack held = CommandScepterItem.getHeldScepter(this.client.player);
			if (!held.isEmpty()) {
				CommandScepterItem.clearMineCorners(held);
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

		// Modal background plate & amber/gold border
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Framed Header
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 26, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§6✦ CONFIRM MINING AREA ✦"), this.width / 2, startY + 8, 0xFFFFFF);

		Text escText = Text.literal("§e[Esc] §7Close");
		context.drawTextWithShadow(this.textRenderer, escText, startX + WINDOW_WIDTH - this.textRenderer.getWidth(escText) - 10, startY + 8, 0xE0E0E0);

		// Spatial Dimension & Volume info bar
		if (this.pos1 != null && this.pos2 != null) {
			String p1Str = "P1: [" + this.pos1.getX() + ", " + this.pos1.getY() + ", " + this.pos1.getZ() + "]";
			String p2Str = "P2: [" + this.pos2.getX() + ", " + this.pos2.getY() + ", " + this.pos2.getZ() + "]";
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + p1Str + "  " + p2Str), startX + 16, startY + 34, 0xAAAAAA);

			boolean valid = hasValidGeometry();
			int destructibleBlocks = getDestructibleBlockCount();
			String geomText;
			if (!valid) {
				geomText = "§c⚠ Exceeds volume limits (max 64x96x64, 393216b)";
			} else if (destructibleBlocks == 0) {
				geomText = "§e⚠ 0 destructible blocks in selection (all air or bedrock)";
			} else {
				geomText = "§6Size: §f" + getDimensionX() + "x" + getDimensionY() + "x" + getDimensionZ() + " §8| §e" + destructibleBlocks + " blocks §8(§7" + getVolume() + "b§8)";
			}
			context.drawTextWithShadow(this.textRenderer, Text.literal(geomText), startX + 16, startY + 48, valid && destructibleBlocks > 0 ? 0xFFA500 : 0xFF5555);

			// Height adjustment label in dedicated row
			context.drawTextWithShadow(this.textRenderer, Text.literal("§eAdjust Height:"), startX + 16, startY + 68, 0xFFD700);

			// Instruction / Description lines
			context.drawTextWithShadow(this.textRenderer, Text.literal("§eExcavation Method: §fTop-to-Bottom Reverse Topological"), startX + 16, startY + 90, 0xFFD700);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Minions will systematically dismantle this 3D quarry."), startX + 16, startY + 104, 0xAAAAAA);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§8Bedrock & indestructible blocks are safeguarded."), startX + 16, startY + 118, 0x888888);
		} else {
			context.drawTextWithShadow(this.textRenderer, Text.literal("§c⚠ Incomplete corner selection (Set Pos1 & Pos2)"), startX + 16, startY + 34, 0xFF5555);
			context.drawTextWithShadow(this.textRenderer, Text.literal("§7Left-click blocks in AREA mode to set Pos1 and Pos2."), startX + 16, startY + 54, 0xAAAAAA);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.startButton != null && this.startButton.active) {
				startMiningArea();
				return true;
			}
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

	public ButtonWidget getStartButton() {
		return this.startButton;
	}

	public ButtonWidget getResetButton() {
		return this.resetButton;
	}

	public ButtonWidget getCancelButton() {
		return this.cancelButton;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
