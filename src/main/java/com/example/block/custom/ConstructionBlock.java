package com.example.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;

/**
 * Ephemeral construction block used by minions and commanders for elevated scaffolding,
 * ravine bridging, and temporary structural staging.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li><b>Solid-top:</b> Full cube collision shape preventing entities from sinking or falling through.</li>
 *   <li><b>Non-collapsing:</b> Does not rely on horizontal distance checks or gravity limits (unlike vanilla ScaffoldingBlock).</li>
 *   <li><b>Easily breakable:</b> Low hardness (0.2F) allowing rapid, effortless teardown.</li>
 *   <li><b>Drop-nothing behavior:</b> Drops zero items upon breaking, preventing inventory clutter or ground item lag.</li>
 * </ul>
 */
public class ConstructionBlock extends Block {

	public static final MapCodec<ConstructionBlock> CODEC = createCodec(ConstructionBlock::new);

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
}
