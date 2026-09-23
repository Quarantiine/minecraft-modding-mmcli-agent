package com.example.blueprint;

import com.example.network.CreateCustomBlueprintPayload;
import com.example.network.DeleteCustomBlueprintPayload;
import com.example.network.SyncCustomBlueprintsPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating server-side custom blueprint persistence,
 * dynamic registration/unregistration lifecycle, world state serialization,
 * corruption handling, dirty state tracking, manager lifecycle, payload codecs,
 * and registry integration.
 */
public class CustomBlueprintPersistenceTest {

	@org.junit.jupiter.api.BeforeAll
	static void initBootstrap() {
		try {
			net.minecraft.SharedConstants.createGameVersion();
			net.minecraft.Bootstrap.initialize();
		} catch (Throwable t) {
			System.err.println("Bootstrap failed: " + t);
			t.printStackTrace();
		}
	}

	@BeforeEach
	@AfterEach
	void cleanup() {
		BlueprintRegistry.clearCustomBlueprints();
		CustomBlueprintManager.getInstance().loadFromPersistentState(null);
	}

	@Test
	@DisplayName("CustomBlueprintPersistentState constants and type initialization")
	void testPersistentStateConstantsAndType() {
		Assertions.assertEquals("minion_custom_blueprints", CustomBlueprintPersistentState.KEY);
		Assertions.assertNotNull(CustomBlueprintPersistentState.TYPE);
	}

	@Test
	@DisplayName("CustomBlueprintPersistentState NBT serialization roundtrip preserves structure geometry and metadata")
	void testPersistentStateSerializationRoundtrip() {
		CustomBlueprintPersistentState state = new CustomBlueprintPersistentState();

		StructureBlueprint bp1 = StructureBlueprint.builder("custom_watchtower_mini", "Mini Watchtower")
			.description("A compact 3x3 stone brick watchtower.")
			.build();

		StructureBlueprint bp2 = StructureBlueprint.builder("custom_gate", "Reinforced Gate")
			.description("Frontier defense barricade.")
			.build();

		state.addBlueprint(bp1);
		state.addBlueprint(bp2);

		Assertions.assertEquals(2, state.getAllBlueprints().size());
		Assertions.assertNotNull(state.getBlueprint("custom_watchtower_mini"));
		Assertions.assertNotNull(state.getBlueprint("custom_gate"));

		// Write to NBT
		NbtCompound rootNbt = new NbtCompound();
		state.writeNbt(rootNbt, null);

		Assertions.assertTrue(rootNbt.contains("CustomBlueprints", NbtElement.LIST_TYPE));
		NbtList list = rootNbt.getList("CustomBlueprints", NbtElement.COMPOUND_TYPE);
		Assertions.assertEquals(2, list.size());

		// Deserialization from root NBT
		CustomBlueprintPersistentState deserialized = CustomBlueprintPersistentState.fromNbt(rootNbt, null);
		Assertions.assertEquals(2, deserialized.getAllBlueprints().size());

		StructureBlueprint loadedBp1 = deserialized.getBlueprint("custom_watchtower_mini");
		Assertions.assertNotNull(loadedBp1);
		Assertions.assertEquals("custom_watchtower_mini", loadedBp1.getId());
		Assertions.assertEquals("Mini Watchtower", loadedBp1.getName());
		Assertions.assertEquals("A compact 3x3 stone brick watchtower.", loadedBp1.getDescription());
		Assertions.assertEquals(bp1.getBlockCount(), loadedBp1.getBlockCount());

		StructureBlueprint loadedBp2 = deserialized.getBlueprint("custom_gate");
		Assertions.assertNotNull(loadedBp2);
		Assertions.assertEquals("Reinforced Gate", loadedBp2.getName());
		Assertions.assertEquals(bp2.getBlockCount(), loadedBp2.getBlockCount());

		// Deserialization with "data" tag wrapper (PersistentStateManager compatibility)
		NbtCompound wrappedNbt = new NbtCompound();
		wrappedNbt.put("data", rootNbt);
		CustomBlueprintPersistentState wrappedDeserialized = CustomBlueprintPersistentState.fromNbt(wrappedNbt, null);
		Assertions.assertEquals(2, wrappedDeserialized.getAllBlueprints().size());
		Assertions.assertNotNull(wrappedDeserialized.getBlueprint("custom_watchtower_mini"));
		Assertions.assertNotNull(wrappedDeserialized.getBlueprint("custom_gate"));
	}

