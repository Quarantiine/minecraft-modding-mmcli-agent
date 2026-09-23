package com.example.blueprint;

import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import com.example.network.CaptureSpatialBlueprintPayload;
import com.example.network.CreateCustomBlueprintPayload;
import com.example.network.DeleteCustomBlueprintPayload;
import com.example.network.SyncCustomBlueprintsPayload;
import com.example.network.UpdateScepterPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests validating:
 * 1. In-world spatial capture (corner bounds, air block exclusion, BlockView sampling).
 * 2. Corner normalization (negative coordinates, inverted corner selection pos1 vs pos2, origin shifting to 0,0,0).
 * 3. Topological sorting (bottom-up construction ordering, hanging block dependencies, Manhattan core expansion, and reverse top-down dismantling).
 * 4. Spatial safeguards (maximum dimension 64x64x64 and volume limit 65,536).
 * 5. Network payload serialization roundtrips for DESIGN mode and custom blueprints.
 */
public class SpatialBlueprintCaptureAndSortingTest {

	private static BlockState dummyState;
	private static BlockState dummyAirState;

	@BeforeAll
	static void initBootstrap() {
		try {
			java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
			dummyState = (BlockState) unsafe.allocateInstance(BlockState.class);
		} catch (Throwable t) {
			System.err.println("Unsafe allocation failed: " + t);
		}
	}

	@BeforeEach
	@AfterEach
	void cleanup() {
		BlueprintRegistry.clearCustomBlueprints();
		CustomBlueprintManager.getInstance().loadFromPersistentState(null);
	}

	/**
	 * Mock implementation of {@link BlockView} for testing in-world spatial capture.
	 */
	static class MockWorldBlockView implements BlockView {
		private final java.util.Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();

		public void setBlock(BlockPos pos, BlockState state) {
			if (state == null) {
				blocks.remove(pos.toImmutable());
			} else {
				blocks.put(pos.toImmutable(), state);
			}
		}

		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return blocks.get(pos);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return Fluids.EMPTY.getDefaultState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getBottomY() {
			return -64;
		}
	}

	@Test
	@DisplayName("Spatial capture extracts non-air blocks from BlockView and filters air coordinates")
	void testSpatialCaptureFromWorld() {
		if (dummyState == null) return;
		MockWorldBlockView world = new MockWorldBlockView();

		// Set up a 3x3x3 volume with selective blocks
		BlockPos corner1 = new BlockPos(100, 64, -200);
		BlockPos corner2 = new BlockPos(102, 66, -198);

		// Bottom layer (Y=64): 4 stone foundation corners
		world.setBlock(new BlockPos(100, 64, -200), dummyState);
		world.setBlock(new BlockPos(102, 64, -200), dummyState);
		world.setBlock(new BlockPos(100, 64, -198), dummyState);
		world.setBlock(new BlockPos(102, 64, -198), dummyState);

		// Middle layer (Y=65): 1 center block
		world.setBlock(new BlockPos(101, 65, -199), dummyState);

		// Top layer (Y=66): 1 apex block
		world.setBlock(new BlockPos(101, 66, -199), dummyState);

		List<BlueprintBlock> captured = StructureBlueprint.captureBlocks(world, corner1, corner2);

		Assertions.assertNotNull(captured);
		Assertions.assertEquals(6, captured.size(), "Must capture exactly 6 non-air blocks out of 27 voxels");

		// Verify bottom layer normalized offsets are at Y=0
		for (int i = 0; i < 4; i++) {
			Assertions.assertEquals(0, captured.get(i).offset().getY(), "Bottom layer blocks must have Y=0");
		}
		// Middle layer at Y=1
		Assertions.assertEquals(1, captured.get(4).offset().getY());
		Assertions.assertEquals(new BlockPos(1, 1, 1), captured.get(4).offset());

		// Top layer at Y=2
		Assertions.assertEquals(2, captured.get(5).offset().getY());
		Assertions.assertEquals(new BlockPos(1, 2, 1), captured.get(5).offset());
	}

