package com.example.client.renderer;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.client.camera.TacticalBuildCameraController;
import com.example.component.CommandMode;
import com.example.component.MiningMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EndRodBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TintedGlassBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallTorchBlock;
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
 * Client-side holographic 3D wireframe preview renderer for architectural blueprints and DESIGN mode spatial capture.
 * Features semantic color-coded ghost block outlines:
 * - 🚪 Doors & Entrances: Bright Emerald Green 2-block portal box.
 * - 🏮 Lighting: Warm Amber Gold.
 * - 📦 Containers, Beds & Workstations: Arcane Purple.
 * - 🧱 Structural Walls & Pillars: Neon Cyan Blue.
 * - 🏠 Roofing & Stairs: Soft Ice Blue.
 * - 📐 In-World DESIGN Mode: Holographic spatial bounding volume with Pos1/Pos2 corner markers and live vertex guides.
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
		boolean isDesignMode = scepterStack != null && CommandScepterItem.getMode(scepterStack) == CommandMode.DESIGN;
		MiningMode miningMode = scepterStack != null ? CommandScepterItem.getMiningMode(scepterStack) : MiningMode.AREA;
		boolean isMineAreaMode = isMineMode && miningMode == MiningMode.AREA;
		boolean isMineDirectMode = isMineMode && miningMode == MiningMode.DIRECT;

		if (isBuildMode || isMineDirectMode) {
			BlockHitResult hitResult = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
			if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
				if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
					hitResult = bhr;
				}
			}

			if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
				BlockPos clickedPos = hitResult.getBlockPos();
				Direction side = hitResult.getSide();
				if (isMineDirectMode) {
					previewAnchorPos = clickedPos;
				} else {
					previewAnchorPos = client.world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
				}

				String blueprintId = CommandScepterItem.getBlueprintId(scepterStack);
				StructureBlueprint bp = BlueprintRegistry.get(blueprintId).orElse(null);

				if (bp != null && bp.getBlockCount() > 0) {
					previewBlueprint = bp.rotate(CommandScepterItem.getRotation(scepterStack));
					showPreview = true;
				}
			}
		}

		BlockPos p1 = ClientDesignCaptureTracker.getPos1();
		BlockPos p2 = ClientDesignCaptureTracker.getPos2();
		boolean hasDesignHologram = isDesignMode && (p1 != null || p2 != null);

		BlockHitResult designTargetHit = null;
		if (isDesignMode && p1 != null && p2 == null) {
			BlockHitResult hit = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
			if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
				if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
					hit = bhr;
				}
			}
			if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
				designTargetHit = hit;
			}
		}

		BlockPos mineP1 = ClientMiningCaptureTracker.getPos1();
		BlockPos mineP2 = ClientMiningCaptureTracker.getPos2();
		boolean hasMiningHologram = isMineAreaMode && (mineP1 != null || mineP2 != null);

		BlockHitResult miningTargetHit = null;
		if (isMineAreaMode && mineP1 != null && mineP2 == null) {
			BlockHitResult hit = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
			if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
				if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
					hit = bhr;
				}
			}
			if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
				miningTargetHit = hit;
			}
		}

		if (activeSessions.isEmpty() && !showPreview && !hasDesignHologram && !hasMiningHologram) {
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
			StructureBlueprint bp = BlueprintRegistry.get(sessionData.blueprintId()).orElse(null);
			if (bp != null && bp.getBlockCount() > 0) {
				StructureBlueprint rotatedBp = bp.rotate(sessionData.rotation());
				if (sessionData.isDismantle()) {
					renderStructureHologram(matrices, buffer, sessionData.anchorPos(), rotatedBp, 1.0F, 0.35F, 0.10F, 0.85F, true, client.world, true);
				} else {
					renderStructureHologram(matrices, buffer, sessionData.anchorPos(), rotatedBp, 0.0F, 0.85F, 1.0F, 0.85F, true, client.world, false);
				}
			} else if (sessionData.isDismantle() && sessionData.sizeX() > 0 && sessionData.sizeY() > 0 && sessionData.sizeZ() > 0) {
				// Persistent fiery quarry wireframe & ghost grid for active area mining sessions
				int minX = sessionData.anchorPos().getX();
				int minY = sessionData.anchorPos().getY();
				int minZ = sessionData.anchorPos().getZ();
				int maxX = minX + sessionData.sizeX() - 1;
				int maxY = minY + sessionData.sizeY() - 1;
				int maxZ = minZ + sessionData.sizeZ() - 1;
				Box quarryBox = new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
				renderMiningBoundingBox(matrices, buffer, quarryBox, 1.0F, 0.45F, 0.05F, 0.85F, sessionData.anchorPos(), new BlockPos(maxX, maxY, maxZ));
				renderMiningGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 1.0F);
			}
		}

		// 2. Crosshair placement preview (BUILD & MINE)
		if (showPreview && previewAnchorPos != null && previewBlueprint != null) {
			if (isMineMode) {
				renderStructureHologram(matrices, buffer, previewAnchorPos, previewBlueprint, 1.0F, 0.35F, 0.10F, 0.90F, true, client.world, true);
			} else {
				renderStructureHologram(matrices, buffer, previewAnchorPos, previewBlueprint, 0.0F, 0.85F, 1.0F, 0.90F, true, client.world, false);
			}
		}

		// 3. DESIGN Mode In-World Spatial Capture Hologram
		if (isDesignMode) {
			if (ClientDesignCaptureTracker.hasCompleteSelection()) {
				Box designBox = ClientDesignCaptureTracker.getBox();
				if (designBox != null) {
					renderDesignBoundingBox(matrices, buffer, designBox, 0.95F, 0.35F, 1.0F, 0.85F, p1, p2);
					int minX = Math.min(p1.getX(), p2.getX());
					int minY = Math.min(p1.getY(), p2.getY());
					int minZ = Math.min(p1.getZ(), p2.getZ());
					int maxX = Math.max(p1.getX(), p2.getX());
					int maxY = Math.max(p1.getY(), p2.getY());
					int maxZ = Math.max(p1.getZ(), p2.getZ());
					renderDesignGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 1.0F);
				}
			} else if (p1 != null) {
				if (designTargetHit != null) {
					BlockPos hitPos = designTargetHit.getBlockPos();
					BlockPos targetPos = (client.world != null && client.world.getBlockState(hitPos).isReplaceable())
						? hitPos
						: (designTargetHit.getSide() != null ? hitPos.offset(designTargetHit.getSide()) : hitPos);
					int minX = Math.min(p1.getX(), targetPos.getX());
					int minY = Math.min(p1.getY(), targetPos.getY());
					int minZ = Math.min(p1.getZ(), targetPos.getZ());
					int maxX = Math.max(p1.getX(), targetPos.getX());
					int maxY = Math.max(p1.getY(), targetPos.getY());
					int maxZ = Math.max(p1.getZ(), targetPos.getZ());
					Box candidateBox = new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
					renderDesignBoundingBox(matrices, buffer, candidateBox, 0.85F, 0.40F, 1.0F, 0.55F, p1, targetPos);
					renderDesignGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 0.65F);
				} else {
					// Render Pos1 anchor box (bright magenta) when awaiting target block
					WorldRenderer.drawBox(matrices, buffer, new Box(p1), 1.0F, 0.20F, 0.85F, 0.95F);
				}
			} else if (p2 != null) {
				// Render Pos2 anchor box (bright cyan)
				WorldRenderer.drawBox(matrices, buffer, new Box(p2), 0.20F, 0.90F, 1.0F, 0.95F);
			}
		}

		// 4. MINE AREA Mode In-World Quarry Bounding Volume & Fiery Ghost Grid Hologram
		if (isMineAreaMode) {
			if (ClientMiningCaptureTracker.hasCompleteSelection()) {
				Box miningBox = ClientMiningCaptureTracker.getBox();
				if (miningBox != null) {
					renderMiningBoundingBox(matrices, buffer, miningBox, 1.0F, 0.45F, 0.05F, 0.85F, mineP1, mineP2);
					int minX = Math.min(mineP1.getX(), mineP2.getX());
					int minY = Math.min(mineP1.getY(), mineP2.getY());
					int minZ = Math.min(mineP1.getZ(), mineP2.getZ());
					int maxX = Math.max(mineP1.getX(), mineP2.getX());
					int maxY = Math.max(mineP1.getY(), mineP2.getY());
					int maxZ = Math.max(mineP1.getZ(), mineP2.getZ());
					renderMiningGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 1.0F);
				}
			} else if (mineP1 != null) {
				if (miningTargetHit != null) {
					BlockPos hitPos = miningTargetHit.getBlockPos();
					BlockPos targetPos = (client.world != null && client.world.getBlockState(hitPos).isReplaceable())
						? hitPos
						: (miningTargetHit.getSide() != null ? hitPos.offset(miningTargetHit.getSide()) : hitPos);
					int minX = Math.min(mineP1.getX(), targetPos.getX());
					int minY = Math.min(mineP1.getY(), targetPos.getY());
					int minZ = Math.min(mineP1.getZ(), targetPos.getZ());
					int maxX = Math.max(mineP1.getX(), targetPos.getX());
					int maxY = Math.max(mineP1.getY(), targetPos.getY());
					int maxZ = Math.max(mineP1.getZ(), targetPos.getZ());
					Box candidateBox = new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
					renderMiningBoundingBox(matrices, buffer, candidateBox, 1.0F, 0.55F, 0.10F, 0.55F, mineP1, targetPos);
					renderMiningGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 0.65F);
				} else {
					// Render Pos1 anchor box (bright fiery orange/amber) when awaiting target block
					WorldRenderer.drawBox(matrices, buffer, new Box(mineP1), 1.0F, 0.40F, 0.05F, 0.95F);
				}
			} else if (mineP2 != null) {
				// Render Pos2 anchor box (bright amber gold)
				WorldRenderer.drawBox(matrices, buffer, new Box(mineP2), 1.0F, 0.80F, 0.10F, 0.95F);
			}
		}

		matrices.pop();

		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}
	}

	private static void renderDesignBoundingBox(
		MatrixStack matrices,
		VertexConsumer buffer,
		Box box,
		float r,
		float g,
		float b,
		float a,
		BlockPos p1,
		BlockPos p2
	) {
		// Main glowing bounding box
		WorldRenderer.drawBox(matrices, buffer, box, r, g, b, a);

		// Corner 1 box (Magenta)
		if (p1 != null) {
			WorldRenderer.drawBox(matrices, buffer, new Box(p1), 1.0F, 0.20F, 0.85F, 0.95F);
		}

		// Corner 2 box (Cyan)
		if (p2 != null) {
			WorldRenderer.drawBox(matrices, buffer, new Box(p2), 0.20F, 0.90F, 1.0F, 0.95F);
		}

		// Vertex corner accent boxes at all 8 corners of the bounding box
		double[][] corners = {
			{ box.minX, box.minY, box.minZ },
			{ box.maxX, box.minY, box.minZ },
			{ box.minX, box.minY, box.maxZ },
			{ box.maxX, box.minY, box.maxZ },
			{ box.minX, box.maxY, box.minZ },
			{ box.maxX, box.maxY, box.minZ },
			{ box.minX, box.maxY, box.maxZ },
			{ box.maxX, box.maxY, box.maxZ }
		};

		for (double[] c : corners) {
			Box cornerBox = new Box(c[0] - 0.05D, c[1] - 0.05D, c[2] - 0.05D, c[0] + 0.05D, c[1] + 0.05D, c[2] + 0.05D);
			WorldRenderer.drawBox(matrices, buffer, cornerBox, 1.0F, 0.85F, 0.20F, 0.80F);
		}
	}

	private static void renderMiningBoundingBox(
		MatrixStack matrices,
		VertexConsumer buffer,
		Box box,
		float r,
		float g,
		float b,
		float a,
		BlockPos p1,
		BlockPos p2
	) {
		// Main glowing fiery bounding box
		WorldRenderer.drawBox(matrices, buffer, box, r, g, b, a);

		// Corner 1 box (Fiery Blaze Orange)
		if (p1 != null) {
			WorldRenderer.drawBox(matrices, buffer, new Box(p1), 1.0F, 0.35F, 0.05F, 0.95F);
		}

		// Corner 2 box (Glowing Amber Gold)
		if (p2 != null) {
			WorldRenderer.drawBox(matrices, buffer, new Box(p2), 1.0F, 0.80F, 0.10F, 0.95F);
		}

		// Vertex corner accent boxes at all 8 corners of the bounding box
		double[][] corners = {
			{ box.minX, box.minY, box.minZ },
			{ box.maxX, box.minY, box.minZ },
			{ box.minX, box.minY, box.maxZ },
			{ box.maxX, box.minY, box.maxZ },
			{ box.minX, box.maxY, box.minZ },
			{ box.maxX, box.maxY, box.minZ },
			{ box.minX, box.maxY, box.maxZ },
			{ box.maxX, box.maxY, box.maxZ }
		};

		for (double[] c : corners) {
			Box cornerBox = new Box(c[0] - 0.05D, c[1] - 0.05D, c[2] - 0.05D, c[0] + 0.05D, c[1] + 0.05D, c[2] + 0.05D);
			WorldRenderer.drawBox(matrices, buffer, cornerBox, 1.0F, 0.70F, 0.05F, 0.85F);
		}
	}

	/**
	 * Renders in-world fiery orange/amber holographic ghost grid blocks and wireframe outlines
	 * within a specified 3D spatial coordinate volume in MINE AREA mode.
	 *
	 * @param matrices The rendering matrix stack translated to world coordinates.
	 * @param buffer The line vertex consumer buffer.
	 * @param world The client world instance to sample block states from.
	 * @param minX Minimum X block coordinate.
	 * @param minY Minimum Y block coordinate.
	 * @param minZ Minimum Z block coordinate.
	 * @param maxX Maximum X block coordinate.
	 * @param maxY Maximum Y block coordinate.
	 * @param maxZ Maximum Z block coordinate.
	 * @param alphaMultiplier Opacity scaling factor for candidate vs confirmed selections.
	 */
	public static void renderMiningGhostGrid(
		MatrixStack matrices,
		VertexConsumer buffer,
		net.minecraft.world.World world,
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ,
		float alphaMultiplier
	) {
		if (world == null) {
			return;
		}

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;
		if (sizeX > ClientMiningCaptureTracker.MAX_DIMENSION
			|| sizeY > ClientMiningCaptureTracker.MAX_HEIGHT
			|| sizeZ > ClientMiningCaptureTracker.MAX_DIMENSION
			|| (long) sizeX * sizeY * sizeZ > ClientMiningCaptureTracker.MAX_VOLUME) {
			return;
		}

		BlockPos.Mutable mutPos = new BlockPos.Mutable();
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutPos.set(x, y, z);
					BlockState state = world.getBlockState(mutPos);
					if (state.isAir()) {
						continue;
					}

					float hardness = state.getHardness(world, mutPos);
					if (hardness < 0.0F) {
						// Indestructible / Bedrock: safeguard outline (subtle dark barrier gray)
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 0.35F, 0.35F, 0.35F, 0.25F * alphaMultiplier);
						continue;
					}

					Block blk = state.getBlock();

					// 💎 VALUABLE ORES & MINERALS: Brilliant Glowing Gold/Amber
					if (blk == Blocks.COAL_ORE || blk == Blocks.DEEPSLATE_COAL_ORE ||
						blk == Blocks.IRON_ORE || blk == Blocks.DEEPSLATE_IRON_ORE ||
						blk == Blocks.COPPER_ORE || blk == Blocks.DEEPSLATE_COPPER_ORE ||
						blk == Blocks.GOLD_ORE || blk == Blocks.DEEPSLATE_GOLD_ORE ||
						blk == Blocks.REDSTONE_ORE || blk == Blocks.DEEPSLATE_REDSTONE_ORE ||
						blk == Blocks.LAPIS_ORE || blk == Blocks.DEEPSLATE_LAPIS_ORE ||
						blk == Blocks.DIAMOND_ORE || blk == Blocks.DEEPSLATE_DIAMOND_ORE ||
						blk == Blocks.EMERALD_ORE || blk == Blocks.DEEPSLATE_EMERALD_ORE ||
						blk == Blocks.NETHER_QUARTZ_ORE || blk == Blocks.NETHER_GOLD_ORE ||
						blk == Blocks.ANCIENT_DEBRIS || blk == Blocks.RAW_IRON_BLOCK ||
						blk == Blocks.RAW_COPPER_BLOCK || blk == Blocks.RAW_GOLD_BLOCK) {
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 1.0F, 0.85F, 0.10F, 0.80F * alphaMultiplier);
						continue;
					}

					// 🧱 GENERAL DESTRUCTIBLE BLOCKS (Stone, Dirt, Wood, Sand, Deepslate, etc.): Fiery Orange/Amber Grid
					WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 1.0F, 0.45F, 0.05F, 0.35F * alphaMultiplier);
				}
			}
		}
	}

	/**
	 * Renders in-world holographic ghost grid blocks and semantic color-coded wireframe outlines
	 * within a specified 3D spatial coordinate volume in DESIGN mode.
	 *
	 * @param matrices The rendering matrix stack translated to world coordinates.
	 * @param buffer The line vertex consumer buffer.
	 * @param world The client world instance to sample block states from.
	 * @param minX Minimum X block coordinate.
	 * @param minY Minimum Y block coordinate.
	 * @param minZ Minimum Z block coordinate.
	 * @param maxX Maximum X block coordinate.
	 * @param maxY Maximum Y block coordinate.
	 * @param maxZ Maximum Z block coordinate.
	 * @param alphaMultiplier Opacity scaling factor for candidate vs confirmed selections.
	 */
	public static void renderDesignGhostGrid(
		MatrixStack matrices,
		VertexConsumer buffer,
		net.minecraft.world.World world,
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ,
		float alphaMultiplier
	) {
		if (world == null) {
			return;
		}

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;
		if (sizeX > ClientDesignCaptureTracker.MAX_DIMENSION
			|| sizeY > ClientDesignCaptureTracker.MAX_HEIGHT
			|| sizeZ > ClientDesignCaptureTracker.MAX_DIMENSION
			|| (long) sizeX * sizeY * sizeZ > ClientDesignCaptureTracker.MAX_VOLUME) {
			return;
		}

		BlockPos.Mutable mutPos = new BlockPos.Mutable();
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutPos.set(x, y, z);
					BlockState state = world.getBlockState(mutPos);
					if (state.isAir()) {
						continue;
					}

					Block blk = state.getBlock();

					// 🚪 DOORS: Bright Emerald Green full 2-block portal box
					if (blk instanceof DoorBlock) {
						if (state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
							Box doorPortalBox = new Box(x, y, z, x + 1.0D, y + 2.0D, z + 1.0D);
							WorldRenderer.drawBox(matrices, buffer, doorPortalBox, 0.0F, 1.0F, 0.53F, 0.90F * alphaMultiplier);
						}
						continue;
					}

					// 🏮 LIGHTING: Warm Amber Gold
					if (blk instanceof LanternBlock || blk instanceof TorchBlock || blk instanceof WallTorchBlock || blk instanceof CampfireBlock ||
						blk == Blocks.GLOWSTONE || blk == Blocks.SEA_LANTERN || blk == Blocks.OCHRE_FROGLIGHT || blk == Blocks.VERDANT_FROGLIGHT ||
						blk == Blocks.PEARLESCENT_FROGLIGHT || blk instanceof RedstoneLampBlock || blk instanceof EndRodBlock) {
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 1.0F, 0.80F, 0.0F, 0.70F * alphaMultiplier);
						continue;
					}

					// 📦 CONTAINERS, BEDS & WORKSTATIONS: Arcane Purple
					if (blk instanceof BedBlock || blk == Blocks.CHEST || blk == Blocks.TRAPPED_CHEST || blk == Blocks.BARREL ||
						blk == Blocks.FURNACE || blk == Blocks.BLAST_FURNACE || blk == Blocks.SMOKER ||
						blk == Blocks.CRAFTING_TABLE || blk instanceof AnvilBlock ||
						blk == Blocks.GRINDSTONE || blk == Blocks.SMITHING_TABLE || blk == Blocks.LOOM || blk == Blocks.CARTOGRAPHY_TABLE ||
						blk == Blocks.BREWING_STAND || blk == Blocks.ENCHANTING_TABLE || blk == Blocks.ENDER_CHEST ||
						blk == Blocks.HOPPER || blk == Blocks.DISPENSER || blk == Blocks.DROPPER || blk instanceof ShulkerBoxBlock) {
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 0.70F, 0.25F, 1.0F, 0.75F * alphaMultiplier);
						continue;
					}

					// 🏠 ROOFING, STAIRS & SLABS: Soft Ice Blue
					if (blk instanceof StairsBlock || blk instanceof SlabBlock) {
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 0.45F, 0.75F, 1.0F, 0.40F * alphaMultiplier);
						continue;
					}

					// 🛡 DEFENSES, WALLS, FENCES & GATES: Arcane Orange-Gold
					if (blk instanceof WallBlock || blk instanceof FenceBlock || blk instanceof FenceGateBlock || blk == Blocks.IRON_BARS) {
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 1.0F, 0.60F, 0.15F, 0.50F * alphaMultiplier);
						continue;
					}

					// 🪟 WINDOWS & GLASS: Crystal Cyan
					if (blk == Blocks.GLASS || blk instanceof StainedGlassBlock || blk instanceof PaneBlock || blk instanceof TintedGlassBlock) {
						WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 0.30F, 0.95F, 0.90F, 0.35F * alphaMultiplier);
						continue;
					}

					// 🧱 GENERAL WALLS, GROUND & FOUNDATION: Vibrant Arcane Cyan-Magenta Grid
					WorldRenderer.drawBox(matrices, buffer, new Box(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), 0.85F, 0.35F, 1.0F, 0.30F * alphaMultiplier);
				}
			}
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
						if (worldState.isOf(block.state().getBlock())) {
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
				if (blk instanceof LanternBlock || blk instanceof TorchBlock || blk instanceof WallTorchBlock || blk instanceof CampfireBlock ||
					blk == Blocks.GLOWSTONE || blk == Blocks.SEA_LANTERN || blk == Blocks.OCHRE_FROGLIGHT || blk == Blocks.VERDANT_FROGLIGHT ||
					blk == Blocks.PEARLESCENT_FROGLIGHT || blk instanceof RedstoneLampBlock || blk instanceof EndRodBlock) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 1.0F, 0.80F, 0.0F, 0.70F);
					continue;
				}

				// 📦 CONTAINERS, BEDS & WORKSTATIONS: Arcane Purple
				if (blk instanceof BedBlock || blk == Blocks.CHEST || blk == Blocks.TRAPPED_CHEST || blk == Blocks.BARREL ||
					blk == Blocks.FURNACE || blk == Blocks.BLAST_FURNACE || blk == Blocks.SMOKER ||
					blk == Blocks.CRAFTING_TABLE || blk instanceof AnvilBlock ||
					blk == Blocks.GRINDSTONE || blk == Blocks.SMITHING_TABLE || blk == Blocks.LOOM || blk == Blocks.CARTOGRAPHY_TABLE ||
					blk == Blocks.BREWING_STAND || blk == Blocks.ENCHANTING_TABLE || blk == Blocks.ENDER_CHEST ||
					blk == Blocks.HOPPER || blk == Blocks.DISPENSER || blk == Blocks.DROPPER || blk instanceof ShulkerBoxBlock) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 0.70F, 0.25F, 1.0F, 0.75F);
					continue;
				}

				// 🏠 ROOFING, STAIRS & SLABS: Soft Ice Blue
				if (blk instanceof StairsBlock || blk instanceof SlabBlock) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 0.45F, 0.75F, 1.0F, 0.40F);
					continue;
				}

				// 🛡 DEFENSES, WALLS, FENCES & GATES: Arcane Orange-Gold
				if (blk instanceof WallBlock || blk instanceof FenceBlock || blk instanceof FenceGateBlock || blk == Blocks.IRON_BARS) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 1.0F, 0.60F, 0.15F, 0.50F);
					continue;
				}

				// 🪟 WINDOWS & GLASS: Crystal Cyan
				if (blk == Blocks.GLASS || blk instanceof StainedGlassBlock || blk instanceof PaneBlock || blk instanceof TintedGlassBlock) {
					WorldRenderer.drawBox(matrices, buffer, new Box(bPos), 0.30F, 0.95F, 0.90F, 0.35F);
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
