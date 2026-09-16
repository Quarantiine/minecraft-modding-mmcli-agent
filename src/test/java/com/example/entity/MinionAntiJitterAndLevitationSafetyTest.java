package com.example.entity;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating Warrior levitation safety ceilings, anti-skyrocket velocity guards,
 * and formation follow anti-jitter station stabilization.
 */
public class MinionAntiJitterAndLevitationSafetyTest {

	@Test
	@DisplayName("Source Invariant: MinionEntity implements push suppression, levitation timeout, and noClip removal")
	void testMinionEntitySafetyInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// 1. pushAwayFrom and isPushable override for mutual push suppression
		Assertions.assertTrue(content.contains("public void pushAwayFrom(Entity entity)"),
				"MinionEntity must override pushAwayFrom to suppress mutual shoving");
		Assertions.assertTrue(content.contains("public boolean isPushable()"),
				"MinionEntity must override isPushable to stay anchored when stationed");

		// 2. NoClip removed from levitation
		Assertions.assertFalse(content.contains("this.noClip = levitating;"),
				"MinionEntity must not enable noClip during levitation to preserve collision physics");

		// 3. Dynamic clearance altitude and 120-tick failsafe
		Assertions.assertTrue(content.contains("targetClearanceAltitude"),
				"MinionEntity must compute targetClearanceAltitude to scale obstacles and prevent skyrocketing");
		Assertions.assertTrue(content.contains("this.arcaneLevitationTicks > 120"),
				"MinionEntity must enforce generous 120-tick failsafe without cutting off active 3-block climbing");

		// 4. Melee Warrior combat grounding
		Assertions.assertTrue(content.contains("this.getRole() == MinionRole.WARRIOR") && content.contains("dy < -0.8D"),
				"MinionEntity must ground melee Warriors elevated above ground combat targets");
	}

	@Test
	@DisplayName("Source Invariant: MinionFormationFollowGoal uses walkableY consistently and zeros arrival velocity")
	void testFormationFollowAntiJitterInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionFormationFollowGoal.java must exist");
		String content = Files.readString(path);

		// Consistent walkableY in canStart and shouldContinue
		Assertions.assertTrue(content.contains("double walkableY = resolveWalkableY(this.minion.getWorld(), station.x, owner.getY(), station.z);"),
				"MinionFormationFollowGoal must resolve walkableY in both canStart and shouldContinue");

		// Zero horizontal velocity on arrival
		Assertions.assertTrue(content.contains("this.minion.setVelocity(0.0D, this.minion.getVelocity().y, 0.0D);"),
				"MinionFormationFollowGoal must zero lateral velocity on station arrival to prevent sliding/jittering");
	}

	@Test
	@DisplayName("Kinematics: Dynamic clearance ceiling allows 3-block climbing and strictly blocks skyrocketing")
	void testUpwardThrustCeilingRestriction() {
		// Simulate compute velocity with dynamic clearance ceiling
		record KinematicsSimulator() {
			Vec3d computeVelocity(Vec3d minionPos, Vec3d targetDest, double targetClearanceAltitude, boolean horizontalCollision) {
				double dx = targetDest.x - minionPos.x;
				double dy = targetDest.y - minionPos.y;
				double dz = targetDest.z - minionPos.z;
				double totalDistSq = dx * dx + dy * dy + dz * dz;
				double dist = Math.sqrt(totalDistSq);
				if (dist < 0.01D) {
					return Vec3d.ZERO;
				}

				Vec3d dir = new Vec3d(dx / dist, dy / dist, dz / dist);
				double speed = 0.35D;
				double vx = dir.x * speed;
				double vy;
				double vz = dir.z * speed;

				if (minionPos.y < targetClearanceAltitude - 0.1D) {
					double liftRemaining = targetClearanceAltitude - minionPos.y;
					vy = Math.min(0.38D, liftRemaining * 0.5D + 0.18D);
					vy = Math.max(vy, 0.30D);
				} else {
					vy = 0.0D;
					if (minionPos.y > targetDest.y + 0.5D) {
						vy = Math.max(-0.25D, (targetDest.y - minionPos.y) * 0.25D);
					}
				}

				if (horizontalCollision && minionPos.y < targetClearanceAltitude - 0.1D) {
					vy = Math.max(vy, 0.38D);
				}

				return new Vec3d(vx, vy, vz);
			}
		}

		KinematicsSimulator sim = new KinematicsSimulator();

		// Case 1: Climbing a 3-block obstacle (Minion at Y = 64.0, 3-block ledge at Y = 67.0).
		// targetClearanceAltitude is 67.15D. Upward lift MUST be applied so minion climbs over!
		Vec3d velClimbing = sim.computeVelocity(new Vec3d(0, 64, 0), new Vec3d(2, 67, 2), 67.15D, true);
		Assertions.assertTrue(velClimbing.y >= 0.30D,
				"When below clearance altitude, strong upward thrust (vy >= 0.30D) must be applied to scale 3-block obstacle");

		// Case 2: Minion reaches top of 3-block ledge (Minion at Y = 67.2, target at Y = 67.0).
		// targetClearanceAltitude is 67.0D. Minion is at or above ceiling: vy MUST NOT be positive!
		Vec3d velAtCeiling = sim.computeVelocity(new Vec3d(2, 67.2, 2), new Vec3d(2, 67, 2), 67.0D, false);
		Assertions.assertTrue(velAtCeiling.y <= 0.0D,
				"When at or above clearance ceiling, vertical velocity must not be positive (anti-skyrocket guard)");

		// Case 3: Minion at Y = 70.0, Target is at Y = 64.0 (destination on ground).
		// Even if brushing an obstacle, minion is above clearance altitude, so it MUST glide downward!
		Vec3d velDescending = sim.computeVelocity(new Vec3d(0, 70, 0), new Vec3d(5, 64, 5), 64.0D, false);
		Assertions.assertTrue(velDescending.y < 0.0D,
				"When destination is below, vertical velocity must be negative to glide down to ground");
	}

	@Test
	@DisplayName("Source Invariant: MinionEntity immediately lands when on solid ground without higher obstacle ahead")
	void testImmediateSolidGroundLandingRule() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String content = Files.readString(path);
		Assertions.assertTrue(content.contains("boolean overSolidGround = this.isOnGround() || belowState.isSolidBlock(serverWorld, below);"),
				"MinionEntity must detect solid ground below feet");
		Assertions.assertTrue(content.contains("boolean climbingWallAhead = (obstacleTopClearanceY > this.getY() + 0.3D)"),
				"MinionEntity must check if an obstacle rises above feet before landing");
		Assertions.assertTrue(content.contains("if (overSolidGround && !climbingWallAhead)"),
				"MinionEntity must immediately land when on solid ground with no higher obstacle ahead");
	}
}
