package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;

/**
 * Tactical ranged skirmishing AI goal for {@link MinionEntity} thralls assigned the {@link MinionRole#WARRIOR} role
 * equipped with ranged weapons (bows, crossbows) or thrown weapons (tridents, frost grenades, tnt sticks).
 * <p>
 * Supports:
 * <ul>
 *   <li>Dynamic strafing and backpedaling within the engagement pocket.</li>
 *   <li>Trident Duality: Throws tridents at medium-long range (5–20 blocks), yielding to melee attack goal when within 5 blocks.</li>
 *   <li>Chargeable weapons (Bows, Crossbows, Tridents) with windup animation and instant thrown ordnance (Frost Grenades, TNT Sticks).</li>
 * </ul>
 */
public class MinionRangedAttackGoal extends Goal {

	private final MinionEntity minion;
	private final double speed;
	private final int attackInterval;

	private static final double MIN_RANGE = 8.0D;
	private static final double MIN_RANGE_SQ = MIN_RANGE * MIN_RANGE; // 64.0D
	private static final double MAX_RANGE = 16.0D;
	private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE; // 256.0D

	private static final double TRIDENT_MELEE_RANGE = 5.0D;
	private static final double TRIDENT_MELEE_RANGE_SQ = TRIDENT_MELEE_RANGE * TRIDENT_MELEE_RANGE; // 25.0D
	private static final double TRIDENT_MAX_RANGE = 20.0D;
	private static final double TRIDENT_MAX_RANGE_SQ = TRIDENT_MAX_RANGE * TRIDENT_MAX_RANGE; // 400.0D

	private int cooldown = -1;
	private int targetSeeingTicker = 0;
	private boolean movingToLeft = false;
	private boolean backward = false;
	private int combatTicks = 0;

