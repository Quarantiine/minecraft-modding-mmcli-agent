package com.example.entity.custom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.example.component.SquadGroup;
import com.example.entity.ai.goal.MinionActiveTargetGoal;
import com.example.entity.ai.goal.MinionBuildGoal;
import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.ai.goal.MinionRangedAttackGoal;
import com.example.entity.ai.goal.SentinelGuardGoal;
import com.example.entity.ai.goal.SentinelHealAllyGoal;
import com.example.entity.ai.goal.WaypointHoldGoal;
import com.example.entity.ai.pathing.MinionNavigation;
import com.example.item.ModItems;
import com.example.item.custom.FrostGrenadeStickItem;
import com.example.item.custom.TntStickItem;
import com.example.screen.MinionScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.LocalDifficulty;
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
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
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
	private static final TrackedData<Boolean> PREVIEW_GLOWING = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> PHASING_BLOCKS = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> PATROL_ROUTE_ID = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CURRENT_WAYPOINT_INDEX = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Optional<UUID>> LEADER_MINION_UUID = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<Integer> ADAPTIVE_ROLE_ID = DataTracker.registerData(MinionEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private LivingEntity lastCombatTarget;
	private int outOfCombatTicks = 0;
	private boolean climbingScaffolding = false;
	private boolean arcaneLevitating = false;
	private int previewGlowTicks = 0;
	private BlockPos guardAnchorPos = null;
	private final List<LivingEntity> assaultTargets = new ArrayList<>();
	private Vec3d activeTraversalDestination = null;
	private boolean activelyBuilding = false;
	private boolean activelyHealing = false;
	private boolean exitingBuilding = false;
	private Vec3d structureExitVec = null;
	private BlockBox lastStructureBox = null;
	private int egressTicks = 0;
	private int traversalStallTicks = 0;
	private int arcaneLevitationTicks = 0;
	private int levitationCooldown = 0;
	private boolean traversalManeuverActive = false;
	private double traversalClearanceAltitude = 0.0D;
	private int traversalCrestTicks = 0;

	private UUID procurementRequesterUuid = null;
	private Item procurementItem = null;
	private LivingEntity procurementTarget = null;

	public MinionEntity(EntityType<? extends TameableEntity> entityType, World world) {
		super(entityType, world);
		if (this.getNavigation() instanceof MobNavigation mobNav) {
			mobNav.setCanPathThroughDoors(true);
			mobNav.setCanEnterOpenDoors(true);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setEquipmentDropChance(slot, 2.0F);
		}
		// Configure door, trapdoor, and water pathfinding penalties for seamless traversal
		this.setPathfindingPenalty(PathNodeType.DOOR_OPEN, 0.0F);
		this.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0F);
		this.setPathfindingPenalty(PathNodeType.WALKABLE_DOOR, 0.0F);
		this.setPathfindingPenalty(PathNodeType.TRAPDOOR, 0.0F);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
		this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0F);
	}

	@Override
	protected void initEquipment(net.minecraft.util.math.random.Random random, LocalDifficulty localDifficulty) {
		// Minions spawn with completely empty equipment; gear is issued intentionally by player.
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE_ID, MinionRole.AUTO.getId());
		builder.add(SQUAD_ID, SquadGroup.ALPHA.getId());
		builder.add(SELECTED, false);
		builder.add(GUARDING, false);
		builder.add(PREVIEW_GLOWING, false);
		builder.add(PHASING_BLOCKS, false);
		builder.add(PATROL_ROUTE_ID, -1);
		builder.add(CURRENT_WAYPOINT_INDEX, 0);
		builder.add(LEADER_MINION_UUID, Optional.empty());
		builder.add(ADAPTIVE_ROLE_ID, MinionRole.WARRIOR.getId());
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
		if (selected) {
			this.clearLeader();
			this.setPatrolRouteId(-1);
		}
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

	public int getPatrolRouteId() {
		return this.dataTracker.get(PATROL_ROUTE_ID);
	}

	public void setPatrolRouteId(int routeId) {
		this.dataTracker.set(PATROL_ROUTE_ID, routeId);
		if (routeId >= 0) {
			this.dataTracker.set(SELECTED, false);
			this.clearLeader();
			this.setGuardAnchorPos(null);
		}
	}

	public int getCurrentWaypointIndex() {
		return this.dataTracker.get(CURRENT_WAYPOINT_INDEX);
	}

	public void setCurrentWaypointIndex(int index) {
		this.dataTracker.set(CURRENT_WAYPOINT_INDEX, Math.max(0, index));
	}

	public UUID getLeaderMinionUuid() {
		return this.dataTracker.get(LEADER_MINION_UUID).orElse(null);
	}

	public void setLeaderMinionUuid(UUID uuid) {
		this.dataTracker.set(LEADER_MINION_UUID, Optional.ofNullable(uuid));
		if (uuid != null) {
			this.dataTracker.set(SELECTED, false);
			this.setGuardAnchorPos(null);
			this.setPatrolRouteId(-1);
			this.clearAssaultTargets();
			this.setTarget(null);
			this.setSitting(false);
		}
	}

	public MinionEntity resolveLeader() {
		UUID leaderUuid = this.getLeaderMinionUuid();
		if (leaderUuid == null) {
			return null;
		}
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			Entity entity = serverWorld.getEntity(leaderUuid);
			if (entity instanceof MinionEntity minionLeader) {
				if (minionLeader.isAlive()) {
					return minionLeader;
				} else {
					this.clearLeader();
					return null;
				}
			}
		}
		return null;
	}

	public boolean hasLeader() {
		return this.dataTracker.get(LEADER_MINION_UUID).isPresent();
	}

	public void clearLeader() {
		this.dataTracker.set(LEADER_MINION_UUID, Optional.empty());
	}

	public MinionRole getAdaptiveRole() {
		return MinionRole.fromId(this.dataTracker.get(ADAPTIVE_ROLE_ID));
	}

	public MinionRole getEffectiveRole() {
		return this.getRole() == MinionRole.AUTO ? this.getAdaptiveRole() : this.getRole();
	}

	public void setAdaptiveRole(MinionRole role) {
		this.dataTracker.set(ADAPTIVE_ROLE_ID, role != null ? role.getId() : MinionRole.WARRIOR.getId());
	}

	public boolean matchesRole(MinionRole expectedRole) {
		if (this.getRole() == expectedRole) {
			return true;
		}
		return this.getRole() == MinionRole.AUTO && this.getAdaptiveRole() == expectedRole;
	}

	@Override
	public boolean isGlowing() {
		return this.isSelected() || this.isPreviewGlowing() || super.isGlowing();
	}

	/**
	 * @return true if this minion is currently preview glowing during channeled command targeting.
	 */
	public boolean isPreviewGlowing() {
		return this.dataTracker.get(PREVIEW_GLOWING);
	}

	/**
	 * Sets whether this minion is preview glowing during scepter channeling.
	 *
	 * @param previewGlowing true to illuminate outline in real-time.
	 */
	public void setPreviewGlowing(boolean previewGlowing) {
		if (previewGlowing) {
			this.previewGlowTicks = 6;
		} else {
			this.previewGlowTicks = 0;
		}
		this.dataTracker.set(PREVIEW_GLOWING, previewGlowing);
	}

	/**
	 * @return true if the minion is currently hovering/flying via Arcane Levitation.
	 */
	public boolean isArcaneLevitating() {
		return this.arcaneLevitating;
	}

	/**
	 * Sets the arcane levitation state, suppressing gravity and resetting fall distance.
	 * Arcane Levitation is strictly isolated to active construction and post-task descent in MinionBuildGoal.
	 * Non-builder minions navigate on the ground with 1.25D step height and companion catch-up teleportation.
	 *
	 * @param levitating true to activate 3D flight/hovering for builders, false to restore gravity.
	 */
	public void setArcaneLevitating(boolean levitating) {
		// Levitation is only valid for builder-role minions.
		// It may be active during building, egress, OR controlled post-task descent (gentleDescending).
		// Non-builder roles are never permitted to levitate.
		if (levitating && !this.matchesRole(MinionRole.BUILDER)) {
			levitating = false;
		}
		this.arcaneLevitating = levitating;
		this.setNoGravity(levitating);
		if (!levitating) {
			this.arcaneLevitationTicks = 0;
			this.traversalManeuverActive = false;
			this.traversalClearanceAltitude = 0.0D;
			this.traversalCrestTicks = 0;
		} else {
			this.fallDistance = 0.0F;
		}
	}

	/**
	 * Applies a single tick of gentle controlled descent for a builder minion that has finished
	 * its task and needs to float smoothly back to solid ground.
	 * Call this every tick while the minion is airborne and done building.
	 * Returns true once the minion has landed and levitation has been deactivated.
	 *
	 * @param serverWorld The server world.
	 * @return true when the minion has successfully landed.
	 */
	public boolean tickGentleDescent(ServerWorld serverWorld) {
		if (!this.arcaneLevitating) {
			return true; // Already landed or levitation was cleared externally
		}
		this.fallDistance = 0.0F;
		BlockPos feet = this.getBlockPos();
		BlockPos below = feet.down();
		BlockState belowState = serverWorld.getBlockState(below);
		boolean overSolid = this.isOnGround()
				|| belowState.isSolidBlock(serverWorld, below)
				|| this.hasSolidGroundBeneath(serverWorld);
		if (overSolid) {
			// Landed — disable levitation cleanly
			this.arcaneLevitating = false;
			this.setNoGravity(false);
			this.arcaneLevitationTicks = 0;
			this.setVelocity(0.0D, 0.0D, 0.0D);
			this.velocityModified = true;
			serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 4, 0.15D, 0.05D, 0.15D, 0.02D);
			return true;
		}
		// Still airborne — glide straight down
		this.setVelocity(this.getVelocity().x * 0.5D, -0.22D, this.getVelocity().z * 0.5D);
		this.velocityModified = true;
		return false;
	}

	/**
	 * @return true if this minion is actively engaged in building/dismantling via MinionBuildGoal.
	 */
	public boolean isActivelyBuilding() {
		return this.activelyBuilding;
	}

	private long lastConstructionActivityTick = 0L;

	/**
	 * Sets whether this minion is actively engaged in building/dismantling.
	 *
	 * @param activelyBuilding true if actively constructing or demolishing blocks.
	 */
	public void setActivelyBuilding(boolean activelyBuilding) {
		this.activelyBuilding = activelyBuilding;
		if (activelyBuilding) {
			this.lastConstructionActivityTick = this.getWorld().getTime();
			this.fallDistance = 0.0F;
		}
	}

	public long getLastConstructionActivityTick() {
		return this.lastConstructionActivityTick;
	}

	public void touchConstructionActivity() {
		this.lastConstructionActivityTick = this.getWorld().getTime();
		this.fallDistance = 0.0F;
	}

	/**
	 * @return true if this sentinel minion is actively channeling healing via SentinelHealAllyGoal.
	 */
	public boolean isActivelyHealing() {
		return this.activelyHealing;
	}

	/**
	 * Sets whether this sentinel minion is actively channeling healing.
	 *
	 * @param activelyHealing true if actively healing allies.
	 */
	public void setActivelyHealing(boolean activelyHealing) {
		this.activelyHealing = activelyHealing;
	}

	/**
	 * @return true if this builder minion is actively phasing out of a completed or cancelled structure.
	 */
	public boolean isExitingBuilding() {
		return this.exitingBuilding;
	}

	/**
	 * Sets the building egress state for this minion.
	 *
	 * @param exitingBuilding true if actively moving out of the structure boundaries.
	 */
	public void setExitingBuilding(boolean exitingBuilding) {
		this.exitingBuilding = exitingBuilding;
	}

	/**
	 * Builder minions are able to pass through blocks (noClip = true) ONLY while actively building
	 * and immediately after they finish building until they have completely evacuated the structure.
	 *
	 * @return true if block phasing is currently active for this builder minion.
	 */
	public boolean isPhasingBlocks() {
		if (this.getWorld().isClient()) {
			return this.dataTracker.get(PHASING_BLOCKS);
		}
		return this.isAlive() && this.matchesRole(MinionRole.BUILDER) && (this.activelyBuilding || this.exitingBuilding);
	}

	/**
	 * Initiates post-construction or post-dismantle evacuation from the structure.
	 * Builder minion retains block phasing (noClip) and levitation until reaching an exterior
	 * safe coordinate outside the structure bounding box with clear headroom.
	 *
	 * @param box            The structure's bounding box.
	 * @param anchorPos      The building anchor position.
	 * @param targetWaypoint Optional exterior waypoint to navigate toward, or null for auto-exterior scan.
	 */
	public void startEgressFromStructure(BlockBox box, BlockPos anchorPos, Vec3d targetWaypoint) {
		if (!this.isAlive() || !this.matchesRole(MinionRole.BUILDER)) {
			return;
		}
		this.lastStructureBox = box;
		this.exitingBuilding = true;
		this.egressTicks = 0;
		this.activelyBuilding = false;
		this.setArcaneLevitating(true);
		this.setNoGravity(true);
		this.noClip = true;
		if (!this.getWorld().isClient()) {
			this.dataTracker.set(PHASING_BLOCKS, true);
		}

		if (targetWaypoint != null) {
			this.structureExitVec = targetWaypoint;
		} else {
			this.structureExitVec = findExteriorExitPosition(this.getWorld(), box, anchorPos);
		}
	}

	/**
	 * Ticks the building egress state machine, steering the builder minion through walls/floors
	 * toward the exterior perimeter waypoint until clear of the structure.
	 */
	public void tickBuildingEgress() {
		if (!this.exitingBuilding || !(this.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		this.egressTicks++;
		this.noClip = true;
		this.setNoGravity(true);
		this.fallDistance = 0.0F;

		// Spawn magical egress phasing particles
		if (this.age % 2 == 0) {
			serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.2D, this.getZ(), 3, 0.2D, 0.1D, 0.2D, 0.02D);
			serverWorld.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.3D, this.getZ(), 2, 0.25D, 0.15D, 0.25D, 0.05D);
		}

		Vec3d dest = this.structureExitVec;
		if (dest == null) {
			dest = findExteriorExitPosition(serverWorld, this.lastStructureBox, this.getBlockPos());
			this.structureExitVec = dest;
		}

		if (dest != null) {
			Vec3d toDest = dest.subtract(this.getPos());
			double dist = toDest.length();
			double speed = Math.min(0.35D, Math.max(0.14D, dist * 0.45D));
			Vec3d vel = toDest.normalize().multiply(speed);
			this.setVelocity(vel);
			this.velocityModified = true;

			// Check if outside structure and in safe open space
			boolean outside = this.lastStructureBox == null || !isInsideStructure(this.lastStructureBox);
			BlockPos footPos = this.getBlockPos();
			BlockState footState = serverWorld.getBlockState(footPos);
			BlockState headState = serverWorld.getBlockState(footPos.up());
			boolean clearHeadroom = !footState.isSolidBlock(serverWorld, footPos) && !headState.isSolidBlock(serverWorld, footPos.up());

			if ((outside && clearHeadroom && (dist < 1.2D || this.egressTicks > 60)) || this.egressTicks > 140) {
				finishBuildingEgress(serverWorld);
			}
		} else {
			finishBuildingEgress(serverWorld);
		}
	}

	/**
	 * Completes the post-construction structure egress, disabling block phasing and settling
	 * the builder minion onto safe ground outside the building.
	 *
	 * @param serverWorld The server world.
	 */
	public void finishBuildingEgress(ServerWorld serverWorld) {
		this.exitingBuilding = false;
		this.lastStructureBox = null;
		this.egressTicks = 0;
		this.noClip = false;
		this.dataTracker.set(PHASING_BLOCKS, false);

		// Check if minion ended up inside solid blocks — if so, nudge to exit vector
		BlockPos footPos = this.getBlockPos();
		BlockState footState = serverWorld.getBlockState(footPos);
		BlockState headState = serverWorld.getBlockState(footPos.up());
		if (footState.isSolidBlock(serverWorld, footPos) || headState.isSolidBlock(serverWorld, footPos.up())) {
			if (this.structureExitVec != null) {
				this.requestTeleport(this.structureExitVec.x, this.structureExitVec.y, this.structureExitVec.z);
			}
		}

		// If still airborne, keep levitation on and let tickGentleDescent() guide them smoothly down.
		// Do NOT restore gravity here — that causes a free-fall and fall damage.
		// tickGentleDescent is called from MinionBuildGoal.tick() every tick until landed.
		boolean overSolid = this.isOnGround() || this.hasSolidGroundBeneath(serverWorld);
		if (overSolid) {
			// Already on ground — clean stop
			this.setArcaneLevitating(false);
			this.setNoGravity(false);
			this.setVelocity(0.0D, 0.0D, 0.0D);
			this.velocityModified = true;
		} else {
			// Still airborne — keep levitation active, MinionBuildGoal.tick() calls tickGentleDescent() each tick
			this.fallDistance = 0.0F;
			// Bleed off any lateral egress velocity so we descend cleanly
			this.setVelocity(this.getVelocity().x * 0.2D, -0.22D, this.getVelocity().z * 0.2D);
			this.velocityModified = true;
		}
		this.structureExitVec = null;

		serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.5D, this.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.05D);
		serverWorld.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.2D, this.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.02D);
	}

	/**
	 * @param box The bounding box to check.
	 * @return true if the minion is within or directly adjacent to the structure bounding box.
	 */
	public boolean isInsideStructure(BlockBox box) {
		if (box == null) {
			return false;
		}
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();
		return x >= box.getMinX() - 0.2D && x <= box.getMaxX() + 1.2D &&
		       y >= box.getMinY() - 0.2D && y <= box.getMaxY() + 1.5D &&
		       z >= box.getMinZ() - 0.2D && z <= box.getMaxZ() + 1.2D;
	}

	/**
	 * Scans the outer perimeter around a structure bounding box to find a safe, open exterior
	 * position with solid footing and clear headroom for builder egress.
	 *
	 * @param world     The world.
	 * @param box       The structure bounding box.
	 * @param anchorPos The reference anchor or doorway location.
	 * @return A safe 3D exterior exit position.
	 */
	public static Vec3d findExteriorExitPosition(World world, BlockBox box, BlockPos anchorPos) {
		if (box == null) {
			return anchorPos != null ? Vec3d.ofBottomCenter(anchorPos) : null;
		}

		int minX = box.getMinX();
		int maxX = box.getMaxX();
		int minY = box.getMinY();
		int minZ = box.getMinZ();
		int maxZ = box.getMaxZ();
		int groundY = minY;

		List<BlockPos> candidates = new ArrayList<>();
		// Front face (South / maxZ)
		for (int x = minX; x <= maxX; x += 2) {
			candidates.add(new BlockPos(x, groundY, maxZ + 2));
		}
		// Back face (North / minZ)
		for (int x = minX; x <= maxX; x += 2) {
			candidates.add(new BlockPos(x, groundY, minZ - 2));
		}
		// West face (minX)
		for (int z = minZ; z <= maxZ; z += 2) {
			candidates.add(new BlockPos(minX - 2, groundY, z));
		}
		// East face (maxX)
		for (int z = minZ; z <= maxZ; z += 2) {
			candidates.add(new BlockPos(maxX + 2, groundY, z));
		}

		// If anchorPos is specified, prioritize candidates closest to anchorPos
		if (anchorPos != null) {
			candidates.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(anchorPos)));
		}

		for (BlockPos cand : candidates) {
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos checkPos = cand.add(0, dy, 0);
				BlockPos ground = checkPos.down();
				if (world.getBlockState(ground).isSolidBlock(world, ground)
						&& !world.getBlockState(checkPos).isSolidBlock(world, checkPos)
						&& !world.getBlockState(checkPos.up()).isSolidBlock(world, checkPos.up())) {
					return Vec3d.ofBottomCenter(checkPos);
				}
			}
		}

		if (anchorPos != null) {
			return Vec3d.ofBottomCenter(anchorPos.add(0, 0, 2));
		}
		return Vec3d.ofBottomCenter(new BlockPos(maxX + 2, groundY, maxZ + 2));
	}

	/**
	 * @return The UUID of the requesting Builder minion that commissioned the mob procurement contract, or null.
	 */
	public UUID getProcurementRequesterUuid() {
		return this.procurementRequesterUuid;
	}

	/**
	 * Sets the UUID of the requesting Builder minion for the active procurement contract.
	 *
	 * @param uuid The requester's UUID.
	 */
	public void setProcurementRequesterUuid(UUID uuid) {
		this.procurementRequesterUuid = uuid;
	}

	/**
	 * @return The requested mob material item being hunted for, or null.
	 */
	public Item getProcurementItem() {
		return this.procurementItem;
	}

	/**
	 * Sets the target material item for the active procurement contract.
	 *
	 * @param item The requested material item.
	 */
	public void setProcurementItem(Item item) {
		this.procurementItem = item;
	}

	/**
	 * @return The active living entity target designated for mob procurement, or null.
	 */
	public LivingEntity getProcurementTarget() {
		return this.procurementTarget;
	}

	/**
	 * Sets the active living entity target designated for mob procurement.
	 *
	 * @param target The target entity.
	 */
	public void setProcurementTarget(LivingEntity target) {
		this.procurementTarget = target;
	}

	/**
	 * @return true if this minion is currently assigned an active mob material procurement task.
	 */
	public boolean hasActiveProcurement() {
		return this.procurementRequesterUuid != null || this.procurementItem != null || this.procurementTarget != null;
	}

	/**
	 * Clears the active mob material procurement contract and resets associated tracking fields.
	 */
	public void clearProcurement() {
		this.procurementRequesterUuid = null;
		this.procurementItem = null;
		this.procurementTarget = null;
	}

	/**
	 * Alias for clearProcurement.
	 */
	public void clearProcurementTask() {
		this.clearProcurement();
	}

	/**
	 * @return The explicit active 3D traversal destination, or null if unset.
	 */
	public Vec3d getActiveTraversalDestination() {
		return this.activeTraversalDestination;
	}

	/**
	 * Sets the explicit active 3D traversal destination for Arcane Levitation and navigation.
	 *
	 * @param destination The target Vec3d in world space, or null to clear.
	 */
	public void setActiveTraversalDestination(Vec3d destination) {
		this.activeTraversalDestination = destination;
	}

	/**
	 * Clears the explicit active 3D traversal destination.
	 */
	public void clearActiveTraversalDestination() {
		this.activeTraversalDestination = null;
		this.traversalManeuverActive = false;
		this.traversalCrestTicks = 0;
	}

	/**
	 * Resolves the primary active target destination for 3D traversal and navigation.
	 * Prioritizes explicit goal traversal destinations, active combat targets, guard anchor positions,
	 * owner commander locations, and ongoing navigation destinations.
	 *
	 * @return The resolved target {@link Vec3d}, or null if no destination is active.
	 */
	public Vec3d resolveActiveTargetDestination() {
		if (this.activeTraversalDestination != null) {
			return this.activeTraversalDestination;
		}

		LivingEntity combatTarget = this.getTarget();
		if (combatTarget != null && combatTarget.isAlive()) {
			return combatTarget.getPos();
		}

		BlockPos anchor = this.getGuardAnchorPos();
		if (anchor != null) {
			return Vec3d.ofBottomCenter(anchor);
		}

		LivingEntity owner = this.getOwner();
		if (owner != null && owner.isAlive()) {
			boolean isFollowingOwner = this.isSelected();
			if (isFollowingOwner && (!this.getNavigation().isIdle() || this.getMoveControl().isMoving() || this.squaredDistanceTo(owner) > 4.0D)) {
				return owner.getPos();
			}
		}

		if (this.hasLeader()) {
			MinionEntity leader = this.resolveLeader();
			if (leader != null && leader.isAlive()) {
				return leader.getPos();
			}
		}

		if (this.getPatrolRouteId() >= 0 && this.getOwnerUuid() != null) {
			com.example.patrol.PatrolRoute route = com.example.patrol.PatrolRouteManager.getInstance().getRoute(this.getOwnerUuid(), this.getPatrolRouteId());
			if (route != null && !route.waypoints().isEmpty()) {
				int idx = MathHelper.clamp(this.getCurrentWaypointIndex(), 0, route.waypoints().size() - 1);
				return route.waypoints().get(idx).toBottomCenterPos();
			}
		}

		if (this.getNavigation().getTargetPos() != null) {
			return Vec3d.ofBottomCenter(this.getNavigation().getTargetPos());
		}

		// 3D Airborne Fallback: If trapped in a hole/pit or obstructed by high wall, direct flight toward owner
		if (this.isSelected() && owner != null && owner.isAlive() && !this.isSitting() && this.getGuardAnchorPos() == null && !this.hasLeader()) {
			if (this.squaredDistanceTo(owner) > 4.0D) {
				return owner.getPos();
			}
		}

		return null;
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
		this.dataTracker.set(ROLE_ID, role != null ? role.getId() : MinionRole.AUTO.getId());
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
		if (guardAnchorPos != null) {
			this.clearAssaultTargets();
		}
	}

	/**
	 * Sets the active assault target queue for this minion thrall during mass assault maneuvers.
	 * Filters for alive, valid entities and immediately acquires the nearest target if not currently fighting.
	 *
	 * @param targets Collection of hostile targets to queue for sequential elimination.
	 */
	public void setAssaultTargets(Collection<? extends LivingEntity> targets) {
		this.assaultTargets.clear();
		if (targets != null) {
			for (LivingEntity target : targets) {
				if (target != null && target.isAlive() && !target.isRemoved() && target.getWorld() == this.getWorld()) {
					if (!this.assaultTargets.contains(target)) {
						this.assaultTargets.add(target);
					}
				}
			}
		}
		if (this.getTarget() == null || !this.getTarget().isAlive()) {
			this.acquireNextAssaultTarget();
		}
	}

	/**
	 * Checks whether the minion has active assault targets remaining in its queue.
	 * Prunes dead, removed, or out-of-world targets.
	 *
	 * @return true if at least one alive assault target remains.
	 */
	public boolean hasAssaultTargets() {
		this.assaultTargets.removeIf(e -> e == null || !e.isAlive() || e.isRemoved() || e.getWorld() != this.getWorld());
		return !this.assaultTargets.isEmpty();
	}

	/**
	 * Returns an unmodifiable list of remaining assault targets in the queue.
	 *
	 * @return Unmodifiable list of alive assault targets.
	 */
	public List<LivingEntity> getAssaultTargets() {
		this.assaultTargets.removeIf(e -> e == null || !e.isAlive() || e.isRemoved() || e.getWorld() != this.getWorld());
		return Collections.unmodifiableList(this.assaultTargets);
	}

	/**
	 * Clears all pending assault targets, immediately stopping sequential mass assault tracking.
	 */
	public void clearAssaultTargets() {
		this.assaultTargets.clear();
	}

	/**
	 * Acquires the next closest alive assault target from the queue, engages pathfinding,
	 * and sets the entity target.
	 *
	 * @return The newly acquired assault target, or null if no valid targets remain.
	 */
	public LivingEntity acquireNextAssaultTarget() {
		this.assaultTargets.removeIf(e -> e == null || !e.isAlive() || e.isRemoved() || e.getWorld() != this.getWorld());
		if (this.assaultTargets.isEmpty()) {
			return null;
		}

		// Prioritize closest living target in the assault queue within 48 blocks
		LivingEntity nextTarget = this.assaultTargets.stream()
			.filter(t -> this.squaredDistanceTo(t) <= 2304.0D) // 48 blocks squared
			.min(Comparator.comparingDouble(this::squaredDistanceTo))
			.orElse(null);

		if (nextTarget != null) {
			this.setTarget(nextTarget);
			this.setAttacking(true);
			this.getNavigation().startMovingTo(nextTarget, 1.35D);
		}
		return nextTarget;
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new MinionNavigation(this, world);
	}

	/**
	 * Creates default living attributes for the minion entity.
	 * Boosted base health (40 HP), armor (4), and damage (5) for high combat survivability,
	 * with step height (1.25D) enabling smooth movement over stairs, slabs, and 1-block steps.
	 */
	public static DefaultAttributeContainer.Builder createMinionAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3D)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0D)
			.add(EntityAttributes.GENERIC_ARMOR, 4.0D)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D)
			.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.25D);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new SitGoal(this));
		this.goalSelector.add(2, new LongDoorInteractGoal(this, true));
		this.goalSelector.add(3, new SentinelGuardGoal(this));
		this.goalSelector.add(3, new SentinelHealAllyGoal(this));
		this.goalSelector.add(3, new WaypointHoldGoal(this));
		this.goalSelector.add(3, new MinionBuildGoal(this));
		this.goalSelector.add(3, new com.example.entity.ai.goal.MinionPatrolGoal(this));
		this.goalSelector.add(4, new MinionRangedAttackGoal(this, 1.25D, 20));
		this.goalSelector.add(5, new MeleeAttackGoal(this, 1.35D, true) {
			@Override
			public boolean canStart() {
				ItemStack held = MinionEntity.this.getMainHandStack();
				// If holding a pure ranged/thrown weapon (bow, crossbow, grenade stick, tnt stick), suppress melee goal
				if (MinionEntity.isRangedWeapon(held) && !MinionEntity.isMeleeWeapon(held)) {
					return false;
				}
				return super.canStart();
			}

			@Override
			public boolean shouldContinue() {
				ItemStack held = MinionEntity.this.getMainHandStack();
				if (MinionEntity.isRangedWeapon(held) && !MinionEntity.isMeleeWeapon(held)) {
					return false;
				}
				return super.shouldContinue();
			}
		});
		this.goalSelector.add(6, new com.example.entity.ai.goal.MinionFollowLeaderGoal(this));
		this.goalSelector.add(6, new MinionFormationFollowGoal(this));
		this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0D) {
			@Override
			public boolean canStart() {
				if (MinionEntity.this.getGuardAnchorPos() != null || MinionEntity.this.isSelected() || MinionEntity.this.hasLeader()
						|| MinionEntity.this.isActivelyBuilding()
						|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(MinionEntity.this)) {
					return false;
				}
				return super.canStart();
			}
		});
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(9, new LookAroundGoal(this));

		this.targetSelector.add(1, new com.example.entity.ai.goal.TrackLeaderAttackerGoal(this));
		this.targetSelector.add(1, new TrackOwnerAttackerGoal(this) {
			@Override
			public boolean canStart() {
				if (MinionEntity.this.hasLeader() || MinionEntity.this.isActivelyBuilding()
						|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(MinionEntity.this)) {
					return false;
				}
				return super.canStart();
			}
		});
		this.targetSelector.add(2, new com.example.entity.ai.goal.AttackWithLeaderGoal(this));
		this.targetSelector.add(2, new AttackWithOwnerGoal(this) {
			@Override
			public boolean canStart() {
				if (MinionEntity.this.hasLeader() || MinionEntity.this.isActivelyBuilding()
						|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(MinionEntity.this)) {
					return false;
				}
				return super.canStart();
			}
		});
		this.targetSelector.add(3, new RevengeGoal(this) {
			@Override
			public boolean canStart() {
				if (MinionEntity.this.isActivelyBuilding()
						|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(MinionEntity.this)) {
					return false;
				}
				return super.canStart();
			}
		}.setGroupRevenge());
		this.targetSelector.add(4, new MinionActiveTargetGoal(this, 24.0D));
	}

	@Override
	public void tick() {
		super.tick();

		if (this.levitationCooldown > 0) {
			this.levitationCooldown--;
		}

		// Builder block phasing: allow passage through blocks only while actively building or exiting building
		boolean phasing = this.isPhasingBlocks();
		this.noClip = phasing;
		if (phasing) {
			this.setNoGravity(true);
			this.fallDistance = 0.0F;
		}

		// Minions working or descending from construction/mining never accumulate fall distance
		if (this.isActivelyBuilding() || this.arcaneLevitating || this.exitingBuilding
				|| (this.getWorld() != null && this.getWorld().getTime() - this.lastConstructionActivityTick <= 100L)
				|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this)) {
			this.fallDistance = 0.0F;
		}

		if (!this.getWorld().isClient()) {
			this.dataTracker.set(PHASING_BLOCKS, phasing);

			if (this.getWorld() instanceof ServerWorld serverWorld) {
				// Unified door auto-opening for all minions in motion or navigating
				this.autoOpenNearbyDoors(serverWorld);
			}

			// Post-construction structure egress phasing
			if (this.exitingBuilding) {
				this.tickBuildingEgress();
			}

			LivingEntity currentTarget = this.getTarget();

			// If current combat target died, was removed, or is absent, chain to next queued assault target
			if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved()) {
				if (this.hasAssaultTargets()) {
					currentTarget = this.acquireNextAssaultTarget();
				}
			}

			// Track combat state
			if (currentTarget != null && currentTarget.isAlive()) {
				this.outOfCombatTicks = 0;
			} else {
				this.outOfCombatTicks++;
				// Post-combat transition: when previous target was cleared or died and no assault targets remain, return to owner
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
				if (this.getRole() == MinionRole.AUTO) {
					this.evaluateAutoRole();
				}
				autoEquipFromInventory();
			}

			// Auto-clear climbing flag if minion is no longer within a scaffolding block
			BlockState currentFootState = this.getBlockStateAtPos();
			if (this.climbingScaffolding && !currentFootState.isOf(Blocks.SCAFFOLDING)) {
				this.climbingScaffolding = false;
			}

			// Auto-clear preview glowing outline if not refreshed within 6 ticks
			if (this.previewGlowTicks > 0) {
				this.previewGlowTicks--;
				if (this.previewGlowTicks == 0 && this.isPreviewGlowing()) {
					this.setPreviewGlowing(false);
				}
			}

			// Arcane Builder Levitation particle trail and fall safety (strictly for active builders)
			if (this.arcaneLevitating && (this.activelyBuilding || this.exitingBuilding)) {
				this.fallDistance = 0.0F;
				if (this.getWorld() instanceof ServerWorld serverWorld && this.age % 2 == 0) {
					serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 2, 0.15D, 0.05D, 0.15D, 0.02D);
					serverWorld.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.15D, this.getZ(), 1, 0.2D, 0.1D, 0.2D, 0.05D);
				}
			}

			// Universal 3D Arcane Levitation Traversal across all minions
			this.tickUniversalArcaneLevitation();
		}
	}

	@Override
	public void pushAwayFrom(Entity entity) {
		// Suppress collisions and mutual shoving when phasing through blocks
		if (this.isPhasingBlocks()) {
			return;
		}
		if (entity instanceof MinionEntity other && other.isPhasingBlocks()) {
			return;
		}
		// Suppress mutual shoving between allied minions when holding station, resting, sitting, or building/levitating
		if (entity instanceof MinionEntity ally && ally.isOwner(this.getOwner())) {
			if (this.isSitting() || this.getGuardAnchorPos() != null || this.isArcaneLevitating() || this.isActivelyBuilding()
					|| ally.isArcaneLevitating() || ally.isActivelyBuilding()
					|| (this.getNavigation().isIdle() && !this.getMoveControl().isMoving())) {
				return;
			}
		}
		super.pushAwayFrom(entity);
	}

	@Override
	public boolean isPushable() {
		if (this.isPhasingBlocks() || this.isSitting() || this.getGuardAnchorPos() != null || this.isArcaneLevitating() || this.isActivelyBuilding()) {
			return false;
		}
		return super.isPushable();
	}

	@Override
	public boolean onKilledOther(ServerWorld world, LivingEntity other) {
		boolean result = super.onKilledOther(world, other);
		if (this.hasActiveProcurement()) {
			com.example.entity.ai.logistics.MinionHarvestingHelper.processMobHuntingDrops(this, other, world);
		}
		if (this.hasAssaultTargets()) {
			LivingEntity next = this.acquireNextAssaultTarget();
			if (next != null) {
				return result;
			}
		}
		returnToOwnerPostCombat();
		return result;
	}

	/**
	 * Detects whether an impassable physical obstacle 2 blocks or higher exists directly in front of the minion.
	 * Returns true ONLY if:
	 * 1. The block at foot level is a fence/wall (> 1.25D collision height) or block at head level (minionFootY + 1) is solid.
	 * 2. Or the target destination is elevated 2.0 blocks or higher.
	 * 1-block steps, slabs, and stairs return false, enabling smooth ground walking like a player.
	 */
	public boolean detectObstacleTwoBlocksOrHigher(ServerWorld world, double dirX, double dirZ) {
		if (this.traversalManeuverActive && (this.getY() < this.traversalClearanceAltitude - 0.1D || this.traversalCrestTicks > 0)) {
			return true;
		}

		double hDist = Math.sqrt(dirX * dirX + dirZ * dirZ);
		double normX;
		double normZ;
		if (hDist > 0.05D) {
			normX = dirX / hDist;
			normZ = dirZ / hDist;
		} else {
			float yawRad = this.getYaw() * 0.017453292F;
			normX = -Math.sin(yawRad);
			normZ = Math.cos(yawRad);
		}

		int minionFootY = this.getBlockY();
		for (double dist : new double[]{0.6D, 1.2D}) {
			int aheadX = (int) Math.floor(this.getX() + normX * dist);
			int aheadZ = (int) Math.floor(this.getZ() + normZ * dist);
			if (aheadX == this.getBlockX() && aheadZ == this.getBlockZ()) {
				continue;
			}

			BlockPos posFoot = new BlockPos(aheadX, minionFootY, aheadZ);
			BlockState stateFoot = world.getBlockState(posFoot);

			// Fences and walls have 1.5-block collision boxes that exceed player step height
			if (stateFoot.isIn(BlockTags.FENCES) || stateFoot.isIn(BlockTags.WALLS)) {
				return true;
			}

			// Obstacle 2 blocks or higher: block at head level (footY + 1) is solid or has collision
			BlockPos posHead = new BlockPos(aheadX, minionFootY + 1, aheadZ);
			BlockState stateHead = world.getBlockState(posHead);
			if (stateHead.isSolidBlock(world, posHead) || stateHead.isFullCube(world, posHead) || !stateHead.getCollisionShape(world, posHead).isEmpty()) {
				// Ensure it is not an open door or passable trapdoor
				if (!stateHead.isIn(BlockTags.DOORS) && !stateHead.isIn(BlockTags.TRAPDOORS)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Detects whether this minion is trapped in a hole, trench, or pit deeper than 1 block (>= 2 blocks deep).
	 * Checks cardinal neighbors at head/eye level (footY + 1) to determine if surrounding ground rises above the minion.
	 * 1-block terrain dips have head level in open air and return false, allowing normal ground stepping with 1.25D step height.
	 */
	public boolean isInHoleOrDepression(ServerWorld world) {
		BlockPos feet = this.getBlockPos();
		int headLevelEnclosure = 0;
		for (Direction dir : Direction.Type.HORIZONTAL) {
			BlockPos sideHead = feet.offset(dir).up();
			BlockState sHead = world.getBlockState(sideHead);
			if (sHead.isSolidBlock(world, sideHead) || !sHead.getCollisionShape(world, sideHead).isEmpty()) {
				headLevelEnclosure++;
			}
		}
		// A true pit has walls rising at or above head level (>= 2 blocks deep) on at least 3 sides.
		// 1-block terrain dips have head level in open air and must return false (stepped out of like a player).
		return headLevelEnclosure >= 3;
	}

	/**
	 * Scans immediate adjacent cardinal directions around the minion up to 6 blocks high to determine the required escape rim clearance altitude.
	 * Stops at the first open air position with headroom to avoid scanning into overhead tree canopies, roofs, or the sky.
	 */
	public double getHoleRimClearanceAltitude(ServerWorld world) {
		BlockPos feet = this.getBlockPos();
		int rimY = feet.getY();
		for (Direction dir : Direction.Type.HORIZONTAL) {
			BlockPos sidePos = feet.offset(dir);
			for (int dy = 1; dy <= 6; dy++) {
				BlockPos checkPos = sidePos.up(dy);
				BlockState s = world.getBlockState(checkPos);
				if (s.isAir() || s.getCollisionShape(world, checkPos).isEmpty()) {
					// Check if there is headroom above this air block to walk out onto the rim
					BlockPos headPos = checkPos.up();
					BlockState headState = world.getBlockState(headPos);
					if (headState.isAir() || headState.getCollisionShape(world, headPos).isEmpty()) {
						int wallTopY = checkPos.getY() - 1;
						if (wallTopY > rimY) {
							rimY = wallTopY;
						}
						break; // Found the rim ground level for this direction; stop scanning into tree canopies/sky!
					}
				}
			}
		}
		return rimY + 1.25D;
	}

	/**
	 * Detects whether a hole, chasm, or ravine 1 block or longer exists directly in front of the minion.
	 * Returns true ONLY if:
	 * 1. The ground 1 block ahead is air/empty at foot level - 1 AND at foot level - 2 (drop of 2+ blocks deep).
	 * 2. The minion is attempting to reach a destination at or above current elevation across the gap.
	 * Normal 1-block steps down terrain return false, allowing natural ground descent without levitating.
	 */
	public boolean detectHoleOrChasmAhead(ServerWorld world, double dirX, double dirZ) {
		double hDist = Math.sqrt(dirX * dirX + dirZ * dirZ);
		double normX;
		double normZ;
		if (hDist > 0.05D) {
			normX = dirX / hDist;
			normZ = dirZ / hDist;
		} else {
			float yawRad = this.getYaw() * 0.017453292F;
			normX = -Math.sin(yawRad);
			normZ = Math.cos(yawRad);
		}

		int minionFootY = this.getBlockY();
		for (double dist : new double[]{0.8D, 1.3D}) {
			int aheadX = (int) Math.floor(this.getX() + normX * dist);
			int aheadZ = (int) Math.floor(this.getZ() + normZ * dist);
			if (aheadX == this.getBlockX() && aheadZ == this.getBlockZ()) {
				continue;
			}

			BlockPos posBelow1 = new BlockPos(aheadX, minionFootY - 1, aheadZ);
			BlockPos posBelow2 = new BlockPos(aheadX, minionFootY - 2, aheadZ);
			BlockState stateBelow1 = world.getBlockState(posBelow1);
			BlockState stateBelow2 = world.getBlockState(posBelow2);

			boolean empty1 = stateBelow1.isAir() || stateBelow1.getCollisionShape(world, posBelow1).isEmpty();
			boolean empty2 = stateBelow2.isAir() || stateBelow2.getCollisionShape(world, posBelow2).isEmpty();

			if (empty1 && empty2) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Updates Arcane Levitation traversal.
	 * Traversal levitation is strictly retired for normal navigation: minions navigate via standard ground pathfinding
	 * with 1.25D step height and companion catch-up teleportation.
	 * Arcane Levitation is strictly isolated to active construction in MinionBuildGoal.
	 */
	public void tickUniversalArcaneLevitation() {
		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		// Non-builder minions never levitate during navigation; they use pure ground pathfinding with 1.25D step height.
		// IMPORTANT: When actively building, MinionBuildGoal is the SOLE authority over levitation velocity.
		// This method must NOT apply any velocity to builder minions — doing so creates a velocity conflict that
		// causes the levitation-teleport loop (goal applies velocity → this method overwrites it → stall watchdog
		// fires → requestTeleport → repeat). Fall-safety particles are handled separately at the tick() call-site.
		if (this.activelyBuilding || this.exitingBuilding) {
			// Actively building or egressing: velocity is strictly controlled by MinionBuildGoal or tickBuildingEgress()
			return;
		} else if (this.matchesRole(MinionRole.BUILDER)) {
			// Builder/miner minions are managed exclusively by MinionBuildGoal during active construction.
			// Outside of active construction sessions, builders navigate on foot like normal players (1.25D step height).
			// If not engaged in an active build/dismantle session, levitation must be cleared immediately.
			if (this.arcaneLevitating && !com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this)) {
				this.setArcaneLevitating(false);
				this.setNoGravity(false);
			}
			return;
		} else {
			if (this.arcaneLevitating) {
				this.setArcaneLevitating(false);
				this.setNoGravity(false);
			}
			if (this.getId() >= 0) {
				return;
			}
		}

		// Passive sitting thralls or dead entities do not levitate
		if (!this.isAlive() || this.isSitting()) {
			if (this.arcaneLevitating) {
				this.setArcaneLevitating(false);
			}
			return;
		}

		if (this.arcaneLevitating) {
			this.arcaneLevitationTicks++;
		}

		Vec3d targetDest = this.resolveActiveTargetDestination();

		// If minion is levitating but has no active destination, smoothly float down to ground
		if (targetDest == null) {
			if (this.arcaneLevitating) {
				if (!this.isOnGround() && !this.hasSolidGroundBeneath(serverWorld) && this.getY() > serverWorld.getBottomY()) {
					this.setVelocity(0.0D, -0.22D, 0.0D);
					this.velocityModified = true;
				} else {
					this.setArcaneLevitating(false);
					this.setNoGravity(false);
					this.setVelocity(0.0D, 0.0D, 0.0D);
					this.velocityModified = true;
					serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 4, 0.15D, 0.05D, 0.15D, 0.02D);
					serverWorld.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.2D, this.getZ(), 3, 0.2D, 0.1D, 0.2D, 0.05D);
				}
			}
			return;
		}

		double dx = targetDest.x - this.getX();
		double dy = targetDest.y - this.getY();
		double dz = targetDest.z - this.getZ();
		double horizontalDistSq = dx * dx + dz * dz;
		double totalDistSq = horizontalDistSq + dy * dy;

		// Arrival check within tolerance (horizontal <= 2.0 blocks, vertical <= 1.5 blocks)
		boolean arrived = horizontalDistSq <= 4.0D && Math.abs(dy) <= 1.5D;

		if (arrived) {
			this.traversalStallTicks = 0;
			if (this.arcaneLevitating) {
				if (!this.isOnGround() && !this.hasSolidGroundBeneath(serverWorld) && this.getY() > serverWorld.getBottomY()) {
					// Smooth descent glide to ground surface
					this.setVelocity(0.0D, -0.22D, 0.0D);
					this.velocityModified = true;
				} else {
					this.setArcaneLevitating(false);
					this.setNoGravity(false);
					this.setVelocity(0.0D, 0.0D, 0.0D);
					this.velocityModified = true;
					// Landing particle fanfare
					serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 6, 0.2D, 0.1D, 0.2D, 0.02D);
					serverWorld.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.2D, this.getZ(), 4, 0.25D, 0.15D, 0.25D, 0.05D);
				}
			}
			return;
		}

		// Determine dynamic target clearance altitude based on destination and obstacles ahead
		double targetClearanceAltitude = targetDest.y;
		double obstacleTopClearanceY = Double.NEGATIVE_INFINITY;

		boolean inPitOrHole = this.isInHoleOrDepression(serverWorld);
		if (inPitOrHole) {
			double rimClearance = this.getHoleRimClearanceAltitude(serverWorld);
			if (rimClearance > obstacleTopClearanceY) {
				obstacleTopClearanceY = rimClearance;
			}
		}

		// Scan along the horizontal traversal vector toward targetDest to detect obstacle walls/ledges
		double hDist = Math.sqrt(horizontalDistSq);
		double dirX = (hDist > 0.01D) ? dx / hDist : 0.0D;
		double dirZ = (hDist > 0.01D) ? dz / hDist : 0.0D;

		if (hDist > 0.15D) {
			int minionFootY = this.getBlockY();
			// Bounded vertical scan window: scan up to maxScanY to detect obstacle top
			int maxScanY = Math.min(minionFootY + 24, Math.max(minionFootY + 6, (int) Math.ceil(targetDest.y + 3.0D)));

			for (double step = 0.4D; step <= 2.2D; step += 0.8D) {
				int aheadX = (int) Math.floor(this.getX() + dirX * step);
				int aheadZ = (int) Math.floor(this.getZ() + dirZ * step);

				// Scan upward from minion feet (or ground below if airborne) up to maxScanY to detect obstacle top
				int scanStartY = this.isOnGround() ? minionFootY : Math.min(minionFootY, MathHelper.floor(this.getY() - 1.0D));
				int topSolidY = -1;
				for (int checkY = scanStartY; checkY <= maxScanY; checkY++) {
					BlockPos checkPos = new BlockPos(aheadX, checkY, aheadZ);
					BlockState state = serverWorld.getBlockState(checkPos);
					boolean isSolid = state.isSolidBlock(serverWorld, checkPos) || state.isFullCube(serverWorld, checkPos) || !state.getCollisionShape(serverWorld, checkPos).isEmpty();
					if (isSolid) {
						topSolidY = checkY;
					} else if (topSolidY >= scanStartY) {
						// Hit open air above obstacle wall; check if there is headroom for the minion to stand/pass
						BlockPos headCheck = checkPos.up();
						BlockState headState = serverWorld.getBlockState(headCheck);
						boolean headClear = headState.isAir() || headState.getCollisionShape(serverWorld, headCheck).isEmpty();
						if (headClear) {
							BlockPos topSolidPos = new BlockPos(aheadX, topSolidY, aheadZ);
							BlockState topState = serverWorld.getBlockState(topSolidPos);
							double fenceBoost = (topState.isIn(BlockTags.FENCES) || topState.isIn(BlockTags.WALLS)) ? 0.5D : 0.0D;
							double neededClearance = topSolidY + 1.25D + fenceBoost;
							if (neededClearance > obstacleTopClearanceY) {
								obstacleTopClearanceY = neededClearance;
							}
							break; // Found the top surface of the obstacle, STOP scanning into the sky!
						}
					}
				}
			}
		}
		targetClearanceAltitude = Math.max(targetDest.y, obstacleTopClearanceY);
		if (this.traversalManeuverActive) {
			targetClearanceAltitude = Math.max(targetClearanceAltitude, this.traversalClearanceAltitude);
		}

		// Headroom ceiling check: if solid blocks exist directly above minion, clamp clearance and velocity to ceiling
		boolean solidCeilingDirectlyOverhead = false;
		if (hasCeilingAboveMinion(serverWorld, 2)) {
			solidCeilingDirectlyOverhead = true;
		}
		for (int cy = this.getBlockY() + 2; cy <= this.getBlockY() + 4; cy++) {
			BlockPos ceilPos = new BlockPos(this.getBlockX(), cy, this.getBlockZ());
			BlockState ceilState = serverWorld.getBlockState(ceilPos);
			if (ceilState.isSolidBlock(serverWorld, ceilPos)) {
				targetClearanceAltitude = Math.min(targetClearanceAltitude, cy - 1.9D);
				if (cy <= this.getBlockY() + 2 || this.getY() >= cy - 1.9D) {
					solidCeilingDirectlyOverhead = true;
				}
				break;
			}
		}

		// Non-builder landing check during traversal:
		// Whenever a minion is over solid ground (on top of a scaled ledge, roof, or ground)
		// and is not actively climbing a higher obstacle directly ahead:
		// IMMEDIATELY LAND! Deactivate levitation and restore normal ground walking & step height!
		if (!this.activelyBuilding && this.arcaneLevitating) {
			BlockPos feet = this.getBlockPos();
			BlockPos below = feet.down();
			BlockState belowState = serverWorld.getBlockState(below);
			boolean overSolidGround = this.isOnGround() || belowState.isSolidBlock(serverWorld, below);
			if (this.hasSolidGroundBeneath(serverWorld)) {
				overSolidGround = true;
			}
			boolean climbingWallAhead = (obstacleTopClearanceY > this.getY() + 0.3D) && (this.horizontalCollision || this.traversalStallTicks >= 1);
			if (this.traversalManeuverActive && (this.getY() < this.traversalClearanceAltitude - 0.1D || this.traversalCrestTicks > 0)) {
				climbingWallAhead = true;
			}

			if (overSolidGround && !climbingWallAhead) {
				this.traversalManeuverActive = false;
				this.traversalClearanceAltitude = 0.0D;
				this.traversalCrestTicks = 0;
				this.setArcaneLevitating(false);
				this.setNoGravity(false);
				this.arcaneLevitationTicks = 0;
				this.traversalStallTicks = 0;
				this.levitationCooldown = 0;
				this.setVelocity(this.getVelocity().x * 0.5D, 0.0D, this.getVelocity().z * 0.5D);
				this.velocityModified = true;
				serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 4, 0.15D, 0.05D, 0.15D, 0.02D);
				serverWorld.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.2D, this.getZ(), 3, 0.2D, 0.1D, 0.2D, 0.05D);
				return;
			}

			// Watchdog anti-stall: if levitating for over 40 ticks with forward collision stall, or over 120 ticks failsafe, force land
			boolean stalledInAir = this.arcaneLevitationTicks > 40 && this.horizontalCollision;
			if (this.arcaneLevitationTicks > 120 || stalledInAir) {
				this.traversalManeuverActive = false;
				this.traversalClearanceAltitude = 0.0D;
				this.traversalCrestTicks = 0;
				this.setArcaneLevitating(false);
				this.setNoGravity(false);
				this.arcaneLevitationTicks = 0;
				this.levitationCooldown = 0;
				this.setVelocity(0.0D, -0.22D, 0.0D);
				this.velocityModified = true;
				return;
			}
		}

		// Melee Warrior grounding in combat:
		// If a melee Warrior is fighting a target on the ground and is elevated above it (dy < -0.8D),
		// glide down immediately to melee reach rather than hovering in the air.
		if ((this.getRole() == MinionRole.WARRIOR || this.matchesRole(MinionRole.WARRIOR)) && (!isRangedWeapon(this.getMainHandStack()) || isMeleeWeapon(this.getMainHandStack())) && this.getTarget() != null) {
			if (dy < -0.8D && this.arcaneLevitating) {
				this.setVelocity(dx * 0.15D, -0.35D, dz * 0.15D);
				this.velocityModified = true;
				return;
			}
		}

		// Minion is not within arrival tolerance: evaluate strict 2-condition levitation triggers
		boolean elevationDisparity = dy > 1.25D || dy < -1.5D;
		if (dy > 0 && dy < 2.0D) {
			// Slabs, stairs, and 1-block steps are stepped over like a player with 1.25D step height
			elevationDisparity = false;
		}
		if (dy < 0 && (this.isOnGround() || dy >= -3.0D)) {
			// Normal ground descents are stepped down like a player
			elevationDisparity = false;
		}

		boolean obstacleTwoBlocksOrHigher = this.detectObstacleTwoBlocksOrHigher(serverWorld, dx, dz);
		boolean holeOrChasmAhead = this.detectHoleOrChasmAhead(serverWorld, dx, dz);

		boolean movingIntent = !this.getNavigation().isIdle() || this.getMoveControl().isMoving();
		boolean horizontalStuck = this.horizontalCollision && movingIntent;
		boolean navigationStalled = !this.arcaneLevitating && movingIntent && this.getNavigation().isIdle() && totalDistSq > 4.0D;

		if (horizontalStuck || navigationStalled) {
			this.traversalStallTicks++;
		} else {
			this.traversalStallTicks = Math.max(0, this.traversalStallTicks - 1);
		}

		// Levitate ONLY for:
		// 1. Obstacle 2 blocks or higher ahead (or target 2+ blocks elevated: dy >= 2.0D)
		// 2. Hole 1 block or longer (drop 2+ blocks deep) ahead
		// 3. High structure drop descent (dy < -1.5D when airborne)
		boolean shouldLevitate = elevationDisparity || obstacleTwoBlocksOrHigher || holeOrChasmAhead;
		if (inPitOrHole && movingIntent) {
			shouldLevitate = true;
		}
		if (this.traversalManeuverActive && (this.getY() < this.traversalClearanceAltitude - 0.1D || this.traversalCrestTicks > 0)) {
			shouldLevitate = true;
		}

		// Indoor ceiling flight suppression:
		// If under an indoor ceiling or enclosure, suppress upward levitation triggers and prioritize ground pathing & door traversal
		boolean hasLowCeiling = hasCeilingAboveMinion(serverWorld, 3);
		if (hasLowCeiling && this.isOnGround() && dy <= 1.25D) {
			shouldLevitate = false;
		}

		if (shouldLevitate) {
			if (!this.arcaneLevitating) {
				this.setArcaneLevitating(true);
				serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 5, 0.2D, 0.1D, 0.2D, 0.02D);
				serverWorld.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.2D, this.getZ(), 3, 0.2D, 0.1D, 0.2D, 0.05D);
			}

			if (obstacleTopClearanceY > this.getY() + 1.4D || inPitOrHole || dy >= 2.0D) {
				if (!this.traversalManeuverActive) {
					this.traversalManeuverActive = true;
					this.traversalClearanceAltitude = targetClearanceAltitude;
				} else {
					this.traversalClearanceAltitude = Math.max(this.traversalClearanceAltitude, targetClearanceAltitude);
				}
			}

			// Arcane 3D gliding kinematics: full horizontal propulsion towards target
			double horizDist = Math.sqrt(horizontalDistSq);
			double speed = (this.traversalCrestTicks > 0 || horizDist > 10.0D) ? 0.45D : 0.38D;
			double vx = (horizDist > 0.01D) ? (dx / horizDist) * speed : 0.0D;
			double vz = (horizDist > 0.01D) ? (dz / horizDist) * speed : 0.0D;
			double vy;

			if (this.getY() < targetClearanceAltitude - 0.05D) {
				// We need to ascend to clear the obstacle or reach elevated target
				double liftRemaining = targetClearanceAltitude - this.getY();
				vy = Math.min(0.38D, liftRemaining * 0.5D + 0.22D);
				vy = Math.max(vy, 0.30D);
				this.traversalCrestTicks = 10;
			} else {
				// At or above target clearance altitude: clear the obstacle ledge smoothly
				vy = 0.0D;
				if (this.traversalCrestTicks > 0) {
					this.traversalCrestTicks--;
				} else if (this.getY() > targetDest.y + 0.5D) {
					// Controlled downward glide toward destination when above ground target,
					// only after obstacle lip has been crested!
					vy = Math.min(vy, -0.22D);
				}
			}

			// If directly colliding horizontally and still below clearance altitude, maintain lift (unless blocked by ceiling)
			if (this.horizontalCollision && this.getY() < targetClearanceAltitude - 0.05D && !solidCeilingDirectlyOverhead) {
				vy = Math.max(vy, 0.38D);
			}

			// Vertical velocity ceiling clamping: strictly prevent upward velocity into solid overhead blocks
			if (solidCeilingDirectlyOverhead && vy > 0.0D) {
				vy = 0.0D;
			}

			this.setVelocity(vx, vy, vz);
			this.velocityModified = true;
			this.fallDistance = 0.0F;
			if (!this.isOnGround()) {
				this.getNavigation().stop();
			}
			this.getLookControl().lookAt(targetDest.x, targetDest.y + 0.5D, targetDest.z);
		} else if (this.arcaneLevitating) {
			this.traversalManeuverActive = false;
			this.traversalClearanceAltitude = 0.0D;
			this.traversalCrestTicks = 0;
			// If minion should NOT levitate, restore gravity and ground walking immediately
			if (this.isOnGround() || this.hasSolidGroundBeneath(serverWorld)) {
				this.setArcaneLevitating(false);
				this.setNoGravity(false);
				this.arcaneLevitationTicks = 0;
				this.traversalStallTicks = 0;
				this.setVelocity(this.getVelocity().x * 0.5D, 0.0D, this.getVelocity().z * 0.5D);
				this.velocityModified = true;
				serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.1D, this.getZ(), 4, 0.15D, 0.05D, 0.15D, 0.02D);
			} else {
				// In air without solid ground beneath, glide down directly to ground
				this.setVelocity(this.getVelocity().x * 0.5D, -0.22D, this.getVelocity().z * 0.5D);
				this.velocityModified = true;
			}
		}
	}

	/**
	 * Commands the minion to immediately pathfind back to its post, leader, or owner after combat concludes.
	 * Stationed minions return to their guard anchor post, escorts return to their leader, and selected
	 * minions return to the master at 1.35D sprint speed. Unselected wandering minions remain in their area.
	 */
	public void returnToOwnerPostCombat() {
		if (this.isTamed() && !this.isSitting()) {
			if (this.getPatrolRouteId() >= 0) {
				return;
			}
			if (this.hasLeader()) {
				MinionEntity leader = this.resolveLeader();
				if (leader != null && leader.isAlive()) {
					if (this.squaredDistanceTo(leader) > 4.0D) {
						this.navigation.startMovingTo(leader, 1.35D);
					}
					return;
				}
			}
			BlockPos anchor = this.getGuardAnchorPos();
			if (anchor != null) {
				if (this.squaredDistanceTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D) > 4.0D) {
					this.navigation.startMovingTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.35D);
				}
			} else if (this.isSelected()) {
				LivingEntity owner = this.getOwner();
				if (owner != null && this.squaredDistanceTo(owner) > 4.0D) {
					this.navigation.startMovingTo(owner, 1.35D);
				}
			}
		}
	}

	/**
	 * Dynamically evaluates environmental priorities and shifts the adaptive archetype role
	 * when operating in {@link MinionRole#AUTO} mode:
	 * - Priority 1 (Medic / Sentinel): Commander or nearby allied minion wounded below 70% HP.
	 * - Priority 2 (Combat / Warrior): Target engaged or hostile mobs within 16m.
	 * - Priority 3 (Architect / Builder): Active construction session or peaceful base operations.
	 */
	public void evaluateAutoRole() {
		if (this.getRole() != MinionRole.AUTO || !(this.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		MinionRole targetAdaptiveRole;

		// 1. Check if owner or any nearby allied minion within 16m has health < 70%
		boolean needsMedic = false;
		if (this.getOwner() instanceof LivingEntity owner && owner.isAlive() && this.squaredDistanceTo(owner) <= 256.0D) {
			if (owner.getHealth() < owner.getMaxHealth() * 0.70F) {
				needsMedic = true;
			}
		}
		if (!needsMedic && this.getOwnerUuid() != null) {
			List<MinionEntity> woundedAllies = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				this.getBoundingBox().expand(16.0D),
				ally -> ally.isAlive() && Objects.equals(ally.getOwnerUuid(), this.getOwnerUuid()) && ally.getHealth() < ally.getMaxHealth() * 0.70F
			);
			if (!woundedAllies.isEmpty()) {
				needsMedic = true;
			}
		}

		if (needsMedic) {
			targetAdaptiveRole = MinionRole.SENTINEL;
		} else if (this.getTarget() != null && this.getTarget().isAlive()) {
			targetAdaptiveRole = MinionRole.WARRIOR;
		} else {
			List<LivingEntity> nearbyHostiles = serverWorld.getEntitiesByClass(
				LivingEntity.class,
				this.getBoundingBox().expand(16.0D),
				e -> e.isAlive() && (e instanceof net.minecraft.entity.mob.HostileEntity || e instanceof net.minecraft.entity.mob.Monster)
			);
			if (!nearbyHostiles.isEmpty()) {
				targetAdaptiveRole = MinionRole.WARRIOR;
			} else if (this.isActivelyBuilding() || this.isPhasingBlocks()) {
				targetAdaptiveRole = MinionRole.BUILDER;
			} else if (this.getOwnerUuid() != null && com.example.construction.ConstructionManager.getInstance().findNearestSessionForMinion(serverWorld, this.getBlockPos(), this.getOwnerUuid(), 128.0D, MinionRole.BUILDER).isPresent()) {
				targetAdaptiveRole = MinionRole.BUILDER;
			} else if (this.isHoldingPosition()) {
				targetAdaptiveRole = MinionRole.SENTINEL;
			} else {
				targetAdaptiveRole = MinionRole.WARRIOR;
			}
		}

		if (this.getAdaptiveRole() != targetAdaptiveRole) {
			this.setAdaptiveRole(targetAdaptiveRole);
			this.autoEquipFromInventory();
		}
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		ItemStack weapon = this.getMainHandStack();
		if (!isRangedWeapon(weapon)) {
			ItemStack offhand = this.getOffHandStack();
			if (isRangedWeapon(offhand)) {
				weapon = offhand;
			}
		}

		if (weapon.isOf(Items.TRIDENT) || weapon.getItem() instanceof TridentItem) {
			TridentEntity tridentEntity = new TridentEntity(
				this.getWorld(),
				this,
				weapon.isEmpty() ? new ItemStack(Items.TRIDENT) : weapon
			);
			if (this.isTamed()) {
				tridentEntity.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			}
			double dx = target.getX() - this.getX();
			double dy = target.getBodyY(0.3333333333333333D) - tridentEntity.getY();
			double dz = target.getZ() - this.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			tridentEntity.setVelocity(dx, dy + distance * 0.20000000298023224D, dz, 1.6F, (float) (14 - this.getWorld().getDifficulty().getId() * 4));
			this.playSound(SoundEvents.ITEM_TRIDENT_THROW.value(), 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
			this.getWorld().spawnEntity(tridentEntity);
		} else if (weapon.isOf(ModItems.FROST_GRENADE_STICK) || weapon.getItem() instanceof FrostGrenadeStickItem) {
			FrostGrenadeEntity grenade = new FrostGrenadeEntity(this.getWorld(), this);
			grenade.setItem(weapon);
			double dx = target.getX() - this.getX();
			double dy = target.getBodyY(0.5D) - grenade.getY();
			double dz = target.getZ() - this.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			grenade.setVelocity(dx, dy + dist * 0.18D, dz, 1.25F, 1.0F);
			this.playSound(SoundEvents.ENTITY_SNOWBALL_THROW, 1.0F, 0.4F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
			this.getWorld().spawnEntity(grenade);
		} else if (weapon.isOf(ModItems.TNT_STICK) || weapon.getItem() instanceof TntStickItem) {
			TntProjectileEntity tnt = new TntProjectileEntity(this.getWorld(), this);
			tnt.setItem(new ItemStack(Items.TNT));
			double dx = target.getX() - this.getX();
			double dy = target.getBodyY(0.5D) - tnt.getY();
			double dz = target.getZ() - this.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			tnt.setVelocity(dx, dy + dist * 0.18D, dz, 1.2F, 1.0F);
			this.playSound(SoundEvents.ENTITY_TNT_PRIMED, 1.0F, 1.0F);
			this.getWorld().spawnEntity(tnt);
		} else {
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
	}

	@Override
	public void setSitting(boolean sitting) {
		super.setSitting(sitting);
		this.setInSittingPose(sitting);
		this.climbingScaffolding = false;
		this.setArcaneLevitating(false);
		this.clearActiveTraversalDestination();
		this.setActivelyBuilding(false);
		if (sitting) {
			this.clearAssaultTargets();
			this.clearProcurement();
			this.setTarget(null);
		}
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		this.clearAssaultTargets();
		this.clearProcurement();
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
		BlockState footState = this.getBlockStateAtPos();
		if (!footState.isOf(Blocks.SCAFFOLDING)) {
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
	 * through the column. For ladders, vines, and other climbables, defaults to vanilla logic.
	 *
	 * @return true if the minion is actively climbing.
	 */
	@Override
	public boolean isClimbing() {
		BlockState footState = this.getBlockStateAtPos();
		if (footState.isOf(Blocks.SCAFFOLDING)) {
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
		if (this.isPhasingBlocks()) {
			this.noClip = true;
			this.fallDistance = 0.0F;
			this.setNoGravity(true);
		}

		BlockState footState = this.getBlockStateAtPos();
		boolean inScaffolding = footState.isOf(Blocks.SCAFFOLDING);
		boolean ascendingScaffolding = this.isAlive()
			&& inScaffolding
			&& this.isNavigatingUpwardInScaffolding();

		if (ascendingScaffolding) {
			this.fallDistance = 0.0F;
			Vec3d currentVelocity = this.getVelocity();
			this.setVelocity(currentVelocity.x, 0.25D, currentVelocity.z);
			this.velocityModified = true;
		}

		super.travel(movementInput);

		if (this.isPhasingBlocks()) {
			this.noClip = true;
			this.fallDistance = 0.0F;
		} else if (this.isAlive() && this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)) {
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
		if (other instanceof IronGolemEntity ironGolem) {
			LivingEntity golemTarget = ironGolem.getTarget();
			if (golemTarget == null) {
				return true;
			}
			if (golemTarget.equals(this)) {
				return false;
			}
			if (this.isTamed()) {
				LivingEntity owner = this.getOwner();
				if (owner != null) {
					if (golemTarget.equals(owner)) {
						return false;
					}
					if (golemTarget instanceof MinionEntity minionTarget && minionTarget.isOwner(owner)) {
						return false;
					}
				}
			}
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

	/**
	 * Checks if solid ceiling blocks exist directly above the minion within checkBlocks height.
	 *
	 * @param world       The server world.
	 * @param checkBlocks Number of blocks above minion head/feet to inspect.
	 * @return true if a solid ceiling block exists overhead.
	 */
	public boolean hasCeilingAboveMinion(ServerWorld world, int checkBlocks) {
		int startY = this.getBlockY() + 2;
		for (int cy = startY; cy <= startY + checkBlocks; cy++) {
			BlockPos ceilPos = new BlockPos(this.getBlockX(), cy, this.getBlockZ());
			BlockState state = world.getBlockState(ceilPos);
			if (state.isSolidBlock(world, ceilPos) || state.isFullCube(world, ceilPos)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether any part of the minion's bounding box footprint (all 4 corners and center)
	 * rests directly over or on top of solid ground, slabs, stairs, fences, or collidable blocks.
	 *
	 * @param world The world to inspect.
	 * @return true if solid or collidable ground exists directly beneath the minion.
	 */
	public boolean hasSolidGroundBeneath(World world) {
		if (this.isOnGround()) {
			return true;
		}
		Box baseBox = this.getBoundingBox().contract(0.05D).offset(0.0D, -0.25D, 0.0D);
		int minX = MathHelper.floor(baseBox.minX);
		int maxX = MathHelper.floor(baseBox.maxX);
		int minZ = MathHelper.floor(baseBox.minZ);
		int maxZ = MathHelper.floor(baseBox.maxZ);
		int checkY = MathHelper.floor(this.getY() - 0.2D);

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos pos = new BlockPos(x, checkY, z);
				BlockState state = world.getBlockState(pos);
				if (!state.isAir() && (state.isSolidBlock(world, pos) || state.isFullCube(world, pos) || !state.getCollisionShape(world, pos).isEmpty())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Automatically opens any closed door in the minion's immediate navigation path (within 1.5 blocks)
	 * so minions across all roles can seamlessly walk in and out of structures without pathing stalls.
	 *
	 * @param world The server world.
	 */
	public void autoOpenNearbyDoors(ServerWorld world) {
		BlockPos minionPos = this.getBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos checkPos = minionPos.add(dx, dy, dz);
					BlockState state = world.getBlockState(checkPos);
					if (state.getBlock() instanceof DoorBlock && !state.get(DoorBlock.OPEN)) {
						((DoorBlock) state.getBlock()).setOpen(this, world, state, checkPos, true);
						world.playSound(null, checkPos, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 1.0F);
					}
				}
			}
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		if (this.arcaneLevitating || this.isActivelyBuilding() || this.exitingBuilding
				|| (this.getWorld() != null && this.getWorld().getTime() - this.lastConstructionActivityTick <= 100L)
				|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this)) {
			return false;
		}
		return super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.arcaneLevitating && source.isOf(DamageTypes.FALL)) {
			return false;
		}
		if (source.isOf(DamageTypes.FALL)) {
			if (this.isActivelyBuilding() || this.exitingBuilding
					|| (this.getWorld() != null && this.getWorld().getTime() - this.lastConstructionActivityTick <= 100L)
					|| com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this)) {
				return false;
			}
		}
		if (this.arcaneLevitating && source.isOf(DamageTypes.IN_WALL)) {
			return false;
		}
		if (this.isTamed() && source.getAttacker() != null) {
			Entity attacker = source.getAttacker();
			LivingEntity owner = this.getOwner();
			if (owner != null && (attacker.equals(owner) || (attacker instanceof MinionEntity otherMinion && otherMinion.isOwner(owner)))) {
				return false;
			}
		}
		if (source.isOf(DamageTypes.IN_WALL)) {
			BlockState state = this.getBlockStateAtPos();
			BlockState headState = this.getWorld().getBlockState(this.getBlockPos().up());
			if (state.isOf(Blocks.SCAFFOLDING) || headState.isOf(Blocks.SCAFFOLDING)) {
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
	 * Determines whether the given item is a thrown weapon (Trident, Frost Grenade Stick, TNT Stick).
	 */
	public static boolean isThrownWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.isOf(Items.TRIDENT)
			|| stack.getItem() instanceof TridentItem
			|| stack.getItem() instanceof FrostGrenadeStickItem
			|| stack.getItem() instanceof TntStickItem;
	}

	/**
	 * Determines whether the given item is a ranged or thrown weapon (bow, crossbow, trident, grenade, or tnt stick).
	 */
	public static boolean isRangedWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW)
			|| stack.getItem() instanceof BowItem
			|| stack.getItem() instanceof CrossbowItem
			|| stack.getItem() instanceof RangedWeaponItem
			|| isThrownWeapon(stack);
	}

	/**
	 * Determines whether the given item is a frontline melee weapon (sword, axe, mace, or trident).
	 */
	public static boolean isMeleeWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.getItem() instanceof SwordItem
			|| stack.getItem() instanceof AxeItem
			|| stack.getItem() instanceof MaceItem
			|| stack.getItem() instanceof TridentItem
			|| stack.isOf(Items.TRIDENT);
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
			case WARRIOR -> isMeleeWeapon(stack) || isRangedWeapon(stack) || isThrownWeapon(stack);
			case SENTINEL -> isMeleeWeapon(stack);
			case BUILDER -> stack.getItem() instanceof MiningToolItem || isMeleeWeapon(stack);
			case AUTO -> isMeleeWeapon(stack) || isRangedWeapon(stack) || stack.getItem() instanceof MiningToolItem;
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
			case WARRIOR -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
			case SENTINEL -> isMeleeWeapon(candidate) && !isMeleeWeapon(current);
			case BUILDER -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
			case AUTO -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
		};
	}

	/**
	 * Automatically equips armor, weapons, and defensive offhand items from the minion's
	 * 9-slot storage inventory into corresponding equipment slots, strictly adhering to role restrictions:
	 * - Warriors seek frontline melee weapons (swords, axes, maces) OR ranged weapons (bows, crossbows).
	 * - Sentinels seek melee weapons in mainhand and prioritize shields in offhand.
	 * - Miners seek mining tools (pickaxes) and defense weapons.
	 * - Builders seek construction tools and defense weapons.
	 * - All roles equip available protective armor.
	 */
	public void autoEquipFromInventory() {
		if (this.getWorld().isClient()) {
			return;
		}

		MinionRole role = this.getRole() == MinionRole.AUTO ? this.getAdaptiveRole() : this.getRole();

		// 0. Active role enforcement: disarm any weapon that violates the minion's active role (Auto minions never disarm)
		if (this.getRole() != MinionRole.AUTO) {
			ItemStack heldMainhand = this.getEquippedStack(EquipmentSlot.MAINHAND);
			if (!heldMainhand.isEmpty() && !canRoleAutoEquipMainhand(role, heldMainhand)) {
				ItemStack remainder = this.inventory.addStack(heldMainhand);
				this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				if (!remainder.isEmpty()) {
					this.dropStack(remainder);
				}
				this.inventory.markDirty();
			}
		}

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
		nbt.putInt("PatrolRouteId", this.getPatrolRouteId());
		nbt.putInt("CurrentWaypointIndex", this.getCurrentWaypointIndex());
		UUID leader = this.getLeaderMinionUuid();
		if (leader != null) {
			nbt.putUuid("LeaderMinionUuid", leader);
		}
		nbt.putInt("AdaptiveRoleId", this.getAdaptiveRole().getId());
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
		if (nbt.contains("PatrolRouteId", NbtElement.INT_TYPE)) {
			this.setPatrolRouteId(nbt.getInt("PatrolRouteId"));
		}
		if (nbt.contains("CurrentWaypointIndex", NbtElement.INT_TYPE)) {
			this.setCurrentWaypointIndex(nbt.getInt("CurrentWaypointIndex"));
		}
		if (nbt.containsUuid("LeaderMinionUuid")) {
			this.setLeaderMinionUuid(nbt.getUuid("LeaderMinionUuid"));
		}
		if (nbt.contains("AdaptiveRoleId", NbtElement.INT_TYPE)) {
			this.setAdaptiveRole(MinionRole.fromId(nbt.getInt("AdaptiveRoleId")));
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

	/**
	 * Verifies whether the specified entity is the legitimate owner of this minion.
	 * In standard environments, evaluates direct UUID match via {@code super.isOwner(entity)}.
	 * In singleplayer integrated environments, automatically adopts the host player if the minion
	 * is tamed, ensuring seamless ownership persistence across dev restarts and offline UUID shifts
	 * without referencing client-only classes.
	 *
	 * @param entity The living entity attempting to exercise ownership.
	 * @return true if the entity is the owner or singleplayer host; false otherwise.
	 */
	@Override
	public boolean isOwner(LivingEntity entity) {
		if (super.isOwner(entity)) {
			return true;
		}
		if (entity instanceof PlayerEntity player && this.getWorld() instanceof ServerWorld serverWorld) {
			MinecraftServer server = serverWorld.getServer();
			if (server != null && server.isSingleplayer() && server.isHost(player.getGameProfile())) {
				if (this.isTamed()) {
					this.setOwner(player);
					return true;
				}
			}
		}
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

		// 2. Empty Hand: toggle stationed / following (yields to Command Scepter if held in off-hand)
		if (itemStack.isEmpty()) {
			if (hand == Hand.MAIN_HAND && player.getOffHandStack().getItem() instanceof com.example.item.custom.CommandScepterItem) {
				return ActionResult.PASS;
			}
			boolean currentlyStationed = this.isHoldingPosition() || this.isSitting() || this.getGuardAnchorPos() != null;
			boolean newStationed = !currentlyStationed;
			this.setSitting(newStationed);
			this.jumping = false;
			this.navigation.stop();
			this.setTarget(null);
			if (newStationed) {
				this.setSelected(false);
				this.setGuardAnchorPos(this.getBlockPos());
				this.setPatrolRouteId(-1);
				this.clearLeader();
			} else {
				this.setSelected(true);
				this.setGuardAnchorPos(null);
				this.setPatrolRouteId(-1);
				this.clearLeader();
				this.getNavigation().startMovingTo(player, 1.35D);
			}
			if (!world.isClient()) {
				String msg = newStationed ? "§e✦ Minion is now holding position (stationed).§r" : "§a✦ Minion is now selected and following you.§r";
				player.sendMessage(Text.literal(msg), true);
			}
			this.playSound(newStationed ? SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM : SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.0F);
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
		this.setArcaneLevitating(false);
		this.clearActiveTraversalDestination();
		this.setActivelyBuilding(false);
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
		this.setArcaneLevitating(false);
		this.clearActiveTraversalDestination();
		this.setActivelyBuilding(false);

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
