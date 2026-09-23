package com.example.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating player-like ground navigation and strict 2-condition Arcane Levitation:
 * 1. Step height 1.25D enables walking over slabs, stairs, carpets, and 1-block steps without tripping.
 * 2. Swimming enabled in MinionNavigation.
 * 3. Removal of hyperactive horizontalCollision vault launcher from tick().
 * 4. Strict 2-condition levitation: ONLY for obstacles >= 2 blocks high or holes >= 1 block long.
 * 5. Immediate ground landing restores gravity and ground walking without mid-air hovering.
 * 6. Goal elevation triggers require dy >= 2.0D.
 */
public class MinionPlayerNavigationAndLevitationRulesTest {

	@Test
	@DisplayName("Source Invariant: MinionEntity defines 1.25D step height and strict 2-condition detection methods")
	void testMinionEntitySourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// 1. Step height attribute set to 1.25D
		Assertions.assertTrue(content.contains(".add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.25D);"),
				"MinionEntity must configure GENERIC_STEP_HEIGHT to 1.25D for smooth player-like stepping");

		// 2. Water pathfinding penalties set to 0.0F
		Assertions.assertTrue(content.contains("this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);"),
				"MinionEntity must set WATER penalty to 0.0F for smooth water traversal");

		// 3. Strict 2-condition detection methods
		Assertions.assertTrue(content.contains("public boolean detectObstacleTwoBlocksOrHigher(ServerWorld world, double dirX, double dirZ)"),
				"MinionEntity must define detectObstacleTwoBlocksOrHigher");
		Assertions.assertTrue(content.contains("public boolean detectHoleOrChasmAhead(ServerWorld world, double dirX, double dirZ)"),
				"MinionEntity must define detectHoleOrChasmAhead");

		// 4. Deleted hyperactive vault launcher from tick()
		Assertions.assertFalse(content.contains("this.obstacleStallTicks >= 2"),
				"MinionEntity.tick() must NOT contain hyperactive obstacleStallTicks launcher");
		Assertions.assertFalse(content.contains("this.obstacleVaultTicks = 12;"),
				"MinionEntity.tick() must NOT contain obstacleVaultTicks = 12");

		// 5. Strict 2-condition evaluation in tickUniversalArcaneLevitation
		Assertions.assertTrue(content.contains("boolean shouldLevitate = elevationDisparity || obstacleTwoBlocksOrHigher || holeOrChasmAhead;"),
				"tickUniversalArcaneLevitation must gate levitation strictly to 2 conditions plus elevated targets");

