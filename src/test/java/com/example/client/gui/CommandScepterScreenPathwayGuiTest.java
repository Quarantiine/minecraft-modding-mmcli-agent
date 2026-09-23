package com.example.client.gui;

import com.example.component.CommandMode;
import com.example.patrol.PatrolRoute;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit test suite validating layout geometry, widget visibility rules,
 * and state management for Pathway / Waypoint mode in {@link CommandScepterScreen}.
 */
public class CommandScepterScreenPathwayGuiTest {

	@Test
	@DisplayName("CommandScepterScreen tracks active patrol route ID")
	void testActivePatrolRouteSelection() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertEquals(0, screen.getActivePatrolRouteId(), "Default active patrol route must be 0");

		for (int i = 0; i < PatrolRoute.CHANNEL_COUNT; i++) {
			screen.selectActiveRoute(i);
			Assertions.assertEquals(i, screen.getActivePatrolRouteId(), "selectActiveRoute should switch to route " + i);
		}
	}

	@Test
	@DisplayName("Source contract: Pathway route selection triggers network synchronization to server")
	void testSelectActiveRouteNetworkSynchronizationContract() throws IOException {
		Path screenPath = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		String screenContent = Files.readString(screenPath);

		// selectActiveRoute must update activePatrolRouteId, update stack, call syncToServer(false), and refresh labels
		Assertions.assertTrue(
			screenContent.contains("public void selectActiveRoute(int routeId) {\n\t\tthis.activePatrolRouteId = routeId;"),
			"selectActiveRoute must store routeId into activePatrolRouteId"
		);
		Assertions.assertTrue(
			screenContent.contains("CommandScepterItem.setActivePatrolRoute(this.scepterStack, routeId);"),
			"selectActiveRoute must update scepterStack active patrol route"
		);
		Assertions.assertTrue(
			screenContent.contains("syncToServer(false);"),
			"selectActiveRoute must invoke syncToServer(false)"
		);

		// syncToServer must transmit activePatrolRouteId
		Assertions.assertTrue(
			screenContent.contains("this.activePatrolRouteId,"),
			"syncToServer must pass this.activePatrolRouteId to ModClientNetworking.sendUpdateScepter"
		);

		// close must invoke syncToServer(false)
		Assertions.assertTrue(
			screenContent.contains("public void close() {\n\t\tthis.closed = true;\n\t\tsyncToServer(false);"),
			"close() must invoke syncToServer(false) to guarantee client-server scepter synchronization on GUI dismissal"
		);
	}

	@Test
	@DisplayName("Source contract: ModNetworking persists activePatrolRoute from UpdateScepterPayload")
	void testModNetworkingPersistsActivePatrolRouteContract() throws IOException {
		Path networkingPath = Path.of("src/main/java/com/example/network/ModNetworking.java");
		String networkingContent = Files.readString(networkingPath);

		Assertions.assertTrue(
			networkingContent.contains("int activePatrolRoute = payload.activePatrolRoute();"),
			"handleUpdateScepter must read activePatrolRoute from payload"
		);
		Assertions.assertTrue(
			networkingContent.contains("CommandScepterItem.setActivePatrolRoute(scepterStack, activePatrolRoute);"),
			"handleUpdateScepter must store activePatrolRoute onto the server scepter ItemStack"
		);
	}

	@Test
	@DisplayName("Source contract: Selecting PATHWAY mode resets activePatrolRouteId to Route 1 (0)")
	void testSelectingPathwayModeDefaultsRoute1() throws IOException {
		Path screenPath = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		String screenContent = Files.readString(screenPath);

		Assertions.assertTrue(
			screenContent.contains("if (mode == CommandMode.PATHWAY) {\n\t\t\tthis.activePatrolRouteId = 0;"),
			"selectMode must reset activePatrolRouteId to 0 when switching to PATHWAY mode"
		);
	}

	@Test
	@DisplayName("Source contract: CommandScepterScreen enforces pathway visibility and prevents blueprint overlap")
	void testPathwayVisibilitySourceContract() throws IOException {
		Path screenPath = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		Assertions.assertTrue(Files.exists(screenPath), "CommandScepterScreen.java must exist");
		String screenContent = Files.readString(screenPath);

		// 1. updateBlueprintButtons must guard against PATHWAY mode
		Assertions.assertTrue(
			screenContent.contains("boolean isPathway = this.selectedMode == CommandMode.PATHWAY;"),
			"updateBlueprintButtons must check if selectedMode is PATHWAY"
		);
		Assertions.assertTrue(
			screenContent.contains("btn.visible = false;"),
			"updateBlueprintButtons must hide blueprint buttons when in PATHWAY mode"
		);

		// 2. updateControlsVisibility must synchronize pathwayWidgets with isPathway
		Assertions.assertTrue(
			screenContent.contains("btn.visible = isPathway;"),
			"updateControlsVisibility must set pathwayWidgets visible only when isPathway is true"
		);

		// 3. refreshButtonLabels must call updateControlsVisibility
		Assertions.assertTrue(
			screenContent.contains("updateControlsVisibility();"),
			"refreshButtonLabels must enforce updateControlsVisibility"
		);
	}

	@Test
	@DisplayName("Source contract: Pathway row dimensions fit cleanly within 160px panel with no overlapping")
	void testPathwayRowDimensionsFitPanel() throws IOException {
		Path screenPath = Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		String screenContent = Files.readString(screenPath);

		// Pathway page size 4
		Assertions.assertTrue(
			screenContent.contains("public static final int PATHWAY_PAGE_SIZE = 4;"),
			"CommandScepterScreen must define PATHWAY_PAGE_SIZE = 4"
		);

		// Channel select button: 70px width starting at startX + 165 -> ends at 235
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 165, rowY, 70, 20)"),
			"selectBtn dimensions must be 70px wide at startX + 165"
		);

		// Mode toggle button: 26px width starting at startX + 237 -> ends at 263
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 237, rowY, 26, 20)"),
			"modeBtn dimensions must be 26px wide at startX + 237"
		);

		// Edit button: 18px width starting at startX + 265 -> ends at 283
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 265, rowY, 18, 20)"),
			"editBtn dimensions must be 18px wide at startX + 265"
		);

		// Clear button: 18px width starting at startX + 285 -> ends at 303
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 285, rowY, 18, 20)"),
			"clearBtn dimensions must be 18px wide at startX + 285"
		);

		// Delete button: 20px width starting at startX + 305 -> ends at 325
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 305, rowY, 20, 20)"),
			"delBtn dimensions must be 20px wide at startX + 305"
		);

		// Mode button uses clean text labels "Loop" and "Ping"
		Assertions.assertTrue(
			screenContent.contains("mode == com.example.patrol.PatrolRoute.PatrolMode.LOOP ? \"§bLoop\" : \"§6Ping\""),
			"Mode button must display clean text labels 'Loop' and 'Ping'"
		);
	}
}