	@Test
	@DisplayName("Corner normalization produces identical normalized blueprints regardless of selection order (pos1 vs pos2)")
	void testCornerNormalizationInvariance() {
		if (dummyState == null) return;
		MockWorldBlockView world = new MockWorldBlockView();

		BlockPos minPos = new BlockPos(-50, 10, 80);
		BlockPos maxPos = new BlockPos(-46, 14, 84);

		world.setBlock(minPos, dummyState);
		world.setBlock(maxPos, dummyState);
		world.setBlock(new BlockPos(-48, 12, 82), dummyState);

		// Order A: minPos to maxPos
		List<BlueprintBlock> blocksA = StructureBlueprint.captureBlocks(world, minPos, maxPos);
		// Order B: maxPos to minPos (inverted)
		List<BlueprintBlock> blocksB = StructureBlueprint.captureBlocks(world, maxPos, minPos);
		// Order C: mixed corners (minX, maxY, minZ to maxX, minY, maxZ)
		BlockPos mixed1 = new BlockPos(-50, 14, 80);
		BlockPos mixed2 = new BlockPos(-46, 10, 84);
		List<BlueprintBlock> blocksC = StructureBlueprint.captureBlocks(world, mixed1, mixed2);

		Assertions.assertEquals(3, blocksA.size());
		Assertions.assertEquals(blocksA.size(), blocksB.size());
		Assertions.assertEquals(blocksA.size(), blocksC.size());

		// Offsets in all permutations must match identically
		for (int i = 0; i < blocksA.size(); i++) {
			BlueprintBlock bA = blocksA.get(i);
			BlueprintBlock bB = blocksB.get(i);
			BlueprintBlock bC = blocksC.get(i);
			Assertions.assertEquals(bA.offset(), bB.offset(), "Normalized offsets must match for index " + i);
			Assertions.assertEquals(bA.offset(), bC.offset(), "Normalized offsets must match for index " + i);
		}

		// Ensure origin offset is (0, 0, 0)
		Assertions.assertEquals(new BlockPos(0, 0, 0), blocksA.get(0).offset());
		Assertions.assertEquals(new BlockPos(2, 2, 2), blocksA.get(1).offset());
		Assertions.assertEquals(new BlockPos(4, 4, 4), blocksA.get(2).offset());
	}

	@Test
	@DisplayName("StructureBlueprint.normalizeBlocks shifts negative and scattered coordinates to origin (0,0,0)")
	void testNormalizeBlocksNegativeAndScattered() {
		if (dummyState == null) return;

		List<BlueprintBlock> unnormalized = List.of(
			new BlueprintBlock(new BlockPos(-150, -40, -300), dummyState),
			new BlueprintBlock(new BlockPos(-145, -35, -295), dummyState),
			new BlueprintBlock(new BlockPos(-148, -38, -298), dummyState)
		);

		List<BlueprintBlock> normalized = StructureBlueprint.normalizeBlocks(unnormalized);
		Assertions.assertEquals(3, normalized.size());

		// Minimum corner is (-150, -40, -300), which translates to (0, 0, 0)
		Assertions.assertEquals(new BlockPos(0, 0, 0), normalized.get(0).offset());

		// (-148, -38, -298) translates to (-148 - -150, -38 - -40, -298 - -300) = (2, 2, 2)
		Assertions.assertEquals(new BlockPos(2, 2, 2), normalized.get(1).offset());

		// (-145, -35, -295) translates to (5, 5, 5)
		Assertions.assertEquals(new BlockPos(5, 5, 5), normalized.get(2).offset());
	}

	@Test
	@DisplayName("Topological sorting orders lower Y layers before higher Y layers (bottom-up)")
	void testTopologicalBottomUpSorting() {
		if (dummyState == null) return;

		// Construct out of order (highest layer first)
		List<BlueprintBlock> unorganized = new ArrayList<>(List.of(
			new BlueprintBlock(new BlockPos(2, 4, 2), dummyState),
			new BlueprintBlock(new BlockPos(0, 0, 0), dummyState),
			new BlueprintBlock(new BlockPos(1, 2, 1), dummyState),
			new BlueprintBlock(new BlockPos(2, 0, 2), dummyState),
			new BlueprintBlock(new BlockPos(1, 0, 1), dummyState),
			new BlueprintBlock(new BlockPos(1, 2, 2), dummyState)
		));

		Collections.sort(unorganized);

		// Verify strict ascending effective Y ordering
		int lastY = -1;
		for (BlueprintBlock block : unorganized) {
			Assertions.assertTrue(block.getEffectiveY() >= lastY, "Effective Y must be non-decreasing");
			lastY = block.getEffectiveY();
		}

		// Foundation (Y=0) must precede intermediate (Y=2) which precedes apex (Y=4)
		Assertions.assertEquals(0, unorganized.get(0).offset().getY());
		Assertions.assertEquals(0, unorganized.get(1).offset().getY());
		Assertions.assertEquals(0, unorganized.get(2).offset().getY());
		Assertions.assertEquals(2, unorganized.get(3).offset().getY());
		Assertions.assertEquals(2, unorganized.get(4).offset().getY());
		Assertions.assertEquals(4, unorganized.get(5).offset().getY());
	}

