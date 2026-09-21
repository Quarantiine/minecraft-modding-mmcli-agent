package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;

/**
 * AI target goal allowing escort minions to coordinate offensive attacks
 * with their designated Squad Leader minion, acquiring the leader's active combat target.
 */
public class AttackWithLeaderGoal extends TrackTargetGoal {

	private final MinionEntity minion;
	private LivingEntity attacking;
	private int lastAttackTime;

	public AttackWithLeaderGoal(MinionEntity minion) {
		super(minion, false);
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.TARGET));
	}

	@Override
	public boolean canStart() {
		if (!this.minion.hasLeader() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		MinionEntity leader = this.minion.resolveLeader();
		if (leader == null || !leader.isAlive()) {
			return false;
		}
		this.attacking = leader.getAttacking();
		int i = leader.getLastAttackTime();
		if (i == this.lastAttackTime || !this.canTrack(this.attacking, TargetPredicate.DEFAULT)) {
			return false;
		}
		// Combat leash check: only attack with leader if within 16 blocks (256.0D sq) of leader and target
		return this.minion.squaredDistanceTo(leader) <= 256.0D && this.minion.squaredDistanceTo(this.attacking) <= 256.0D;
	}

	@Override
	public void start() {
		this.mob.setTarget(this.attacking);
		MinionEntity leader = this.minion.resolveLeader();
		if (leader != null) {
			this.lastAttackTime = leader.getLastAttackTime();
		}
		super.start();
	}
}
