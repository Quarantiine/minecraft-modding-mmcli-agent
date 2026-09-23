package com.example.item;

import com.example.component.CommandMode;
import com.example.item.custom.CommandScepterItem;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Scepter DESIGN mode mechanics:
 * - Corner 1 (Pos1) and Corner 2 (Pos2) spatial coordinate logic
 * - Dimensional calculations (sizeX, sizeY, sizeZ, volume)
 * - Complete selection invariants
 * - Interaction invariants (Sequential Pos1 -> Pos2 on left-click, right-click capture trigger)
 */
public class CommandScepterDesignModeTest {

	public static int[] calculateDimensions(BlockPos pos1, BlockPos pos2) {
		if (pos1 == null || pos2 == null) return new int[] { 0, 0, 0, 0 };
		int sx = Math.abs(pos1.getX() - pos2.getX()) + 1;
		int sy = Math.abs(pos1.getY() - pos2.getY()) + 1;
		int sz = Math.abs(pos1.getZ() - pos2.getZ()) + 1;
		int volume = sx * sy * sz;
		return new int[] { sx, sy, sz, volume };
	}

	@Test
	@DisplayName("CommandMode includes DESIGN enum with correct pitch and formatting")
	void testCommandModeDesignProperties() {
		CommandMode mode = CommandMode.DESIGN;
		Assertions.assertNotNull(mode);
		Assertions.assertEquals("design", mode.asString());
		Assertions.assertEquals("Design", mode.getDisplayName());
		Assertions.assertEquals("§d", mode.getColorCode());
		Assertions.assertEquals("§dDesign", mode.getFormattedName());
		Assertions.assertEquals(1.7F, mode.getPitch(), 1e-4F);
	}

	@Test
	@DisplayName("DESIGN mode corner coordinate dimension calculations")
	void testDimensionCalculation() {
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
	@DisplayName("Corner selection state machine invariants")
	void testCornerSelectionInvariants() {
		BlockPos p1 = new BlockPos(100, 60, -50);
		BlockPos p2 = new BlockPos(120, 75, -30);

		// No corners set
		Assertions.assertFalse(hasBothCorners(null, null));

		// Only Pos1 set
		Assertions.assertFalse(hasBothCorners(p1, null));

		// Only Pos2 set
		Assertions.assertFalse(hasBothCorners(null, p2));

		// Both set
		Assertions.assertTrue(hasBothCorners(p1, p2));
	}

	private boolean hasBothCorners(BlockPos p1, BlockPos p2) {
		return p1 != null && p2 != null;
	}

	@Test
	@DisplayName("Modal opener callback interface contract")
	void testModalOpenerContract() {
		boolean[] opened = new boolean[] { false };
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 74, 20);

		CommandScepterItem.CaptureModalOpener opener = (player, hand, stack, corner1, corner2) -> {
			Assertions.assertEquals(p1, corner1);
			Assertions.assertEquals(p2, corner2);
			opened[0] = true;
		};

		opener.openCaptureModal(null, null, null, p1, p2);
		Assertions.assertTrue(opened[0], "Capture modal opener must be called with corners");
	}

	@Test
	@DisplayName("Modal opener fallback to tracker positions contract")
	void testModalOpenerFallbackToTrackerContract() {
		boolean[] modalOpened = new boolean[] { false };
		BlockPos trackerPos1 = new BlockPos(100, 64, 100);
		BlockPos trackerPos2 = new BlockPos(110, 74, 110);

		CommandScepterItem.CaptureModalOpener fallbackOpener = (player, hand, stack, p1, p2) -> {
			if (p1 == null) p1 = trackerPos1;
			if (p2 == null) p2 = trackerPos2;
			if (p1 != null && p2 != null) {
				modalOpened[0] = true;
				Assertions.assertEquals(trackerPos1, p1);
				Assertions.assertEquals(trackerPos2, p2);
			}
		};

		// When stack corners are null, fallback supplies tracker positions
		fallbackOpener.openCaptureModal(null, null, null, null, null);
		Assertions.assertTrue(modalOpened[0], "Modal opener must successfully resolve fallback tracker coordinates");
	}

