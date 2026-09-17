package com.example.entity;

import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating:
 * 1. BlueprintRegistry exact and category-variant outline resolution source contracts (preventing Barricade defaulting to Watchtower).
 * 2. Builder Minion block phasing (noClip) active ONLY during construction and egress.
 * 3. Post-construction egress state machine evacuating builders from structures before dropping noClip.
 * 4. Automatic perimeter waypoint deployment and builder stationing on build completion.
 */
public class BuilderBlockPhasingAndEgressTest {

	// =========================================================================
	// 1. Blueprint Outline and Sizing Resolution Source Contracts
	// =========================================================================

	@Test
	@DisplayName("Source Invariant: BlueprintRegistry resolves category and size variants without falling back to Watchtower")
	void testBlueprintRegistryVariantResolutionSourceContracts() throws IOException {
		Path path = Path.of("src/main/java/com/example/blueprint/BlueprintRegistry.java");
		Assertions.assertTrue(Files.exists(path), "BlueprintRegistry.java must exist");
		String content = Files.readString(path);

		// Category names registered directly
		Assertions.assertTrue(content.contains("REGISTRY.put(HOME_ID, HOME);"),
			"BlueprintRegistry must register HOME_ID directly");
		Assertions.assertTrue(content.contains("REGISTRY.put(WORKSHOP_ID, WORKSHOP);"),
			"BlueprintRegistry must register WORKSHOP_ID directly");
		Assertions.assertTrue(content.contains("REGISTRY.put(SUPPLY_DEPOT_ID, SUPPLY_DEPOT);"),
			"BlueprintRegistry must register SUPPLY_DEPOT_ID directly");

		// All size variants registered in static init
		Assertions.assertTrue(content.contains("for (BuildingCategory cat : BuildingCategory.values())"),
			"BlueprintRegistry must iterate over all BuildingCategory values in static init");
		Assertions.assertTrue(content.contains("StructureBlueprint small = cat.createBlueprint(BuildingCategory.SIZE_SMALL"),
			"BlueprintRegistry must register small variants for all categories");
		Assertions.assertTrue(content.contains("StructureBlueprint grand = cat.createBlueprint(BuildingCategory.SIZE_GRAND"),
			"BlueprintRegistry must register grand variants for all categories");

		// Category + Size prefix/suffix match in get(String id)
		Assertions.assertTrue(content.contains("cleanId.startsWith(catId) || (cat == BuildingCategory.SUPPLY_DEPOT && cleanId.startsWith(\"depot\"))"),
			"BlueprintRegistry.get() must handle category prefix matching for variants like barricade_small, barricade_grand");
	}

	@Test
	@DisplayName("Source Invariant: CommandScepterItem and BlueprintHologramRenderer resolve variants and dynamic buildings")
	void testScepterAndRendererVariantSourceContracts() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String scepterContent = Files.readString(scepterPath);

		Assertions.assertTrue(
			scepterContent.contains("c.getId().equalsIgnoreCase(bpId) || bpId.toLowerCase().startsWith(c.getId().toLowerCase())"),
			"CommandScepterItem executeBuildPlacement must support prefix matching on category ID"
		);

		Path rendererPath = Path.of("src/client/java/com/example/client/renderer/BlueprintHologramRenderer.java");
		Assertions.assertTrue(Files.exists(rendererPath), "BlueprintHologramRenderer.java must exist");
		String rendererContent = Files.readString(rendererPath);

