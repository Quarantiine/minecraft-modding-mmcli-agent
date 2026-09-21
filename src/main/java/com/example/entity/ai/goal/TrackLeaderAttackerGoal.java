package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;

/**
 * AI target goal allowing escort minions to automatically acquire and defend
 * against any hostile entity that inflicts damage on their designated Squad Leader minion.
 */
public class TrackLeaderAttackerGoal extends TrackTargetGoal {

	private final MinionEntity minion;
	private LivingEntity attacker;
	private int lastAttackedTime;

	public TrackLeaderAttackerGoal(MinionEntity minion) {
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
		this.attacker = leader.getAttacker();
		int i = leader.getLastAttackedTime();
		if (i == this.lastAttackedTime || !this.canTrack(this.attacker, TargetPredicate.DEFAULT)) {
			return false;
		}
		// Combat leash check: only defend if within 16 blocks (256.0D sq) of leader and attacker
		return this.minion.squaredDistanceTo(leader) <= 256.0D && this.minion.squaredDistanceTo(this.attacker) <= 256.0D;
	}

	@Override
	public void start() {
		this.mob.setTarget(this.attacker);
		MinionEntity leader = this.minion.resolveLeader();
		if (leader != null) {
			this.lastAttackedTime = leader.getLastAttackedTime();
		}
		super.start();
	}
}