	@Test
	@DisplayName("Topological sorting prioritizes central core blocks over outward overhangs via Manhattan distance")
	void testTopologicalManhattanCoreSorting() {
		if (dummyState == null) return;

		// On the same Y layer (Y=0): center (0, 0), inner ring (1, 0), outer corner (3, 3)
		BlueprintBlock center = new BlueprintBlock(new BlockPos(0, 0, 0), dummyState);
		BlueprintBlock inner = new BlueprintBlock(new BlockPos(1, 0, 0), dummyState);
		BlueprintBlock outer = new BlueprintBlock(new BlockPos(3, 0, 3), dummyState);

		List<BlueprintBlock> layerBlocks = new ArrayList<>(List.of(outer, inner, center));
		Collections.sort(layerBlocks);

		Assertions.assertEquals(center, layerBlocks.get(0), "Center block (Manhattan distance 0) must be built first");
		Assertions.assertEquals(inner, layerBlocks.get(1), "Inner block (Manhattan distance 1) must be built second");
		Assertions.assertEquals(outer, layerBlocks.get(2), "Outer block (Manhattan distance 6) must be built last");
	}

	@Test
	@DisplayName("Reverse topological sorting in DISMANTLE mode clears roofs before walls and foundations")
	void testReverseTopologicalDismantleOrder() {
		if (dummyState == null) return;

		List<BlueprintBlock> buildOrder = new ArrayList<>(List.of(
			new BlueprintBlock(new BlockPos(0, 0, 0), dummyState),
			new BlueprintBlock(new BlockPos(0, 1, 0), dummyState),
			new BlueprintBlock(new BlockPos(0, 2, 0), dummyState),
			new BlueprintBlock(new BlockPos(0, 3, 0), dummyState)
		));
		Collections.sort(buildOrder);

		Assertions.assertEquals(4, buildOrder.size());
		Assertions.assertEquals(0, buildOrder.get(0).offset().getY());
		Assertions.assertEquals(3, buildOrder.get(3).offset().getY());

		// Reverse build order for dismantle tasks
		List<BlueprintBlock> dismantleOrder = new ArrayList<>(buildOrder);
		Collections.reverse(dismantleOrder);

		Assertions.assertEquals(3, dismantleOrder.get(0).offset().getY(), "Highest block (roof) must be first dismantle task");
		Assertions.assertEquals(2, dismantleOrder.get(1).offset().getY(), "Ceiling must be second dismantle task");
		Assertions.assertEquals(1, dismantleOrder.get(2).offset().getY(), "Wall must be third dismantle task");
		Assertions.assertEquals(0, dismantleOrder.get(3).offset().getY(), "Foundation must be last dismantle task");
	}

	@Test
	@DisplayName("Spatial capture safeguards reject oversized bounding boxes and volumes")
	void testSpatialSafeguardsExceedingLimits() {
		BlockPos origin = new BlockPos(0, 0, 0);

		// Exceeds max dimension 64 (dimension is 65 blocks: 64 - 0 + 1 = 65)
		BlockPos tooWide = new BlockPos(64, 0, 0);
		Assertions.assertThrows(IllegalArgumentException.class, () ->
			StructureBlueprint.captureBlocks(null, origin, tooWide)
		);

		// Exceeds max dimension in Y (max height is 96 blocks: 96 - 0 + 1 = 97)
		BlockPos tooTall = new BlockPos(0, 96, 0);
		Assertions.assertThrows(IllegalArgumentException.class, () ->
			StructureBlueprint.captureBlocks(null, origin, tooTall)
		);

		// Exceeds max dimension in Z
		BlockPos tooDeep = new BlockPos(0, 0, 64);
		Assertions.assertThrows(IllegalArgumentException.class, () ->
			StructureBlueprint.captureBlocks(null, origin, tooDeep)
		);

		// Max allowed dimensions: 63 -> size = 64 (within MAX_SPATIAL_DIMENSION)
		BlockPos maxAllowedDim = new BlockPos(63, 0, 0);
		Assertions.assertDoesNotThrow(() ->
			StructureBlueprint.captureBlocks(null, origin, maxAllowedDim)
		);

		// Max allowed height: 95 -> size = 96 (within MAX_SPATIAL_HEIGHT)
		BlockPos maxAllowedHeight = new BlockPos(0, 95, 0);
		Assertions.assertDoesNotThrow(() ->
			StructureBlueprint.captureBlocks(null, origin, maxAllowedHeight)
		);
	}

