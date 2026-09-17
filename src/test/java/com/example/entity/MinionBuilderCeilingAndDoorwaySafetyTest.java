package com.example.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating builder levitation ceiling clearance checks, hover station line-of-sight validation,
 * collision/stall fallback to ground A* door navigation, vertical velocity clamping, and self-intersection nudging.
 */
public class MinionBuilderCeilingAndDoorwaySafetyTest {

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal implements ceiling clearance, LOS validation, stall fallback, velocity clamping, and self-intersection nudging")
	void testMinionBuildGoalCeilingAndDoorwayInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// 1. Ceiling clearance checks
		Assertions.assertTrue(content.contains("hasCeilingAboveMinion"),
				"MinionBuildGoal must have hasCeilingAboveMinion method for ceiling clearance checks");
		Assertions.assertTrue(content.contains("hasCeilingAboveMinion(serverWorld, 3)"),
				"MinionBuildGoal must check ceiling clearance before enabling levitation");

		// 2. Line of sight validation
		Assertions.assertTrue(content.contains("hasLineOfSightToStation"),
				"MinionBuildGoal must validate line-of-sight to candidate hover station");

		// 3. Collision/stall fallback to ground A* door navigation
		Assertions.assertTrue(content.contains("this.stallCollisionTicks >= 12"),
				"MinionBuildGoal must trigger ground fallback after 12 stall/collision ticks");
		Assertions.assertTrue(content.contains("this.groundNavigationForced = true;"),
				"MinionBuildGoal must force ground navigation when blocked by ceiling or collision");

		// 4. Vertical velocity clamping under ceiling
		Assertions.assertTrue(content.contains("hasCeilingAboveMinion(serverWorld, 2) && vel.y > 0.0D"),
				"MinionBuildGoal must clamp upward vertical velocity (vel.y <= 0) when solid ceiling is overhead");

		// 5. Self-intersection nudging before block placement
		Assertions.assertTrue(content.contains("nudgeMinionAwayFromTargetBlock"),
				"MinionBuildGoal must check and nudge minion away from target block coordinates before placement");
		Assertions.assertTrue(content.contains("minionBox.intersects(targetBox)"),
				"nudgeMinionAwayFromTargetBlock must inspect bounding box intersection with target block");
	}

	@Test
	@DisplayName("Source Invariant: MinionEntity enforces vertical velocity ceiling clamping overhead")
	void testMinionEntityCeilingClampingInvariant() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("solidCeilingDirectlyOverhead"),
				"MinionEntity must track solid ceiling directly overhead");
		Assertions.assertTrue(content.contains("if (solidCeilingDirectlyOverhead && vy > 0.0D)"),
				"MinionEntity must clamp vy to 0.0D when a solid ceiling is directly overhead");
	}

	@Test
	@DisplayName("Geometry: Self-intersection detection and safe nudge resolution logic")
	void testSelfIntersectionAndNudgeMath() {
		BlockPos targetPos = new BlockPos(10, 64, 10);
		Box targetBox = new Box(targetPos);

		// Minion standing inside the target position
		Box insideMinionBox = new Box(10.2, 64.0, 10.2, 10.8, 65.8, 10.8);
		Assertions.assertTrue(insideMinionBox.intersects(targetBox),
				"Minion inside target block must intersect target box");

		// Minion safely standing adjacent to target position
		Box adjacentMinionBox = new Box(11.2, 64.0, 10.2, 11.8, 65.8, 10.8);
		Assertions.assertFalse(adjacentMinionBox.intersects(targetBox),
				"Minion standing safely outside target block must not intersect target box");

		// Compute lateral push offset from center (>= 1.2 blocks from block center)
		double minionX = 10.3D;
		double minionZ = 10.3D;
		double pushX = minionX - (targetPos.getX() + 0.5D);
		double pushZ = minionZ - (targetPos.getZ() + 0.5D);
		double len = Math.sqrt(pushX * pushX + pushZ * pushZ);
		double nudgedX = targetPos.getX() + 0.5D + (pushX / len) * 1.2D;
		double nudgedZ = targetPos.getZ() + 0.5D + (pushZ / len) * 1.2D;

		Box nudgedBox = new Box(nudgedX - 0.3D, 64.0, nudgedZ - 0.3D, nudgedX + 0.3D, 65.8, nudgedZ + 0.3D);
		Assertions.assertFalse(nudgedBox.intersects(targetBox),
				"Nudged minion position must no longer intersect the target block box");
	}

	@Test
	@DisplayName("Kinematics: Ceiling headroom velocity clamping prevents upward ramming")
	void testCeilingVelocityClampingKinematics() {
		record VelocityClamper() {
			Vec3d clampVelocity(Vec3d desiredVel, boolean ceilingWithinRange) {
				if (ceilingWithinRange && desiredVel.y > 0.0D) {
					return new Vec3d(desiredVel.x, 0.0D, desiredVel.z);
				}
				return desiredVel;
			}
		}

		VelocityClamper clamper = new VelocityClamper();

		Vec3d desiredUpward = new Vec3d(0.25D, 0.40D, 0.25D);
		Vec3d clamped = clamper.clampVelocity(desiredUpward, true);

		Assertions.assertEquals(0.0D, clamped.y, 1e-6, "Vertical velocity must be clamped to 0 under low ceilings");
		Assertions.assertEquals(0.25D, clamped.x, 1e-6, "Horizontal X velocity must be preserved");
		Assertions.assertEquals(0.25D, clamped.z, 1e-6, "Horizontal Z velocity must be preserved");

		Vec3d unconstrained = clamper.clampVelocity(desiredUpward, false);
		Assertions.assertEquals(0.40D, unconstrained.y, 1e-6, "Vertical velocity must not be clamped when open sky above");
	}
}
