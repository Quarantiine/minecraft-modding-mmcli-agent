package com.example.entity.ai.logistics;

import com.example.entity.custom.MinionEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * Manages peer-to-peer item sharing and resource redistribution among allied minions.
 */
public final class MinionLogisticsHelper {

	private static final double SHARING_RADIUS = 24.0D;

	private MinionLogisticsHelper() {}

	/**
	 * Scans nearby allied minions for the required item and transfers 1 item from an ally's inventory
	 * to the requesting minion's inventory, displaying particle beams and sound effects.
	 *
	 * @param requester    The minion requesting resources.
	 * @param world        The server world.
	 * @param requiredItem The item required for construction or crafting.
	 * @return True if the item was successfully transferred from an ally, false otherwise.
	 */
	public static boolean requestItemFromAllies(MinionEntity requester, ServerWorld world, Item requiredItem) {
		if (requester == null || world == null || requiredItem == null) {
			return false;
		}
		UUID ownerUuid = requester.getOwnerUuid();
		if (ownerUuid == null) {
			return false;
		}

		Box searchBox = requester.getBoundingBox().expand(SHARING_RADIUS);
		List<MinionEntity> allies = world.getEntitiesByClass(
			MinionEntity.class,
			searchBox,
			ally -> ally != requester
				&& ally.isAlive()
				&& ownerUuid.equals(ally.getOwnerUuid())
		);

		for (MinionEntity ally : allies) {
			SimpleInventory allyInv = ally.getInventory();
			// First check direct item match
			for (int slot = 0; slot < allyInv.size(); slot++) {
				ItemStack stack = allyInv.getStack(slot);
				if (!stack.isEmpty() && stack.isOf(requiredItem) && stack.getCount() > 0) {
					// Extract 1 item from ally
					ItemStack transferred = stack.split(1);
					allyInv.markDirty();

					// Add to requester
					requester.getInventory().addStack(transferred);
					requester.getInventory().markDirty();

					// Visual particle beam between ally and requester
					spawnSharingBeam(world, ally.getEyePos(), requester.getEyePos());

					// Pickup sound at requester
					world.playSound(
						null,
						requester.getX(),
						requester.getY(),
						requester.getZ(),
						SoundEvents.ENTITY_ITEM_PICKUP,
						SoundCategory.NEUTRAL,
						0.8F,
						1.2F + (world.random.nextFloat() * 0.2F)
					);

					return true;
				}
			}

			// Second check: check if ally can synthesize the item from raw materials (e.g. bones -> bone meal, string -> wool)
			if (MinionHarvestingHelper.isMobProcurementResource(requiredItem)) {
				if (MinionHarvestingHelper.synthesizeMaterial(allyInv, requiredItem)) {
					for (int slot = 0; slot < allyInv.size(); slot++) {
						ItemStack stack = allyInv.getStack(slot);
						if (!stack.isEmpty() && stack.isOf(requiredItem) && stack.getCount() > 0) {
							ItemStack transferred = stack.split(1);
							allyInv.markDirty();

							requester.getInventory().addStack(transferred);
							requester.getInventory().markDirty();

							spawnSharingBeam(world, ally.getEyePos(), requester.getEyePos());

							world.playSound(
								null,
								requester.getX(),
								requester.getY(),
								requester.getZ(),
								SoundEvents.ENTITY_ITEM_PICKUP,
								SoundCategory.NEUTRAL,
								0.8F,
								1.2F + (world.random.nextFloat() * 0.2F)
							);

							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * Spawns visual particle beams between two positions.
	 *
	 * @param world Server world.
	 * @param from  Origin position.
	 * @param to    Destination position.
	 */
	public static void spawnSharingBeam(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d diff = to.subtract(from);
		double dist = diff.length();
		if (dist < 0.1D) return;
		int steps = Math.max(3, (int) (dist * 3));
		Vec3d stepVec = diff.multiply(1.0D / steps);

		for (int i = 0; i <= steps; i++) {
			Vec3d pos = from.add(stepVec.multiply(i));
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y, pos.z, 1, 0.05D, 0.05D, 0.05D, 0.01D);
			world.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 1, 0.02D, 0.02D, 0.02D, 0.01D);
		}
	}
}
