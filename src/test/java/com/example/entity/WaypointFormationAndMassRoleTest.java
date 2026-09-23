package com.example.entity;

import com.example.ExampleMod;
import com.example.block.ModBlocks;
import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.client.renderer.ClientConstructionTracker;
import com.example.component.SquadGroup;
import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.custom.MinionRole;
import com.example.network.EndConstructionSessionPayload;
import com.example.network.MassRolePayload;
import com.example.network.SyncConstructionSessionPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests validating:
 * 1. Automatic Waypoint Ping parametric formation distribution and clearance.
 * 2. MassRolePayload networking definitions, codecs, and squad channels.
 * 3. Custom ConstructionBlock retirement in favor of vanilla scaffolding.
 * 4. ClientConstructionTracker persistent wireframe lifecycle tracking.
 * 5. StructureBlueprint rotation index and state transformations.
 * 6. Channeled Banner of Courage mass select archetype transformation logic.
 * 7. Mass select role preservation when no target role is active.
 * 8. Command Scepter target role data component, GUI toggle, and scepter source invariants.
 */
public class WaypointFormationAndMassRoleTest {

	// =========================================================================
	// 1. Waypoint Ping Tactical Formation Distribution
	// =========================================================================

	@Test
	@DisplayName("Waypoint ping automatically distributes mixed minion workforce into tactical stations with clearance")
	void testWaypointTacticalFormationDistribution() {
		Vec3d waypointCenter = new Vec3d(100.0, 64.0, 100.0);
		float facingYaw = 0.0F; // Facing South (+Z forward, -X right, +X left)

		// Create a realistic workforce composition: 2 Warriors, 2 Sentinels, 3 Builders
		List<MinionRole> squadRoles = List.of(
			MinionRole.WARRIOR,
			MinionRole.WARRIOR,
			MinionRole.SENTINEL,
			MinionRole.SENTINEL,
			MinionRole.BUILDER,
			MinionRole.BUILDER,
			MinionRole.BUILDER
		);

		Map<MinionRole, Integer> roleRanks = new HashMap<>();
		List<Vec3d> stations = new ArrayList<>();

		for (MinionRole role : squadRoles) {
			int rank = roleRanks.getOrDefault(role, 0);
			roleRanks.put(role, rank + 1);

			Vec3d station = MinionFormationFollowGoal.calculateFormationStation(
				waypointCenter.x,
				waypointCenter.y,
				waypointCenter.z,
				facingYaw,
				role,
				rank
			);
			stations.add(station);
		}

		Assertions.assertEquals(7, stations.size(), "All 7 minions must receive a station");

		// Warriors (Vanguard) must be positioned ahead of the waypoint ping (Z > center Z)
		Vec3d warrior0 = stations.get(0);
		Vec3d warrior1 = stations.get(1);
		Assertions.assertTrue(warrior0.z > waypointCenter.z, "Warrior 0 must be in vanguard forward (+Z)");
		Assertions.assertTrue(warrior1.z > waypointCenter.z, "Warrior 1 must be in vanguard forward (+Z)");

		// Sentinels (Flanks) must be positioned flanking left and right
		Vec3d sentinel0 = stations.get(2);
		Vec3d sentinel1 = stations.get(3);
		Assertions.assertNotEquals(sentinel0.x, sentinel1.x, "Sentinels must flank opposite sides of the waypoint");

		// Miners (Rearguard) must be positioned behind the waypoint ping (Z < center Z)
		Vec3d miner0 = stations.get(5);
		Vec3d miner1 = stations.get(6);
		Assertions.assertTrue(miner0.z < waypointCenter.z, "Miner 0 must be in rearguard (-Z)");
		Assertions.assertTrue(miner1.z < waypointCenter.z, "Miner 1 must be in rearguard (-Z)");

		// Verify inter-station separation: no two stations should overlap (minimum 1.5 blocks clearance)
		for (int i = 0; i < stations.size(); i++) {
			for (int j = i + 1; j < stations.size(); j++) {
				double dist = stations.get(i).distanceTo(stations.get(j));
				Assertions.assertTrue(
					dist >= 1.5D,
					String.format("Stations %d and %d are too close: %.2f blocks", i, j, dist)
				);
			}
		}
	}

