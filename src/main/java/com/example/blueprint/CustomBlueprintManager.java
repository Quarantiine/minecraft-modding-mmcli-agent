package com.example.blueprint;

import com.example.network.SyncCustomBlueprintsPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;

/**
 * Server-side singleton manager maintaining user-designed {@link StructureBlueprint}s.
 * Handles blueprint querying, saving, dynamic registration into {@link BlueprintRegistry},
 * world persistence via {@link CustomBlueprintPersistentState}, and client synchronization.
 */
public class CustomBlueprintManager {

	private static final CustomBlueprintManager INSTANCE = new CustomBlueprintManager();

	private CustomBlueprintPersistentState persistentState = null;
	private PersistentStateManager stateManager = null;
	private final Map<String, StructureBlueprint> customBlueprints = new ConcurrentHashMap<>();

	private CustomBlueprintManager() {}

	public static CustomBlueprintManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Initializes the manager with the world's PersistentStateManager on server startup.
	 *
	 * @param overworld The server overworld instance.
	 */
	public void init(ServerWorld overworld) {
		if (overworld == null) return;
		this.stateManager = overworld.getPersistentStateManager();
		this.persistentState = this.stateManager.getOrCreate(
			CustomBlueprintPersistentState.TYPE,
			CustomBlueprintPersistentState.KEY
		);
		loadFromPersistentState(this.persistentState);
	}

	/**
	 * Loads custom blueprints from persistent state and registers them into the global registry.
	 *
	 * @param state The loaded CustomBlueprintPersistentState.
	 */
	public void loadFromPersistentState(CustomBlueprintPersistentState state) {
		this.persistentState = state;
		this.customBlueprints.clear();
		try {
			BlueprintRegistry.clearCustomBlueprints();
		} catch (Throwable ignored) {}

		if (state != null) {
			for (StructureBlueprint blueprint : state.getAllBlueprints()) {
				this.customBlueprints.put(blueprint.getId().toLowerCase(), blueprint);
				try {
					BlueprintRegistry.registerCustomBlueprint(blueprint);
				} catch (Throwable ignored) {}
			}
		}
	}

	/**
	 * Called during server shutdown to finalize and save persistent custom blueprint data.
	 */
	public void onServerStopping() {
		markDirtyAndPersist();
	}

