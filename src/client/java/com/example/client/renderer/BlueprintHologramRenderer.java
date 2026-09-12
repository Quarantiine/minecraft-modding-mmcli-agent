package com.example.client.renderer;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.component.CommandMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side holographic 3D wireframe preview renderer for architectural blueprints.
 * Renders during {@link WorldRenderEvents#AFTER_TRANSLUCENT} when the commanding player
 * is holding the Loki Command Scepter in {@link CommandMode#BUILD} mode and aiming at a valid block.
 *
 * Visual Components:
 * 1. Neon Cyan Blueprint Wireframe: Bounding volume outlining the entire multiblock structure.
 * 2. Gold Origin Anchor Box: Highlights the ground origin block where construction anchors.
 * 3. Ghost Block Schematics: Faint translucent wireframe outlines representing individual planned blocks.
 */
public class BlueprintHologramRenderer {

	/**
	 * Maximum raycast distance for targeting ground placement when holding the scepter.
	 */
	private static final double MAX_PREVIEW_REACH = 24.0D;

	/**
	 * Registers the holographic wireframe preview hook into Fabric's world rendering pipeline.
	 */
	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(BlueprintHologramRenderer::render);
	}

	/**
	 * World render event handler executed after translucent geometry has rendered.
	 *
	 * @param context The world render context providing matrices, camera, and vertex consumers.
	 */
	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || client.player == null) {
			return;
		}

		ClientPlayerEntity player = client.player;

		// 1. Verify player is holding the Command Scepter in main hand or off hand
		ItemStack scepterStack = null;
		if (player.getMainHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getMainHandStack();
		} else if (player.getOffHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getOffHandStack();
		}

		if (scepterStack == null) {
			return;
		}

		// 2. Verify Command Scepter is actively in BUILD mode
		CommandMode mode = CommandScepterItem.getMode(scepterStack);
		if (mode != CommandMode.BUILD) {
			return;
		}

		// 3. Resolve block hit target via crosshair or extended raycast
		BlockHitResult hitResult = null;
		if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			hitResult = bhr;
		} else {
			float tickDelta = context.tickCounter() != null ? context.tickCounter().getTickDelta(false) : 0.0F;
			HitResult ray = player.raycast(MAX_PREVIEW_REACH, tickDelta, false);
			if (ray instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
				hitResult = bhr;
			}
		}

		if (hitResult == null) {
			return;
		}

		// 4. Resolve anchor block coordinates identical to CommandScepterItem#useOnBlock
		BlockPos clickedPos = hitResult.getBlockPos();
		Direction side = hitResult.getSide();
		BlockPos anchorPos = client.world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);

		// 5. Resolve active blueprint from item data component and apply current scepter rotation
		String blueprintId = CommandScepterItem.getBlueprintId(scepterStack);
		StructureBlueprint blueprint = BlueprintRegistry.getOrDefault(blueprintId);
		if (blueprint == null || blueprint.getBlockCount() == 0) {
			return;
		}
		blueprint = blueprint.rotate(CommandScepterItem.getRotation(scepterStack));

		// 6. Compute bounding boxes in world space
		BlockBox localBox = blueprint.getBoundingBox();
		double minX = anchorPos.getX() + localBox.getMinX();
		double minY = anchorPos.getY() + localBox.getMinY();
		double minZ = anchorPos.getZ() + localBox.getMinZ();
		double maxX = anchorPos.getX() + localBox.getMaxX() + 1.0D;
		double maxY = anchorPos.getY() + localBox.getMaxY() + 1.0D;
		double maxZ = anchorPos.getZ() + localBox.getMaxZ() + 1.0D;

		Box renderBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
		Box anchorBox = new Box(anchorPos);

		// 7. Verify matrix stack and camera
		MatrixStack matrices = context.matrixStack();
		Camera camera = context.camera();
		if (matrices == null || camera == null) {
			return;
		}

		Vec3d cameraPos = camera.getPos();
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
		}
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

		// 8. Transform matrix stack relative to camera position
		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		// Faint block-level schematic wireframes (cyan / aqua)
		for (BlueprintBlock block : blueprint.getBlocks()) {
			BlockPos bPos = anchorPos.add(block.offset());
			WorldRenderer.drawBox(
				matrices,
				buffer,
				new Box(bPos),
				0.0F, 0.70F, 0.95F, 0.35F
			);
		}

		// Gold anchor box marking the blueprint origin
		WorldRenderer.drawBox(
			matrices,
			buffer,
			anchorBox,
			1.0F, 0.84F, 0.0F, 1.0F
		);

		// Neon cyan blueprint 3D wireframe outline box
		WorldRenderer.drawBox(
			matrices,
			buffer,
			renderBox,
			0.0F, 0.85F, 1.0F, 0.90F
		);

		matrices.pop();

		// Flush lines layer immediately to GPU if buffer is immediate
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}
	}
}
