package com.example.entity.custom;

import com.example.component.SquadGroup;
import com.example.entity.ai.goal.MinionActiveTargetGoal;
import com.example.entity.ai.goal.MinionBuildGoal;
import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.ai.goal.MinionRangedAttackGoal;
import com.example.entity.ai.goal.MinionSapperGoal;
import com.example.entity.ai.goal.SentinelGuardGoal;
import com.example.entity.ai.goal.WaypointHoldGoal;
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
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
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
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
public class MinionEntity extends TameableEntity implements InventoryOwner, RangedAttackMob {

	public static final int INVENTORY_SIZE = 9;
	private final SimpleInventory inventory = new SimpleInventory(INVENTORY_SIZE);

	private static final TrackedData<Integer> ROLE_ID = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SQUAD_ID = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> SELECTED = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> GUARDING = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private LivingEntity lastCombatTarget;
	private int outOfCombatTicks = 0;
	private boolean climbingScaffolding = false;
	private BlockPos guardAnchorPos = null;

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
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE_ID, MinionRole.WARRIOR.getId());
		builder.add(SQUAD_ID, SquadGroup.ALPHA.getId());
		builder.add(SELECTED, false);
		builder.add(GUARDING, false);
	}

	/**
	 * @return true if this minion is actively selected in the player's tactical command group.
	 */
	public boolean isSelected() {
		return this.dataTracker.get(SELECTED);
	}

	/**
	 * Sets the active selection status of this minion.
	 *
	 * @param selected true to select for waypoint dispatch and squad maneuvers.
	 */
	public void setSelected(boolean selected) {
		this.dataTracker.set(SELECTED, selected);
	}

	/**
	 * @return true if this minion is actively stationed on standing guard duty at an anchor post.
	 */
	public boolean isGuarding() {
		return this.dataTracker.get(GUARDING);
	}

	/**
	 * @return true if this minion is currently holding position (either sitting or standing guard).
	 */
	public boolean isHoldingPosition() {
		return this.isSitting() || this.isGuarding();
	}

	@Override
	public boolean isGlowing() {
		return this.isSelected() || super.isGlowing();
	}

	@Override
	public int getTeamColorValue() {
		return this.getSquad() != null ? this.getSquad().getOutlineColor() : 0xFFD700;
	}

	/**
	 * @return The current tactical role assigned to this minion.
	 */
	public MinionRole getRole() {
		return MinionRole.fromId(this.dataTracker.get(ROLE_ID));
	}

	/**
	 * Sets the tactical role assigned to this minion.
	 *
	 * @param role The new role to assign.
	 */
	public void setRole(MinionRole role) {
		this.dataTracker.set(ROLE_ID, role != null ? role.getId() : MinionRole.WARRIOR.getId());
	}

	/**
	 * @return The squad organizational group this minion belongs to.
	 */
	public SquadGroup getSquad() {
		return SquadGroup.fromId(this.dataTracker.get(SQUAD_ID));
	}

	/**
	 * Sets the squad organizational group for this minion.
	 *
	 * @param squad The squad to assign.
	 */
	public void setSquad(SquadGroup squad) {
		this.dataTracker.set(SQUAD_ID, squad != null ? squad.getId() : SquadGroup.ALPHA.getId());
	}

	/**
	 * @return The anchor position for sentinel guard duty, or null if unset.
	 */
	public BlockPos getGuardAnchorPos() {
		return this.guardAnchorPos;
	}

	/**
	 * Sets the anchor position for sentinel guard duty.
	 *
	 * @param guardAnchorPos The guard anchor block coordinate.
	 */
	public void setGuardAnchorPos(BlockPos guardAnchorPos) {
		this.guardAnchorPos = guardAnchorPos;
		this.dataTracker.set(GUARDING, guardAnchorPos != null);
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
		this.goalSelector.add(2, new MinionSapperGoal(this));
		this.goalSelector.add(3, new SentinelGuardGoal(this));
		this.goalSelector.add(3, new WaypointHoldGoal(this));
		this.goalSelector.add(3, new MinionBuildGoal(this));
		this.goalSelector.add(4, new MinionRangedAttackGoal(this, 1.25D, 20));
		this.goalSelector.add(5, new MeleeAttackGoal(this, 1.35D, true) {
			@Override
			public boolean canStart() {
				if (MinionEntity.this.getRole() == MinionRole.RANGER && (MinionEntity.this.isHolding(Items.BOW) || MinionEntity.this.isHolding(Items.CROSSBOW) || MinionEntity.this.getMainHandStack().getItem() instanceof BowItem)) {
					return false;
				}
				return super.canStart();
			}
		});
		this.goalSelector.add(6, new MinionFormationFollowGoal(this));
		this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0D) {
			@Override
			public boolean canStart() {
				if (MinionEntity.this.getGuardAnchorPos() != null || MinionEntity.this.isSelected()) {
					return false;
				}
				return super.canStart();
			}
		});
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(9, new LookAroundGoal(this));

		this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
		this.targetSelector.add(2, new AttackWithOwnerGoal(this));
		this.targetSelector.add(3, new RevengeGoal(this).setGroupRevenge());
		this.targetSelector.add(4, new MinionActiveTargetGoal(this, 24.0D));
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

			// Auto-clear climbing flag if minion is no longer within a scaffolding block
			if (this.climbingScaffolding && !this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)) {
				this.climbingScaffolding = false;
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
	 * Commands the minion to immediately pathfind back to its post or owner after combat concludes,
	 * ensuring thralls quickly regroup at 1.35D sprint speed and do not get estranged or lost.
	 * Sentinels return strictly to their guard anchor post; other roles return to the master.
	 */
	public void returnToOwnerPostCombat() {
		if (this.isTamed() && !this.isSitting()) {
			BlockPos anchor = this.getGuardAnchorPos();
			if (anchor != null) {
				if (this.squaredDistanceTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D) > 4.0D) {
					this.navigation.startMovingTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.35D);
				}
			} else if (this.getRole() == MinionRole.SENTINEL) {
				LivingEntity owner = this.getOwner();
				if (owner != null && this.squaredDistanceTo(owner) > 4.0D) {
					this.navigation.startMovingTo(owner, 1.35D);
				}
			} else {
				LivingEntity owner = this.getOwner();
				if (owner != null && this.squaredDistanceTo(owner) > 4.0D) {
					this.navigation.startMovingTo(owner, 1.35D);
				}
			}
		}
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		ItemStack weapon = this.getMainHandStack();
		if (!weapon.isOf(Items.BOW) && !weapon.isOf(Items.CROSSBOW) && !(weapon.getItem() instanceof BowItem)) {
			ItemStack offhand = this.getOffHandStack();
			if (offhand.isOf(Items.BOW) || offhand.isOf(Items.CROSSBOW) || offhand.getItem() instanceof BowItem) {
				weapon = offhand;
			}
		}
		ItemStack arrowStack = this.getProjectileType(weapon);
		if (arrowStack.isEmpty()) {
			arrowStack = new ItemStack(Items.ARROW);
		}
		PersistentProjectileEntity arrowEntity = ProjectileUtil.createArrowProjectile(
			this,
			arrowStack,
			pullProgress,
			weapon.isEmpty() ? null : weapon
		);
		if (this.isTamed()) {
			arrowEntity.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		}
		double dx = target.getX() - this.getX();
		double dy = target.getBodyY(0.3333333333333333D) - arrowEntity.getY();
		double dz = target.getZ() - this.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		arrowEntity.setVelocity(dx, dy + distance * 0.20000000298023224D, dz, 1.6F, (float) (14 - this.getWorld().getDifficulty().getId() * 4));
		this.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		this.getWorld().spawnEntity(arrowEntity);
	}

	@Override
	public void setSitting(boolean sitting) {
		super.setSitting(sitting);
		this.setInSittingPose(sitting);
		this.climbingScaffolding = false;
	}

	/**
	 * Returns whether the minion is actively climbing a scaffolding column under AI control.
	 *
	 * @return true if currently climbing scaffolding under AI control.
	 */
	public boolean isClimbingScaffolding() {
		return this.climbingScaffolding;
	}

	/**
	 * Sets whether the minion should actively climb inside a scaffolding column.
	 *
	 * @param climbing true to allow climbing scaffolding physics; false to treat scaffolding as walkable without climbing clamping.
	 */
	public void setClimbingScaffolding(boolean climbing) {
		this.climbingScaffolding = climbing;
	}

	/**
	 * Determines whether the minion is currently positioned within a scaffolding block
	 * and actively navigating upward toward an elevated waypoint, destination, or target.
	 *
	 * @return true if inside scaffolding and ascending/navigating to a higher Y coordinate.
	 */
	public boolean isNavigatingUpwardInScaffolding() {
		if (!this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)) {
			return false;
		}
		// Explicit climbing flag from build or sapper AI goals
		if (this.climbingScaffolding) {
			return true;
		}
		// Entity has active jump input flag
		if (this.jumping) {
			return true;
		}
		// MoveControl target is higher than current position
		if (this.getMoveControl().isMoving() && this.getMoveControl().getTargetY() > this.getY() + 0.1D) {
			return true;
		}
		// Active navigation path has an elevated next waypoint or destination
		if (!this.getNavigation().isIdle()) {
			Path path = this.getNavigation().getCurrentPath();
			if (path != null && !path.isFinished()) {
				PathNode currentNode = path.getCurrentNode();
				if (currentNode != null && currentNode.y > this.getBlockY()) {
					return true;
				}
				BlockPos target = path.getTarget();
				if (target != null && target.getY() > this.getBlockY()) {
					return true;
				}
			}
		}
		// Combat target is elevated above minion
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && target.getY() > this.getY() + 0.5D) {
			return true;
		}
		return false;
	}

	/**
	 * Overrides vanilla climbing behavior to give the minion AI explicit control over scaffolding traversal.
	 * If the minion is currently within a scaffolding block, climbing physics is enabled if
	 * {@link #isClimbingScaffolding()} is true or if the minion is actively navigating upward
	 * through the scaffolding column. For ladders, vines, and other climbables, defaults to vanilla logic.
	 *
	 * @return true if the minion is actively climbing.
	 */
	@Override
	public boolean isClimbing() {
		if (this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)) {
			return this.climbingScaffolding || this.isNavigatingUpwardInScaffolding();
		}
		return super.isClimbing();
	}

	/**
	 * Overrides entity travel physics to implement smooth scaffolding climbing mechanics for minions.
	 * Because mob entities lack client jump input packets, vanilla scaffolding logic fails to propel
	 * mobs upward when ascending. When inside scaffolding and navigating upward or toward elevated targets,
	 * applies a continuous +0.25D vertical velocity impulse and zeroes fall distance.
	 *
	 * @param movementInput Lateral and forward directional movement vector.
	 */
	@Override
	public void travel(Vec3d movementInput) {
		boolean ascendingScaffolding = this.isAlive()
			&& this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)
			&& this.isNavigatingUpwardInScaffolding();

		if (ascendingScaffolding) {
			this.fallDistance = 0.0F;
			Vec3d currentVelocity = this.getVelocity();
			this.setVelocity(currentVelocity.x, 0.25D, currentVelocity.z);
			this.velocityModified = true;
		}

		super.travel(movementInput);

		if (this.isAlive() && this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)) {
			this.fallDistance = 0.0F;
			if (ascendingScaffolding) {
				Vec3d currentVelocity = this.getVelocity();
				if (currentVelocity.y < 0.25D) {
					this.setVelocity(currentVelocity.x, 0.25D, currentVelocity.z);
					this.velocityModified = true;
				}
			}
		}
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

	@Override
	public boolean isTeammate(Entity other) {
		if (super.isTeammate(other)) {
			return true;
		}
		if (this.isTamed()) {
			LivingEntity owner = this.getOwner();
			if (owner != null) {
				if (other.equals(owner)) {
					return true;
				}
				if (other instanceof MinionEntity otherMinion && otherMinion.isTamed() && owner.equals(otherMinion.getOwner())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.isTamed() && source.getAttacker() != null) {
			Entity attacker = source.getAttacker();
			LivingEntity owner = this.getOwner();
			if (owner != null && (attacker.equals(owner) || (attacker instanceof MinionEntity otherMinion && otherMinion.isOwner(owner)))) {
				return false;
			}
		}
		return super.damage(source, amount);
	}

	/**
	 * Automatically equips armor, weapons, and defensive offhand items from the minion's
	 * 9-slot storage inventory into any corresponding empty equipment slots.
	 */
	/**
	 * Determines whether the given item is a ranged weapon (bow or crossbow).
	 */
	public static boolean isRangedWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW)
			|| stack.getItem() instanceof BowItem
			|| stack.getItem() instanceof CrossbowItem
			|| stack.getItem() instanceof RangedWeaponItem;
	}

	/**
	 * Determines whether the given item is a frontline melee weapon (sword, axe, mace, or trident).
	 */
	public static boolean isMeleeWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.getItem() instanceof SwordItem
			|| stack.getItem() instanceof AxeItem
			|| stack.getItem() instanceof MaceItem
			|| stack.getItem() instanceof TridentItem;
	}

	/**
	 * Determines whether the given item is a shield.
	 */
	public static boolean isShield(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.getItem() instanceof ShieldItem;
	}

	/**
	 * Validates whether an item is eligible for auto-equipping into the mainhand slot
	 * for the specified archetype role.
	 *
	 * @param role  The minion's active role.
	 * @param stack The item stack candidate.
	 * @return true if the item is permitted for the role, false otherwise.
	 */
	public static boolean canRoleAutoEquipMainhand(MinionRole role, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return switch (role) {
			case RANGER -> isRangedWeapon(stack);
			case WARRIOR, SENTINEL -> isMeleeWeapon(stack);
			case MINER -> stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof SwordItem;
			case BUILDER -> stack.getItem() instanceof MiningToolItem || isMeleeWeapon(stack);
		};
	}

	/**
	 * Determines if a candidate item in inventory should replace the currently equipped mainhand item
	 * based on role specialization priorities.
	 */
	public static boolean isPreferredMainhandWeapon(MinionRole role, ItemStack candidate, ItemStack current) {
		if (candidate == null || candidate.isEmpty()) return false;
		if (current == null || current.isEmpty()) return canRoleAutoEquipMainhand(role, candidate);

		return switch (role) {
			case RANGER -> isRangedWeapon(candidate) && !isRangedWeapon(current);
			case WARRIOR, SENTINEL -> isMeleeWeapon(candidate) && !isMeleeWeapon(current);
			case MINER -> (candidate.getItem() instanceof MiningToolItem) && !(current.getItem() instanceof MiningToolItem);
			case BUILDER -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
		};
	}

	/**
	 * Automatically equips armor, weapons, and defensive offhand items from the minion's
	 * 9-slot storage inventory into corresponding equipment slots, strictly adhering to role restrictions:
	 * - Rangers seek bows and crossbows (never melee weapons or mining tools).
	 * - Warriors seek frontline melee weapons (swords, axes, maces; never ranged weapons).
	 * - Sentinels seek melee weapons in mainhand and prioritize shields in offhand.
	 * - Miners seek mining tools (pickaxes) and defense weapons.
	 * - Builders seek construction tools and defense weapons.
	 * - All roles equip available protective armor.
	 */
	public void autoEquipFromInventory() {
		if (this.getWorld().isClient()) {
			return;
		}

		MinionRole role = this.getRole();

		for (int i = 0; i < this.inventory.size(); i++) {
			ItemStack stack = this.inventory.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}

			// 1. Check Armor slots (HEAD, CHEST, LEGS, FEET) - all roles equip available protective armor
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

			// 2. Sentinel Offhand Priority: Sentinels specifically seek shields in offhand
			if (role == MinionRole.SENTINEL && isShield(stack)) {
				ItemStack currentOffhand = this.getEquippedStack(EquipmentSlot.OFFHAND);
				if (!isShield(currentOffhand)) {
					ItemStack toEquip = stack.split(1);
					this.equipStack(EquipmentSlot.OFFHAND, toEquip);
					if (stack.isEmpty()) {
						this.inventory.setStack(i, currentOffhand);
					} else {
						this.inventory.setStack(i, stack);
						if (!currentOffhand.isEmpty()) {
							this.inventory.addStack(currentOffhand);
						}
					}
					this.inventory.markDirty();
					this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.2F);
					continue;
				}
			}

			// 3. Mainhand Auto-Equip based on Role
			ItemStack currentMainhand = this.getEquippedStack(EquipmentSlot.MAINHAND);
			if (currentMainhand.isEmpty()) {
				if (canRoleAutoEquipMainhand(role, stack)) {
					ItemStack toEquip = stack.split(1);
					this.equipStack(EquipmentSlot.MAINHAND, toEquip);
					if (stack.isEmpty()) {
						this.inventory.setStack(i, ItemStack.EMPTY);
					}
					this.inventory.markDirty();
					this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.2F);
					continue;
				}
			} else if (isPreferredMainhandWeapon(role, stack, currentMainhand)) {
				ItemStack toEquip = stack.split(1);
				this.equipStack(EquipmentSlot.MAINHAND, toEquip);
				if (stack.isEmpty()) {
					this.inventory.setStack(i, currentMainhand);
				} else {
					this.inventory.setStack(i, stack);
					if (!currentMainhand.isEmpty()) {
						this.inventory.addStack(currentMainhand);
					}
				}
				this.inventory.markDirty();
				this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.2F);
				continue;
			}

			// 4. Offhand Auto-Equip (Shield / Totem) when offhand is empty
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
		nbt.putString("MinionRole", this.getRole().asString());
		nbt.putInt("MinionRoleId", this.getRole().getId());
		nbt.putString("MinionSquad", this.getSquad().asString());
		nbt.putInt("MinionSquadId", this.getSquad().getId());
		nbt.putBoolean("Selected", this.isSelected());
		if (this.guardAnchorPos != null) {
			nbt.putLong("GuardAnchorPos", this.guardAnchorPos.asLong());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Inventory", NbtElement.LIST_TYPE)) {
			this.inventory.readNbtList(nbt.getList("Inventory", NbtElement.COMPOUND_TYPE), this.getRegistryManager());
		}
		if (nbt.contains("Selected", NbtElement.BYTE_TYPE)) {
			this.setSelected(nbt.getBoolean("Selected"));
		}
		if (nbt.contains("MinionRoleId", NbtElement.INT_TYPE)) {
			this.setRole(MinionRole.fromId(nbt.getInt("MinionRoleId")));
		} else if (nbt.contains("MinionRole", NbtElement.STRING_TYPE)) {
			String roleName = nbt.getString("MinionRole");
			for (MinionRole r : MinionRole.values()) {
				if (r.asString().equalsIgnoreCase(roleName)) {
					this.setRole(r);
					break;
				}
			}
		}

		if (nbt.contains("MinionSquadId", NbtElement.INT_TYPE)) {
			this.setSquad(SquadGroup.fromId(nbt.getInt("MinionSquadId")));
		} else if (nbt.contains("MinionSquad", NbtElement.STRING_TYPE)) {
			String squadName = nbt.getString("MinionSquad");
			for (SquadGroup s : SquadGroup.values()) {
				if (s.asString().equalsIgnoreCase(squadName)) {
					this.setSquad(s);
					break;
				}
			}
		}

		if (nbt.contains("GuardAnchorPos", NbtElement.LONG_TYPE)) {
			this.setGuardAnchorPos(BlockPos.fromLong(nbt.getLong("GuardAnchorPos")));
		} else {
			this.setGuardAnchorPos(null);
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
			if (newSitting) {
				this.setSelected(false);
				this.setGuardAnchorPos(this.getBlockPos());
			} else {
				this.setSelected(true);
				this.setGuardAnchorPos(null);
			}
			if (!world.isClient()) {
				String msg = newSitting ? "§e✦ Minion is now holding position (stationed).§r" : "§a✦ Minion is now selected and following you.§r";
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
		this.climbingScaffolding = false;
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

		this.climbingScaffolding = false;

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
