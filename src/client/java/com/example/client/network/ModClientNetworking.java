package com.example.client.network;

import com.example.ExampleMod;
import com.example.client.ExampleModClient;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import com.example.network.DeselectMinionsPayload;
import com.example.network.DismissMinionPayload;
import com.example.network.TeleportMinionPayload;
import com.example.network.UpdateMinionConfigPayload;
import com.example.network.UpdateScepterPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side networking manager for Fabric C2S / S2C payload handling.
 * Manages outgoing scepter updates, directive broadcasts, and verifies networking readiness.
 */
public class ModClientNetworking {

	/**
	 * Initializes and verifies client networking registration.
	 * Invoked during client mod initialization.
	 */
	public static void registerClientNetworking() {
		ExampleModClient.LOGGER.info("Registering Client Networking handlers for {}", ExampleMod.MOD_ID);
		ClientPlayNetworking.registerGlobalReceiver(
			com.example.network.SyncConstructionSessionPayload.ID,
			(payload, context) -> context.client().execute(() -> com.example.client.renderer.ClientConstructionTracker.addSession(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			com.example.network.EndConstructionSessionPayload.ID,
			(payload, context) -> context.client().execute(() -> com.example.client.renderer.ClientConstructionTracker.removeSession(payload.sessionId()))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			com.example.network.SyncPatrolRoutesPayload.ID,
			(payload, context) -> context.client().execute(() -> {
				com.example.client.renderer.ClientPatrolRouteTracker.setRoutes(payload.routes());
				if (context.client().currentScreen instanceof com.example.client.gui.CommandScepterScreen screen) {
					screen.refreshButtonLabels();
				}
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(
			com.example.network.SyncCustomBlueprintsPayload.ID,
			(payload, context) -> context.client().execute(() -> {
				com.example.blueprint.BlueprintRegistry.clearCustomBlueprints();
				if (payload.blueprints() != null) {
					for (com.example.blueprint.StructureBlueprint bp : payload.blueprints()) {
						com.example.blueprint.BlueprintRegistry.registerCustomBlueprint(bp);
					}
				}
				if (context.client().currentScreen instanceof com.example.client.gui.CommandScepterScreen screen) {
					screen.refreshButtonLabels();
				}
			})
		);

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			com.example.client.renderer.ClientPatrolRouteTracker.clear();
			com.example.blueprint.BlueprintRegistry.clearCustomBlueprints();
		});
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} from the client GUI to the server including squad filtering, rotation, target archetype role, and active patrol route channel.
	 *
	 * @param mode              The updated {@link CommandMode}.
	 * @param blueprintId       The active architectural blueprint identifier.
	 * @param targetSquad       The active target {@link SquadGroup} filter channel.
	 * @param rotation          The structure rotation index (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
	 * @param targetRole        Optional target {@link MinionRole} archetype for mass role transformation.
	 * @param activePatrolRoute The active patrol route channel index (0 to CHANNEL_COUNT - 1).
	 * @param executeDirective  True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, Optional<MinionRole> targetRole, int activePatrolRoute, boolean executeDirective) {
		UpdateScepterPayload payload = new UpdateScepterPayload(mode, blueprintId, targetSquad, rotation, targetRole, activePatrolRoute, executeDirective);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} from the client GUI to the server including squad filtering, rotation, and target archetype role.
	 *
	 * @param mode             The updated {@link CommandMode}.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The active target {@link SquadGroup} filter channel.
	 * @param rotation         The structure rotation index (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
	 * @param targetRole       Optional target {@link MinionRole} archetype for mass role transformation.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, Optional<MinionRole> targetRole, boolean executeDirective) {
		sendUpdateScepter(mode, blueprintId, targetSquad, rotation, targetRole, 0, executeDirective);
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} with nullable target archetype role.
	 *
	 * @param mode             The updated {@link CommandMode}.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The active target {@link SquadGroup} filter channel.
	 * @param rotation         The structure rotation index (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
	 * @param targetRole       Nullable target {@link MinionRole} archetype for mass role transformation.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, MinionRole targetRole, boolean executeDirective) {
		sendUpdateScepter(mode, blueprintId, targetSquad, rotation, Optional.ofNullable(targetRole), executeDirective);
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} from the client GUI to the server including squad filtering and rotation.
	 *
	 * @param mode             The updated {@link CommandMode}.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The active target {@link SquadGroup} filter channel.
	 * @param rotation         The structure rotation index (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, boolean executeDirective) {
		UpdateScepterPayload payload = new UpdateScepterPayload(mode, blueprintId, targetSquad, rotation, executeDirective);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} from the client GUI to the server including squad filtering.
	 *
	 * @param mode             The updated {@link CommandMode}.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The active target {@link SquadGroup} filter channel.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, SquadGroup targetSquad, boolean executeDirective) {
		sendUpdateScepter(mode, blueprintId, targetSquad, 0, executeDirective);
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} from the client GUI to the server with default squad wildcard (ALL).
	 *
	 * @param mode             The updated {@link CommandMode}.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, boolean executeDirective) {
		sendUpdateScepter(mode, blueprintId, SquadGroup.ALL, executeDirective);
	}

	/**
	 * Dispatches an {@link UpdateMinionConfigPayload} to update archetype role and squad assignment of a minion.
	 *
	 * @param minionId The entity ID of the target minion.
	 * @param role     The assigned {@link MinionRole}.
	 * @param squad    The assigned {@link SquadGroup}.
	 */
	public static void sendUpdateMinionConfig(int minionId, MinionRole role, SquadGroup squad) {
		UpdateMinionConfigPayload payload = new UpdateMinionConfigPayload(minionId, role, squad);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link DismissMinionPayload} to dismiss a single minion thrall.
	 *
	 * @param minionId The entity ID of the minion to dismiss.
	 */
	public static void sendDismissMinion(int minionId) {
		DismissMinionPayload payload = new DismissMinionPayload(minionId, false);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link DismissMinionPayload} to dismiss all owned minions within the command radius.
	 */
	public static void sendDismissAllMinions() {
		DismissMinionPayload payload = new DismissMinionPayload(DismissMinionPayload.TARGET_ALL, true);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link DismissMinionPayload} to dismiss only selected owned minions within the command radius.
	 */
	public static void sendDismissSelectedMinions() {
		DismissMinionPayload payload = new DismissMinionPayload(DismissMinionPayload.TARGET_SELECTED, false);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link TeleportMinionPayload} to teleport a single minion thrall to the player.
	 *
	 * @param minionId The entity ID of the minion to teleport.
	 */
	public static void sendTeleportMinion(int minionId) {
		TeleportMinionPayload payload = new TeleportMinionPayload(minionId, false);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link TeleportMinionPayload} to teleport all owned minions within the command radius to the player.
	 */
	public static void sendTeleportAllMinions() {
		TeleportMinionPayload payload = new TeleportMinionPayload(-1, true);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link DeselectMinionsPayload} to deselect all owned minions within the command radius.
	 */
	public static void sendDeselectAllMinions() {
		DeselectMinionsPayload payload = new DeselectMinionsPayload(-1, true);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.MassRolePayload} to assign an archetype role to all selected minions.
	 *
	 * @param role        The newly assigned archetype role.
	 * @param targetSquad The target squad channel filter.
	 */
	public static void sendMassRole(MinionRole role, SquadGroup targetSquad) {
		com.example.network.MassRolePayload payload = new com.example.network.MassRolePayload(role, targetSquad);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.RetreatPayload} to recall active minions to formation.
	/**
	 * Dispatches a {@link com.example.network.RetreatPayload} to recall active minions or trigger emergency citadel recall.
	 *
	 * @param targetSquad            The target squad channel filter.
	 * @param isEmergencyCitadelCall True to recall all units base-wide including patrol sentries.
	 */
	public static void sendRetreat(SquadGroup targetSquad, boolean isEmergencyCitadelCall) {
		com.example.network.RetreatPayload payload = new com.example.network.RetreatPayload(
			targetSquad != null ? targetSquad : SquadGroup.ALL,
			isEmergencyCitadelCall
		);
		ClientPlayNetworking.send(payload);
	}

	public static void sendRetreat(SquadGroup targetSquad) {
		sendRetreat(targetSquad, false);
	}

	/**
	 * Dispatches a {@link com.example.network.RetreatPayload} to recall all active minions to formation.
	 */
	public static void sendRetreat() {
		sendRetreat(SquadGroup.ALL);
	}

	/**
	 * Dispatches an {@link com.example.network.AnchorConstructionPayload} to anchor a construction or dismantle session.
	 *
	 * @param clickedPos  The targeted block position.
	 * @param side        The targeted block face side.
	 * @param isDismantle True if this should start a dismantle session.
	 */
	public static void sendAnchorConstruction(net.minecraft.util.math.BlockPos clickedPos, net.minecraft.util.math.Direction side, boolean isDismantle) {
		com.example.network.AnchorConstructionPayload payload = new com.example.network.AnchorConstructionPayload(clickedPos, side, isDismantle);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.ModifyPatrolRoutePayload} to modify in-world patrol routes or minion assignments.
	 */
	public static void sendModifyPatrolRoute(com.example.network.ModifyPatrolRoutePayload.Action action, int routeId, net.minecraft.util.math.BlockPos pos, int minionId, int targetMinionId) {
		com.example.network.ModifyPatrolRoutePayload payload = new com.example.network.ModifyPatrolRoutePayload(
			action,
			routeId,
			pos != null ? pos : net.minecraft.util.math.BlockPos.ORIGIN,
			minionId,
			targetMinionId
		);
		ClientPlayNetworking.send(payload);
	}

	public static void sendTogglePatrolMode(int routeId) {
		sendModifyPatrolRoute(com.example.network.ModifyPatrolRoutePayload.Action.TOGGLE_PATROL_MODE, routeId, net.minecraft.util.math.BlockPos.ORIGIN, -1, -1);
	}

	public static void sendClearPatrolRoute(int routeId) {
		sendModifyPatrolRoute(com.example.network.ModifyPatrolRoutePayload.Action.CLEAR_ROUTE, routeId, net.minecraft.util.math.BlockPos.ORIGIN, -1, -1);
	}

	public static void sendAssignMinionPatrol(int minionId, int routeId) {
		sendModifyPatrolRoute(com.example.network.ModifyPatrolRoutePayload.Action.ASSIGN_MINION, routeId, net.minecraft.util.math.BlockPos.ORIGIN, minionId, -1);
	}

	public static void sendSetEscort(int minionId, int leaderMinionId) {
		sendModifyPatrolRoute(com.example.network.ModifyPatrolRoutePayload.Action.SET_ESCORT, -1, net.minecraft.util.math.BlockPos.ORIGIN, minionId, leaderMinionId);
	}

	public static void sendClearEscort(int minionId) {
		sendModifyPatrolRoute(com.example.network.ModifyPatrolRoutePayload.Action.CLEAR_ESCORT, -1, net.minecraft.util.math.BlockPos.ORIGIN, minionId, -1);
	}

	public static void sendSavePatrolRoute(int routeId, String name, int colorRgb, com.example.patrol.PatrolRoute.PatrolMode mode) {
		com.example.network.ConfigurePatrolRoutePayload payload = new com.example.network.ConfigurePatrolRoutePayload(
			com.example.network.ConfigurePatrolRoutePayload.Action.SAVE,
			routeId,
			name,
			colorRgb,
			mode != null ? mode : com.example.patrol.PatrolRoute.PatrolMode.LOOP
		);
		ClientPlayNetworking.send(payload);
	}

	public static void sendDeletePatrolRoute(int routeId, String name) {
		com.example.network.ConfigurePatrolRoutePayload payload = new com.example.network.ConfigurePatrolRoutePayload(
			com.example.network.ConfigurePatrolRoutePayload.Action.DELETE,
			routeId,
			name != null ? name : "",
			0,
			com.example.patrol.PatrolRoute.PatrolMode.LOOP
		);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.CreateCustomBlueprintPayload} to compile and persist a custom blueprint.
	 *
	 * @param id          Unique blueprint identifier.
	 * @param name        Display name.
	 * @param description Description.
	 * @param blocks      Blueprint blocks.
	 */
	public static void sendCreateCustomBlueprint(String id, String name, String description, java.util.List<com.example.blueprint.BlueprintBlock> blocks) {
		com.example.network.CreateCustomBlueprintPayload payload = new com.example.network.CreateCustomBlueprintPayload(id, name, description, blocks);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.CreateCustomBlueprintPayload} to save a compiled StructureBlueprint.
	 *
	 * @param blueprint The StructureBlueprint to serialize and send.
	 */
	public static void sendCreateCustomBlueprint(com.example.blueprint.StructureBlueprint blueprint) {
		com.example.network.CreateCustomBlueprintPayload payload = new com.example.network.CreateCustomBlueprintPayload(blueprint);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.CaptureSpatialBlueprintPayload} to trigger server-side
	 * in-world spatial capture and persistence.
	 *
	 * @param id          Unique blueprint identifier.
	 * @param name        Display name.
	 * @param description Description.
	 * @param pos1        First corner position.
	 * @param pos2        Second corner position.
	 */
	public static void sendCaptureSpatialBlueprint(String id, String name, String description, net.minecraft.util.math.BlockPos pos1, net.minecraft.util.math.BlockPos pos2) {
		com.example.network.CaptureSpatialBlueprintPayload payload = new com.example.network.CaptureSpatialBlueprintPayload(id, name, description, pos1, pos2);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.DeleteCustomBlueprintPayload} to remove a custom blueprint.
	 *
	 * @param blueprintId Unique blueprint identifier to delete.
	 */
	public static void sendDeleteCustomBlueprint(String blueprintId) {
		com.example.network.DeleteCustomBlueprintPayload payload = new com.example.network.DeleteCustomBlueprintPayload(blueprintId);
		ClientPlayNetworking.send(payload);
	}

	/**
	 * Dispatches a {@link com.example.network.StartMiningAreaPayload} to initiate an area dismantle session on the server.
	 *
	 * @param pos1 First corner position.
	 * @param pos2 Second corner position.
	 */
	public static void sendStartMiningArea(net.minecraft.util.math.BlockPos pos1, net.minecraft.util.math.BlockPos pos2) {
		com.example.network.StartMiningAreaPayload payload = new com.example.network.StartMiningAreaPayload(pos1, pos2);
		ClientPlayNetworking.send(payload);
	}
}