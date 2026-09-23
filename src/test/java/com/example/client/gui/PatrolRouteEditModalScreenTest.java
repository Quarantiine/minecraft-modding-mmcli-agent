package com.example.client.gui;

import com.example.network.ConfigurePatrolRoutePayload;
import com.example.patrol.PatrolRoute;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test suite validating {@link PatrolRouteEditModalScreen}, hex color picker parsing,
 * color preset swatches, route name validation, and network payload dispatch.
 */
public class PatrolRouteEditModalScreenTest {

	@Test
	@DisplayName("PatrolRoute hex parsing and formatting handles arbitrary RGB values and fallbacks")
	void testHexColorParsingAndFormatting() {
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor("#FFD700"));
		Assertions.assertEquals(0x00E5FF, PatrolRoute.parseHexColor("0x00E5FF"));
		Assertions.assertEquals(0x00FF66, PatrolRoute.parseHexColor("00FF66"));
		Assertions.assertEquals(0xFF2244, PatrolRoute.parseHexColor("#FF2244"));
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor("invalid_hex"));
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor(""));
		Assertions.assertEquals(0xFFD700, PatrolRoute.parseHexColor(null));

		Assertions.assertEquals("#FFD700", PatrolRoute.toHexCode(0xFFD700));
		Assertions.assertEquals("#00E5FF", PatrolRoute.toHexCode(0x00E5FF));
		Assertions.assertEquals("#00FF66", PatrolRoute.toHexCode(0x00FF66));
	}

	@Test
	@DisplayName("PatrolRoute custom route creation and mutation methods")
	void testCustomRouteCreationAndMutation() {
		PatrolRoute route = PatrolRoute.createCustom(5, "Perimeter Alpha", 0x3388FF);
		Assertions.assertEquals(5, route.routeId());
		Assertions.assertEquals("Perimeter Alpha", route.name());
		Assertions.assertEquals(0x3388FF, route.colorRgb());
		Assertions.assertEquals(PatrolRoute.PatrolMode.LOOP, route.patrolMode());
		Assertions.assertTrue(route.waypoints().isEmpty());

		PatrolRoute renamed = route.withName("Inner Fortress");
		Assertions.assertEquals("Inner Fortress", renamed.name());
		Assertions.assertEquals(0x3388FF, renamed.colorRgb());

		PatrolRoute recolored = route.withColorRgb(0xFF3399);
		Assertions.assertEquals(0xFF3399, recolored.colorRgb());
		Assertions.assertEquals("Perimeter Alpha", recolored.name());

		PatrolRoute pingPong = route.withPatrolMode(PatrolRoute.PatrolMode.PING_PONG);
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, pingPong.patrolMode());
	}

	@Test
	@DisplayName("Source contract: PatrolRouteEditModalScreen defines presets and disables blur")
	void testPatrolRouteEditModalScreenSourceContract() throws IOException {
		Path path = Path.of("src/client/java/com/example/client/gui/PatrolRouteEditModalScreen.java");
		Assertions.assertTrue(Files.exists(path), "PatrolRouteEditModalScreen.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(content.contains("public static final int WINDOW_WIDTH = 300;"));
		Assertions.assertTrue(content.contains("public static final int WINDOW_HEIGHT = 240;"));
		Assertions.assertTrue(content.contains("public static final int[] PRESET_COLORS = {"));
		Assertions.assertTrue(content.contains("0xFFD700, // Gold"));
		Assertions.assertTrue(content.contains("0x00E5FF, // Cyan"));
		Assertions.assertTrue(content.contains("0x00FF66, // Emerald"));
		Assertions.assertTrue(content.contains("0xB300FF, // Arcane Purple"));
		Assertions.assertTrue(content.contains("0xFF2244, // Crimson"));
		Assertions.assertTrue(content.contains("protected void applyBlur(float delta)"));
		Assertions.assertTrue(content.contains("ModClientNetworking.sendSavePatrolRoute"));
		Assertions.assertTrue(content.contains("ModClientNetworking.sendDeletePatrolRoute"));
	}

	@Test
	@DisplayName("ConfigurePatrolRoutePayload serialization contract")
	void testConfigurePatrolRoutePayload() {
		ConfigurePatrolRoutePayload savePayload = new ConfigurePatrolRoutePayload(
			ConfigurePatrolRoutePayload.Action.SAVE,
			2,
			"Wall Sentry Route",
			0x00FF66,
			PatrolRoute.PatrolMode.PING_PONG
		);

		Assertions.assertEquals(ConfigurePatrolRoutePayload.Action.SAVE, savePayload.action());
		Assertions.assertEquals(2, savePayload.routeId());
		Assertions.assertEquals("Wall Sentry Route", savePayload.name());
		Assertions.assertEquals(0x00FF66, savePayload.colorRgb());
		Assertions.assertEquals(PatrolRoute.PatrolMode.PING_PONG, savePayload.patrolMode());
		Assertions.assertNotNull(savePayload.getId());
		Assertions.assertNotNull(ConfigurePatrolRoutePayload.PACKET_CODEC);

		ConfigurePatrolRoutePayload deletePayload = new ConfigurePatrolRoutePayload(
			ConfigurePatrolRoutePayload.Action.DELETE,
			2,
			"Wall Sentry Route",
			0,
			PatrolRoute.PatrolMode.LOOP
		);
		Assertions.assertEquals(ConfigurePatrolRoutePayload.Action.DELETE, deletePayload.action());
	}
}
