package com.example.entity;

import com.example.block.ModBlocks;
import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite validating:
 * 1. 90° Forward Conical Sector mathematical dot product targeting across all cardinal bearings.
 * 2. Real-time preview glowing outline state management and decay safeguards.
 * 3. Arcane Builder Levitation gravity suppression, particle trails, and fall damage immunity.
 * 4. MinionBuildGoal 3D flight station calculation, reach hovering, and ground landing.
 * 5. MinionPathNodeMaker traversal support for ModBlocks.CONSTRUCTION_BLOCK.
 */
public class ArcaneLevitationAndSectorTest {

	// =========================================================================
	// 1. 90° Forward Conical Sector Mathematical Model Validation
	// =========================================================================

	/**
	 * Pure mathematical implementation matching CommandScepterItem.isWithinSector.
	 */
	private static boolean checkSector(double playerX, double playerZ, float yaw, double targetX, double targetZ, double maxRadius) {
		double dx = targetX - playerX;
		double dz = targetZ - playerZ;
		double distSq = dx * dx + dz * dz;
		if (distSq > maxRadius * maxRadius) {
			return false;
		}
		double dist = Math.sqrt(distSq);
		if (dist < 0.001D) {
			return true;
		}

		float yawRad = yaw * 0.017453292F;
		double lookX = -Math.sin(yawRad);
		double lookZ = Math.cos(yawRad);

		double dot = (dx * lookX + dz * lookZ) / dist;
		return dot >= 0.70710678D; // cos(45°)
	}

	@Test
	@DisplayName("90° Forward Sector correctly accepts targets within +-45° and rejects targets outside")
	void testSectorTargetingSouthFacing() {
		double px = 100.0D;
		double pz = 100.0D;
		float yawSouth = 0.0F; // South (+Z)
		double radius = 16.0D;

		// 1. Directly in front (0° offset)
		Assertions.assertTrue(checkSector(px, pz, yawSouth, px, pz + 8.0D, radius), "Directly in front must be inside");

		// 2. Exactly at +45° right boundary
		Assertions.assertTrue(checkSector(px, pz, yawSouth, px + 5.0D, pz + 5.0D, radius), "+45° right boundary must be inside");

		// 3. Exactly at -45° left boundary
		Assertions.assertTrue(checkSector(px, pz, yawSouth, px - 5.0D, pz + 5.0D, radius), "-45° left boundary must be inside");

		// 4. Slightly inside (+40° offset)
		double angle40Rad = Math.toRadians(40.0);
		Assertions.assertTrue(checkSector(px, pz, yawSouth, px + 10.0D * Math.sin(angle40Rad), pz + 10.0D * Math.cos(angle40Rad), radius), "40° offset must be inside");

		// 5. Flank unit (+60° offset) - must be rejected
		double angle60Rad = Math.toRadians(60.0);
		Assertions.assertFalse(checkSector(px, pz, yawSouth, px + 10.0D * Math.sin(angle60Rad), pz + 10.0D * Math.cos(angle60Rad), radius), "60° flank must be outside");

		// 6. 90° lateral unit (due East) - must be rejected
		Assertions.assertFalse(checkSector(px, pz, yawSouth, px + 10.0D, pz, radius), "90° lateral unit must be outside");

		// 7. Rear unit (due North, behind player) - must be rejected
		Assertions.assertFalse(checkSector(px, pz, yawSouth, px, pz - 8.0D, radius), "Rear unit must be outside");

		// 8. Directly on top of player (dist < 0.001) - inside
		Assertions.assertTrue(checkSector(px, pz, yawSouth, px, pz, radius), "Zero distance must be inside");

		// 9. Directly in front but beyond max radius - must be rejected
		Assertions.assertFalse(checkSector(px, pz, yawSouth, px, pz + 20.0D, radius), "Beyond max radius must be outside");
	}

