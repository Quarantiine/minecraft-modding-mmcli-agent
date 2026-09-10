package com.example.item;

import com.example.ExampleMod;
import com.example.item.custom.TntStickItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Registry class for mod items adhering to the recommended Fabric architecture.
 * Manages item initialization, custom item helper registration, and creative tab placement.
 */
public class ModItems {

	/**
	 * TNT Stick item that launches explosive TNT projectiles.
	 */
	public static final Item TNT_STICK = registerItem(
		"tnt_stick",
		new TntStickItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC))
	);

	/**
	 * Helper method to register an item in the Minecraft item registry under the mod namespace.
	 *
	 * @param name The registry path name for the item.
	 * @param item The item instance to register.
	 * @return The registered item instance.
	 */
	private static Item registerItem(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(ExampleMod.MOD_ID, name), item);
	}

	/**
	 * Registers mod items into creative inventory tabs and logs item registration progress.
	 * Called during common mod initialization.
	 */
	public static void registerModItems() {
		ExampleMod.LOGGER.info("Registering Mod Items for {}", ExampleMod.MOD_ID);

		// Add TNT Stick to the Combat item group / creative tab
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(TNT_STICK);
		});
	}
}
