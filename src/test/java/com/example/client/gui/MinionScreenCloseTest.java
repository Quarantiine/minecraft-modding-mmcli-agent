package com.example.client.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/**
 * Comprehensive unit test suite validating the smart Shift-to-close mechanism with
 * shift-click item transfer latching (Refinement 4), 'E' inventory key close support,
 * open-state guards, GLFW key repeat absorption, and client-safe singleplayer owner checks
 * in {@link MinionScreen}.
 */
public class MinionScreenCloseTest {

	// =========================================================================
	// 1. Shift-to-Close Open Guard & Repeat Absorption
	// =========================================================================

	@Test
	@DisplayName("Opening screen while holding Shift suppresses immediate closure and absorbs Shift key repeats")
	void testShiftHeldOnOpenSuppressesImmediateClose() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.initOpenState(true);

		Assertions.assertTrue(handler.isShiftHeldOnOpen(), "shiftHeldOnOpen guard should be armed upon opening with Shift");
		Assertions.assertFalse(handler.isClosed(), "Screen must start open");

		// Initial Shift press event while player's finger is physically down from sneak-opening
		boolean handled = handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handled, "Shift press event should be consumed by screen");
		Assertions.assertFalse(handler.isClosed(), "Screen must NOT close on initial held Shift press");

		// GLFW repeat events generated while holding Shift down
		boolean repeatHandled = handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		Assertions.assertTrue(repeatHandled, "Shift repeat event should be consumed");
		Assertions.assertFalse(handler.isClosed(), "Screen must NOT close on repeated Shift press while guard is active");

		// Releasing Shift clears open guard without closing screen
		boolean released = handler.onKeyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertFalse(handler.isShiftHeldOnOpen(), "shiftHeldOnOpen must reset to false upon Shift release");
		Assertions.assertFalse(handler.isClosed(), "Screen must remain open immediately after releasing opening Shift");
	}

	@Test
	@DisplayName("Releasing Shift clears open-state guard and subsequent tap of Shift dismisses screen")
	void testTapShiftClosesScreenWhenDisarmed() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		Assertions.assertFalse(handler.isShiftHeldOnOpen(), "Guard should be disarmed");
		Assertions.assertFalse(handler.isClosed(), "Screen must start open");

		// Player presses Shift
		boolean handledPress = handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handledPress, "Shift press should be absorbed");
		Assertions.assertFalse(handler.isClosed(), "Screen should not close on press");

		// Player releases Shift without clicking any slot
		boolean handledRelease = handler.onKeyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handledRelease, "Shift release should be handled");
		Assertions.assertTrue(handler.isClosed(), "Screen must close on Shift release when no item slot was clicked");
	}

	// =========================================================================
	// 2. Refinement 4: Shift-Click Item Transfer Latching
	// =========================================================================

	@Test
	@DisplayName("Shift-click item transfer latching prevents screen closure upon releasing Shift (Refinement 4)")
	void testShiftClickTransferLatchingPreventsClose() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		// 1. Player presses Shift to begin item transfer
		handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		Assertions.assertFalse(handler.isClosed(), "Screen must stay open on Shift press");

		// 2. Player clicks an item slot with Shift held -> latches slotClickedWithShift
		handler.onMouseClicked(true);
		Assertions.assertTrue(handler.isSlotClickedWithShift(), "slotClickedWithShift flag must be latched");

		// 3. Player releases Shift after transferring item
		handler.onKeyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertFalse(handler.isClosed(), "Screen must NOT close on Shift release when item transfer occurred");
		Assertions.assertFalse(handler.isSlotClickedWithShift(), "slotClickedWithShift must reset to false upon release");

		// 4. Now subsequent Shift tap without item click closes the screen
		handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		handler.onKeyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handler.isClosed(), "Screen must close on subsequent Shift tap without item transfer");
	}

	@Test
	@DisplayName("Multiple item transfers during single Shift hold keep screen open until clean tap")
	void testMultipleShiftClicksDuringSingleHold() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		// Transfer 1
		handler.onMouseClicked(true);
		// Transfer 2
		handler.onMouseClicked(true);
		// Transfer 3
		handler.onMouseClicked(true);

		// Release Shift
		handler.onKeyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertFalse(handler.isClosed(), "Screen must NOT close after multiple shift clicks");
		Assertions.assertFalse(handler.isSlotClickedWithShift(), "Flag must reset");
	}

	@Test
	@DisplayName("Clicking slot without Shift held does not latch transfer flag")
	void testClickWithoutShiftDoesNotLatch() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		// Normal click without shift
		handler.onMouseClicked(false);
		Assertions.assertFalse(handler.isSlotClickedWithShift(), "Flag should remain false");

		// Subsequent tap of Shift should still close the screen
		handler.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		handler.onKeyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handler.isClosed(), "Clean Shift tap closes screen");
	}

	@Test
	@DisplayName("Shift-click item transfer latching does not block explicit 'E' inventory or Escape dismissal")
	void testShiftClickTransferDoesNotBlockExplicitDismissalKeys() {
		// Test 'E' key dismissal with transfer latched
		MinionScreen.SmartCloseHandler handlerE = new MinionScreen.SmartCloseHandler();
		handlerE.setShiftHeldOnOpen(false);
		handlerE.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handlerE.setClosed(true));
		handlerE.onMouseClicked(true);
		Assertions.assertTrue(handlerE.isSlotClickedWithShift());

		// Player presses 'E' while transfer flag was latched
		boolean handledE = handlerE.onKeyPressed(GLFW.GLFW_KEY_E, 0, false, true, false, () -> handlerE.setClosed(true));
		Assertions.assertTrue(handledE, "'E' key must be handled");
		Assertions.assertTrue(handlerE.isClosed(), "'E' inventory key must close screen even if slot was shift-clicked");

		// Test Escape key dismissal with transfer latched
		MinionScreen.SmartCloseHandler handlerEsc = new MinionScreen.SmartCloseHandler();
		handlerEsc.setShiftHeldOnOpen(false);
		handlerEsc.onKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, true, false, false, () -> handlerEsc.setClosed(true));
		handlerEsc.onMouseClicked(true);
		Assertions.assertTrue(handlerEsc.isSlotClickedWithShift());

		boolean handledEsc = handlerEsc.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, false, false, true, () -> handlerEsc.setClosed(true));
		Assertions.assertTrue(handledEsc, "Escape key must be handled");
		Assertions.assertTrue(handlerEsc.isClosed(), "Escape key must close screen even if slot was shift-clicked");
	}

	@Test
	@DisplayName("Right Shift key exhibits identical smart Shift-to-close and item transfer latching semantics")
	void testRightShiftSmartCloseSemantics() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		// Press Right Shift
		handler.onKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		Assertions.assertFalse(handler.isClosed());

		// Latch item transfer
		handler.onMouseClicked(true);
		Assertions.assertTrue(handler.isSlotClickedWithShift());

		// Release Right Shift -> suppressed close
		handler.onKeyReleased(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertFalse(handler.isClosed(), "Screen must NOT close after Right Shift item transfer");
		Assertions.assertFalse(handler.isSlotClickedWithShift());

		// Next clean Right Shift tap -> closes
		handler.onKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, true, false, false, () -> handler.setClosed(true));
		handler.onKeyReleased(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, true, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handler.isClosed(), "Clean Right Shift tap closes screen");
	}

	@Test
	@DisplayName("Zero-latency physical release in tick/render disarms shiftHeldOnOpen")
	void testTickOrRenderDisarmsGuard() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.initOpenState(true);
		Assertions.assertTrue(handler.isShiftHeldOnOpen());

		// When physical shift is released
		handler.tickOrRender(false);
		Assertions.assertFalse(handler.isShiftHeldOnOpen(), "Physical shift release must clear shiftHeldOnOpen");
	}

	// =========================================================================
	// 3. Multi-Key Dismissals ('E' Inventory and Escape)
	// =========================================================================

	@Test
	@DisplayName("Pressing 'E' inventory key closes screen immediately")
	void testInventoryKeyClosesScreen() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		boolean handled = handler.onKeyPressed(GLFW.GLFW_KEY_E, 0, false, true, false, () -> handler.setClosed(true));
		Assertions.assertTrue(handled, "'E' key press should be handled");
		Assertions.assertTrue(handler.isClosed(), "Screen must close immediately on 'E' inventory key press");
	}

	@Test
	@DisplayName("Pressing Escape closes screen immediately")
	void testEscapeKeyClosesScreen() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		boolean handled = handler.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, false, false, true, () -> handler.setClosed(true));
		Assertions.assertTrue(handled, "Escape key press should be handled");
		Assertions.assertTrue(handler.isClosed(), "Screen must close immediately on Escape press");
	}

	@Test
	@DisplayName("Unrelated keys do not close the screen")
	void testUnrelatedKeysDoNotCloseScreen() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();
		handler.setShiftHeldOnOpen(false);

		int[] unrelatedKeys = {
			GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
			GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER
		};

		for (int key : unrelatedKeys) {
			boolean handled = handler.onKeyPressed(key, 0, false, false, false, () -> handler.setClosed(true));
			Assertions.assertFalse(handled, "Unrelated key should not be handled by close controller");
			Assertions.assertFalse(handler.isClosed(), "Key " + key + " must not close the screen");
		}
	}

	// =========================================================================
	// 4. Client-Safe Singleplayer Owner Bypass Logic
	// =========================================================================

	@Test
	@DisplayName("Singleplayer host always bypasses owner check for tamed minions")
	void testSingleplayerHostOwnerBypass() {
		// inSingleplayer=true, isTamed=true, isOwnerEntity=false, hasOwnerUuid=true
		boolean authorized = MinionScreen.evaluateOwnership(true, true, false, true);
		Assertions.assertTrue(authorized, "Singleplayer host must bypass ownership for tamed minions");
	}

	@Test
	@DisplayName("Singleplayer host does not bypass ownership for untamed minions")
	void testSingleplayerUntamedMinion() {
		// inSingleplayer=true, isTamed=false, isOwnerEntity=false, hasOwnerUuid=true
		boolean authorized = MinionScreen.evaluateOwnership(true, false, false, true);
		Assertions.assertFalse(authorized, "Singleplayer host should not bypass untamed minions without owner match");
	}

	@Test
	@DisplayName("Multiplayer requires exact player entity match")
	void testMultiplayerOwnerMatching() {
		// inSingleplayer=false, isTamed=true, isOwnerEntity=true, hasOwnerUuid=true
		boolean match = MinionScreen.evaluateOwnership(false, true, true, true);
		Assertions.assertTrue(match, "Multiplayer matching owner must be authorized");

		// inSingleplayer=false, isTamed=true, isOwnerEntity=false, hasOwnerUuid=true
		boolean mismatch = MinionScreen.evaluateOwnership(false, true, false, true);
		Assertions.assertFalse(mismatch, "Multiplayer non-owner must not be authorized");
	}

	@Test
	@DisplayName("Unowned minion (hasOwnerUuid=false) is always open to configuration")
	void testUnownedMinionOpenToAll() {
		boolean authorized = MinionScreen.evaluateOwnership(false, false, false, false);
		Assertions.assertTrue(authorized, "Minion with no owner UUID is open to configuration");
	}

	// =========================================================================
	// 5. State Lifecycle Invariants
	// =========================================================================

	@Test
	@DisplayName("State lifecycle getters and setters adhere to invariants")
	void testStateLifecycleInvariants() {
		MinionScreen.SmartCloseHandler handler = new MinionScreen.SmartCloseHandler();

		handler.setClosed(true);
		Assertions.assertTrue(handler.isClosed());
		handler.setClosed(false);
		Assertions.assertFalse(handler.isClosed());

		handler.setInitializedOpenState(true);
		Assertions.assertTrue(handler.isInitializedOpenState());
		handler.setInitializedOpenState(false);
		Assertions.assertFalse(handler.isInitializedOpenState());

		handler.setSlotClickedWithShift(true);
		Assertions.assertTrue(handler.isSlotClickedWithShift());
		handler.setSlotClickedWithShift(false);
		Assertions.assertFalse(handler.isSlotClickedWithShift());
	}
}
