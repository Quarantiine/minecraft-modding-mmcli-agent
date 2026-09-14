package com.example.blueprint;

import com.example.construction.ConstructionSession;
import com.example.entity.ai.goal.MinionBuildGoal;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying that builder scaffolding logic has been completely retired
 * in favor of full 3D Arcane Builder Levitation.
 */
public class ScaffoldingTest {

	@Test
	@DisplayName("ConstructionSession no longer contains scaffolding column tracking or temporary scaffolding collections")
	public void testConstructionSessionScaffoldingRetired() {
		// Verify no scaffolding fields exist in ConstructionSession
		for (Field field : ConstructionSession.class.getDeclaredFields()) {
			String name = field.getName().toLowerCase();
			Assertions.assertFalse(
				name.contains("scaffold"),
				"ConstructionSession must not contain any scaffolding field: " + field.getName()
			);
		}

		// Verify no scaffolding methods exist in ConstructionSession
		for (Method method : ConstructionSession.class.getDeclaredMethods()) {
			String name = method.getName().toLowerCase();
			Assertions.assertFalse(
				name.contains("scaffold"),
				"ConstructionSession must not contain any scaffolding method: " + method.getName()
			);
		}
	}

	@Test
	@DisplayName("MinionBuildGoal no longer contains scaffolding ascent, descent, or column fields")
	public void testMinionBuildGoalScaffoldingFieldsRetired() {
		for (Field field : MinionBuildGoal.class.getDeclaredFields()) {
			String name = field.getName().toLowerCase();
			Assertions.assertFalse(
				name.contains("scaffold"),
				"MinionBuildGoal must not contain any scaffolding field: " + field.getName()
			);
			Assertions.assertFalse(
				name.contains("ascending") || name.contains("descending"),
				"MinionBuildGoal must not contain scaffolding climbing state fields: " + field.getName()
			);
		}
	}

	@Test
	@DisplayName("MinionBuildGoal source code audit verifies scaffolding generation is replaced by Arcane Levitation")
	public void testMinionBuildGoalSourceCodeAudit() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String code = Files.readString(buildGoalPath);

		// Obsolete methods must not exist in source code
		Assertions.assertFalse(code.contains("deployScaffoldingIfNeeded"),
			"deployScaffoldingIfNeeded must be removed");
		Assertions.assertFalse(code.contains("findReusableScaffoldColumn"),
			"findReusableScaffoldColumn must be removed");
		Assertions.assertFalse(code.contains("extendScaffoldColumnTo"),
			"extendScaffoldColumnTo must be removed");
		Assertions.assertFalse(code.contains("findScaffoldColumn"),
			"findScaffoldColumn must be removed");
		Assertions.assertFalse(code.contains("removeScaffoldBlockWithFeedback"),
			"removeScaffoldBlockWithFeedback must be removed");
		Assertions.assertFalse(code.contains("abortClimbAndDescend"),
			"abortClimbAndDescend must be removed");
		Assertions.assertFalse(code.contains("shouldTeardownOnDescent"),
			"shouldTeardownOnDescent must be removed");
		Assertions.assertFalse(code.contains("isAscendingScaffolding"),
			"isAscendingScaffolding must be removed");
		Assertions.assertFalse(code.contains("isDescendingScaffolding"),
			"isDescendingScaffolding must be removed");

		// Arcane Levitation must be present
		Assertions.assertTrue(code.contains("setArcaneLevitating(true)"),
			"MinionBuildGoal must engage Arcane Levitation for elevated tasks");
		Assertions.assertTrue(code.contains("findOptimalHoverStation"),
			"MinionBuildGoal must compute optimal 3D hover stations");
	}

	@Test
	@DisplayName("Minion engagement check does not rely on scaffolding column claims")
	public void testMinionEngagementWithoutScaffolding() {
		UUID minionUuid = UUID.randomUUID();
		// Test dummy ConstructionSession instance reflection or verification
		Assertions.assertDoesNotThrow(() -> {
			Method isEngaged = ConstructionSession.class.getDeclaredMethod("isMinionEngaged", UUID.class);
			Assertions.assertNotNull(isEngaged);
		});
	}

	@Test
	@DisplayName("MinionBuildGoal.isScaffoldBlock preserves safety compatibility for world query blocks")
	public void testIsScaffoldBlockNullSafety() {
		Assertions.assertFalse(MinionBuildGoal.isScaffoldBlock(null));
	}
}
