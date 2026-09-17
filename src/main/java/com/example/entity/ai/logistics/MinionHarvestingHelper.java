package com.example.entity.ai.logistics;

import com.example.construction.ConstructionSession;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.GlowSquidEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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

		// 4. Mob Material Procurement & In-Inventory Synthesis
		if (isMobProcurementResource(requiredItem)) {
			// First attempt in-inventory synthesis (e.g. bones -> bone meal, string -> wool)
			if (synthesizeMaterial(minion.getInventory(), requiredItem)) {
				return hasItemInInventory(minion, requiredItem);
			}
			// Attempt mob hunting or warrior contract commissioning
			if (tryAutonomousMobHunting(minion, world, requiredItem)) {
				return true;
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
		// If session requires this item or it is a raw ingredient for synthesis, it is not excess
		if (session != null) {
			for (var task : session.getTasks()) {
				if (!task.isCompleted()) {
					Item req = task.getBlueprintBlock().getRequiredItem();
					if (req == item || isSynthesisSource(item, req)) {
						return false;
					}
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

	// =========================================================================
	// Mob Material Procurement, Hunting Contracts, & Synthesis
	// =========================================================================

	/**
	 * Checks if an item can be procured from mob drops or synthesized from mob drops.
	 */
	public static boolean isMobProcurementResource(Item item) {
		if (item == null) return false;
		return isWoolResource(item)
			|| item == Items.STRING
			|| item == Items.SPIDER_EYE
			|| item == Items.FERMENTED_SPIDER_EYE
			|| item == Items.COBWEB
			|| item == Items.BONE
			|| item == Items.BONE_MEAL
			|| item == Items.BONE_BLOCK
			|| item == Items.ARROW
			|| item == Items.SLIME_BALL
			|| item == Items.SLIME_BLOCK
			|| item == Items.STICKY_PISTON
			|| item == Items.MAGMA_CREAM
			|| item == Items.MAGMA_BLOCK
			|| item == Items.LEATHER
			|| item == Items.ITEM_FRAME
			|| item == Items.GLOW_ITEM_FRAME
			|| item == Items.INK_SAC
			|| item == Items.GLOW_INK_SAC
			|| item == Items.FEATHER
			|| item == Items.BLAZE_ROD
			|| item == Items.BLAZE_POWDER
			|| item == Items.ENDER_PEARL
			|| item == Items.ENDER_EYE
			|| item == Items.GUNPOWDER
			|| item == Items.ROTTEN_FLESH
			|| item == Items.PRISMARINE_SHARD
			|| item == Items.PRISMARINE_CRYSTALS
			|| item == Items.PRISMARINE
			|| item == Items.PRISMARINE_BRICKS
			|| item == Items.DARK_PRISMARINE
			|| item == Items.SEA_LANTERN
			|| item == Items.RABBIT_HIDE
			|| item == Items.RABBIT_FOOT
			|| item == Items.PHANTOM_MEMBRANE
			|| item == Items.SHULKER_SHELL
			|| item == Items.SHULKER_BOX;
	}

	/**
	 * Checks if the specified item is any colored wool block.
	 */
	public static boolean isWoolResource(Item item) {
		if (item == null) return false;
		return item == Items.WHITE_WOOL || item == Items.ORANGE_WOOL || item == Items.MAGENTA_WOOL
			|| item == Items.LIGHT_BLUE_WOOL || item == Items.YELLOW_WOOL || item == Items.LIME_WOOL
			|| item == Items.PINK_WOOL || item == Items.GRAY_WOOL || item == Items.LIGHT_GRAY_WOOL
			|| item == Items.CYAN_WOOL || item == Items.PURPLE_WOOL || item == Items.BLUE_WOOL
			|| item == Items.BROWN_WOOL || item == Items.GREEN_WOOL || item == Items.RED_WOOL
			|| item == Items.BLACK_WOOL || item.getDefaultStack().isIn(ItemTags.WOOL);
	}

	/**
	 * Verifies whether a given living entity is a valid mob source for the requested item.
	 */
	public static boolean isTargetMobForResource(LivingEntity entity, Item requiredItem) {
		if (entity == null || requiredItem == null) return false;

		// Wool items: Sheep or Spiders (via string synthesis)
		if (isWoolResource(requiredItem)) {
			return entity instanceof SheepEntity || entity instanceof SpiderEntity || entity instanceof CaveSpiderEntity;
		}
		// String & Spider Eye
		if (requiredItem == Items.STRING || requiredItem == Items.SPIDER_EYE || requiredItem == Items.FERMENTED_SPIDER_EYE || requiredItem == Items.COBWEB) {
			return entity instanceof SpiderEntity || entity instanceof CaveSpiderEntity;
		}
		// Bones, Bone Meal, Bone Block, Arrows
		if (requiredItem == Items.BONE || requiredItem == Items.BONE_MEAL || requiredItem == Items.BONE_BLOCK || requiredItem == Items.ARROW) {
			return entity instanceof AbstractSkeletonEntity;
		}
		// Slime ball, Slime block, Sticky piston
		if (requiredItem == Items.SLIME_BALL || requiredItem == Items.SLIME_BLOCK || requiredItem == Items.STICKY_PISTON) {
			return entity instanceof SlimeEntity;
		}
		// Magma cream, Magma block
		if (requiredItem == Items.MAGMA_CREAM || requiredItem == Items.MAGMA_BLOCK) {
			return entity instanceof MagmaCubeEntity;
		}
		// Leather & Item Frame
		if (requiredItem == Items.LEATHER || requiredItem == Items.ITEM_FRAME || requiredItem == Items.BEEF
			|| requiredItem == Items.LEATHER_HELMET || requiredItem == Items.LEATHER_CHESTPLATE
			|| requiredItem == Items.LEATHER_LEGGINGS || requiredItem == Items.LEATHER_BOOTS) {
			return entity instanceof CowEntity || entity instanceof MooshroomEntity || entity instanceof HoglinEntity
				|| (entity instanceof AbstractHorseEntity horse && !horse.isTame());
		}
		// Ink & Glow Ink
		if (requiredItem == Items.INK_SAC || requiredItem == Items.BLACK_DYE) {
			return entity instanceof SquidEntity;
		}
		if (requiredItem == Items.GLOW_INK_SAC || requiredItem == Items.GLOW_ITEM_FRAME) {
			return entity instanceof GlowSquidEntity;
		}
		// Feathers & Chicken
		if (requiredItem == Items.FEATHER || requiredItem == Items.CHICKEN || requiredItem == Items.COOKED_CHICKEN) {
			return entity instanceof ChickenEntity || entity instanceof ParrotEntity;
		}
		// Blaze Rod & Powder
		if (requiredItem == Items.BLAZE_ROD || requiredItem == Items.BLAZE_POWDER) {
			return entity instanceof BlazeEntity;
		}
		// Ender Pearl & Eye
		if (requiredItem == Items.ENDER_PEARL || requiredItem == Items.ENDER_EYE) {
			return entity instanceof EndermanEntity;
		}
		// Gunpowder & TNT
		if (requiredItem == Items.GUNPOWDER || requiredItem == Items.TNT) {
			return entity instanceof CreeperEntity || entity instanceof GhastEntity || entity instanceof WitchEntity;
		}
		// Rotten Flesh
		if (requiredItem == Items.ROTTEN_FLESH) {
			return entity instanceof ZombieEntity;
		}
		// Prismarine
		if (requiredItem == Items.PRISMARINE_SHARD || requiredItem == Items.PRISMARINE_CRYSTALS
			|| requiredItem == Items.PRISMARINE || requiredItem == Items.PRISMARINE_BRICKS
			|| requiredItem == Items.DARK_PRISMARINE || requiredItem == Items.SEA_LANTERN) {
			return entity instanceof GuardianEntity;
		}
		// Rabbit
		if (requiredItem == Items.RABBIT_HIDE || requiredItem == Items.RABBIT_FOOT || requiredItem == Items.RABBIT) {
			return entity instanceof RabbitEntity;
		}
		// Phantom Membrane
		if (requiredItem == Items.PHANTOM_MEMBRANE) {
			return entity instanceof PhantomEntity;
		}
		// Shulker Shell
		if (requiredItem == Items.SHULKER_SHELL || requiredItem == Items.SHULKER_BOX) {
			return entity instanceof ShulkerEntity;
		}

		return false;
	}

	/**
	 * Validates whether an entity is a safe target for autonomous mob hunting contracts.
	 * Strictly protects player pets, named mobs, villagers, iron golems, allays, and allied minions.
	 */
	public static boolean isSafeHuntTarget(LivingEntity target, UUID ownerUuid) {
		if (target == null || !target.isAlive()) {
			return false;
		}
		// Never hunt players
		if (target instanceof PlayerEntity) {
			return false;
		}
		// Never hunt allied or player minions
		if (target instanceof MinionEntity) {
			return false;
		}
		// Never hunt named mobs (player pets / tagged entities)
		if (target.hasCustomName()) {
			return false;
		}
		// Never hunt tamed pets (wolves, cats, parrots)
		if (target instanceof TameableEntity tameable && tameable.isTamed()) {
			return false;
		}
		// Never hunt tamed horses / mounts
		if (target instanceof AbstractHorseEntity horse && horse.isTame()) {
			return false;
		}
		// Never hunt villagers or wandering traders
		if (target instanceof MerchantEntity) {
			return false;
		}
		// Never hunt village defenders (iron golems, snow golems)
		if (target instanceof IronGolemEntity || target instanceof SnowGolemEntity) {
			return false;
		}
		// Never hunt allays or armor stands
		if (target instanceof AllayEntity || target instanceof ArmorStandEntity) {
			return false;
		}

		return true;
	}

	/**
	 * Checks if sourceItem can be synthesized into targetItem.
	 */
	public static boolean isSynthesisSource(Item sourceItem, Item targetItem) {
		if (sourceItem == null || targetItem == null) return false;
		if (sourceItem == Items.BONE && (targetItem == Items.BONE_MEAL || targetItem == Items.BONE_BLOCK)) return true;
		if (sourceItem == Items.BONE_MEAL && targetItem == Items.BONE_BLOCK) return true;
		if (sourceItem == Items.STRING && (isWoolResource(targetItem) || targetItem == Items.COBWEB)) return true;
		if (sourceItem == Items.SLIME_BALL && (targetItem == Items.SLIME_BLOCK || targetItem == Items.STICKY_PISTON)) return true;
		if (sourceItem == Items.MAGMA_CREAM && targetItem == Items.MAGMA_BLOCK) return true;
		if (sourceItem == Items.BLAZE_ROD && (targetItem == Items.BLAZE_POWDER || targetItem == Items.ENDER_EYE)) return true;
		if (sourceItem == Items.BLAZE_POWDER && targetItem == Items.ENDER_EYE) return true;
		if (sourceItem == Items.ENDER_PEARL && targetItem == Items.ENDER_EYE) return true;
		if (sourceItem == Items.PRISMARINE_SHARD && (targetItem == Items.PRISMARINE || targetItem == Items.PRISMARINE_BRICKS || targetItem == Items.SEA_LANTERN)) return true;
		if (sourceItem == Items.PRISMARINE_CRYSTALS && targetItem == Items.SEA_LANTERN) return true;
		if (sourceItem == Items.RABBIT_HIDE && targetItem == Items.LEATHER) return true;
		return false;
	}

	/**
	 * Synthesizes refined construction materials from raw mob ingredients present in the inventory.
	 */
	public static boolean synthesizeMaterial(SimpleInventory inv, Item targetItem) {
		if (inv == null || targetItem == null) return false;

		// 1. Bone Meal: 1 Bone -> 3 Bone Meal
		if (targetItem == Items.BONE_MEAL) {
			if (countItemInInventory(inv, Items.BONE) >= 1) {
				consumeItemFromInventory(inv, Items.BONE, 1);
				inv.addStack(new ItemStack(Items.BONE_MEAL, 3));
				return true;
			}
		}

		// 2. Bone Block: 9 Bone Meal -> 1 Bone Block (or 3 Bones -> 9 Bone Meal -> 1 Bone Block)
		if (targetItem == Items.BONE_BLOCK) {
			if (countItemInInventory(inv, Items.BONE_MEAL) < 9 && countItemInInventory(inv, Items.BONE) >= 3) {
				int bonesToConvert = Math.min(countItemInInventory(inv, Items.BONE), 3);
				consumeItemFromInventory(inv, Items.BONE, bonesToConvert);
				inv.addStack(new ItemStack(Items.BONE_MEAL, bonesToConvert * 3));
			}
			if (countItemInInventory(inv, Items.BONE_MEAL) >= 9) {
				consumeItemFromInventory(inv, Items.BONE_MEAL, 9);
				inv.addStack(new ItemStack(Items.BONE_BLOCK, 1));
				return true;
			}
		}

		// 3. Wool: 4 String -> 1 Wool
		if (isWoolResource(targetItem)) {
			if (countItemInInventory(inv, Items.STRING) >= 4) {
				consumeItemFromInventory(inv, Items.STRING, 4);
				inv.addStack(new ItemStack(targetItem, 1));
				return true;
			}
		}

		// 4. Slime Block: 9 Slimeballs -> 1 Slime Block
		if (targetItem == Items.SLIME_BLOCK) {
			if (countItemInInventory(inv, Items.SLIME_BALL) >= 9) {
				consumeItemFromInventory(inv, Items.SLIME_BALL, 9);
				inv.addStack(new ItemStack(Items.SLIME_BLOCK, 1));
				return true;
			}
		}

		// 5. Sticky Piston: 1 Piston + 1 Slimeball -> 1 Sticky Piston
		if (targetItem == Items.STICKY_PISTON) {
			if (countItemInInventory(inv, Items.PISTON) >= 1 && countItemInInventory(inv, Items.SLIME_BALL) >= 1) {
				consumeItemFromInventory(inv, Items.PISTON, 1);
				consumeItemFromInventory(inv, Items.SLIME_BALL, 1);
				inv.addStack(new ItemStack(Items.STICKY_PISTON, 1));
				return true;
			}
		}

		// 6. Magma Block: 4 Magma Cream -> 1 Magma Block
		if (targetItem == Items.MAGMA_BLOCK) {
			if (countItemInInventory(inv, Items.MAGMA_CREAM) >= 4) {
				consumeItemFromInventory(inv, Items.MAGMA_CREAM, 4);
				inv.addStack(new ItemStack(Items.MAGMA_BLOCK, 1));
				return true;
			}
		}

		// 7. Blaze Powder: 1 Blaze Rod -> 2 Blaze Powder
		if (targetItem == Items.BLAZE_POWDER) {
			if (countItemInInventory(inv, Items.BLAZE_ROD) >= 1) {
				consumeItemFromInventory(inv, Items.BLAZE_ROD, 1);
				inv.addStack(new ItemStack(Items.BLAZE_POWDER, 2));
				return true;
			}
		}

		// 8. Eye of Ender: 1 Ender Pearl + 1 Blaze Powder -> 1 Eye of Ender
		if (targetItem == Items.ENDER_EYE) {
			if (countItemInInventory(inv, Items.BLAZE_POWDER) < 1 && countItemInInventory(inv, Items.BLAZE_ROD) >= 1) {
				consumeItemFromInventory(inv, Items.BLAZE_ROD, 1);
				inv.addStack(new ItemStack(Items.BLAZE_POWDER, 2));
			}
			if (countItemInInventory(inv, Items.ENDER_PEARL) >= 1 && countItemInInventory(inv, Items.BLAZE_POWDER) >= 1) {
				consumeItemFromInventory(inv, Items.ENDER_PEARL, 1);
				consumeItemFromInventory(inv, Items.BLAZE_POWDER, 1);
				inv.addStack(new ItemStack(Items.ENDER_EYE, 1));
				return true;
			}
		}

		// 9. Prismarine: 4 Prismarine Shards -> 1 Prismarine
		if (targetItem == Items.PRISMARINE) {
			if (countItemInInventory(inv, Items.PRISMARINE_SHARD) >= 4) {
				consumeItemFromInventory(inv, Items.PRISMARINE_SHARD, 4);
				inv.addStack(new ItemStack(Items.PRISMARINE, 1));
				return true;
			}
		}

		// 10. Prismarine Bricks: 9 Prismarine Shards -> 1 Prismarine Bricks
		if (targetItem == Items.PRISMARINE_BRICKS) {
			if (countItemInInventory(inv, Items.PRISMARINE_SHARD) >= 9) {
				consumeItemFromInventory(inv, Items.PRISMARINE_SHARD, 9);
				inv.addStack(new ItemStack(Items.PRISMARINE_BRICKS, 1));
				return true;
			}
		}

		// 11. Sea Lantern: 4 Prismarine Shards + 5 Prismarine Crystals -> 1 Sea Lantern
		if (targetItem == Items.SEA_LANTERN) {
			if (countItemInInventory(inv, Items.PRISMARINE_SHARD) >= 4 && countItemInInventory(inv, Items.PRISMARINE_CRYSTALS) >= 5) {
				consumeItemFromInventory(inv, Items.PRISMARINE_SHARD, 4);
				consumeItemFromInventory(inv, Items.PRISMARINE_CRYSTALS, 5);
				inv.addStack(new ItemStack(Items.SEA_LANTERN, 1));
				return true;
			}
		}

		// 12. Leather: 4 Rabbit Hide -> 1 Leather
		if (targetItem == Items.LEATHER) {
			if (countItemInInventory(inv, Items.RABBIT_HIDE) >= 4) {
				consumeItemFromInventory(inv, Items.RABBIT_HIDE, 4);
				inv.addStack(new ItemStack(Items.LEATHER, 1));
				return true;
			}
		}

		// 13. Cobweb: 9 String -> 1 Cobweb
		if (targetItem == Items.COBWEB) {
			if (countItemInInventory(inv, Items.STRING) >= 9) {
				consumeItemFromInventory(inv, Items.STRING, 9);
				inv.addStack(new ItemStack(Items.COBWEB, 1));
				return true;
			}
		}

		return false;
	}

	/**
	 * Finds a nearby available Warrior thrall belonging to the same owner who can be commissioned.
	 */
	public static MinionEntity findNearbyAvailableWarrior(MinionEntity requester, ServerWorld world, double radius) {
		if (requester == null || world == null) return null;
		UUID ownerUuid = requester.getOwnerUuid();
		if (ownerUuid == null) return null;

		Box box = requester.getBoundingBox().expand(radius);
		List<MinionEntity> warriors = world.getEntitiesByClass(
			MinionEntity.class,
			box,
			m -> m != requester
				&& m.isAlive()
				&& ownerUuid.equals(m.getOwnerUuid())
				&& m.getRole() == MinionRole.WARRIOR
				&& !m.isSitting()
				&& !m.hasActiveProcurement()
				&& !m.hasAssaultTargets()
		);

		if (warriors.isEmpty()) return null;
		warriors.sort((a, b) -> Double.compare(a.squaredDistanceTo(requester), b.squaredDistanceTo(requester)));
		return warriors.get(0);
	}

	/**
	 * Finds the closest candidate mob within radius that satisfies safe hunt checks and matches the resource.
	 */
	public static LivingEntity findCandidateMobForProcurement(
		MinionEntity requester,
		ServerWorld world,
		Item requiredItem,
		double radius
	) {
		if (requester == null || world == null || requiredItem == null) return null;
		UUID ownerUuid = requester.getOwnerUuid();
		Box searchBox = requester.getBoundingBox().expand(radius);

		List<LivingEntity> candidates = world.getEntitiesByClass(
			LivingEntity.class,
			searchBox,
			entity -> isSafeHuntTarget(entity, ownerUuid) && isTargetMobForResource(entity, requiredItem)
		);

		if (candidates.isEmpty()) {
			return null;
		}

		candidates.sort((a, b) -> Double.compare(a.squaredDistanceTo(requester), b.squaredDistanceTo(requester)));
		return candidates.get(0);
	}

	/**
	 * Commissions an allied Warrior thrall on a tactical mob hunting procurement contract.
	 */
	public static boolean commissionWarriorHunt(
		MinionEntity builder,
		MinionEntity warrior,
		LivingEntity targetMob,
		Item requiredItem,
		ServerWorld world
	) {
		if (builder == null || warrior == null || targetMob == null || requiredItem == null || world == null) {
			return false;
		}

		warrior.setProcurementRequesterUuid(builder.getUuid());
		warrior.setProcurementItem(requiredItem);
		warrior.setProcurementTarget(targetMob);
		warrior.setTarget(targetMob);

		// Audio feedback: weaponsmith / chime feedback
		world.playSound(
			null,
			builder.getX(),
			builder.getY(),
			builder.getZ(),
			SoundEvents.ENTITY_VILLAGER_WORK_WEAPONSMITH,
			SoundCategory.NEUTRAL,
			1.0F,
			1.0F
		);

		// Visual sharing beam between builder and warrior
		MinionLogisticsHelper.spawnSharingBeam(world, builder.getEyePos(), warrior.getEyePos());

		// Target indicator particles
		world.spawnParticles(
			ParticleTypes.CRIT,
			targetMob.getX(),
			targetMob.getBodyY(0.5D),
			targetMob.getZ(),
			10,
			0.3D,
			0.3D,
			0.3D,
			0.1D
		);

		return true;
	}

	/**
	 * Attempts autonomous mob hunting: scans for candidate mobs and either commissions a nearby Warrior
	 * or falls back to solo hunting by the builder thrall.
	 */
	public static boolean tryAutonomousMobHunting(
		MinionEntity builder,
		ServerWorld world,
		Item requiredItem
	) {
		if (builder == null || world == null || requiredItem == null) {
			return false;
		}

		// 1. Check if we can synthesize from current inventory first
		if (synthesizeMaterial(builder.getInventory(), requiredItem)) {
			return true;
		}

		// 2. Scan for candidate mobs within 32 blocks
		LivingEntity targetMob = findCandidateMobForProcurement(builder, world, requiredItem, 32.0D);
		if (targetMob == null) {
			return false;
		}

		// 3. Find nearby available Warrior thrall
		MinionEntity warrior = findNearbyAvailableWarrior(builder, world, 32.0D);
		if (warrior != null) {
			return commissionWarriorHunt(builder, warrior, targetMob, requiredItem, world);
		} else {
			// Solo Hunting Fallback: Builder engages target mob directly
			builder.setProcurementRequesterUuid(builder.getUuid());
			builder.setProcurementItem(requiredItem);
			builder.setProcurementTarget(targetMob);
			builder.setTarget(targetMob);
			world.playSound(null, builder.getX(), builder.getY(), builder.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8F, 1.2F);
			return true;
		}
	}

	/**
	 * Processes mob hunting drops upon target slay, synthesizing the required item and delivering it to the requester.
	 */
	public static void processMobHuntingDrops(MinionEntity warrior, LivingEntity slainTarget, ServerWorld world) {
		if (warrior == null || world == null) return;

		Item requiredItem = warrior.getProcurementItem();
		UUID requesterUuid = warrior.getProcurementRequesterUuid();

		if (requiredItem != null) {
			// Resolve primary drops from the slain mob
			List<ItemStack> drops = resolveMobDropsForProcurement(slainTarget, requiredItem);
			for (ItemStack drop : drops) {
				warrior.getInventory().addStack(drop);
			}

			// Perform inventory material synthesis if needed (e.g. bones -> bone meal, strings -> wool)
			synthesizeMaterial(warrior.getInventory(), requiredItem);

			// Deliver synthesized/procured item to requester if alive and nearby
			if (requesterUuid != null) {
				deliverProcuredItemToRequester(warrior, requesterUuid, requiredItem, world);
			}

			// Auditory and particle completion feedback
			world.playSound(
				null,
				warrior.getX(),
				warrior.getY(),
				warrior.getZ(),
				SoundEvents.ENTITY_ITEM_PICKUP,
				SoundCategory.NEUTRAL,
				1.0F,
				1.2F
			);
			world.spawnParticles(
				ParticleTypes.HAPPY_VILLAGER,
				warrior.getX(),
				warrior.getY() + 1.0D,
				warrior.getZ(),
				8,
				0.3D,
				0.3D,
				0.3D,
				0.05D
			);
		}

		warrior.clearProcurementTask();
	}

	/**
	 * Resolves drop items yielded from slaying the target entity for a procurement contract.
	 */
	public static List<ItemStack> resolveMobDropsForProcurement(LivingEntity slainTarget, Item requiredItem) {
		List<ItemStack> drops = new ArrayList<>();
		if (slainTarget == null || requiredItem == null) return drops;

		if (slainTarget instanceof SheepEntity) {
			Item woolItem = isWoolResource(requiredItem) ? requiredItem : Items.WHITE_WOOL;
			drops.add(new ItemStack(woolItem, 2));
			drops.add(new ItemStack(Items.MUTTON, 1));
		} else if (slainTarget instanceof SpiderEntity || slainTarget instanceof CaveSpiderEntity) {
			drops.add(new ItemStack(Items.STRING, 2));
			drops.add(new ItemStack(Items.SPIDER_EYE, 1));
		} else if (slainTarget instanceof AbstractSkeletonEntity) {
			drops.add(new ItemStack(Items.BONE, 2));
			drops.add(new ItemStack(Items.ARROW, 2));
		} else if (slainTarget instanceof SlimeEntity) {
			drops.add(new ItemStack(Items.SLIME_BALL, 2));
		} else if (slainTarget instanceof MagmaCubeEntity) {
			drops.add(new ItemStack(Items.MAGMA_CREAM, 2));
		} else if (slainTarget instanceof CowEntity || slainTarget instanceof MooshroomEntity || slainTarget instanceof HoglinEntity) {
			drops.add(new ItemStack(Items.LEATHER, 2));
			drops.add(new ItemStack(Items.BEEF, 2));
		} else if (slainTarget instanceof SquidEntity) {
			drops.add(new ItemStack(Items.INK_SAC, 2));
		} else if (slainTarget instanceof GlowSquidEntity) {
			drops.add(new ItemStack(Items.GLOW_INK_SAC, 2));
		} else if (slainTarget instanceof ChickenEntity || slainTarget instanceof ParrotEntity) {
			drops.add(new ItemStack(Items.FEATHER, 2));
			drops.add(new ItemStack(Items.CHICKEN, 1));
		} else if (slainTarget instanceof BlazeEntity) {
			drops.add(new ItemStack(Items.BLAZE_ROD, 1));
		} else if (slainTarget instanceof EndermanEntity) {
			drops.add(new ItemStack(Items.ENDER_PEARL, 1));
		} else if (slainTarget instanceof CreeperEntity || slainTarget instanceof GhastEntity || slainTarget instanceof WitchEntity) {
			drops.add(new ItemStack(Items.GUNPOWDER, 2));
		} else if (slainTarget instanceof ZombieEntity) {
			drops.add(new ItemStack(Items.ROTTEN_FLESH, 2));
		} else if (slainTarget instanceof GuardianEntity) {
			drops.add(new ItemStack(Items.PRISMARINE_SHARD, 2));
			drops.add(new ItemStack(Items.PRISMARINE_CRYSTALS, 1));
		} else if (slainTarget instanceof RabbitEntity) {
			drops.add(new ItemStack(Items.RABBIT_HIDE, 2));
			drops.add(new ItemStack(Items.RABBIT_FOOT, 1));
		} else if (slainTarget instanceof PhantomEntity) {
			drops.add(new ItemStack(Items.PHANTOM_MEMBRANE, 1));
		} else if (slainTarget instanceof ShulkerEntity) {
			drops.add(new ItemStack(Items.SHULKER_SHELL, 1));
		} else {
			drops.add(new ItemStack(requiredItem, 1));
		}

		return drops;
	}

	/**
	 * Delivers procured items from the hunting warrior to the original builder minion requester.
	 */
	public static boolean deliverProcuredItemToRequester(
		MinionEntity warrior,
		UUID requesterUuid,
		Item requiredItem,
		ServerWorld world
	) {
		if (warrior == null || requesterUuid == null || world == null) return false;

		// If warrior is the requester (solo hunting fallback), it is already in their inventory
		if (warrior.getUuid().equals(requesterUuid)) {
			return true;
		}

		List<MinionEntity> requesters = world.getEntitiesByClass(
			MinionEntity.class,
			warrior.getBoundingBox().expand(48.0D),
			m -> m.getUuid().equals(requesterUuid) && m.isAlive()
		);

		for (MinionEntity ally : requesters) {
			SimpleInventory warriorInv = warrior.getInventory();
			SimpleInventory allyInv = ally.getInventory();

			// First ensure synthesized if needed
			synthesizeMaterial(warriorInv, requiredItem);

			for (int slot = 0; slot < warriorInv.size(); slot++) {
				ItemStack stack = warriorInv.getStack(slot);
				if (!stack.isEmpty() && stack.isOf(requiredItem) && stack.getCount() > 0) {
					ItemStack transferred = stack.split(1);
					warriorInv.markDirty();
					allyInv.addStack(transferred);
					allyInv.markDirty();

					MinionLogisticsHelper.spawnSharingBeam(world, warrior.getEyePos(), ally.getEyePos());
					world.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8F, 1.2F);
					return true;
				}
			}
		}
		return false;
	}
}
