package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.Box;

/**
 * Dedicated hostile target acquisition goal for minion thralls.
 * Primarily active for {@link MinionRole#WARRIOR} units, scanning a 24-block bounding box
 * for hostile mobs threatening the master or perimeter.
 */
public class MinionActiveTargetGoal extends ActiveTargetGoal<HostileEntity> {

	private final MinionEntity minion;
	private final double scanDistance;

	public MinionActiveTargetGoal(MinionEntity minion, double scanDistance) {
		super(minion, HostileEntity.class, true);
		this.minion = minion;
		this.scanDistance = scanDistance;
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		// Warriors actively scan and engage hostiles across the tactical zone
		if (this.minion.getRole() != MinionRole.WARRIOR) {
			return false;
		}
		return super.canStart();
	}

	@Override
	protected Box getSearchBox(double distance) {
		return this.mob.getBoundingBox().expand(this.scanDistance, 4.0D, this.scanDistance);
	}
}
