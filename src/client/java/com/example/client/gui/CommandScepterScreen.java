package com.example.client.gui;

import com.example.blueprint.ArchitectureStyle;
import com.example.blueprint.BuildingCategory;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.ExampleModClient;
import com.example.client.network.ModClientNetworking;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.item.custom.CommandScepterItem;
import com.example.network.UpdateScepterPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import org.lwjgl.glfw.GLFW;

/**
 * Interactive Client GUI for the Loki Command Scepter.
 * Allows the player to switch scepter operating modes, inspect and select
 * architectural blueprints from the catalog, observe nearby minion thralls,
 * and broadcast tactical directives to thralls via C2S packets.
 */
public class CommandScepterScreen extends Screen {

	private static final int WINDOW_WIDTH = 340;
	private static final int WINDOW_HEIGHT = 280;
	private static final int BLUEPRINT_PAGE_SIZE = 3;

	private final Hand hand;
	private final ItemStack scepterStack;
	private CommandMode selectedMode;
	private String selectedBlueprintId;
	private SquadGroup selectedSquad;
	private MinionRole selectedRole;
	private int nearbyThralls = 0;
	private int nearbySelectedThralls = 0;
	private int blueprintPage = 0;

	private ArchitectureStyle selectedStyle = ArchitectureStyle.BIOME_NATIVE;
	private int selectedSize = BuildingCategory.SIZE_MEDIUM;
	private int selectedRotation = 0;

	private final List<ButtonWidget> squadButtons = new ArrayList<>();
	private final List<ButtonWidget> roleButtons = new ArrayList<>();
	private final List<ButtonWidget> architectureButtons = new ArrayList<>();
	private final List<ButtonWidget> modeButtons = new ArrayList<>();
	private final List<ButtonWidget> blueprintButtons = new ArrayList<>();
	private final List<ButtonWidget> sizeButtons = new ArrayList<>();
	private ButtonWidget rotateBtn;
	private ButtonWidget prevPageBtn;
	private ButtonWidget nextPageBtn;

	// Open-state guard to prevent immediate dismissals when opening via sneak-right-click
	private boolean shiftHeldOnOpen = false;
	private boolean initializedOpenState = false;
	private boolean closed = false;

	public CommandScepterScreen(Hand hand, ItemStack scepterStack) {
		super(Text.translatable("gui.modid-mmcli-agent-modding.command_hub.title"));
		this.hand = hand;
		this.scepterStack = scepterStack;
		if (scepterStack != null && !scepterStack.isEmpty()) {
			this.selectedMode = CommandScepterItem.getMode(scepterStack);
			this.selectedBlueprintId = CommandScepterItem.getBlueprintId(scepterStack);
			this.selectedSquad = CommandScepterItem.getTargetSquad(scepterStack);
			this.selectedRole = null; // Archetypes must never be selected by default
			this.selectedStyle = CommandScepterItem.getArchitectureStyle(scepterStack);
			this.selectedSize = CommandScepterItem.getBuildingSize(scepterStack);
			this.selectedRotation = CommandScepterItem.getRotationIndex(scepterStack);
		} else {
			this.selectedMode = CommandMode.FOLLOW;
			this.selectedBlueprintId = "modid-mmcli-agent-modding:watchtower";
			this.selectedSquad = SquadGroup.ALL;
			this.selectedRole = null;
			this.selectedStyle = ArchitectureStyle.BIOME_NATIVE;
			this.selectedSize = BuildingCategory.SIZE_MEDIUM;
			this.selectedRotation = 0;
		}
		this.shiftHeldOnOpen = isShiftOrSneakDown();
	}

	/**
	 * Headless / testing constructor initializing the Command Hub screen with default settings.
	 */
	public CommandScepterScreen() {
		this(Hand.MAIN_HAND, null);
	}

