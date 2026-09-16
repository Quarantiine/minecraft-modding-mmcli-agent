package com.example.entity.ai.logistics;

import com.example.construction.ConstructionSession;
import com.example.entity.custom.MinionEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provides autonomous material harvesting, agro-forestry tree cultivation, tool self-crafting,
 * and automated supply depot chest placement for Builder minions in Survival mode.
 */
public final class MinionHarvestingHelper {

	private static final int HARVEST_SEARCH_RADIUS = 16;
	private static final int BASE_BUFFER_RADIUS = 12;

	private MinionHarvestingHelper() {}

	/**
	 * Attempts to autonomously harvest or craft the required material for an active blueprint task.
	 *
	 * @param minion       The builder minion.
	 * @param world        The server world.
	 * @param requiredItem The item needed for construction.
	 * @param session      The active construction session.
	 * @return True if the required item was obtained and placed in the minion's inventory.
	 */
	public static boolean tryAutonomousHarvest(
		MinionEntity minion,
		ServerWorld world,
		Item requiredItem,
		ConstructionSession session
	) {
		if (minion == null || world == null || requiredItem == null) {
			return false;
		}

		// 1. Tool check & self-crafting: ensure appropriate tool is equipped or crafted
		ensureAppropriateTool(minion, world, requiredItem);

		// 2. Wood / Timber / Planks Harvesting & Agro-Forestry
		if (isWoodResource(requiredItem)) {
			if (harvestWoodOrAgroForestry(minion, world, session)) {
				// Convert to planks if required item is planks
				if (isPlanksItem(requiredItem)) {
					convertLogsToPlanks(minion, requiredItem);
				}
				return hasItemInInventory(minion, requiredItem);
			}
		}

		// 3. Natural Stone / Cobblestone / Deepslate / Earth Quarrying
		if (isQuarryResource(requiredItem)) {
			if (quarryNaturalStone(minion, world, requiredItem, session)) {
				return hasItemInInventory(minion, requiredItem);
			}
		}

		return false;
	}

	/**
	 * Checks minion inventory capacity and deposits surplus non-blueprint materials into nearby chests.
	 * If no chest exists or nearby containers are full, crafts and places an autonomous Chest / Double Chest.
	 *
	 * @param minion  The builder minion.
	 * @param world   The server world.
	 * @param session The active construction session.
	 */
	public static void checkAndDepositExcessMaterials(
		MinionEntity minion,
		ServerWorld world,
		ConstructionSession session
	) {
		if (minion == null || world == null) {
			return;
		}

		SimpleInventory inv = minion.getInventory();
		int occupiedSlots = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (!inv.getStack(i).isEmpty()) {
				occupiedSlots++;
			}
		}

		// Only deposit if inventory is near capacity (>= 7 of 9 slots full)
		if (occupiedSlots < 7) {
			return;
		}

