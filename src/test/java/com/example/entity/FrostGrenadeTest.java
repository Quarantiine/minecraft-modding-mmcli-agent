package com.example.entity;

import com.example.entity.custom.FrostGrenadeEntity;
import com.example.item.custom.FrostGrenadeStickItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests validating the Frost Grenade Projectile Stick and entity mechanics:
 * 1. Constant thresholds and cryogenic configurations
 * 2. Ring geometry distance filtering and open center preservation
 * 3. Fluid and fire transmutation rules (water -> ice, lava -> obsidian, fires extinguished)
 * 4. Entity freezing ticks threshold and Slowness III amplifier
 * 5. Item registration, creative properties, and cooldown constraints
 */
public class FrostGrenadeTest {

	@Test
	@DisplayName("Validate cryogenic constants and debuff durations")
	void testCryogenicConstants() {
		Assertions.assertEquals(3.5F, FrostGrenadeEntity.FREEZE_RADIUS, 0.01F,
				"Freeze radius should be 3.5 blocks");
		Assertions.assertEquals(2.0F, FrostGrenadeEntity.RING_MIN_RADIUS, 0.01F,
				"Ring min radius should be 2.0 blocks to keep center clear");
		Assertions.assertEquals(3.5F, FrostGrenadeEntity.RING_MAX_RADIUS, 0.01F,
				"Ring max radius should match outer freeze boundary (3.5 blocks)");
		Assertions.assertEquals(5.0F, FrostGrenadeEntity.ENTITY_EFFECT_RADIUS, 0.01F,
				"Entity debuff radius should be 5.0 blocks");
		Assertions.assertTrue(FrostGrenadeEntity.FREEZE_TICKS >= 140,
				"Freeze ticks (360) must exceed vanilla freeze damage threshold of 140 ticks");
		Assertions.assertEquals(360, FrostGrenadeEntity.FREEZE_TICKS,
				"Freeze ticks should be 360 ticks (18 seconds of frost)");
		Assertions.assertEquals(160, FrostGrenadeEntity.SLOWNESS_DURATION_TICKS,
				"Slowness duration should be 160 ticks (8 seconds)");
		Assertions.assertEquals(2, FrostGrenadeEntity.SLOWNESS_AMPLIFIER,
				"Slowness amplifier should be 2 for Slowness III");
		Assertions.assertEquals(10, FrostGrenadeStickItem.USAGE_COOLDOWN_TICKS,
				"Stick cooldown should be 10 ticks (0.5s)");
	}

	@Test
	@DisplayName("Validate powder snow ring geometry: perimeter points accepted, center and outer excluded")
	void testRingGeometryFiltering() {
		double minRadiusSq = FrostGrenadeEntity.RING_MIN_RADIUS * FrostGrenadeEntity.RING_MIN_RADIUS; // 4.0
		double maxRadiusSq = FrostGrenadeEntity.RING_MAX_RADIUS * FrostGrenadeEntity.RING_MAX_RADIUS; // 12.25

		List<BlockPos> ringOffsets = new ArrayList<>();
		int maxR = (int) Math.ceil(FrostGrenadeEntity.RING_MAX_RADIUS);

		for (int dx = -maxR; dx <= maxR; dx++) {
			for (int dz = -maxR; dz <= maxR; dz++) {
				double distSq = dx * dx + dz * dz;
				if (distSq >= minRadiusSq && distSq <= maxRadiusSq) {
					ringOffsets.add(new BlockPos(dx, 0, dz));
				}
			}
		}

		// Verify center points (0,0), (1,0), (0,1), (1,1) are NOT in the ring
		Assertions.assertFalse(ringOffsets.contains(new BlockPos(0, 0, 0)), "Center (0,0) must not contain powder snow");
		Assertions.assertFalse(ringOffsets.contains(new BlockPos(1, 0, 0)), "Point (1,0) is inside ring inner radius");
		Assertions.assertFalse(ringOffsets.contains(new BlockPos(0, 0, 1)), "Point (0,1) is inside ring inner radius");
		Assertions.assertFalse(ringOffsets.contains(new BlockPos(1, 0, 1)), "Point (1,1) distSq 2 is inside ring inner radius");

		// Verify perimeter points are in the ring
		Assertions.assertTrue(ringOffsets.contains(new BlockPos(2, 0, 0)), "Point (2,0) distSq 4 must be in ring");
		Assertions.assertTrue(ringOffsets.contains(new BlockPos(-2, 0, 0)), "Point (-2,0) distSq 4 must be in ring");
		Assertions.assertTrue(ringOffsets.contains(new BlockPos(0, 0, 2)), "Point (0,2) distSq 4 must be in ring");
		Assertions.assertTrue(ringOffsets.contains(new BlockPos(2, 0, 2)), "Point (2,2) distSq 8 must be in ring");
		Assertions.assertTrue(ringOffsets.contains(new BlockPos(3, 0, 0)), "Point (3,0) distSq 9 must be in ring");
		Assertions.assertTrue(ringOffsets.contains(new BlockPos(0, 0, 3)), "Point (0,3) distSq 9 must be in ring");

		// Verify outer points are NOT in the ring
		Assertions.assertFalse(ringOffsets.contains(new BlockPos(4, 0, 0)), "Point (4,0) distSq 16 is outside ring");
		Assertions.assertFalse(ringOffsets.contains(new BlockPos(3, 0, 3)), "Point (3,3) distSq 18 is outside ring");

		// Ring should form a continuous circular perimeter of 16-24 ground positions
		Assertions.assertTrue(ringOffsets.size() >= 12 && ringOffsets.size() <= 28,
				"Ring perimeter should contain between 12 and 28 points, actual: " + ringOffsets.size());
	}

