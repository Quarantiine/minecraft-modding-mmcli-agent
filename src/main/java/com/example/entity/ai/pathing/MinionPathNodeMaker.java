package com.example.entity.ai.pathing;

import com.example.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Custom {@link LandPathNodeMaker} for Minion thralls.
 *
 * In vanilla Minecraft, {@link LandPathNodeMaker} treats {@link Blocks#SCAFFOLDING} as
 * {@link PathNodeType#BLOCKED}, preventing mobs from pathfinding onto scaffolding columns,
 * ascending scaffolding ladders, or traversing scaffolding work platforms.
 *
 * This path node maker re-evaluates scaffolding and construction blocks:
 * 1. Scaffolding / construction blocks themselves are treated as {@link PathNodeType#OPEN}
 *    (or {@link PathNodeType#WALKABLE} if supported from below), allowing vertical ascent and lateral traversal.
 * 2. Standing on top of scaffolding / construction blocks is treated as {@link PathNodeType#WALKABLE},
 *    enabling smooth movement across elevated building platforms and sapper bridges.
 */
public class MinionPathNodeMaker extends LandPathNodeMaker {

	public MinionPathNodeMaker() {
		super();
	}

	private static boolean isScaffoldOrConstruction(BlockState state) {
		return state.isOf(Blocks.SCAFFOLDING) || state.isOf(ModBlocks.CONSTRUCTION_BLOCK);
	}

	@Override
	public PathNodeType getDefaultNodeType(PathContext context, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = context.getBlockState(pos);

		// If current block is scaffolding or construction block
		if (isScaffoldOrConstruction(state)) {
			BlockState below = context.getBlockState(pos.down());
			if (isScaffoldOrConstruction(below) || (!below.isAir() && below.isOpaque())) {
				return PathNodeType.WALKABLE;
			}
			return PathNodeType.OPEN;
		}

		// If the block below is scaffolding/construction and current block is passable, it is walkable ground
		BlockState belowState = context.getBlockState(pos.down());
		if (isScaffoldOrConstruction(belowState)) {
			if (state.isAir() || state.canPathfindThrough(NavigationType.LAND)) {
				return PathNodeType.WALKABLE;
			}
		}

		return super.getDefaultNodeType(context, x, y, z);
	}

	@Override
	public PathNodeType getNodeType(PathContext context, int x, int y, int z, MobEntity mob) {
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = context.getBlockState(pos);

		// Scaffolding or construction block traversal
		if (isScaffoldOrConstruction(state)) {
			BlockState below = context.getBlockState(pos.down());
			if (isScaffoldOrConstruction(below) || (!below.isAir() && below.isOpaque())) {
				return PathNodeType.WALKABLE;
			}
			return PathNodeType.OPEN;
		}

		// Surface of scaffolding or construction block
		BlockState belowState = context.getBlockState(pos.down());
		if (isScaffoldOrConstruction(belowState)) {
			if (state.isAir() || state.canPathfindThrough(NavigationType.LAND)) {
				return PathNodeType.WALKABLE;
			}
		}

		return super.getNodeType(context, x, y, z, mob);
	}
}