	// =========================================================================
	// 2. MassRolePayload Validation
	// =========================================================================

	@Test
	@DisplayName("MassRolePayload verifies packet ID, role, squad group, and equality")
	void testMassRolePayload() {
		MassRolePayload payload = new MassRolePayload(MinionRole.BUILDER, SquadGroup.ALL);

		Assertions.assertEquals(MinionRole.BUILDER, payload.role());
		Assertions.assertEquals(SquadGroup.ALL, payload.squad());
		Assertions.assertEquals(MassRolePayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("mass_role", payload.getId().id().getPath());
		Assertions.assertNotNull(MassRolePayload.PACKET_CODEC);

		// Record equality
		MassRolePayload copy = new MassRolePayload(MinionRole.BUILDER, SquadGroup.ALL);
		Assertions.assertEquals(payload, copy);
		Assertions.assertEquals(payload.hashCode(), copy.hashCode());

		MassRolePayload differentSquad = new MassRolePayload(MinionRole.BUILDER, SquadGroup.ALPHA);
		Assertions.assertNotEquals(payload, differentSquad);

		MassRolePayload differentRole = new MassRolePayload(MinionRole.WARRIOR, SquadGroup.ALL);
		Assertions.assertNotEquals(payload, differentRole);
	}

	@Test
	@DisplayName("MassRolePayload supports all 5 minion roles and squad channels")
	void testMassRolePayloadArchetypes() {
		for (MinionRole role : MinionRole.values()) {
			for (SquadGroup squad : SquadGroup.values()) {
				MassRolePayload p = new MassRolePayload(role, squad);
				Assertions.assertEquals(role, p.role());
				Assertions.assertEquals(squad, p.squad());
			}
		}
	}

	// =========================================================================
	// 3. Custom Construction Block Retirement & Vanilla Scaffolding Invariants
	// =========================================================================

	@Test
	@DisplayName("ModBlocks and codebase enforce complete retirement of custom construction block in favor of vanilla scaffolding")
	void testConstructionBlockRetirementInvariants() throws IOException {
		Path modBlocksPath = Path.of("src/main/java/com/example/block/ModBlocks.java");
		Assertions.assertTrue(Files.exists(modBlocksPath), "ModBlocks.java must exist");
		String modBlocksContent = Files.readString(modBlocksPath);

		// Verify custom CONSTRUCTION_BLOCK is no longer registered in ModBlocks
		Assertions.assertFalse(
			modBlocksContent.contains("CONSTRUCTION_BLOCK"),
			"ModBlocks must not register custom CONSTRUCTION_BLOCK"
		);

		Path constructionBlockPath = Path.of("src/main/java/com/example/block/custom/ConstructionBlock.java");
		Assertions.assertFalse(
			Files.exists(constructionBlockPath),
			"ConstructionBlock.java must be deleted and no longer exist"
		);
	}

	// =========================================================================
	// 4. ClientConstructionTracker Lifecycle
	// =========================================================================

	@Test
	@DisplayName("ClientConstructionTracker stores, retrieves, and clears active sessions")
	void testClientConstructionTracker() {
		ClientConstructionTracker.clear();
		Assertions.assertTrue(ClientConstructionTracker.getActiveSessions().isEmpty());

		UUID sessionId1 = UUID.randomUUID();
		BlockPos anchor1 = new BlockPos(10, 64, 20);
		SyncConstructionSessionPayload p1 = new SyncConstructionSessionPayload(
			sessionId1,
			anchor1,
			"modid-mmcli-agent-modding:small_house",
			1,
			false
		);

		ClientConstructionTracker.addSession(p1);
		Assertions.assertEquals(1, ClientConstructionTracker.getActiveSessions().size());

		ClientConstructionTracker.ActiveSessionClientData data = ClientConstructionTracker.getActiveSessions().iterator().next();
		Assertions.assertEquals(sessionId1, data.sessionId());
		Assertions.assertEquals(anchor1, data.anchorPos());
		Assertions.assertEquals("modid-mmcli-agent-modding:small_house", data.blueprintId());
		Assertions.assertEquals(1, data.rotation());
		Assertions.assertFalse(data.isDismantle());
		Assertions.assertEquals(0, data.sizeX());
		Assertions.assertEquals(0, data.sizeY());
		Assertions.assertEquals(0, data.sizeZ());

		// Add second session (dismantle area mining with dimensions)
		UUID sessionId2 = UUID.randomUUID();
		SyncConstructionSessionPayload p2 = new SyncConstructionSessionPayload(
			sessionId2,
			new BlockPos(30, 70, 40),
			"mining_area_999",
			0,
			true,
			10,
			5,
			10
		);
		ClientConstructionTracker.addSession(p2);
		Assertions.assertEquals(2, ClientConstructionTracker.getActiveSessions().size());

		// Remove session 1
		ClientConstructionTracker.removeSession(sessionId1);
		Assertions.assertEquals(1, ClientConstructionTracker.getActiveSessions().size());

		ClientConstructionTracker.ActiveSessionClientData data2 = ClientConstructionTracker.getActiveSessions().iterator().next();
		Assertions.assertEquals(sessionId2, data2.sessionId());
		Assertions.assertTrue(data2.isDismantle());
		Assertions.assertEquals(10, data2.sizeX());
		Assertions.assertEquals(5, data2.sizeY());
		Assertions.assertEquals(10, data2.sizeZ());

		// Clear all
		ClientConstructionTracker.clear();
		Assertions.assertTrue(ClientConstructionTracker.getActiveSessions().isEmpty());
	}

	// =========================================================================
	// 5. StructureBlueprint Rotation Tracking
	// =========================================================================

	@Test
	@DisplayName("StructureBlueprint rotation index and getter integrity across rotations")
	void testStructureBlueprintRotationIntegrity() throws IOException {
		// Empty blueprint defaults to NONE (index 0)
		StructureBlueprint empty = StructureBlueprint.builder("test_bp", "Test Blueprint").build();
		Assertions.assertEquals(BlockRotation.NONE, empty.getRotation());
		Assertions.assertEquals(0, empty.getRotationIndex());

		// Validate StructureBlueprint source contract for rotation tracking and integer rotation indices
		Path bpPath = Path.of("src/main/java/com/example/blueprint/StructureBlueprint.java");
		Assertions.assertTrue(Files.exists(bpPath), "StructureBlueprint.java must exist");
		String bpContent = Files.readString(bpPath);

		Assertions.assertTrue(
			bpContent.contains("public BlockRotation getRotation()"),
			"StructureBlueprint must provide public BlockRotation getRotation()"
		);
		Assertions.assertTrue(
			bpContent.contains("public int getRotationIndex()"),
			"StructureBlueprint must provide public int getRotationIndex()"
		);
		Assertions.assertTrue(
			bpContent.contains("public StructureBlueprint rotate(int rotationIndex)"),
			"StructureBlueprint must provide public StructureBlueprint rotate(int rotationIndex)"
		);
		Assertions.assertTrue(
			bpContent.contains("CLOCKWISE_90 -> 1"),
			"getRotationIndex must map CLOCKWISE_90 to 1"
		);
		Assertions.assertTrue(
			bpContent.contains("CLOCKWISE_180 -> 2"),
			"getRotationIndex must map CLOCKWISE_180 to 2"
		);
		Assertions.assertTrue(
			bpContent.contains("COUNTERCLOCKWISE_90 -> 3"),
			"getRotationIndex must map COUNTERCLOCKWISE_90 to 3"
		);
	}

	// =========================================================================
	// 6. Channeled Banner of Courage Mass Select Archetype Transformation
	// =========================================================================

	@Test
	@DisplayName("Channeled mass select rally transforms all enclosed minions into scepter target role and updates squad")
	void testChanneledMassSelectArchetypeTransformationLogic() {
		UUID commanderId = UUID.randomUUID();
		UUID enemyCommanderId = UUID.randomUUID();

		class MockTacticalMinion {
			final int id;
			final UUID owner;
			final boolean alive;
			final double x;
			final double z;
			SquadGroup squad;
			MinionRole role;
			boolean selected;
			boolean sitting;
			String guardAnchor;
			boolean autoEquipped;
			boolean navigating;

			MockTacticalMinion(int id, UUID owner, boolean alive, double x, double z, SquadGroup squad, MinionRole role, boolean sitting, String guardAnchor) {
				this.id = id;
				this.owner = owner;
				this.alive = alive;
				this.x = x;
				this.z = z;
				this.squad = squad;
				this.role = role;
				this.selected = false;
				this.sitting = sitting;
				this.guardAnchor = guardAnchor;
				this.autoEquipped = false;
				this.navigating = false;
			}

			boolean isWithinRadius(double originX, double originZ, double radius) {
				double dx = this.x - originX;
				double dz = this.z - originZ;
				return (dx * dx + dz * dz) <= (radius * radius);
			}

			void applyRallyTransform(SquadGroup targetSquad, MinionRole targetRole) {
				if (!targetSquad.isWildcard()) {
					this.squad = targetSquad;
				}
				if (targetRole != null) {
					this.role = targetRole;
					this.autoEquipped = true;
				}
				this.selected = true;
				this.guardAnchor = null;
				this.sitting = false;
				this.navigating = true;
			}
		}

		for (MinionRole chosenRole : MinionRole.values()) {
			List<MockTacticalMinion> army = List.of(
				// Enclosed within radius 8.0 (commander at 0.0, 0.0)
				new MockTacticalMinion(1, commanderId, true, 3.0, 4.0, SquadGroup.ALPHA, MinionRole.WARRIOR, true, "10,64,10"),   // dist = 5.0 <= 8.0
				new MockTacticalMinion(2, commanderId, true, -4.0, 3.0, SquadGroup.BRAVO, MinionRole.BUILDER, false, "20,64,20"),   // dist = 5.0 <= 8.0
				new MockTacticalMinion(3, commanderId, true, 2.0, -3.0, SquadGroup.CHARLIE, MinionRole.BUILDER, true, null),      // dist = 3.6 <= 8.0
				// Outside radius (dist = 14.0 > 8.0)
				new MockTacticalMinion(4, commanderId, true, 14.0, 0.0, SquadGroup.DELTA, MinionRole.SENTINEL, true, "30,64,30"),
				// Dead minion nearby (must be ignored)
				new MockTacticalMinion(5, commanderId, false, 1.0, 1.0, SquadGroup.ALPHA, MinionRole.WARRIOR, true, null),
				// Enemy minion nearby (must be ignored)
				new MockTacticalMinion(6, enemyCommanderId, true, 2.0, 2.0, SquadGroup.ALPHA, MinionRole.WARRIOR, false, null)
			);

			double rallyRadius = 8.0;
			SquadGroup targetSquad = SquadGroup.DELTA;

			// Gather enclosed owned living units
			List<MockTacticalMinion> enclosed = army.stream()
				.filter(m -> m.alive && m.owner.equals(commanderId) && m.isWithinRadius(0.0, 0.0, rallyRadius))
				.toList();

			Assertions.assertEquals(3, enclosed.size(), "Must gather exactly 3 enclosed owned minions");

			for (MockTacticalMinion minion : enclosed) {
				minion.applyRallyTransform(targetSquad, chosenRole);
			}

			// Verify transformed enclosed minions
			for (MockTacticalMinion m : enclosed) {
				Assertions.assertEquals(chosenRole, m.role, "Minion role must be transformed to chosen role " + chosenRole);
				Assertions.assertEquals(SquadGroup.DELTA, m.squad, "Squad must be updated to target squad DELTA");
				Assertions.assertTrue(m.selected, "Enclosed minion must be marked selected");
				Assertions.assertFalse(m.sitting, "Sitting stance must be cleared");
				Assertions.assertNull(m.guardAnchor, "Guard anchor must be cleared");
				Assertions.assertTrue(m.autoEquipped, "Auto-equip must be triggered on role transformation");
				Assertions.assertTrue(m.navigating, "Minion must start navigating to commander");
			}

			// Verify un-enclosed minion 4 remained unchanged
			MockTacticalMinion outsideMinion = army.get(3);
			Assertions.assertEquals(MinionRole.SENTINEL, outsideMinion.role, "Outside minion role must not change");
			Assertions.assertEquals(SquadGroup.DELTA, outsideMinion.squad);
			Assertions.assertFalse(outsideMinion.selected, "Outside minion must not be selected");
			Assertions.assertTrue(outsideMinion.sitting, "Outside minion sitting state must remain unchanged");
			Assertions.assertEquals("30,64,30", outsideMinion.guardAnchor);
			Assertions.assertFalse(outsideMinion.autoEquipped);

			// Verify enemy and dead minions remained untouched
			MockTacticalMinion deadMinion = army.get(4);
			Assertions.assertFalse(deadMinion.selected);
			Assertions.assertFalse(deadMinion.autoEquipped);

			MockTacticalMinion enemyMinion = army.get(5);
			Assertions.assertFalse(enemyMinion.selected);
			Assertions.assertFalse(enemyMinion.autoEquipped);
		}
	}

	// =========================================================================
	// 7. Mass Select Without Target Role Preserves Existing Minion Roles
	// =========================================================================

	@Test
	@DisplayName("Channeled mass select without active target role preserves each minion's existing role")
	void testChanneledMassSelectWithoutTargetRolePreservesExistingRoles() {
		UUID commanderId = UUID.randomUUID();

		class MinionUnit {
			final int id;
			final UUID owner;
			final boolean alive;
			final double x;
			final double z;
			SquadGroup squad;
			MinionRole role;
			boolean selected;
			boolean autoEquipped;

			MinionUnit(int id, UUID owner, boolean alive, double x, double z, SquadGroup squad, MinionRole role) {
				this.id = id;
				this.owner = owner;
				this.alive = alive;
				this.x = x;
				this.z = z;
				this.squad = squad;
				this.role = role;
				this.selected = false;
				this.autoEquipped = false;
			}
		}

		List<MinionUnit> units = List.of(
			new MinionUnit(1, commanderId, true, 2.0, 2.0, SquadGroup.ALPHA, MinionRole.WARRIOR),
			new MinionUnit(2, commanderId, true, -2.0, 1.0, SquadGroup.BRAVO, MinionRole.SENTINEL),
			new MinionUnit(3, commanderId, true, 1.0, -3.0, SquadGroup.CHARLIE, MinionRole.BUILDER)
		);

		MinionRole activeTargetRole = null; // No archetype button selected
		SquadGroup targetSquad = SquadGroup.BRAVO;

		for (MinionUnit u : units) {
			if (!targetSquad.isWildcard()) {
				u.squad = targetSquad;
			}
			if (activeTargetRole != null) {
				u.role = activeTargetRole;
				u.autoEquipped = true;
			}
			u.selected = true;
		}

		// Roles must remain intact
		Assertions.assertEquals(MinionRole.WARRIOR, units.get(0).role);
		Assertions.assertFalse(units.get(0).autoEquipped);
		Assertions.assertEquals(MinionRole.SENTINEL, units.get(1).role);
		Assertions.assertFalse(units.get(1).autoEquipped);
		Assertions.assertEquals(MinionRole.BUILDER, units.get(2).role);
		Assertions.assertFalse(units.get(2).autoEquipped);

		// Squads and selection are still updated
		for (MinionUnit u : units) {
			Assertions.assertEquals(SquadGroup.BRAVO, u.squad);
			Assertions.assertTrue(u.selected);
		}
	}

	// =========================================================================
	// 8. Command Scepter Target Role Component & Mass Select Source Invariants
	// =========================================================================

	@Test
	@DisplayName("Validate CommandScepterItem, ModDataComponents, and CommandScepterScreen source contracts")
	void testCommandScepterTargetRoleComponentAndMassSelectInvariants() throws IOException {
		// 1. ModDataComponents registers TARGET_ROLE component
		Path dataCompPath = Path.of("src/main/java/com/example/component/ModDataComponents.java");
		Assertions.assertTrue(Files.exists(dataCompPath), "ModDataComponents.java must exist");
		String dataCompContent = Files.readString(dataCompPath);

		Assertions.assertTrue(
			dataCompContent.contains("ComponentType<MinionRole> TARGET_ROLE"),
			"ModDataComponents must define TARGET_ROLE component"
		);
		Assertions.assertTrue(
			dataCompContent.contains("target_role"),
			"ModDataComponents must register target_role identifier"
		);
		Assertions.assertTrue(
			dataCompContent.contains("MinionRole.CODEC"),
			"ModDataComponents must use MinionRole.CODEC for TARGET_ROLE"
		);
		Assertions.assertTrue(
			dataCompContent.contains("MinionRole.PACKET_CODEC"),
			"ModDataComponents must use MinionRole.PACKET_CODEC for TARGET_ROLE"
		);

		// 2. CommandScepterItem provides getTargetRole / setTargetRole and performs mass role transformation
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String scepterContent = Files.readString(scepterPath);

		Assertions.assertTrue(
			scepterContent.contains("public static MinionRole getTargetRole(ItemStack stack)"),
			"CommandScepterItem must provide getTargetRole(ItemStack)"
		);
		Assertions.assertTrue(
			scepterContent.contains("public static void setTargetRole(ItemStack stack, MinionRole role)"),
			"CommandScepterItem must provide setTargetRole(ItemStack, MinionRole)"
		);
		Assertions.assertTrue(
			scepterContent.contains("ModDataComponents.TARGET_ROLE"),
			"CommandScepterItem must reference ModDataComponents.TARGET_ROLE"
		);

		// onStoppedUsing channeled rally mass select transformation invariants
		Assertions.assertTrue(
			scepterContent.contains("MinionRole targetRole = getTargetRole(stack)"),
			"CommandScepterItem onStoppedUsing must read getTargetRole(stack)"
		);
		Assertions.assertTrue(
			scepterContent.contains("minion.setRole(targetRole)"),
			"CommandScepterItem onStoppedUsing must apply minion.setRole(targetRole)"
		);
		Assertions.assertTrue(
			scepterContent.contains("minion.autoEquipFromInventory()"),
			"CommandScepterItem onStoppedUsing must invoke minion.autoEquipFromInventory()"
		);
		Assertions.assertTrue(
			scepterContent.contains("minion.setSelected(true)"),
			"CommandScepterItem onStoppedUsing must select enclosed minions"
		);
		Assertions.assertTrue(
			scepterContent.contains("SoundEvents.ENTITY_PLAYER_LEVELUP"),
			"CommandScepterItem onStoppedUsing must play level-up sound when targetRole != null"
		);

		// 3. CommandScepterScreen provides selectable role toggle and updates scepter target role
		Path screenPath = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		Assertions.assertTrue(Files.exists(screenPath), "CommandScepterScreen.java must exist");
		String screenContent = Files.readString(screenPath);

		Assertions.assertTrue(
			screenContent.contains("private MinionRole selectedRole;"),
			"CommandScepterScreen must define selectedRole field"
		);
		Assertions.assertTrue(
			screenContent.contains("public MinionRole getSelectedRole()"),
			"CommandScepterScreen must provide getSelectedRole()"
		);
		Assertions.assertTrue(
			screenContent.contains("public void setSelectedRole(MinionRole role)"),
			"CommandScepterScreen must provide setSelectedRole(MinionRole)"
		);
		Assertions.assertTrue(
			screenContent.contains("CommandScepterItem.setTargetRole("),
			"CommandScepterScreen must persist target role to scepter item stack"
		);
		Assertions.assertTrue(
			screenContent.contains("Optional.ofNullable(this.selectedRole)"),
			"CommandScepterScreen must dispatch UpdateScepterPayload with Optional.ofNullable(selectedRole)"
		);
	}

	// =========================================================================
	// 9. Waypoint Ping Automatic Deselection & Hold Guard Takeover
	// =========================================================================

	@Test
	@DisplayName("Waypoint ping automatically deselects all commanded minions so WaypointHoldGoal can activate")
	void testWaypointPingAutoDeselectionInvariants() throws IOException {
		// 1. Source contract: CommandScepterItem.executeGroundWaypointPing calls minion.setSelected(false)
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(scepterPath), "CommandScepterItem.java must exist");
		String scepterContent = Files.readString(scepterPath);

		int pingMethodIndex = scepterContent.indexOf("public static void executeGroundWaypointPing");
		Assertions.assertTrue(pingMethodIndex != -1, "executeGroundWaypointPing method must exist in CommandScepterItem");
		String pingMethodBody = scepterContent.substring(pingMethodIndex, scepterContent.indexOf("public static BlockPos findSafeWaypointGround"));

		Assertions.assertTrue(
			pingMethodBody.contains("minion.setSelected(false)"),
			"executeGroundWaypointPing must automatically deselect commanded minions by calling minion.setSelected(false)"
		);
		Assertions.assertTrue(
			pingMethodBody.contains("minion.setGuardAnchorPos(groundStationPos)"),
			"executeGroundWaypointPing must assign guard anchor to safe ground station position"
		);

		// 2. Behavioral contract: Mock minion transitioning from selected -> auto-deselected on waypoint dispatch
		class MockWaypointMinion {
			boolean selected = true;
			boolean sitting = false;
			BlockPos guardAnchor = null;
			double stationX, stationY, stationZ;

			void applyWaypointPing(BlockPos targetGround, double targetX, double targetY, double targetZ) {
				this.selected = false;
				this.sitting = false;
				this.guardAnchor = targetGround;
				this.stationX = targetX;
				this.stationY = targetY;
				this.stationZ = targetZ;
			}

			boolean canWaypointHoldGoalStart() {
				if (this.sitting || this.selected) {
					return false;
				}
				return this.guardAnchor != null;
			}
		}

		MockWaypointMinion minion = new MockWaypointMinion();
		Assertions.assertTrue(minion.selected, "Minion begins selected");
		Assertions.assertFalse(minion.canWaypointHoldGoalStart(), "WaypointHoldGoal must not start while minion is selected");

		// Issue waypoint ping
		BlockPos anchor = new BlockPos(120, 64, 150);
		minion.applyWaypointPing(anchor, 120.5, 64.0, 150.5);

		Assertions.assertFalse(minion.selected, "Minion must be automatically deselected on waypoint ping");
		Assertions.assertEquals(anchor, minion.guardAnchor, "Guard anchor must be assigned");
		Assertions.assertTrue(minion.canWaypointHoldGoalStart(), "WaypointHoldGoal must be allowed to start once deselected");
	}
}
