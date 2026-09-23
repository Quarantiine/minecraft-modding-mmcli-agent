package com.example.blueprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * World-saved persistent state for all user-created custom blueprints (`minion_custom_blueprints`).
 * Stores custom structure specifications and metadata across world reloads via NBT serialization.
 */
public class CustomBlueprintPersistentState extends PersistentState {

	public static final String KEY = "minion_custom_blueprints";

	private final Map<String, StructureBlueprint> customBlueprints = new ConcurrentHashMap<>();

	public CustomBlueprintPersistentState() {}

	public static final Type<CustomBlueprintPersistentState> TYPE = new Type<>(
		CustomBlueprintPersistentState::new,
		CustomBlueprintPersistentState::fromNbt,
		null
	);

	/**
	 * Deserializes custom blueprints from disk NBT.
	 *
	 * @param nbt        Root NBT compound.
	 * @param registries Dynamic registry wrapper lookup.
	 * @return Instantiated CustomBlueprintPersistentState.
	 */
	public static CustomBlueprintPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		CustomBlueprintPersistentState state = new CustomBlueprintPersistentState();
		if (nbt == null) {
			return state;
		}

		NbtCompound dataCompound = nbt.contains("data", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("data") : nbt;
		if (!dataCompound.contains("CustomBlueprints", NbtElement.LIST_TYPE)) {
			return state;
		}

		NbtList blueprintListNbt = dataCompound.getList("CustomBlueprints", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < blueprintListNbt.size(); i++) {
			NbtCompound bpNbt = blueprintListNbt.getCompound(i);
			try {
				String id = bpNbt.getString("id");
				String name = bpNbt.getString("name");
				String description = bpNbt.contains("description") ? bpNbt.getString("description") : "";

				if (id == null || id.isBlank() || name == null || name.isBlank()) {
					continue;
				}

				StructureBlueprint.Builder builder = StructureBlueprint.builder(id, name)
					.description(description);

				if (bpNbt.contains("blocks", NbtElement.LIST_TYPE)) {
					NbtList blockListNbt = bpNbt.getList("blocks", NbtElement.COMPOUND_TYPE);
					for (int j = 0; j < blockListNbt.size(); j++) {
						NbtCompound bNbt = blockListNbt.getCompound(j);
						int x = bNbt.getInt("x");
						int y = bNbt.getInt("y");
						int z = bNbt.getInt("z");
						int rawStateId = bNbt.getInt("state");

						try {
							BlockState blockState = Block.getStateFromRawId(rawStateId);
							if (blockState != null && !blockState.isAir()) {
								builder.addBlock(x, y, z, blockState);
							}
						} catch (Throwable ignored) {}
					}
				}

				StructureBlueprint blueprint = builder.build();
				state.customBlueprints.put(blueprint.getId().toLowerCase(), blueprint);
			} catch (Throwable e) {
				System.err.println("[CustomBlueprintPersistentState] Failed to deserialize custom blueprint at index " + i + ": " + e.getMessage());
			}
		}

		return state;
	}

	/**
	 * Serializes all active custom blueprints into NBT for world saving.
	 *
	 * @param nbt        The target compound to populate.
	 * @param registries Dynamic registry wrapper lookup.
	 * @return Populated NBT compound.
	 */
	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList blueprintListNbt = new NbtList();

		for (StructureBlueprint blueprint : this.customBlueprints.values()) {
			NbtCompound bpNbt = new NbtCompound();
			bpNbt.putString("id", blueprint.getId());
			bpNbt.putString("name", blueprint.getName());
			bpNbt.putString("description", blueprint.getDescription() != null ? blueprint.getDescription() : "");

			NbtList blockListNbt = new NbtList();
			for (BlueprintBlock block : blueprint.getBlocks()) {
				NbtCompound bNbt = new NbtCompound();
				bNbt.putInt("x", block.offset().getX());
				bNbt.putInt("y", block.offset().getY());
				bNbt.putInt("z", block.offset().getZ());
				bNbt.putInt("state", Block.getRawIdFromState(block.state()));
				blockListNbt.add(bNbt);
			}

			bpNbt.put("blocks", blockListNbt);
			blueprintListNbt.add(bpNbt);
		}

		nbt.put("CustomBlueprints", blueprintListNbt);
		return nbt;
	}

	/**
	 * Returns mutable map of all custom blueprints stored in this state.
	 *
	 * @return Map of lowercase blueprint IDs to StructureBlueprint objects.
	 */
	public Map<String, StructureBlueprint> getCustomBlueprints() {
		return this.customBlueprints;
	}

	/**
	 * Adds or updates a custom blueprint in persistent state.
	 *
	 * @param blueprint The structure blueprint to store.
	 */
	public void addBlueprint(StructureBlueprint blueprint) {
		Objects.requireNonNull(blueprint, "blueprint cannot be null");
		this.customBlueprints.put(blueprint.getId().toLowerCase(), blueprint);
		this.markDirty();
	}

	/**
	 * Removes a custom blueprint by identifier.
	 *
	 * @param id The blueprint identifier to remove.
	 * @return True if a blueprint was removed.
	 */
	public boolean removeBlueprint(String id) {
		if (id == null) return false;
		StructureBlueprint removed = this.customBlueprints.remove(id.toLowerCase().trim());
		if (removed != null) {
			this.markDirty();
			return true;
		}
		return false;
	}

	/**
	 * Retrieves a custom blueprint by ID.
	 *
	 * @param id The blueprint identifier.
	 * @return The structure blueprint, or null if not found.
	 */
	public StructureBlueprint getBlueprint(String id) {
		if (id == null) return null;
		return this.customBlueprints.get(id.toLowerCase().trim());
	}

	/**
	 * Returns an unmodifiable collection of all custom blueprints.
	 *
	 * @return Collection of custom blueprints.
	 */
	public Collection<StructureBlueprint> getAllBlueprints() {
		return Collections.unmodifiableCollection(this.customBlueprints.values());
	}
}
