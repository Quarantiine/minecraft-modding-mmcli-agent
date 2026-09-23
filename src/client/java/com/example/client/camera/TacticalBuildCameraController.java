package com.example.client.camera;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.component.CommandMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import com.example.mixin.client.CameraAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Manages tactical bird's-eye third-person camera perspective during construction directives.
 *
 * Features:
 * 1. Automatic size-scaled elevation based on active blueprint volume.
 * 2. Cave & ceiling raycast collision clamping (stays 1.2 blocks below cavern roofs).
 * 3. Smooth cubic easing transitions between first-person and tactical view.
 * 4. Interactive zoom offset control via Ctrl + Scroll and H-key toggle.
 */
public class TacticalBuildCameraController {

	private static boolean enabled = true;
	private static float transitionProgress = 0.0F; // 0.0F (normal) to 1.0F (full tactical)
	private static float zoomOffset = 0.0F;

	// Preset zoom cycles for 'H' key
	private static int zoomPresetIndex = 1; // 0: Close, 1: Medium, 2: High, 3: Disabled
	private static final float[] ZOOM_PRESETS = { -4.0F, 0.0F, 6.0F };

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggleEnabled() {
		enabled = !enabled;
	}

	public static void cycleZoomPreset() {
		zoomPresetIndex = (zoomPresetIndex + 1) % 4;
		if (zoomPresetIndex == 3) {
			enabled = false;
		} else {
			enabled = true;
			zoomOffset = ZOOM_PRESETS[zoomPresetIndex];
		}
	}

	public static void adjustZoom(float delta) {
		zoomOffset = MathHelper.clamp(zoomOffset + delta, -6.0F, 16.0F);
	}

	public static void updateCamera(Camera camera, Entity focusedEntity, float tickDelta) {
		// Option A: Detached third-person camera offset is retired in favor of physical Arcane Build Flight.
		// The camera remains centered on the player's physical avatar as they smoothly hover in 3D space.
		transitionProgress = 0.0F;
	}

	/**
	 * Casts a ray from the camera's exact 3D world position along its orientation vector
	 * through the center screen crosshair, returning the targeted block within {@code reach} blocks.
	 * Works seamlessly across both first-person and elevated tactical third-person perspectives.
	 *
	 * @param client The Minecraft client instance.
	 * @param reach  Maximum raycast distance (e.g. 96.0F).
	 * @return BlockHitResult representing block contact or miss.
	 */
	public static BlockHitResult getCameraTargetedBlock(MinecraftClient client, float reach) {
		if (client == null || client.world == null || client.gameRenderer == null) {
			return null;
		}
		Camera camera = client.gameRenderer.getCamera();
		if (camera == null) {
			return null;
		}
		Vec3d start = camera.getPos();
		float pitch = camera.getPitch();
		float yaw = camera.getYaw();
		float f = pitch * 0.017453292F;
		float g = -yaw * 0.017453292F;
		float h = MathHelper.cos(g);
		float i = MathHelper.sin(g);
		float j = MathHelper.cos(f);
		float k = MathHelper.sin(f);
		Vec3d dir = new Vec3d((double) (i * j), (double) (-k), (double) (h * j));
		Vec3d end = start.add(dir.x * reach, dir.y * reach, dir.z * reach);
		net.minecraft.block.ShapeContext shapeContext = client.player != null ? net.minecraft.block.ShapeContext.of(client.player) : net.minecraft.block.ShapeContext.absent();
		return client.world.raycast(new RaycastContext(
			start,
			end,
			RaycastContext.ShapeType.OUTLINE,
			RaycastContext.FluidHandling.NONE,
			shapeContext
		));
	}
}
