package com.example.mixin;

import com.example.entity.custom.MinionEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for {@link TridentEntity} to handle Loyalty trident returns for {@link MinionEntity} owners.
 * <p>
 * In vanilla Minecraft, returning Loyalty tridents fly toward their owner with {@code noClip = true},
 * but vanilla only collects and despawns the returning trident in {@code onPlayerCollision(PlayerEntity)}.
 * Because {@link MinionEntity} is a {@link net.minecraft.entity.passive.TameableEntity} and not a {@link net.minecraft.entity.player.PlayerEntity},
 * returning Loyalty tridents overshoot the minion's eye coordinates and enter an infinite buzzing orbit loop.
 * </p>
 * <p>
 * This mixin intercepts {@code tick()} on {@link TridentEntity}: when the trident is returning (noClip is active)
 * and its owner is an alive {@link MinionEntity}, if the trident reaches within proximity of the minion (distance squared &le; 2.25D,
 * i.e., 1.5 blocks), it returns the trident item to the minion's inventory/hands, plays the return sound and particles,
 * and discards the projectile entity.
 * </p>
 */
@Mixin(TridentEntity.class)
public abstract class TridentEntityMixin extends PersistentProjectileEntity {

	protected TridentEntityMixin(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onMinionLoyaltyTick(CallbackInfo ci) {
		if (!this.getWorld().isClient && this.isNoClip()) {
			Entity owner = this.getOwner();
			if (owner instanceof MinionEntity minion && minion.isAlive()) {
				// Check distance between trident and minion center/eye
				double distSq = this.squaredDistanceTo(minion);
				if (distSq <= 2.25D) { // within 1.5 blocks
					returnTridentToMinion(minion);
				}
			}
		}
	}

	/**
	 * Returns the trident item stack back to the minion owner, playing return effects and discarding the entity.
	 *
	 * @param minion the owner minion receiving the returning trident
	 */
	private void returnTridentToMinion(MinionEntity minion) {
		ItemStack stackToReturn = this.getItemStack().copy();

		if (!stackToReturn.isEmpty()) {
			ItemStack mainHand = minion.getMainHandStack();
			if (mainHand.isEmpty()) {
				minion.equipStack(EquipmentSlot.MAINHAND, stackToReturn);
			} else {
				ItemStack offHand = minion.getOffHandStack();
				if (offHand.isEmpty()) {
					minion.equipStack(EquipmentSlot.OFFHAND, stackToReturn);
				} else {
					// Add to minion's 9-slot internal inventory
					ItemStack remaining = minion.getInventory().addStack(stackToReturn);
					if (!remaining.isEmpty()) {
						// If inventory full, drop at minion location
						minion.dropStack(remaining);
					}
				}
			}
		}

		// Audio cue for trident catching
		this.getWorld().playSound(
				null,
				minion.getX(),
				minion.getY(),
				minion.getZ(),
				SoundEvents.ITEM_TRIDENT_RETURN,
				SoundCategory.NEUTRAL,
				1.0F,
				1.0F
		);

		// Visual particle cue in server world
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(
					ParticleTypes.PORTAL,
					this.getX(),
					this.getY() + 0.5D,
					this.getZ(),
					8,
					0.2D,
					0.2D,
					0.2D,
					0.05D
			);
		}

		// Discard the returning projectile
		this.discard();
	}
}