package com.example.blueprint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating ArchitectureStyle definitions, lookup fallbacks, and formatting.
 */
public class ArchitectureStyleTest {

	@Test
	@DisplayName("ArchitectureStyle enum defines all expected architectural styles")
	void testEnumConstants() {
		ArchitectureStyle[] styles = ArchitectureStyle.values();
		Assertions.assertEquals(4, styles.length, "Should contain exactly 4 architecture styles");

		Assertions.assertEquals(ArchitectureStyle.BIOME_NATIVE, ArchitectureStyle.valueOf("BIOME_NATIVE"));
		Assertions.assertEquals(ArchitectureStyle.FORTRESS_STONE, ArchitectureStyle.valueOf("FORTRESS_STONE"));
		Assertions.assertEquals(ArchitectureStyle.FRONTIER_TIMBER, ArchitectureStyle.valueOf("FRONTIER_TIMBER"));
		Assertions.assertEquals(ArchitectureStyle.ARCANE_NETHER, ArchitectureStyle.valueOf("ARCANE_NETHER"));
	}

	@Test
	@DisplayName("ArchitectureStyle byId resolves valid identifiers case-insensitively and falls back safely")
	void testByIdLookup() {
		Assertions.assertEquals(ArchitectureStyle.BIOME_NATIVE, ArchitectureStyle.byId("biome_native"));
		Assertions.assertEquals(ArchitectureStyle.FORTRESS_STONE, ArchitectureStyle.byId("FORTRESS_STONE"));
		Assertions.assertEquals(ArchitectureStyle.FRONTIER_TIMBER, ArchitectureStyle.byId("frontier_timber"));
		Assertions.assertEquals(ArchitectureStyle.ARCANE_NETHER, ArchitectureStyle.byId("Arcane_Nether"));

		// Fallbacks
		Assertions.assertEquals(ArchitectureStyle.BIOME_NATIVE, ArchitectureStyle.byId(null));
		Assertions.assertEquals(ArchitectureStyle.BIOME_NATIVE, ArchitectureStyle.byId("unknown_style"));
	}

	@Test
	@DisplayName("ArchitectureStyle display metadata and formatting are non-null and descriptive")
	void testDisplayMetadata() {
		for (ArchitectureStyle style : ArchitectureStyle.values()) {
			Assertions.assertNotNull(style.getId(), "Style ID must not be null");
			Assertions.assertNotNull(style.getDisplayName(), "Display name must not be null");
			Assertions.assertNotNull(style.getColor(), "Color formatting must not be null");
			Assertions.assertNotNull(style.getDescription(), "Description must not be null");
			Assertions.assertNotNull(style.getFormattedName(), "Formatted name must not be null");
			Assertions.assertEquals(style.getId(), style.asString(), "asString must match getId");
		}
	}
}