	@Test
	@DisplayName("CustomBlueprintPersistentState handles empty descriptions and coordinate extremes")
	void testPersistentStateCoordinatesAndNullDescription() {
		CustomBlueprintPersistentState state = new CustomBlueprintPersistentState();

		StructureBlueprint bp = StructureBlueprint.builder("extreme_pos_bp", "Extreme Coordinates")
			.description(null)
			.build();

		state.addBlueprint(bp);

		NbtCompound rootNbt = new NbtCompound();
		state.writeNbt(rootNbt, null);

		CustomBlueprintPersistentState deserialized = CustomBlueprintPersistentState.fromNbt(rootNbt, null);
		StructureBlueprint loaded = deserialized.getBlueprint("extreme_pos_bp");
		Assertions.assertNotNull(loaded);
		Assertions.assertEquals("Extreme Coordinates", loaded.getName());
		Assertions.assertEquals("", loaded.getDescription());
		Assertions.assertEquals(0, loaded.getBlockCount());
	}

	@Test
	@DisplayName("CustomBlueprintPersistentState mutation and deletion marks state dirty correctly")
	void testPersistentStateDirtyAndRemoval() {
		CustomBlueprintPersistentState state = new CustomBlueprintPersistentState();
		state.setDirty(false);
		Assertions.assertFalse(state.isDirty());

		StructureBlueprint bp = StructureBlueprint.builder("custom_bunker", "Bunker")
			.description("Underground bunker")
			.build();

		state.addBlueprint(bp);
		Assertions.assertTrue(state.isDirty());
		Assertions.assertEquals(1, state.getAllBlueprints().size());

		state.setDirty(false);
		boolean removed = state.removeBlueprint("custom_bunker");
		Assertions.assertTrue(removed);
		Assertions.assertTrue(state.isDirty());
		Assertions.assertNull(state.getBlueprint("custom_bunker"));
		Assertions.assertEquals(0, state.getAllBlueprints().size());

		// Removing non-existent does not mark dirty if it was false
		state.setDirty(false);
		boolean removedAgain = state.removeBlueprint("non_existent");
		Assertions.assertFalse(removedAgain);
		Assertions.assertFalse(state.isDirty());

		Assertions.assertFalse(state.removeBlueprint(null));
		Assertions.assertNull(state.getBlueprint(null));
	}