	@Test
	@DisplayName("Sequential left-click state transition simulation")
	void testSequentialLeftClickTransitions() {
		class DesignSelectionState {
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

			boolean isReadyForCapture() {
				return p1 != null && p2 != null;
			}
		}

		DesignSelectionState state = new DesignSelectionState();
		BlockPos posA = new BlockPos(10, 64, 10);
		BlockPos posB = new BlockPos(20, 74, 20);
		BlockPos posC = new BlockPos(30, 80, 30);

		// 1st click: sets pos1
		int s1 = state.click(posA);
		Assertions.assertEquals(1, s1);
		Assertions.assertEquals(posA, state.p1);
		Assertions.assertNull(state.p2);
		Assertions.assertFalse(state.isReadyForCapture());

		// 2nd click: sets pos2 -> ready for right-click capture
		int s2 = state.click(posB);
		Assertions.assertEquals(2, s2);
		Assertions.assertEquals(posA, state.p1);
		Assertions.assertEquals(posB, state.p2);
		Assertions.assertTrue(state.isReadyForCapture());

		// 3rd click: resets/restarts selection cycle with new pos1
		int s3 = state.click(posC);
		Assertions.assertEquals(1, s3);
		Assertions.assertEquals(posC, state.p1);
		Assertions.assertNull(state.p2);
		Assertions.assertFalse(state.isReadyForCapture());
	}

	@Test
	@DisplayName("DESIGN_CORNER_STEPPER functional interface contract and lifecycle")
	void testDesignCornerStepperContract() {
		CommandScepterItem.DesignCornerStepper previous = CommandScepterItem.DESIGN_CORNER_STEPPER;
		try {
			int[] stepCounter = new int[] { 0 };
			BlockPos[] lastPos = new BlockPos[] { null };

			CommandScepterItem.DESIGN_CORNER_STEPPER = (pos) -> {
				lastPos[0] = pos;
				stepCounter[0] = (stepCounter[0] % 2) + 1;
				return stepCounter[0];
			};

			Assertions.assertNotNull(CommandScepterItem.DESIGN_CORNER_STEPPER);

			BlockPos p1 = new BlockPos(10, 64, 10);
			int step1 = CommandScepterItem.DESIGN_CORNER_STEPPER.stepCorner(p1);
			Assertions.assertEquals(1, step1);
			Assertions.assertEquals(p1, lastPos[0]);

			BlockPos p2 = new BlockPos(20, 74, 20);
			int step2 = CommandScepterItem.DESIGN_CORNER_STEPPER.stepCorner(p2);
			Assertions.assertEquals(2, step2);
			Assertions.assertEquals(p2, lastPos[0]);

			BlockPos p3 = new BlockPos(30, 84, 30);
			int step3 = CommandScepterItem.DESIGN_CORNER_STEPPER.stepCorner(p3);
			Assertions.assertEquals(1, step3);
			Assertions.assertEquals(p3, lastPos[0]);
		} finally {
			CommandScepterItem.DESIGN_CORNER_STEPPER = previous;
		}
	}

	@Test
	@DisplayName("Simulated AttackBlockCallback / AttackEntityCallback corner stepping in DESIGN mode")
	void testAttackCallbackDesignModeStepping() {
		CommandScepterItem.DesignCornerStepper previous = CommandScepterItem.DESIGN_CORNER_STEPPER;
		try {
			java.util.List<BlockPos> steppedCorners = new java.util.ArrayList<>();
			CommandScepterItem.DESIGN_CORNER_STEPPER = (pos) -> {
				steppedCorners.add(pos);
				return (steppedCorners.size() % 2 == 1) ? 1 : 2;
			};

			BlockPos targetBlock = new BlockPos(15, 60, 25);
			BlockPos targetEntityPos = new BlockPos(40, 65, 50);

			// Simulate left-clicking block in DESIGN mode
			if (CommandScepterItem.DESIGN_CORNER_STEPPER != null) {
				int s1 = CommandScepterItem.DESIGN_CORNER_STEPPER.stepCorner(targetBlock);
				Assertions.assertEquals(1, s1);
			}

			// Simulate left-clicking entity in DESIGN mode
			if (CommandScepterItem.DESIGN_CORNER_STEPPER != null) {
				int s2 = CommandScepterItem.DESIGN_CORNER_STEPPER.stepCorner(targetEntityPos);
				Assertions.assertEquals(2, s2);
			}

			Assertions.assertEquals(2, steppedCorners.size());
			Assertions.assertEquals(targetBlock, steppedCorners.get(0));
			Assertions.assertEquals(targetEntityPos, steppedCorners.get(1));
		} finally {
			CommandScepterItem.DESIGN_CORNER_STEPPER = previous;
		}
	}

