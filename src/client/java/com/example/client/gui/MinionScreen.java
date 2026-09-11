package com.example.client.gui;

import com.example.client.network.ModClientNetworking;
import com.example.entity.custom.MinionEntity;
import com.example.screen.MinionScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-side GUI screen for inspecting and managing minion equipment and inventory.
 * Provides live 3D entity preview, equipment slots (Head, Chest, Legs, Feet, Mainhand, Offhand),
 * 3x3 minion inventory slots, player inventory/hotbar slots, and a dismiss button.
 */
public class MinionScreen extends HandledScreen<MinionScreenHandler> {

	private static final Identifier CONTAINER_TEXTURE = Identifier.ofVanilla("textures/gui/container/inventory.png");

	public MinionScreen(MinionScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();

		int btnWidth = 82;
		int btnHeight = 20;
		int btnY = this.y + this.backgroundHeight + 4;

		// Teleport to Me action button
		ButtonWidget teleportBtn = ButtonWidget.builder(
			Text.literal("§d✦ ").append(Text.translatable("gui.modid-mmcli-agent-modding.minion.teleport")),
			button -> {
				int id = this.handler.getMinionId();
				if (id >= 0) {
					ModClientNetworking.sendTeleportMinion(id);
				} else {
					ModClientNetworking.sendTeleportAllMinions();
				}
				this.close();
			}
		)
		.dimensions(this.x + 4, btnY, btnWidth, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.teleport_desc")))
		.build();

		// Dismiss Minion action button
		ButtonWidget dismissBtn = ButtonWidget.builder(
			Text.literal("§c✖ ").append(Text.translatable("gui.modid-mmcli-agent-modding.minion.dismiss")),
			button -> {
				int id = this.handler.getMinionId();
				if (id >= 0) {
					ModClientNetworking.sendDismissMinion(id);
				} else {
					ModClientNetworking.sendDismissAllMinions();
				}
				this.close();
			}
		)
		.dimensions(this.x + 90, btnY, btnWidth, btnHeight)
		.tooltip(Tooltip.of(Text.translatable("tooltip.modid-mmcli-agent-modding.minion_screen.dismiss_desc")))
		.build();

		this.addDrawableChild(teleportBtn);
		this.addDrawableChild(dismissBtn);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int startX = this.x;
		int startY = this.y;

		// 1. Draw standard container background frame (contains player inventory and hotbar slots)
		context.drawTexture(CONTAINER_TEXTURE, startX, startY, 0, 0, this.backgroundWidth, this.backgroundHeight);

		// 2. Clear upper minion section to clean container gray
		context.fill(startX + 7, startY + 16, startX + 169, startY + 75, 0xFFC6C6C6);

		// 3. Draw slot frames for equipment (slots 0..5) and minion inventory (slots 6..14)
		for (int i = 0; i < MinionScreenHandler.MINION_INV_END; i++) {
			Slot slot = this.handler.getSlot(i);
			context.drawTexture(CONTAINER_TEXTURE, startX + slot.x - 1, startY + slot.y - 1, 7, 83, 18, 18);
		}

		// 4. Center 3D entity preview frame
		int previewLeft = startX + 47;
		int previewTop = startY + 17;
		int previewRight = startX + 113;
		int previewBottom = startY + 73;

		context.fill(previewLeft, previewTop, previewRight, previewBottom, 0xEE141923);
		context.drawBorder(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop, 0xFF3D4F66);

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
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);

		// Minion status tooltip when hovering over entity preview
		MinionEntity minion = this.handler.getMinion();
		if (minion != null && mouseX >= this.x + 47 && mouseX <= this.x + 113 && mouseY >= this.y + 17 && mouseY <= this.y + 73) {
			List<Text> tooltipList = new ArrayList<>();
			tooltipList.add(Text.literal("§6✦ " + minion.getName().getString()));
			tooltipList.add(Text.literal("§c❤ Health: §f" + String.format("%.1f", minion.getHealth()) + " / " + String.format("%.1f", minion.getMaxHealth())));
			tooltipList.add(Text.literal("§b🛡 Armor: §f" + minion.getArmor()));
			if (minion.isSitting()) {
				tooltipList.add(Text.literal("§eStatus: §7Holding Position"));
			} else {
				tooltipList.add(Text.literal("§aStatus: §7Guarding / Following Master"));
			}
			context.drawTooltip(this.textRenderer, tooltipList, mouseX, mouseY);
		}
	}
}
