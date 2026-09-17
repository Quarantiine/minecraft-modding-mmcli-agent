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
		UpdateScepterPayload payload = new UpdateScepterPayload(mode, blueprintId, targetSquad, rotation, targetRole, executeDirective);
		ClientPlayNetworking.send(payload);
	}

	public static void sendUpdateScepter(
		CommandMode mode,
		String blueprintId,
		SquadGroup targetSquad,
		int rotation,
		Optional<MinionRole> targetRole,
		boolean executeDirective,
		com.example.blueprint.ArchitectureStyle style,
		int buildingSize
	) {
		UpdateScepterPayload payload = new UpdateScepterPayload(
			mode,
			blueprintId,
			targetSquad,
			rotation,
			targetRole,
			executeDirective,
			style,
			buildingSize
		);
		ClientPlayNetworking.send(payload);
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
		DismissMinionPayload payload = new DismissMinionPayload(-1, true);
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
	 *
	 * @param targetSquad The target squad channel filter.
	 */
	public static void sendRetreat(SquadGroup targetSquad) {
		com.example.network.RetreatPayload payload = new com.example.network.RetreatPayload(targetSquad != null ? targetSquad : SquadGroup.ALL);
		ClientPlayNetworking.send(payload);
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
}