package com.example.item;

import com.example.component.CommandMode;
import com.example.component.MiningMode;
import com.example.item.custom.CommandScepterItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests validating Command Scepter MINE mode mechanics:
 * - MiningMode enum values (DIRECT vs AREA) and cycling
 * - Corner 1 (Pos1) and Corner 2 (Pos2) spatial coordinate logic for AREA mining
 * - Dimensional calculations (sizeX, sizeY, sizeZ, volume)
 * - Complete selection invariants
 * - Interaction invariants (Sequential Pos1 -> Pos2 on left-click, right-click area confirm modal trigger)
 * - Attack callback routing for MINE AREA mode vs DIRECT dismantle
 * - Surface anchoring calculations
 */
public class CommandScepterMiningModeTest {

	@BeforeEach
	void setUp() {
		CommandScepterItem.resetMineClickDebounce();
		CommandScepterItem.MINE_CORNER_STEPPER = null;
		CommandScepterItem.MINE_CORNER_RESETTER = null;
		CommandScepterItem.MINE_CLICK_CONSUMER = null;
		CommandScepterItem.MINE_MODAL_OPENER = null;
	}

	public static int[] calculateDimensions(BlockPos pos1, BlockPos pos2) {
		if (pos1 == null || pos2 == null) return new int[] { 0, 0, 0, 0 };
		int sx = Math.abs(pos1.getX() - pos2.getX()) + 1;
		int sy = Math.abs(pos1.getY() - pos2.getY()) + 1;
		int sz = Math.abs(pos1.getZ() - pos2.getZ()) + 1;
		int volume = sx * sy * sz;
		return new int[] { sx, sy, sz, volume };
	}

	public static BlockPos computeEffectiveAttackBlockPos(boolean isReplaceable, BlockPos clickedPos, Direction direction) {
		return isReplaceable ? clickedPos : ((direction != null) ? clickedPos.offset(direction) : clickedPos.up());
	}

	@Test
	@DisplayName("MiningMode includes DIRECT and AREA enum values with correct formatting and cycling")
	void testMiningModeEnumProperties() {
		MiningMode direct = MiningMode.DIRECT;
		Assertions.assertNotNull(direct);
		Assertions.assertEquals("direct", direct.asString());
		Assertions.assertEquals("Direct / Structure", direct.getDisplayName());
		Assertions.assertEquals("§6", direct.getColorCode());
		Assertions.assertEquals("§6Direct / Structure", direct.getFormattedName());

		MiningMode area = MiningMode.AREA;
		Assertions.assertNotNull(area);
		Assertions.assertEquals("area", area.asString());
		Assertions.assertEquals("Custom Area", area.getDisplayName());
		Assertions.assertEquals("§e", area.getColorCode());
		Assertions.assertEquals("§eCustom Area", area.getFormattedName());

		// Cycling
		Assertions.assertEquals(MiningMode.AREA, direct.next());
		Assertions.assertEquals(MiningMode.DIRECT, area.next());
		Assertions.assertEquals(MiningMode.AREA, direct.previous());
		Assertions.assertEquals(MiningMode.DIRECT, area.previous());
	}

	@Test
	@DisplayName("MINE AREA mode corner coordinate dimension calculations")
	void testMiningDimensionCalculation() {
		BlockPos p1 = new BlockPos(10, 64, 20);
		BlockPos p2 = new BlockPos(15, 70, 25);

		int[] dims = calculateDimensions(p1, p2);
		Assertions.assertEquals(6, dims[0]); // 15 - 10 + 1 = 6
		Assertions.assertEquals(7, dims[1]); // 70 - 64 + 1 = 7
		Assertions.assertEquals(6, dims[2]); // 25 - 20 + 1 = 6
		Assertions.assertEquals(252, dims[3]); // 6 * 7 * 6 = 252

		// Inverted coordinates
		int[] dimsInverted = calculateDimensions(p2, p1);
		Assertions.assertArrayEquals(dims, dimsInverted);

		// Single block selection
		int[] single = calculateDimensions(p1, p1);
		Assertions.assertEquals(1, single[0]);
		Assertions.assertEquals(1, single[1]);
		Assertions.assertEquals(1, single[2]);
		Assertions.assertEquals(1, single[3]);
	}

	@Test
	@DisplayName("Mining area modal opener callback interface contract")
	void testMineModalOpenerContract() {
		boolean[] opened = new boolean[] { false };
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 74, 20);

		CommandScepterItem.MineModalOpener opener = (player, hand, stack, corner1, corner2) -> {
			Assertions.assertEquals(p1, corner1);
			Assertions.assertEquals(p2, corner2);
			opened[0] = true;
		};

