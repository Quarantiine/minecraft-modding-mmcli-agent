package com.example.client.gui;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.network.ModClientNetworking;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
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
	private static final int WINDOW_HEIGHT = 250;
	private static final int BLUEPRINT_PAGE_SIZE = 3;

	private final Hand hand;
	private final ItemStack scepterStack;
	private CommandMode selectedMode;
	private String selectedBlueprintId;
	private SquadGroup selectedSquad;
	private int nearbyThralls = 0;
	private int blueprintPage = 0;

	private final List<ButtonWidget> squadButtons = new ArrayList<>();
	private final List<ButtonWidget> modeButtons = new ArrayList<>();
	private final List<ButtonWidget> blueprintButtons = new ArrayList<>();
	private ButtonWidget prevPageBtn;
	private ButtonWidget nextPageBtn;

	public CommandScepterScreen(Hand hand, ItemStack scepterStack) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.command_hub.title"));
		this.hand = hand;
		this.scepterStack = scepterStack;
		this.selectedMode = CommandScepterItem.getMode(scepterStack);
		this.selectedBlueprintId = CommandScepterItem.getBlueprintId(scepterStack);
		this.selectedSquad = CommandScepterItem.getTargetSquad(scepterStack);
	}

	@Override
	protected void init() {
		super.init();
		this.squadButtons.clear();
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

		// Interactive Squad Selection Bar across top of controls (All / Alpha / Bravo / Charlie / Delta)
		SquadGroup[] squads = SquadGroup.values();
		int squadBtnWidth = 58;
		int squadGap = 5;
		int squadStartX = startX + 15;
		int squadY = startY + 46;
		for (int i = 0; i < squads.length; i++) {
			SquadGroup squad = squads[i];
			int btnX = squadStartX + i * (squadBtnWidth + squadGap);
			ButtonWidget btn = ButtonWidget.builder(getSquadButtonText(squad), b -> selectSquad(squad))
				.dimensions(btnX, squadY, squadBtnWidth, 20)
				.tooltip(getSquadTooltip(squad))
				.build();

			this.squadButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// 6 Command Mode Buttons in a 2-column grid
		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length; i++) {
			CommandMode mode = modes[i];
			int col = i % 2;
			int row = i / 2;
			int btnX = startX + 16 + col * 70;
			int btnY = startY + 84 + row * 24;

			ButtonWidget btn = ButtonWidget.builder(getModeButtonText(mode), b -> selectMode(mode))
				.dimensions(btnX, btnY, 66, 20)
				.build();

			this.modeButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// Architectural Blueprint Catalog with fixed 3-item viewport and pagination controls
		List<StructureBlueprint> blueprints = new ArrayList<>(BlueprintRegistry.getAll());
		for (int i = 0; i < blueprints.size(); i++) {
			if (blueprints.get(i).getId().equalsIgnoreCase(this.selectedBlueprintId)) {
				this.blueprintPage = i / BLUEPRINT_PAGE_SIZE;
				break;
			}
		}

		for (int slot = 0; slot < BLUEPRINT_PAGE_SIZE; slot++) {
			final int slotIndex = slot;
			int btnX = startX + 165;
			int btnY = startY + 84 + slot * 36;

			ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
				int index = this.blueprintPage * BLUEPRINT_PAGE_SIZE + slotIndex;
				List<StructureBlueprint> bps = new ArrayList<>(BlueprintRegistry.getAll());
				if (index >= 0 && index < bps.size()) {
					selectBlueprint(bps.get(index).getId());
				}
			})
			.dimensions(btnX, btnY, 160, 30)
			.build();

			this.blueprintButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// Blueprint Catalog Pagination Controls
		int pageControlsY = startY + 192;
		this.prevPageBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.blueprintPage > 0) {
				this.blueprintPage--;
				updateBlueprintButtons();
			}
		})
		.dimensions(startX + 165, pageControlsY, 36, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.addDrawableChild(this.prevPageBtn);

		this.nextPageBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			int totalPages = Math.max(1, (BlueprintRegistry.getAll().size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
			if (this.blueprintPage + 1 < totalPages) {
				this.blueprintPage++;
				updateBlueprintButtons();
			}
		})
		.dimensions(startX + 289, pageControlsY, 36, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.addDrawableChild(this.nextPageBtn);

		updateBlueprintButtons();

		// Action Buttons: Execute Directive, Teleport Minions, Dismiss All Minions, & Close
		int bottomY = startY + 216;
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

	private static String getSquadLabel(SquadGroup squad) {
		return switch (squad) {
			case ALL -> "All";
			case ALPHA -> "Alpha";
			case BRAVO -> "Bravo";
			case CHARLIE -> "Charlie";
			case DELTA -> "Delta";
		};
	}

	private static Tooltip getSquadTooltip(SquadGroup squad) {
		String desc = switch (squad) {
			case ALL -> "Direct commands and broadcasts to all minions across all squads.";
			case ALPHA -> "Direct commands exclusively to Minions enrolled in Squad Alpha.";
			case BRAVO -> "Direct commands exclusively to Minions enrolled in Squad Bravo.";
			case CHARLIE -> "Direct commands exclusively to Minions enrolled in Squad Charlie.";
			case DELTA -> "Direct commands exclusively to Minions enrolled in Squad Delta.";
		};
		return Tooltip.of(Text.literal("§6✦ Channel: " + squad.getFormattedName() + "\n§7" + desc + "\n§eClick to select this squad channel."));
	}

	private Text getSquadButtonText(SquadGroup squad) {
		boolean isSelected = squad == this.selectedSquad;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + squad.getColorCode() + getSquadLabel(squad));
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

	private void selectSquad(SquadGroup squad) {
		this.selectedSquad = squad;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetSquad(this.scepterStack, squad);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	private void selectMode(CommandMode mode) {
		this.selectedMode = mode;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setMode(this.scepterStack, mode);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	private void selectBlueprint(String blueprintId) {
		this.selectedBlueprintId = blueprintId;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBlueprintId(this.scepterStack, blueprintId);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	private void executeDirective() {
		syncToServer(true);
		this.close();
	}

	private void syncToServer(boolean executeDirective) {
		ModClientNetworking.sendUpdateScepter(this.selectedMode, this.selectedBlueprintId, this.selectedSquad, executeDirective);
	}

	private void refreshButtonLabels() {
		SquadGroup[] squads = SquadGroup.values();
		for (int i = 0; i < squads.length && i < this.squadButtons.size(); i++) {
			this.squadButtons.get(i).setMessage(getSquadButtonText(squads[i]));
			this.squadButtons.get(i).setTooltip(getSquadTooltip(squads[i]));
		}

		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length && i < this.modeButtons.size(); i++) {
			this.modeButtons.get(i).setMessage(getModeButtonText(modes[i]));
		}

		updateBlueprintButtons();
	}

	private void updateBlueprintButtons() {
		List<StructureBlueprint> blueprints = new ArrayList<>(BlueprintRegistry.getAll());
		int totalPages = Math.max(1, (blueprints.size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
		if (this.blueprintPage >= totalPages) {
			this.blueprintPage = totalPages - 1;
		}
		if (this.blueprintPage < 0) {
			this.blueprintPage = 0;
		}

		for (int slot = 0; slot < BLUEPRINT_PAGE_SIZE && slot < this.blueprintButtons.size(); slot++) {
			int index = this.blueprintPage * BLUEPRINT_PAGE_SIZE + slot;
			ButtonWidget btn = this.blueprintButtons.get(slot);
			if (index < blueprints.size()) {
				StructureBlueprint bp = blueprints.get(index);
				btn.visible = true;
				btn.active = true;
				btn.setMessage(getBlueprintButtonText(bp));
			} else {
				btn.visible = false;
				btn.active = false;
			}
		}

		if (this.prevPageBtn != null) {
			this.prevPageBtn.active = this.blueprintPage > 0;
		}
		if (this.nextPageBtn != null) {
			this.nextPageBtn.active = (this.blueprintPage + 1) < totalPages;
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
		context.fill(startX + 1, startY + 1, startX + WINDOW_WIDTH - 1, startY + 30, 0xDD1B2A3A);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§6✦ LOKI COMMAND HUB ✦"),
			this.width / 2,
			startY + 6,
			0xFFFFFF
		);

		// Subheader: Nearby thrall statistics & active squad channel
		String thrallColor = this.nearbyThralls > 0 ? "§a" : "§c";
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Thralls: " + thrallColor + this.nearbyThralls + " §8| §7Channel: " + this.selectedSquad.getFormattedName() + " §8| §7Radius: §f32b"),
			this.width / 2,
			startY + 18,
			0xAAAAAA
		);

		// Squad bar label
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§eTarget Squad Channel:"),
			startX + 16,
			startY + 35,
			0xFFD700
		);

		// Active squad indicator underline
		int selectedSquadIndex = this.selectedSquad.ordinal();
		SquadGroup[] squads = SquadGroup.values();
		if (selectedSquadIndex >= 0 && selectedSquadIndex < squads.length) {
			int squadStartX = startX + 15;
			int squadBtnWidth = 58;
			int squadGap = 5;
			int squadY = startY + 46;
			int indX = squadStartX + selectedSquadIndex * (squadBtnWidth + squadGap);
			int indicatorColor = this.selectedSquad.getFormatting().getColorValue() != null
				? (0xFF000000 | this.selectedSquad.getFormatting().getColorValue())
				: 0xFFFFD700;
			context.fill(indX, squadY + 20, indX + squadBtnWidth, squadY + 22, indicatorColor);
		}

		// Section headers
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§eCommand Mode:"),
			startX + 16,
			startY + 72,
			0xFFD700
		);

		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§bBlueprint Catalog:"),
			startX + 165,
			startY + 72,
			0x55FFFF
		);

		// Blueprint pagination indicator
		int totalPages = Math.max(1, (BlueprintRegistry.getAll().size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Page §f" + (this.blueprintPage + 1) + "§7/§f" + totalPages),
			startX + 245,
			startY + 197,
			0xAAAAAA
		);

		// Mode Description Footer Box
		int descY = startY + 160;
		context.fill(startX + 14, descY, startX + 158, descY + 46, 0x880D151D);
		context.drawBorder(startX + 14, descY, 144, 46, 0xFF3A4E63);

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
			Text.literal("§fMode: " + this.selectedMode.getFormattedName() + " §8[" + this.selectedSquad.getFormattedName() + "§8]"),
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
