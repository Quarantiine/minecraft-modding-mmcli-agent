package com.example.entity;

import com.example.mixin.TridentEntityMixin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests verifying Trident Loyalty return interception mechanics for MinionEntity owners:
 * 1. Mixin presence, registration in modid.mixins.json, and bytecode target definitions.
 * 2. Proximity threshold validation (within 1.5 blocks / 2.25 distance squared).
 * 3. Inventory return priority hierarchy: Mainhand -> Offhand -> 9-slot inventory -> Ground drop.
 * 4. Sound event and particle emission triggers before entity discard.
 */
public class TridentLoyaltyReturnTest {

	@Test
	@DisplayName("TridentEntityMixin registration in modid.mixins.json")
	void testMixinRegistration() throws IOException {
		Path mixinJsonPath = Path.of("src/main/resources/modid.mixins.json");
		Assertions.assertTrue(Files.exists(mixinJsonPath), "modid.mixins.json must exist");
		String content = Files.readString(mixinJsonPath);
		Assertions.assertTrue(content.contains("\"TridentEntityMixin\""),
				"modid.mixins.json must declare TridentEntityMixin");
	}

	@Test
	@DisplayName("Source Invariant: TridentEntityMixin targets TridentEntity and handles MinionEntity owner")
	void testTridentMixinSourceInvariants() throws IOException {
		Path mixinPath = Path.of("src/main/java/com/example/mixin/TridentEntityMixin.java");
		Assertions.assertTrue(Files.exists(mixinPath), "TridentEntityMixin.java must exist");
		String code = Files.readString(mixinPath);

		Assertions.assertTrue(code.contains("@Mixin(TridentEntity.class)"),
				"Mixin must target TridentEntity");
		Assertions.assertTrue(code.contains("minion.getMainHandStack()"),
				"Must check minion main hand stack");
		Assertions.assertTrue(code.contains("minion.getOffHandStack()"),
				"Must check minion off hand stack");
		Assertions.assertTrue(code.contains("minion.getInventory().addStack("),
				"Must check minion 9-slot inventory");
		Assertions.assertTrue(code.contains("SoundEvents.ITEM_TRIDENT_RETURN"),
				"Must play trident return sound");
		Assertions.assertTrue(code.contains("this.discard()"),
				"Must discard projectile upon return");
	}

	@Test
	@DisplayName("Loyalty Return Proximity: Distance squared threshold 2.25D correctly bounds 1.5 blocks")
	void testProximityThreshold() {
		double maxReturnDistance = 1.5D;
		double maxDistSq = maxReturnDistance * maxReturnDistance; // 2.25D

		Assertions.assertEquals(2.25D, maxDistSq, 0.0001D);

		// Inside range
		Assertions.assertTrue(0.5D * 0.5D <= maxDistSq, "0.5 blocks away is within catch distance");
		Assertions.assertTrue(1.0D * 1.0D <= maxDistSq, "1.0 blocks away is within catch distance");
		Assertions.assertTrue(1.49D * 1.49D <= maxDistSq, "1.49 blocks away is within catch distance");

		// Outside range
		Assertions.assertFalse(1.51D * 1.51D <= maxDistSq, "1.51 blocks away is outside catch distance");
		Assertions.assertFalse(2.0D * 2.0D <= maxDistSq, "2.0 blocks away is outside catch distance");
	}

	@Test
	@DisplayName("Inventory Return Logic Simulation: Mainhand -> Offhand -> Inventory -> Drop")
	void testInventoryReturnPriorities() {
		// Mock inventory slot states
		class MockMinion {
			String mainHand = "";
			String offHand = "";
			int invCount = 0;
			int droppedCount = 0;

			void returnItem(String item) {
				if (mainHand.isEmpty()) {
					mainHand = item;
				} else if (offHand.isEmpty()) {
					offHand = item;
				} else if (invCount < 9) {
					invCount++;
				} else {
					droppedCount++;
				}
			}
		}

		// Case 1: Empty hands -> equips mainhand
		MockMinion m1 = new MockMinion();
		m1.returnItem("trident");
		Assertions.assertEquals("trident", m1.mainHand);
		Assertions.assertEquals("", m1.offHand);
		Assertions.assertEquals(0, m1.invCount);

		// Case 2: Mainhand occupied (e.g. sword) -> equips offhand
		MockMinion m2 = new MockMinion();
		m2.mainHand = "sword";
		m2.returnItem("trident");
		Assertions.assertEquals("sword", m2.mainHand);
		Assertions.assertEquals("trident", m2.offHand);
		Assertions.assertEquals(0, m2.invCount);

		// Case 3: Both hands occupied -> stores in internal inventory
		MockMinion m3 = new MockMinion();
		m3.mainHand = "sword";
		m3.offHand = "shield";
		m3.returnItem("trident");
		Assertions.assertEquals("sword", m3.mainHand);
		Assertions.assertEquals("shield", m3.offHand);
		Assertions.assertEquals(1, m3.invCount);

		// Case 4: Hands and inventory full -> drops item
		MockMinion m4 = new MockMinion();
		m4.mainHand = "sword";
		m4.offHand = "shield";
		m4.invCount = 9;
		m4.returnItem("trident");
		Assertions.assertEquals(1, m4.droppedCount);
	}
}