	@Override
	protected void init() {
		super.init();
		this.squadButtons.clear();
		this.roleButtons.clear();
		this.architectureButtons.clear();
		this.modeButtons.clear();
		this.blueprintButtons.clear();
		this.sizeButtons.clear();

		// Detect physical sneak/shift state on initial opening with state guard
		if (!this.initializedOpenState) {
			if (!this.shiftHeldOnOpen) {
				this.shiftHeldOnOpen = isShiftOrSneakDown();
			}
			this.initializedOpenState = true;
		}

		// Count nearby owned minions and selected units
		if (this.client != null && this.client.world != null && this.client.player != null) {
			List<MinionEntity> minions = this.client.world.getEntitiesByClass(
				MinionEntity.class,
				this.client.player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS),
				m -> m.isAlive() && m.isOwner(this.client.player)
			);
			this.nearbyThralls = minions.size();
			this.nearbySelectedThralls = (int) minions.stream().filter(MinionEntity::isSelected).count();
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

		// Interactive Mass Role Assignment Bar (Warrior / Sentinel / Builder)
		MinionRole[] roles = MinionRole.values();
		int roleBtnWidth = 98;
		int roleGap = 6;
		int roleStartX = startX + 17;
		int roleY = startY + 80;
		for (int i = 0; i < roles.length; i++) {
			MinionRole role = roles[i];
			int btnX = roleStartX + i * (roleBtnWidth + roleGap);
			ButtonWidget btn = ButtonWidget.builder(
				getRoleButtonText(role),
				b -> toggleRole(role)
			)
			.dimensions(btnX, roleY, roleBtnWidth, 20)
			.tooltip(getRoleTooltip(role))
			.build();

			this.roleButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// Interactive Architecture Style Bar (Biome Native / Fortress Stone / Frontier Timber / Arcane Nether)
		ArchitectureStyle[] styles = ArchitectureStyle.values();
		int styleBtnWidth = 72;
		int styleGap = 6;
		int styleStartX = startX + 17;
		int styleY = startY + 80;
		for (int i = 0; i < styles.length; i++) {
			ArchitectureStyle style = styles[i];
			int btnX = styleStartX + i * (styleBtnWidth + styleGap);
			ButtonWidget btn = ButtonWidget.builder(
				getStyleButtonText(style),
				b -> selectStyle(style)
			)
			.dimensions(btnX, styleY, styleBtnWidth, 20)
			.tooltip(getStyleTooltip(style))
			.build();

			this.architectureButtons.add(btn);
			this.addDrawableChild(btn);
		}

		// 6 Command Mode Buttons in a 2-column grid
		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length; i++) {
			CommandMode mode = modes[i];
			int col = i % 2;
			int row = i / 2;
			int btnX = startX + 16 + col * 70;
			int btnY = startY + 118 + row * 24;

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
			int btnY = startY + 118 + slot * 36;

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
		int pageControlsY = startY + 226;
		this.prevPageBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
			if (this.blueprintPage > 0) {
				this.blueprintPage--;
				updateBlueprintButtons();
			}
		})
		.dimensions(startX + 165, pageControlsY, 20, 18)
		.tooltip(Tooltip.of(Text.literal("Previous Page")))
		.build();
		this.addDrawableChild(this.prevPageBtn);

		// Procedural Blueprint Size Selectors [ S ] [ M ] [ L ] [ 🎲 ]
		for (int i = 0; i < 4; i++) {
			final int sizeIdx = i;
			int btnX = startX + 188 + i * 29;
			ButtonWidget sizeBtn = ButtonWidget.builder(
				getSizeButtonText(sizeIdx),
				b -> selectSize(sizeIdx)
			)
			.dimensions(btnX, pageControlsY, 27, 18)
			.tooltip(getSizeTooltip(sizeIdx))
			.build();

			this.sizeButtons.add(sizeBtn);
			this.addDrawableChild(sizeBtn);
		}

		this.nextPageBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
			int totalPages = Math.max(1, (BlueprintRegistry.getAll().size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
			if (this.blueprintPage + 1 < totalPages) {
				this.blueprintPage++;
				updateBlueprintButtons();
			}
		})
		.dimensions(startX + 305, pageControlsY, 20, 18)
		.tooltip(Tooltip.of(Text.literal("Next Page")))
		.build();
		this.addDrawableChild(this.nextPageBtn);

