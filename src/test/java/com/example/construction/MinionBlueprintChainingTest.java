package com.example.construction;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests validating autonomous Minion Builder Blueprint Chaining and Vicinity Transitions.
 * Ensures that when a builder minion completes a blueprint or when new blueprints are placed down
 * in their vicinity (<= 128 blocks) while they are working or stationed, they seamlessly transition
 * to the next nearest blueprint without getting stuck in a sleeping/stationed stance.
 */
public class MinionBlueprintChainingTest {

	// =========================================================================
	// Domain Simulation Models for Blueprint Chaining
	// =========================================================================

	static class SimulatedTask {
		final String id;
		final BlockPos worldPos;
		final String requiredMaterial;
		boolean claimed = false;
		boolean completed = false;

		SimulatedTask(String id, BlockPos worldPos, String requiredMaterial) {
			this.id = id;
			this.worldPos = worldPos;
			this.requiredMaterial = requiredMaterial;
		}
	}

	static class SimulatedConstructionSession {
		final UUID id = UUID.randomUUID();
		final String name;
		final BlockPos anchorPos;
		final UUID ownerUuid;
		boolean active = true;
		final List<SimulatedTask> tasks = new ArrayList<>();

		SimulatedConstructionSession(String name, BlockPos anchorPos, UUID ownerUuid) {
			this.name = name;
			this.anchorPos = anchorPos;
			this.ownerUuid = ownerUuid;
		}

		void addTask(String id, BlockPos pos, String material) {
			this.tasks.add(new SimulatedTask(id, pos, material));
		}

		SimulatedTask claimNextTask(UUID workerUuid) {
			if (!active) return null;
			for (SimulatedTask task : tasks) {
				if (!task.claimed && !task.completed) {
					task.claimed = true;
					return task;
				}
			}
			return null;
		}

		boolean hasPendingTasks() {
			if (!active) return false;
			for (SimulatedTask task : tasks) {
				if (!task.completed) return true;
			}
			return false;
		}

		void completeSession() {
			this.active = false;
			for (SimulatedTask task : tasks) {
				task.completed = true;
			}
		}
	}

	static class SimulatedConstructionManager {
		final List<SimulatedConstructionSession> sessions = new ArrayList<>();

		void addSession(SimulatedConstructionSession session) {
			this.sessions.add(session);
		}

		Optional<SimulatedConstructionSession> findNearestSessionForMinion(
			BlockPos minionPos,
			UUID ownerUuid,
			double maxDistance
		) {
			if (ownerUuid == null) return Optional.empty();
			double maxDistSq = maxDistance * maxDistance;
			SimulatedConstructionSession bestSession = null;
			double bestDistSq = Double.MAX_VALUE;

			for (SimulatedConstructionSession session : sessions) {
				if (!session.active || !session.ownerUuid.equals(ownerUuid) || !session.hasPendingTasks()) {
					continue;
				}
				double distSq = minionPos.getSquaredDistance(session.anchorPos);
				if (distSq <= maxDistSq && distSq < bestDistSq) {
					bestDistSq = distSq;
					bestSession = session;
				}
			}
			return Optional.ofNullable(bestSession);
		}

		void completeSession(SimulatedConstructionSession session, List<SimulatedBuilderMinion> minions) {
			session.completeSession();

			for (SimulatedBuilderMinion minion : minions) {
				minion.activelyBuilding = false;

				// Check if another active blueprint session exists in vicinity (<= 128 blocks)
				Optional<SimulatedConstructionSession> nextSession = findNearestSessionForMinion(
					minion.currentPos,
					session.ownerUuid,
					128.0D
				);

				if (nextSession.isPresent()) {
					// Chained build detected: keep awake, do not station at perimeter
					minion.sitting = false;
					minion.guardAnchor = null;
				} else {
					// No more blueprints in vicinity: station minion safely at perimeter
					minion.sitting = true;
					minion.guardAnchor = minion.currentPos;
				}
			}
		}

