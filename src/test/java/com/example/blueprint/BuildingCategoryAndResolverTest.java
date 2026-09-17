package com.example.blueprint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating procedural architecture category specifications,
 * environment type classifications, and dynamic building resolution constants.
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
			Assertions.assertEquals(cat.getId(), cat.asString());
			Assertions.assertEquals(cat, BuildingCategory.byId(cat.getId()));
		}

		// Fallback
		Assertions.assertEquals(BuildingCategory.HOME, BuildingCategory.byId("invalid_cat"));
		Assertions.assertEquals(BuildingCategory.HOME, BuildingCategory.byId(null));
	}

	@Test
	@DisplayName("BuildingCategory size presets define standard size indices")
	void testSizePresets() {
		Assertions.assertEquals(0, BuildingCategory.SIZE_SMALL);
		Assertions.assertEquals(1, BuildingCategory.SIZE_MEDIUM);
		Assertions.assertEquals(2, BuildingCategory.SIZE_GRAND);
		Assertions.assertEquals(3, BuildingCategory.SIZE_RANDOM);
	}

	@Test
	@DisplayName("BiomePalette defines all seven expected environmental archetypes")
	void testEnvironmentTypes() {
		BiomePalette.EnvironmentType[] envs = BiomePalette.EnvironmentType.values();
		Assertions.assertEquals(7, envs.length, "Should contain 7 environment types");

		Assertions.assertNotNull(BiomePalette.EnvironmentType.PLAINS_FOREST);
		Assertions.assertNotNull(BiomePalette.EnvironmentType.DESERT_BADLANDS);
		Assertions.assertNotNull(BiomePalette.EnvironmentType.TAIGA_SNOWY);
		Assertions.assertNotNull(BiomePalette.EnvironmentType.JUNGLE_SWAMP);
		Assertions.assertNotNull(BiomePalette.EnvironmentType.SUBTERRANEAN_CAVE);
		Assertions.assertNotNull(BiomePalette.EnvironmentType.NETHER);
		Assertions.assertNotNull(BiomePalette.EnvironmentType.END);
	}

	@Test
	@DisplayName("DynamicBuildingResolver foundation depth limit is configured to 8 blocks")
	void testFoundationDepthLimit() throws Exception {
		Field depthField = DynamicBuildingResolver.class.getDeclaredField("MAX_FOUNDATION_DEPTH");
		depthField.setAccessible(true);
		int maxDepth = depthField.getInt(null);
		Assertions.assertEquals(8, maxDepth, "Foundation slope snapping depth should be exactly 8 blocks");
	}

	@Test
	@DisplayName("DynamicBuildingResolver evaluateNoise produces deterministic output in range [0, 1]")
	void testNoiseDeterminism() throws Exception {
		Method noiseMethod = DynamicBuildingResolver.class.getDeclaredMethod("evaluateNoise", int.class, int.class, int.class, long.class);
		noiseMethod.setAccessible(true);

		float val1 = (float) noiseMethod.invoke(null, 10, 5, 20, 12345L);
		float val2 = (float) noiseMethod.invoke(null, 10, 5, 20, 12345L);
		Assertions.assertEquals(val1, val2, 0.00001F, "Noise must be 100% deterministic for identical coordinates and seed");

		Assertions.assertTrue(val1 >= 0.0F && val1 <= 1.0F, "Noise value must lie within [0.0, 1.0]");

		float val3 = (float) noiseMethod.invoke(null, 11, 5, 20, 12345L);
		Assertions.assertNotEquals(val1, val3, "Noise values must vary across spatial coordinates");
	}
}
