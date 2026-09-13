package com.example.block;

import com.example.ExampleMod;
import com.example.block.custom.ConstructionBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registry class for mod blocks adhering to the recommended Fabric architecture.
 * Manages block initialization, associated block items, and creative tab placement.
 */
public class ModBlocks {

	/**
	 * Solid-top, easily breakable, non-collapsing ephemeral construction block
	 * with drop-nothing behavior for minion scaffolding and bridging.
	 */
	public static final Block CONSTRUCTION_BLOCK = registerBlock(
		"construction_block",
		new ConstructionBlock(
			AbstractBlock.Settings.create()
				.strength(0.2F, 0.2F)
				.sounds(BlockSoundGroup.SCAFFOLDING)
				.dropsNothing()
				.nonOpaque()
		)
	);

	/**
	 * Helper method to register a block and its corresponding BlockItem in their respective registries.
	 *
	 * @param name  The registry path name for the block.
	 * @param block The block instance to register.
	 * @return The registered block instance.
	 */
	private static Block registerBlock(String name, Block block) {
		registerBlockItem(name, block);
		return Registry.register(Registries.BLOCK, Identifier.of(ExampleMod.MOD_ID, name), block);
	}

	/**
	 * Helper method to register a BlockItem for a registered block instance.
	 *
	 * @param name  The registry path name for the item.
	 * @param block The block instance to create an item for.
	 * @return The registered BlockItem instance.
	 */
	private static Item registerBlockItem(String name, Block block) {
		return Registry.register(
			Registries.ITEM,
			Identifier.of(ExampleMod.MOD_ID, name),
			new BlockItem(block, new Item.Settings())
		);
	}

	/**
	 * Registers mod blocks and logs block registration progress.
	 * Called during common mod initialization.
	 */
	public static void registerModBlocks() {
		ExampleMod.LOGGER.info("Registering Mod Blocks for {}", ExampleMod.MOD_ID);

		// Add Construction Block to the Building Blocks creative inventory tab
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			entries.add(CONSTRUCTION_BLOCK);
		});
	}
}