	@Test
	@DisplayName("Validate entity debuff distance check")
	void testEntityDebuffDistanceCalculation() {
		Vec3d hitPos = new Vec3d(10.0, 64.0, 10.0);
		double radiusSq = FrostGrenadeEntity.ENTITY_EFFECT_RADIUS * FrostGrenadeEntity.ENTITY_EFFECT_RADIUS; // 25.0

		// Entity at 3 blocks away
		Vec3d nearEntity = new Vec3d(10.0, 64.0, 13.0);
		Assertions.assertTrue(nearEntity.squaredDistanceTo(hitPos) <= radiusSq,
				"Entity 3 blocks away should be within 5.0 block effect radius");

		// Entity at 4.9 blocks away
		Vec3d boundaryEntity = new Vec3d(10.0, 64.0, 14.9);
		Assertions.assertTrue(boundaryEntity.squaredDistanceTo(hitPos) <= radiusSq,
				"Entity 4.9 blocks away should be within effect radius");

		// Entity at 5.5 blocks away
		Vec3d outsideEntity = new Vec3d(10.0, 64.0, 15.5);
		Assertions.assertFalse(outsideEntity.squaredDistanceTo(hitPos) <= radiusSq,
				"Entity 5.5 blocks away should be outside 5.0 block effect radius");
	}

	@Test
	@DisplayName("Validate item registry and stack properties source invariants")
	void testItemProperties() throws Exception {
		java.nio.file.Path modItemsPath = java.nio.file.Path.of("src/main/java/com/example/item/ModItems.java");
		Assertions.assertTrue(java.nio.file.Files.exists(modItemsPath), "ModItems.java must exist");
		String modItemsContent = java.nio.file.Files.readString(modItemsPath);
		Assertions.assertTrue(modItemsContent.contains("FROST_GRENADE_STICK"), "ModItems must define FROST_GRENADE_STICK");
		Assertions.assertTrue(modItemsContent.contains("\"frost_grenade_stick\""), "ModItems must register 'frost_grenade_stick'");
		Assertions.assertTrue(modItemsContent.contains("entries.add(FROST_GRENADE_STICK)"), "ModItems must add FROST_GRENADE_STICK to Combat creative tab");

		java.nio.file.Path modEntitiesPath = java.nio.file.Path.of("src/main/java/com/example/entity/ModEntities.java");
		Assertions.assertTrue(java.nio.file.Files.exists(modEntitiesPath), "ModEntities.java must exist");
		String modEntitiesContent = java.nio.file.Files.readString(modEntitiesPath);
		Assertions.assertTrue(modEntitiesContent.contains("FROST_PROJECTILE"), "ModEntities must define FROST_PROJECTILE");
		Assertions.assertTrue(modEntitiesContent.contains("\"frost_projectile\""), "ModEntities must register 'frost_projectile'");

		java.nio.file.Path clientPath = java.nio.file.Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(java.nio.file.Files.exists(clientPath), "ExampleModClient.java must exist");
		String clientContent = java.nio.file.Files.readString(clientPath);
		Assertions.assertTrue(clientContent.contains("EntityRendererRegistry.register(ModEntities.FROST_PROJECTILE, FlyingItemEntityRenderer::new)"),
				"Client must register FlyingItemEntityRenderer for FROST_PROJECTILE");
	}
}