		// Blueprint Rotation Button (visible only in BUILD mode)
		this.rotateBtn = ButtonWidget.builder(
			getRotateButtonText(),
			b -> cycleRotationGui()
		)
		.dimensions(startX + 18, startY + 214, 136, 20)
		.tooltip(Tooltip.of(Text.literal("§6✦ Rotate Blueprint 90° Clockwise\n§7In-Game: Press [R] or Left-Click with Scepter.")))
		.build();
		this.rotateBtn.visible = this.selectedMode == CommandMode.BUILD;
		this.addDrawableChild(this.rotateBtn);

		updateBlueprintButtons();
		updateControlsVisibility();

		// Action Buttons: Execute Directive, Deselect All, Teleport Minions, Dismiss All Minions, & Close
		int bottomY = startY + 250;
		ButtonWidget executeBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.command_hub.execute"),
			b -> executeDirective()
		).dimensions(startX + 14, bottomY, 58, 20).build();
		this.addDrawableChild(executeBtn);

		ButtonWidget deselectBtn = ButtonWidget.builder(
			Text.literal("§e✕ Deselect"),
			b -> {
				ModClientNetworking.sendDeselectAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 76, bottomY, 62, 20)
		.tooltip(Tooltip.of(Text.literal("Deselect all nearby minions and anchor them at their posts")))
		.build();
		deselectBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(deselectBtn);

		ButtonWidget teleportBtn = ButtonWidget.builder(
			Text.literal("§d✦ Teleport"),
			b -> {
				ModClientNetworking.sendTeleportAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 142, bottomY, 60, 20)
		.tooltip(Tooltip.of(this.nearbyThralls > 0
			? Text.literal("§dTeleport all " + this.nearbyThralls + " nearby owned minion(s) to you")
			: Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_teleport")))
		.build();
		teleportBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(teleportBtn);

		ButtonWidget dismissBtn = ButtonWidget.builder(
			Text.literal("§c✖ Destroy All"),
			b -> {
				ModClientNetworking.sendDismissAllMinions();
				this.close();
			}
		)
		.dimensions(startX + 206, bottomY, 66, 20)
		.tooltip(Tooltip.of(this.nearbyThralls > 0
			? Text.literal("§cDestroy all " + this.nearbyThralls + " nearby owned minion(s) and drop equipment")
			: Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_dismiss")))
		.build();
		dismissBtn.active = this.nearbyThralls > 0;
		this.addDrawableChild(dismissBtn);

		ButtonWidget closeBtn = ButtonWidget.builder(
			Text.translatable("gui.modid-mmcli-agent-modding.command_hub.close"),
			b -> this.close()
		)
		.dimensions(startX + 276, bottomY, 50, 20)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.command_hub.close_desc")))
		.build();
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

	private Text getRoleButtonText(MinionRole role) {
		boolean isSelected = role == this.selectedRole;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + role.getColorCode() + role.getDisplayName());
	}

	private Tooltip getRoleTooltip(MinionRole role) {
		boolean isSelected = role == this.selectedRole;
		String desc = isSelected
			? "§a[Active Selection] Minions inside Banner of Courage rally ring will transform into this archetype.\n§eClick again to deselect."
			: "§7Click to select this archetype for channeled rally ring transformation.";
		return Tooltip.of(Text.literal(
			"§6✦ Archetype: " + role.getFormattedName() + " §7(" + role.getIcon() + ")\n" + desc
		));
	}

	private Text getModeButtonText(CommandMode mode) {
		boolean isSelected = mode == this.selectedMode;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + mode.getColorCode() + mode.getDisplayName());
	}

	private Text getBlueprintButtonText(StructureBlueprint bp) {
		boolean isSelected = bp.getId().equalsIgnoreCase(this.selectedBlueprintId);
		StructureBlueprint resolved = BlueprintRegistry.resolveCategoryBlueprint(bp.getId(), this.selectedSize, 0L);
		if (resolved == null) {
			resolved = bp;
		}
		BlockBox box = resolved.getBoundingBox();
		int dimX = box.getBlockCountX();
		int dimY = box.getBlockCountY();
		int dimZ = box.getBlockCountZ();

		String prefix = isSelected ? "§6✦ " : "§f";
		return Text.literal(prefix + bp.getName() + "\n§8" + dimX + "x" + dimY + "x" + dimZ + " §8| §e" + resolved.getBlockCount() + "b");
	}

	private Text getStyleButtonText(ArchitectureStyle style) {
		boolean isSelected = style == this.selectedStyle;
		String prefix = isSelected ? "§6▶ " : "";
		return Text.literal(prefix + style.getDisplayName());
	}

	private Tooltip getStyleTooltip(ArchitectureStyle style) {
		boolean isSelected = style == this.selectedStyle;
		String desc = isSelected
			? "§a[Active Selection] " + style.getDescription()
			: "§7" + style.getDescription() + "\n§eClick to select this architecture style.";
		return Tooltip.of(Text.literal("§6✦ Style: " + style.getFormattedName() + "\n" + desc));
	}

	private Text getSizeButtonText(int size) {
		boolean isSelected = size == this.selectedSize;
		String label = switch (size) {
			case BuildingCategory.SIZE_SMALL -> "S";
			case BuildingCategory.SIZE_MEDIUM -> "M";
			case BuildingCategory.SIZE_GRAND -> "L";
			default -> "🎲";
		};
		String color = isSelected ? "§6§l" : "§7";
		return Text.literal(color + label);
	}

	private Tooltip getSizeTooltip(int size) {
		String label = switch (size) {
			case BuildingCategory.SIZE_SMALL -> "Small (5x5 footprint)";
			case BuildingCategory.SIZE_MEDIUM -> "Medium (7x7 footprint)";
			case BuildingCategory.SIZE_GRAND -> "Grand (9x9 footprint)";
			default -> "Random Procedural Size";
		};
		boolean isSelected = size == this.selectedSize;
		String status = isSelected ? "§a[Active Size]\n" : "§eClick to select size.\n";
		return Tooltip.of(Text.literal("§b✦ Size: §f" + label + "\n" + status + "§7Scales procedural categories (Home, Tower, Barricade, etc.)"));
	}

	private void selectSquad(SquadGroup squad) {
		this.selectedSquad = squad;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetSquad(this.scepterStack, squad);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	/**
	 * Toggles or selects the active target archetype role. If the clicked role is already selected,
	 * it is toggled off (unselected). Updates the held scepter item component, synchronizes with the server,
	 * and refreshes button styling.
	 *
	 * @param role The MinionRole archetype to toggle.
	 */
	public void toggleRole(MinionRole role) {
		if (this.selectedRole == role) {
			this.selectedRole = null;
		} else {
			this.selectedRole = role;
		}
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetRole(this.scepterStack, this.selectedRole);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	/**
	 * Directly sets the active target archetype role, updating the scepter stack and synchronizing with the server.
	 *
	 * @param role The MinionRole archetype to select, or null to clear selection.
	 */
	public void setSelectedRole(MinionRole role) {
		this.selectedRole = role;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setTargetRole(this.scepterStack, this.selectedRole);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	/**
	 * Resolves the currently selected target archetype role for mass conversion.
	 *
	 * @return The selected MinionRole, or null if no archetype is active.
	 */
	public MinionRole getSelectedRole() {
		return this.selectedRole;
	}

	public void selectStyle(ArchitectureStyle style) {
		this.selectedStyle = style;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setArchitectureStyle(this.scepterStack, style);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public ArchitectureStyle getSelectedStyle() {
		return this.selectedStyle;
	}

	public void setSelectedStyle(ArchitectureStyle style) {
		this.selectedStyle = style;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setArchitectureStyle(this.scepterStack, style);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void selectSize(int size) {
		this.selectedSize = size;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBuildingSize(this.scepterStack, size);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public int getSelectedSize() {
		return this.selectedSize;
	}

	public void setSelectedSize(int size) {
		this.selectedSize = size;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setBuildingSize(this.scepterStack, size);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void updateControlsVisibility() {
		boolean isBuild = this.selectedMode == CommandMode.BUILD;
		for (ButtonWidget btn : this.roleButtons) {
			btn.visible = !isBuild;
		}
		for (ButtonWidget btn : this.architectureButtons) {
			btn.visible = isBuild;
		}
		for (ButtonWidget btn : this.sizeButtons) {
			btn.visible = isBuild;
		}
		if (this.rotateBtn != null) {
			this.rotateBtn.visible = isBuild;
		}
	}

	public List<ButtonWidget> getArchitectureButtons() {
		return this.architectureButtons;
	}

	public List<ButtonWidget> getSizeButtons() {
		return this.sizeButtons;
	}

	public ButtonWidget getRotateButton() {
		return this.rotateBtn;
	}

	public int getSelectedRotation() {
		return this.selectedRotation;
	}

	public void setSelectedRotation(int rotation) {
		this.selectedRotation = Math.floorMod(rotation, 4);
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setRotationIndex(this.scepterStack, this.selectedRotation);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	public void cycleRotationGui() {
		this.selectedRotation = Math.floorMod(this.selectedRotation + 1, 4);
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setRotationIndex(this.scepterStack, this.selectedRotation);
		}
		if (this.client != null && this.client.world != null && this.client.player != null) {
			this.client.world.playSound(
				null,
				this.client.player.getX(),
				this.client.player.getY(),
				this.client.player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME,
				SoundCategory.PLAYERS,
				0.8F,
				1.0F + (this.selectedRotation * 0.15F)
			);
		}
		syncToServer(false);
		refreshButtonLabels();
	}

	private Text getRotateButtonText() {
		int degrees = this.selectedRotation * 90;
		String dir = switch (this.selectedRotation) {
			case 1 -> "East";
			case 2 -> "South";
			case 3 -> "West";
			default -> "North";
		};
		return Text.literal("§6↻ Rotate: §b" + degrees + "° §7(" + dir + ")");
	}

	private void selectMode(CommandMode mode) {
		this.selectedMode = mode;
		if (this.scepterStack != null && !this.scepterStack.isEmpty()) {
			CommandScepterItem.setMode(this.scepterStack, mode);
		}
		updateControlsVisibility();
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
		try {
			int rotation = this.selectedRotation;
			ModClientNetworking.sendUpdateScepter(
				this.selectedMode,
				this.selectedBlueprintId,
				this.selectedSquad,
				rotation,
				Optional.ofNullable(this.selectedRole),
				executeDirective,
				this.selectedStyle,
				this.selectedSize
			);
		} catch (Throwable ignored) {
			// Graceful fallback in headless or uninitialized test environments
		}
	}

	private void refreshButtonLabels() {
		SquadGroup[] squads = SquadGroup.values();
		for (int i = 0; i < squads.length && i < this.squadButtons.size(); i++) {
			this.squadButtons.get(i).setMessage(getSquadButtonText(squads[i]));
			this.squadButtons.get(i).setTooltip(getSquadTooltip(squads[i]));
		}

		MinionRole[] roles = MinionRole.values();
		for (int i = 0; i < roles.length && i < this.roleButtons.size(); i++) {
			this.roleButtons.get(i).setMessage(getRoleButtonText(roles[i]));
			this.roleButtons.get(i).setTooltip(getRoleTooltip(roles[i]));
		}

		ArchitectureStyle[] styles = ArchitectureStyle.values();
		for (int i = 0; i < styles.length && i < this.architectureButtons.size(); i++) {
			this.architectureButtons.get(i).setMessage(getStyleButtonText(styles[i]));
			this.architectureButtons.get(i).setTooltip(getStyleTooltip(styles[i]));
		}

		for (int i = 0; i < this.sizeButtons.size(); i++) {
			this.sizeButtons.get(i).setMessage(getSizeButtonText(i));
			this.sizeButtons.get(i).setTooltip(getSizeTooltip(i));
		}

		if (this.rotateBtn != null) {
			this.rotateBtn.setMessage(getRotateButtonText());
		}

		CommandMode[] modes = CommandMode.values();
		for (int i = 0; i < modes.length && i < this.modeButtons.size(); i++) {
			this.modeButtons.get(i).setMessage(getModeButtonText(modes[i]));
		}

		if (!this.blueprintButtons.isEmpty()) {
			updateBlueprintButtons();
		}
	}

	private void updateBlueprintButtons() {
		if (this.blueprintButtons.isEmpty()) {
			return;
		}
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
		// Zero-latency release transition check: clear open-state guard as soon as physical shift is released
		if (this.shiftHeldOnOpen && !isShiftOrSneakDown()) {
			this.shiftHeldOnOpen = false;
		}

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

		// Subtle header UX indicator for Shift-to-close fast exit
		Text shiftCloseText = Text.literal("§e[Shift] §7Close");
		int shiftCloseWidth = this.textRenderer.getWidth(shiftCloseText);
		context.drawTextWithShadow(this.textRenderer, shiftCloseText, startX + WINDOW_WIDTH - shiftCloseWidth - 10, startY + 6, 0xE0E0E0);

		// Subheader: Nearby thrall statistics & active squad channel
		String thrallColor = this.nearbyThralls > 0 ? "§a" : "§c";
		String selectedColor = this.nearbySelectedThralls > 0 ? "§e" : "§7";
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("§7Thralls: " + thrallColor + this.nearbyThralls + " §8| §7Selected: " + selectedColor + this.nearbySelectedThralls + " §8| §7Channel: " + this.selectedSquad.getFormattedName()),
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

		if (this.selectedMode == CommandMode.BUILD) {
			// Architecture Style Bar label
			context.drawTextWithShadow(
				this.textRenderer,
				Text.literal("§bArchitecture Style: " + this.selectedStyle.getFormattedName()),
				startX + 16,
				startY + 69,
				0x55FFFF
			);

			// Active style indicator underline
			int selectedStyleIndex = this.selectedStyle.ordinal();
			ArchitectureStyle[] styles = ArchitectureStyle.values();
			if (selectedStyleIndex >= 0 && selectedStyleIndex < styles.length) {
				int styleStartX = startX + 17;
				int styleBtnWidth = 72;
				int styleGap = 6;
				int styleY = startY + 80;
				int indX = styleStartX + selectedStyleIndex * (styleBtnWidth + styleGap);
				int indicatorColor = this.selectedStyle.getFormatting().getColorValue() != null
					? (0xFF000000 | this.selectedStyle.getFormatting().getColorValue())
					: 0xFF55FFFF;
				context.fill(indX, styleY + 20, indX + styleBtnWidth, styleY + 22, indicatorColor);
			}
		} else {
			// Mass Role Archetype Bar label
			String roleLabelSuffix = this.selectedRole != null
				? " " + this.selectedRole.getFormattedName() + " §8(Rally Transform)"
				: " §7[None]";
			context.drawTextWithShadow(
				this.textRenderer,
				Text.literal("§eMass Role Archetype:" + roleLabelSuffix),
				startX + 16,
				startY + 69,
				0xFFD700
			);

			// Active role indicator underline
			if (this.selectedRole != null) {
				int selectedRoleIndex = this.selectedRole.ordinal();
				MinionRole[] roles = MinionRole.values();
				if (selectedRoleIndex >= 0 && selectedRoleIndex < roles.length) {
					int roleStartX = startX + 17;
					int roleBtnWidth = 98;
					int roleGap = 6;
					int roleY = startY + 80;
					int indX = roleStartX + selectedRoleIndex * (roleBtnWidth + roleGap);
					int indicatorColor = this.selectedRole.getFormatting().getColorValue() != null
						? (0xFF000000 | this.selectedRole.getFormatting().getColorValue())
						: 0xFFFFD700;
					context.fill(indX, roleY + 20, indX + roleBtnWidth, roleY + 22, indicatorColor);
				}
			}
		}

		// Section headers
		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§eCommand Mode:"),
			startX + 16,
			startY + 106,
			0xFFD700
		);

		context.drawTextWithShadow(
			this.textRenderer,
			Text.literal("§bBlueprint Catalog:"),
			startX + 165,
			startY + 106,
			0x55FFFF
		);

		// Blueprint pagination indicator (only shown when not in BUILD mode, as size buttons occupy this space)
		if (this.selectedMode != CommandMode.BUILD) {
			int totalPages = Math.max(1, (BlueprintRegistry.getAll().size() + BLUEPRINT_PAGE_SIZE - 1) / BLUEPRINT_PAGE_SIZE);
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("§7Page §f" + (this.blueprintPage + 1) + "§7/§f" + totalPages),
				startX + 245,
				startY + 231,
				0xAAAAAA
			);
		}

		// Mode Description Footer Box
		int descY = startY + 194;
		context.fill(startX + 14, descY, startX + 158, descY + 46, 0x880D151D);
		context.drawBorder(startX + 14, descY, 144, 46, 0xFF3A4E63);

		String modeDesc = switch (this.selectedMode) {
			case FOLLOW -> "§aMinions actively follow & guard master.";
			case STAY -> "§eMinions hold positions & guard zone.";
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
		if (this.selectedMode != CommandMode.BUILD) {
			context.drawTextWithShadow(
				this.textRenderer,
				Text.literal(modeDesc),
				startX + 18,
				descY + 22,
				0xCCCCCC
			);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void tick() {
		super.tick();
		// Periodic zero-latency fallback to clear shiftHeldOnOpen
		if (this.shiftHeldOnOpen && !isShiftOrSneakDown()) {
			this.shiftHeldOnOpen = false;
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// 1. Shift or Sneak Key handling with open-state guard
		if (isShiftOrSneakKey(keyCode, scanCode)) {
			if (this.shiftHeldOnOpen) {
				// Player opened GUI via sneak-right-click and Shift is still held.
				// Suppress immediate close and absorb GLFW key repeats.
				return true;
			}
			// Shift pressed while GUI was already open -> fast dismiss
			this.close();
			return true;
		}

		// 2. Command Hub hotkey toggle ('V')
		if (isCommandHubKey(keyCode, scanCode)) {
			this.close();
			return true;
		}

		// 3. Inventory key toggle ('E')
		if (isInventoryKey(keyCode, scanCode)) {
			this.close();
			return true;
		}

		// 4. Default key handling (handles Escape to close via Screen.keyPressed)
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (isShiftOrSneakKey(keyCode, scanCode)) {
			this.shiftHeldOnOpen = false;
		}
		if (this.shiftHeldOnOpen && !isShiftOrSneakDown()) {
			this.shiftHeldOnOpen = false;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		this.closed = true;
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
	 * Checks if the given GLFW keycode / scancode corresponds to the Command Hub toggle key (default 'V').
	 *
	 * @param keyCode GLFW keycode.
	 * @param scanCode Physical scancode.
	 * @return True if matching Command Hub keybinding or GLFW_KEY_V.
	 */
	public boolean isCommandHubKey(int keyCode, int scanCode) {
		try {
			if (ExampleModClient.commandHubKey != null && ExampleModClient.commandHubKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		return keyCode == GLFW.GLFW_KEY_V;
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

	/**
	 * Returns whether Shift was held down when this screen was first opened.
	 *
	 * @return True if Shift was held during screen opening, suppressing immediate closure.
	 */
	public boolean isShiftHeldOnOpen() {
		return this.shiftHeldOnOpen;
	}

	/**
	 * Sets the open-state guard flag for Shift and marks initialization complete.
	 *
	 * @param shiftHeldOnOpen Whether Shift should be treated as held on screen opening.
	 */
	public void setShiftHeldOnOpen(boolean shiftHeldOnOpen) {
		this.shiftHeldOnOpen = shiftHeldOnOpen;
		this.initializedOpenState = true;
	}

	/**
	 * Returns whether this screen has been closed.
	 *
	 * @return True if {@link #close()} has been invoked.
	 */
	public boolean isClosed() {
		return this.closed;
	}

	/**
	 * Manually sets the closed state of this screen.
	 *
	 * @param closed True to mark the screen as closed.
	 */
	public void setClosed(boolean closed) {
		this.closed = closed;
	}

	/**
	 * Returns whether the screen's initial opening state has been captured.
	 *
	 * @return True if open-state guard has run once.
	 */
	public boolean isInitializedOpenState() {
		return this.initializedOpenState;
	}

	/**
	 * Sets whether the screen's initial opening state has been captured.
	 *
	 * @param initializedOpenState True if open-state initialization has completed.
	 */
	public void setInitializedOpenState(boolean initializedOpenState) {
		this.initializedOpenState = initializedOpenState;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
