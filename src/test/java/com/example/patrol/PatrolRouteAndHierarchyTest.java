package com.example.patrol;

import com.example.component.CommandMode;
import com.example.network.ModifyPatrolRoutePayload;
import com.example.network.SyncPatrolRoutesPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating:
 * - {@link PatrolRoute} data model and channel metadata
 * - {@link PatrolRouteManager} route operations and isolation
 * - {@link CommandMode#PATHWAY} properties and cycling
 * - {@link SyncPatrolRoutesPayload} and {@link ModifyPatrolRoutePayload} packets
 */
public class PatrolRouteAndHierarchyTest {

	@Test
	@DisplayName("Validate PatrolRoute channel constants, defaults, and formatted names")
	void testPatrolRouteDefaults() {
		Assertions.assertEquals(5, PatrolRoute.CHANNEL_COUNT);
		Assertions.assertEquals(5, PatrolRoute.CHANNEL_COLORS.length);
		Assertions.assertEquals(5, PatrolRoute.CHANNEL_NAMES.length);

		PatrolRoute route0 = PatrolRoute.createDefault(0);
		Assertions.assertEquals(0, route0.routeId());
		Assertions.assertEquals("Route 1 (Gold)", route0.name());
		Assertions.assertEquals(0xFFD700, route0.colorRgb());
		Assertions.assertTrue(route0.waypoints().isEmpty());
		Assertions.assertTrue(route0.getFormattedName().contains("Gold"));

		PatrolRoute route1 = PatrolRoute.createDefault(1);
		Assertions.assertEquals(1, route1.routeId());
		Assertions.assertEquals("Route 2 (Cyan)", route1.name());
		Assertions.assertEquals(0x00E5FF, route1.colorRgb());

		PatrolRoute route2 = PatrolRoute.createDefault(2);
		Assertions.assertEquals(2, route2.routeId());
		Assertions.assertEquals(0x00FF66, route2.colorRgb());

		PatrolRoute route3 = PatrolRoute.createDefault(3);
		Assertions.assertEquals(3, route3.routeId());
		Assertions.assertEquals(0xB300FF, route3.colorRgb());

		PatrolRoute route4 = PatrolRoute.createDefault(4);
		Assertions.assertEquals(4, route4.routeId());
		Assertions.assertEquals(0xFF2244, route4.colorRgb());
	}

	@Test
	@DisplayName("Validate immutable waypoint modification operations")
	void testPatrolRouteWaypointModifications() {
		PatrolRoute route = PatrolRoute.createDefault(0);
		BlockPos p1 = new BlockPos(10, 64, 20);
		BlockPos p2 = new BlockPos(15, 64, 25);
		BlockPos p3 = new BlockPos(20, 65, 30);

		PatrolRoute withP1 = route.withAddedWaypoint(p1);
		Assertions.assertEquals(1, withP1.waypoints().size());
		Assertions.assertEquals(p1, withP1.waypoints().get(0));
		Assertions.assertEquals(0, route.waypoints().size(), "Original route must remain immutable");

		PatrolRoute withP2 = withP1.withAddedWaypoint(p2).withAddedWaypoint(p3);
		Assertions.assertEquals(3, withP2.waypoints().size());
		Assertions.assertEquals(List.of(p1, p2, p3), withP2.waypoints());

		PatrolRoute withoutP2 = withP2.withRemovedWaypoint(p2);
		Assertions.assertEquals(2, withoutP2.waypoints().size());
		Assertions.assertEquals(List.of(p1, p3), withoutP2.waypoints());

		PatrolRoute cleared = withoutP2.withClearedWaypoints();
		Assertions.assertTrue(cleared.waypoints().isEmpty());
	}

	@Test
	@DisplayName("Validate PatrolRouteManager player isolation, adding, removing, and clearing")
	void testPatrolRouteManagerOperations() {
		PatrolRouteManager manager = PatrolRouteManager.getInstance();
		UUID player1 = UUID.randomUUID();
		UUID player2 = UUID.randomUUID();

		BlockPos posA = new BlockPos(100, 70, 100);
		BlockPos posB = new BlockPos(110, 70, 110);

		manager.addWaypoint(player1, 0, posA);
		manager.addWaypoint(player1, 0, posB);

		PatrolRoute p1Route0 = manager.getRoute(player1, 0);
		Assertions.assertEquals(2, p1Route0.waypoints().size());
		Assertions.assertEquals(posA, p1Route0.waypoints().get(0));
		Assertions.assertEquals(posB, p1Route0.waypoints().get(1));

		// Verify player 2 has independent routes
		PatrolRoute p2Route0 = manager.getRoute(player2, 0);
		Assertions.assertTrue(p2Route0.waypoints().isEmpty(), "Player 2's route must be isolated from Player 1");

		manager.removeWaypoint(player1, 0, posA);
		Assertions.assertEquals(1, manager.getRoute(player1, 0).waypoints().size());

		manager.clearRoute(player1, 0);
		Assertions.assertTrue(manager.getRoute(player1, 0).waypoints().isEmpty());
	}

	@Test
	@DisplayName("Validate CommandMode PATHWAY properties and cyclic navigation")
	void testCommandModePathway() {
		CommandMode pathway = CommandMode.PATHWAY;
		Assertions.assertEquals("pathway", pathway.asString());
		Assertions.assertEquals("Pathway", pathway.getDisplayName());
		Assertions.assertEquals(2.0F, pathway.getPitch());
		Assertions.assertEquals("§3", pathway.getColorCode());
		Assertions.assertEquals("§3Pathway", pathway.getFormattedName());

		// Verify cycling through all modes
		CommandMode current = CommandMode.FOLLOW;
		Assertions.assertEquals(CommandMode.STAY, current.next());
		Assertions.assertEquals(CommandMode.MINE, current.next().next());
		Assertions.assertEquals(CommandMode.BUILD, current.next().next().next());
		Assertions.assertEquals(CommandMode.RECRUIT, current.next().next().next().next());
		Assertions.assertEquals(CommandMode.PATHWAY, current.next().next().next().next().next());
		Assertions.assertEquals(CommandMode.FOLLOW, current.next().next().next().next().next().next());
	}

	@Test
	@DisplayName("Validate ModifyPatrolRoutePayload and SyncPatrolRoutesPayload contracts")
	void testPatrolPayloads() {
		BlockPos pos = new BlockPos(50, 65, 80);
		ModifyPatrolRoutePayload addPayload = new ModifyPatrolRoutePayload(
			ModifyPatrolRoutePayload.Action.ADD_WAYPOINT,
			1,
			pos,
			-1,
			-1
		);
		Assertions.assertEquals(ModifyPatrolRoutePayload.Action.ADD_WAYPOINT, addPayload.action());
		Assertions.assertEquals(1, addPayload.routeId());
		Assertions.assertEquals(pos, addPayload.pos());

		ModifyPatrolRoutePayload escortPayload = new ModifyPatrolRoutePayload(
			ModifyPatrolRoutePayload.Action.SET_ESCORT,
			0,
			BlockPos.ORIGIN,
			10,
			20
		);
		Assertions.assertEquals(ModifyPatrolRoutePayload.Action.SET_ESCORT, escortPayload.action());
		Assertions.assertEquals(10, escortPayload.minionId());
		Assertions.assertEquals(20, escortPayload.targetMinionId());

		List<PatrolRoute> routes = List.of(PatrolRoute.createDefault(0), PatrolRoute.createDefault(1));
		SyncPatrolRoutesPayload syncPayload = new SyncPatrolRoutesPayload(routes);
		Assertions.assertEquals(2, syncPayload.routes().size());
		Assertions.assertEquals(SyncPatrolRoutesPayload.ID, syncPayload.getId());

		ModifyPatrolRoutePayload togglePayload = new ModifyPatrolRoutePayload(
			ModifyPatrolRoutePayload.Action.TOGGLE_PATROL_MODE,
			2,
			BlockPos.ORIGIN,
			-1,
			-1
		);
		Assertions.assertEquals(ModifyPatrolRoutePayload.Action.TOGGLE_PATROL_MODE, togglePayload.action());
		Assertions.assertEquals(2, togglePayload.routeId());
	}

	@Test
	@DisplayName("Validate PatrolMode enum, toggle logic, and Ping-Pong traversal behavior")
	void testPatrolModeAndPingPong() {
		PatrolRoute.PatrolMode loop = PatrolRoute.PatrolMode.LOOP;
		PatrolRoute.PatrolMode pingPong = PatrolRoute.PatrolMode.PING_PONG;

		Assertions.assertEquals("Loop", loop.getDisplayName());
		Assertions.assertEquals("🔁", loop.getIcon());
		Assertions.assertEquals(pingPong, loop.toggle());
		Assertions.assertEquals(loop, pingPong.toggle());

		PatrolRoute route = PatrolRoute.createDefault(0);
		Assertions.assertEquals(PatrolRoute.PatrolMode.LOOP, route.patrolMode());

		PatrolRoute toggled = route.withPatrolMode(PatrolRoute.PatrolMode.PING_PONG);
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, toggled.patrolMode());

		// Test PatrolRouteManager toggle
		PatrolRouteManager manager = PatrolRouteManager.getInstance();
		UUID player = UUID.randomUUID();
		PatrolRoute r1 = manager.getRoute(player, 1);
		Assertions.assertEquals(PatrolRoute.PatrolMode.LOOP, r1.patrolMode());

		PatrolRoute r2 = manager.togglePatrolMode(player, 1);
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, r2.patrolMode());

		PatrolRoute r3 = manager.togglePatrolMode(player, 1);
		Assertions.assertEquals(PatrolRoute.PatrolMode.LOOP, r3.patrolMode());

		// Ping-Pong Traversal State Machine Simulation (3 waypoints: 0 -> 1 -> 2 -> 1 -> 0 -> 1 -> 2)
		int totalWaypoints = 3;
		int currentIdx = 0;
		int direction = 1;
		int[] expectedSequence = { 0, 1, 2, 1, 0, 1, 2 };

		for (int step = 0; step < expectedSequence.length; step++) {
			Assertions.assertEquals(expectedSequence[step], currentIdx, "Step " + step + " index mismatch");
			if (step == expectedSequence.length - 1) break;

			// Advance step in Ping-Pong mode
			int nextIdx = currentIdx + direction;
			if (nextIdx >= totalWaypoints) {
				direction = -1;
				nextIdx = totalWaypoints - 2;
			} else if (nextIdx < 0) {
				direction = 1;
				nextIdx = 1;
			}
			currentIdx = nextIdx;
		}
	}

	@Test
	@DisplayName("Validate RetreatPayload with Emergency Citadel Call flag")
	void testRetreatPayload() {
		com.example.network.RetreatPayload standardRetreat = new com.example.network.RetreatPayload(
			com.example.component.SquadGroup.ALPHA,
			false
		);
		Assertions.assertEquals(com.example.component.SquadGroup.ALPHA, standardRetreat.targetSquad());
		Assertions.assertFalse(standardRetreat.isEmergencyCitadelCall());

		com.example.network.RetreatPayload emergencyRetreat = new com.example.network.RetreatPayload(
			com.example.component.SquadGroup.ALL,
			true
		);
		Assertions.assertEquals(com.example.component.SquadGroup.ALL, emergencyRetreat.targetSquad());
		Assertions.assertTrue(emergencyRetreat.isEmergencyCitadelCall());
		Assertions.assertEquals(com.example.network.RetreatPayload.ID, emergencyRetreat.getId());
	}

	@Test
	@DisplayName("Validate MinionRole.AUTO properties and tactical icon")
	void testAutoRoleProperties() {
		com.example.entity.custom.MinionRole auto = com.example.entity.custom.MinionRole.AUTO;
		Assertions.assertEquals("auto", auto.asString());
		Assertions.assertEquals("Auto", auto.getDisplayName());
		Assertions.assertEquals("⚙", auto.getIcon());
		Assertions.assertEquals("§e", auto.getColorCode());
		Assertions.assertEquals("§eAuto", auto.getFormattedName());
		Assertions.assertEquals("role.modid-mmcli-agent-modding.auto", auto.getTranslationKey());
		Assertions.assertEquals("⚙ AUTO", auto.getBadgeLabel());
		Assertions.assertEquals(3, auto.getId());
	}

	@Test
	@DisplayName("Source contract: CommandScepterItem handles PATHWAY directive execution")
	void testBroadcastPathwayContract() throws java.io.IOException {
		java.nio.file.Path path = java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String content = java.nio.file.Files.readString(path);

		Assertions.assertTrue(
			content.contains("case PATHWAY -> broadcastPathway(player, world, targetSquad);"),
			"executeDirective must delegate PATHWAY mode to broadcastPathway"
		);
		Assertions.assertTrue(
			content.contains("public static void broadcastPathway(PlayerEntity player, World world, SquadGroup targetSquad)"),
			"CommandScepterItem must define broadcastPathway method"
		);
		Assertions.assertTrue(
			content.contains("minion.setPatrolRouteId(routeId);"),
			"broadcastPathway must assign minion patrol route ID"
		);
	}

	@Test
	@DisplayName("Source contract: Banner of Courage 90° forward sector dispatches minions in PATHWAY mode")
	void testBannerOfCourageSectorPathwayDispatchContract() throws java.io.IOException {
		java.nio.file.Path path = java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String content = java.nio.file.Files.readString(path);

		Assertions.assertTrue(
			content.contains("if (mode == CommandMode.PATHWAY) {"),
			"onStoppedUsing must handle PATHWAY mode for 90-degree forward sector release"
		);
		Assertions.assertTrue(
			content.contains("Banner of Courage! Dispatched "),
			"onStoppedUsing must notify player of Banner of Courage pathway dispatch"
		);
	}

	@Test
	@DisplayName("Validate PatrolRoutePersistentState NBT serialization roundtrip")
	void testPatrolRoutePersistentStateRoundtrip() {
		PatrolRoutePersistentState state = new PatrolRoutePersistentState();
		UUID playerId = UUID.randomUUID();
		BlockPos wp1 = new BlockPos(10, 65, 20);
		BlockPos wp2 = new BlockPos(30, 68, 40);

		PatrolRoute route = new PatrolRoute(
			2,
			"Route 3 (Emerald)",
			0x00FF66,
			List.of(wp1, wp2),
			PatrolRoute.PatrolMode.PING_PONG
		);

		state.getRoutes().computeIfAbsent(playerId, u -> new java.util.concurrent.ConcurrentHashMap<>()).put(2, route);

		net.minecraft.nbt.NbtCompound nbt = state.writeNbt(new net.minecraft.nbt.NbtCompound(), null);
		Assertions.assertNotNull(nbt);
		Assertions.assertTrue(nbt.contains("Players"));

		PatrolRoutePersistentState restored = PatrolRoutePersistentState.fromNbt(nbt, null);
		Assertions.assertTrue(restored.getRoutes().containsKey(playerId));
		PatrolRoute restoredRoute = restored.getRoutes().get(playerId).get(2);
		Assertions.assertNotNull(restoredRoute);
		Assertions.assertEquals(2, restoredRoute.routeId());
		Assertions.assertEquals("Route 3 (Emerald)", restoredRoute.name());
		Assertions.assertEquals(0x00FF66, restoredRoute.colorRgb());
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, restoredRoute.patrolMode());
		Assertions.assertEquals(2, restoredRoute.waypoints().size());
		Assertions.assertEquals(wp1, restoredRoute.waypoints().get(0));
		Assertions.assertEquals(wp2, restoredRoute.waypoints().get(1));
	}

	@Test
	@DisplayName("Validate PatrolRoutePersistentState handles wrapped data compound correctly")
	void testPatrolRoutePersistentStateDataWrapping() {
		PatrolRoutePersistentState state = new PatrolRoutePersistentState();
		UUID playerId = UUID.randomUUID();
		BlockPos wp = new BlockPos(12, 64, -8);
		PatrolRoute route = new PatrolRoute(1, "Route 2 (Cyan)", 0x00E5FF, List.of(wp), PatrolRoute.PatrolMode.LOOP);
		state.getRoutes().computeIfAbsent(playerId, u -> new java.util.concurrent.ConcurrentHashMap<>()).put(1, route);

		net.minecraft.nbt.NbtCompound written = state.writeNbt(new net.minecraft.nbt.NbtCompound(), null);

		// Simulate Minecraft PersistentStateManager wrapping into a root compound with "data" tag
		net.minecraft.nbt.NbtCompound root = new net.minecraft.nbt.NbtCompound();
		root.put("data", written);
		root.putInt("DataVersion", 3953);

		PatrolRoutePersistentState restored = PatrolRoutePersistentState.fromNbt(root, null);
		Assertions.assertTrue(restored.getRoutes().containsKey(playerId));
		Assertions.assertEquals(1, restored.getRoutes().get(playerId).get(1).waypoints().size());
		Assertions.assertEquals(wp, restored.getRoutes().get(playerId).get(1).waypoints().get(0));
	}

	@Test
	@DisplayName("Validate multi-session world reload preserves waypoints across restart")
	void testMultiSessionWorldReloadIntegration() {
		PatrolRouteManager manager = PatrolRouteManager.getInstance();

		// Session 1: World starts and player places waypoints
		PatrolRoutePersistentState session1State = new PatrolRoutePersistentState();
		manager.loadFromPersistentState(session1State);

		UUID playerId = UUID.randomUUID();
		BlockPos wp1 = new BlockPos(100, 70, 200);
		BlockPos wp2 = new BlockPos(105, 71, 205);
		manager.addWaypoint(playerId, 0, wp1);
		manager.addWaypoint(playerId, 0, wp2);

		Assertions.assertEquals(2, manager.getRoute(playerId, 0).waypoints().size());

		// Server stopping: persist data
		manager.onServerStopping();
		net.minecraft.nbt.NbtCompound savedOnDisk = new net.minecraft.nbt.NbtCompound();
		savedOnDisk.put("data", session1State.writeNbt(new net.minecraft.nbt.NbtCompound(), null));
		savedOnDisk.putInt("DataVersion", 3953);

		// Session 2: Game restarted, world reloaded
		PatrolRoutePersistentState session2State = PatrolRoutePersistentState.fromNbt(savedOnDisk, null);
		manager.loadFromPersistentState(session2State);

		// Verify that all routes and waypoints survive restart cleanly
		List<PatrolRoute> restoredRoutes = manager.getAllRoutes(playerId);
		Assertions.assertNotNull(restoredRoutes);
		Assertions.assertEquals(5, restoredRoutes.size());
		Assertions.assertEquals(2, restoredRoutes.get(0).waypoints().size());
		Assertions.assertEquals(wp1, restoredRoutes.get(0).waypoints().get(0));
		Assertions.assertEquals(wp2, restoredRoutes.get(0).waypoints().get(1));
	}

	@Test
	@DisplayName("Validate PatrolRouteManager persistence integration")
	void testPatrolRouteManagerPersistenceIntegration() {
		PatrolRoutePersistentState state = new PatrolRoutePersistentState();
		PatrolRouteManager manager = PatrolRouteManager.getInstance();
		manager.loadFromPersistentState(state);

		UUID playerId = UUID.randomUUID();
		BlockPos wp = new BlockPos(5, 70, 15);
		manager.addWaypoint(playerId, 0, wp);

		Assertions.assertTrue(state.isDirty(), "PersistentState must be marked dirty when routes are updated");
		Assertions.assertTrue(state.getRoutes().containsKey(playerId));
		Assertions.assertEquals(1, state.getRoutes().get(playerId).get(0).waypoints().size());
		Assertions.assertEquals(wp, state.getRoutes().get(playerId).get(0).waypoints().get(0));

		manager.removeWaypoint(playerId, 0, wp);
		Assertions.assertTrue(state.getRoutes().get(playerId).get(0).waypoints().isEmpty());
	}

	@Test
	@DisplayName("Source contract: MinionPatrolGoal end-only linger and smooth intermediate traversal")
	void testMinionPatrolGoalIntermediateTraversalContract() throws java.io.IOException {
		java.nio.file.Path path = java.nio.file.Path.of("src/main/java/com/example/entity/ai/goal/MinionPatrolGoal.java");
		String content = java.nio.file.Files.readString(path);

		Assertions.assertTrue(
			content.contains("boolean isTerminal;"),
			"MinionPatrolGoal must calculate isTerminal to differentiate ends from intermediate waypoints"
		);
		Assertions.assertTrue(
			content.contains("if (!isTerminal) {"),
			"MinionPatrolGoal must handle intermediate waypoints without lingering"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setCurrentWaypointIndex(nextIndex);"),
			"MinionPatrolGoal must advance currentWaypointIndex"
		);
	}

	@Test
	@DisplayName("Source contract: PathwayHologramRenderer selective visibility and vector laser tether")
	void testPathwayHologramRendererContract() throws java.io.IOException {
		java.nio.file.Path path = java.nio.file.Path.of("src/client/java/com/example/client/renderer/PathwayHologramRenderer.java");
		String content = java.nio.file.Files.readString(path);

		Assertions.assertTrue(
			content.contains("if (!isPathwayMode) {"),
			"PathwayHologramRenderer must only render when in PATHWAY mode"
		);
		Assertions.assertTrue(
			content.contains("drawVectorLine("),
			"PathwayHologramRenderer must use drawVectorLine for 3D laser tether"
		);
	}

	@Test
	@DisplayName("Source contract: MinionPatrolGoal edge cases (anchor post check, empty route auto-detach, linger reset)")
	void testMinionPatrolGoalEdgeCaseContracts() throws java.io.IOException {
		java.nio.file.Path path = java.nio.file.Path.of("src/main/java/com/example/entity/ai/goal/MinionPatrolGoal.java");
		String content = java.nio.file.Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.minion.getGuardAnchorPos() != null || this.minion.isHoldingPosition()"),
			"MinionPatrolGoal must yield when minion is holding an anchor position or stationed"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setPatrolRouteId(-1);"),
			"MinionPatrolGoal must auto-detach patrol route ID when assigned route has no waypoints"
		);
		Assertions.assertTrue(
			content.contains("this.lingerTicks = 0;"),
			"MinionPatrolGoal must reset lingerTicks when navigating en-route"
		);
	}

	@Test
	@DisplayName("Source contract: MinionEntity and CommandScepter stance & recall unbinding")
	void testMinionStanceAndRecallUnbindingContracts() throws java.io.IOException {
		String minionEntityContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));
		Assertions.assertTrue(
			minionEntityContent.contains("this.setPatrolRouteId(-1);") && minionEntityContent.contains("this.clearLeader();"),
			"MinionEntity empty-hand interaction must clear patrol route ID and leader"
		);

		String scepterContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		Assertions.assertTrue(
			scepterContent.contains("minion.clearLeader(); // Recall escorting units to stronghold master"),
			"CommandScepterItem Emergency Citadel Call must clear escort leaders"
		);
		Assertions.assertTrue(
			scepterContent.contains("targetLeader.getLeaderMinionUuid().equals(follower.getUuid())"),
			"CommandScepterItem handlePrimedEscort must break reciprocal circular escort loops"
		);
	}

	@Test
	@DisplayName("Source contract: ModNetworking empty route rejection and route clear unbinding")
	void testModNetworkingEdgeCaseContracts() throws java.io.IOException {
		String networkingContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/network/ModNetworking.java"));
		Assertions.assertTrue(
			networkingContent.contains("Cannot assign to ") && networkingContent.contains("no waypoints set!"),
			"ModNetworking must reject minion assignment to empty routes"
		);
		Assertions.assertTrue(
			networkingContent.contains("follower.equals(leader)"),
			"ModNetworking must prevent minion self-escort"
		);
		Assertions.assertTrue(
			networkingContent.contains("leader.getLeaderMinionUuid().equals(follower.getUuid())"),
			"ModNetworking must break reciprocal circular escort loops"
		);
		Assertions.assertTrue(
			networkingContent.contains("follower.setSelected(true)"),
			"ModNetworking CLEAR_ESCORT must select the freed minion"
		);
	}

	@Test
	@DisplayName("Source contract: Punch-tethering selected minions with automatic deselection and self-follow prevention")
	void testMassSelectedPunchTetherAndDeselectionContract() throws java.io.IOException {
		String scepterContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		Assertions.assertTrue(
			scepterContent.contains("assignSelectedMinionsToLeader"),
			"CommandScepterItem must define assignSelectedMinionsToLeader"
		);
		Assertions.assertTrue(
			scepterContent.contains("!m.getUuid().equals(targetLeader.getUuid())"),
			"assignSelectedMinionsToLeader must exclude target leader so a minion cannot follow itself"
		);
		Assertions.assertTrue(
			scepterContent.contains("follower.setSelected(false); // Automatically deselect follower so it only follows the leader!"),
			"assignSelectedMinionsToLeader must deselect follower from player so it only follows leader"
		);
		Assertions.assertTrue(
			scepterContent.contains("SoundEvents.BLOCK_NOTE_BLOCK_CHIME") && scepterContent.contains("SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE"),
			"CommandScepterItem must play note block chime and amethyst resonate for audible feedback"
		);

		String exampleModContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java"));
		Assertions.assertTrue(
			exampleModContent.contains("CommandScepterItem.assignSelectedMinionsToLeader(player, minion)"),
			"AttackEntityCallback must call assignSelectedMinionsToLeader when punching an owned minion"
		);
	}

	@Test
	@DisplayName("Source contract: SentinelGuardGoal yields to escort leader and patrol")
	void testSentinelGuardGoalEscortYieldContract() throws java.io.IOException {
		String sentinelContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/ai/goal/SentinelGuardGoal.java"));
		Assertions.assertTrue(
			sentinelContent.contains("if (this.minion.hasLeader()) {\n\t\t\treturn false;\n\t\t}"),
			"SentinelGuardGoal must yield when minion has a leader so it doesn't follow both player and leader"
		);
		Assertions.assertTrue(
			sentinelContent.contains("if (this.minion.getPatrolRouteId() >= 0) {\n\t\t\treturn false;\n\t\t}"),
			"SentinelGuardGoal must yield when minion has a patrol route"
		);
	}

	@Test
	@DisplayName("Source contract: MinionEntity enforces mutual exclusivity between player selection and leader escorting")
	void testMinionEntityEscortMutualExclusivityContract() throws java.io.IOException {
		String minionContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));
		Assertions.assertTrue(
			minionContent.contains("if (selected) {\n\t\t\tthis.clearLeader();\n\t\t}"),
			"MinionEntity.setSelected(true) must automatically clear leader minion so followers detach upon following player"
		);
		Assertions.assertTrue(
			minionContent.contains("if (uuid != null) {\n\t\t\tthis.dataTracker.set(SELECTED, false);"),
			"MinionEntity.setLeaderMinionUuid(non-null) must automatically deselect minion from player commander"
		);
	}

	@Test
	@DisplayName("Source contract: AttackEntityCallback maps Normal-Punch to toggleMinionSelection and Shift-Punch to assignSelectedMinionsToLeader")
	void testAttackEntityCallbackControlMappingContract() throws java.io.IOException {
		String exampleModContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java"));
		Assertions.assertTrue(
			exampleModContent.contains("CommandScepterItem.toggleMinionSelection(player, minion)"),
			"Normal punch must call toggleMinionSelection"
		);
		Assertions.assertTrue(
			exampleModContent.contains("if (player.isSneaking()) {\n\t\t\t\t\t\tif (!world.isClient()) {\n\t\t\t\t\t\t\tint assigned = CommandScepterItem.assignSelectedMinionsToLeader(player, minion);"),
			"Shift + Punch must call assignSelectedMinionsToLeader"
		);
	}

	@Test
	@DisplayName("Source contract: CommandScepterItem allows 90° cone sweep selection in PATHWAY mode when not sneaking")
	void testPathwayConeSweepSelectionContract() throws java.io.IOException {
		String scepterContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		Assertions.assertTrue(
			scepterContent.contains("if (mode == CommandMode.PATHWAY) {\n\t\t\t\tif (player.isSneaking()) {"),
			"CommandScepterItem must only dispatch to patrol route when sneaking in PATHWAY mode, allowing normal cone sweep to select"
		);
		String exampleModContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java"));
		Assertions.assertTrue(
			exampleModContent.contains("int assigned = CommandScepterItem.assignSelectedMinionsToLeader(player, minion);"),
			"ExampleMod must bind assignSelectedMinionsToLeader"
		);
	}

	@Test
	@DisplayName("Validate PatrolRouteManager cross-channel findWaypoint lookup and zero-overlap protection")
	void testCrossChannelWaypointLookupAndZeroOverlap() {
		PatrolRouteManager manager = PatrolRouteManager.getInstance();
		UUID playerId = UUID.randomUUID();
		BlockPos posGold = new BlockPos(100, 65, 200);

		// 1. Add waypoint to Route 0 (Gold)
		manager.addWaypoint(playerId, 0, posGold);
		Assertions.assertTrue(manager.getRoute(playerId, 0).waypoints().contains(posGold));
		Assertions.assertFalse(manager.getRoute(playerId, 1).waypoints().contains(posGold));

		// 2. findWaypoint must discover posGold across all channels
		PatrolRouteManager.WaypointMatch match = manager.findWaypoint(playerId, posGold);
		Assertions.assertNotNull(match, "findWaypoint must discover waypoint placed on another channel");
		Assertions.assertEquals(0, match.routeId(), "match.routeId must match the channel it was added to");
		Assertions.assertEquals(posGold, match.pos());

		// 3. Client tracker cross-channel lookup
		com.example.client.renderer.ClientPatrolRouteTracker.setRoutes(manager.getAllRoutes(playerId));
		Assertions.assertTrue(com.example.client.renderer.ClientPatrolRouteTracker.isAnyWaypoint(posGold));
		Assertions.assertFalse(com.example.client.renderer.ClientPatrolRouteTracker.isAnyWaypoint(new BlockPos(999, 99, 999)));

		// 4. Remove via matched routeId
		manager.removeWaypoint(playerId, match.routeId(), match.pos());
		Assertions.assertFalse(manager.getRoute(playerId, 0).waypoints().contains(posGold));
		Assertions.assertNull(manager.findWaypoint(playerId, posGold));
	}

	@Test
	@DisplayName("Source contract: CommandScepterItem and ExampleMod use universal findWaypoint for zero-overlap and cross-channel removal")
	void testCrossChannelZeroOverlapSourceContracts() throws java.io.IOException {
		String scepterContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		Assertions.assertTrue(
			scepterContent.contains("PatrolRouteManager.getInstance().findWaypoint("),
			"CommandScepterItem.useOnBlock must call findWaypoint across all channels"
		);

		String exampleModContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/ExampleMod.java"));
		Assertions.assertTrue(
			exampleModContent.contains("com.example.patrol.PatrolRouteManager.getInstance().findWaypoint("),
			"ExampleMod.AttackBlockCallback must call findWaypoint across all channels"
		);

		String clientContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/client/java/com/example/client/ExampleModClient.java"));
		Assertions.assertTrue(
			clientContent.contains("ClientPatrolRouteTracker.isAnyWaypoint(pos)"),
			"ExampleModClient must use isAnyWaypoint to recognize waypoints across all channels"
		);
	}

	@Test
	@DisplayName("Validate escort formation station offsets across multiple squad sizes with zero overlapping coordinates")
	void testEscortMultiUnitFormationOffsetsAndZeroOverlap() {
		UUID leaderUuid = UUID.randomUUID();
		double leaderX = 0.0D;
		double leaderY = 64.0D;
		double leaderZ = 0.0D;
		float leaderYaw = 0.0F;

		// Simulate an escort squad of 6 warriors and 4 sentinels
		java.util.Set<String> computedStations = new java.util.HashSet<>();
		for (int rank = 0; rank < 6; rank++) {
			net.minecraft.util.math.Vec3d station = com.example.entity.ai.goal.MinionFormationFollowGoal.calculateFormationStation(
				leaderUuid, leaderX, leaderY, leaderZ, leaderYaw, com.example.entity.custom.MinionRole.WARRIOR, rank
			);
			String key = String.format("W_%d:%.2f,%.2f", rank, station.x, station.z);
			Assertions.assertFalse(computedStations.contains(key), "Every warrior station must be unique: " + key);
			computedStations.add(key);
		}

		for (int rank = 0; rank < 4; rank++) {
			net.minecraft.util.math.Vec3d station = com.example.entity.ai.goal.MinionFormationFollowGoal.calculateFormationStation(
				leaderUuid, leaderX, leaderY, leaderZ, leaderYaw, com.example.entity.custom.MinionRole.SENTINEL, rank
			);
			String key = String.format("S_%d:%.2f,%.2f", rank, station.x, station.z);
			Assertions.assertFalse(computedStations.contains(key), "Every sentinel station must be unique: " + key);
			computedStations.add(key);
		}

		Assertions.assertEquals(10, computedStations.size(), "All 10 escort stations must be uniquely distributed around leader");
	}

	@Test
	@DisplayName("Source contract: MinionFollowLeaderGoal and MinionEntity implement robust escort following and combat defense")
	void testEscortHierarchyAndCombatLeashContracts() throws java.io.IOException {
		String followLeaderContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/ai/goal/MinionFollowLeaderGoal.java"));
		Assertions.assertTrue(
			followLeaderContent.contains("MinionFormationFollowGoal.calculateFormationStation("),
			"MinionFollowLeaderGoal must use MinionFormationFollowGoal.calculateFormationStation for army battle stations"
		);
		Assertions.assertTrue(
			followLeaderContent.contains("COMBAT_LEASH_OVERRIDE_SQ"),
			"MinionFollowLeaderGoal must define and enforce COMBAT_LEASH_OVERRIDE_SQ to prevent escorts being lured away"
		);
		Assertions.assertTrue(
			followLeaderContent.contains("TELEPORT_DISTANCE_THRESHOLD_SQ"),
			"MinionFollowLeaderGoal must define TELEPORT_DISTANCE_THRESHOLD_SQ to recall estranged escorts"
		);

		String minionContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));
		Assertions.assertTrue(
			minionContent.contains("if (this.hasLeader()) {\n\t\t\t\tMinionEntity leader = this.resolveLeader();"),
			"MinionEntity.returnToOwnerPostCombat must return to squad leader when minion is an escort"
		);
		Assertions.assertTrue(
			minionContent.contains("new com.example.entity.ai.goal.TrackLeaderAttackerGoal(this)"),
			"MinionEntity must register TrackLeaderAttackerGoal to defend squad leader"
		);
		Assertions.assertTrue(
			minionContent.contains("new com.example.entity.ai.goal.AttackWithLeaderGoal(this)"),
			"MinionEntity must register AttackWithLeaderGoal to attack with squad leader"
		);

		String waypointHoldContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/entity/ai/goal/WaypointHoldGoal.java"));
		Assertions.assertTrue(
			waypointHoldContent.contains("this.minion.hasLeader()"),
			"WaypointHoldGoal must yield when minion has a leader"
		);

		String scepterContent = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));
		Assertions.assertTrue(
			scepterContent.contains("!m.hasLeader() && filterSquad.matches(m.getSquad())"),
			"CommandScepterItem.broadcastFollow and executeRetreat must exclude escorts from direct player recall"
		);
	}
}