		void tickPeriodicMobilization(List<SimulatedBuilderMinion> allMinions) {
			// Periodic 20-tick mobilization for active sessions
			for (SimulatedConstructionSession activeSession : sessions) {
				if (activeSession.active && activeSession.hasPendingTasks()) {
					double maxDistSq = 128.0D * 128.0D;
					for (SimulatedBuilderMinion minion : allMinions) {
						if (minion.ownerUuid.equals(activeSession.ownerUuid)
								&& (minion.sitting || minion.guardAnchor != null)) {
							double distSq = minion.currentPos.getSquaredDistance(activeSession.anchorPos);
							if (distSq <= maxDistSq) {
								minion.sitting = false;
								minion.guardAnchor = null;
							}
						}
					}
				}
			}
		}
	}

	static class SimulatedBuilderMinion {
		final UUID uuid = UUID.randomUUID();
		final UUID ownerUuid;
		BlockPos currentPos;
		boolean sitting = false;
		BlockPos guardAnchor = null;
		boolean activelyBuilding = false;
		String equippedItem = "NONE";
		SimulatedConstructionSession currentSession = null;
		SimulatedTask currentTask = null;

		SimulatedBuilderMinion(UUID ownerUuid, BlockPos startingPos) {
			this.ownerUuid = ownerUuid;
			this.currentPos = startingPos;
		}

		boolean tryClaimTaskOrChainNextSession(SimulatedConstructionManager manager) {
			// 1. Try claim task from current session if active
			if (this.currentSession != null && this.currentSession.active) {
				SimulatedTask next = this.currentSession.claimNextTask(this.uuid);
				if (next != null) {
					this.currentTask = next;
					this.activelyBuilding = true;
					this.equippedItem = next.requiredMaterial;
					return true;
				}
			}

			// 2. Chaining: Find nearest active blueprint session in 128-block vicinity
			Optional<SimulatedConstructionSession> chainedOpt = manager.findNearestSessionForMinion(
				this.currentPos,
				this.ownerUuid,
				128.0D
			);

			if (chainedOpt.isPresent()) {
				SimulatedConstructionSession chained = chainedOpt.get();
				this.currentSession = chained;
				SimulatedTask chainedTask = chained.claimNextTask(this.uuid);
				if (chainedTask != null) {
					this.currentTask = chainedTask;
					this.activelyBuilding = true;
					this.sitting = false;
					this.guardAnchor = null;
					this.equippedItem = chainedTask.requiredMaterial;
					return true;
				}
				// Active session found waiting on ally or ready state
				this.currentTask = null;
				this.activelyBuilding = true;
				this.sitting = false;
				this.guardAnchor = null;
				return true;
			}

			// No active session in vicinity
			this.currentSession = null;
			this.currentTask = null;
			this.activelyBuilding = false;
			this.equippedItem = "SWORD";
			return false;
		}
	}

	// =========================================================================
	// Scenario Tests: Autonomous Vicinity Transition & Chaining
	// =========================================================================