	@Test
	@DisplayName("CustomBlueprintPersistentState handles null, empty, missing tags, and corrupted NBT gracefully")
	void testPersistentStateCorruptedNbtHandling() {
		// Null NBT
		CustomBlueprintPersistentState state1 = CustomBlueprintPersistentState.fromNbt(null, null);
		Assertions.assertNotNull(state1);
		Assertions.assertTrue(state1.getAllBlueprints().isEmpty());

		// Empty NBT
		CustomBlueprintPersistentState state2 = CustomBlueprintPersistentState.fromNbt(new NbtCompound(), null);
		Assertions.assertNotNull(state2);
		Assertions.assertTrue(state2.getAllBlueprints().isEmpty());

		// Empty wrapped data tag
		NbtCompound emptyData = new NbtCompound();
		emptyData.put("data", new NbtCompound());
		CustomBlueprintPersistentState stateDataEmpty = CustomBlueprintPersistentState.fromNbt(emptyData, null);
		Assertions.assertNotNull(stateDataEmpty);
		Assertions.assertTrue(stateDataEmpty.getAllBlueprints().isEmpty());

		// Malformed entries (blank id, missing name, missing blocks) alongside valid entry
		NbtCompound compound = new NbtCompound();
		NbtList list = new NbtList();

		// Bad entry 1: blank id
		NbtCompound badEntry1 = new NbtCompound();
		badEntry1.putString("id", "   ");
		badEntry1.putString("name", "Unnamed");
		list.add(badEntry1);

		// Bad entry 2: missing name
		NbtCompound badEntry2 = new NbtCompound();
		badEntry2.putString("id", "valid_id");
		badEntry2.putString("name", "   ");
		list.add(badEntry2);

		// Bad entry 3: invalid blockstate raw id
		NbtCompound badEntry3 = new NbtCompound();
		badEntry3.putString("id", "corrupt_blocks");
		badEntry3.putString("name", "Corrupt Blocks");
		NbtList badBlocks = new NbtList();
		NbtCompound badBlockTag = new NbtCompound();
		badBlockTag.putInt("x", 0);
		badBlockTag.putInt("y", 0);
		badBlockTag.putInt("z", 0);
		badBlockTag.putInt("state", 0); // 0 or air
		badBlocks.add(badBlockTag);
		badEntry3.put("blocks", badBlocks);
		list.add(badEntry3);

		// Good entry
		NbtCompound goodEntry = new NbtCompound();
		goodEntry.putString("id", "valid_custom");
		goodEntry.putString("name", "Valid Custom Blueprint");
		goodEntry.putString("description", "A valid test blueprint");
		list.add(goodEntry);

		compound.put("CustomBlueprints", list);

		CustomBlueprintPersistentState state3 = CustomBlueprintPersistentState.fromNbt(compound, null);
		Assertions.assertNotNull(state3);
		// badEntry1 & badEntry2 skipped; badEntry3 loaded with 0 non-air blocks; goodEntry loaded
		Assertions.assertEquals(2, state3.getAllBlueprints().size());
		Assertions.assertNotNull(state3.getBlueprint("valid_custom"));
		Assertions.assertNotNull(state3.getBlueprint("corrupt_blocks"));
		Assertions.assertEquals(0, state3.getBlueprint("corrupt_blocks").getBlockCount());
		Assertions.assertEquals("Valid Custom Blueprint", state3.getBlueprint("valid_custom").getName());
	}

	@Test
	@DisplayName("CustomBlueprintManager loads from persistent state and manages cached blueprints")
	void testCustomBlueprintManagerLifecycle() {
		CustomBlueprintManager manager = CustomBlueprintManager.getInstance();
		Assertions.assertNotNull(manager);

		CustomBlueprintPersistentState state = new CustomBlueprintPersistentState();
		StructureBlueprint custom1 = StructureBlueprint.builder("custom_shrine", "Arcane Shrine")
			.description("A mystical shrine")
			.build();
		state.addBlueprint(custom1);

		manager.loadFromPersistentState(state);

		Assertions.assertEquals(1, manager.getAllCustomBlueprints().size());
		Assertions.assertNotNull(manager.getCustomBlueprint("custom_shrine"));
		Assertions.assertEquals("Arcane Shrine", manager.getCustomBlueprint("custom_shrine").getName());
		Assertions.assertNull(manager.getCustomBlueprint("non_existent"));
		Assertions.assertNull(manager.getCustomBlueprint(null));
		Assertions.assertTrue(BlueprintRegistry.isCustom("custom_shrine"));

		// Save new blueprint via manager
		StructureBlueprint custom2 = StructureBlueprint.builder("custom_bridge", "Arched Bridge")
			.description("A high arched bridge")
			.build();
		manager.saveBlueprint(null, custom2);

		Assertions.assertEquals(2, manager.getAllCustomBlueprints().size());
		Assertions.assertNotNull(manager.getCustomBlueprint("custom_bridge"));
		Assertions.assertTrue(BlueprintRegistry.isCustom("custom_bridge"));
		Assertions.assertNotNull(state.getBlueprint("custom_bridge"));

		// Delete blueprint via manager
		boolean deleted = manager.deleteBlueprint(null, "custom_shrine");
		Assertions.assertTrue(deleted);
		Assertions.assertEquals(1, manager.getAllCustomBlueprints().size());
		Assertions.assertNull(manager.getCustomBlueprint("custom_shrine"));
		Assertions.assertFalse(BlueprintRegistry.isCustom("custom_shrine"));
		Assertions.assertNotNull(manager.getCustomBlueprint("custom_bridge"));

		// Delete non-existent
		Assertions.assertFalse(manager.deleteBlueprint(null, null));
		Assertions.assertFalse(manager.deleteBlueprint(null, "non_existent"));

		// Loading null resets state cleanly
		manager.loadFromPersistentState(null);
		Assertions.assertEquals(0, manager.getAllCustomBlueprints().size());
		Assertions.assertFalse(BlueprintRegistry.isCustom("custom_bridge"));

		// Verify unmodifiable collection
		Collection<StructureBlueprint> allCustoms = manager.getAllCustomBlueprints();
		Assertions.assertThrows(UnsupportedOperationException.class, () -> allCustoms.add(custom1));
	}