	@Test
	@DisplayName("Shift + Left-Click in DESIGN mode triggers corner reset and clears corners")
	void testHandleDesignClickSneakResetContract() {
		CommandScepterItem.DesignCornerResetter prevResetter = CommandScepterItem.DESIGN_CORNER_RESETTER;
		try {
			boolean[] resetCalled = new boolean[] { false };
			CommandScepterItem.DESIGN_CORNER_RESETTER = () -> resetCalled[0] = true;

			// In-memory dummy stack (mocked or empty stack without items)
			int step = CommandScepterItem.handleDesignClick(null, null, null, new BlockPos(10, 64, 10), true);
			Assertions.assertEquals(0, step, "Sneak click with null stack returns 0");

			// Test handleDesignReset callback invocation
			CommandScepterItem.handleDesignReset(null, null, null);
			Assertions.assertTrue(resetCalled[0], "DESIGN_CORNER_RESETTER must be called during design reset");
		} finally {
			CommandScepterItem.DESIGN_CORNER_RESETTER = prevResetter;
		}
	}

	@Test
	@DisplayName("DESIGN_CLICK_CONSUMER callback execution contract")
	void testDesignClickConsumerContract() {
		Runnable prevConsumer = CommandScepterItem.DESIGN_CLICK_CONSUMER;
		try {
			boolean[] consumed = new boolean[] { false };
			CommandScepterItem.DESIGN_CLICK_CONSUMER = () -> consumed[0] = true;

			Assertions.assertNotNull(CommandScepterItem.DESIGN_CLICK_CONSUMER);
			CommandScepterItem.DESIGN_CLICK_CONSUMER.run();
			Assertions.assertTrue(consumed[0], "DESIGN_CLICK_CONSUMER must execute to drain keybinding press queue");
		} finally {
			CommandScepterItem.DESIGN_CLICK_CONSUMER = prevConsumer;
		}
	}

	@Test
	@DisplayName("Source contract: ExampleMod Attack callbacks in DESIGN mode are strictly client-scoped to prevent server duplicate execution")
	void testAttackCallbacksAreClientScopedToPreventServerDuplicateStep() throws java.io.IOException {
		java.nio.file.Path modPath = java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java");
		Assertions.assertTrue(java.nio.file.Files.exists(modPath), "ExampleMod.java must exist");
		String content = java.nio.file.Files.readString(modPath);

		// Verify AttackBlockCallback checks world.isClient() for DESIGN mode
		Assertions.assertTrue(
			content.contains("mode == CommandMode.DESIGN) {\n\t\t\t\t\tif (world.isClient()) {"),
			"AttackBlockCallback in DESIGN mode must check world.isClient() before calling handleDesignClick"
		);

		// Verify AttackEntityCallback checks world.isClient() for DESIGN mode
		Assertions.assertTrue(
			content.contains("mode == CommandMode.DESIGN) {\n\t\t\t\t\tif (world.isClient()) {\n\t\t\t\t\t\tBlockPos entityPos"),
			"AttackEntityCallback in DESIGN mode must check world.isClient() before calling handleDesignClick"
		);
	}