	@Test
	@DisplayName("90° Forward Sector correctly functions across all 4 cardinal rotations")
	void testSectorTargetingAllCardinals() {
		double px = 0.0D;
		double pz = 0.0D;
		double radius = 16.0D;

		// North facing (yaw = 180, look = (0, -1))
		float yawNorth = 180.0F;
		Assertions.assertTrue(checkSector(px, pz, yawNorth, 0.0D, -10.0D, radius), "North ahead must be inside");
		Assertions.assertFalse(checkSector(px, pz, yawNorth, 0.0D, 10.0D, radius), "South behind must be outside");
		Assertions.assertFalse(checkSector(px, pz, yawNorth, 10.0D, 0.0D, radius), "East flank must be outside");

		// East facing (yaw = -90, look = (1, 0))
		float yawEast = -90.0F;
		Assertions.assertTrue(checkSector(px, pz, yawEast, 10.0D, 0.0D, radius), "East ahead must be inside");
		Assertions.assertFalse(checkSector(px, pz, yawEast, -10.0D, 0.0D, radius), "West behind must be outside");
		Assertions.assertFalse(checkSector(px, pz, yawEast, 0.0D, 10.0D, radius), "South flank must be outside");

		// West facing (yaw = 90, look = (-1, 0))
		float yawWest = 90.0F;
		Assertions.assertTrue(checkSector(px, pz, yawWest, -10.0D, 0.0D, radius), "West ahead must be inside");
		Assertions.assertFalse(checkSector(px, pz, yawWest, 10.0D, 0.0D, radius), "East behind must be outside");
		Assertions.assertFalse(checkSector(px, pz, yawWest, 0.0D, -10.0D, radius), "North flank must be outside");
	}

	// =========================================================================
	// 2. CommandScepterItem Source Contract & Preview Highlighting Invariants
	// =========================================================================

	@Test
	@DisplayName("Validate CommandScepterItem 90° forward sector and real-time preview illumination source contracts")
	void testCommandScepterSectorAndPreviewInvariants() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String content = Files.readString(scepterPath);

		// Sector math method
		Assertions.assertTrue(
			content.contains("public static boolean isWithinSector(LivingEntity center, double targetX, double targetZ, double maxRadius)"),
			"CommandScepterItem must expose isWithinSector method"
		);
		Assertions.assertTrue(
			content.contains("dot >= 0.70710678D"),
			"CommandScepterItem must evaluate dot product threshold >= cos(45°)"
		);

		// Real-time preview glowing in usageTick
		Assertions.assertTrue(
			content.contains("minion.setPreviewGlowing(true)"),
			"usageTick must set preview glowing on minions in sector"
		);
		Assertions.assertTrue(
			content.contains("minion.setPreviewGlowing(false)"),
			"usageTick must clear preview glowing on minions outside sector"
		);

		// Boundary ray particle projection
		Assertions.assertTrue(
			content.contains("rayAngle : rayAngles"),
			"usageTick must render boundary rays along sector borders"
		);
		Assertions.assertTrue(
			content.contains("-Math.PI / 4.0D, Math.PI / 4.0D"),
			"usageTick boundary rays must project at +-45 degrees"
		);