	public MinionRangedAttackGoal(MinionEntity minion, double speed, int attackInterval) {
		this.minion = minion;
		this.speed = speed;
		this.attackInterval = attackInterval;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	/**
	 * Checks whether the minion thrall is currently wielding a ranged or thrown weapon
	 * (bow, crossbow, trident, frost grenade stick, tnt stick) in either hand.
	 *
	 * @return true if equipped with a ranged or thrown weapon.
	 */
	public boolean isHoldingRangedWeapon() {
		return MinionEntity.isRangedWeapon(this.minion.getMainHandStack())
			|| MinionEntity.isRangedWeapon(this.minion.getOffHandStack());
	}

	/**
	 * Legacy compatibility method checking whether the minion is wielding a bow, crossbow, or thrown weapon.
	 */
	public boolean isHoldingBow() {
		return isHoldingRangedWeapon();
	}

	/**
	 * Checks whether the minion is specifically wielding a Trident in either hand.
	 */
	public boolean isHoldingTrident() {
		ItemStack main = this.minion.getMainHandStack();
		ItemStack off = this.minion.getOffHandStack();
		return main.isOf(Items.TRIDENT) || main.getItem() instanceof TridentItem
			|| off.isOf(Items.TRIDENT) || off.getItem() instanceof TridentItem;
	}

	private Hand getHoldingHand() {
		ItemStack mainStack = this.minion.getMainHandStack();
		if (MinionEntity.isRangedWeapon(mainStack)) {
			return Hand.MAIN_HAND;
		}
		return Hand.OFF_HAND;
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		boolean isWarrior = this.minion.matchesRole(MinionRole.WARRIOR);
		boolean isSentinelWarriorMode = this.minion.matchesRole(MinionRole.SENTINEL)
				&& !this.minion.hasNearbyAlliesNeedingHealing();
		if (!isWarrior && !isSentinelWarriorMode) {
			return false;
		}
		LivingEntity target = this.minion.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (!this.isHoldingRangedWeapon()) {
			return false;
		}
		// Trident Duality: if wielding a Trident in mainhand without a pure ranged weapon, yield to MeleeAttackGoal when within 5 blocks
		if (this.isHoldingTrident()) {
			ItemStack mainStack = this.minion.getMainHandStack();
			boolean isPureRangedOffhand = MinionEntity.isRangedWeapon(this.minion.getOffHandStack()) && !MinionEntity.isMeleeWeapon(this.minion.getOffHandStack());
			if (!isPureRangedOffhand && (mainStack.isOf(Items.TRIDENT) || mainStack.getItem() instanceof TridentItem)) {
				double distSq = this.minion.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
				if (distSq <= TRIDENT_MELEE_RANGE_SQ) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		boolean isWarrior = this.minion.matchesRole(MinionRole.WARRIOR);
		boolean isSentinelWarriorMode = this.minion.matchesRole(MinionRole.SENTINEL)
				&& !this.minion.hasNearbyAlliesNeedingHealing();
		if (!isWarrior && !isSentinelWarriorMode) {
			return false;
		}
		LivingEntity target = this.minion.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (!this.isHoldingRangedWeapon()) {
			return false;
		}
		// Trident Duality: if target closed into melee range (<= 5 blocks), yield to MeleeAttackGoal
		if (this.isHoldingTrident()) {
			ItemStack mainStack = this.minion.getMainHandStack();
			boolean isPureRangedOffhand = MinionEntity.isRangedWeapon(this.minion.getOffHandStack()) && !MinionEntity.isMeleeWeapon(this.minion.getOffHandStack());
			if (!isPureRangedOffhand && (mainStack.isOf(Items.TRIDENT) || mainStack.getItem() instanceof TridentItem)) {
				double distSq = this.minion.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
				if (distSq <= TRIDENT_MELEE_RANGE_SQ) {
					return false;
				}
			}
		}
		return (this.canStart() || !this.minion.getNavigation().isIdle());
	}

	@Override
	public void start() {
		super.start();
		this.minion.setAttacking(true);
	}

	@Override
	public void stop() {
		super.stop();
		this.minion.setAttacking(false);
		this.targetSeeingTicker = 0;
		this.cooldown = -1;
		this.combatTicks = 0;
		this.minion.clearActiveItem();
	}

	@Override
	public void tick() {
		LivingEntity target = this.minion.getTarget();
		if (target == null) {
			return;
		}

		double distSq = this.minion.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
		boolean canSee = this.minion.getVisibilityCache().canSee(target);

		if (canSee) {
			this.targetSeeingTicker++;
		} else {
			this.targetSeeingTicker = 0;
		}

		ItemStack heldWeapon = this.minion.getStackInHand(this.getHoldingHand());
		boolean isTrident = heldWeapon.isOf(Items.TRIDENT) || heldWeapon.getItem() instanceof TridentItem;
		double minRangeSq = isTrident ? TRIDENT_MELEE_RANGE_SQ : MIN_RANGE_SQ;
		double maxRangeSq = isTrident ? TRIDENT_MAX_RANGE_SQ : MAX_RANGE_SQ;

		// Tactical positioning & dynamic strafing logic
		if (distSq < minRangeSq) {
			// Hostile encroached closer than min range -> backpedal immediately
			this.backward = true;
			if (this.minion.getRandom().nextFloat() < 0.15F) {
				this.movingToLeft = !this.movingToLeft;
			}
			this.minion.getMoveControl().strafeTo(-0.75F, this.movingToLeft ? 0.5F : -0.5F);
			if (!this.minion.getNavigation().isIdle()) {
				this.minion.getNavigation().stop();
			}
		} else if (distSq > maxRangeSq || !canSee) {
			// Beyond max range or lost line of sight -> navigate towards hostile
			this.minion.getNavigation().startMovingTo(target, this.speed);
			this.combatTicks = 0;
		} else {
			// Within the optimal engagement pocket -> dynamic strafe combat
			this.combatTicks++;
			if (this.combatTicks >= 20) {
				if (this.minion.getRandom().nextFloat() < 0.3F) {
					this.movingToLeft = !this.movingToLeft;
				}
				if (this.minion.getRandom().nextFloat() < 0.2F) {
					this.backward = !this.backward;
				}
				this.combatTicks = 0;
			}

			float forward = this.backward ? -0.35F : 0.35F;
			float strafe = this.movingToLeft ? 0.6F : -0.6F;
			this.minion.getMoveControl().strafeTo(forward, strafe);
			if (!this.minion.getNavigation().isIdle()) {
				this.minion.getNavigation().stop();
			}
		}

		this.minion.getLookControl().lookAt(target, 30.0F, 30.0F);

		// Projectile charging & loosed logic
		UseAction useAction = heldWeapon.getUseAction();
		boolean isChargeable = useAction == UseAction.BOW || useAction == UseAction.SPEAR || useAction == UseAction.CROSSBOW;

		if (isChargeable) {
			if (this.minion.isUsingItem()) {
				if (!canSee && this.targetSeeingTicker < -60) {
					this.minion.clearActiveItem();
				} else if (canSee) {
					int pullTicks = this.minion.getItemUseTime();
					int requiredTicks = (useAction == UseAction.SPEAR) ? 10 : 20;
					if (pullTicks >= requiredTicks) {
						this.minion.clearActiveItem();
						float pullProgress = (useAction == UseAction.SPEAR) ? 1.0F : BowItem.getPullProgress(pullTicks);
						this.minion.shootAt(target, pullProgress);
						this.cooldown = this.attackInterval;
					}
				}
			} else if (--this.cooldown <= 0 && this.targetSeeingTicker >= -60 && canSee) {
				this.minion.setCurrentHand(this.getHoldingHand());
			}
		} else {
			// Instant thrown weapons (Frost Grenades, TNT Sticks)
			if (this.minion.isUsingItem()) {
				this.minion.clearActiveItem();
			}
			if (--this.cooldown <= 0 && canSee) {
				this.minion.shootAt(target, 1.0F);
				this.cooldown = this.attackInterval;
			}
		}
	}
}
