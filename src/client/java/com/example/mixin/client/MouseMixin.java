package com.example.mixin.client;

import com.example.client.renderer.ClientDesignCaptureTracker;
import com.example.client.renderer.ClientMiningCaptureTracker;
import com.example.component.CommandMode;
import com.example.component.MiningMode;
import com.example.item.custom.CommandScepterItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into mouse scroll handling to adjust DESIGN mode and MINE AREA mode selection height
 * when holding Ctrl without cycling hotbar item slots.
 */
@Mixin(Mouse.class)
public class MouseMixin {

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.currentScreen != null) {
			return;
		}

		if (window == client.getWindow().getHandle() && vertical != 0.0D) {
			ItemStack stack = CommandScepterItem.getHeldScepter(client.player);
			if (!stack.isEmpty()) {
				CommandMode mode = CommandScepterItem.getMode(stack);
				boolean ctrlPressed = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
					|| InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL)
					|| Screen.hasControlDown();

				if (ctrlPressed) {
					if (mode == CommandMode.DESIGN) {
						int step = (vertical > 0.0D) ? 1 : -1;
						if (Screen.hasShiftDown()) {
							step *= 5;
						}

						int newHeight = ClientDesignCaptureTracker.adjustHeight(step);
						if (newHeight > 0 && ClientDesignCaptureTracker.getPos1() != null && ClientDesignCaptureTracker.getPos2() != null) {
							int minY = Math.min(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY());
							int maxY = Math.max(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY());
							client.player.sendMessage(
								Text.literal("§d✦ Design Box Height: §f" + newHeight + " blocks §8(Y: " + minY + " → " + maxY + ") §8| §7" + ClientDesignCaptureTracker.getDimensionString()),
								true
							);
							client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.7F, 1.2F);
						}

						ci.cancel(); // Prevent cycling hotbar slots!
					} else if (mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == MiningMode.AREA) {
						int step = (vertical > 0.0D) ? 1 : -1;
						if (Screen.hasShiftDown()) {
							step *= 5;
						}

						int newHeight = ClientMiningCaptureTracker.adjustHeight(step);
						if (newHeight > 0 && ClientMiningCaptureTracker.getPos1() != null && ClientMiningCaptureTracker.getPos2() != null) {
							int minY = Math.min(ClientMiningCaptureTracker.getPos1().getY(), ClientMiningCaptureTracker.getPos2().getY());
							int maxY = Math.max(ClientMiningCaptureTracker.getPos1().getY(), ClientMiningCaptureTracker.getPos2().getY());
							client.player.sendMessage(
								Text.literal("§6✦ Mining Box Height: §f" + newHeight + " blocks §8(Y: " + minY + " → " + maxY + ") §8| §e" + ClientMiningCaptureTracker.getDimensionString()),
								true
							);
							client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.7F, 1.4F);
						}

						ci.cancel(); // Prevent cycling hotbar slots!
					}
				}
			}
		}
	}
}