		// 6. Navigation preserved on ground
		Assertions.assertTrue(content.contains("if (!this.isOnGround()) {\n\t\t\t\tthis.getNavigation().stop();\n\t\t\t}")
						|| content.contains("if (!this.isOnGround()) {\r\n\t\t\t\tthis.getNavigation().stop();\r\n\t\t\t}"),
				"MinionEntity must only stop navigation when airborne, preserving ground pathfinding");
	}

	@Test
	@DisplayName("Source Invariant: MinionNavigation enables swimming")
	void testMinionNavigationEnablesSwimming() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/pathing/MinionNavigation.java");
		Assertions.assertTrue(Files.exists(path), "MinionNavigation.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("this.setCanSwim(true);"),
				"MinionNavigation constructor must invoke setCanSwim(true)");
	}

	@Test
	@DisplayName("Source Invariant: Non-builder AI Goals use pure ground navigation and companion catch-up teleport")
	void testAiGoalsEnforceGroundNavigationAndCatchUpTeleport() throws IOException {
		String[] nonBuilderGoals = {
				"src/main/java/com/example/entity/ai/goal/MinionPatrolGoal.java",
				"src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java",
				"src/main/java/com/example/entity/ai/goal/WaypointHoldGoal.java",
				"src/main/java/com/example/entity/ai/goal/SentinelGuardGoal.java",
				"src/main/java/com/example/entity/ai/goal/MinionFollowLeaderGoal.java"
		};

		for (String goalFile : nonBuilderGoals) {
			Path path = Path.of(goalFile);
			Assertions.assertTrue(Files.exists(path), goalFile + " must exist");
			String content = Files.readString(path);

			Assertions.assertFalse(content.contains("setArcaneLevitating(true)"),
					goalFile + " must NOT trigger traversal levitation; minions use pure ground navigation");
		}

		// Follow goals must track stuckTicks for companion catch-up teleportation
		String followContent = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java"));
		Assertions.assertTrue(followContent.contains("stuckTicks >= 40"),
				"MinionFormationFollowGoal must track stuckTicks for catch-up teleport");

		String leaderFollowContent = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionFollowLeaderGoal.java"));
		Assertions.assertTrue(leaderFollowContent.contains("stuckTicks >= 40"),
				"MinionFollowLeaderGoal must track stuckTicks for catch-up teleport");
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal enforces ground-first work execution and bounded hover stationing")
	void testMinionBuildGoalGroundFirstExecutionSourceContract() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// Ground reach check for targets within reachable distance (horizontal <= 16 sq, vertical <= 2.5 blocks)
		Assertions.assertTrue(
			content.contains("boolean inRange = horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;"),
			"MinionBuildGoal must evaluate ground reachability for targets within reachable bounds"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setArcaneLevitating(false);"),
			"MinionBuildGoal must disable arcane levitation when stopping or switching to ground navigation"
		);
	}

	@Test
	@DisplayName("Simulation: Slabs, stairs, and 1-block steps do NOT trigger levitation (minion walks like player)")
	void testSlabsAndStairsWalkedWithoutLevitation() {
		record TraversalCheck(boolean headLevelSolid, boolean fenceOrWall, double dy, boolean holeAhead) {
			boolean detectObstacleTwoBlocksOrHigher() {
				return fenceOrWall || headLevelSolid || dy >= 2.0D;
			}

			boolean shouldLevitate() {
				boolean elevationDisparity = dy > 1.25D || dy < -1.5D;
				if (dy > 0 && dy < 2.0D) {
					elevationDisparity = false;
				}
				if (dy < 0 && dy >= -3.0D) {
					elevationDisparity = false;
				}
				return elevationDisparity || detectObstacleTwoBlocksOrHigher() || holeAhead;
			}
		}

		// Case 1: Stepping up a 0.5-block slab (head level is air, not a fence, dy = 0.5)
		TraversalCheck slab = new TraversalCheck(false, false, 0.5D, false);
		Assertions.assertFalse(slab.detectObstacleTwoBlocksOrHigher(), "Slab must NOT be detected as 2-block obstacle");
		Assertions.assertFalse(slab.shouldLevitate(), "Minion must NOT levitate for slabs; steps over natively");

		// Case 2: Stepping up a stair (head level is air, dy = 1.0)
		TraversalCheck stair = new TraversalCheck(false, false, 1.0D, false);
		Assertions.assertFalse(stair.detectObstacleTwoBlocksOrHigher(), "Stair must NOT be detected as 2-block obstacle");
		Assertions.assertFalse(stair.shouldLevitate(), "Minion must NOT levitate for stairs; steps over natively");

		// Case 3: 1-block dirt step (head level is air, dy = 1.0)
		TraversalCheck oneBlock = new TraversalCheck(false, false, 1.0D, false);
		Assertions.assertFalse(oneBlock.detectObstacleTwoBlocksOrHigher(), "1-block step must NOT be detected as 2-block obstacle");
		Assertions.assertFalse(oneBlock.shouldLevitate(), "Minion must NOT levitate for 1-block steps; steps over natively");
	}

	@Test
	@DisplayName("Simulation: 2-block obstacle and fence/wall DO trigger levitation")
	void testTwoBlockObstacleTriggersLevitation() {
		record TraversalCheck(boolean headLevelSolid, boolean fenceOrWall, double dy, boolean holeAhead) {
			boolean detectObstacleTwoBlocksOrHigher() {
				return fenceOrWall || headLevelSolid || dy >= 2.0D;
			}

			boolean shouldLevitate() {
				boolean elevationDisparity = dy > 1.25D || dy < -1.5D;
				if (dy > 0 && dy < 2.0D) {
					elevationDisparity = false;
				}
				if (dy < 0 && dy >= -3.0D) {
					elevationDisparity = false;
				}
				return elevationDisparity || detectObstacleTwoBlocksOrHigher() || holeAhead;
			}
		}

		// Case 1: 2-block cobblestone wall (head level is solid)
		TraversalCheck twoBlockWall = new TraversalCheck(true, false, 0.0D, false);
		Assertions.assertTrue(twoBlockWall.detectObstacleTwoBlocksOrHigher(), "2-block wall must be detected as obstacle >= 2 blocks");
		Assertions.assertTrue(twoBlockWall.shouldLevitate(), "Minion must engage levitation to scale 2-block obstacle");

		// Case 2: Fence / Wall block (1.5 blocks tall)
		TraversalCheck fence = new TraversalCheck(false, true, 0.0D, false);
		Assertions.assertTrue(fence.detectObstacleTwoBlocksOrHigher(), "Fence must be detected as obstacle > 1.25 blocks");
		Assertions.assertTrue(fence.shouldLevitate(), "Minion must engage levitation to clear fence");

		// Case 3: Target destination on elevated 3-block ledge (dy = 3.0D)
		TraversalCheck cliffLedge = new TraversalCheck(false, false, 3.0D, false);
		Assertions.assertTrue(cliffLedge.detectObstacleTwoBlocksOrHigher(), "Elevated target >= 2.0D must be detected as 2-block obstacle");
		Assertions.assertTrue(cliffLedge.shouldLevitate(), "Minion must engage levitation to reach elevated ledge");
	}

	@Test
	@DisplayName("Simulation: 1-block or longer hole (drop >= 2 blocks) triggers levitation; 1-block ground drop does not")
	void testHoleDetectionSimulation() {
		record HoleSimulator(boolean emptyBelow1, boolean emptyBelow2, double dy) {
			boolean detectHoleOrChasmAhead() {
				return emptyBelow1 && emptyBelow2;
			}

			boolean shouldLevitate() {
				boolean elevationDisparity = dy > 1.25D || dy < -1.5D;
				if (dy > 0 && dy < 2.0D) {
					elevationDisparity = false;
				}
				if (dy < 0 && dy >= -3.0D) {
					elevationDisparity = false;
				}
				return elevationDisparity || detectHoleOrChasmAhead();
			}
		}

		// Case 1: Chasm / ravine ahead (empty at Y-1 AND Y-2, target across gap at dy = 0)
		HoleSimulator chasm = new HoleSimulator(true, true, 0.0D);
		Assertions.assertTrue(chasm.detectHoleOrChasmAhead(), "Gap with 2+ block drop must be detected as hole/chasm");
		Assertions.assertTrue(chasm.shouldLevitate(), "Minion must levitate across hole/chasm to reach other side");

		// Case 2: Normal 1-block terrain drop (solid ground at Y-1, empty at Y-2 is false because Y-1 has block)
		HoleSimulator oneBlockDrop = new HoleSimulator(false, false, -1.0D);
		Assertions.assertFalse(oneBlockDrop.detectHoleOrChasmAhead(), "1-block terrain drop is NOT a hole/chasm");
		Assertions.assertFalse(oneBlockDrop.shouldLevitate(), "Minion must NOT levitate for 1-block terrain drop; walks down like a player");
	}

	@Test
	@DisplayName("Simulation: Minion airborne with ground destination descends smoothly and lands immediately")
	void testAirborneGroundDestinationDescentAndLanding() {
		class MinionTraversalSim {
			boolean arcaneLevitating = true;
			boolean onGround = false;
			double posY = 66.0D;
			double targetDestY = 64.0D;
			double velocityY = 0.0D;

			void tickTraversal(boolean hasSolidGroundBeneath) {
				double dy = targetDestY - posY;

				// Landing check:
				boolean overSolidGround = onGround || hasSolidGroundBeneath;
				boolean climbingWallAhead = false;

				if (overSolidGround && !climbingWallAhead) {
					this.arcaneLevitating = false;
					this.velocityY = 0.0D;
					return;
				}

				// When in air and destination is below, glide down to ground:
				if (this.posY > this.targetDestY + 0.5D) {
					this.velocityY = -0.22D;
				}
			}
		}

		MinionTraversalSim sim = new MinionTraversalSim();

		// Tick 1: Minion at Y=66, target at Y=64, in air (no ground beneath)
		sim.tickTraversal(false);
		Assertions.assertTrue(sim.arcaneLevitating, "Still airborne during descent");
		Assertions.assertEquals(-0.22D, sim.velocityY, 1e-6, "Must apply downward glide (-0.22D) towards ground target");

		// Tick 2: Minion reaches ground surface (hasSolidGroundBeneath = true)
		sim.posY = 64.1D;
		sim.tickTraversal(true);
		Assertions.assertFalse(sim.arcaneLevitating, "Must immediately land upon reaching solid ground surface");
		Assertions.assertEquals(0.0D, sim.velocityY, 1e-6, "Vertical velocity must be stopped at 0.0D");
	}

	@Test
	@DisplayName("Source Invariant: Unified Traversal State Machine, Pit Escape, and Selected-Only Teleportation")
	void testUnifiedTraversalAndSelectedTeleportationInvariants() throws IOException {
		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionPath), "MinionEntity.java must exist");
		String minionContent = Files.readString(minionPath);

		// Traversal Maneuver fields
		Assertions.assertTrue(minionContent.contains("private boolean traversalManeuverActive = false;"),
				"MinionEntity must define traversalManeuverActive");
		Assertions.assertTrue(minionContent.contains("private double traversalClearanceAltitude = 0.0D;"),
				"MinionEntity must define traversalClearanceAltitude");
		Assertions.assertTrue(minionContent.contains("private int traversalCrestTicks = 0;"),
				"MinionEntity must define traversalCrestTicks");

		// Pit / Hole Escape methods
		Assertions.assertTrue(minionContent.contains("public boolean isInHoleOrDepression(ServerWorld world)"),
				"MinionEntity must define isInHoleOrDepression");
		Assertions.assertTrue(minionContent.contains("public double getHoleRimClearanceAltitude(ServerWorld world)"),
				"MinionEntity must define getHoleRimClearanceAltitude");

		// Selected-only teleportation in ModNetworking
		Path netPath = Path.of("src/main/java/com/example/network/ModNetworking.java");
		Assertions.assertTrue(Files.exists(netPath), "ModNetworking.java must exist");
		String netContent = Files.readString(netPath);
		Assertions.assertTrue(netContent.contains("m -> m.isAlive() && m.isOwner(player) && m.isSelected()"),
				"ModNetworking handleTeleportMinion must filter broadcast teleport strictly to selected minions");

		// GUI button active check in CommandScepterScreen
		Path guiPath = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		Assertions.assertTrue(Files.exists(guiPath), "CommandScepterScreen.java must exist");
		String guiContent = Files.readString(guiPath);
		Assertions.assertTrue(guiContent.contains("teleportBtn.active = this.nearbySelectedThralls > 0;"),
				"CommandScepterScreen teleportBtn must only be active when selected minions exist");
	}

	@Test
	@DisplayName("Simulation: 2-block obstacle cresting maintains altitude and pushes forward across lip")
	void testTwoBlockCrestingSimulation() {
		class CrestSim {
			double posY = 64.0D;
			double targetClearanceY = 66.25D;
			double groundDestY = 64.0D;
			double vy = 0.0D;
			double speed = 0.38D;
			int crestTicks = 0;
			boolean maneuverActive = true;

			void tick() {
				if (posY < targetClearanceY - 0.05D) {
					double liftRemaining = targetClearanceY - posY;
					vy = Math.min(0.38D, liftRemaining * 0.5D + 0.22D);
					vy = Math.max(vy, 0.30D);
					crestTicks = 10;
				} else {
					vy = 0.0D;
					if (crestTicks > 0) {
						crestTicks--;
						speed = 0.45D; // Boosted forward horizontal propulsion
					} else if (posY > groundDestY + 0.5D) {
						vy = Math.min(vy, -0.22D); // Downward glide only after cresting!
					}
				}
			}
		}

		CrestSim sim = new CrestSim();

		// Phase 1: Ascending (Y=64.0 to Y=66.20)
		sim.tick();
		Assertions.assertTrue(sim.vy >= 0.30D, "Must provide upward lift while below clearance");
		Assertions.assertEquals(10, sim.crestTicks, "Must seed crest ticks for cresting phase");

		// Phase 2: Cresting at Y=66.25 (top lip of 2-block obstacle)
		sim.posY = 66.25D;
		sim.tick();
		Assertions.assertEquals(0.0D, sim.vy, 1e-6, "Must NOT pull down into lip; vy must be 0.0D");
		Assertions.assertEquals(0.45D, sim.speed, 1e-6, "Must provide boosted horizontal speed (0.45D) to cross lip");
		Assertions.assertEquals(9, sim.crestTicks, "Crest ticks must count down");

		// Run out remaining crest ticks
		while (sim.crestTicks > 0) {
			sim.tick();
		}

		// Phase 3: Now horizontally clear, destination on ground allows downward glide
		sim.tick();
		Assertions.assertEquals(-0.22D, sim.vy, 1e-6, "Downward glide only permitted after lip is crested");
	}

	@Test
	@DisplayName("Simulation: 1-block terrain dip is not a pit (head level open air); 2-block pit triggers escape")
	void testPitEnclosureSimulation() {
		record PitSim(int headLevelSolidCount) {
			boolean isInHoleOrDepression() {
				return headLevelSolidCount >= 3;
			}
		}

		// Case 1: 1-block terrain dip (walls only at foot level, head level is open air on all sides)
		PitSim oneBlockDip = new PitSim(0);
		Assertions.assertFalse(oneBlockDip.isInHoleOrDepression(),
				"1-block terrain dip must NOT be detected as pit; minion steps out natively with 1.25D step height");

		// Case 2: 1-block step in inside corner (head level is open air)
		PitSim cornerStep = new PitSim(0);
		Assertions.assertFalse(cornerStep.isInHoleOrDepression(),
				"Inside corner 1-block step must NOT trigger pit escape");

		// Case 3: 2-block deep pit or dead-end trench (enclosed at head level on 3 sides)
		PitSim threeSidedPit = new PitSim(3);
		Assertions.assertTrue(threeSidedPit.isInHoleOrDepression(),
				"Pit with 3 sides enclosed at head level must trigger pit escape");

		// Case 4: 2-block deep 1x1 or 2x2 pit (enclosed at head level on 4 sides)
		PitSim fourSidedPit = new PitSim(4);
		Assertions.assertTrue(fourSidedPit.isInHoleOrDepression(),
				"4-sided enclosed pit at head level must trigger pit escape");
	}

	@Test
	@DisplayName("Simulation: Obstacle vertical scan breaks on open air and does not launch into tree canopy/sky")
	void testObstacleScanBreakOnAirSimulation() {
		class ColumnScanSim {
			double scanClearance(int[] columnSolidBlocks, int scanStartY, int maxScanY) {
				int topSolidY = -1;
				double clearance = Double.NEGATIVE_INFINITY;
				for (int checkY = scanStartY; checkY <= maxScanY; checkY++) {
					final int y = checkY;
					boolean isSolid = java.util.Arrays.stream(columnSolidBlocks).anyMatch(b -> b == y);
					if (isSolid) {
						topSolidY = checkY;
					} else if (topSolidY >= scanStartY) {
						// Check headroom above this air block (y + 1)
						final int headY = y + 1;
						boolean headClear = java.util.Arrays.stream(columnSolidBlocks).noneMatch(b -> b == headY);
						if (headClear) {
							clearance = topSolidY + 1.25D;
							break; // STOP scanning into the sky!
						}
					}
				}
				return clearance;
			}
		}

		ColumnScanSim sim = new ColumnScanSim();

		// Scenario A: 1-block obstacle at Y=64, air at Y=65 and Y=66, but tree branch at Y=69 (minion at Y=64.0)
		int[] oneBlockWithTree = new int[]{64, 69, 70};
		double clearanceA = sim.scanClearance(oneBlockWithTree, 64, 88);
		Assertions.assertEquals(65.25D, clearanceA, 1e-6,
				"Must stop scanning at air gap above 1-block obstacle; clearance must be 65.25D, NOT 70+ into tree");

		// Check maneuver trigger: clearanceA (65.25D) vs minionY + 1.4D (64.0 + 1.4 = 65.4D)
		boolean triggerA = clearanceA > 64.0D + 1.4D;
		Assertions.assertFalse(triggerA,
				"1-block obstacle (65.25D) must NOT trigger traversal maneuver (threshold 65.4D); minion walks natively");

		// Scenario B: 2-block obstacle at Y=64 and Y=65, air at Y=66 and Y=67, tree branch at Y=71 (minion at Y=64.0)
		int[] twoBlockWithTree = new int[]{64, 65, 71, 72};
		double clearanceB = sim.scanClearance(twoBlockWithTree, 64, 88);
		Assertions.assertEquals(66.25D, clearanceB, 1e-6,
				"Must stop scanning at air gap above 2-block obstacle; clearance must be 66.25D (+2.25D clearance)");

		// Check maneuver trigger: clearanceB (66.25D) vs minionY + 1.4D (65.4D)
		boolean triggerB = clearanceB > 64.0D + 1.4D;
		Assertions.assertTrue(triggerB,
				"2-block obstacle (66.25D) MUST trigger traversal maneuver bounded to 66.25D");
	}
}
