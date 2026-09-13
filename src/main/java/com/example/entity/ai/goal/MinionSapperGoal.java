package com.example.entity.ai.goal;

import com.example.block.ModBlocks;
import com.example.component.SquadGroup;
import com.example.construction.ConstructionManager;
import com.example.construction.TraversalScaffoldingManager;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Combat sapper AI goal for {@link MinionEntity} thralls.
 *
 * <p>Enables autonomous terrain traversal when following commanders, marching towards tactical waypoints,
 * or charging combat targets across rugged terrain:
 * <ul>
 *   <li><b>Chasm & Ravine Bridging:</b> Detects sudden terrain drops (>= 2 blocks deep or liquid hazards)
 *       1.2–2.0 blocks ahead and computes bridging scaffolding coordinates up to 6 blocks across the gap.</li>
 *   <li><b>Cliff & Mountain Ascent:</b> Detects solid vertical obstacles rising > 1.0625 blocks ahead and
 *       generates vertical climbing column/shaft coordinates up to 6 blocks to reach elevated ledges,
 *       enforcing strict overhead ceiling and headroom clearance checks so minions never bump into ceilings.</li>
 *   <li><b>Climbing Safety & Stall Sensors:</b> Features climbing stall sensors (aborts if vertical progress halts
 *       for &gt; 20 ticks), safety timeouts (max 120 climb ticks), and ceiling collision detection.</li>
 *   <li><b>Multi-Minion Coordination:</b> Claims and respects vertical climbing column reservations via
 *       {@link TraversalScaffoldingManager} to avoid collision crowding and ensure column spacing.</li>
 *   <li><b>Combat Sapper Dynamic:</b> Minions with {@link MinionRole#BUILDER} act as dedicated squad sappers
 *       placing temporary scaffolding/construction blocks at zero resource cost. Other minion archetypes consume
 *       scaffolding/construction items from their 9-slot inventory, or signal nearby squad {@code BUILDER} minions
 *       within 24 blocks to rush to the obstacle.</li>
 * </ul>
 *
 * Placed scaffolding is registered with {@link TraversalScaffoldingManager} for ephemeral decay and
 * entity safety protection. Uses {@link ModBlocks#CONSTRUCTION_BLOCK} or {@link Blocks#SCAFFOLDING}.
 */
public class MinionSapperGoal extends Goal {

	public enum ObstacleType {
		NONE,
		RAVINE_GAP,
		CLIFF_ASCENT
	}

	public enum SapperState {
		IDLE,
		BRIDGING,
		CLIMBING,
		SIGNALING
	}

	public static final int MAX_BRIDGE_SPAN = 6;
	public static final int MAX_CLIFF_HEIGHT = 6;
	public static final double SQUAD_SIGNAL_RADIUS = 24.0D;
	public static final long SIGNAL_TIMEOUT_TICKS = 200L;
	public static final double STEP_HEIGHT = 1.0625D;

	public static final int MAX_CLIMB_TICKS = 120; // 6 seconds max climbing safety timeout
	public static final int STALL_THRESHOLD_TICKS = 20; // 1 second without upward movement aborts climb
	public static final double MIN_VERTICAL_PROGRESS_PER_TICK = 0.02D;

	private static final Map<UUID, SapperRequest> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

	private final MinionEntity minion;
	private SapperState state = SapperState.IDLE;
	private ObstacleType obstacleType = ObstacleType.NONE;

	private final List<BlockPos> plannedBlocks = new ArrayList<>();
	private BlockPos activeColumnBase = null;
	private int targetClimbTopY = -1;
	private int executionTicks = 0;
	private int checkCooldown = 0;

	// Climbing stall & safety sensor state
	private double lastClimbY = Double.NEGATIVE_INFINITY;
	private int stallTicks = 0;

	public MinionSapperGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}

		// Suppress sapper behavior if this minion is engaged in construction or near an active construction session
		if (isSuppressedByConstruction()) {
			return false;
		}

		if (this.checkCooldown > 0) {
			this.checkCooldown--;
			return false;
		}

		// 1. Check if this minion has been signaled as a squad sapper to assist an ally
		if (this.minion.getRole() == MinionRole.BUILDER) {
			SapperRequest incoming = ACTIVE_REQUESTS.get(this.minion.getUuid());
			if (incoming != null) {
				if (serverWorld.getTime() - incoming.requestTick() > SIGNAL_TIMEOUT_TICKS) {
					ACTIVE_REQUESTS.remove(this.minion.getUuid());
				} else if (incoming.requester().isAlive() && !incoming.requester().isSitting()) {
					this.obstacleType = incoming.obstacleType();
					this.plannedBlocks.clear();
					this.plannedBlocks.addAll(incoming.plannedBlocks());
					this.activeColumnBase = incoming.columnBase();
					this.targetClimbTopY = incoming.targetLedgeY();
					return !this.plannedBlocks.isEmpty();
				}
			}
		}

		// 2. Resolve active destination (combat target, waypoint anchor, or owner)
		Vec3d destination = getActiveDestination();
		if (destination == null) {
			return false;
		}

		double distSq = this.minion.squaredDistanceTo(destination);
		if (distSq < 2.25D) {
			return false;
		}

		// 3. Calculate horizontal heading towards destination
		Vec3d minionPos = this.minion.getPos();
		double dx = destination.x - minionPos.x;
		double dz = destination.z - minionPos.z;
		double horizDist = Math.hypot(dx, dz);
		if (horizDist < 0.5D) {
			return false;
		}

		double dirX = dx / horizDist;
		double dirZ = dz / horizDist;

		BlockPos feetPos = this.minion.getBlockPos();

		// 4. Scan for Cliff / Mountain Ascent
		List<BlockPos> cliffColumn = detectCliffAscent(serverWorld, feetPos, dirX, dirZ);
		if (!cliffColumn.isEmpty()) {
			return evaluateSapperAuthorization(serverWorld, ObstacleType.CLIFF_ASCENT, cliffColumn, cliffColumn.get(0), cliffColumn.get(cliffColumn.size() - 1).getY() + 1, destination);
		}

		// 5. Scan for Ravine / Chasm Gap
		List<BlockPos> ravineBridge = detectRavineGap(serverWorld, feetPos, dirX, dirZ);
		if (!ravineBridge.isEmpty()) {
			return evaluateSapperAuthorization(serverWorld, ObstacleType.RAVINE_GAP, ravineBridge, null, -1, destination);
		}

		this.checkCooldown = 4;
		return false;
	}

	private boolean evaluateSapperAuthorization(ServerWorld world, ObstacleType type, List<BlockPos> blocks, BlockPos colBase, int targetLedgeY, Vec3d destination) {
		this.obstacleType = type;
		this.plannedBlocks.clear();
		this.plannedBlocks.addAll(blocks);
		this.activeColumnBase = colBase;
		this.targetClimbTopY = targetLedgeY;

		// Builder role or minions carrying scaffolding can build immediately
		if (canMinionBuildScaffolding()) {
			// If ascending a cliff, verify and claim the climbing column reservation
			if (type == ObstacleType.CLIFF_ASCENT && colBase != null) {
				if (!TraversalScaffoldingManager.getInstance().claimClimbingColumn(world, colBase, this.minion.getUuid())) {
					// Column claimed by another minion; wait or back off
					this.checkCooldown = 10;
					return false;
				}
			}
			return true;
		}

		// Non-builder without scaffolding: search for nearby allied BUILDER to signal
		MinionEntity squadBuilder = findNearbySquadBuilder(world);
		if (squadBuilder != null) {
			// Also ensure column is not claimed by another minion if cliff ascent
			if (type == ObstacleType.CLIFF_ASCENT && colBase != null) {
				if (!TraversalScaffoldingManager.getInstance().isColumnAvailable(world, colBase, squadBuilder.getUuid())
					&& !TraversalScaffoldingManager.getInstance().isColumnAvailable(world, colBase, this.minion.getUuid())) {
					this.checkCooldown = 10;
					return false;
				}
			}

			ACTIVE_REQUESTS.put(squadBuilder.getUuid(), new SapperRequest(
				this.minion,
				this.minion.getBlockPos(),
				destination,
				type,
				new ArrayList<>(blocks),
				colBase,
				targetLedgeY,
				world.getTime()
			));
			this.state = SapperState.SIGNALING;
			world.playSound(null, this.minion.getX(), this.minion.getY(), this.minion.getZ(),
				SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.8F, 1.2F);
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.minion.getX(), this.minion.getY() + 1.2D, this.minion.getZ(),
				5, 0.2D, 0.2D, 0.2D, 0.05D);
			this.checkCooldown = 20;
			return false;
		}

		this.checkCooldown = 15;
		return false;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (isSuppressedByConstruction()) {
			return false;
		}
		if (this.state == SapperState.CLIMBING) {
			// Hard safety timeout
			if (this.executionTicks > MAX_CLIMB_TICKS) {
				return false;
			}
			// Vertical stall sensor timeout
			if (this.stallTicks > STALL_THRESHOLD_TICKS) {
				return false;
			}
			return this.minion.getY() < (double) this.targetClimbTopY;
		}
		return false;
	}

	@Override
	public void start() {
		this.executionTicks = 0;
		this.stallTicks = 0;
		this.lastClimbY = this.minion.getY();

		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		if (this.plannedBlocks.isEmpty()) {
			return;
		}

		// Ensure column reservation is held if ascending
		if (this.obstacleType == ObstacleType.CLIFF_ASCENT && this.activeColumnBase != null) {
			TraversalScaffoldingManager.getInstance().claimClimbingColumn(serverWorld, this.activeColumnBase, this.minion.getUuid());
		}

		// Consume resource and deploy scaffolding blocks
		deployScaffoldingBlocks(serverWorld);

		if (this.obstacleType == ObstacleType.CLIFF_ASCENT && this.targetClimbTopY > this.minion.getBlockY()) {
			this.state = SapperState.CLIMBING;
			this.minion.setClimbingScaffolding(true);
			this.minion.getNavigation().stop();
		} else {
			this.state = SapperState.IDLE;
			this.minion.getNavigation().recalculatePath();
		}
	}

	@Override
	public void tick() {
		this.executionTicks++;

		if (this.state == SapperState.CLIMBING && this.activeColumnBase != null) {
			this.minion.setClimbingScaffolding(true);
			this.minion.fallDistance = 0.0F;

			// Stall detection: measure vertical progress
			double currentY = this.minion.getY();
			if (currentY - this.lastClimbY < MIN_VERTICAL_PROGRESS_PER_TICK) {
				this.stallTicks++;
			} else {
				this.stallTicks = 0;
			}
			this.lastClimbY = currentY;

			// Overhead ceiling collision check directly above minion head
			if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
				BlockPos headPos = this.minion.getBlockPos().up(2);
				BlockState headState = serverWorld.getBlockState(headPos);
				if (headState.isSolidBlock(serverWorld, headPos) && !isPassableScaffolding(headState)) {
					// Overhead ceiling collision detected; abort climb immediately to avoid hammering head
					this.stop();
					return;
				}
			}

			// If stalled for too long, abort climb
			if (this.stallTicks > STALL_THRESHOLD_TICKS) {
				this.stop();
				return;
			}

			double colCenterX = this.activeColumnBase.getX() + 0.5D;
			double colCenterZ = this.activeColumnBase.getZ() + 0.5D;
			double alignX = colCenterX - this.minion.getX();
			double alignZ = colCenterZ - this.minion.getZ();

			if (this.minion.getY() < (double) this.targetClimbTopY) {
				this.minion.setVelocity(alignX * 0.25D, 0.24D, alignZ * 0.25D);
				this.minion.velocityModified = true;
				this.minion.setJumping(true);

				if (this.executionTicks % 6 == 0 && this.minion.getWorld() instanceof ServerWorld serverWorld) {
					serverWorld.playSound(null, this.minion.getX(), this.minion.getY(), this.minion.getZ(),
						SoundEvents.BLOCK_SCAFFOLDING_STEP, SoundCategory.BLOCKS, 0.7F, 1.2F);
				}
			} else {
				this.minion.setPosition(colCenterX, (double) this.targetClimbTopY, colCenterZ);
				this.minion.setVelocity(0.0D, 0.0D, 0.0D);
				this.minion.velocityModified = true;
				this.minion.setJumping(false);
				this.minion.setClimbingScaffolding(false);
				this.state = SapperState.IDLE;
				if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
					TraversalScaffoldingManager.getInstance().releaseClimbingColumn(serverWorld, this.activeColumnBase, this.minion.getUuid());
				}
				this.minion.getNavigation().recalculatePath();
			}
		}
	}

	@Override
	public void stop() {
		if (this.activeColumnBase != null && this.minion.getWorld() instanceof ServerWorld serverWorld) {
			TraversalScaffoldingManager.getInstance().releaseClimbingColumn(serverWorld, this.activeColumnBase, this.minion.getUuid());
		}

		this.minion.setClimbingScaffolding(false);
		this.minion.setJumping(false);
		this.state = SapperState.IDLE;
		this.obstacleType = ObstacleType.NONE;
		this.plannedBlocks.clear();
		this.activeColumnBase = null;
		this.targetClimbTopY = -1;
		this.executionTicks = 0;
		this.stallTicks = 0;
		this.lastClimbY = Double.NEGATIVE_INFINITY;
		this.checkCooldown = 5;

		if (this.minion.getRole() == MinionRole.BUILDER) {
			ACTIVE_REQUESTS.remove(this.minion.getUuid());
		}
	}

	/**
	 * Deploys the computed scaffolding blocks into the world, plays placement audio,
	 * swings the minion's hand, registers blocks with {@link TraversalScaffoldingManager},
	 * and consumes scaffolding items if applicable. Prioritizes {@link ModBlocks#CONSTRUCTION_BLOCK}.
	 */
	public void deployScaffoldingBlocks(ServerWorld world) {
		boolean placedAny = false;
		for (BlockPos pos : this.plannedBlocks) {
			BlockState current = world.getBlockState(pos);
			if (current.isAir() || current.isReplaceable()) {
				ItemStack consumedStack = consumeScaffoldingResourceStack();
				if (consumedStack == null && !canRoleBuildZeroCost(this.minion.getRole())) {
					break;
				}

				BlockState placedState;
				if (consumedStack != null && consumedStack.isOf(ModBlocks.CONSTRUCTION_BLOCK.asItem())) {
					placedState = ModBlocks.CONSTRUCTION_BLOCK.getDefaultState();
				} else if (consumedStack != null && consumedStack.isOf(Items.SCAFFOLDING)) {
					placedState = Blocks.SCAFFOLDING.getDefaultState()
						.with(ScaffoldingBlock.DISTANCE, 0)
						.with(ScaffoldingBlock.BOTTOM, false);
				} else {
					// Zero-cost builder placement defaults to ModBlocks.CONSTRUCTION_BLOCK for superior solidity & non-collapsing properties
					placedState = ModBlocks.CONSTRUCTION_BLOCK.getDefaultState();
				}

				world.setBlockState(pos, placedState, Block.NOTIFY_ALL);
				TraversalScaffoldingManager.getInstance().registerScaffolding(world, pos);
				placedAny = true;
			}
		}

		if (placedAny) {
			this.minion.swingHand(Hand.MAIN_HAND);
			BlockPos soundPos = this.plannedBlocks.get(0);
			world.playSound(null, soundPos.getX() + 0.5D, soundPos.getY() + 0.5D, soundPos.getZ() + 0.5D,
				SoundEvents.BLOCK_SCAFFOLDING_PLACE, SoundCategory.BLOCKS, 0.9F, 1.1F);
			world.spawnParticles(ParticleTypes.CLOUD, soundPos.getX() + 0.5D, soundPos.getY() + 0.5D, soundPos.getZ() + 0.5D,
				4, 0.15D, 0.15D, 0.15D, 0.02D);
		}
	}

	/**
	 * Checks whether this minion is authorized and equipped to place traversal scaffolding.
	 */
	public boolean canMinionBuildScaffolding() {
		if (canRoleBuildZeroCost(this.minion.getRole())) {
			return true;
		}
		return findScaffoldingSlot() != -1;
	}

	/**
	 * Builder archetype thralls act as combat sappers with free zero-cost temporary scaffolding.
	 */
	public static boolean canRoleBuildZeroCost(MinionRole role) {
		return role == MinionRole.BUILDER;
	}

	/**
	 * Consumes a scaffolding item from the minion's inventory unless operating under zero-cost builder archetype.
	 */
	public boolean consumeScaffoldingResource() {
		return consumeScaffoldingResourceStack() != null || canRoleBuildZeroCost(this.minion.getRole());
	}

	/**
	 * Consumes and returns a scaffolding or construction block stack from the minion's inventory.
	 * Returns null if the minion has zero-cost builder archetype or no materials.
	 */
	public ItemStack consumeScaffoldingResourceStack() {
		if (canRoleBuildZeroCost(this.minion.getRole())) {
			return null;
		}
		int slot = findScaffoldingSlot();
		if (slot != -1) {
			return this.minion.getInventory().removeStack(slot, 1);
		}
		return null;
	}

	private int findScaffoldingSlot() {
		SimpleInventory inv = this.minion.getInventory();
		// Check for ModBlocks.CONSTRUCTION_BLOCK first
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(ModBlocks.CONSTRUCTION_BLOCK.asItem())) {
				return i;
			}
		}
		// Fallback to vanilla SCAFFOLDING
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(Items.SCAFFOLDING)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Checks whether this minion's sapper behavior should be suppressed because the minion
	 * is a BUILDER or MINER currently engaged in or near an active construction session.
	 * Prevents minions from misidentifying structure walls as natural cliffs and deploying
	 * erratic traversal bridges instead of executing their planned construction/dismantle tasks.
	 *
	 * @return True if sapper behavior should be suppressed in favor of construction AI.
	 */
	public boolean isSuppressedByConstruction() {
		MinionRole role = this.minion.getRole();
		if (role == MinionRole.BUILDER || role == MinionRole.MINER) {
			return ConstructionManager.getInstance().isMinionEngagedInConstruction(this.minion);
		}
		return false;
	}

	/**
	 * Searches for an allied minion with the {@link MinionRole#BUILDER} role within 24 blocks.
	 */
	public MinionEntity findNearbySquadBuilder(ServerWorld world) {
		LivingEntity owner = this.minion.getOwner();
		if (owner == null) {
			return null;
		}
		List<MinionEntity> candidates = world.getEntitiesByClass(
			MinionEntity.class,
			this.minion.getBoundingBox().expand(SQUAD_SIGNAL_RADIUS),
			m -> m != this.minion && m.isAlive() && m.isTamed() && !m.isSitting()
				&& m.isOwner(owner)
				&& m.getRole() == MinionRole.BUILDER
				&& !ConstructionManager.getInstance().isMinionEngagedInConstruction(m)
				&& (this.minion.getSquad() == SquadGroup.ALL || m.getSquad() == SquadGroup.ALL || m.getSquad() == this.minion.getSquad())
		);
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	/**
	 * Resolves the minion's active directional target: combat target, ground waypoint anchor, or owner.
	 */
	public Vec3d getActiveDestination() {
		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			return target.getPos();
		}
		BlockPos anchor = this.minion.getGuardAnchorPos();
		if (anchor != null) {
			return new Vec3d(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
		}
		LivingEntity owner = this.minion.getOwner();
		if (owner != null && !this.minion.isSitting()) {
			return owner.getPos();
		}
		return null;
	}

	/**
	 * Scans 1.2–2.0 blocks ahead for steep chasms, ravines, or deep liquid hazards (drop >= 2 blocks).
	 *
	 * @return List of block positions across the chasm where bridge scaffolding should be placed.
	 */
	public static List<BlockPos> detectRavineGap(ServerWorld world, BlockPos feetPos, double dirX, double dirZ) {
		BlockPos forwardPos = BlockPos.ofFloored(
			feetPos.getX() + 0.5D + dirX * 1.5D,
			feetPos.getY(),
			feetPos.getZ() + 0.5D + dirZ * 1.5D
		);

		if (forwardPos.equals(feetPos)) {
			Direction primaryDir = Direction.getFacing(dirX, 0.0D, dirZ);
			forwardPos = feetPos.offset(primaryDir);
		}

		BlockState forwardState = world.getBlockState(forwardPos);
		boolean forwardPassable = forwardState.isAir() || forwardState.isReplaceable() || isPassableScaffolding(forwardState);

		if (!forwardPassable) {
			return Collections.emptyList();
		}

		BlockState down1 = world.getBlockState(forwardPos.down());
		BlockState down2 = world.getBlockState(forwardPos.down(2));

		boolean isChasmDrop = isDropHazard(down1) && isDropHazard(down2);
		if (!isChasmDrop) {
			return Collections.emptyList();
		}

		// Calculate horizontal bridge coordinates across the chasm up to MAX_BRIDGE_SPAN blocks
		List<BlockPos> bridgeCoords = new ArrayList<>();
		for (int step = 1; step <= MAX_BRIDGE_SPAN; step++) {
			BlockPos bridgePos = BlockPos.ofFloored(
				feetPos.getX() + 0.5D + dirX * step,
				feetPos.getY(),
				feetPos.getZ() + 0.5D + dirZ * step
			);

			if (bridgeCoords.contains(bridgePos)) {
				continue;
			}

			// Check headroom clearance for the minion above the bridge path (bridgePos.up(1) and bridgePos.up(2))
			BlockPos bridgeHead1 = bridgePos.up(1);
			BlockPos bridgeHead2 = bridgePos.up(2);
			BlockState head1State = world.getBlockState(bridgeHead1);
			BlockState head2State = world.getBlockState(bridgeHead2);
			if (head1State.isSolidBlock(world, bridgeHead1) || head2State.isSolidBlock(world, bridgeHead2)) {
				// Overhead ceiling obstructs this bridge path
				break;
			}

			BlockState stateAtBridge = world.getBlockState(bridgePos);
			if (stateAtBridge.isSolidBlock(world, bridgePos) && !isPassableScaffolding(stateAtBridge)) {
				// Reached solid ground on the far side of the ravine!
				break;
			}

			bridgeCoords.add(bridgePos);
		}

		return bridgeCoords;
	}

	public static boolean isPassableScaffolding(BlockState state) {
		return state.isOf(Blocks.SCAFFOLDING) || state.isOf(ModBlocks.CONSTRUCTION_BLOCK);
	}

	private static boolean isDropHazard(BlockState state) {
		return state.isAir() || state.isReplaceable() || state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
	}

	/**
	 * Scans 1.0–1.5 blocks ahead for vertical cliff faces rising > 1.0625 blocks above minion foot height.
	 * Enforces strict overhead ceiling and headroom clearance checks for both the climbing shaft and the landing ledge.
	 *
	 * @return List of block positions forming a vertical climbing column, or empty list if no valid ledge.
	 */
	public static List<BlockPos> detectCliffAscent(ServerWorld world, BlockPos feetPos, double dirX, double dirZ) {
		BlockPos wallPos = BlockPos.ofFloored(
			feetPos.getX() + 0.5D + dirX * 1.2D,
			feetPos.getY(),
			feetPos.getZ() + 0.5D + dirZ * 1.2D
		);

		if (wallPos.equals(feetPos)) {
			Direction primaryDir = Direction.getFacing(dirX, 0.0D, dirZ);
			wallPos = feetPos.offset(primaryDir);
		}

		BlockState wallFeetState = world.getBlockState(wallPos);
		BlockState wallEyeState = world.getBlockState(wallPos.up());

		boolean isSolidObstacle = wallEyeState.isSolidBlock(world, wallPos.up()) || wallFeetState.isSolidBlock(world, wallPos);
		if (!isSolidObstacle) {
			return Collections.emptyList();
		}

		// Check if the climbing column location at feetPos is unobstructed by any existing non-scaffold solid block
		BlockPos columnBase = feetPos;
		BlockState columnFeetState = world.getBlockState(columnBase);
		if (!columnFeetState.isAir() && !columnFeetState.isReplaceable() && !isPassableScaffolding(columnFeetState)) {
			return Collections.emptyList();
		}

		// Scan upward for walkable ledge elevation within MAX_CLIFF_HEIGHT
		int foundLedgeY = -1;
		for (int h = 2; h <= MAX_CLIFF_HEIGHT; h++) {
			int checkY = feetPos.getY() + h;

			// Verify the climbing column at this elevation is passable
			BlockPos shaftPos = new BlockPos(columnBase.getX(), checkY - 1, columnBase.getZ());
			BlockState shaftState = world.getBlockState(shaftPos);
			if (shaftState.isSolidBlock(world, shaftPos) && !isPassableScaffolding(shaftState)) {
				// Solid overhead ceiling block in the climbing shaft! Cannot climb higher
				break;
			}

			BlockPos ledgeGround = new BlockPos(wallPos.getX(), checkY - 1, wallPos.getZ());
			BlockPos ledgeFeet = new BlockPos(wallPos.getX(), checkY, wallPos.getZ());
			BlockPos ledgeHead = new BlockPos(wallPos.getX(), checkY + 1, wallPos.getZ());

			BlockState groundState = world.getBlockState(ledgeGround);
			BlockState feetState = world.getBlockState(ledgeFeet);
			BlockState headState = world.getBlockState(ledgeHead);

			if (groundState.isSolidBlock(world, ledgeGround)
				&& (feetState.isAir() || feetState.isReplaceable() || isPassableScaffolding(feetState))
				&& (headState.isAir() || headState.isReplaceable() || isPassableScaffolding(headState))) {
				
				// Verify overhead headroom clearance above the top of the shaft (checkY and checkY + 1 above columnBase)
				BlockPos shaftHead1 = new BlockPos(columnBase.getX(), checkY, columnBase.getZ());
				BlockPos shaftHead2 = new BlockPos(columnBase.getX(), checkY + 1, columnBase.getZ());
				BlockState sh1 = world.getBlockState(shaftHead1);
				BlockState sh2 = world.getBlockState(shaftHead2);

				// Must have at least 2 blocks headroom above the top scaffold to emerge onto the ledge
				if ((sh1.isAir() || sh1.isReplaceable() || isPassableScaffolding(sh1))
					&& (sh2.isAir() || sh2.isReplaceable() || isPassableScaffolding(sh2))) {
					foundLedgeY = checkY;
					break;
				}
			}
		}

		if (foundLedgeY == -1) {
			return Collections.emptyList();
		}

		return calculateCliffColumnCoordinates(columnBase, feetPos.getY(), foundLedgeY);
	}

	/**
	 * Computes continuous horizontal bridge coordinates along a 2D heading vector.
	 *
	 * @param originFeet The starting foot coordinates.
	 * @param dirX       The horizontal X component of heading direction.
	 * @param dirZ       The horizontal Z component of heading direction.
	 * @param maxSpan    The maximum span distance in blocks.
	 * @return List of block coordinates along the horizontal bridge.
	 */
	public static List<BlockPos> calculateRavineBridgeCoordinates(BlockPos originFeet, double dirX, double dirZ, int maxSpan) {
		double len = Math.hypot(dirX, dirZ);
		if (len < 1e-5) {
			return Collections.emptyList();
		}
		double ndx = dirX / len;
		double ndz = dirZ / len;

		List<BlockPos> coordinates = new ArrayList<>();
		for (int step = 1; step <= maxSpan; step++) {
			int bx = (int) Math.floor(originFeet.getX() + 0.5D + ndx * step);
			int by = originFeet.getY();
			int bz = (int) Math.floor(originFeet.getZ() + 0.5D + ndz * step);
			BlockPos pos = new BlockPos(bx, by, bz);
			if (!coordinates.contains(pos)) {
				coordinates.add(pos);
			}
		}
		return Collections.unmodifiableList(coordinates);
	}

	/**
	 * Computes vertical column coordinates for scaffolding from starting ground height up to the target ledge.
	 *
	 * @param columnBase   The base X/Z location of the column.
	 * @param startY       The bottom elevation.
	 * @param targetLedgeY The elevation of the destination ledge.
	 * @return List of block coordinates from startY up to targetLedgeY - 1.
	 */
	public static List<BlockPos> calculateCliffColumnCoordinates(BlockPos columnBase, int startY, int targetLedgeY) {
		List<BlockPos> coordinates = new ArrayList<>();
		for (int y = startY; y < targetLedgeY; y++) {
			coordinates.add(new BlockPos(columnBase.getX(), y, columnBase.getZ()));
		}
		return Collections.unmodifiableList(coordinates);
	}

	public int getExecutionTicks() {
		return this.executionTicks;
	}

	public int getStallTicks() {
		return this.stallTicks;
	}

	public double getLastClimbY() {
		return this.lastClimbY;
	}

	// State accessors for testing and inspection
	public SapperState getState() {
		return this.state;
	}

	public ObstacleType getObstacleType() {
		return this.obstacleType;
	}

	public List<BlockPos> getPlannedBlocks() {
		return Collections.unmodifiableList(this.plannedBlocks);
	}

	public BlockPos getActiveColumnBase() {
		return this.activeColumnBase;
	}

	public int getTargetClimbTopY() {
		return this.targetClimbTopY;
	}

	public static Map<UUID, SapperRequest> getActiveRequests() {
		return ACTIVE_REQUESTS;
	}

	/**
	 * Record representing an active sapper assistance request dispatched by a minion facing terrain obstacles.
	 */
	public record SapperRequest(
		MinionEntity requester,
		BlockPos obstaclePos,
		Vec3d destination,
		ObstacleType obstacleType,
		List<BlockPos> plannedBlocks,
		BlockPos columnBase,
		int targetLedgeY,
		long requestTick
	) {}
}
