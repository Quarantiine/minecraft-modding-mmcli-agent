package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.MinionOverheadBadgeFeatureRenderer;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.screen.MinionScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side GUI screen for inspecting and managing minion equipment, inventory,
 * archetype role, and squad group assignment.
 * <p>
 * Features:
 * <ul>
 *   <li>Live 3D entity preview with status, role, squad, and combat statistics tooltip</li>
 *   <li>Interactive Role Cycling Button to switch archetype (Warrior, Sentinel, Builder)</li>
 *   <li>Interactive Squad Cycling Button to assign tactical squad channel (Alpha, Bravo, Charlie, Delta)</li>
 *   <li>Equipment slots (Head, Chest, Legs, Feet, Mainhand, Offhand)</li>
 *   <li>3x3 Minion Inventory and player inventory/hotbar slots</li>
 *   <li>Action buttons for instant teleportation and minion dismissal</li>
 * </ul>
 */
public class MinionScreen extends HandledScreen<MinionScreenHandler> {

	private static final Identifier CONTAINER_TEXTURE = Identifier.ofVanilla("textures/gui/container/inventory.png");
	public static final int TOP_PANEL_HEIGHT = 36;
	public static final int BOTTOM_PANEL_HEIGHT = 46;
	public static final int TOTAL_MODAL_HEIGHT = 166 + TOP_PANEL_HEIGHT + BOTTOM_PANEL_HEIGHT; // 248px

	private MinionRole currentRole;
	private SquadGroup currentSquad;

	private CyclingButtonWidget<MinionRole> roleButton;
	private CyclingButtonWidget<SquadGroup> squadButton;
	private ButtonWidget teleportBtn;
	private ButtonWidget destroyBtn;
	private ButtonWidget cancelBtn;
	private boolean confirmingDestroy = false;

	// Smart Shift-to-close & interaction state controller (Refinement 4)
	private final SmartCloseHandler closeHandler = new SmartCloseHandler();

	public MinionScreen(MinionScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title != null ? title : Text.literal("Minion"));
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;

		MinionEntity minion = handler != null ? handler.getMinion() : null;
		if (minion != null) {
			this.currentRole = minion.getRole();
			this.currentSquad = minion.getSquad();
		} else {
			this.currentRole = MinionRole.WARRIOR;
			this.currentSquad = SquadGroup.ALPHA;
		}

