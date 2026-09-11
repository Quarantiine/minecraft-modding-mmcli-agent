package com.example.entity.custom;

import com.example.entity.ai.goal.MinionBuildGoal;
import com.example.entity.ai.pathing.MinionNavigation;
import com.example.screen.MinionScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.InventoryOwner;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * MinionEntity represents an autonomous worker and combat thrall bound to a player owner.
 * Extends {@link TameableEntity} and implements {@link InventoryOwner} with a dedicated 9-slot inventory.
 * Supports player interactions:
 * - Sneak + Right-Click: opens the Minion Management GUI (Equipment, Inventory, and 3D preview).
 * - Empty Hand Right-Click: toggles sitting / staying (following) state.
 * - Food / Gold Right-Click: heals wounded minion with particles and auditory feedback.
 */
public class MinionEntity extends TameableEntity implements InventoryOwner {

	public static final int INVENTORY_SIZE = 9;
	private final SimpleInventory inventory = new SimpleInventory(INVENTORY_SIZE);

	private LivingEntity lastCombatTarget;
	private int outOfCombatTicks = 0;

	public MinionEntity(EntityType<? extends TameableEntity> entityType, World world) {
		super(entityType, world);
		if (this.getNavigation() instanceof MobNavigation mobNav) {
			mobNav.setCanPathThroughDoors(true);
			mobNav.setCanEnterOpenDoors(true);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setEquipmentDropChance(slot, 2.0F);
		}
		// Configure door and trapdoor pathfinding penalties for seamless structure traversal
		this.setPathfindingPenalty(PathNodeType.DOOR_OPEN, 0.0F);
		this.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0F);
		this.setPathfindingPenalty(PathNodeType.WALKABLE_DOOR, 0.0F);
		this.setPathfindingPenalty(PathNodeType.TRAPDOOR, 0.0F);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new MinionNavigation(this, world);
	}

	/**
	 * Creates default living attributes for the minion entity.
	 * Boosted base health (40 HP), armor (4), and damage (5) for high combat survivability,
	 * with step height (1.0625D) enabling smooth movement over stairs, slabs, and 1-block steps.
	 */
	public static DefaultAttributeContainer.Builder createMinionAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3D)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0D)
			.add(EntityAttributes.GENERIC_ARMOR, 4.0D)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
			.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0625D);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new SitGoal(this));
		this.goalSelector.add(2, new LongDoorInteractGoal(this, true));
		this.goalSelector.add(3, new MinionBuildGoal(this));
		this.goalSelector.add(4, new MeleeAttackGoal(this, 1.25D, true));
		this.goalSelector.add(5, new FollowOwnerGoal(this, 1.15D, 3.0F, 1.5F));
		this.goalSelector.add(6, new WanderAroundFarGoal(this, 1.0D));
		this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(8, new LookAroundGoal(this));

		this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
		this.targetSelector.add(2, new AttackWithOwnerGoal(this));
		this.targetSelector.add(3, new RevengeGoal(this).setGroupRevenge());
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.getWorld().isClient()) {
			LivingEntity currentTarget = this.getTarget();

			// Track combat state
			if (currentTarget != null && currentTarget.isAlive()) {
				this.outOfCombatTicks = 0;
			} else {
				this.outOfCombatTicks++;
				// Post-combat transition: when previous target was cleared or died, return to owner
				if (this.lastCombatTarget != null && (currentTarget == null || !this.lastCombatTarget.isAlive())) {
					returnToOwnerPostCombat();
				}
			}
			this.lastCombatTarget = currentTarget;

			// Passive out-of-combat health regeneration: 1 HP every 40 ticks (2 seconds) after 60 ticks out of combat
			if (this.isAlive() && this.isTamed() && this.outOfCombatTicks >= 60 && this.getHealth() < this.getMaxHealth()) {
				if (this.age % 40 == 0) {
					this.heal(1.0F);
				}
			}

			// Periodic auto-equipment check from internal 9-slot inventory (every 20 ticks / 1 second)
			if (this.age % 20 == 0) {
				autoEquipFromInventory();
			}
		}
	}

	@Override
	public boolean onKilledOther(ServerWorld world, LivingEntity other) {
		boolean result = super.onKilledOther(world, other);
		returnToOwnerPostCombat();
		return result;
	}

	/**
	 * Commands the minion to immediately pathfind back to its owner after combat concludes,
	 * ensuring thralls quickly regroup and do not get estranged or lost.
	 */
	public void returnToOwnerPostCombat() {
		if (this.isTamed() && !this.isSitting()) {
			LivingEntity owner = this.getOwner();
			if (owner != null && this.squaredDistanceTo(owner) > 4.0D) {
				this.navigation.startMovingTo(owner, 1.25D);
			}
		}
	}

	@Override
	public void setSitting(boolean sitting) {
		super.setSitting(sitting);
		this.setInSittingPose(sitting);
	}

	/**
	 * Overrides attack logic to swing the minion's main hand animation, apply weapon damage/enchantments,
	 * and play appropriate combat weapon hit sounds.
	 */
	@Override
	public boolean tryAttack(Entity target) {
		this.swingHand(Hand.MAIN_HAND);
		boolean attacked = super.tryAttack(target);
		if (attacked) {
			ItemStack weapon = this.getMainHandStack();
			if (!weapon.isEmpty()) {
				if (weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem || weapon.getItem() instanceof MaceItem) {
					this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
				} else {
					this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 1.0F, 1.0F);
				}
			}
		}
		return attacked;
	}

	/**
	 * Automatically equips armor, weapons, and defensive offhand items from the minion's
	 * 9-slot storage inventory into any corresponding empty equipment slots.
	 */
	public void autoEquipFromInventory() {
		if (this.getWorld().isClient()) {
			return;
		}

		for (int i = 0; i < this.inventory.size(); i++) {
			ItemStack stack = this.inventory.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}

			// 1. Check Armor slots (HEAD, CHEST, LEGS, FEET)
			EquipmentSlot preferredSlot = this.getPreferredEquipmentSlot(stack);
			if (preferredSlot == EquipmentSlot.HEAD
				|| preferredSlot == EquipmentSlot.CHEST
				|| preferredSlot == EquipmentSlot.LEGS
				|| preferredSlot == EquipmentSlot.FEET) {
				if (this.getEquippedStack(preferredSlot).isEmpty()) {
					ItemStack toEquip = stack.split(1);
					this.equipStack(preferredSlot, toEquip);
					if (stack.isEmpty()) {
						this.inventory.setStack(i, ItemStack.EMPTY);
					}
					this.inventory.markDirty();
					this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.0F);
					continue;
				}
			}

			// 2. Check Weapon/Tool for MAINHAND
			if (MinionScreenHandler.isWeaponOrTool(stack) && this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()) {
				ItemStack toEquip = stack.split(1);
				this.equipStack(EquipmentSlot.MAINHAND, toEquip);
				if (stack.isEmpty()) {
					this.inventory.setStack(i, ItemStack.EMPTY);
				}
				this.inventory.markDirty();
				this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.2F);
				continue;
			}

			// 3. Check Shield / Totem for OFFHAND
			if (MinionScreenHandler.isShieldOrTotem(stack) && this.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()) {
				ItemStack toEquip = stack.split(1);
				this.equipStack(EquipmentSlot.OFFHAND, toEquip);
				if (stack.isEmpty()) {
					this.inventory.setStack(i, ItemStack.EMPTY);
				}
				this.inventory.markDirty();
				this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.2F);
			}
		}
	}

	@Override
	public SimpleInventory getInventory() {
		return this.inventory;
	}

	@Override
	public boolean canEquip(ItemStack stack) {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.put("Inventory", this.inventory.toNbtList(this.getRegistryManager()));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Inventory", NbtElement.LIST_TYPE)) {
			this.inventory.readNbtList(nbt.getList("Inventory", NbtElement.COMPOUND_TYPE), this.getRegistryManager());
		}
	}

	@Override
	protected void dropInventory() {
		super.dropInventory();
		if (this.inventory != null) {
			for (int i = 0; i < this.inventory.size(); ++i) {
				ItemStack itemStack = this.inventory.getStack(i);
				if (!itemStack.isEmpty()) {
					this.dropStack(itemStack.copy());
					this.inventory.setStack(i, ItemStack.EMPTY);
				}
			}
		}
	}

	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack itemStack = player.getStackInHand(hand);
		World world = this.getWorld();

		// Handle taming if untamed
		if (!this.isTamed()) {
			if (itemStack.isOf(Items.GOLD_INGOT)) {
				if (!player.getAbilities().creativeMode) {
					itemStack.decrement(1);
				}
				if (!world.isClient()) {
					this.setOwner(player);
					this.navigation.stop();
					this.setTarget(null);
					world.sendEntityStatus(this, (byte) 7); // Heart particles
					player.sendMessage(Text.literal("§6✦ You have bound a new Minion to your will!§r"), false);
				}
				this.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
				return ActionResult.success(world.isClient());
			}
			return ActionResult.PASS;
		}

		// Only the owner can interact with a tamed minion
		if (!this.isOwner(player)) {
			return ActionResult.PASS;
		}

		// 1. Sneak + Right-Click: open Minion Screen GUI (Equipment + Inventory)
		if (player.isSneaking()) {
			if (!world.isClient()) {
				this.openInventoryScreen(player);
			}
			return ActionResult.success(world.isClient());
		}

		// 2. Empty Hand: toggle sit / stay
		if (itemStack.isEmpty()) {
			boolean newSitting = !this.isSitting();
			this.setSitting(newSitting);
			this.jumping = false;
			this.navigation.stop();
			this.setTarget(null);
			if (!world.isClient()) {
				String msg = newSitting ? "§e✦ Minion is now holding position (sitting).§r" : "§a✦ Minion is now following you.§r";
				player.sendMessage(Text.literal(msg), true);
			}
			this.playSound(newSitting ? SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM : SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.0F);
			return ActionResult.success(world.isClient());
		}

		// 3. Food / Gold Healing: heal wounded minion
		if (this.getHealth() < this.getMaxHealth()) {
			boolean isFood = itemStack.contains(DataComponentTypes.FOOD);
			boolean isGold = itemStack.isOf(Items.GOLD_INGOT) || itemStack.isOf(Items.GOLD_NUGGET)
				|| itemStack.isOf(Items.GOLD_BLOCK) || itemStack.isOf(Items.RAW_GOLD);

			if (isFood || isGold) {
				float healAmount;
				if (isFood) {
					FoodComponent food = itemStack.get(DataComponentTypes.FOOD);
					healAmount = food != null ? (float) food.nutrition() : 2.0F;
					this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
				} else {
					if (itemStack.isOf(Items.GOLD_BLOCK)) {
						healAmount = 20.0F;
					} else if (itemStack.isOf(Items.GOLD_NUGGET)) {
						healAmount = 1.0F;
					} else {
						healAmount = 4.0F;
					}
					this.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F);
				}

				this.heal(healAmount);
				if (!player.getAbilities().creativeMode) {
					itemStack.decrement(1);
				}
				if (world instanceof ServerWorld serverWorld) {
					serverWorld.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
				}
				return ActionResult.success(world.isClient());
			}
		}

		// Direct item deposit has been removed in favor of the GUI screen.
		return ActionResult.PASS;
	}

	/**
	 * Opens the {@link MinionScreenHandler} GUI for the inspecting player.
	 *
	 * @param player The commanding player opening the screen.
	 */
	public void openInventoryScreen(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
				@Override
				public Text getDisplayName() {
					return MinionEntity.this.getDisplayName();
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
					return new MinionScreenHandler(syncId, playerInventory, MinionEntity.this.getInventory(), MinionEntity.this);
				}

				@Override
				public Integer getScreenOpeningData(ServerPlayerEntity player) {
					return MinionEntity.this.getId();
				}
			});
		}
	}

	/**
	 * Safely dismisses this minion thrall, dropping all equipped items and internal inventory,
	 * emitting poof particles and teleport sounds, and removing the entity from the world.
	 */
	public void dismiss() {
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = this.getEquippedStack(slot);
				if (!stack.isEmpty()) {
					this.dropStack(stack.copy());
					this.equipStack(slot, ItemStack.EMPTY);
				}
			}
			this.dropInventory();

			serverWorld.spawnParticles(
				ParticleTypes.POOF,
				this.getX(),
				this.getY() + 0.5D,
				this.getZ(),
				15,
				0.3D,
				0.5D,
				0.3D,
				0.05D
			);
			serverWorld.playSound(
				null,
				this.getX(),
				this.getY(),
				this.getZ(),
				SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE,
				SoundCategory.NEUTRAL,
				0.8F,
				1.2F
			);
		}
		this.discard();
	}

	/**
	 * Safely teleports this minion thrall adjacent to the specified player.
	 * Finds a suitable solid ground position within radius of the player, resets fall distance,
	 * halts ongoing navigation, and spawns enderman portal visual/auditory effects.
	 *
	 * @param player The commanding player to teleport towards.
	 * @return true if safe teleportation succeeded, false otherwise.
	 */
	public boolean teleportToPlayer(ServerPlayerEntity player) {
		if (player == null || !(this.getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}

		double originX = this.getX();
		double originY = this.getY();
		double originZ = this.getZ();

		BlockPos playerPos = player.getBlockPos();
		BlockPos safePos = null;

		int[] offsets = { 0, 1, -1, 2, -2 };
		for (int dx : offsets) {
			for (int dz : offsets) {
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos candidate = playerPos.add(dx, dy, dz);
					if (isSafeTeleportTarget(serverWorld, candidate)) {
						safePos = candidate;
						break;
					}
				}
				if (safePos != null) break;
			}
			if (safePos != null) break;
		}

		double destX = safePos != null ? safePos.getX() + 0.5D : player.getX();
		double destY = safePos != null ? safePos.getY() : player.getY();
		double destZ = safePos != null ? safePos.getZ() + 0.5D : player.getZ();

		// Origin teleport VFX & SFX
		serverWorld.spawnParticles(ParticleTypes.PORTAL, originX, originY + 0.5D, originZ, 20, 0.3D, 0.5D, 0.3D, 0.1D);
		serverWorld.playSound(null, originX, originY, originZ, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 0.8F, 1.2F);

		// Execute position update and cancel inertia
		this.refreshPositionAndAngles(destX, destY, destZ, this.getYaw(), this.getPitch());
		this.getNavigation().stop();
		this.setVelocity(0.0D, 0.0D, 0.0D);
		this.velocityModified = true;
		this.fallDistance = 0.0F;

		// Destination arrival VFX & SFX
		serverWorld.spawnParticles(ParticleTypes.PORTAL, destX, destY + 0.5D, destZ, 25, 0.3D, 0.5D, 0.3D, 0.1D);
		serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, destX, destY + 0.2D, destZ, 10, 0.2D, 0.2D, 0.2D, 0.02D);
		serverWorld.playSound(null, destX, destY, destZ, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.0F, 1.0F);

		return true;
	}

	/**
	 * Validates whether the candidate block position is a safe landing site for minion teleportation.
	 */
	private boolean isSafeTeleportTarget(ServerWorld world, BlockPos pos) {
		BlockPos below = pos.down();
		BlockState belowState = world.getBlockState(below);

		if (!belowState.isSolidBlock(world, below) && !belowState.isOf(Blocks.SCAFFOLDING)) {
			return false;
		}

		BlockState feetState = world.getBlockState(pos);
		BlockState headState = world.getBlockState(pos.up());

		boolean feetClear = feetState.isAir() || feetState.isOf(Blocks.SCAFFOLDING) || feetState.canPathfindThrough(NavigationType.LAND);
		boolean headClear = headState.isAir() || headState.isOf(Blocks.SCAFFOLDING) || headState.canPathfindThrough(NavigationType.LAND);
		boolean hazard = feetState.isOf(Blocks.LAVA) || feetState.isOf(Blocks.FIRE) || feetState.isOf(Blocks.SWEET_BERRY_BUSH);

		return feetClear && headClear && !hazard;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_VILLAGER_DEATH;
	}
}
