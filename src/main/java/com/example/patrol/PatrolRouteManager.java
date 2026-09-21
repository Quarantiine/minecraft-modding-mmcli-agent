package com.example.patrol;

import com.example.network.SyncPatrolRoutesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Server-side singleton manager maintaining all designated {@link PatrolRoute}s
 * per player commander. Handles route querying, modifications, and synchronization
 * to clients via network payloads.
 */
public class PatrolRouteManager {

	private static final PatrolRouteManager INSTANCE = new PatrolRouteManager();

	private final Map<UUID, Map<Integer, PatrolRoute>> playerRoutes = new ConcurrentHashMap<>();
	private PatrolRoutePersistentState persistentState = null;
	private net.minecraft.world.PersistentStateManager stateManager = null;

	private PatrolRouteManager() {}

	public static PatrolRouteManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Initializes the manager with the world's PersistentStateManager on server startup/load.
	 *
	 * @param overworld The server overworld instance.
	 */
	public void init(net.minecraft.server.world.ServerWorld overworld) {
		if (overworld == null) return;
		this.stateManager = overworld.getPersistentStateManager();
		this.persistentState = this.stateManager.getOrCreate(
			PatrolRoutePersistentState.TYPE,
			PatrolRoutePersistentState.KEY
		);
		loadFromPersistentState(this.persistentState);
	}

	/**
	 * Loads routes directly from a persistent state instance.
	 */
	public void loadFromPersistentState(PatrolRoutePersistentState state) {
		this.persistentState = state;
		this.playerRoutes.clear();
		if (state != null) {
			for (Map.Entry<UUID, Map<Integer, PatrolRoute>> entry : state.getRoutes().entrySet()) {
				this.playerRoutes.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
			}
		}
	}

	/**
	 * Called during server shutdown to finalize and save persistent route data.
	 */
	public void onServerStopping() {
		markDirtyAndPersist();
	}

	private void markDirtyAndPersist() {
		if (this.persistentState != null) {
			this.persistentState.getRoutes().clear();
			for (Map.Entry<UUID, Map<Integer, PatrolRoute>> entry : this.playerRoutes.entrySet()) {
				this.persistentState.getRoutes().put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
			}
			this.persistentState.markDirty();
			if (this.stateManager != null) {
				try {
					this.stateManager.save();
				} catch (Exception e) {
					System.err.println("[PatrolRouteManager] Failed to flush persistent state to disk: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Retrieves the designated patrol route for the given player and channel ID (0 to 4).
	 * If no route exists yet, a default empty route is created and registered.
	 *
	 * @param playerUuid The UUID of the player commander.
	 * @param routeId    The channel index (0 to 4).
	 * @return The active PatrolRoute.
	 */
	public PatrolRoute getRoute(UUID playerUuid, int routeId) {
		if (playerUuid == null) {
			return PatrolRoute.createDefault(routeId);
		}
		Map<Integer, PatrolRoute> routes = playerRoutes.computeIfAbsent(playerUuid, u -> new ConcurrentHashMap<>());
		return routes.computeIfAbsent(routeId, PatrolRoute::createDefault);
	}

	/**
	 * Retrieves all 5 channel routes for a player commander.
	 *
	 * @param playerUuid The UUID of the player commander.
	 * @return List of 5 PatrolRoutes (Channels 0 to 4).
	 */
	public List<PatrolRoute> getAllRoutes(UUID playerUuid) {
		List<PatrolRoute> list = new ArrayList<>();
		for (int i = 0; i < PatrolRoute.CHANNEL_COUNT; i++) {
			list.add(getRoute(playerUuid, i));
		}
		return list;
	}

	/**
	 * Stores or updates an entire patrol route for a player commander.
	 */
	public void setRoute(UUID playerUuid, PatrolRoute route) {
		if (playerUuid == null || route == null) return;
		Map<Integer, PatrolRoute> routes = playerRoutes.computeIfAbsent(playerUuid, u -> new ConcurrentHashMap<>());
		routes.put(route.routeId(), route);
		markDirtyAndPersist();
	}

	/**
	 * Appends a waypoint block position to the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @param pos        The world block position.
	 * @return The updated PatrolRoute.
	 */
	public PatrolRoute addWaypoint(UUID playerUuid, int routeId, BlockPos pos) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		PatrolRoute updated = current.withAddedWaypoint(pos);
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Removes a waypoint block position from the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @param pos        The world block position.
	 * @return The updated PatrolRoute.
	 */
	public PatrolRoute removeWaypoint(UUID playerUuid, int routeId, BlockPos pos) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		PatrolRoute updated = current.withRemovedWaypoint(pos);
		setRoute(playerUuid, updated);
		return updated;
	}

	public record WaypointMatch(int routeId, BlockPos pos, PatrolRoute route) {}

	/**
	 * Scans across all 5 route channels for a player to find if any route contains any candidate position.
	 *
	 * @param playerUuid         The commanding player.
	 * @param candidatePositions One or more world block positions to check.
	 * @return The WaypointMatch if found on any route channel, or null if none match.
	 */
	public WaypointMatch findWaypoint(UUID playerUuid, BlockPos... candidatePositions) {
		if (playerUuid == null || candidatePositions == null) {
			return null;
		}
		for (int routeId = 0; routeId < PatrolRoute.CHANNEL_COUNT; routeId++) {
			PatrolRoute route = getRoute(playerUuid, routeId);
			for (BlockPos cand : candidatePositions) {
				if (cand != null && route.waypoints().contains(cand)) {
					return new WaypointMatch(routeId, cand, route);
				}
			}
		}
		return null;
	}

	/**
	 * Clears all waypoints from the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @return The cleared PatrolRoute.
	 */
	public PatrolRoute clearRoute(UUID playerUuid, int routeId) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		PatrolRoute updated = current.withClearedWaypoints();
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Toggles the patrol mode (LOOP vs PING_PONG) for the specified route.
	 *
	 * @param playerUuid The commanding player.
	 * @param routeId    The channel index.
	 * @return The updated PatrolRoute.
	 */
	public PatrolRoute togglePatrolMode(UUID playerUuid, int routeId) {
		PatrolRoute current = getRoute(playerUuid, routeId);
		PatrolRoute updated = current.withPatrolMode(current.patrolMode().toggle());
		setRoute(playerUuid, updated);
		return updated;
	}

	/**
	 * Synchronizes all active patrol routes for a player from server to client.
	 *
	 * @param player The server player entity.
	 */
	public void syncToPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		if (this.persistentState == null && player.getServer() != null) {
			net.minecraft.server.world.ServerWorld overworld = player.getServer().getOverworld();
			if (overworld != null) {
				init(overworld);
			}
		}
		List<PatrolRoute> routes = getAllRoutes(player.getUuid());
		ServerPlayNetworking.send(player, new SyncPatrolRoutesPayload(routes));
	}
}
