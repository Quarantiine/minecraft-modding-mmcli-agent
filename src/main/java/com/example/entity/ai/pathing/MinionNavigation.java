package com.example.entity.ai.pathing;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

/**
 * Custom {@link MobNavigation} for Minion thralls.
 *
 * Utilizes {@link MinionPathNodeMaker} to support pathfinding through scaffolding columns
 * and over elevated scaffolding platforms. Pre-configures door navigation so minions can
 * traverse interior doors and structural portals smoothly.
 */
public class MinionNavigation extends MobNavigation {

	public MinionNavigation(MobEntity mobEntity, World world) {
		super(mobEntity, world);
		this.setCanPathThroughDoors(true);
		this.setCanEnterOpenDoors(true);
		this.setCanSwim(true);
	}

	@Override
	protected PathNodeNavigator createPathNodeNavigator(int range) {
		this.nodeMaker = new MinionPathNodeMaker();
		return new PathNodeNavigator(this.nodeMaker, range);
	}
}