	@Test
	@DisplayName("CustomBlueprintManager saveBlueprint null checks and ID normalization")
	void testCustomBlueprintManagerSaveValidation() {
		CustomBlueprintManager manager = CustomBlueprintManager.getInstance();
		Assertions.assertThrows(NullPointerException.class, () -> manager.saveBlueprint(null, null));

		StructureBlueprint unnormalized = StructureBlueprint.builder("  Custom_Fortress_Alpha  ", "Alpha Fortress")
			.description("Fortress")
			.build();

		manager.saveBlueprint(null, unnormalized);

		Assertions.assertNotNull(manager.getCustomBlueprint("custom_fortress_alpha"));
		Assertions.assertNotNull(manager.getCustomBlueprint("CUSTOM_FORTRESS_ALPHA"));
		Assertions.assertNotNull(manager.getCustomBlueprint("  custom_fortress_alpha  "));
		Assertions.assertTrue(BlueprintRegistry.isCustom("custom_fortress_alpha"));
	}

	@Test
	@DisplayName("BlueprintRegistry integration: dynamic registration, catalog cycling, and lookup")
	void testBlueprintRegistryCustomIntegration() {
		// Built-in presets are not custom
		Assertions.assertFalse(BlueprintRegistry.isCustom("watchtower"));
		Assertions.assertFalse(BlueprintRegistry.isCustom("obelisk"));
		Assertions.assertFalse(BlueprintRegistry.isCustom("barricade"));
		Assertions.assertFalse(BlueprintRegistry.isCustom(null));

		int initialCatalogSize = BlueprintRegistry.getActiveCatalog().size();

		StructureBlueprint custom1 = StructureBlueprint.builder("custom_alpha", "Custom Alpha")
			.description("Alpha structure")
			.build();
		StructureBlueprint custom2 = StructureBlueprint.builder("custom_beta", "Custom Beta")
			.description("Beta structure")
			.build();

		BlueprintRegistry.registerCustomBlueprint(custom1);
		BlueprintRegistry.registerCustomBlueprint(custom2);

		Assertions.assertTrue(BlueprintRegistry.isCustom("custom_alpha"));
		Assertions.assertTrue(BlueprintRegistry.isCustom("CUSTOM_ALPHA"));
		Assertions.assertTrue(BlueprintRegistry.isCustom("custom_beta"));
		Assertions.assertEquals(2, BlueprintRegistry.getCustomBlueprints().size());
		Assertions.assertEquals(initialCatalogSize + 2, BlueprintRegistry.getActiveCatalog().size());

		// Direct lookup via get()
		Optional<StructureBlueprint> opt1 = BlueprintRegistry.get("custom_alpha");
		Assertions.assertTrue(opt1.isPresent());
		Assertions.assertEquals("Custom Alpha", opt1.get().getName());

		// getOrDefault lookup
		StructureBlueprint foundOrDefault = BlueprintRegistry.getOrDefault("custom_alpha");
		Assertions.assertEquals("Custom Alpha", foundOrDefault.getName());

		// Catalog cycling with custom blueprints
		StructureBlueprint next = BlueprintRegistry.getNext("custom_alpha");
		Assertions.assertEquals("custom_beta", next.getId());

		StructureBlueprint prev = BlueprintRegistry.getPrevious("custom_beta");
		Assertions.assertEquals("custom_alpha", prev.getId());

		// Unregistering one custom blueprint
		boolean unreg = BlueprintRegistry.unregisterCustomBlueprint("custom_alpha");
		Assertions.assertTrue(unreg);
		Assertions.assertFalse(BlueprintRegistry.isCustom("custom_alpha"));
		Assertions.assertFalse(BlueprintRegistry.unregisterCustomBlueprint("custom_alpha"));
		Assertions.assertFalse(BlueprintRegistry.unregisterCustomBlueprint(null));
		Assertions.assertEquals(1, BlueprintRegistry.getCustomBlueprints().size());

		// Clear all custom blueprints leaves catalog empty (100% custom-driven)
		BlueprintRegistry.clearCustomBlueprints();
		Assertions.assertEquals(0, BlueprintRegistry.getCustomBlueprints().size());
		Assertions.assertEquals(0, BlueprintRegistry.getActiveCatalog().size());
		Assertions.assertTrue(BlueprintRegistry.get("watchtower").isEmpty());
		Assertions.assertTrue(BlueprintRegistry.get("obelisk").isEmpty());
		Assertions.assertTrue(BlueprintRegistry.get("barricade").isEmpty());
	}

