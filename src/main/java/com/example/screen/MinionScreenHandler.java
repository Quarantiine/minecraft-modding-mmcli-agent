package com.example.screen;

import com.example.entity.custom.MinionEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

/**
 * ScreenHandler for minion entity management.
 * Provides 6 equipment slots (Head, Chest, Legs, Feet, Mainhand, Offhand),
 * 9 minion inventory slots (3x3 grid), and 36 player inventory slots (main inventory + hotbar).
 * Supports quickMove (shift-clicking) item transfers and synchronized equipment updating.
 */
public class MinionScreenHandler extends ScreenHandler {

	public static final int EQUIPMENT_SLOT_COUNT = 6;
	public static final int MINION_INVENTORY_SLOT_COUNT = 9;
	public static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
	public static final int PLAYER_HOTBAR_SLOT_COUNT = 9;

	public static final int HEAD_SLOT_INDEX = 0;
	public static final int CHEST_SLOT_INDEX = 1;
	public static final int LEGS_SLOT_INDEX = 2;
	public static final int FEET_SLOT_INDEX = 3;
	public static final int MAINHAND_SLOT_INDEX = 4;
	public static final int OFFHAND_SLOT_INDEX = 5;

	public static final int MINION_INV_START = 6;
	public static final int MINION_INV_END = 15; // 6 + 9 = 15 exclusive
	public static final int PLAYER_INV_START = 15;
	public static final int PLAYER_INV_END = 42; // 15 + 27 = 42 exclusive
	public static final int PLAYER_HOTBAR_START = 42;
	public static final int PLAYER_HOTBAR_END = 51; // 42 + 9 = 51 exclusive

	private final Inventory minionInventory;
	private final Inventory equipmentInventory;
	private final MinionEntity minion;
	private final int minionId;
	private final PlayerEntity player;

	/**
	 * Client-side / network factory constructor invoked by {@link net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType}.
	 *
	 * @param syncId          The window synchronization ID.
	 * @param playerInventory The opening player's inventory.
	 * @param minionId        The entity ID of the target minion.
	 */
	public MinionScreenHandler(int syncId, PlayerInventory playerInventory, int minionId) {
		this(syncId, playerInventory, resolveMinionEntity(playerInventory, minionId), minionId);
	}

