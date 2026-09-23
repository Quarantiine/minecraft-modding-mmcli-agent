package com.example.client.gui;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests verifying that background blur post-processing shader execution is disabled
 * across all custom Sovereign GUI screens by overriding `protected void applyBlur(float delta)`
 * as an empty method.
 */
public class GuiBlurRemovalTest {

	private static final Class<?>[] TARGET_SCREENS = new Class<?>[] {
		BlueprintCaptureModalScreen.class,
		CommandScepterScreen.class,
		MinionScreen.class
	};

	@ParameterizedTest(name = "Screen {0} declares overridden applyBlur(float) method")
	@ValueSource(classes = {
		BlueprintCaptureModalScreen.class,
		CommandScepterScreen.class,
		MinionScreen.class
	})
	@DisplayName("Screen declares applyBlur(float) override directly on class")
	void testApplyBlurMethodDeclaredOnScreen(Class<?> screenClass) throws NoSuchMethodException {
		Method declaredMethod = screenClass.getDeclaredMethod("applyBlur", float.class);
		Assertions.assertNotNull(declaredMethod, () -> screenClass.getSimpleName() + " must declare applyBlur(float)");

		int modifiers = declaredMethod.getModifiers();
		Assertions.assertTrue(
			Modifier.isProtected(modifiers) || Modifier.isPublic(modifiers),
			() -> screenClass.getSimpleName() + ".applyBlur must be protected or public"
		);
		Assertions.assertEquals(
			void.class,
			declaredMethod.getReturnType(),
			() -> screenClass.getSimpleName() + ".applyBlur must return void"
		);
	}

	@Test
	@DisplayName("Invoking overridden applyBlur on instantiated screens executes safely as a no-op")
	void testApplyBlurInvocationIsNoOp() throws Exception {
		CommandScepterScreen scepterScreen = new CommandScepterScreen();
		Method scepterApplyBlur = CommandScepterScreen.class.getDeclaredMethod("applyBlur", float.class);
		scepterApplyBlur.setAccessible(true);
		Assertions.assertDoesNotThrow(() -> scepterApplyBlur.invoke(scepterScreen, 1.0f));
		Assertions.assertDoesNotThrow(() -> scepterApplyBlur.invoke(scepterScreen, 0.0f));

		BlueprintCaptureModalScreen modalScreen = new BlueprintCaptureModalScreen();
		Method modalApplyBlur = BlueprintCaptureModalScreen.class.getDeclaredMethod("applyBlur", float.class);
		modalApplyBlur.setAccessible(true);
		Assertions.assertDoesNotThrow(() -> modalApplyBlur.invoke(modalScreen, 1.0f));
		Assertions.assertDoesNotThrow(() -> modalApplyBlur.invoke(modalScreen, 0.0f));
	}

	@Test
	@DisplayName("All active GUI screens implement applyBlur override")
	void testAllScreensCovered() {
		Assertions.assertEquals(3, TARGET_SCREENS.length);
		for (Class<?> screenClass : TARGET_SCREENS) {
			boolean found = false;
			for (Method method : screenClass.getDeclaredMethods()) {
				if (method.getName().equals("applyBlur") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == float.class) {
					found = true;
					break;
				}
			}
			Assertions.assertTrue(found, "Expected " + screenClass.getSimpleName() + " to declare applyBlur(float)");
		}
	}
}