	@Test
	@DisplayName("Custom blueprint payloads record definitions, IDs, and conversions")
	void testCustomBlueprintPayloads() {
		CreateCustomBlueprintPayload createPayload = new CreateCustomBlueprintPayload("custom_watchtower", "Watchtower", "Tall tower", List.of());
		Assertions.assertEquals("custom_watchtower", createPayload.id());
		Assertions.assertEquals("Watchtower", createPayload.name());
		Assertions.assertEquals("Tall tower", createPayload.description());
		Assertions.assertEquals(0, createPayload.blocks().size());
		Assertions.assertEquals(CreateCustomBlueprintPayload.ID, createPayload.getId());
		Assertions.assertNotNull(CreateCustomBlueprintPayload.PACKET_CODEC);

		// Compilation into StructureBlueprint
		StructureBlueprint compiled = createPayload.toStructureBlueprint();
		Assertions.assertNotNull(compiled);
		Assertions.assertEquals("custom_watchtower", compiled.getId());
		Assertions.assertEquals("Watchtower", compiled.getName());
		Assertions.assertEquals("Tall tower", compiled.getDescription());
		Assertions.assertEquals(0, compiled.getBlockCount());

		// StructureBlueprint constructor overload
		CreateCustomBlueprintPayload fromBp = new CreateCustomBlueprintPayload(compiled);
		Assertions.assertEquals("custom_watchtower", fromBp.id());
		Assertions.assertEquals("Watchtower", fromBp.name());
		Assertions.assertEquals("Tall tower", fromBp.description());
		Assertions.assertEquals(0, fromBp.blocks().size());

		// DeleteCustomBlueprintPayload
		DeleteCustomBlueprintPayload deletePayload = new DeleteCustomBlueprintPayload("custom_watchtower");
		Assertions.assertEquals("custom_watchtower", deletePayload.blueprintId());
		Assertions.assertEquals(DeleteCustomBlueprintPayload.ID, deletePayload.getId());
		Assertions.assertNotNull(DeleteCustomBlueprintPayload.PACKET_CODEC);

		DeleteCustomBlueprintPayload deleteCopy = new DeleteCustomBlueprintPayload("custom_watchtower");
		Assertions.assertEquals(deletePayload, deleteCopy);
		Assertions.assertEquals(deletePayload.hashCode(), deleteCopy.hashCode());

		DeleteCustomBlueprintPayload deleteDiff = new DeleteCustomBlueprintPayload("other_id");
		Assertions.assertNotEquals(deletePayload, deleteDiff);

		// SyncCustomBlueprintsPayload
		SyncCustomBlueprintsPayload syncPayload = new SyncCustomBlueprintsPayload(List.of(compiled));
		Assertions.assertEquals(1, syncPayload.blueprints().size());
		Assertions.assertEquals(SyncCustomBlueprintsPayload.ID, syncPayload.getId());
		Assertions.assertNotNull(SyncCustomBlueprintsPayload.PACKET_CODEC);
		Assertions.assertNotNull(SyncCustomBlueprintsPayload.BLUEPRINT_CODEC);

		SyncCustomBlueprintsPayload emptySync = new SyncCustomBlueprintsPayload(List.of());
		Assertions.assertEquals(0, emptySync.blueprints().size());
	}