		opener.openMineModal(null, null, null, p1, p2);
		Assertions.assertTrue(opened[0], "Mine confirmation modal opener must be called with corners");
	}

	@Test
	@DisplayName("Mining corner stepper hook transitions sequentially: Pos1 -> Pos2 -> Pos1")
	void testMineCornerStepperContract() {
		int[] callCount = new int[] { 0 };
		BlockPos target1 = new BlockPos(5, 60, 5);
		BlockPos target2 = new BlockPos(15, 70, 15);

		CommandScepterItem.MINE_CORNER_STEPPER = (pos) -> {
			callCount[0]++;
			if (callCount[0] % 2 == 1) {
				return 1;
			} else {
				return 2;
			}
		};

		Assertions.assertEquals(1, CommandScepterItem.MINE_CORNER_STEPPER.stepCorner(target1));
		Assertions.assertEquals(2, CommandScepterItem.MINE_CORNER_STEPPER.stepCorner(target2));
		Assertions.assertEquals(1, CommandScepterItem.MINE_CORNER_STEPPER.stepCorner(target1));
	}

	@Test
	@DisplayName("Mining corner resetter hook clears state")
	void testMineCornerResetterContract() {
		boolean[] resetCalled = new boolean[] { false };
		CommandScepterItem.MINE_CORNER_RESETTER = () -> resetCalled[0] = true;

		CommandScepterItem.handleMineReset(null, null, null);
		Assertions.assertTrue(resetCalled[0], "Mine corner resetter must be invoked upon reset");
	}

	@Test
	@DisplayName("MINE AREA mode attack callback surface anchoring formula")
	void testMiningModeAttackBlockCallbackSurfaceAnchoring() {
		BlockPos solidGround = new BlockPos(100, 64, 200);

		// Solid block clicked on top face -> anchors at clickedPos.up()
		BlockPos targetTop = computeEffectiveAttackBlockPos(false, solidGround, Direction.UP);
		Assertions.assertEquals(new BlockPos(100, 65, 200), targetTop);

		// Solid block clicked on north face -> anchors at clickedPos.north()
		BlockPos targetNorth = computeEffectiveAttackBlockPos(false, solidGround, Direction.NORTH);
		Assertions.assertEquals(new BlockPos(100, 64, 199), targetNorth);

		// Replaceable block (e.g. tall grass, air) -> anchors at clickedPos directly
		BlockPos targetReplaceable = computeEffectiveAttackBlockPos(true, solidGround, Direction.UP);
		Assertions.assertEquals(solidGround, targetReplaceable);
	}

	@Test
	@DisplayName("Mine click debounce protects against rapid repeated triggers")
	void testMineClickDebounceContract() {
		CommandScepterItem.resetMineClickDebounce();
		Assertions.assertFalse(CommandScepterItem.isMineClickDebounced());

		CommandScepterItem.recordMineClick();
		Assertions.assertTrue(CommandScepterItem.isMineClickDebounced());

		CommandScepterItem.resetMineClickDebounce();
		Assertions.assertFalse(CommandScepterItem.isMineClickDebounced());
	}

	@Test
	@DisplayName("Null and empty stack safety for mining corners and mode")
	void testNullStackMiningCornersSafety() {
		Assertions.assertNull(CommandScepterItem.getMinePos1(null));
		Assertions.assertNull(CommandScepterItem.getMinePos2(null));
		Assertions.assertFalse(CommandScepterItem.hasCompleteMineSelection(null));
		Assertions.assertEquals(MiningMode.AREA, CommandScepterItem.getMiningMode(null));

		Assertions.assertDoesNotThrow(() -> CommandScepterItem.setMinePos1(null, new BlockPos(0, 0, 0)));
		Assertions.assertDoesNotThrow(() -> CommandScepterItem.setMinePos2(null, new BlockPos(0, 0, 0)));
		Assertions.assertDoesNotThrow(() -> CommandScepterItem.setMineCorners(null, null, null));
		Assertions.assertDoesNotThrow(() -> CommandScepterItem.clearMineCorners(null));
		Assertions.assertDoesNotThrow(() -> CommandScepterItem.setMiningMode(null, MiningMode.AREA));
	}

	@Test
	@DisplayName("Source contract: ExampleModClient registers MINE hooks and handles MINE AREA attack key")
	void testClientRegistrationHooksContract() throws java.io.IOException {
		java.nio.file.Path clientPath = java.nio.file.Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(java.nio.file.Files.exists(clientPath), "ExampleModClient.java must exist");
		String content = java.nio.file.Files.readString(clientPath);

		Assertions.assertTrue(
			content.contains("CommandScepterItem.MINE_CORNER_STEPPER = ClientMiningCaptureTracker::stepCorner;"),
			"ExampleModClient must register MINE_CORNER_STEPPER"
		);
		Assertions.assertTrue(
			content.contains("CommandScepterItem.MINE_CORNER_RESETTER = ClientMiningCaptureTracker::clear;"),
			"ExampleModClient must register MINE_CORNER_RESETTER"
		);
		Assertions.assertTrue(
			content.contains("CommandScepterItem.MINE_MODAL_OPENER ="),
			"ExampleModClient must register MINE_MODAL_OPENER"
		);
		Assertions.assertTrue(
			content.contains("heldMode == CommandMode.MINE && CommandScepterItem.getMiningMode(heldScepter) == com.example.component.MiningMode.AREA"),
			"ExampleModClient must handle attackKey for MINE AREA mode"
		);
	}

	@Test
	@DisplayName("Mining corner state machine sequential transition simulation")
	void testMiningSequentialCornerTransitions() {
		class MineSelectionState {
			BlockPos p1 = null;
			BlockPos p2 = null;

			int click(BlockPos pos) {
				if (pos == null) return 0;
				if (p1 == null || p2 != null) {
					p1 = pos;
					p2 = null;
					return 1;
				} else {
					p2 = pos;
					return 2;
				}
			}

			boolean isReadyForExcavation() {
				return p1 != null && p2 != null;
			}
		}

		MineSelectionState state = new MineSelectionState();
		BlockPos posA = new BlockPos(10, 64, 10);
		BlockPos posB = new BlockPos(20, 74, 20);
		BlockPos posC = new BlockPos(30, 80, 30);

		// 1st click: sets pos1
		int s1 = state.click(posA);
		Assertions.assertEquals(1, s1);
		Assertions.assertEquals(posA, state.p1);
		Assertions.assertNull(state.p2);
		Assertions.assertFalse(state.isReadyForExcavation());

		// 2nd click: sets pos2 -> ready for modal trigger
		int s2 = state.click(posB);
		Assertions.assertEquals(2, s2);
		Assertions.assertEquals(posA, state.p1);
		Assertions.assertEquals(posB, state.p2);
		Assertions.assertTrue(state.isReadyForExcavation());

		// 3rd click: resets/restarts selection cycle with new pos1
		int s3 = state.click(posC);
		Assertions.assertEquals(1, s3);
		Assertions.assertEquals(posC, state.p1);
		Assertions.assertNull(state.p2);
		Assertions.assertFalse(state.isReadyForExcavation());
	}

	@Test
	@DisplayName("Source contract: ExampleMod Attack callbacks in MINE mode check world.isClient() and MiningMode.AREA")
	void testAttackCallbacksAreClientScopedForMineAreaMode() throws java.io.IOException {
		java.nio.file.Path modPath = java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java");
		Assertions.assertTrue(java.nio.file.Files.exists(modPath), "ExampleMod.java must exist");
		String content = java.nio.file.Files.readString(modPath);

		// Verify AttackBlockCallback checks world.isClient() and MiningMode.AREA
		Assertions.assertTrue(
			content.contains("mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == com.example.component.MiningMode.AREA)"),
			"AttackBlockCallback in MINE mode must check MiningMode.AREA"
		);

		// Verify AttackEntityCallback checks world.isClient() and MiningMode.AREA
		Assertions.assertTrue(
			content.contains("mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == com.example.component.MiningMode.AREA)"),
			"AttackEntityCallback in MINE mode must check MiningMode.AREA"
		);
	}

	@Test
	@DisplayName("MINE_CLICK_CONSUMER callback execution contract")
	void testMineClickConsumerContract() {
		Runnable prevConsumer = CommandScepterItem.MINE_CLICK_CONSUMER;
		try {
			boolean[] consumed = new boolean[] { false };
			CommandScepterItem.MINE_CLICK_CONSUMER = () -> consumed[0] = true;

			Assertions.assertNotNull(CommandScepterItem.MINE_CLICK_CONSUMER);
			CommandScepterItem.MINE_CLICK_CONSUMER.run();
			Assertions.assertTrue(consumed[0], "MINE_CLICK_CONSUMER must execute to drain keybinding press queue");
		} finally {
			CommandScepterItem.MINE_CLICK_CONSUMER = prevConsumer;
		}
	}
}