	@Test
	@DisplayName("Minion builder immediately transitions to nearby placed blueprint upon completing previous build")
	void testImmediateChainingToNearbyBlueprintOnSessionCompletion() {
		UUID ownerId = UUID.randomUUID();
		SimulatedConstructionManager manager = new SimulatedConstructionManager();
		SimulatedBuilderMinion builder = new SimulatedBuilderMinion(ownerId, new BlockPos(100, 64, 100));

		// Session 1: Gatehouse at [100, 64, 100]
		SimulatedConstructionSession session1 = new SimulatedConstructionSession("Gatehouse", new BlockPos(100, 64, 100), ownerId);
		session1.addTask("task1_1", new BlockPos(100, 64, 100), "STONE_BRICKS");
		session1.addTask("task1_2", new BlockPos(100, 65, 100), "OAK_DOOR");

		// Session 2: Guard Tower placed nearby at [140, 64, 120] (distance ~44 blocks <= 128 blocks)
		SimulatedConstructionSession session2 = new SimulatedConstructionSession("GuardTower", new BlockPos(140, 64, 120), ownerId);
		session2.addTask("task2_1", new BlockPos(140, 64, 120), "COBBLESTONE");
		session2.addTask("task2_2", new BlockPos(140, 65, 120), "LADDER");

		manager.addSession(session1);
		manager.addSession(session2);

		// Builder starts on Session 1
		Assertions.assertTrue(builder.tryClaimTaskOrChainNextSession(manager));
		Assertions.assertEquals(session1, builder.currentSession);
		Assertions.assertEquals("STONE_BRICKS", builder.equippedItem);
		Assertions.assertTrue(builder.activelyBuilding);

		// Complete all tasks of Session 1
		session1.tasks.get(0).completed = true;
		session1.tasks.get(1).completed = true;
		manager.completeSession(session1, List.of(builder));

		// ConstructionManager.completeSession MUST NOT force minion to sit/station because session2 is active in vicinity
		Assertions.assertFalse(builder.sitting, "Builder must NOT sit down when a nearby blueprint is pending");
		Assertions.assertNull(builder.guardAnchor, "Guard anchor must be null when a nearby blueprint is pending");

		// MinionBuildGoal ticks and chains immediately to Session 2
		boolean chained = builder.tryClaimTaskOrChainNextSession(manager);
		Assertions.assertTrue(chained, "Builder must successfully chain to the nearby Guard Tower session");
		Assertions.assertEquals(session2, builder.currentSession, "Current session must now be the Guard Tower");
		Assertions.assertEquals("COBBLESTONE", builder.equippedItem, "Builder must equip the material for the new blueprint");
		Assertions.assertTrue(builder.activelyBuilding, "Builder must remain in actively building posture");
		Assertions.assertFalse(builder.sitting, "Builder must not be sitting while chained to new blueprint");
	}

	@Test
	@DisplayName("Minion builder stations at perimeter only when NO pending blueprints exist within 128 blocks")
	void testStationingAtPerimeterWhenNoNearbyBlueprintsRemain() {
		UUID ownerId = UUID.randomUUID();
		SimulatedConstructionManager manager = new SimulatedConstructionManager();
		SimulatedBuilderMinion builder = new SimulatedBuilderMinion(ownerId, new BlockPos(200, 64, 200));

		// Session 1: Watchpost at [200, 64, 200]
		SimulatedConstructionSession session1 = new SimulatedConstructionSession("Watchpost", new BlockPos(200, 64, 200), ownerId);
		session1.addTask("t1", new BlockPos(200, 64, 200), "SPRUCE_LOG");
		manager.addSession(session1);

		// Far-away session at [500, 64, 500] (distance ~424 blocks > 128 max distance)
		SimulatedConstructionSession farSession = new SimulatedConstructionSession("FarCastle", new BlockPos(500, 64, 500), ownerId);
		farSession.addTask("t_far", new BlockPos(500, 64, 500), "OBSIDIAN");
		manager.addSession(farSession);

		builder.tryClaimTaskOrChainNextSession(manager);
		Assertions.assertEquals(session1, builder.currentSession);

		// Complete session 1
		session1.tasks.get(0).completed = true;
		manager.completeSession(session1, List.of(builder));

		// Far session is beyond 128 blocks; builder should safely enter stationed/sitting stance at perimeter
		Assertions.assertTrue(builder.sitting, "Builder must enter sitting stance when no nearby blueprints exist");
		Assertions.assertEquals(builder.currentPos, builder.guardAnchor, "Builder must set guard anchor at perimeter pos");
		Assertions.assertFalse(builder.activelyBuilding, "Builder must stop active building");

		boolean chained = builder.tryClaimTaskOrChainNextSession(manager);
		Assertions.assertFalse(chained, "Builder must NOT chain to blueprints beyond the 128-block vicinity threshold");
	}