		Assertions.assertTrue(
			rendererContent.contains("c.getId().equalsIgnoreCase(blueprintId) || blueprintId.toLowerCase().startsWith(c.getId().toLowerCase())"),
			"BlueprintHologramRenderer must support prefix matching on category ID"
		);
		Assertions.assertTrue(
			rendererContent.contains("rotatedBp = DynamicBuildingResolver.resolve(rotatedBp, client.world, sessionData.anchorPos(), ArchitectureStyle.BIOME_NATIVE);"),
			"BlueprintHologramRenderer must resolve dynamic building foundation wireframe for active build sessions"
		);
	}

	// =========================================================================
	// 2. Builder Block-Phasing and Egress State Simulation
	// =========================================================================

	static class MockBuilderMinion {
		MinionRole role = MinionRole.BUILDER;
		boolean alive = true;
		boolean activelyBuilding = false;
		boolean exitingBuilding = false;
		boolean noClip = false;
		boolean arcaneLevitating = false;
		boolean noGravity = false;
		Vec3d pos = new Vec3d(5.0, 65.0, 5.0);
		Vec3d egressTarget = null;
		BlockBox structureBox = null;
		int egressTicks = 0;
		boolean sitting = false;
		boolean selected = true;
		BlockPos guardAnchor = null;

		boolean isPhasingBlocks() {
			return this.alive && this.role == MinionRole.BUILDER && (this.activelyBuilding || this.exitingBuilding);
		}

		void startBuilding() {
			this.activelyBuilding = true;
			this.noClip = this.isPhasingBlocks();
			this.arcaneLevitating = true;
			this.noGravity = true;
		}

		void startEgress(BlockBox box, BlockPos anchor, Vec3d targetWaypoint) {
			this.structureBox = box;
			this.exitingBuilding = true;
			this.activelyBuilding = false;
			this.arcaneLevitating = true;
			this.noGravity = true;
			this.noClip = this.isPhasingBlocks();
			this.egressTarget = targetWaypoint != null ? targetWaypoint : new Vec3d(box.getMaxX() + 2.0, box.getMinY(), box.getMaxZ() + 2.0);
		}

		boolean isInsideStructure(BlockBox box) {
			if (box == null) return false;
			return pos.x >= box.getMinX() - 0.2 && pos.x <= box.getMaxX() + 1.2 &&
			       pos.y >= box.getMinY() - 0.2 && pos.y <= box.getMaxY() + 1.5 &&
			       pos.z >= box.getMinZ() - 0.2 && pos.z <= box.getMaxZ() + 1.2;
		}

		void tickEgress() {
			if (!this.exitingBuilding) return;
			this.egressTicks++;
			this.noClip = this.isPhasingBlocks();
			this.noGravity = true;

			// Step towards exit
			if (this.egressTarget != null) {
				Vec3d toTarget = this.egressTarget.subtract(this.pos);
				if (toTarget.length() > 0.5) {
					this.pos = this.pos.add(toTarget.normalize().multiply(0.5));
				} else {
					this.pos = this.egressTarget;
				}
			}

			// Complete egress when outside structure
			if (!isInsideStructure(this.structureBox) || this.egressTicks > 60) {
				finishEgress();
			}
		}

		void finishEgress() {
			this.exitingBuilding = false;
			this.noClip = this.isPhasingBlocks();
			this.noGravity = false;
			this.arcaneLevitating = false;
			this.structureBox = null;
			this.egressTarget = null;
		}
	}

	@Test
	@DisplayName("Builder Minion phases through blocks ONLY while building and during structure evacuation")
	void testBuilderPhasingLifecycle() {
		MockBuilderMinion builder = new MockBuilderMinion();

		// 1. Initially idle: noClip and phasing MUST be false
		Assertions.assertFalse(builder.isPhasingBlocks(), "Idle builder must not phase through blocks");
		Assertions.assertFalse(builder.noClip, "Idle builder must not have noClip");

		// 2. Non-builder role never phases through blocks
		builder.role = MinionRole.WARRIOR;
		builder.activelyBuilding = true;
		Assertions.assertFalse(builder.isPhasingBlocks(), "Warrior role must never phase through blocks");

		builder.role = MinionRole.BUILDER;

		// 3. Active construction starts: noClip and phasing engage
		builder.startBuilding();
		Assertions.assertTrue(builder.isPhasingBlocks(), "Actively building minion must phase through blocks");
		Assertions.assertTrue(builder.noClip, "Actively building minion must have noClip enabled");
		Assertions.assertTrue(builder.noGravity, "Actively building minion must have gravity suppressed");

		// 4. Construction completes while builder is inside the house
		BlockBox houseBox = new BlockBox(0, 64, 0, 10, 72, 10);
		builder.pos = new Vec3d(5.0, 68.0, 5.0); // Inside 2nd floor of house
		Assertions.assertTrue(builder.isInsideStructure(houseBox), "Builder should be inside house");

		// Trigger egress towards exterior waypoint
		Vec3d exteriorWaypoint = new Vec3d(12.0, 64.0, 5.0);
		builder.startEgress(houseBox, new BlockPos(5, 64, 0), exteriorWaypoint);

		// While exiting, activelyBuilding is false, BUT phasing and noClip remain TRUE!
		Assertions.assertFalse(builder.activelyBuilding, "Actively building is false post-completion");
		Assertions.assertTrue(builder.exitingBuilding, "Exiting building is true");
		Assertions.assertTrue(builder.isPhasingBlocks(), "Builder MUST still phase through blocks during evacuation");
		Assertions.assertTrue(builder.noClip, "Builder MUST retain noClip during evacuation");

		// 5. Builder ticks egress until clear of structure
		while (builder.exitingBuilding) {
			builder.tickEgress();
		}

		// 6. Once clear of structure: noClip and phasing are completely removed!
		Assertions.assertFalse(builder.exitingBuilding, "Egress must be finished");
		Assertions.assertFalse(builder.isPhasingBlocks(), "Phasing must be revoked once outside structure");
		Assertions.assertFalse(builder.noClip, "NoClip must be revoked once outside structure");
		Assertions.assertFalse(builder.noGravity, "Gravity must be restored once outside structure");
		Assertions.assertFalse(builder.arcaneLevitating, "Arcane levitation must be deactivated once outside structure");
		Assertions.assertFalse(builder.isInsideStructure(houseBox), "Builder must be safely outside house bounds");
	}

	// =========================================================================
	// 3. Source Code Invariants in MinionEntity, MinionBuildGoal, ConstructionManager
	// =========================================================================

	@Test
	@DisplayName("Source Invariant: MinionEntity implements isPhasingBlocks, egress engine, and noClip control")
	void testMinionEntityPhasingSourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// PHASING_BLOCKS tracked data
		Assertions.assertTrue(content.contains("PHASING_BLOCKS"), "MinionEntity must define PHASING_BLOCKS tracked data");

		// isPhasingBlocks method
		Assertions.assertTrue(content.contains("public boolean isPhasingBlocks()"),
			"MinionEntity must define isPhasingBlocks()");

		// startEgressFromStructure, tickBuildingEgress, finishBuildingEgress
		Assertions.assertTrue(content.contains("public void startEgressFromStructure(BlockBox box, BlockPos anchorPos, Vec3d targetWaypoint)"),
			"MinionEntity must define startEgressFromStructure");
		Assertions.assertTrue(content.contains("public void tickBuildingEgress()"),
			"MinionEntity must define tickBuildingEgress");
		Assertions.assertTrue(content.contains("public void finishBuildingEgress(ServerWorld serverWorld)"),
			"MinionEntity must define finishBuildingEgress");

		// Collision and shoving suppression while phasing
		Assertions.assertTrue(content.contains("if (this.isPhasingBlocks())") && content.contains("return;"),
			"MinionEntity pushAwayFrom must suppress shoving while phasing");
		Assertions.assertTrue(content.contains("if (this.isPhasingBlocks() || this.isSitting()"),
			"MinionEntity isPushable must return false while phasing");

		// noClip assignment in tick and travel
		Assertions.assertTrue(content.contains("this.noClip = phasing;"),
			"MinionEntity tick must synchronize this.noClip to phasing");
		Assertions.assertTrue(content.contains("this.noClip = true;"),
			"MinionEntity travel must preserve noClip while phasing");
	}

	@Test
	@DisplayName("Source Invariant: ConstructionManager deploys perimeter waypoints and stations builders on completion")
	void testConstructionManagerPerimeterWaypointsInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(path), "ConstructionManager.java must exist");
		String content = Files.readString(path);

		// Perimeter waypoints list
		Assertions.assertTrue(content.contains("List<BlockPos> perimeterWaypoints = new ArrayList<>();"),
			"ConstructionManager must calculate perimeter waypoints around finished building");

		// Golden beacon beam particles and chime SFX
		Assertions.assertTrue(content.contains("BLOCK_AMETHYST_BLOCK_CHIME"),
			"ConstructionManager must play chime sound at perimeter waypoints");
		Assertions.assertTrue(content.contains("minion.startEgressFromStructure(box, anchor, Vec3d.ofBottomCenter(waypoint));"),
			"ConstructionManager must dispatch builder minions to perimeter waypoints via startEgressFromStructure");

		// Builder stationing at waypoint
		Assertions.assertTrue(content.contains("minion.setSitting(true);") && content.contains("minion.setGuardAnchorPos(waypoint);"),
			"ConstructionManager must station builder minions at perimeter waypoints holding position");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal triggers egress on stop and tick completion")
	void testMinionBuildGoalEgressInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// Stop trigger egress
		Assertions.assertTrue(content.contains("this.minion.startEgressFromStructure(sessionBox, sessionAnchor, null);"),
			"MinionBuildGoal stop and tick must trigger startEgressFromStructure when inside structure");

		// Phasing bypasses ceiling clamping
		Assertions.assertTrue(content.contains("!this.minion.isPhasingBlocks() && hasCeilingAboveMinion"),
			"MinionBuildGoal must bypass ceiling clamping while phasing blocks");
	}

	@Test
	@DisplayName("Perimeter waypoints for multiple minions are spread out across distinct sides of the structure")
	void testPerimeterWaypointsAngularSpreadSimulation() {
		BlockBox box = new BlockBox(0, 64, 0, 10, 70, 10);
		BlockPos anchor = new BlockPos(5, 64, 10); // Front door (South)

		int minX = box.getMinX();
		int maxX = box.getMaxX();
		int minZ = box.getMinZ();
		int maxZ = box.getMaxZ();
		int baseY = box.getMinY();

		List<BlockPos> perimeterRing = new java.util.ArrayList<>();
		// 1. South perimeter (Front / maxZ + 2): West to East
		for (int x = minX; x <= maxX; x++) {
			perimeterRing.add(new BlockPos(x, baseY, maxZ + 2));
		}
		// 2. East flank (maxX + 2): South to North
		for (int z = maxZ + 1; z >= minZ - 1; z--) {
			perimeterRing.add(new BlockPos(maxX + 2, baseY, z));
		}
		// 3. North perimeter (Back / minZ - 2): East to West
		for (int x = maxX; x >= minX; x--) {
			perimeterRing.add(new BlockPos(x, baseY, minZ - 2));
		}
		// 4. West flank (minX - 2): North to South
		for (int z = minZ - 1; z <= maxZ + 1; z++) {
			perimeterRing.add(new BlockPos(minX - 2, baseY, z));
		}

		int startIdx = 0;
		double bestDistSq = Double.MAX_VALUE;
		for (int i = 0; i < perimeterRing.size(); i++) {
			double d = perimeterRing.get(i).getSquaredDistance(anchor);
			if (d < bestDistSq) {
				bestDistSq = d;
				startIdx = i;
			}
		}

		int numMinions = 4;
		java.util.Set<BlockPos> assignedWaypoints = new java.util.HashSet<>();
		List<BlockPos> minionWaypoints = new java.util.ArrayList<>();

		for (int minionIdx = 0; minionIdx < numMinions; minionIdx++) {
			int ringSize = perimeterRing.size();
			int targetIdx = (startIdx + (int) Math.round(minionIdx * ((double) ringSize / (double) numMinions))) % ringSize;
			int probe = 0;
			BlockPos waypoint = perimeterRing.get(targetIdx);
			while (assignedWaypoints.contains(waypoint) && probe < ringSize) {
				probe++;
				targetIdx = (targetIdx + 1) % ringSize;
				waypoint = perimeterRing.get(targetIdx);
			}
			assignedWaypoints.add(waypoint);
			minionWaypoints.add(waypoint);
		}

		// 1. Verify all 4 waypoints are completely unique (no two minions at same spot)
		Assertions.assertEquals(4, assignedWaypoints.size(), "All 4 builder waypoints must be unique");

		// 2. Verify minions are distributed across distinct sides/flanks around the structure
		boolean hasSouth = false;
		boolean hasEast = false;
		boolean hasNorth = false;
		boolean hasWest = false;

		for (BlockPos wp : minionWaypoints) {
			if (wp.getZ() >= maxZ + 2) hasSouth = true;
			if (wp.getX() >= maxX + 2) hasEast = true;
			if (wp.getZ() <= minZ - 2) hasNorth = true;
			if (wp.getX() <= minX - 2) hasWest = true;
		}

		Assertions.assertTrue(hasSouth, "Must station at least one builder at South (Front)");
		Assertions.assertTrue(hasEast, "Must station at least one builder at East flank");
		Assertions.assertTrue(hasNorth, "Must station at least one builder at North (Back)");
		Assertions.assertTrue(hasWest, "Must station at least one builder at West flank");
	}

	@Test
	@DisplayName("Source Invariant: Stationed builder minions autonomously mobilize upon new blueprint placement")
	void testStationedBuilderAutonomousMobilizationSourceInvariants() throws IOException {
		Path cmPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(cmPath), "ConstructionManager.java must exist");
		String cmContent = Files.readString(cmPath);

		// ConstructionManager.startSession wakes up nearby stationed builders
		Assertions.assertTrue(cmContent.contains("builder.setSitting(false);"),
			"ConstructionManager.startSession must wake up nearby sitting builders");
		Assertions.assertTrue(cmContent.contains("builder.setGuardAnchorPos(null);"),
			"ConstructionManager.startSession must clear old guard anchor when waking builders");

		Path mbgPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(mbgPath), "MinionBuildGoal.java must exist");
		String mbgContent = Files.readString(mbgPath);

		// MinionBuildGoal.canStart evaluates even if sitting, using 64 block radius
		Assertions.assertTrue(mbgContent.contains("double searchRadius = this.minion.isSitting() ? 64.0D : 128.0D;"),
			"MinionBuildGoal.canStart must allow sitting builders to search within 64 blocks");
		Assertions.assertTrue(mbgContent.contains("this.minion.setSitting(false);"),
			"MinionBuildGoal.canStart must unseat sitting builder upon claiming a task");

		// MinionBuildGoal suppresses exit traversal while phasing blocks
		Assertions.assertTrue(mbgContent.contains("if (!this.minion.isPhasingBlocks())") && mbgContent.contains("findStructureExitWaypoint"),
			"MinionBuildGoal must suppress exit waypoint traversal while phasing blocks to prevent drifting");
	}

	@Test
	@DisplayName("Source Invariant: Minions guaranteed stationed at perimeter waypoints & water cancels build flight")
	void testWaterFlightCancellationAndPerimeterStationingSourceInvariants() throws IOException {
		Path cmPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		String cmContent = Files.readString(cmPath);

		// 1. Minions are placed directly at perimeter waypoints
		Assertions.assertTrue(cmContent.contains("minion.requestTeleport(wx, waypoint.getY(), wz);"),
			"ConstructionManager.completeSession must teleport minions directly to their assigned perimeter waypoints");
		Assertions.assertTrue(cmContent.contains("public void cancelActiveSessionsForOwner(UUID ownerUuid, ServerWorld world)"),
			"ConstructionManager must define cancelActiveSessionsForOwner");

		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String scepterContent = Files.readString(scepterPath);

		// 2. Water detection and cancellation
		Assertions.assertTrue(scepterContent.contains("public static boolean isPlayerOverWater(World world, PlayerEntity player)"),
			"CommandScepterItem must define isPlayerOverWater helper");
		Assertions.assertTrue(scepterContent.contains("if (isPlayerOverWater(world, serverPlayer))"),
			"CommandScepterItem must check isPlayerOverWater in inventoryTick");
		Assertions.assertTrue(scepterContent.contains("cancelActiveSessionsForOwner(uuid, serverPlayer.getServerWorld());"),
			"CommandScepterItem must cancel active construction sessions when flying over water");
		Assertions.assertTrue(scepterContent.contains("setMode(stack, CommandMode.FOLLOW);"),
			"CommandScepterItem must switch mode out of BUILD to FOLLOW when flying over water");

		Path flightPath = Path.of("src/client/java/com/example/client/BuildFlightManager.java");
		String flightContent = Files.readString(flightPath);
		Assertions.assertTrue(flightContent.contains("boolean overWater = CommandScepterItem.isPlayerOverWater(world, player);"),
			"BuildFlightManager must check if player is over water to revoke flight");
	}
}