	/**
	 * Flags persistent state dirty and flushes it to disk via the state manager.
	 */
	private void markDirtyAndPersist() {
		if (this.persistentState != null) {
			this.persistentState.getCustomBlueprints().clear();
			for (Map.Entry<String, StructureBlueprint> entry : this.customBlueprints.entrySet()) {
				this.persistentState.getCustomBlueprints().put(entry.getKey(), entry.getValue());
			}
			this.persistentState.markDirty();
			if (this.stateManager != null) {
				try {
					this.stateManager.save();
				} catch (Exception e) {
					System.err.println("[CustomBlueprintManager] Failed to flush persistent state to disk: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Captures an in-world spatial volume and compiles it into a normalized, topologically
	 * sorted {@link StructureBlueprint} without immediately saving or broadcasting.
	 *
	 * @param world       The ServerWorld instance.
	 * @param pos1        First corner position.
	 * @param pos2        Second corner position.
	 * @param id          Unique blueprint identifier.
	 * @param name        User-facing display name.
	 * @param description User-facing description.
	 * @return Compiled StructureBlueprint.
	 */
	public StructureBlueprint captureSpatialBlueprint(
		ServerWorld world,
		net.minecraft.util.math.BlockPos pos1,
		net.minecraft.util.math.BlockPos pos2,
		String id,
		String name,
		String description
	) {
		Objects.requireNonNull(world, "world cannot be null");
		Objects.requireNonNull(pos1, "pos1 cannot be null");
		Objects.requireNonNull(pos2, "pos2 cannot be null");

		String cleanId = (id != null && !id.isBlank()) ? id.toLowerCase().trim() : "custom_capture_" + System.currentTimeMillis();
		String cleanName = (name != null && !name.isBlank()) ? name.trim() : "Captured Structure";
		String cleanDesc = (description != null && !description.isBlank()) ? description.trim() : "";

		return StructureBlueprint.captureFromWorld(world, pos1, pos2, cleanId, cleanName, cleanDesc);
	}

	/**
	 * Captures an in-world spatial volume, normalizes and compiles it into a {@link StructureBlueprint},
	 * saves and registers it dynamically into world persistent state, and broadcasts to all clients.
	 *
	 * @param server      The MinecraftServer instance.
	 * @param world       The ServerWorld instance.
	 * @param player      The commanding player initiating the capture.
	 * @param pos1        First corner position.
	 * @param pos2        Second corner position.
	 * @param id          Unique blueprint identifier.
	 * @param name        User-facing display name.
	 * @param description User-facing description.
	 * @return Saved StructureBlueprint.
	 */
	public StructureBlueprint captureAndSaveBlueprint(
		MinecraftServer server,
		ServerWorld world,
		ServerPlayerEntity player,
		net.minecraft.util.math.BlockPos pos1,
		net.minecraft.util.math.BlockPos pos2,
		String id,
		String name,
		String description
	) {
		StructureBlueprint blueprint = captureSpatialBlueprint(world, pos1, pos2, id, name, description);
		saveBlueprint(server, blueprint);
		return blueprint;
	}

	/**
	 * Convenience overload for server-side player-initiated spatial capture and save.
	 *
	 * @param player      The server player.
	 * @param pos1        First corner position.
	 * @param pos2        Second corner position.
	 * @param id          Unique blueprint identifier.
	 * @param name        User-facing display name.
	 * @param description User-facing description.
	 * @return Saved StructureBlueprint.
	 */
	public StructureBlueprint captureAndSaveBlueprint(
		ServerPlayerEntity player,
		net.minecraft.util.math.BlockPos pos1,
		net.minecraft.util.math.BlockPos pos2,
		String id,
		String name,
		String description
	) {
		Objects.requireNonNull(player, "player cannot be null");
		return captureAndSaveBlueprint(player.getServer(), player.getServerWorld(), player, pos1, pos2, id, name, description);
	}

	/**
	 * Saves a custom blueprint, registers it dynamically, persists it to disk,
	 * and synchronizes the updated catalog to all connected players.
	 *
	 * @param server    MinecraftServer instance.
	 * @param blueprint The structure blueprint to save.
	 */
	public void saveBlueprint(MinecraftServer server, StructureBlueprint blueprint) {
		Objects.requireNonNull(blueprint, "blueprint cannot be null");
		String cleanId = blueprint.getId().toLowerCase().trim();
		this.customBlueprints.put(cleanId, blueprint);
		try {
			BlueprintRegistry.registerCustomBlueprint(blueprint);
		} catch (Throwable ignored) {}
		if (this.persistentState != null) {
			this.persistentState.addBlueprint(blueprint);
		}
		markDirtyAndPersist();

		if (server != null) {
			syncToAll(server);
		}
	}

	/**
	 * Deletes a custom blueprint, unregisters it from the global registry, updates disk persistence,
	 * and synchronizes the updated catalog to all connected players.
	 *
	 * @param server      MinecraftServer instance.
	 * @param blueprintId The blueprint identifier to remove.
	 * @return True if a blueprint was deleted.
	 */
	public boolean deleteBlueprint(MinecraftServer server, String blueprintId) {
		if (blueprintId == null) return false;
		String cleanId = blueprintId.toLowerCase().trim();
		StructureBlueprint removed = this.customBlueprints.remove(cleanId);
		boolean registryRemoved = false;
		try {
			registryRemoved = BlueprintRegistry.unregisterCustomBlueprint(cleanId);
		} catch (Throwable ignored) {}
		boolean stateRemoved = false;
		if (this.persistentState != null) {
			stateRemoved = this.persistentState.removeBlueprint(cleanId);
		}
		markDirtyAndPersist();

		if (server != null) {
			syncToAll(server);
		}
		return removed != null || registryRemoved || stateRemoved;
	}

	/**
	 * Returns an unmodifiable collection of all custom blueprints currently loaded on the server.
	 *
	 * @return Collection of custom blueprints.
	 */
	public Collection<StructureBlueprint> getAllCustomBlueprints() {
		return Collections.unmodifiableCollection(this.customBlueprints.values());
	}

	/**
	 * Returns a custom blueprint by ID if present.
	 *
	 * @param id The blueprint identifier.
	 * @return The structure blueprint, or null if not found.
	 */
	public StructureBlueprint getCustomBlueprint(String id) {
		if (id == null) return null;
		return this.customBlueprints.get(id.toLowerCase().trim());
	}

	/**
	 * Synchronizes all custom blueprints to a specific player client.
	 *
	 * @param player The server player entity to synchronize.
	 */
	public void syncToPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		if (this.persistentState == null && player.getServer() != null) {
			ServerWorld overworld = player.getServer().getOverworld();
			if (overworld != null) {
				init(overworld);
			}
		}
		List<StructureBlueprint> blueprints = new ArrayList<>(this.customBlueprints.values());
		ServerPlayNetworking.send(player, new SyncCustomBlueprintsPayload(blueprints));
	}

	/**
	 * Synchronizes all custom blueprints to all connected players on the server.
	 *
	 * @param server The Minecraft server.
	 */
	public void syncToAll(MinecraftServer server) {
		if (server == null) return;
		List<StructureBlueprint> blueprints = new ArrayList<>(this.customBlueprints.values());
		SyncCustomBlueprintsPayload payload = new SyncCustomBlueprintsPayload(blueprints);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
