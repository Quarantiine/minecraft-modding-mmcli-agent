package com.example.client.renderer;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import java.util.Collection;
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

		MatrixStack matrices = context.matrixStack();
		Camera camera = context.camera();
		if (matrices == null || camera == null) {
			return;
		}

		Collection<ClientConstructionTracker.ActiveSessionClientData> activeSessions = ClientConstructionTracker.getActiveSessions();

		// Check whether player is holding scepter in BUILD mode for crosshair preview
		ClientPlayerEntity player = client.player;
		ItemStack scepterStack = null;
		if (player.getMainHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getMainHandStack();
		} else if (player.getOffHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getOffHandStack();
		}

		boolean showPreview = false;
		BlockPos previewAnchorPos = null;
		StructureBlueprint previewBlueprint = null;
		boolean isMineMode = scepterStack != null && CommandScepterItem.getMode(scepterStack) == CommandMode.MINE;
		boolean isBuildMode = scepterStack != null && CommandScepterItem.getMode(scepterStack) == CommandMode.BUILD;

		if (isBuildMode || isMineMode) {
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

			if (hitResult != null) {
				BlockPos clickedPos = hitResult.getBlockPos();
				Direction side = hitResult.getSide();
				if (isMineMode) {
					previewAnchorPos = clickedPos;
				} else {
					previewAnchorPos = client.world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
				}

				String blueprintId = CommandScepterItem.getBlueprintId(scepterStack);
				StructureBlueprint bp = BlueprintRegistry.getOrDefault(blueprintId);
				if (bp != null && bp.getBlockCount() > 0) {
					previewBlueprint = bp.rotate(CommandScepterItem.getRotation(scepterStack));
					showPreview = true;
				}
			}
		}

		// If no active sessions and no active scepter preview, skip matrix push & buffer allocation
		if (activeSessions.isEmpty() && !showPreview) {
			return;
		}

		Vec3d cameraPos = camera.getPos();
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
		}
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		// 1. Render persistent 3D holographic wireframes for all active in-world construction sessions
		for (ClientConstructionTracker.ActiveSessionClientData sessionData : activeSessions) {
			StructureBlueprint bp = BlueprintRegistry.getOrDefault(sessionData.blueprintId());
			if (bp != null && bp.getBlockCount() > 0) {
				StructureBlueprint rotatedBp = bp.rotate(sessionData.rotation());
				if (sessionData.isDismantle()) {
					// Fiery orange/red outline for active dismantling sessions
					renderStructureHologram(matrices, buffer, sessionData.anchorPos(), rotatedBp, 1.0F, 0.35F, 0.10F, 0.85F, false);
				} else {
					// Neon cyan outline with ghost block schematics for active building sessions
					renderStructureHologram(matrices, buffer, sessionData.anchorPos(), rotatedBp, 0.0F, 0.85F, 1.0F, 0.85F, true);
				}
			}
		}

		// 2. Render placement or mine preview at crosshair if scepter is actively in BUILD or MINE mode
		if (showPreview && previewAnchorPos != null && previewBlueprint != null) {
			if (isMineMode) {
				renderStructureHologram(matrices, buffer, previewAnchorPos, previewBlueprint, 1.0F, 0.35F, 0.10F, 0.90F, false);
			} else {
				renderStructureHologram(matrices, buffer, previewAnchorPos, previewBlueprint, 0.0F, 0.85F, 1.0F, 0.90F, true);
			}
		}

		matrices.pop();

		// Flush lines layer immediately to GPU if buffer is immediate
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}
	}

	/**
	 * Renders the 3D wireframe bounding box, origin anchor box, and optional ghost blocks for a structure.
	 */
	private static void renderStructureHologram(
		MatrixStack matrices,
		VertexConsumer buffer,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		float r,
		float g,
		float b,
		float a,
		boolean renderGhostBlocks
	) {
		BlockBox localBox = blueprint.getBoundingBox();
		double minX = anchorPos.getX() + localBox.getMinX();
		double minY = anchorPos.getY() + localBox.getMinY();
		double minZ = anchorPos.getZ() + localBox.getMinZ();
		double maxX = anchorPos.getX() + localBox.getMaxX() + 1.0D;
		double maxY = anchorPos.getY() + localBox.getMaxY() + 1.0D;
		double maxZ = anchorPos.getZ() + localBox.getMaxZ() + 1.0D;

		Box renderBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
		Box anchorBox = new Box(anchorPos);

		// Ghost block outlines
		if (renderGhostBlocks) {
			for (BlueprintBlock block : blueprint.getBlocks()) {
				BlockPos bPos = anchorPos.add(block.offset());
				WorldRenderer.drawBox(
					matrices,
					buffer,
					new Box(bPos),
					r * 0.8F,
					g * 0.8F,
					b * 0.8F,
					0.25F
				);
			}
		}

		// Gold origin anchor box
		WorldRenderer.drawBox(
			matrices,
			buffer,
			anchorBox,
			1.0F,
			0.84F,
			0.0F,
			0.95F
		);

		// Main bounding box outline
		WorldRenderer.drawBox(
			matrices,
			buffer,
			renderBox,
			r,
			g,
			b,
			a
		);
	}
}
