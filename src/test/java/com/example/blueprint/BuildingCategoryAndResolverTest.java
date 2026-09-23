package com.example.blueprint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating blueprint category classifications, formatting, and fallbacks.
 */
public class BuildingCategoryAndResolverTest {

	@Test
	@DisplayName("All 6 BuildingCategory definitions exist and resolve by id")
	void testCategoryDefinitions() {
		BuildingCategory[] categories = BuildingCategory.values();
		Assertions.assertEquals(6, categories.length, "Should contain exactly 6 building categories");

		for (BuildingCategory cat : categories) {
			Assertions.assertNotNull(cat.getId());
			Assertions.assertNotNull(cat.getDisplayName());
			Assertions.assertNotNull(cat.getDescription());
			Assertions.assertNotNull(cat.getColor());
			Assertions.assertNotNull(cat.getFormattedText());
			Assertions.assertEquals(cat.getId(), cat.asString());
			Assertions.assertEquals(cat, BuildingCategory.byId(cat.getId()));
		}

		// Fallback
		Assertions.assertEquals(BuildingCategory.HOME, BuildingCategory.byId("invalid_cat"));
		Assertions.assertEquals(BuildingCategory.HOME, BuildingCategory.byId(null));
	}
}
