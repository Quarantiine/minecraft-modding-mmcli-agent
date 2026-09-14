package com.example.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Ephemeral construction block used by minions and commanders for elevated scaffolding,
 * ravine bridging, and temporary structural staging.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li><b>Solid-top platform:</b> Solid collision face when standing above, empty inside for suffocation-free climbing.</li>
 *   <li><b>Non-collapsing:</b> Does not rely on horizontal distance checks or gravity limits (unlike vanilla ScaffoldingBlock).</li>
 *   <li><b>Easily breakable:</b> Low hardness (0.2F) allowing rapid, effortless teardown.</li>
 *   <li><b>Drop-nothing behavior:</b> Drops zero items upon breaking, preventing inventory clutter or ground item lag.</li>
 * </ul>
 */
public class ConstructionBlock extends Block {

	public static final MapCodec<ConstructionBlock> CODEC = createCodec(ConstructionBlock::new);
	private static final VoxelShape TOP_OUTLINE_SHAPE = Block.createCuboidShape(0.0D, 14.0D, 0.0D, 16.0D, 16.0D, 16.0D);

	/**
	 * Creates a new ConstructionBlock instance with the provided settings.
	 *
	 * @param settings Block configuration settings (strength, sounds, dropsNothing, etc.).
	 */
	public ConstructionBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<ConstructionBlock> getCodec() {
		return CODEC;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		if (context.isAbove(VoxelShapes.fullCube(), pos, true) && !context.isDescending()) {
			return TOP_OUTLINE_SHAPE;
		}
		return VoxelShapes.empty();
	}
}