	@Test
	@DisplayName("Minion builder chains to the nearest blueprint when multiple blueprints are placed simultaneously")
	void testNearestBlueprintChainingPriority() {
		UUID ownerId = UUID.randomUUID();
		SimulatedConstructionManager manager = new SimulatedConstructionManager();
		BlockPos origin = new BlockPos(0, 64, 0);
		SimulatedBuilderMinion builder = new SimulatedBuilderMinion(ownerId, origin);

		SimulatedConstructionSession current = new SimulatedConstructionSession("CurrentBuild", origin, ownerId);
		current.addTask("c1", origin, "DIRT");

		// Candidate 1: 80 blocks away
		SimulatedConstructionSession midDistance = new SimulatedConstructionSession("MidBuilding", new BlockPos(80, 64, 0), ownerId);
		midDistance.addTask("m1", new BlockPos(80, 64, 0), "STONE");

		// Candidate 2: 25 blocks away (closer!)
		SimulatedConstructionSession closeBuilding = new SimulatedConstructionSession("CloseBuilding", new BlockPos(25, 64, 0), ownerId);
		closeBuilding.addTask("cl1", new BlockPos(25, 64, 0), "BRICKS");

		// Candidate 3: 110 blocks away
		SimulatedConstructionSession farBuilding = new SimulatedConstructionSession("FarBuilding", new BlockPos(110, 64, 0), ownerId);
		farBuilding.addTask("f1", new BlockPos(110, 64, 0), "GLASS");

		manager.addSession(current);
		manager.addSession(midDistance);
		manager.addSession(closeBuilding);
		manager.addSession(farBuilding);

		// Complete current build
		builder.tryClaimTaskOrChainNextSession(manager);
		current.tasks.get(0).completed = true;
		manager.completeSession(current, List.of(builder));

		// Chain to next
		builder.tryClaimTaskOrChainNextSession(manager);
		Assertions.assertEquals(closeBuilding, builder.currentSession, "Builder must select the nearest blueprint (25 blocks) over farther candidates");
		Assertions.assertEquals("BRICKS", builder.equippedItem);
	}

	@Test
	@DisplayName("Stationed builder is mobilized automatically when a new blueprint is placed in their vicinity")
	void testPeriodicMobilizationOfStationedBuilders() {
		UUID ownerId = UUID.randomUUID();
		SimulatedConstructionManager manager = new SimulatedConstructionManager();
		BlockPos stationedPos = new BlockPos(50, 64, 50);
		SimulatedBuilderMinion builder = new SimulatedBuilderMinion(ownerId, stationedPos);

		// Minion is currently resting / stationed
		builder.sitting = true;
		builder.guardAnchor = stationedPos;
		builder.activelyBuilding = false;

		// Player places a new blueprint at [80, 64, 70] (~36 blocks away)
		SimulatedConstructionSession newBlueprint = new SimulatedConstructionSession("Barracks", new BlockPos(80, 64, 70), ownerId);
		newBlueprint.addTask("b1", new BlockPos(80, 64, 70), "OAK_PLANKS");
		manager.addSession(newBlueprint);

		// ConstructionManager periodic 20-tick mobilization cycle runs
		manager.tickPeriodicMobilization(List.of(builder));

		// Stationed builder must be woken up
		Assertions.assertFalse(builder.sitting, "Stationed builder must be woken up by the periodic mobilization scan");
		Assertions.assertNull(builder.guardAnchor, "Guard anchor must be cleared on mobilization");

		// Builder claims task and begins construction
		boolean started = builder.tryClaimTaskOrChainNextSession(manager);
		Assertions.assertTrue(started, "Mobilized builder must successfully claim and start the placed blueprint");
		Assertions.assertEquals(newBlueprint, builder.currentSession);
		Assertions.assertEquals("OAK_PLANKS", builder.equippedItem);
	}

	// =========================================================================
	// Source Invariant Contracts
	// =========================================================================