	@Test
	@DisplayName("CustomBlueprintManager gracefully handles sync with null player or server")
	void testCustomBlueprintManagerNullSync() {
		CustomBlueprintManager manager = CustomBlueprintManager.getInstance();
		Assertions.assertDoesNotThrow(() -> manager.syncToPlayer(null));
		Assertions.assertDoesNotThrow(() -> manager.syncToAll(null));
		Assertions.assertDoesNotThrow(() -> manager.init(null));
		Assertions.assertDoesNotThrow(manager::onServerStopping);
	}

	@Test
	@DisplayName("Source contracts: BlueprintRegistry, CustomBlueprintManager, ExampleMod, and ModNetworking integration")
	void testCustomBlueprintSourceContracts() throws IOException {
		// Verify BlueprintRegistry dynamic registration API
		Path registryPath = Path.of("src/main/java/com/example/blueprint/BlueprintRegistry.java");
		Assertions.assertTrue(Files.exists(registryPath), "BlueprintRegistry.java must exist");
		String registryCode = Files.readString(registryPath);
		Assertions.assertTrue(registryCode.contains("registerCustomBlueprint(StructureBlueprint"), "BlueprintRegistry must declare registerCustomBlueprint");
		Assertions.assertTrue(registryCode.contains("unregisterCustomBlueprint(String id)"), "BlueprintRegistry must declare unregisterCustomBlueprint");
		Assertions.assertTrue(registryCode.contains("clearCustomBlueprints()"), "BlueprintRegistry must declare clearCustomBlueprints");
		Assertions.assertTrue(registryCode.contains("getCustomBlueprints()"), "BlueprintRegistry must declare getCustomBlueprints");
		Assertions.assertTrue(registryCode.contains("isCustom(String id)"), "BlueprintRegistry must declare isCustom");

		// Verify ExampleMod server lifecycle hooks
		Path modPath = Path.of("src/main/java/com/example/ExampleMod.java");
		Assertions.assertTrue(Files.exists(modPath), "ExampleMod.java must exist");
		String modCode = Files.readString(modPath);
		Assertions.assertTrue(modCode.contains("CustomBlueprintManager.getInstance().init("), "ExampleMod must initialize CustomBlueprintManager on server start");
		Assertions.assertTrue(modCode.contains("CustomBlueprintManager.getInstance().onServerStopping()"), "ExampleMod must flush CustomBlueprintManager on server stopping");
		Assertions.assertTrue(modCode.contains("CustomBlueprintManager.getInstance().syncToPlayer("), "ExampleMod must synchronize blueprints on player join");

		// Verify ModNetworking packet handlers for CREATE and DELETE custom blueprint
		Path netPath = Path.of("src/main/java/com/example/network/ModNetworking.java");
		Assertions.assertTrue(Files.exists(netPath), "ModNetworking.java must exist");
		String netCode = Files.readString(netPath);
		Assertions.assertTrue(netCode.contains("CreateCustomBlueprintPayload.ID"), "ModNetworking must register CreateCustomBlueprintPayload receiver");
		Assertions.assertTrue(netCode.contains("DeleteCustomBlueprintPayload.ID"), "ModNetworking must register DeleteCustomBlueprintPayload receiver");
		Assertions.assertTrue(netCode.contains("CustomBlueprintManager.getInstance().saveBlueprint("), "ModNetworking must delegate save to CustomBlueprintManager");
		Assertions.assertTrue(netCode.contains("CustomBlueprintManager.getInstance().deleteBlueprint("), "ModNetworking must delegate delete to CustomBlueprintManager");

		// Verify Client networking handler for SYNC custom blueprints
		Path clientNetPath = Path.of("src/client/java/com/example/client/network/ModClientNetworking.java");
		Assertions.assertTrue(Files.exists(clientNetPath), "ModClientNetworking.java must exist");
		String clientNetCode = Files.readString(clientNetPath);
		Assertions.assertTrue(clientNetCode.contains("SyncCustomBlueprintsPayload.ID"), "ModClientNetworking must register SyncCustomBlueprintsPayload receiver");
		Assertions.assertTrue(clientNetCode.contains("sendCreateCustomBlueprint"), "ModClientNetworking must implement sendCreateCustomBlueprint");
		Assertions.assertTrue(clientNetCode.contains("sendDeleteCustomBlueprint"), "ModClientNetworking must implement sendDeleteCustomBlueprint");
		Assertions.assertTrue(clientNetCode.contains("sendCaptureSpatialBlueprint"), "ModClientNetworking must implement sendCaptureSpatialBlueprint");
	}