		if (this.currentRole == null) {
			this.currentRole = MinionRole.WARRIOR;
		}
		if (this.currentSquad == null || this.currentSquad == SquadGroup.ALL) {
			this.currentSquad = SquadGroup.ALPHA;
		}
	}

	/**
	 * Factory helper to construct a standalone MinionScreen for testing or headless execution.
	 *
	 * @return A new MinionScreen instance with default test handlers.
	 */
	public static MinionScreen createForTest() {
		return new MinionScreen(null, null, Text.literal("Minion"));
	}

	@Override
	protected void init() {
		super.init();

		// Capture physical shift state upon opening to prevent immediate closure
		this.closeHandler.initOpenState(isShiftOrSneakDown());

		// Calculate vertical placement dynamically so the full unified modal (top badges + inventory + action buttons)
		// remains perfectly centered and never clips off the top or bottom on any screen resolution or GUI scale
		int idealTopY = (this.height - TOTAL_MODAL_HEIGHT) / 2;
		int topY = Math.max(2, idealTopY);
		if (topY + TOTAL_MODAL_HEIGHT > this.height) {
			topY = Math.max(0, this.height - TOTAL_MODAL_HEIGHT);
		}
		this.y = topY + TOP_PANEL_HEIGHT;

		MinionEntity minion = this.handler != null ? this.handler.getMinion() : null;
		boolean isOwner = isMinionOwner(minion);

		int btnWidth = 82;
		int btnHeight = 20;

		// Cycling badge widgets for Role and Squad positioned inside top header frame
		int badgeY = this.y - 22;

		// 1. Role Cycling Badge Widget (Warrior -> Sentinel -> Builder)
		this.roleButton = CyclingButtonWidget.<MinionRole>builder(role -> Text.literal(role.getColorCode() + getRoleBadgeSymbol(role) + " " + role.getDisplayName()))
			.values(MinionRole.values())
			.initially(this.currentRole)
			.omitKeyText()
			.tooltip(role -> Tooltip.of(getRoleTooltip(role)))
			.build(
				this.x + 4,
				badgeY,
				btnWidth,
				btnHeight,
				Text.literal("Role"),
				(button, newRole) -> {
					this.currentRole = newRole;
					this.syncMinionConfig();
				}
			);
		this.roleButton.active = isOwner;

		// 2. Squad Cycling Badge Widget (Alpha -> Bravo -> Charlie -> Delta)
		this.squadButton = CyclingButtonWidget.<SquadGroup>builder(squad -> Text.literal(squad.getColorCode() + "⚑ " + formatSquadName(squad)))
			.values(SquadGroup.getSelectableSquads())
			.initially(this.currentSquad)
			.omitKeyText()
			.tooltip(squad -> Tooltip.of(getSquadTooltip(squad)))
			.build(
				this.x + 90,
				badgeY,
				btnWidth,
				btnHeight,
				Text.literal("Squad"),
				(button, newSquad) -> {
					this.currentSquad = newSquad;
					this.syncMinionConfig();
				}
			);
		this.squadButton.active = isOwner;

		// Action buttons framed inside bottom panel (2 rows)
		int actionRow1Y = this.y + this.backgroundHeight + 3;
		int actionRow2Y = this.y + this.backgroundHeight + 24;

		// 3. Teleport to Me action button (Row 1, full width 168px)
		this.teleportBtn = ButtonWidget.builder(
			Text.literal("§d✦ ").append(Text.translatable("gui.modid-mmcli-agent-modding.minion.teleport")),
			button -> {
				int id = this.resolveMinionId();
				if (id >= 0) {
					ModClientNetworking.sendTeleportMinion(id);
				} else {
					ModClientNetworking.sendTeleportAllMinions();
				}
				this.close();
			}
		)
		.dimensions(this.x + 4, actionRow1Y, 168, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.teleport_desc")))
		.build();
		this.teleportBtn.active = isOwner;

		// 4. Destroy Minion action button (Row 2, left, 82px)
		this.destroyBtn = ButtonWidget.builder(
			Text.literal("§c✖ ").append(Text.translatable("gui.modid-mmcli-agent-modding.minion.destroy")),
			button -> {
				if (!this.confirmingDestroy) {
					this.confirmingDestroy = true;
					this.destroyBtn.setMessage(Text.literal("§c⚠ Confirm?"));
					this.destroyBtn.setTooltip(Tooltip.of(Text.literal("§cClick again to confirm decommission.")));
					return;
				}
				int id = this.resolveMinionId();
				if (id >= 0) {
					ModClientNetworking.sendDismissMinion(id);
				} else {
					ModClientNetworking.sendDismissAllMinions();
				}
				this.close();
			}
		)
		.dimensions(this.x + 4, actionRow2Y, btnWidth, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.destroy_desc")))
		.build();
		this.destroyBtn.active = isOwner;

		// 5. Cancel action button (Row 2, right, 82px)
		this.cancelBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.minion.cancel"),
			button -> this.close()
		)
		.dimensions(this.x + 90, actionRow2Y, btnWidth, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.cancel_desc")))
		.build();
		this.cancelBtn.active = true;

		this.addDrawableChild(this.roleButton);
		this.addDrawableChild(this.squadButton);
		this.addDrawableChild(this.teleportBtn);
		this.addDrawableChild(this.destroyBtn);
		this.addDrawableChild(this.cancelBtn);
	}

	private int resolveMinionId() {
		if (this.handler == null) {
			return -1;
		}
		int id = this.handler.getMinionId();
		if (id < 0 && this.handler.getMinion() != null) {
			return this.handler.getMinion().getId();
		}
		return id;
	}

	private void syncMinionConfig() {
		int id = this.resolveMinionId();
		if (id >= 0) {
			ModClientNetworking.sendUpdateMinionConfig(id, this.currentRole, this.currentSquad);
		}
		if (this.handler != null) {
			MinionEntity minion = this.handler.getMinion();
			if (minion != null) {
				minion.setRole(this.currentRole);
				minion.setSquad(this.currentSquad);
			}
		}
	}

	/**
	 * Evaluates whether a minion is considered owned under client-safe singleplayer bypass rules.
	 *
	 * @param inSingleplayer Whether the client is currently in a local singleplayer world.
	 * @param isTamed Whether the target minion is tamed.
	 * @param isOwnerEntity Whether the minion recognizes the local player entity as its owner.
	 * @param hasOwnerUuid Whether the minion currently has a recorded owner UUID.
	 * @return True if authorized as owner, false otherwise.
	 */
	public static boolean evaluateOwnership(boolean inSingleplayer, boolean isTamed, boolean isOwnerEntity, boolean hasOwnerUuid) {
		if (!hasOwnerUuid) {
			return true;
		}
		if (inSingleplayer && isTamed) {
			return true;
		}
		return isOwnerEntity;
	}

	/**
	 * Evaluates whether the local player is authorized as the owner of the minion.
	 * Incorporates client-safe singleplayer bypass checks (Refinement 1) so that the host
	 * of a local singleplayer world can always configure and command tamed minions even
	 * if player UUIDs shift across offline/development sessions.
	 *
	 * @param minion The target minion entity, or null.
	 * @return True if the minion is owned by the local player, unowned, or in singleplayer mode.
	 */
	public boolean isMinionOwner(MinionEntity minion) {
		if (minion == null) {
			return false;
		}
		boolean hasOwnerUuid = minion.getOwnerUuid() != null;
		boolean inSingleplayer = this.client != null && this.client.isInSingleplayer();
		boolean isTamed = minion.isTamed();
		boolean isOwnerEntity = this.client != null && this.client.player != null && minion.isOwner(this.client.player);
		return evaluateOwnership(inSingleplayer, isTamed, isOwnerEntity, hasOwnerUuid);
	}

	private static String getRoleBadgeSymbol(MinionRole role) {
		return switch (role) {
			case WARRIOR -> "⚔";
			case SENTINEL -> "🛡";
			case BUILDER -> "🔨";
			case AUTO -> "⚙";
		};
	}

	private static String formatSquadName(SquadGroup squad) {
		String name = squad.asString();
		if (name.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static Text getRoleTooltip(MinionRole role) {
		String desc = switch (role) {
			case WARRIOR -> "§7Versatile combatant engaging in melee or ranged combat.\n§8• Swordsman or Archer based on equipped weapon\n§8• Equipment: Swords, Axes, Maces, Bows & Crossbows";
			case SENTINEL -> "§7Perimeter guard holding station & intercepting hostiles.\n§8• Perimeter: 8 blocks | Anchor Leash: 128 blocks\n§8• Equipment: Shields (offhand) & Melee weapons";
			case BUILDER -> "§7Architectural constructor, miner & resource logistics specialist.\n§8• Blueprint building, vein mining & autonomous harvesting\n§8• Equipment: Blueprint blocks, Pickaxes, Axes & Shovels";
			case AUTO -> "§7Autonomous tactical agent adapting dynamically.\n§8• Sentinel medic when allies < 70% HP\n§8• Warrior vanguard when threats near, Builder during construction";
		};
		return Text.literal("§6✦ Role Archetype: " + role.getFormattedName() + "\n" + desc + "\n§eClick or scroll to cycle role.");
	}

	private static Text getSquadTooltip(SquadGroup squad) {
		String desc = switch (squad) {
			case ALL -> "All squads channel (Wildcard).";
			case ALPHA -> "§cRed Banner Division - Frontline Assault";
			case BRAVO -> "§9Blue Banner Division - Vanguard Legion";
			case CHARLIE -> "§aGreen Banner Division - Perimeter Sentinels";
			case DELTA -> "§6Gold Banner Division - Heavy Artillery";
		};
		return Text.literal("§6✦ Squad Channel: " + squad.getFormattedName() + "\n§7" + desc + "\n§8• Commands: Follow, Stay, Mine, Build, Recruit\n§eClick or scroll to cycle squad.");
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int startX = this.x;
		int startY = this.y;

		int squadColor = (this.currentSquad != null && this.currentSquad.getFormatting().getColorValue() != null)
			? (0xFF000000 | this.currentSquad.getFormatting().getColorValue())
			: 0xFF3D4F66;
		int roleColor = (this.currentRole != null && this.currentRole.getFormatting().getColorValue() != null)
			? (0xFF000000 | this.currentRole.getFormatting().getColorValue())
			: 0xFFFFD700;

		// 0. Draw unified top header frame spanning full container width (176px)
		context.fill(startX, startY - TOP_PANEL_HEIGHT, startX + this.backgroundWidth, startY, 0xEE141923);
		context.drawBorder(startX, startY - TOP_PANEL_HEIGHT, this.backgroundWidth, TOP_PANEL_HEIGHT, squadColor);

		// Top header title and subtle [Shift] Close indicator
		if (this.textRenderer != null) {
			Text headerTitle = Text.literal("§6✦ MINION COMMAND ✦");
			context.drawTextWithShadow(this.textRenderer, headerTitle, startX + 4, startY - TOP_PANEL_HEIGHT + 3, 0xFFFFFF);

			Text shiftCloseText = Text.literal("§e[Shift] §7Close");
			int shiftCloseWidth = this.textRenderer.getWidth(shiftCloseText);
			context.drawTextWithShadow(this.textRenderer, shiftCloseText, startX + this.backgroundWidth - shiftCloseWidth - 4, startY - TOP_PANEL_HEIGHT + 3, 0xE0E0E0);
		}

		// Subtle header separator divider above role/squad badges
		context.fill(startX + 2, startY - 24, startX + this.backgroundWidth - 2, startY - 23, 0x443D4F66);

		// Real-time dynamic accent trims under Role and Squad badge widgets
		context.fill(startX + 4, startY - 2, startX + 86, startY, roleColor);
		context.fill(startX + 90, startY - 2, startX + 172, startY, squadColor);

		// 1. Draw standard container background frame (contains player inventory and hotbar slots)
		context.drawTexture(CONTAINER_TEXTURE, startX, startY, 0, 0, this.backgroundWidth, this.backgroundHeight);

		// 2. Clear upper minion section to clean container gray
		context.fill(startX + 7, startY + 16, startX + 169, startY + 75, 0xFFC6C6C6);

		// 3. Draw slot frames for equipment (slots 0..5) and minion inventory (slots 6..14)
		if (this.handler != null) {
			for (int i = 0; i < MinionScreenHandler.MINION_INV_END; i++) {
				Slot slot = this.handler.getSlot(i);
				context.drawTexture(CONTAINER_TEXTURE, startX + slot.x - 1, startY + slot.y - 1, 7, 83, 18, 18);
			}
		}

		// 4. Center 3D entity preview frame with real-time dynamic squad & role color trims
		int previewLeft = startX + 47;
		int previewTop = startY + 17;
		int previewRight = startX + 113;
		int previewBottom = startY + 73;

		context.fill(previewLeft, previewTop, previewRight, previewBottom, 0xEE141923);
		context.drawBorder(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop, squadColor);

		// Real-time dynamic accent trims (top/bottom squad trim + corner role accents)
		context.fill(previewLeft + 1, previewTop + 1, previewRight - 1, previewTop + 3, squadColor);
		context.fill(previewLeft + 1, previewBottom - 3, previewRight - 1, previewBottom - 1, squadColor);
		context.fill(previewLeft, previewTop, previewLeft + 4, previewTop + 4, roleColor);
		context.fill(previewRight - 4, previewTop, previewRight, previewTop + 4, roleColor);
		context.fill(previewLeft, previewBottom - 4, previewLeft + 4, previewBottom, roleColor);
		context.fill(previewRight - 4, previewBottom - 4, previewRight, previewBottom, roleColor);

		// 5. Draw unified bottom action frame spanning full container width (176px)
		int bottomPanelY = startY + this.backgroundHeight;
		context.fill(startX, bottomPanelY, startX + this.backgroundWidth, bottomPanelY + BOTTOM_PANEL_HEIGHT, 0xEE141923);
		context.drawBorder(startX, bottomPanelY, this.backgroundWidth, BOTTOM_PANEL_HEIGHT, squadColor);

		// Action accent trims for the 2 button rows
		context.fill(startX + 4, bottomPanelY, startX + 172, bottomPanelY + 2, 0xFFE040FB); // Teleport top trim
		context.fill(startX + 4, bottomPanelY + 22, startX + 86, bottomPanelY + 24, 0xFFFF5252); // Destroy top trim (left)
		context.fill(startX + 90, bottomPanelY + 22, startX + 172, bottomPanelY + 24, 0xFF9E9E9E); // Cancel top trim (right)

		MinionEntity minion = this.handler != null ? this.handler.getMinion() : null;
		if (minion != null) {
			InventoryScreen.drawEntity(
				context,
				previewLeft + 1,
				previewTop + 1,
				previewRight - 1,
				previewBottom - 1,
				26,
				0.0625F,
				(float) mouseX,
				(float) mouseY,
				minion
			);

			// Prominently display the hearts health indicator at the bottom of the central 3D preview panel
			int healthPlateTop = previewBottom - 12;
			int healthPlateBottom = previewBottom - 1;
			context.fill(previewLeft + 1, healthPlateTop, previewRight - 1, healthPlateBottom, 0xDD101520);
			context.fill(previewLeft + 1, healthPlateTop, previewRight - 1, healthPlateTop + 1, squadColor);

			Text healthDisplay = MinionOverheadBadgeFeatureRenderer.getHealthDisplay(minion.getHealth(), minion.getMaxHealth());
			float healthTextWidth = this.textRenderer.getWidth(healthDisplay);
			float maxPlateWidth = (float) (previewRight - previewLeft - 4); // 62px
			float healthScale = healthTextWidth > maxPlateWidth ? (maxPlateWidth / healthTextWidth) : 1.0F;

			context.getMatrices().push();
			float centerX = (previewLeft + previewRight) / 2.0F;
			float textY = healthPlateTop + (11 - 8 * healthScale) / 2.0F;
			context.getMatrices().translate(centerX, textY, 0.0F);
			context.getMatrices().scale(healthScale, healthScale, 1.0F);
			context.drawCenteredTextWithShadow(this.textRenderer, healthDisplay, 0, 0, 0xFFFFFF);
			context.getMatrices().pop();
		} else {
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("§8Minion"),
				startX + 80,
				startY + 40,
				0x888888
			);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// Category header: bold, high-contrast Inventory label
		context.drawText(this.textRenderer, Text.literal("§8§l").append(Text.translatable("gui.modid-mmcli-agent-modding.minion.inventory")), 116, 5, 0x1E293B, false);
	}

	@Override
	protected void applyBlur(float delta) {
		// Disable background world blur post-processing shader while keeping screen darkening and UI elements crisp
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Zero-latency release transition check: clear open-state guard as soon as physical shift is released
		this.closeHandler.tickOrRender(isShiftOrSneakDown());

		// Synchronize state if minion data tracker changed externally
		MinionEntity minion = this.handler != null ? this.handler.getMinion() : null;
		if (minion != null) {
			MinionRole entityRole = minion.getRole();
			if (entityRole != null && entityRole != this.currentRole && this.roleButton != null) {
				this.currentRole = entityRole;
				this.roleButton.setValue(entityRole);
			}

			SquadGroup entitySquad = minion.getSquad();
			if (entitySquad != null && entitySquad != this.currentSquad && entitySquad != SquadGroup.ALL && this.squadButton != null) {
				this.currentSquad = entitySquad;
				this.squadButton.setValue(entitySquad);
			}
		}

		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);

		// Minion status tooltip when hovering over entity preview (refreshed in real-time)
		if (minion != null && mouseX >= this.x + 47 && mouseX <= this.x + 113 && mouseY >= this.y + 17 && mouseY <= this.y + 73) {
			List<Text> tooltipList = new ArrayList<>();
			tooltipList.add(Text.literal("§6✦ " + minion.getName().getString()));
			tooltipList.add(Text.literal("§7Role: " + this.currentRole.getFormattedName() + " §8[" + getRoleBadgeSymbol(this.currentRole) + "§8]"));
			tooltipList.add(Text.literal("§7Squad: " + this.currentSquad.getFormattedName() + " §8[⚑]"));
			tooltipList.add(Text.literal("§c❤ Health: §f" + String.format("%.1f", minion.getHealth()) + " / " + String.format("%.1f", minion.getMaxHealth())));
			tooltipList.add(Text.literal("§b🛡 Armor: §f" + minion.getArmor()));
			if (minion.isHoldingPosition()) {
				tooltipList.add(Text.literal("§eStatus: §7Holding Position (Stationed)"));
			} else {
				tooltipList.add(Text.literal("§aStatus: §7Guarding / Following Master"));
			}
			tooltipList.add(Text.literal("§8Click badges above to cycle role / squad."));
			context.drawTooltip(this.textRenderer, tooltipList, mouseX, mouseY);
		}
	}

	@Override
	protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
		// Encompass top badges and bottom action bar within the container bounds so clicks never drop items
		return mouseX < (double) left || mouseX >= (double) (left + this.backgroundWidth)
			|| mouseY < (double) (top - TOP_PANEL_HEIGHT) || mouseY >= (double) (top + this.backgroundHeight + BOTTOM_PANEL_HEIGHT);
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		// Periodic zero-latency fallback to clear shiftHeldOnOpen
		this.closeHandler.tickOrRender(isShiftOrSneakDown());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Refinement 4: When holding Shift while clicking on slots/widgets, latch the transfer flag
		// so that releasing Shift afterwards will NOT accidentally close the screen
		this.closeHandler.onMouseClicked(isShiftOrSneakDown());
		if (this.client == null) {
			return false;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean isShift = isShiftOrSneakKey(keyCode, scanCode);
		boolean isInv = isInventoryKey(keyCode, scanCode);
		boolean isEsc = (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc());

		if (this.closeHandler.onKeyPressed(keyCode, scanCode, isShift, isInv, isEsc, this::close)) {
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		boolean isShift = isShiftOrSneakKey(keyCode, scanCode);
		boolean isShiftDown = isShiftOrSneakDown();

		if (this.closeHandler.onKeyReleased(keyCode, scanCode, isShift, isShiftDown, this::close)) {
			return true;
		}

		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		this.closeHandler.setClosed(true);
		if (this.client != null) {
			super.close();
		}
	}

	/**
	 * Checks whether Left Shift, Right Shift, or the configured sneak key is physically held down.
	 *
	 * @return True if a shift or sneak key is physically pressed, false otherwise.
	 */
	public boolean isShiftOrSneakDown() {
		try {
			MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
			if (mc != null && mc.getWindow() != null) {
				long handle = mc.getWindow().getHandle();
				if (handle != 0L) {
					if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
						|| InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
						return true;
					}
					if (mc.options != null && mc.options.sneakKey != null) {
						InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(mc.options.sneakKey);
						if (boundKey != null && boundKey.getCategory() == InputUtil.Type.KEYSYM) {
							int code = boundKey.getCode();
							if (code > 0 && InputUtil.isKeyPressed(handle, code)) {
								return true;
							}
						}
					}
				}
			}
		} catch (Throwable ignored) {
			// Graceful fallback for headless or uninitialized test environments
		}
		return false;
	}

	/**
	 * Checks if the given GLFW keycode / scancode corresponds to Shift or the player's configured sneak key.
	 *
	 * @param keyCode GLFW keycode.
	 * @param scanCode Physical scancode.
	 * @return True if matching Left/Right Shift or Sneak.
	 */
	public boolean isShiftOrSneakKey(int keyCode, int scanCode) {
		if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			return true;
		}
		try {
			MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
			if (mc != null && mc.options != null && mc.options.sneakKey != null) {
				if (mc.options.sneakKey.matchesKey(keyCode, scanCode)) {
					return true;
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/**
	 * Checks if the given GLFW keycode / scancode corresponds to the Inventory key (default 'E').
	 *
	 * @param keyCode GLFW keycode.
	 * @param scanCode Physical scancode.
	 * @return True if matching inventory keybinding or GLFW_KEY_E.
	 */
	public boolean isInventoryKey(int keyCode, int scanCode) {
		try {
			MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
			if (mc != null && mc.options != null && mc.options.inventoryKey != null) {
				if (mc.options.inventoryKey.matchesKey(keyCode, scanCode)) {
					return true;
				}
			}
		} catch (Throwable ignored) {
		}
		return keyCode == GLFW.GLFW_KEY_E;
	}

	public SmartCloseHandler getCloseHandler() {
		return this.closeHandler;
	}

	public boolean isShiftHeldOnOpen() {
		return this.closeHandler.isShiftHeldOnOpen();
	}

	public void setShiftHeldOnOpen(boolean shiftHeldOnOpen) {
		this.closeHandler.setShiftHeldOnOpen(shiftHeldOnOpen);
	}

	public boolean isSlotClickedWithShift() {
		return this.closeHandler.isSlotClickedWithShift();
	}

	public void setSlotClickedWithShift(boolean slotClickedWithShift) {
		this.closeHandler.setSlotClickedWithShift(slotClickedWithShift);
	}

	public boolean isClosed() {
		return this.closeHandler.isClosed();
	}

	public void setClosed(boolean closed) {
		this.closeHandler.setClosed(closed);
	}

	public boolean isInitializedOpenState() {
		return this.closeHandler.isInitializedOpenState();
	}

	public void setInitializedOpenState(boolean initializedOpenState) {
		this.closeHandler.setInitializedOpenState(initializedOpenState);
	}

	/**
	 * State controller encapsulating the smart Shift-to-close state machine,
	 * open-state guards, GLFW key repeat absorption, and shift-click item transfer
	 * latching (Refinement 4).
	 */
	public static class SmartCloseHandler {
		private boolean shiftHeldOnOpen;
		private boolean slotClickedWithShift;
		private boolean initializedOpenState;
		private boolean closed;

		public SmartCloseHandler() {}

		public void initOpenState(boolean shiftOrSneakDown) {
			if (!this.initializedOpenState) {
				this.shiftHeldOnOpen = shiftOrSneakDown;
				this.initializedOpenState = true;
			}
		}

		public void tickOrRender(boolean shiftOrSneakDown) {
			if (this.shiftHeldOnOpen && !shiftOrSneakDown) {
				this.shiftHeldOnOpen = false;
			}
		}

		public void onMouseClicked(boolean shiftOrSneakDown) {
			if (shiftOrSneakDown) {
				this.slotClickedWithShift = true;
			}
		}

		public boolean onKeyPressed(int keyCode, int scanCode, boolean isShiftOrSneak, boolean isInventory, boolean isEscape, Runnable closeAction) {
			if (isShiftOrSneak) {
				// Absorb Shift key press events
				return true;
			}
			if (isInventory || isEscape) {
				this.closed = true;
				if (closeAction != null) {
					closeAction.run();
				}
				return true;
			}
			return false;
		}

		public boolean onKeyReleased(int keyCode, int scanCode, boolean isShiftOrSneak, boolean shiftOrSneakDown, Runnable closeAction) {
			if (isShiftOrSneak) {
				if (!this.slotClickedWithShift && !this.shiftHeldOnOpen) {
					this.closed = true;
					if (closeAction != null) {
						closeAction.run();
					}
					this.slotClickedWithShift = false;
					return true;
				}
				this.shiftHeldOnOpen = false;
				this.slotClickedWithShift = false;
			}
			if (this.shiftHeldOnOpen && !shiftOrSneakDown) {
				this.shiftHeldOnOpen = false;
			}
			return false;
		}

		public boolean isShiftHeldOnOpen() {
			return this.shiftHeldOnOpen;
		}

		public void setShiftHeldOnOpen(boolean shiftHeldOnOpen) {
			this.shiftHeldOnOpen = shiftHeldOnOpen;
			this.initializedOpenState = true;
		}

		public boolean isSlotClickedWithShift() {
			return this.slotClickedWithShift;
		}

		public void setSlotClickedWithShift(boolean slotClickedWithShift) {
			this.slotClickedWithShift = slotClickedWithShift;
		}

		public boolean isClosed() {
			return this.closed;
		}

		public void setClosed(boolean closed) {
			this.closed = closed;
		}

		public boolean isInitializedOpenState() {
			return this.initializedOpenState;
		}

		public void setInitializedOpenState(boolean initializedOpenState) {
			this.initializedOpenState = initializedOpenState;
		}
	}

	public ButtonWidget getTeleportButton() {
		return this.teleportBtn;
	}

	public ButtonWidget getDestroyButton() {
		return this.destroyBtn;
	}

	public ButtonWidget getCancelButton() {
		return this.cancelBtn;
	}
}
