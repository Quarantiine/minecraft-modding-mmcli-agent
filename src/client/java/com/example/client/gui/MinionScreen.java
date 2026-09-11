package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.screen.MinionScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-side GUI screen for inspecting and managing minion equipment, inventory,
 * archetype role, and squad group assignment.
 * <p>
 * Features:
 * <ul>
 *   <li>Live 3D entity preview with status, role, squad, and combat statistics tooltip</li>
 *   <li>Interactive Role Cycling Button to switch archetype (Warrior, Sentinel, Builder, Miner, Ranger)</li>
 *   <li>Interactive Squad Cycling Button to assign tactical squad channel (Alpha, Bravo, Charlie, Delta)</li>
 *   <li>Equipment slots (Head, Chest, Legs, Feet, Mainhand, Offhand)</li>
 *   <li>3x3 Minion Inventory and player inventory/hotbar slots</li>
 *   <li>Action buttons for instant teleportation and minion dismissal</li>
 * </ul>
 */
public class MinionScreen extends HandledScreen<MinionScreenHandler> {

	private static final Identifier CONTAINER_TEXTURE = Identifier.ofVanilla("textures/gui/container/inventory.png");
	public static final int TOP_PANEL_HEIGHT = 24;
	public static final int BOTTOM_PANEL_HEIGHT = 24;
	public static final int TOTAL_MODAL_HEIGHT = 166 + TOP_PANEL_HEIGHT + BOTTOM_PANEL_HEIGHT; // 214px

	private MinionRole currentRole;
	private SquadGroup currentSquad;

	private CyclingButtonWidget<MinionRole> roleButton;
	private CyclingButtonWidget<SquadGroup> squadButton;
	private ButtonWidget teleportBtn;
	private ButtonWidget dismissBtn;