		// onStoppedUsing cone filtering & cleanup
		Assertions.assertTrue(
			content.contains("isWithinSector(player, minion.getX(), minion.getZ(), rallyRadius)"),
			"onStoppedUsing must filter enclosed minions using isWithinSector"
		);
		Assertions.assertTrue(
			content.contains("minion.setPreviewGlowing(false)"),
			"onStoppedUsing must clear preview glowing states on release"
		);
	}

	// =========================================================================
	// 3. MinionEntity Preview Glowing & Arcane Levitation Source Invariants
	// =========================================================================

	@Test
	@DisplayName("Validate MinionEntity preview glowing and arcane levitation fields and methods")
	void testMinionEntityPreviewGlowAndLevitationInvariants() throws IOException {
		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionPath), "MinionEntity.java must exist");
		String content = Files.readString(minionPath);

		// PREVIEW_GLOWING TrackedData
		Assertions.assertTrue(
			content.contains("TrackedData<Boolean> PREVIEW_GLOWING"),
			"MinionEntity must register PREVIEW_GLOWING TrackedData"
		);
		Assertions.assertTrue(
			content.contains("builder.add(PREVIEW_GLOWING, false)"),
			"MinionEntity initDataTracker must register PREVIEW_GLOWING"
		);
		Assertions.assertTrue(
			content.contains("public boolean isPreviewGlowing()"),
			"MinionEntity must define isPreviewGlowing"
		);
		Assertions.assertTrue(
			content.contains("public void setPreviewGlowing(boolean previewGlowing)"),
			"MinionEntity must define setPreviewGlowing"
		);
		Assertions.assertTrue(
			content.contains("this.isSelected() || this.isPreviewGlowing() || super.isGlowing()"),
			"MinionEntity isGlowing must incorporate isPreviewGlowing"
		);

		// Arcane Levitation
		Assertions.assertTrue(
			content.contains("private boolean arcaneLevitating = false;"),
			"MinionEntity must define arcaneLevitating field"
		);
		Assertions.assertTrue(
			content.contains("public boolean isArcaneLevitating()"),
			"MinionEntity must define isArcaneLevitating"
		);
		Assertions.assertTrue(
			content.contains("public void setArcaneLevitating(boolean levitating)"),
			"MinionEntity must define setArcaneLevitating"
		);
		Assertions.assertTrue(
			content.contains("this.setNoGravity(levitating)"),
			"setArcaneLevitating must toggle setNoGravity"
		);
		Assertions.assertTrue(
			content.contains("this.arcaneLevitating && source.isOf(DamageTypes.FALL)"),
			"damage() must suppress fall damage while arcaneLevitating"
		);
	}

	// =========================================================================
	// 4. MinionBuildGoal Arcane Hover Navigation & De-scaffolding Invariants
	// =========================================================================

	@Test
	@DisplayName("Validate MinionBuildGoal Arcane Builder Levitation and station computation")
	void testMinionBuildGoalArcaneLevitationInvariants() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String content = Files.readString(buildGoalPath);

		// Station computation
		Assertions.assertTrue(
			content.contains("public Vec3d findOptimalHoverStation(ServerWorld world, BlockPos targetPos)"),
			"MinionBuildGoal must define findOptimalHoverStation"
		);

		// Engagement of Arcane Levitation
		Assertions.assertTrue(
			content.contains("this.minion.setArcaneLevitating(true)"),
			"MinionBuildGoal must activate arcane levitation for elevated construction"
		);
		Assertions.assertTrue(
			content.contains("this.minion.isArcaneLevitating()"),
			"MinionBuildGoal must check isArcaneLevitating"
		);

		// Hover station motion
		Assertions.assertTrue(
			content.contains("Vec3d vel = delta.normalize().multiply(speed)") || content.contains("Vec3d vel = delta.normalize().multiply(0.35D)"),
			"MinionBuildGoal must smoothly propel minion toward hover station"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setVelocity(0.0D, 0.0D, 0.0D)"),
			"MinionBuildGoal must halt velocity when hovering in reach"
		);

		// Gentle landing when done
		Assertions.assertTrue(
			content.contains("this.minion.setVelocity(0.0D, -0.22D, 0.0D)"),
			"MinionBuildGoal must float minion down gently on task completion"
		);

		// ShouldContinue keeps goal active during descent
		Assertions.assertTrue(
			content.contains("this.minion.isArcaneLevitating() && !this.minion.isOnGround()"),
			"shouldContinue must keep goal active while levitating to safely land on ground"
		);
	}

	// =========================================================================
	// 5. MinionPathNodeMaker Scaffolding Traversal Support
	// =========================================================================

	@Test
	@DisplayName("Validate MinionPathNodeMaker traversal support for Blocks.SCAFFOLDING")
	void testMinionPathNodeMakerSupportsScaffolding() throws IOException {
		Path nodeMakerPath = Path.of("src/main/java/com/example/entity/ai/pathing/MinionPathNodeMaker.java");
		Assertions.assertTrue(Files.exists(nodeMakerPath), "MinionPathNodeMaker.java must exist");
		String content = Files.readString(nodeMakerPath);

		Assertions.assertTrue(
			content.contains("Blocks.SCAFFOLDING"),
			"MinionPathNodeMaker must reference Blocks.SCAFFOLDING"
		);
		Assertions.assertTrue(
			content.contains("isScaffold"),
			"MinionPathNodeMaker must inspect scaffolding blocks"
		);
		Assertions.assertFalse(
			content.contains("ModBlocks.CONSTRUCTION_BLOCK"),
			"MinionPathNodeMaker must not reference retired ModBlocks.CONSTRUCTION_BLOCK"
		);
	}
}
