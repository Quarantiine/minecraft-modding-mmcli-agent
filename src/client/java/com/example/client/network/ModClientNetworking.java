package com.example.client.network;

import com.example.ExampleMod;
import com.example.client.ExampleModClient;
import com.example.component.CommandMode;
import com.example.network.DismissMinionPayload;
import com.example.network.TeleportMinionPayload;
import com.example.network.UpdateScepterPayload;
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
	}

	/**
	 * Dispatches an {@link UpdateScepterPayload} from the client GUI to the server.
	 *
	 * @param mode             The updated {@link CommandMode}.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public static void sendUpdateScepter(CommandMode mode, String blueprintId, boolean executeDirective) {
		UpdateScepterPayload payload = new UpdateScepterPayload(mode, blueprintId, executeDirective);
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
}