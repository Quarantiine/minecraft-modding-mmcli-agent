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

		// Channel select button: 94px width starting at startX + 165 -> ends at 259
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 165, rowY, 94, 20)"),
			"selectBtn dimensions must be 94px wide at startX + 165"
		);

		// Mode toggle button: 38px width starting at startX + 262 -> ends at 300
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 262, rowY, 38, 20)"),
			"modeBtn dimensions must be 38px wide at startX + 262"
		);

		// Clear button: 22px width starting at startX + 303 -> ends at 325
		Assertions.assertTrue(
			screenContent.contains(".dimensions(startX + 303, rowY, 22, 20)"),
			"clearBtn dimensions must be 22px wide at startX + 303"
		);

		// Route button labels use concise formatting without long names: "Route X [N]"
		Assertions.assertTrue(
			screenContent.contains("\"Route \" + (routeId + 1) + \" §8[\" + waypointCount + \"]\""),
			"Route channel button text must use concise format to avoid overflowing"
		);

		// Mode button uses clean text labels "Loop" and "Ping"
		Assertions.assertTrue(
			screenContent.contains("mode == com.example.patrol.PatrolRoute.PatrolMode.LOOP ? \"§bLoop\" : \"§6Ping\""),
			"Mode button must display clean text labels 'Loop' and 'Ping'"
		);
	}
}