	/**
	 * Fallback / test constructor with default dummy inventory.
	 *
	 * @param syncId          The window synchronization ID.
	 * @param playerInventory The opening player's inventory.
	 */
	public MinionScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(MinionEntity.INVENTORY_SIZE), null, -1);
	}

	/**
	 * Server-side constructor when minion entity and inventory are directly available.
	 *
	 * @param syncId          The window synchronization ID.
	 * @param playerInventory The opening player's inventory.
	 * @param minionInventory The minion's 9-slot inventory.
	 * @param minion          The minion entity instance.
	 */
	public MinionScreenHandler(int syncId, PlayerInventory playerInventory, Inventory minionInventory, MinionEntity minion) {
		this(
			syncId,
			playerInventory,
			minionInventory != null ? minionInventory : (minion != null ? minion.getInventory() : new SimpleInventory(MinionEntity.INVENTORY_SIZE)),
			minion,
			minion != null ? minion.getId() : -1
		);
	}

	/**
	 * Internal helper constructor resolving minion entity.
	 */
	private MinionScreenHandler(int syncId, PlayerInventory playerInventory, MinionEntity minion, int minionId) {
		this(
			syncId,
			playerInventory,
			minion != null ? minion.getInventory() : new SimpleInventory(MinionEntity.INVENTORY_SIZE),
			minion,
			minionId
		);
	}

	/**
	 * Master constructor initializing all equipment, minion inventory, and player slots.
	 */
	private MinionScreenHandler(
		int syncId,
		PlayerInventory playerInventory,
		Inventory minionInventory,
		MinionEntity minion,
		int minionId
	) {
		super(ModScreenHandlers.MINION_SCREEN_HANDLER, syncId);
		checkSize(minionInventory, MINION_INVENTORY_SLOT_COUNT);

		this.player = playerInventory.player;
		this.minion = minion;
		this.minionId = minionId;
		this.minionInventory = minionInventory;
		this.equipmentInventory = new MinionEquipmentInventory(minion);

		minionInventory.onOpen(playerInventory.player);

		// 1. Minion Equipment Slots (0..5)
		// Head (0)
		this.addSlot(new Slot(this.equipmentInventory, HEAD_SLOT_INDEX, 8, 18) {
			@Override
			public int getMaxItemCount() {
				return 1;
			}

			@Override
			public boolean canInsert(ItemStack stack) {
				return canEquipSlot(stack, EquipmentSlot.HEAD);
			}

			@Override
			public Pair<Identifier, Identifier> getBackgroundSprite() {
				return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE);
			}
		});

		// Chest (1)
		this.addSlot(new Slot(this.equipmentInventory, CHEST_SLOT_INDEX, 8, 36) {
			@Override
			public int getMaxItemCount() {
				return 1;
			}

			@Override
			public boolean canInsert(ItemStack stack) {
				return canEquipSlot(stack, EquipmentSlot.CHEST);
			}

			@Override
			public Pair<Identifier, Identifier> getBackgroundSprite() {
				return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE);
			}
		});

		// Legs (2)
		this.addSlot(new Slot(this.equipmentInventory, LEGS_SLOT_INDEX, 8, 54) {
			@Override
			public int getMaxItemCount() {
				return 1;
			}

			@Override
			public boolean canInsert(ItemStack stack) {
				return canEquipSlot(stack, EquipmentSlot.LEGS);
			}

			@Override
			public Pair<Identifier, Identifier> getBackgroundSprite() {
				return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE);
			}
		});

		// Feet (3)
		this.addSlot(new Slot(this.equipmentInventory, FEET_SLOT_INDEX, 26, 54) {
			@Override
			public int getMaxItemCount() {
				return 1;
			}

			@Override
			public boolean canInsert(ItemStack stack) {
				return canEquipSlot(stack, EquipmentSlot.FEET);
			}

			@Override
			public Pair<Identifier, Identifier> getBackgroundSprite() {
				return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE);
			}
		});

		// Mainhand (4)
		this.addSlot(new Slot(this.equipmentInventory, MAINHAND_SLOT_INDEX, 26, 36) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return true;
			}
		});

		// Offhand (5)
		this.addSlot(new Slot(this.equipmentInventory, OFFHAND_SLOT_INDEX, 26, 18) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return true;
			}

			@Override
			public Pair<Identifier, Identifier> getBackgroundSprite() {
				return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_OFFHAND_ARMOR_SLOT);
			}
		});

		// 2. Minion Inventory Slots (6..14, 3x3 grid)
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 3; ++col) {
				int index = col + row * 3;
				int x = 116 + col * 18;
				int y = 18 + row * 18;
				this.addSlot(new Slot(this.minionInventory, index, x, y));
			}
		}

		// 3. Player Inventory Slots (15..41, 3x9 grid)
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				int index = col + row * 9 + 9;
				int x = 8 + col * 18;
				int y = 84 + row * 18;
				this.addSlot(new Slot(playerInventory, index, x, y));
			}
		}

		// 4. Player Hotbar Slots (42..50, 1x9 grid)
		for (int col = 0; col < 9; ++col) {
			int x = 8 + col * 18;
			int y = 142;
			this.addSlot(new Slot(playerInventory, col, x, y));
		}
	}

	private static MinionEntity resolveMinionEntity(PlayerInventory playerInventory, int minionId) {
		if (playerInventory != null && playerInventory.player != null && playerInventory.player.getWorld() != null && minionId >= 0) {
			Entity entity = playerInventory.player.getWorld().getEntityById(minionId);
			if (entity instanceof MinionEntity minionEntity) {
				return minionEntity;
			}
		}
		return null;
	}

	/**
	 * Determines if the given stack is a weapon or tool eligible for auto-equipping into the mainhand slot.
	 *
	 * @param stack The item stack to check.
	 * @return True if the item is a sword, mining tool, ranged weapon, trident, or mace.
	 */
	public static boolean isWeaponOrTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof SwordItem
			|| stack.getItem() instanceof MiningToolItem
			|| stack.getItem() instanceof RangedWeaponItem
			|| stack.getItem() instanceof TridentItem
			|| stack.getItem() instanceof MaceItem;
	}

	/**
	 * Determines if the given stack is a defensive offhand item eligible for the offhand slot.
	 *
	 * @param stack The item stack to check.
	 * @return True if the item is a shield or totem of undying.
	 */
	public static boolean isShieldOrTotem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof ShieldItem || stack.isOf(Items.TOTEM_OF_UNDYING);
	}

	private boolean canEquipSlot(ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty()) {
			return false;
		}
		if (slot == EquipmentSlot.MAINHAND) {
			return isWeaponOrTool(stack);
		}
		if (slot == EquipmentSlot.OFFHAND) {
			return isShieldOrTotem(stack);
		}
		if (this.minion != null) {
			return this.minion.getPreferredEquipmentSlot(stack) == slot;
		}
		return this.player.getPreferredEquipmentSlot(stack) == slot;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (this.minion != null) {
			return this.minion.isAlive() && this.minion.squaredDistanceTo(player) <= 64.0D;
		}
		return this.minionInventory.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.minionInventory.onClose(player);
		if (this.minion != null) {
			this.minion.autoEquipFromInventory();
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);

		if (slot != null && slot.hasStack()) {
			ItemStack originalStack = slot.getStack();
			newStack = originalStack.copy();

			// 1. From Equipment (0..5) or Minion Inventory (6..14) -> Move to Player Inventory/Hotbar (15..51)
			if (slotIndex < MINION_INV_END) {
				if (!this.insertItem(originalStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			}
			// 2. From Player Inventory (15..41) or Hotbar (42..50)
			else {
				boolean inserted = false;

				// Try to equip if armor, weapon, or offhand item
				EquipmentSlot preferredSlot = this.minion != null
					? this.minion.getPreferredEquipmentSlot(originalStack)
					: player.getPreferredEquipmentSlot(originalStack);

				if (preferredSlot == EquipmentSlot.HEAD && !this.slots.get(HEAD_SLOT_INDEX).hasStack()) {
					inserted = this.insertItem(originalStack, HEAD_SLOT_INDEX, HEAD_SLOT_INDEX + 1, false);
				} else if (preferredSlot == EquipmentSlot.CHEST && !this.slots.get(CHEST_SLOT_INDEX).hasStack()) {
					inserted = this.insertItem(originalStack, CHEST_SLOT_INDEX, CHEST_SLOT_INDEX + 1, false);
				} else if (preferredSlot == EquipmentSlot.LEGS && !this.slots.get(LEGS_SLOT_INDEX).hasStack()) {
					inserted = this.insertItem(originalStack, LEGS_SLOT_INDEX, LEGS_SLOT_INDEX + 1, false);
				} else if (preferredSlot == EquipmentSlot.FEET && !this.slots.get(FEET_SLOT_INDEX).hasStack()) {
					inserted = this.insertItem(originalStack, FEET_SLOT_INDEX, FEET_SLOT_INDEX + 1, false);
				} else if (isWeaponOrTool(originalStack) && !this.slots.get(MAINHAND_SLOT_INDEX).hasStack()) {
					inserted = this.insertItem(originalStack, MAINHAND_SLOT_INDEX, MAINHAND_SLOT_INDEX + 1, false);
				} else if (isShieldOrTotem(originalStack) && !this.slots.get(OFFHAND_SLOT_INDEX).hasStack()) {
					inserted = this.insertItem(originalStack, OFFHAND_SLOT_INDEX, OFFHAND_SLOT_INDEX + 1, false);
				}

				// If not equipped, try Minion Inventory (6..15)
				if (!inserted && !this.insertItem(originalStack, MINION_INV_START, MINION_INV_END, false)) {
					// Fallback: move between Player Inventory and Hotbar
					if (slotIndex >= PLAYER_INV_START && slotIndex < PLAYER_INV_END) {
						if (!this.insertItem(originalStack, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END, false)) {
							return ItemStack.EMPTY;
						}
					} else if (slotIndex >= PLAYER_HOTBAR_START && slotIndex < PLAYER_HOTBAR_END) {
						if (!this.insertItem(originalStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
							return ItemStack.EMPTY;
						}
					}
				}
			}

			if (originalStack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}

			if (originalStack.getCount() == newStack.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTakeItem(player, originalStack);
		}

		return newStack;
	}

	public MinionEntity getMinion() {
		return this.minion;
	}

	public int getMinionId() {
		return this.minionId;
	}

	public Inventory getMinionInventory() {
		return this.minionInventory;
	}

	public Inventory getEquipmentInventory() {
		return this.equipmentInventory;
	}

	/**
	 * Inventory wrapper bridging the 6 equipment slots to {@link MinionEntity#getEquippedStack(EquipmentSlot)}
	 * and {@link MinionEntity#equipStack(EquipmentSlot, ItemStack)}.
	 */
	public static class MinionEquipmentInventory implements Inventory {
		public static final EquipmentSlot[] SLOTS = new EquipmentSlot[] {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND,
			EquipmentSlot.OFFHAND
		};

		private final MinionEntity minion;
		private final DefaultedList<ItemStack> fallbackStacks = DefaultedList.ofSize(EQUIPMENT_SLOT_COUNT, ItemStack.EMPTY);

		public MinionEquipmentInventory(MinionEntity minion) {
			this.minion = minion;
		}

		@Override
		public int size() {
			return EQUIPMENT_SLOT_COUNT;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < EQUIPMENT_SLOT_COUNT; i++) {
				if (!getStack(i).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getStack(int slot) {
			if (slot < 0 || slot >= EQUIPMENT_SLOT_COUNT) {
				return ItemStack.EMPTY;
			}
			if (this.minion != null) {
				return this.minion.getEquippedStack(SLOTS[slot]);
			}
			return this.fallbackStacks.get(slot);
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			ItemStack current = getStack(slot);
			if (current.isEmpty() || amount <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack result = current.split(amount);
			setStack(slot, current);
			return result;
		}

		@Override
		public ItemStack removeStack(int slot) {
			ItemStack current = getStack(slot);
			if (current.isEmpty()) {
				return ItemStack.EMPTY;
			}
			setStack(slot, ItemStack.EMPTY);
			return current;
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			if (slot < 0 || slot >= EQUIPMENT_SLOT_COUNT) {
				return;
			}
			if (this.minion != null) {
				this.minion.equipStack(SLOTS[slot], stack);
			} else {
				this.fallbackStacks.set(slot, stack);
			}
		}

		@Override
		public void markDirty() {
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			if (this.minion != null) {
				return this.minion.isAlive() && this.minion.squaredDistanceTo(player) <= 64.0D;
			}
			return true;
		}

		@Override
		public void clear() {
			for (int i = 0; i < EQUIPMENT_SLOT_COUNT; i++) {
				setStack(i, ItemStack.EMPTY);
			}
		}
	}
}
