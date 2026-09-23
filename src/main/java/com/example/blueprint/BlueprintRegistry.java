package com.example.blueprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry and catalog of dynamic custom blueprints captured by players.
 * Exclusively provides lookup, ordered cycling, and multiblock blueprints
 * for minion construction directives from user custom blueprints.
 */
public class BlueprintRegistry {

	private static final Map<String, StructureBlueprint> REGISTRY = new LinkedHashMap<>();
	private static final Map<String, StructureBlueprint> CUSTOM_REGISTRY = new LinkedHashMap<>();

	public static final String WATCHTOWER_ID = "watchtower";

	/**
	 * Safe empty blueprint fallback when no custom blueprints have been captured.
	 */
	public static final StructureBlueprint EMPTY = StructureBlueprint.builder("empty", "None")
		.description("No custom blueprint selected. Capture an in-world structure in DESIGN mode.")
		.build();

	public static final StructureBlueprint WATCHTOWER = EMPTY;

	/**
	 * Registers a blueprint into the static registry.
	 *
	 * @param blueprint The structure blueprint to register.
	 * @return The registered blueprint.
	 */
	public static synchronized StructureBlueprint register(StructureBlueprint blueprint) {
		if (blueprint != null) {
			REGISTRY.put(blueprint.getId().toLowerCase().trim(), blueprint);
		}
		return blueprint;
	}

	/**
	 * Dynamically registers a custom player-created blueprint.
	 *
	 * @param blueprint The custom structure blueprint to register.
	 * @return The registered blueprint.
	 */
	public static synchronized StructureBlueprint registerCustomBlueprint(StructureBlueprint blueprint) {
		if (blueprint == null) return null;
		String cleanId = blueprint.getId().toLowerCase().trim();
		CUSTOM_REGISTRY.put(cleanId, blueprint);
		REGISTRY.put(cleanId, blueprint);
		return blueprint;
	}

	/**
	 * Unregisters a custom blueprint by identifier.
	 *
	 * @param id The blueprint identifier to remove.
	 * @return True if a custom blueprint was removed.
	 */
	public static synchronized boolean unregisterCustomBlueprint(String id) {
		if (id == null) return false;
		String cleanId = id.toLowerCase().trim();
		StructureBlueprint removedCustom = CUSTOM_REGISTRY.remove(cleanId);
		StructureBlueprint removedGlobal = REGISTRY.remove(cleanId);
		return removedCustom != null || removedGlobal != null;
	}

	/**
	 * Checks if a given blueprint ID belongs to a player-created custom blueprint.
	 *
	 * @param id Blueprint identifier.
	 * @return True if registered as a custom blueprint.
	 */
	public static synchronized boolean isCustom(String id) {
		if (id == null) return false;
		return CUSTOM_REGISTRY.containsKey(id.toLowerCase().trim());
	}

	/**
	 * Clears all registered custom blueprints (e.g. on client disconnection or full catalog resync).
	 */
	public static synchronized void clearCustomBlueprints() {
		for (String customId : CUSTOM_REGISTRY.keySet()) {
			REGISTRY.remove(customId);
		}
		CUSTOM_REGISTRY.clear();
	}

	/**
	 * Returns an unmodifiable collection of all currently registered custom blueprints.
	 *
	 * @return Collection of custom blueprints.
	 */
	public static synchronized Collection<StructureBlueprint> getCustomBlueprints() {
		return Collections.unmodifiableCollection(new ArrayList<>(CUSTOM_REGISTRY.values()));
	}

	/**
	 * Looks up a custom blueprint by case-insensitive identifier.
	 *
	 * @param id The blueprint identifier.
	 * @return Optional containing the blueprint if found.
	 */
	public static synchronized Optional<StructureBlueprint> get(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		String cleanId = id.toLowerCase().trim();
		StructureBlueprint direct = REGISTRY.get(cleanId);
		if (direct != null) {
			return Optional.of(direct);
		}
		return Optional.empty();
	}

	/**
	 * Returns the blueprint with the given id, or the EMPTY blueprint if not found.
	 *
	 * @param id The blueprint identifier.
	 * @return The resolved StructureBlueprint.
	 */
	public static StructureBlueprint getOrDefault(String id) {
		return get(id).orElse(EMPTY);
	}

	/**
	 * Resolves a blueprint dynamically. For custom blueprints, returns the exact blueprint as registered.
	 *
	 * @param id   The blueprint identifier.
	 * @param size The size preset (retained for signature compatibility).
	 * @param seed The generation seed (retained for signature compatibility).
	 * @return The resolved StructureBlueprint.
	 */
	public static StructureBlueprint resolveCategoryBlueprint(String id, int size, long seed) {
		return getOrDefault(id);
	}

	/**
	 * Returns the active catalog of blueprints (all registered custom blueprints).
	 *
	 * @return List of active blueprints in display sequence.
	 */
	public static synchronized List<StructureBlueprint> getActiveCatalog() {
		return new ArrayList<>(CUSTOM_REGISTRY.values());
	}

	/**
	 * Cycles to the next blueprint in the registered custom catalog.
	 *
	 * @param currentId The current active blueprint identifier.
	 * @return The next StructureBlueprint in sequence, or EMPTY if catalog is empty.
	 */
	public static synchronized StructureBlueprint getNext(String currentId) {
		List<StructureBlueprint> catalog = getActiveCatalog();
		if (catalog.isEmpty()) {
			return EMPTY;
		}
		if (currentId == null || currentId.isBlank()) {
			return catalog.get(0);
		}
		String cleanId = currentId.toLowerCase().trim();
		for (int i = 0; i < catalog.size(); i++) {
			StructureBlueprint bp = catalog.get(i);
			if (bp.getId().equalsIgnoreCase(cleanId)) {
				return catalog.get((i + 1) % catalog.size());
			}
		}
		return catalog.get(0);
	}

	/**
	 * Cycles to the previous blueprint in the registered custom catalog.
	 *
	 * @param currentId The current active blueprint identifier.
	 * @return The previous StructureBlueprint in sequence, or EMPTY if catalog is empty.
	 */
	public static synchronized StructureBlueprint getPrevious(String currentId) {
		List<StructureBlueprint> catalog = getActiveCatalog();
		if (catalog.isEmpty()) {
			return EMPTY;
		}
		if (currentId == null || currentId.isBlank()) {
			return catalog.get(catalog.size() - 1);
		}
		String cleanId = currentId.toLowerCase().trim();
		for (int i = 0; i < catalog.size(); i++) {
			StructureBlueprint bp = catalog.get(i);
			if (bp.getId().equalsIgnoreCase(cleanId)) {
				return catalog.get((i - 1 + catalog.size()) % catalog.size());
			}
		}
		return catalog.get(catalog.size() - 1);
	}

	/**
	 * Returns all registered custom blueprints.
	 *
	 * @return Unmodifiable collection of blueprints.
	 */
	public static synchronized Collection<StructureBlueprint> getAll() {
		return Collections.unmodifiableCollection(new ArrayList<>(CUSTOM_REGISTRY.values()));
	}

	/**
	 * Returns the default fallback blueprint.
	 *
	 * @return The default StructureBlueprint.
	 */
	public static StructureBlueprint getDefaultBlueprint() {
		List<StructureBlueprint> catalog = getActiveCatalog();
		return catalog.isEmpty() ? EMPTY : catalog.get(0);
	}
}
