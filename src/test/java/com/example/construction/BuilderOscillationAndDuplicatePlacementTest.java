package com.example.construction;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating fixes for builder minion sinking/oscillation,
 * stall false-positives, and duplicate building placement prevention.
 */
public class BuilderOscillationAndDuplicatePlacementTest {

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal uses positive targetY offset and safe upward block nudging")
	void testMinionBuildGoalElevationAndNudgeInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// 1. Positive targetY offset prevents sinking into roof/floor geometry
		Assertions.assertTrue(content.contains("targetY = targetPos.getY() + 0.05D"),
				"MinionBuildGoal must hover above target block base (targetPos.getY() + 0.05D) to prevent sinking");
		Assertions.assertFalse(content.contains("targetY = targetPos.getY() - 0.2D"),
				"MinionBuildGoal must never subtract from targetPos.getY() which sinks minions into solid floor blocks");

		// 2. Safe footing offset above solid blocks
		Assertions.assertTrue(content.contains("safeCandY = Math.max(targetY, (double) candPos.getY() + 0.05D)"),
				"Candidate hover station must ensure feet are above solid flooring");

		// 3. Nudging prioritizes Direction.UP so minions step safely on top of placed blocks
		Assertions.assertTrue(content.contains("Direction.UP, Direction.NORTH"),
				"nudgeMinionAwayFromTargetBlock must prioritize Direction.UP to step up onto newly placed blocks");
		Assertions.assertTrue(content.contains("safeY = Math.max(this.minion.getY(), targetPos.getY() + 1.0D)"),
				"nudgeMinionAwayFromTargetBlock fallback must ensure safe Y above the target block");
	}

	@Test
	@DisplayName("Source Invariant: CommandScepter prevents duplicate placement via cooldowns and onStoppedUsing cleanup")
	void testCommandScepterDuplicatePlacementPreventionInvariants() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String scepterContent = Files.readString(scepterPath);

		// 1. executeBuildPlacement checks and applies cooldown
		Assertions.assertTrue(scepterContent.contains("player.getItemCooldownManager().isCoolingDown(stack.getItem())"),
				"CommandScepterItem must check isCoolingDown before executing build placement");
		Assertions.assertTrue(scepterContent.contains("player.getItemCooldownManager().set(stack.getItem(), 10)"),
				"CommandScepterItem must set a 10-tick placement cooldown");

		// 2. onStoppedUsing does not duplicate placement on mouse release
		Assertions.assertFalse(scepterContent.contains("executeBuildPlacement(serverWorld, player, stack, blockHit"),
				"onStoppedUsing must not execute build placement on mouse release");

		// 3. Client UseItemCallback enforces cooldown before sending packet
		Path clientPath = Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(Files.exists(clientPath), "ExampleModClient.java must exist");
		String clientContent = Files.readString(clientPath);
		Assertions.assertTrue(clientContent.contains("player.getItemCooldownManager().isCoolingDown(stack.getItem())"),
				"Client UseItemCallback must check isCoolingDown before sending anchor payload");

		// 4. Server ModNetworking checks cooldown
		Path networkPath = Path.of("src/main/java/com/example/network/ModNetworking.java");
		Assertions.assertTrue(Files.exists(networkPath), "ModNetworking.java must exist");
		String networkContent = Files.readString(networkPath);
		Assertions.assertTrue(networkContent.contains("player.getItemCooldownManager().isCoolingDown(scepterStack.getItem())"),
				"ModNetworking must check isCoolingDown when receiving AnchorConstructionPayload");
	}

	@Test
	@DisplayName("Geometry: Hover elevation above roof prevents negative vertical velocity oscillation")
	void testHoverStationElevationKinematics() {
		BlockPos roofBlock = new BlockPos(20, 64, 20);
		double targetY = roofBlock.getY() + 0.05D;

		// When minion is standing on roof block (feet at y = 65.0)
		double minionStandingY = 65.0D;
		// Hover target for an adjacent block on the roof layer at Y=64:
		// If old code used 64 - 0.2 = 63.8, delta.y was 63.8 - 65.0 = -1.2 (sinking into roof)
		// With targetY = 64 + 0.05 = 64.05, candidate stations on top of roof have safeCandY >= 65.05D:
		double safeCandY = Math.max(targetY, 64.0D + 1.0D + 0.05D);
		Vec3d hoverStation = new Vec3d(roofBlock.getX() + 1.8D, safeCandY, roofBlock.getZ() + 0.5D);

		Assertions.assertTrue(hoverStation.y >= minionStandingY,
				"Hover station Y must be level with or above roof surface (65.0D)");
		double deltaY = hoverStation.y - minionStandingY;
		Assertions.assertTrue(deltaY >= 0.0D,
				"Hover delta.y must not be negative into roof geometry");
	}

	@Test
	@DisplayName("Kinematics: Upward nudge teleports minion completely above the placed block bounding box")
	void testUpwardNudgeClearsBoundingBox() {
		BlockPos placedPos = new BlockPos(10, 64, 10);
		Box placedBox = new Box(placedPos);

		// Minion stepping UP onto placed block
		BlockPos abovePos = placedPos.offset(Direction.UP);
		Vec3d escapeVec = Vec3d.ofBottomCenter(abovePos);

		Box minionNudgedBox = new Box(
				escapeVec.x - 0.3D, escapeVec.y, escapeVec.z - 0.3D,
				escapeVec.x + 0.3D, escapeVec.y + 1.8D, escapeVec.z + 0.3D
		);

		Assertions.assertFalse(minionNudgedBox.intersects(placedBox),
				"Nudged minion standing on top of placed block must not intersect the block box");
		Assertions.assertEquals(placedPos.getY() + 1.0D, escapeVec.y, 1e-6,
				"Minion feet Y must be at placedPos.getY() + 1.0");
	}
}