		// Identify excess stacks
		List<Integer> excessSlots = new ArrayList<>();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty()) continue;
			if (isExcessItem(stack.getItem(), session)) {
				excessSlots.add(i);
			}
		}

		if (excessSlots.isEmpty()) {
			return;
		}

		// 1. Look for existing container within 12 blocks
		BlockPos minionPos = minion.getBlockPos();
		BlockPos targetChestPos = findNearbyChestWithSpace(world, minionPos, 12);

		// 2. If no container with space found, attempt autonomous chest crafting and deployment
		if (targetChestPos == null) {
			targetChestPos = deployAutonomousChest(minion, world, session);
		}

		// 3. Deposit excess items into the container
		if (targetChestPos != null) {
			BlockEntity be = world.getBlockEntity(targetChestPos);
			if (be instanceof Inventory containerInv) {
				for (int slotIndex : excessSlots) {
					ItemStack stack = inv.getStack(slotIndex);
					if (stack.isEmpty()) continue;

					for (int cSlot = 0; cSlot < containerInv.size(); cSlot++) {
						ItemStack destStack = containerInv.getStack(cSlot);
						if (destStack.isEmpty()) {
							containerInv.setStack(cSlot, stack.copy());
							inv.setStack(slotIndex, ItemStack.EMPTY);
							break;
						} else if (ItemStack.areItemsAndComponentsEqual(destStack, stack)) {
							int transferable = Math.min(stack.getCount(), destStack.getMaxCount() - destStack.getCount());
							if (transferable > 0) {
								destStack.increment(transferable);
								stack.decrement(transferable);
								if (stack.isEmpty()) {
									inv.setStack(slotIndex, ItemStack.EMPTY);
									break;
								}
							}
						}
					}
				}
				containerInv.markDirty();
				inv.markDirty();

				world.playSound(null, targetChestPos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.8F, 1.0F);
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetChestPos.getX() + 0.5D, targetChestPos.getY() + 0.8D, targetChestPos.getZ() + 0.5D, 6, 0.3D, 0.2D, 0.3D, 0.02D);
			}
		}
	}

	private static BlockPos deployAutonomousChest(MinionEntity minion, ServerWorld world, ConstructionSession session) {
		// Ensure minion has or can craft a chest
		if (!hasItemInInventory(minion, Items.CHEST)) {
			// Try crafting chest: 8 planks needed
			int totalPlanks = countItemInInventory(minion.getInventory(), ItemTags.PLANKS);
			if (totalPlanks < 8) {
				// Convert logs to planks if available
				int logs = countItemInInventory(minion.getInventory(), ItemTags.LOGS);
				if (logs >= 2) {
					consumeItemFromInventory(minion.getInventory(), ItemTags.LOGS, 2);
					minion.getInventory().addStack(new ItemStack(Items.OAK_PLANKS, 8));
					totalPlanks += 8;
				}
			}
			if (totalPlanks >= 8) {
				consumeItemFromInventory(minion.getInventory(), ItemTags.PLANKS, 8);
				minion.getInventory().addStack(new ItemStack(Items.CHEST, 1));
			}
		}

		if (!hasItemInInventory(minion, Items.CHEST)) {
			return null;
		}

		// Find a solid ground spot within 6 blocks
		BlockPos minionPos = minion.getBlockPos();
		BlockPos candidate = null;
		for (BlockPos pos : BlockPos.iterateOutwards(minionPos, 5, 2, 5)) {
			if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
				if (!isProtectedBlock(world, pos, session, minion.getOwnerUuid())) {
					candidate = pos.toImmutable();
					break;
				}
			}
		}

		if (candidate == null) {
			return null;
		}

		// Check if adjacent block is a single chest to form a Double Chest
		Direction doubleChestFacing = null;
		ChestType chestType = ChestType.SINGLE;

		for (Direction dir : Direction.Type.HORIZONTAL) {
			BlockPos neighborPos = candidate.offset(dir);
			BlockState neighborState = world.getBlockState(neighborPos);
			if (neighborState.isOf(Blocks.CHEST) && neighborState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
				Direction facing = neighborState.get(ChestBlock.FACING);
				if (dir == facing.rotateYClockwise()) {
					// Neighbor is right side, candidate is left
					chestType = ChestType.LEFT;
					doubleChestFacing = facing;
					world.setBlockState(neighborPos, neighborState.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT), Block.NOTIFY_ALL);
					break;
				} else if (dir == facing.rotateYCounterclockwise()) {
					// Neighbor is left side, candidate is right
					chestType = ChestType.RIGHT;
					doubleChestFacing = facing;
					world.setBlockState(neighborPos, neighborState.with(ChestBlock.CHEST_TYPE, ChestType.LEFT), Block.NOTIFY_ALL);
					break;
				}
			}
		}

		BlockState chestState = Blocks.CHEST.getDefaultState();
		if (doubleChestFacing != null) {
			chestState = chestState.with(ChestBlock.FACING, doubleChestFacing).with(ChestBlock.CHEST_TYPE, chestType);
		} else {
			chestState = chestState.with(ChestBlock.FACING, Direction.NORTH);
		}

		world.setBlockState(candidate, chestState, Block.NOTIFY_ALL);
		consumeItemFromInventory(minion.getInventory(), Items.CHEST, 1);

		world.playSound(null, candidate, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		return candidate;
	}

	private static BlockPos findNearbyChestWithSpace(ServerWorld world, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.iterateOutwards(center, radius, 3, radius)) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.BARREL)) {
				BlockEntity be = world.getBlockEntity(pos);
				if (be instanceof Inventory inv) {
					for (int i = 0; i < inv.size(); i++) {
						if (inv.getStack(i).isEmpty()) {
							return pos.toImmutable();
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isExcessItem(Item item, ConstructionSession session) {
		if (item == Items.CHEST) return false;
		if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem) return false;
		// If session requires this item, it is not excess
		if (session != null) {
			for (var task : session.getTasks()) {
				if (!task.isCompleted() && task.getBlueprintBlock().getRequiredItem() == item) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean harvestWoodOrAgroForestry(
		MinionEntity minion,
		ServerWorld world,
		ConstructionSession session
	) {
		BlockPos minionPos = minion.getBlockPos();

		// 1. Scan for existing natural logs within 16 blocks
		for (BlockPos pos : BlockPos.iterateOutwards(minionPos, HARVEST_SEARCH_RADIUS, 8, HARVEST_SEARCH_RADIUS)) {
			BlockState state = world.getBlockState(pos);
			if (state.isIn(BlockTags.LOGS) && !isProtectedBlock(world, pos, session, minion.getOwnerUuid())) {
				if (!hasAdjacentLava(world, pos)) {
					fellLog(minion, world, pos.toImmutable(), state);
					return true;
				}
			}
		}

		// 2. Agro-Forestry: plant sapling and accelerate growth using bone meal
		Item saplingItem = findSaplingInInventory(minion.getInventory());
		if (saplingItem != null) {
			BlockPos plantPos = findPlantingPosition(world, minionPos, session, minion.getOwnerUuid());
			if (plantPos != null) {
				Block saplingBlock = Block.getBlockFromItem(saplingItem);
				if (saplingBlock != null && saplingBlock != Blocks.AIR) {
					world.setBlockState(plantPos, saplingBlock.getDefaultState(), Block.NOTIFY_ALL);
					consumeItemFromInventory(minion.getInventory(), saplingItem, 1);

					// Apply bone meal if minion has it
					if (hasItemInInventory(minion, Items.BONE_MEAL)) {
						BlockState currentSaplingState = world.getBlockState(plantPos);
						if (currentSaplingState.getBlock() instanceof Fertilizable fertilizable) {
							fertilizable.grow(world, world.random, plantPos, currentSaplingState);
							consumeItemFromInventory(minion.getInventory(), Items.BONE_MEAL, 1);

							world.playSound(null, plantPos, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
							world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, plantPos.getX() + 0.5D, plantPos.getY() + 0.5D, plantPos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.05D);
						}
					}

					// Check if tree grew at or above plantPos
					BlockState resultState = world.getBlockState(plantPos);
					if (resultState.isIn(BlockTags.LOGS)) {
						fellLog(minion, world, plantPos, resultState);
						// Replenish a sapling for sustainable forestry
						minion.getInventory().addStack(new ItemStack(saplingItem, 1));
						return true;
					} else {
						// Even if not fully grown yet, minion gathers initial timber
						minion.getInventory().addStack(new ItemStack(Items.OAK_LOG, 2));
						return true;
					}
				}
			}
		}

		return false;
	}

	private static void fellLog(MinionEntity minion, ServerWorld world, BlockPos pos, BlockState state) {
		world.breakBlock(pos, false, minion);
		Item dropItem = state.getBlock().asItem();
		if (dropItem == Items.AIR) {
			dropItem = Items.OAK_LOG;
		}
		minion.getInventory().addStack(new ItemStack(dropItem, 1));

		// Damage equipped axe if held
		ItemStack mainhand = minion.getMainHandStack();
		if (mainhand.getItem() instanceof AxeItem) {
			mainhand.damage(1, minion, EquipmentSlot.MAINHAND);
		}

		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 10, 0.2D, 0.2D, 0.2D, 0.1D);
		world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
	}

	private static boolean quarryNaturalStone(
		MinionEntity minion,
		ServerWorld world,
		Item requiredItem,
		ConstructionSession session
	) {
		BlockPos minionPos = minion.getBlockPos();

		for (BlockPos pos : BlockPos.iterateOutwards(minionPos, HARVEST_SEARCH_RADIUS, 10, HARVEST_SEARCH_RADIUS)) {
			BlockState state = world.getBlockState(pos);
			if (isNaturalStoneOrEarth(state) && !isProtectedBlock(world, pos, session, minion.getOwnerUuid())) {
				if (!hasAdjacentLava(world, pos)) {
					world.breakBlock(pos, false, minion);

					Item dropItem = resolveQuarryDrop(state, requiredItem);
					minion.getInventory().addStack(new ItemStack(dropItem, 1));

					// Damage pickaxe/shovel if held
					ItemStack mainhand = minion.getMainHandStack();
					if (mainhand.getItem() instanceof PickaxeItem || mainhand.getItem() instanceof ShovelItem) {
						mainhand.damage(1, minion, EquipmentSlot.MAINHAND);
					}

					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.1D);
					world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isNaturalStoneOrEarth(BlockState state) {
		return state.isOf(Blocks.STONE)
			|| state.isOf(Blocks.COBBLESTONE)
			|| state.isOf(Blocks.DEEPSLATE)
			|| state.isOf(Blocks.COBBLED_DEEPSLATE)
			|| state.isOf(Blocks.ANDESITE)
			|| state.isOf(Blocks.DIORITE)
			|| state.isOf(Blocks.GRANITE)
			|| state.isOf(Blocks.DIRT)
			|| state.isOf(Blocks.SAND)
			|| state.isOf(Blocks.GRAVEL)
			|| state.isOf(Blocks.SANDSTONE);
	}

	private static Item resolveQuarryDrop(BlockState state, Item requiredItem) {
		if (state.isOf(Blocks.STONE)) {
			return requiredItem == Items.STONE ? Items.STONE : Items.COBBLESTONE;
		}
		if (state.isOf(Blocks.DEEPSLATE)) {
			return requiredItem == Items.DEEPSLATE ? Items.DEEPSLATE : Items.COBBLED_DEEPSLATE;
		}
		return state.getBlock().asItem();
	}

	/**
	 * Comprehensive build protection rules:
	 * 1. Never break blocks inside the active blueprint or any other active session of the owner.
	 * 2. Never break human-crafted or processed architectural blocks (planks, bricks, slabs, glass, etc.).
	 * 3. Never break blocks within 12 blocks of beds, chests, or respawn anchors.
	 * 4. Never break indestructible blocks (bedrock, barriers).
	 */
	public static boolean isProtectedBlock(
		ServerWorld world,
		BlockPos pos,
		ConstructionSession session,
		UUID ownerUuid
	) {
		BlockState state = world.getBlockState(pos);

		// Bedrock and indestructible blocks
		if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.BARRIER) || state.getHardness(world, pos) < 0.0F) {
			return true;
		}

		// Inside current session bounding box or tasks
		if (session != null && session.getWorldBoundingBox().contains(pos)) {
			return true;
		}

		// Processed/crafted blocks check
		String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
		if (isProcessedBlockId(blockId)) {
			return true;
		}

		// Within 12-block buffer of player beds, chests, or respawn anchors
		for (BlockPos neighbor : BlockPos.iterateOutwards(pos, BASE_BUFFER_RADIUS, 4, BASE_BUFFER_RADIUS)) {
			BlockState neighborState = world.getBlockState(neighbor);
			if (neighborState.isIn(BlockTags.BEDS)
				|| neighborState.isOf(Blocks.CHEST)
				|| neighborState.isOf(Blocks.TRAPPED_CHEST)
				|| neighborState.isOf(Blocks.BARREL)
				|| neighborState.isOf(Blocks.RESPAWN_ANCHOR)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isProcessedBlockId(String path) {
		return path.contains("planks")
			|| path.contains("brick")
			|| path.contains("slab")
			|| path.contains("stair")
			|| path.contains("glass")
			|| path.contains("terracotta")
			|| path.contains("concrete")
			|| path.contains("wool")
			|| path.contains("carpet")
			|| path.contains("door")
			|| path.contains("trapdoor")
			|| path.contains("fence")
			|| path.contains("wall")
			|| path.contains("gate")
			|| path.contains("lantern")
			|| path.contains("torch")
			|| path.contains("chest")
			|| path.contains("barrel")
			|| path.contains("shulker")
			|| path.contains("furnace")
			|| path.contains("crafting_table");
	}

	/**
	 * Hazard avoidance: skips any block whose 6 immediate orthogonal neighbors include lava.
	 */
	public static boolean hasAdjacentLava(ServerWorld world, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = pos.offset(dir);
			BlockState state = world.getBlockState(neighbor);
			if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.LAVA_CAULDRON) || world.getFluidState(neighbor).isIn(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	private static void ensureAppropriateTool(MinionEntity minion, ServerWorld world, Item requiredItem) {
		ItemStack mainhand = minion.getMainHandStack();

		boolean needsPickaxe = isQuarryResource(requiredItem);
		boolean needsAxe = isWoodResource(requiredItem);

		if (needsPickaxe && !(mainhand.getItem() instanceof PickaxeItem)) {
			// Check inventory for pickaxe
			int pickSlot = findToolSlot(minion.getInventory(), PickaxeItem.class);
			if (pickSlot != -1) {
				ItemStack pick = minion.getInventory().getStack(pickSlot);
				minion.equipStack(EquipmentSlot.MAINHAND, pick);
			} else {
				// Craft wooden or stone pickaxe
				craftPickaxe(minion, world);
			}
		} else if (needsAxe && !(mainhand.getItem() instanceof AxeItem)) {
			int axeSlot = findToolSlot(minion.getInventory(), AxeItem.class);
			if (axeSlot != -1) {
				ItemStack axe = minion.getInventory().getStack(axeSlot);
				minion.equipStack(EquipmentSlot.MAINHAND, axe);
			} else {
				// Craft wooden axe
				craftAxe(minion, world);
			}
		}
	}

	private static void craftPickaxe(MinionEntity minion, ServerWorld world) {
		ensureSticksAndPlanks(minion);
		SimpleInventory inv = minion.getInventory();

		// Prefer stone pickaxe if cobblestone available
		int cobble = countItemInInventory(inv, Items.COBBLESTONE);
		int sticks = countItemInInventory(inv, Items.STICK);
		if (cobble >= 3 && sticks >= 2) {
			consumeItemFromInventory(inv, Items.COBBLESTONE, 3);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
			playCraftingFeedback(minion, world);
			return;
		}

		int planks = countItemInInventory(inv, ItemTags.PLANKS);
		if (planks >= 3 && sticks >= 2) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 3);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
			playCraftingFeedback(minion, world);
		}
	}

	private static void craftAxe(MinionEntity minion, ServerWorld world) {
		ensureSticksAndPlanks(minion);
		SimpleInventory inv = minion.getInventory();

		int planks = countItemInInventory(inv, ItemTags.PLANKS);
		int sticks = countItemInInventory(inv, Items.STICK);
		if (planks >= 3 && sticks >= 2) {
			consumeItemFromInventory(inv, ItemTags.PLANKS, 3);
			consumeItemFromInventory(inv, Items.STICK, 2);
			minion.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
			playCraftingFeedback(minion, world);
		}
	}

	private static void ensureSticksAndPlanks(MinionEntity minion) {
		SimpleInventory inv = minion.getInventory();
		// If sticks < 2 and planks >= 2, craft 4 sticks
		if (countItemInInventory(inv, Items.STICK) < 2) {
			if (countItemInInventory(inv, ItemTags.PLANKS) >= 2) {
				consumeItemFromInventory(inv, ItemTags.PLANKS, 2);
				inv.addStack(new ItemStack(Items.STICK, 4));
			} else if (countItemInInventory(inv, ItemTags.LOGS) >= 1) {
				consumeItemFromInventory(inv, ItemTags.LOGS, 1);
				inv.addStack(new ItemStack(Items.OAK_PLANKS, 4));
				consumeItemFromInventory(inv, ItemTags.PLANKS, 2);
				inv.addStack(new ItemStack(Items.STICK, 4));
			}
		}

		// If planks < 3 and minion has logs, convert 1 log into 4 planks
		if (countItemInInventory(inv, ItemTags.PLANKS) < 3 && countItemInInventory(inv, ItemTags.LOGS) >= 1) {
			consumeItemFromInventory(inv, ItemTags.LOGS, 1);
			inv.addStack(new ItemStack(Items.OAK_PLANKS, 4));
		}
	}

	private static void playCraftingFeedback(MinionEntity minion, ServerWorld world) {
		world.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.8F, 1.2F);
		world.spawnParticles(ParticleTypes.CRIT, minion.getX(), minion.getY() + 1.0D, minion.getZ(), 5, 0.2D, 0.2D, 0.2D, 0.05D);
	}

	private static void convertLogsToPlanks(MinionEntity minion, Item requiredPlanks) {
		SimpleInventory inv = minion.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isIn(ItemTags.LOGS) && stack.getCount() > 0) {
				stack.decrement(1);
				inv.addStack(new ItemStack(requiredPlanks, 4));
				inv.markDirty();
				return;
			}
		}
	}

	private static BlockPos findPlantingPosition(ServerWorld world, BlockPos center, ConstructionSession session, UUID ownerUuid) {
		for (BlockPos pos : BlockPos.iterateOutwards(center, 8, 2, 8)) {
			BlockState below = world.getBlockState(pos.down());
			if ((below.isOf(Blocks.DIRT) || below.isOf(Blocks.GRASS_BLOCK)) && world.getBlockState(pos).isAir()) {
				// Ensure clearance above for tree canopy
				boolean clear = true;
				for (int y = 1; y <= 5; y++) {
					if (!world.getBlockState(pos.up(y)).isAir()) {
						clear = false;
						break;
					}
				}
				if (clear && !isProtectedBlock(world, pos, session, ownerUuid)) {
					return pos.toImmutable();
				}
			}
		}
		return null;
	}

	private static Item findSaplingInInventory(SimpleInventory inv) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(ItemTags.SAPLINGS)) {
				return stack.getItem();
			}
		}
		return Items.OAK_SAPLING; // Default fallback sapling for sustainable forestry
	}

	private static int findToolSlot(SimpleInventory inv, Class<?> toolClass) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && toolClass.isInstance(stack.getItem())) {
				return i;
			}
		}
		return -1;
	}

	private static boolean hasItemInInventory(MinionEntity minion, Item item) {
		SimpleInventory inv = minion.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				return true;
			}
		}
		return false;
	}

	private static int countItemInInventory(SimpleInventory inv, Item item) {
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int countItemInInventory(SimpleInventory inv, net.minecraft.registry.tag.TagKey<Item> tag) {
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(tag)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void consumeItemFromInventory(SimpleInventory inv, Item item, int amount) {
		for (int i = 0; i < inv.size() && amount > 0; i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				int consumed = Math.min(stack.getCount(), amount);
				stack.decrement(consumed);
				amount -= consumed;
			}
		}
		inv.markDirty();
	}

	private static void consumeItemFromInventory(SimpleInventory inv, net.minecraft.registry.tag.TagKey<Item> tag, int amount) {
		for (int i = 0; i < inv.size() && amount > 0; i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isIn(tag)) {
				int consumed = Math.min(stack.getCount(), amount);
				stack.decrement(consumed);
				amount -= consumed;
			}
		}
		inv.markDirty();
	}

	private static boolean isWoodResource(Item item) {
		return item.getDefaultStack().isIn(ItemTags.LOGS)
			|| item.getDefaultStack().isIn(ItemTags.PLANKS)
			|| item.getDefaultStack().isIn(ItemTags.WOODEN_STAIRS)
			|| item.getDefaultStack().isIn(ItemTags.WOODEN_SLABS)
			|| item.getDefaultStack().isIn(ItemTags.WOODEN_DOORS);
	}

	private static boolean isPlanksItem(Item item) {
		return item.getDefaultStack().isIn(ItemTags.PLANKS);
	}

	private static boolean isQuarryResource(Item item) {
		return item == Items.COBBLESTONE
			|| item == Items.STONE
			|| item == Items.DEEPSLATE
			|| item == Items.COBBLED_DEEPSLATE
			|| item == Items.ANDESITE
			|| item == Items.DIORITE
			|| item == Items.GRANITE
			|| item == Items.DIRT
			|| item == Items.SAND
			|| item == Items.GRAVEL
			|| item == Items.SANDSTONE;
	}
}
