package com.example.client.gui;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.network.DismissMinionPayload;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Custom Blueprint catalog prioritization,
 * GUI deletion confirmation flow, and minion dismissal confirmation options.
 */
public class CustomBlueprintCatalogGuiTest {

	@BeforeAll
	static void initBootstrap() {
		try {
			net.minecraft.SharedConstants.createGameVersion();
			net.minecraft.Bootstrap.initialize();
		} catch (Throwable ignored) {}
	}

	private StructureBlueprint customVilla;
	private StructureBlueprint customOutpost;

	@BeforeEach
	void setUp() {
		BlueprintRegistry.clearCustomBlueprints();

		customVilla = StructureBlueprint.builder("custom_villa", "Custom Roman Villa")
			.description("A luxurious custom estate.")
			.build();

		customOutpost = StructureBlueprint.builder("custom_outpost", "Custom Guard Outpost")
			.description("A defensive outpost.")
			.build();

		BlueprintRegistry.registerCustomBlueprint(customVilla);
		BlueprintRegistry.registerCustomBlueprint(customOutpost);
	}

	@AfterEach
	void tearDown() {
		BlueprintRegistry.clearCustomBlueprints();
	}

	@Test
	@DisplayName("Custom blueprints are ordered at the very beginning of BlueprintRegistry.getAll()")
	void testCustomBlueprintsOrderFirstInGetAll() {
		Collection<StructureBlueprint> all = BlueprintRegistry.getAll();
		List<StructureBlueprint> list = List.copyOf(all);

		Assertions.assertEquals(2, list.size());
		Assertions.assertEquals("custom_villa", list.get(0).getId());
		Assertions.assertEquals("custom_outpost", list.get(1).getId());
		Assertions.assertTrue(BlueprintRegistry.isCustom(list.get(0).getId()));
		Assertions.assertTrue(BlueprintRegistry.isCustom(list.get(1).getId()));
	}

	@Test
	@DisplayName("Custom blueprints are ordered at the very beginning of getActiveCatalog()")
	void testCustomBlueprintsOrderFirstInActiveCatalog() {
		List<StructureBlueprint> catalog = BlueprintRegistry.getActiveCatalog();

		Assertions.assertTrue(catalog.size() >= 2);
		Assertions.assertEquals("custom_villa", catalog.get(0).getId());
		Assertions.assertEquals("custom_outpost", catalog.get(1).getId());
	}

	@Test
	@DisplayName("Catalog cycling (getNext and getPrevious) cycles custom blueprints first")
	void testCatalogCyclingWithCustomPriority() {
		StructureBlueprint next = BlueprintRegistry.getNext("custom_villa");
		Assertions.assertEquals("custom_outpost", next.getId());

		StructureBlueprint prev = BlueprintRegistry.getPrevious("custom_outpost");
		Assertions.assertEquals("custom_villa", prev.getId());
	}

	@Test
	@DisplayName("CommandScepterScreen confirmation modal tracks DELETE_BLUEPRINT state")
	void testDeleteBlueprintConfirmationState() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());

		screen.requestDeleteBlueprintConfirmation(customVilla);
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.DELETE_BLUEPRINT, screen.getPendingConfirmation());
		Assertions.assertEquals(customVilla, screen.getPendingBlueprintToDelete());

		screen.cancelConfirmation();
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());
		Assertions.assertNull(screen.getPendingBlueprintToDelete());
	}

	@Test
	@DisplayName("Confirming custom blueprint deletion unregisters blueprint and returns to NONE confirmation")
	void testConfirmDeleteCustomBlueprint() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.requestDeleteBlueprintConfirmation(customVilla);
		Assertions.assertTrue(BlueprintRegistry.isCustom("custom_villa"));

		screen.confirmDeleteCustomBlueprint();
		Assertions.assertFalse(BlueprintRegistry.isCustom("custom_villa"));
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());
	}

	@Test
	@DisplayName("CommandScepterScreen tracks DISMISS_MINIONS confirmation state")
	void testDismissMinionsConfirmationState() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());

		// When thralls exist or are simulated, request dismissal triggers DISMISS_MINIONS
		screen.requestDismissMinionsConfirmation();
		// Screen with 0 nearby thralls does not open confirmation
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());
	}

	@Test
	@DisplayName("DismissMinionPayload TARGET_SELECTED (-2) correctly identifies selected target scope")
	void testDismissMinionPayloadTargetSelected() {
		DismissMinionPayload selectedPayload = new DismissMinionPayload(DismissMinionPayload.TARGET_SELECTED, false);
		Assertions.assertEquals(DismissMinionPayload.TARGET_SELECTED, selectedPayload.minionId());
		Assertions.assertTrue(selectedPayload.isTargetSelected());
		Assertions.assertFalse(selectedPayload.isTargetAll());

		DismissMinionPayload allPayload = new DismissMinionPayload(DismissMinionPayload.TARGET_ALL, true);
		Assertions.assertEquals(DismissMinionPayload.TARGET_ALL, allPayload.minionId());
		Assertions.assertFalse(allPayload.isTargetSelected());
		Assertions.assertTrue(allPayload.isTargetAll());
	}

	@Test
	@DisplayName("Confirming dismiss selected minions dispatches cleanly and cancels confirmation")
	void testConfirmDismissSelectedMinions() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.confirmDismissSelectedMinions();
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());
		Assertions.assertTrue(screen.isClosed());
	}

	@Test
	@DisplayName("Confirming dismiss all minions dispatches cleanly and cancels confirmation")
	void testConfirmDismissAllMinions() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.confirmDismissAllMinions();
		Assertions.assertEquals(CommandScepterScreen.ConfirmationType.NONE, screen.getPendingConfirmation());
		Assertions.assertTrue(screen.isClosed());
	}

	@Test
	@DisplayName("3D Voxel Studio is removed from CommandScepterScreen API")
	void testVoxelStudioRemovedFromCommandHub() {
		// Verify CustomBlueprintEditorScreen class does not exist on classpath
		Assertions.assertThrows(ClassNotFoundException.class, () -> {
			Class.forName("com.example.client.gui.CustomBlueprintEditorScreen");
		});

		// Verify CommandScepterScreen does not declare getStudioButton or openVoxelStudio
		for (java.lang.reflect.Method method : CommandScepterScreen.class.getDeclaredMethods()) {
			Assertions.assertNotEquals("getStudioButton", method.getName());
			Assertions.assertNotEquals("openVoxelStudio", method.getName());
		}
	}
}