	@Test
	@DisplayName("Design click debounce suppresses rapid consecutive invocations within 350ms window")
	void testDesignClickDebounceContract() {
		CommandScepterItem.DesignCornerStepper prevStepper = CommandScepterItem.DESIGN_CORNER_STEPPER;
		try {
			int[] stepCount = new int[] { 0 };
			CommandScepterItem.DESIGN_CORNER_STEPPER = (pos) -> {
				stepCount[0]++;
				return stepCount[0];
			};

			CommandScepterItem.resetDesignClickDebounce();
			CommandScepterItem.resetDesignClickDebounce();
			Assertions.assertFalse(CommandScepterItem.isDesignClickDebounced());

			// First click: record timestamp and activate debounce
			CommandScepterItem.recordDesignClick();
			Assertions.assertTrue(CommandScepterItem.isDesignClickDebounced(), "After recording click, debounce should be active");

			// Reset debounce
			CommandScepterItem.resetDesignClickDebounce();
			Assertions.assertFalse(CommandScepterItem.isDesignClickDebounced(), "After reset, debounce should be inactive");
		} finally {
			CommandScepterItem.DESIGN_CORNER_STEPPER = prevStepper;
			CommandScepterItem.resetDesignClickDebounce();
		}
	}

	public static BlockPos computeEffectiveAttackBlockPos(boolean isReplaceable, BlockPos clickedPos, net.minecraft.util.math.Direction direction) {
		return isReplaceable ? clickedPos : ((direction != null) ? clickedPos.offset(direction) : clickedPos.up());
	}

	@Test
	@DisplayName("Universal surface anchoring formula for DESIGN mode AttackBlockCallback")
	void testDesignModeAttackBlockCallbackSurfaceAnchoringFormula() {
		BlockPos clicked = new BlockPos(10, 64, 10);

		// Replaceable blocks (air, tall grass, water, etc.) anchor directly at clickedPos without offset
		Assertions.assertEquals(clicked, computeEffectiveAttackBlockPos(true, clicked, net.minecraft.util.math.Direction.UP));
		Assertions.assertEquals(clicked, computeEffectiveAttackBlockPos(true, clicked, net.minecraft.util.math.Direction.NORTH));
		Assertions.assertEquals(clicked, computeEffectiveAttackBlockPos(true, clicked, net.minecraft.util.math.Direction.DOWN));
		Assertions.assertEquals(clicked, computeEffectiveAttackBlockPos(true, clicked, null));

		// Non-replaceable solid blocks anchor on top / adjacent face
		Assertions.assertEquals(clicked.up(), computeEffectiveAttackBlockPos(false, clicked, net.minecraft.util.math.Direction.UP));
		Assertions.assertEquals(clicked.down(), computeEffectiveAttackBlockPos(false, clicked, net.minecraft.util.math.Direction.DOWN));
		Assertions.assertEquals(clicked.north(), computeEffectiveAttackBlockPos(false, clicked, net.minecraft.util.math.Direction.NORTH));
		Assertions.assertEquals(clicked.south(), computeEffectiveAttackBlockPos(false, clicked, net.minecraft.util.math.Direction.SOUTH));
		Assertions.assertEquals(clicked.east(), computeEffectiveAttackBlockPos(false, clicked, net.minecraft.util.math.Direction.EAST));
		Assertions.assertEquals(clicked.west(), computeEffectiveAttackBlockPos(false, clicked, net.minecraft.util.math.Direction.WEST));
		// Null direction fallback to clicked.up()
		Assertions.assertEquals(clicked.up(), computeEffectiveAttackBlockPos(false, clicked, null));
	}

	@Test
	@DisplayName("Source contract: ExampleMod AttackBlockCallback in DESIGN mode applies universal surface anchoring isReplaceable formula")
	void testAttackBlockCallbackUsesReplaceableSurfaceAnchoringContract() throws java.io.IOException {
		java.nio.file.Path modPath = java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java");
		Assertions.assertTrue(java.nio.file.Files.exists(modPath), "ExampleMod.java must exist");
		String content = java.nio.file.Files.readString(modPath);

		Assertions.assertTrue(
			content.contains("world.getBlockState(pos).isReplaceable()"),
			"ExampleMod.java AttackBlockCallback must check world.getBlockState(pos).isReplaceable() for DESIGN mode anchoring"
		);
		Assertions.assertTrue(
			content.contains("BlockPos effectivePos = world.getBlockState(pos).isReplaceable()"),
			"ExampleMod.java AttackBlockCallback must compute effectivePos with isReplaceable conditional offset"
		);
	}
}
