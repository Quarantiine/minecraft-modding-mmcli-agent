package com.example.client.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/**
 * Comprehensive unit test suite validating the Shift-to-close mechanism, open-state guard,
 * zero-latency release transitions, GLFW key repeat absorption, multi-key dismissals
 * (Shift, 'V', 'E', 'Esc'), and state lifecycle invariants in {@link CommandScepterScreen}.
 */
public class CommandScepterScreenCloseTest {

	@Test
	@DisplayName("Opening screen while holding Shift suppresses immediate closure and absorbs Left Shift key repeats")
	void testShiftHeldOnOpenSuppressesImmediateClose() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true);

		Assertions.assertTrue(screen.isShiftHeldOnOpen(), "shiftHeldOnOpen guard should be armed upon opening with Shift");
		Assertions.assertFalse(screen.isClosed(), "Screen must start open");

		// Initial Left Shift press event while player's finger is physically down from sneak-opening
		boolean handledLeft = screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
		Assertions.assertTrue(handledLeft, "Shift press event should be consumed by screen");
		Assertions.assertFalse(screen.isClosed(), "Screen must NOT close on initial held Shift press");

		// GLFW repeat events generated while holding Shift down
		boolean repeatLeft = screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
		Assertions.assertTrue(repeatLeft, "Shift repeat event should be consumed");
		Assertions.assertFalse(screen.isClosed(), "Screen must NOT close on repeated Shift press while guard is active");
	}

	@Test
	@DisplayName("Opening screen while holding Shift absorbs Right Shift key repeats")
	void testRightShiftHeldOnOpenSuppressesImmediateClose() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true);

		boolean handledRight = screen.keyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0);
		Assertions.assertTrue(handledRight, "Right Shift event should be consumed");
		Assertions.assertFalse(screen.isClosed(), "Screen must NOT close on Right Shift press while guard is active");

		boolean repeatRight = screen.keyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0);
		Assertions.assertTrue(repeatRight, "Right Shift repeat event should be consumed");
		Assertions.assertFalse(screen.isClosed(), "Screen must NOT close on repeated Right Shift press while guard is active");
	}

	@Test
	@DisplayName("Releasing Left Shift clears open-state guard and subsequent Left Shift press dismisses screen")
	void testReleaseTransitionEnablesShiftClose() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true);

		// Player physically releases Left Shift
		screen.keyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
		Assertions.assertFalse(screen.isShiftHeldOnOpen(), "shiftHeldOnOpen must reset to false upon Shift release");
		Assertions.assertFalse(screen.isClosed(), "Screen should remain open immediately after Shift release");

		// Subsequent Left Shift press closes the GUI immediately
		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
		Assertions.assertTrue(handled, "Shift press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close on subsequent Shift press once open guard is disarmed");
	}

	@Test
	@DisplayName("Releasing Right Shift clears open-state guard and subsequent Right Shift press dismisses screen")
	void testRightShiftReleaseTransition() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true);

		// Player physically releases Right Shift
		screen.keyReleased(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0);
		Assertions.assertFalse(screen.isShiftHeldOnOpen(), "shiftHeldOnOpen must reset to false upon Right Shift release");
		Assertions.assertFalse(screen.isClosed(), "Screen should remain open after Right Shift release");

		// Subsequent Right Shift press closes the GUI
		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0);
		Assertions.assertTrue(handled, "Right Shift press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close on subsequent Right Shift press");
	}

	@Test
	@DisplayName("Opening screen without Shift allows immediate closure on first Left Shift press")
	void testOpenWithoutShiftClosesImmediatelyOnShiftPress() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(false);

		Assertions.assertFalse(screen.isShiftHeldOnOpen(), "shiftHeldOnOpen guard should be unarmed");
		Assertions.assertFalse(screen.isClosed(), "Screen should start open");

		// Pressing Left Shift immediately closes the screen
		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
		Assertions.assertTrue(handled, "Left Shift press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close immediately on Left Shift press when opened without Shift");
	}

	@Test
	@DisplayName("Opening screen without Shift allows immediate closure on first Right Shift press")
	void testOpenWithoutShiftClosesImmediatelyOnRightShiftPress() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(false);

		// Pressing Right Shift immediately closes the screen
		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0);
		Assertions.assertTrue(handled, "Right Shift press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close immediately on Right Shift press when opened without Shift");
	}

	@Test
	@DisplayName("Pressing Command Hub hotkey 'V' closes screen immediately when shift guard is disarmed")
	void testCommandHubKeyDismissalWhenShiftDisarmed() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(false);

		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
		Assertions.assertTrue(handled, "'V' key press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close immediately on 'V' toggle press");
	}

	@Test
	@DisplayName("Pressing Command Hub hotkey 'V' closes screen immediately even when shift guard is active")
	void testCommandHubKeyDismissalWhenShiftArmed() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true); // Shift guard armed

		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
		Assertions.assertTrue(handled, "'V' key press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close immediately on 'V' toggle press even if shift was held");
	}

	@Test
	@DisplayName("Pressing Inventory hotkey 'E' closes screen immediately when shift guard is disarmed")
	void testInventoryKeyDismissalWhenShiftDisarmed() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(false);

		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
		Assertions.assertTrue(handled, "'E' key press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close immediately on 'E' inventory key press");
	}

	@Test
	@DisplayName("Pressing Inventory hotkey 'E' closes screen immediately even when shift guard is active")
	void testInventoryKeyDismissalWhenShiftArmed() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true); // Shift guard armed

		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
		Assertions.assertTrue(handled, "'E' key press should be handled");
		Assertions.assertTrue(screen.isClosed(), "Screen must close immediately on 'E' inventory key press even if shift was held");
	}

	@Test
	@DisplayName("Pressing Escape key closes screen via vanilla screen delegation")
	void testEscapeKeyDismissal() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true); // Even if shift guard was armed

		boolean handled = screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
		Assertions.assertTrue(handled, "Escape key press should be handled by super.keyPressed");
		Assertions.assertTrue(screen.isClosed(), "Screen must close on Escape key press");
	}

	@Test
	@DisplayName("Non-dismissal keys do not close the screen")
	void testNonClosingKeysDoNotDismiss() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(false);

		int[] nonClosingKeys = new int[] {
			GLFW.GLFW_KEY_SPACE,
			GLFW.GLFW_KEY_ENTER,
			GLFW.GLFW_KEY_W,
			GLFW.GLFW_KEY_A,
			GLFW.GLFW_KEY_S,
			GLFW.GLFW_KEY_D,
			GLFW.GLFW_KEY_B,
			GLFW.GLFW_KEY_X,
			GLFW.GLFW_KEY_1,
			GLFW.GLFW_KEY_F,
			GLFW.GLFW_KEY_C,
			GLFW.GLFW_KEY_R
		};

		for (int key : nonClosingKeys) {
			screen.keyPressed(key, 0, 0);
			Assertions.assertFalse(screen.isClosed(), "Key " + key + " should not dismiss the screen");
		}
	}

	@Test
	@DisplayName("Zero-latency tick fallback clears shiftHeldOnOpen when physical shift is not down")
	void testZeroLatencyTransitionInTick() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(true);

		Assertions.assertTrue(screen.isShiftHeldOnOpen(), "Guard should start armed");

		// In headless environment, isShiftOrSneakDown() returns false, so tick() must disarm the guard
		screen.tick();
		Assertions.assertFalse(screen.isShiftHeldOnOpen(), "tick() should clear shiftHeldOnOpen when shift is physically released");

		// Now Shift closes the screen
		screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
		Assertions.assertTrue(screen.isClosed(), "Screen must close on Shift press once cleared by tick()");
	}

	@Test
	@DisplayName("Open-state guard initialization flag prevents redundant re-arming")
	void testInitializedOpenStatePreservesGuard() {
		CommandScepterScreen screen = new CommandScepterScreen();
		screen.setShiftHeldOnOpen(false); // Disarmed guard
		Assertions.assertTrue(screen.isInitializedOpenState(), "initializedOpenState should be latched");
		Assertions.assertFalse(screen.isShiftHeldOnOpen(), "shiftHeldOnOpen should be false");

		// When initializedOpenState is latched, re-running initialization logic preserves unarmed state
		if (!screen.isInitializedOpenState()) {
			screen.setShiftHeldOnOpen(true);
		}
		Assertions.assertFalse(screen.isShiftHeldOnOpen(), "shiftHeldOnOpen must remain false when initializedOpenState is already latched");
	}

	@Test
	@DisplayName("Validate key identification helper functions")
	void testKeyRecognitionHelpers() {
		CommandScepterScreen screen = new CommandScepterScreen();

		// Shift / Sneak helpers
		Assertions.assertTrue(screen.isShiftOrSneakKey(GLFW.GLFW_KEY_LEFT_SHIFT, 0));
		Assertions.assertTrue(screen.isShiftOrSneakKey(GLFW.GLFW_KEY_RIGHT_SHIFT, 0));
		Assertions.assertFalse(screen.isShiftOrSneakKey(GLFW.GLFW_KEY_V, 0));
		Assertions.assertFalse(screen.isShiftOrSneakKey(GLFW.GLFW_KEY_E, 0));
		Assertions.assertFalse(screen.isShiftOrSneakKey(GLFW.GLFW_KEY_SPACE, 0));

		// Command Hub helpers
		Assertions.assertTrue(screen.isCommandHubKey(GLFW.GLFW_KEY_V, 0));
		Assertions.assertFalse(screen.isCommandHubKey(GLFW.GLFW_KEY_B, 0));
		Assertions.assertFalse(screen.isCommandHubKey(GLFW.GLFW_KEY_LEFT_SHIFT, 0));

		// Inventory helpers
		Assertions.assertTrue(screen.isInventoryKey(GLFW.GLFW_KEY_E, 0));
		Assertions.assertFalse(screen.isInventoryKey(GLFW.GLFW_KEY_I, 0));
		Assertions.assertFalse(screen.isInventoryKey(GLFW.GLFW_KEY_V, 0));
	}

	@Test
	@DisplayName("Validate state getters and setters lifecycle")
	void testStateFlagsAndSetters() {
		CommandScepterScreen screen = new CommandScepterScreen();

		screen.setShiftHeldOnOpen(false);
		Assertions.assertFalse(screen.isShiftHeldOnOpen());
		Assertions.assertTrue(screen.isInitializedOpenState());

		screen.setShiftHeldOnOpen(true);
		Assertions.assertTrue(screen.isShiftHeldOnOpen());

		screen.setClosed(true);
		Assertions.assertTrue(screen.isClosed());

		screen.setClosed(false);
		Assertions.assertFalse(screen.isClosed());

		screen.setInitializedOpenState(false);
		Assertions.assertFalse(screen.isInitializedOpenState());
	}

	@Test
	@DisplayName("Multiple close calls are idempotent and maintain closed state")
	void testCloseIdempotency() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertFalse(screen.isClosed());

		screen.close();
		Assertions.assertTrue(screen.isClosed());

		// Second close call
		screen.close();
		Assertions.assertTrue(screen.isClosed());
	}
}