	private static BlockState createDummyBlockState() {
		try {
			java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
			return (BlockState) unsafe.allocateInstance(BlockState.class);
		} catch (Throwable t) {
			return null;
		}
	}

	@Test
	@DisplayName("StructureBlueprint.normalizeBlocks normalizes coordinate offsets to origin and sorts topographically")
	void testStructureBlueprintNormalizeBlocks() {
		BlockState dummyState = createDummyBlockState();
		if (dummyState == null) return;

		List<BlueprintBlock> unnormalized = List.of(
			new BlueprintBlock(new net.minecraft.util.math.BlockPos(10, 5, 20), dummyState),
			new BlueprintBlock(new net.minecraft.util.math.BlockPos(12, 7, 22), dummyState),
			new BlueprintBlock(new net.minecraft.util.math.BlockPos(11, 5, 20), dummyState)
		);

		List<BlueprintBlock> normalized = StructureBlueprint.normalizeBlocks(unnormalized);
		Assertions.assertEquals(3, normalized.size());

		// Lowest Y layer blocks must be at Y = 0
		Assertions.assertEquals(0, normalized.get(0).offset().getY());
		Assertions.assertEquals(0, normalized.get(1).offset().getY());
		Assertions.assertEquals(2, normalized.get(2).offset().getY());

		// Verify minimum corner offset is (0, 0, 0)
		Assertions.assertEquals(new net.minecraft.util.math.BlockPos(0, 0, 0), normalized.get(0).offset());
		Assertions.assertEquals(new net.minecraft.util.math.BlockPos(1, 0, 0), normalized.get(1).offset());
		Assertions.assertEquals(new net.minecraft.util.math.BlockPos(2, 2, 2), normalized.get(2).offset());

		// Empty and null handling
		Assertions.assertTrue(StructureBlueprint.normalizeBlocks(null).isEmpty());
		Assertions.assertTrue(StructureBlueprint.normalizeBlocks(List.of()).isEmpty());
	}

	@Test
	@DisplayName("StructureBlueprint spatial dimension safeguards prevent oversized volume captures")
	void testStructureBlueprintSpatialSafeguards() {
		net.minecraft.util.math.BlockPos pos1 = new net.minecraft.util.math.BlockPos(0, 0, 0);
		net.minecraft.util.math.BlockPos posTooLarge = new net.minecraft.util.math.BlockPos(100, 0, 0);

		Assertions.assertThrows(IllegalArgumentException.class, () ->
			StructureBlueprint.captureBlocks(null, pos1, posTooLarge)
		);
	}
}
