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
import net.minecraft.util.Hand;

/**
 * Tactical ranged skirmishing AI goal for {@link MinionEntity} thralls assigned the {@link MinionRole#RANGER} role.
 * <p>
 * Maintains an optimal engagement pocket between 8 and 16 blocks from the hostile target:
 * <ul>
 *   <li>Dynamically strafes laterally and adjusts footing when within the 8–16 block zone.</li>
 *   <li>Actively backpedals away if hostiles press closer than 8 blocks.</li>
 *   <li>Draws bow, tracks line of sight, and looses fully charged arrows at the enemy.</li>
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
	 * Checks whether the minion thrall is currently wielding a bow or crossbow in either hand.
	 *
	 * @return true if equipped with a ranged weapon.
	 */
	public boolean isHoldingBow() {
		return this.minion.isHolding(Items.BOW)
			|| this.minion.isHolding(Items.CROSSBOW)
			|| this.minion.getMainHandStack().getItem() instanceof BowItem
			|| this.minion.getOffHandStack().getItem() instanceof BowItem;
	}

	private Hand getHoldingHand() {
		ItemStack mainStack = this.minion.getMainHandStack();
		if (mainStack.isOf(Items.BOW) || mainStack.getItem() instanceof BowItem || mainStack.isOf(Items.CROSSBOW)) {
			return Hand.MAIN_HAND;
		}
		return Hand.OFF_HAND;
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (this.minion.getRole() != MinionRole.RANGER) {
			return false;
		}
		LivingEntity target = this.minion.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		return this.isHoldingBow();
	}

	@Override
	public boolean shouldContinue() {
		return (this.canStart() || !this.minion.getNavigation().isIdle()) && this.isHoldingBow();
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

		// Tactical positioning & dynamic strafing logic
		if (distSq < MIN_RANGE_SQ) {
			// Hostile encroached closer than 8 blocks -> backpedal immediately
			this.backward = true;
			if (this.minion.getRandom().nextFloat() < 0.15F) {
				this.movingToLeft = !this.movingToLeft;
			}
			this.minion.getMoveControl().strafeTo(-0.75F, this.movingToLeft ? 0.5F : -0.5F);
			if (!this.minion.getNavigation().isIdle()) {
				this.minion.getNavigation().stop();
			}
		} else if (distSq > MAX_RANGE_SQ || !canSee) {
			// Beyond 16 blocks or lost line of sight -> navigate towards hostile
			this.minion.getNavigation().startMovingTo(target, this.speed);
			this.combatTicks = 0;
		} else {
			// Within the optimal 8 to 16 block pocket -> dynamic strafe combat
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

		// Bow charging and projectile loosed logic
		if (this.minion.isUsingItem()) {
			if (!canSee && this.targetSeeingTicker < -60) {
				this.minion.clearActiveItem();
			} else if (canSee) {
				int pullTicks = this.minion.getItemUseTime();
				if (pullTicks >= 20) {
					this.minion.clearActiveItem();
					this.minion.shootAt(target, BowItem.getPullProgress(pullTicks));
					this.cooldown = this.attackInterval;
				}
			}
		} else if (--this.cooldown <= 0 && this.targetSeeingTicker >= -60 && canSee) {
			this.minion.setCurrentHand(this.getHoldingHand());
		}
	}
}
