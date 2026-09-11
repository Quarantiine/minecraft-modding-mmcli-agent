package com.example.client.gui;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.network.ModClientNetworking;
import com.example.component.CommandMode;
import com.example.entity.custom.MinionEntity;
import com.example.item.custom.CommandScepterItem;
import com.example.network.UpdateScepterPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;

/**
 * Interactive Client GUI for the Loki Command Scepter.
 * Allows the player to switch scepter operating modes, inspect and select
 * architectural blueprints from the catalog, observe nearby minion thralls,
 * and broadcast tactical directives to thralls via C2S packets.
 */
public class CommandScepterScreen extends Screen {

	private static final int WINDOW_WIDTH = 340;
	private static final int WINDOW_HEIGHT = 220;

	private final Hand hand;
	private final ItemStack scepterStack;
	private CommandMode selectedMode;
	private String selectedBlueprintId;
	private int nearbyThralls = 0;

	private final List<ButtonWidget> modeButtons = new ArrayList<>();
	private final List<ButtonWidget> blueprintButtons = new ArrayList<>();

	public CommandScepterScreen(Hand hand, ItemStack scepterStack) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.command_hub.title"));
		this.hand = hand;
		this.scepterStack = scepterStack;
		this.selectedMode = CommandScepterItem.getMode(scepterStack);
		this.selectedBlueprintId = CommandScepterItem.getBlueprintId(scepterStack);
	}

	@Override
	protected void init() {
		super.init();
		this.modeButtons.clear();
		this.blueprintButtons.clear();

		// Count nearby owned minions
		if (this.client != null && this.client.world != null && this.client.player != null) {
			this.nearbyThralls = this.client.world.getEntitiesByClass(
				MinionEntity.class,
				this.client.player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS),
				m -> m.isAlive() && m.isOwner(this.client.player)
			).size();
		}

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// 6 Command Mode Buttons in a 2-column grid
		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length; i++) {
			CommandMode mode = modes[i];
			int col = i % 2;
			int row = i / 2;
			int btnX = startX + 16 + col * 70;
			int btnY = startY + 54 + row * 24;

			ButtonWidget btn = ButtonWidget.builder(getModeButtonText(mode), b -> selectMode(mode))
				.dimensions(btnX, btnY, 66, 20)
				.build();

			this.modeButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// Architectural Blueprint Catalog
		List<StructureBlueprint> blueprints = new ArrayList<>(BlueprintRegistry.getAll());
		for (int i = 0; i < blueprints.size(); i++) {
			StructureBlueprint bp = blueprints.get(i);
			int btnX = startX + 165;
			int btnY = startY + 54 + i * 36;

			ButtonWidget btn = ButtonWidget.builder(getBlueprintButtonText(bp), b -> selectBlueprint(bp.getId()))
				.dimensions(btnX, btnY, 160, 30)
				.build();

			this.blueprintButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// Action Buttons: Execute Directive, Teleport Minions, Dismiss All Minions, & Close
		int bottomY = startY + 185;
		ButtonWidget executeBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.command_hub.execute"),
			b -> executeDirective()
		).dimensions(startX + 14, bottomY, 74, 20).build();
		this.addDrawableChild(executeBtn);

		ButtonWidget teleportBtn = ButtonWidget.builder(
			Text.literal("§d✦ ").append(Text.translatable("gui.modid-mmcli-agent-modding.command_hub.teleport_all")),
			b -> {
				ModClientNetworking.sendTeleportAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 92, bottomY, 78, 20)
		.tooltip(Tooltip.of(this.nearbyThralls > 0
			? Text.literal("§dTeleport all " + this.nearbyThralls + " nearby owned minion(s) to you")
			: Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_teleport")))
		.build();
		teleportBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(teleportBtn);

		ButtonWidget dismissBtn = ButtonWidget.builder(
			Text.literal("§c✖ ").append(Text.translatable("gui.modid-mmcli-agent-modding.command_hub.dismiss_all")),
			b -> {
				ModClientNetworking.sendDismissAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 174, bottomY, 78, 20)
		.tooltip(Tooltip.of(this.nearbyThralls > 0
			? Text.literal("§cDismiss all " + this.nearbyThralls + " nearby owned minion(s) and drop equipment")
			: Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_dismiss")))
		.build();
		dismissBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(dismissBtn);

		ButtonWidget closeBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.command_hub.close"),
			b -> this.close()
		).dimensions(startX + 256, bottomY, 70, 20).build();
		this.addDrawableChild(closeBtn);
	}

	private Text getModeButtonText(CommandMode mode) {
		boolean isSelected = mode == this.selectedMode;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + mode.getColorCode() + mode.getDisplayName());
	}

	private Text getBlueprintButtonText(StructureBlueprint bp) {
		boolean isSelected = bp.getId().equalsIgnoreCase(this.selectedBlueprintId);
		BlockBox box = bp.getBoundingBox();
		int dimX = box.getBlockCountX();
		int dimY = box.getBlockCountY();
		int dimZ = box.getBlockCountZ();

		String prefix = isSelected ? "§6✦ " : "§f";
		return Text.literal(prefix + bp.getName() + "\n§8" + dimX + "x" + dimY + "x" + dimZ + " §8| §e" + bp.getBlockCount() + "b");
	}

	private void selectMode(CommandMode mode) {
		this.selectedMode = mode;
		syncToServer(false);
		refreshButtonLabels();
	}

	private void selectBlueprint(String blueprintId) {
		this.selectedBlueprintId = blueprintId;
		syncToServer(false);
		refreshButtonLabels();
	}

	private void executeDirective() {
		syncToServer(true);
		this.close();
	}

	private void syncToServer(boolean executeDirective) {
		ModClientNetworking.sendUpdateScepter(this.selectedMode, this.selectedBlueprintId, executeDirective);
	}

	private void refreshButtonLabels() {
		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length && i < this.modeButtons.size(); i++) {
			this.modeButtons.get(i).setMessage(getModeButtonText(modes[i]));
		}

		List<StructureBlueprint> blueprints = new ArrayList<>(BlueprintRegistry.getAll());
		for (int i = 0; i < blueprints.size() && i < this.blueprintButtons.size(); i++) {
			this.blueprintButtons.get(i).setMessage(getBlueprintButtonText(blueprints.get(i)));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);

		int startX = (this.width - WINDOW_WIDTH) / 2;
		int startY = (this.height - WINDOW_HEIGHT) / 2;

		// Main window modal background
		context.fill(startX, startY, startX + WINDOW_WIDTH, startY + WINDOW_HEIGHT, 0xEE111822);
		context.drawBorder(startX, startY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFE2B007);

		// Header bar
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 36, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ LOKI COMMAND HUB ✦"),
			this.width / 2,
			startY + 8,
			0xFFFFFF
		);

		// Subheader: Nearby thrall statistics
		String thrallColor = this.nearbyThralls > 0 ? "§a" : "§c";
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Thralls Bound: " + thrallColor + this.nearbyThralls + " §8| §7Radius: §f32 blocks"),
			this.width / 2,
			startY + 22,
			0xAAAAAA
		);

		// Section headers
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§eCommand Mode:"),
			startX + 16,
			startY + 42,
			0xFFD700
		);

		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§bBlueprint Catalog:"),
			startX + 165,
			startY + 42,
			0x55FFFF
		);

		// Mode Description Footer Box
		int descY = startY + 132;
		context.fill(startX + 14, descY, startX + 158, descY + 45, 0x880D151D);
		context.drawBorder(startX + 14, descY, 144, 45, 0xFF3A4E63);

		String modeDesc = switch (this.selectedMode) {
			case FOLLOW -> "§aMinions actively follow & guard master.";
			case STAY -> "§eMinions hold positions & guard zone.";
			case ATTACK -> "§cMinions prioritize & assault targets.";
			case MINE -> "§6Minions harvest ores & break blocks.";
			case BUILD -> "§bMinions erect selected blueprint.";
			case RECRUIT -> "§dEnthrall living mobs into thralls.";
		};

		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§fSelected: " + this.selectedMode.getFormattedName()),
			startX + 18,
			descY + 6,
			0xFFFFFF
		);
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal(modeDesc),
			startX + 18,
			descY + 22,
			0xCCCCCC
		);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