	@Test
	@DisplayName("Source Invariant: ConstructionManager.completeSession checks vicinity before stationing builders")
	void testConstructionManagerCompleteSessionSourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(path), "ConstructionManager.java must exist");
		String content = Files.readString(path);

		// 1. findNearestSessionForMinion is called in completeSession with 128-block radius
		Assertions.assertTrue(
			content.contains("Optional<ConstructionSession> nextSession = findNearestSessionForMinion("),
			"ConstructionManager.completeSession must check findNearestSessionForMinion before putting minions to sleep"
		);
		Assertions.assertTrue(
			content.contains("128.0D,"),
			"ConstructionManager must search within a 128-block radius for chained blueprints"
		);
		Assertions.assertTrue(
			content.contains("MinionRole.BUILDER"),
			"ConstructionManager must specifically check for builder role in blueprint chaining"
		);

		// 2. If nextSession is present, minion does not sit and clears guard anchor
		Assertions.assertTrue(
			content.contains("if (nextSession.isPresent()) {"),
			"ConstructionManager.completeSession must have a dedicated branch when nextSession.isPresent()"
		);
		Assertions.assertTrue(
			content.contains("minion.setSitting(false);"),
			"ConstructionManager.completeSession must keep sitting = false when chaining"
		);
		Assertions.assertTrue(
			content.contains("minion.setGuardAnchorPos(null);"),
			"ConstructionManager.completeSession must clear guard anchor pos when chaining"
		);
	}

	@Test
	@DisplayName("Source Invariant: ConstructionManager.tick runs periodic 20-tick mobilization scan for stationed builders")
	void testConstructionManagerPeriodicMobilizationSourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(path), "ConstructionManager.java must exist");
		String content = Files.readString(path);

		// 1. Every 20 ticks mobilization scan
		Assertions.assertTrue(
			content.contains("if (currentTick % 20L == 0L) {"),
			"ConstructionManager.tick must perform periodic 20-tick checks"
		);
		Assertions.assertTrue(
			content.contains("mobilizationBox = new Box("),
			"ConstructionManager.tick must create a 128-block mobilization box"
		);
		Assertions.assertTrue(
			content.contains("m.matchesRole(MinionRole.BUILDER)"),
			"ConstructionManager.tick must target builder minions for mobilization"
		);
		Assertions.assertTrue(
			content.contains("(m.isSitting() || m.getGuardAnchorPos() != null)"),
			"ConstructionManager.tick must detect stationed/sitting builders"
		);
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal implements autonomous chaining and persistent shouldContinue")
	void testMinionBuildGoalChainingSourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// 1. tryClaimTaskOrChainNextSession handles task claim and fallback chaining
		Assertions.assertTrue(
			content.contains("private boolean tryClaimTaskOrChainNextSession(ServerWorld serverWorld)"),
			"MinionBuildGoal must implement tryClaimTaskOrChainNextSession"
		);
		Assertions.assertTrue(
			content.contains("Optional<ConstructionSession> chainedOpt = ConstructionManager.getInstance().findNearestSessionForMinion("),
			"MinionBuildGoal must query ConstructionManager for chained active sessions"
		);

		// 2. shouldContinue checks vicinity if minion is sitting or current session ends
		Assertions.assertTrue(
			content.contains("if (this.currentSession == null || !this.currentSession.isActive()) {"),
			"MinionBuildGoal.shouldContinue must check for chained sessions when current session finishes"
		);
		Assertions.assertTrue(
			content.contains("Optional<ConstructionSession> chained = ConstructionManager.getInstance().findNearestSessionForMinion("),
			"MinionBuildGoal.shouldContinue must query chained session within 128 blocks"
		);

		// 3. canStart supports autonomous wake-up for stationed builders within 112 blocks
		Assertions.assertTrue(
			content.contains("double searchRadius = this.minion.isSitting() ? 112.0D : 128.0D;"),
			"MinionBuildGoal.canStart must allow stationed minions within 112 blocks to autonomously start build goal"
		);
	}
}
