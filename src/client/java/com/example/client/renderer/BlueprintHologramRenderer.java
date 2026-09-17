package com.example.client.renderer;

import com.example.blueprint.ArchitectureStyle;
import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.BuildingCategory;
import com.example.blueprint.DynamicBuildingResolver;
import com.example.blueprint.StructureBlueprint;
import com.example.client.camera.TacticalBuildCameraController;
import com.example.component.CommandMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
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
 * Features semantic color-coded ghost block outlines:
 * - 🚪 Doors & Entrances: Bright Emerald Green 2-block portal box.
 * - 🏮 Lighting: Warm Amber Gold.
 * - 📦 Containers, Beds & Workstations: Arcane Purple.
 * - 🧱 Structural Walls & Pillars: Neon Cyan Blue.
 * - 🏠 Roofing & Stairs: Soft Ice Blue.
 */
public class BlueprintHologramRenderer {

	private static final double MAX_PREVIEW_REACH = 24.0D;

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(BlueprintHologramRenderer::render);
	}

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
			BlockHitResult hitResult = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
			if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
				if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
					hitResult = bhr;
				}
			}

			if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
				BlockPos clickedPos = hitResult.getBlockPos();
				Direction side = hitResult.getSide();
				if (isMineMode) {
					previewAnchorPos = clickedPos;
				} else {
					previewAnchorPos = client.world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
				}

				String blueprintId = CommandScepterItem.getBlueprintId(scepterStack);
				int size = CommandScepterItem.getBuildingSize(scepterStack);
				ArchitectureStyle style = CommandScepterItem.getArchitectureStyle(scepterStack);

				BuildingCategory cat = null;
				for (BuildingCategory c : BuildingCategory.values()) {
					if (c.getId().equalsIgnoreCase(blueprintId) || blueprintId.toLowerCase().startsWith(c.getId().toLowerCase())) {
						cat = c;
						break;
					}
				}

				StructureBlueprint bp;
				long seed = previewAnchorPos.asLong() ^ (long) blueprintId.hashCode() ^ (long) style.ordinal();
				if (cat != null) {
					bp = cat.createBlueprint(size, seed);
				} else {
					bp = BlueprintRegistry.getOrDefault(blueprintId);
				}

				if (bp != null && bp.getBlockCount() > 0) {
					previewBlueprint = bp.rotate(CommandScepterItem.getRotation(scepterStack));
					if (isBuildMode) {
						previewBlueprint = DynamicBuildingResolver.resolve(previewBlueprint, client.world, previewAnchorPos, style);
					}
					showPreview = true;
				}
			}
		}

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

		// 1. In-world active construction sessions
		for (ClientConstructionTracker.ActiveSessionClientData sessionData : activeSessions) {
			StructureBlueprint bp = BlueprintRegistry.getOrDefault(sessionData.blueprintId());
			if (bp != null && bp.getBlockCount() > 0) {
				StructureBlueprint rotatedBp = bp.rotate(sessionData.rotation());
				if (!sessionData.isDismantle() && client.world != null) {
					rotatedBp = DynamicBuildingResolver.resolve(rotatedBp, client.world, sessionData.anchorPos(), ArchitectureStyle.BIOME_NATIVE);
				}
				if (sessionData.isDismantle()) {
					renderStructureHologram(matrices, buffer, sessionData.anchorPos(), rotatedBp, 1.0F, 0.35F, 0.10F, 0.85F, true, client.world, true);
				} else {
					renderStructureHologram(matrices, buffer, sessionData.anchorPos(), rotatedBp, 0.0F, 0.85F, 1.0F, 0.85F, true, client.world, false);
				}
			}
		}

		// 2. Crosshair placement preview
		if (showPreview && previewAnchorPos != null && previewBlueprint != null) {
			if (isMineMode) {
				renderStructureHologram(matrices, buffer, previewAnchorPos, previewBlueprint, 1.0F, 0.35F, 0.10F, 0.90F, true, client.world, true);
			} else {
				renderStructureHologram(matrices, buffer, previewAnchorPos, previewBlueprint, 0.0F, 0.85F, 1.0F, 0.90F, true, client.world, false);
			}
		}

		matrices.pop();

		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}
	}

	private static void renderStructureHologram(
		MatrixStack matrices,
		VertexConsumer buffer,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		float r,
		float g,
		float b,
		float a,
		boolean renderGhostBlocks,
		net.minecraft.world.World world,
		boolean isDismantle
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

		// Semantic Color-Coded Ghost Block Outlines
		if (renderGhostBlocks) {
			for (BlueprintBlock block : blueprint.getBlocks()) {
				BlockPos bPos = anchorPos.add(block.offset());

				// Real-time per-block ghost grid disappearance:
				// As each block is placed in the world, its ghost wireframe box vanishes instantly!
				if (world != null) {
					BlockState worldState = world.getBlockState(bPos);
					if (isDismantle) {
						if (worldState.isAir() || worldState.isReplaceable()) {
							continue; // Already dismantled / cleared!
						}
					} else {
						if (worldState.isOf(block.state().getBlock()) || (!worldState.isAir() && !worldState.isReplaceable() && !worldState.isLiquid())) {
							continue; // Already placed in world!
						}
					}
				}

				if (isDismantle) {
					// Dismantle outline: warm red/orange box around remaining blocks to clear
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 1.0F, 0.30F, 0.10F, 0.45F);
					continue;
				}

				Block blk = block.state().getBlock();

				// 🚪 DOORS: Bright Emerald Green full 2-block portal box
				if (blk instanceof DoorBlock) {
					if (block.state().get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
						Box doorPortalBox = new Box(bPos.getX(), bPos.getY(), bPos.getZ(), bPos.getX() + 1.0D, bPos.getY() + 2.0D, bPos.getZ() + 1.0D);
						WorldRenderer.drawBox(matrices, buffer, doorPortalBox, 0.0F, 1.0F, 0.53F, 0.90F);
					}
					continue;
				}

				// 🏮 LIGHTING: Warm Amber Gold
				if (blk instanceof LanternBlock || blk instanceof TorchBlock || blk instanceof CampfireBlock) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 1.0F, 0.80F, 0.0F, 0.70F);
					continue;
				}

				// 📦 CONTAINERS, BEDS & WORKSTATIONS: Arcane Purple
				if (blk instanceof BedBlock || blk == Blocks.CHEST || blk == Blocks.BARREL || blk == Blocks.FURNACE ||
					blk == Blocks.BLAST_FURNACE || blk == Blocks.CRAFTING_TABLE || blk == Blocks.ANVIL ||
					blk == Blocks.GRINDSTONE || blk == Blocks.SMITHING_TABLE || blk == Blocks.LOOM || blk == Blocks.CARTOGRAPHY_TABLE) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 0.70F, 0.25F, 1.0F, 0.75F);
					continue;
				}

				// 🏠 ROOFING & STAIRS: Soft Ice Blue
				if (blk instanceof StairsBlock) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 0.45F, 0.75F, 1.0F, 0.40F);
					continue;
				}

				// 🧱 GENERAL WALLS & FOUNDATION: Neon Cyan Blue
				WorldRenderer.drawBox(matrices, buffer, new Box(bPos), r * 0.8F, g * 0.8F, b * 0.8F, 0.30F);
			}
		}

		// Gold origin anchor box
		WorldRenderer.drawBox(matrices, buffer, anchorBox, 1.0F, 0.84F, 0.0F, 0.95F);

		// Main bounding box outline
		WorldRenderer.drawBox(matrices, buffer, renderBox, r, g, b, a);
	}
}