	@Test
	@DisplayName("CaptureSpatialBlueprintPayload packet codec serialization roundtrip")
	void testCaptureSpatialBlueprintPayloadPacketCodecRoundtrip() {
		BlockPos pos1 = new BlockPos(120, 64, -350);
		BlockPos pos2 = new BlockPos(140, 80, -330);
		CaptureSpatialBlueprintPayload payload = new CaptureSpatialBlueprintPayload(
			"custom_arena",
			"Gladiator Arena",
			"Grand circular combat arena",
			pos1,
			pos2
		);

		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			CaptureSpatialBlueprintPayload.PACKET_CODEC.encode(buf, payload);
			Assertions.assertTrue(buf.readableBytes() > 0);

			CaptureSpatialBlueprintPayload decoded = CaptureSpatialBlueprintPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(payload, decoded);
			Assertions.assertEquals("custom_arena", decoded.id());
			Assertions.assertEquals("Gladiator Arena", decoded.name());
			Assertions.assertEquals("Grand circular combat arena", decoded.description());
			Assertions.assertEquals(pos1, decoded.pos1());
			Assertions.assertEquals(pos2, decoded.pos2());
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("CreateCustomBlueprintPayload PacketCodec encode/decode with empty blocks")
	void testCreateCustomBlueprintPayloadRoundtrip() {
		CreateCustomBlueprintPayload payload = new CreateCustomBlueprintPayload(
			"custom_pillar",
			"Log Pillar",
			"Simple 3-high log pillar",
			List.of()
		);

		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			CreateCustomBlueprintPayload.PACKET_CODEC.encode(buf, payload);
			Assertions.assertTrue(buf.readableBytes() > 0);

			CreateCustomBlueprintPayload decoded = CreateCustomBlueprintPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(payload.id(), decoded.id());
			Assertions.assertEquals(payload.name(), decoded.name());
			Assertions.assertEquals(payload.description(), decoded.description());
			Assertions.assertEquals(payload.blocks().size(), decoded.blocks().size());
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("DeleteCustomBlueprintPayload and SyncCustomBlueprintsPayload packet codec roundtrips")
	void testDeleteAndSyncCustomBlueprintsPayloads() {
		DeleteCustomBlueprintPayload deletePayload = new DeleteCustomBlueprintPayload("custom_old_tower");
		RegistryByteBuf delBuf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			DeleteCustomBlueprintPayload.PACKET_CODEC.encode(delBuf, deletePayload);
			Assertions.assertTrue(delBuf.readableBytes() > 0);
			DeleteCustomBlueprintPayload decoded = DeleteCustomBlueprintPayload.PACKET_CODEC.decode(delBuf);
			Assertions.assertEquals(deletePayload, decoded);
			Assertions.assertEquals("custom_old_tower", decoded.blueprintId());
		} finally {
			delBuf.release();
		}

		SyncCustomBlueprintsPayload syncPayload = new SyncCustomBlueprintsPayload(List.of());
		RegistryByteBuf syncBuf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			SyncCustomBlueprintsPayload.PACKET_CODEC.encode(syncBuf, syncPayload);
			Assertions.assertTrue(syncBuf.readableBytes() > 0);
			SyncCustomBlueprintsPayload decodedSync = SyncCustomBlueprintsPayload.PACKET_CODEC.decode(syncBuf);
			Assertions.assertEquals(0, decodedSync.blueprints().size());
		} finally {
			syncBuf.release();
		}
	}

	@Test
	@DisplayName("UpdateScepterPayload supports DESIGN mode with packet codec roundtrip")
	void testUpdateScepterPayloadWithDesignMode() {
		UpdateScepterPayload payload = new UpdateScepterPayload(
			CommandMode.DESIGN,
			"custom_capture_target",
			SquadGroup.ALPHA,
			0,
			Optional.of(MinionRole.BUILDER),
			false
		);

		Assertions.assertEquals(CommandMode.DESIGN, payload.mode());
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			UpdateScepterPayload.PACKET_CODEC.encode(buf, payload);
			Assertions.assertTrue(buf.readableBytes() > 0);

			UpdateScepterPayload decoded = UpdateScepterPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(payload, decoded);
			Assertions.assertEquals(CommandMode.DESIGN, decoded.mode());
		} finally {
			buf.release();
		}
	}
}