	public MinionScreen(MinionScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;

		MinionEntity minion = handler.getMinion();
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

	@Override
	protected void init() {
		super.init();

		// Calculate vertical placement dynamically so the full unified modal (top badges + inventory + action buttons)
		// remains perfectly centered and never clips off the top or bottom on any screen resolution or GUI scale
		int idealTopY = (this.height - TOTAL_MODAL_HEIGHT) / 2;
		int topY = Math.max(2, idealTopY);
		if (topY + TOTAL_MODAL_HEIGHT > this.height) {
			topY = Math.max(0, this.height - TOTAL_MODAL_HEIGHT);
		}
		this.y = topY + TOP_PANEL_HEIGHT;

		MinionEntity minion = this.handler.getMinion();
		boolean isOwner = minion != null && (minion.getOwner() == null || minion.isOwner(this.client.player));

		int btnWidth = 82;
		int btnHeight = 20;

		// Cycling badge widgets for Role and Squad positioned inside top header frame
		int badgeY = this.y - 22;

		// 1. Role Cycling Badge Widget (Warrior -> Sentinel -> Builder -> Miner -> Ranger)
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

		// Action buttons framed inside bottom panel
		int actionRowY = this.y + this.backgroundHeight + 2;

		// 3. Teleport to Me action button
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
		.dimensions(this.x + 4, actionRowY, btnWidth, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.teleport_desc")))
		.build();
		this.teleportBtn.active = isOwner;

		// 4. Dismiss Minion action button
		this.dismissBtn = ButtonWidget.builder(
			Text.literal("§c✖ ").append(Text.translatable("gui.modid-mmcli-agent-modding.minion.dismiss")),
			button -> {
				int id = this.resolveMinionId();
				if (id >= 0) {
					ModClientNetworking.sendDismissMinion(id);
				} else {
					ModClientNetworking.sendDismissAllMinions();
				}
				this.close();
			}
		)
		.dimensions(this.x + 90, actionRowY, btnWidth, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.dismiss_desc")))
		.build();
		this.dismissBtn.active = isOwner;

		this.addDrawableChild(this.roleButton);
		this.addDrawableChild(this.squadButton);
		this.addDrawableChild(this.teleportBtn);
		this.addDrawableChild(this.dismissBtn);
	}

	private int resolveMinionId() {
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
		MinionEntity minion = this.handler.getMinion();
		if (minion != null) {
			minion.setRole(this.currentRole);
			minion.setSquad(this.currentSquad);
		}
	}

	private static String getRoleBadgeSymbol(MinionRole role) {
		return switch (role) {
			case WARRIOR -> "⚔";
			case SENTINEL -> "🛡";
			case BUILDER -> "🔨";
			case MINER -> "⛏";
			case RANGER -> "🏹";
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
			case WARRIOR -> "§7Frontline shock infantry engaging enemies in melee.\n§8• Target Range: 24 blocks | Melee Sprint: +35%\n§8• Equipment: Swords, Axes, Maces";
			case SENTINEL -> "§7Perimeter guard holding station & intercepting hostiles.\n§8• Perimeter: 8 blocks | Anchor Leash: 12 blocks\n§8• Equipment: Shields (offhand) & Melee weapons";
			case BUILDER -> "§7Architectural constructor building blueprint structures.\n§8• Scaffolding navigation & structural placement\n§8• Equipment: Scaffolding, Blueprint blocks & Pickaxes";
			case MINER -> "§7Resource excavator and subsurface mining specialist.\n§8• Vein mining & automated block gathering\n§8• Equipment: Pickaxes & Shovels";
			case RANGER -> "§7Ranged artillery skirmisher with bows & crossbows.\n§8• Sweet Spot: 8-16 blocks | Dynamic strafing\n§8• Equipment: Bows & Crossbows";
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
		return Text.literal("§6✦ Squad Channel: " + squad.getFormattedName() + "\n§7" + desc + "\n§8• Commands: Follow, Stay, Attack, Mine, Build\n§eClick or scroll to cycle squad.");
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

		// Real-time dynamic accent trims under Role and Squad badge widgets
		context.fill(startX + 4, startY - 2, startX + 86, startY, roleColor);
		context.fill(startX + 90, startY - 2, startX + 172, startY, squadColor);

		// 1. Draw standard container background frame (contains player inventory and hotbar slots)
		context.drawTexture(CONTAINER_TEXTURE, startX, startY, 0, 0, this.backgroundWidth, this.backgroundHeight);

		// 2. Clear upper minion section to clean container gray
		context.fill(startX + 7, startY + 16, startX + 169, startY + 75, 0xFFC6C6C6);

		// 3. Draw slot frames for equipment (slots 0..5) and minion inventory (slots 6..14)
		for (int i = 0; i < MinionScreenHandler.MINION_INV_END; i++) {
			Slot slot = this.handler.getSlot(i);
			context.drawTexture(CONTAINER_TEXTURE, startX + slot.x - 1, startY + slot.y - 1, 7, 83, 18, 18);
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

		// Action accent trims
		context.fill(startX + 4, bottomPanelY, startX + 86, bottomPanelY + 2, 0xFFE040FB);
		context.fill(startX + 90, bottomPanelY, startX + 172, bottomPanelY + 2, 0xFFFF5252);

		MinionEntity minion = this.handler.getMinion();
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
		// Category headers
		context.drawText(this.textRenderer, Text.translatable("gui.modid-mmcli-agent-modding.minion.equipment"), 8, 6, 0x404040, false);
		context.drawText(this.textRenderer, Text.translatable("gui.modid-mmcli-agent-modding.minion.inventory"), 116, 6, 0x404040, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);

		// Center Minion title
		MinionEntity minion = this.handler.getMinion();
		Text nameText = (minion != null && minion.hasCustomName()) ? minion.getCustomName() : this.title;
		int nameWidth = this.textRenderer.getWidth(nameText);
		int nameX = 47 + (66 - nameWidth) / 2;
		context.drawText(this.textRenderer, nameText, Math.max(46, nameX), 6, 0x222222, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Synchronize state if minion data tracker changed externally
		MinionEntity minion = this.handler.getMinion();
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
			if (minion.isSitting()) {
				tooltipList.add(Text.literal("§eStatus: §7Holding Position"));
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
}
