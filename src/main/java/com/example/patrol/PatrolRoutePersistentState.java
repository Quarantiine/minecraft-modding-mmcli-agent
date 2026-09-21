package com.example.patrol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * World-saved persistent state for all player minion patrol routes.
 * Stored natively in the world's data directory (data/minion_patrol_routes.dat),
 * ensuring routes persist across game restarts and world reloads.
 */
public class PatrolRoutePersistentState extends PersistentState {

	public static final String KEY = "minion_patrol_routes";

	private final Map<UUID, Map<Integer, PatrolRoute>> routes = new ConcurrentHashMap<>();

	public PatrolRoutePersistentState() {}

	public static final Type<PatrolRoutePersistentState> TYPE = new Type<>(
		PatrolRoutePersistentState::new,
		PatrolRoutePersistentState::fromNbt,
		null
	);

	public static PatrolRoutePersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		PatrolRoutePersistentState state = new PatrolRoutePersistentState();
		if (nbt == null) {
			return state;
		}

		// Handle root wrapper if present ("data" tag used by PersistentStateManager)
		NbtCompound dataCompound = nbt.contains("data", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("data") : nbt;
		if (!dataCompound.contains("Players", NbtElement.COMPOUND_TYPE)) {
			return state;
		}

		NbtCompound playersNbt = dataCompound.getCompound("Players");
		for (String uuidKey : playersNbt.getKeys()) {
			try {
				UUID playerUuid = UUID.fromString(uuidKey);
				NbtList routeListNbt = playersNbt.getList(uuidKey, NbtElement.COMPOUND_TYPE);
				Map<Integer, PatrolRoute> playerRouteMap = new ConcurrentHashMap<>();

				for (int i = 0; i < routeListNbt.size(); i++) {
					NbtCompound rNbt = routeListNbt.getCompound(i);
					int routeId = rNbt.getInt("routeId");
					String name = rNbt.getString("name");
					int colorRgb = rNbt.getInt("colorRgb");

					PatrolRoute.PatrolMode mode = PatrolRoute.PatrolMode.LOOP;
					if (rNbt.contains("patrolMode", NbtElement.STRING_TYPE)) {
						try {
							mode = PatrolRoute.PatrolMode.valueOf(rNbt.getString("patrolMode"));
						} catch (IllegalArgumentException ignored) {}
					}

					List<BlockPos> waypoints = new ArrayList<>();
					if (rNbt.contains("waypoints")) {
						NbtList wpList = rNbt.getList("waypoints", NbtElement.COMPOUND_TYPE);
						for (int j = 0; j < wpList.size(); j++) {
							NbtCompound wp = wpList.getCompound(j);
							waypoints.add(new BlockPos(wp.getInt("x"), wp.getInt("y"), wp.getInt("z")));
						}
					}

					playerRouteMap.put(routeId, new PatrolRoute(routeId, name, colorRgb, waypoints, mode));
				}
				state.routes.put(playerUuid, playerRouteMap);
			} catch (Exception e) {
				System.err.println("[PatrolRoutePersistentState] Failed to deserialize route for " + uuidKey + ": " + e.getMessage());
			}
		}

		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound playersNbt = new NbtCompound();

		for (Map.Entry<UUID, Map<Integer, PatrolRoute>> entry : this.routes.entrySet()) {
			NbtList routeListNbt = new NbtList();
			for (PatrolRoute route : entry.getValue().values()) {
				NbtCompound rNbt = new NbtCompound();
				rNbt.putInt("routeId", route.routeId());
				rNbt.putString("name", route.name());
				rNbt.putInt("colorRgb", route.colorRgb());
				rNbt.putString("patrolMode", route.patrolMode().name());

				NbtList wpList = new NbtList();
				for (BlockPos pos : route.waypoints()) {
					NbtCompound wp = new NbtCompound();
					wp.putInt("x", pos.getX());
					wp.putInt("y", pos.getY());
					wp.putInt("z", pos.getZ());
					wpList.add(wp);
				}
				rNbt.put("waypoints", wpList);
				routeListNbt.add(rNbt);
			}
			playersNbt.put(entry.getKey().toString(), routeListNbt);
		}

		nbt.put("Players", playersNbt);
		return nbt;
	}

	public Map<UUID, Map<Integer, PatrolRoute>> getRoutes() {
		return this.routes;
	}
}
