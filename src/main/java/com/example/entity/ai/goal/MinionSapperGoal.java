package com.example.entity.ai.goal;

import com.example.component.SquadGroup;
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
 *       generates vertical climbing column/shaft coordinates up to 6 blocks to reach elevated ledges.</li>
 *   <li><b>Combat Sapper Dynamic:</b> Minions with {@link MinionRole#BUILDER} act as dedicated squad sappers
 *       placing temporary scaffolding at zero resource cost. Other minion archetypes consume scaffolding from
 *       their 9-slot inventory, or signal nearby squad {@code BUILDER} minions within 24 blocks to rush to
 *       the obstacle.</li>
 * </ul>
 *
 * Placed scaffolding is registered with {@link TraversalScaffoldingManager} for ephemeral decay and
 * entity safety protection.
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

	private static final Map<UUID, SapperRequest> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

	private final MinionEntity minion;
	private SapperState state = SapperState.IDLE;
	private ObstacleType obstacleType = ObstacleType.NONE;

	private final List<BlockPos> plannedBlocks = new ArrayList<>();
	private BlockPos activeColumnBase = null;
	private int targetClimbTopY = -1;
	private int executionTicks = 0;
	private int checkCooldown = 0;

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
			return true;
		}

		// Non-builder without scaffolding: search for nearby allied BUILDER to signal
		MinionEntity squadBuilder = findNearbySquadBuilder(world);
		if (squadBuilder != null) {
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
		if (this.state == SapperState.CLIMBING) {
			if (this.executionTicks > 120) {
				return false;
			}
			return this.minion.getY() < (double) this.targetClimbTopY;
		}
		return false;
	}

	@Override
	public void start() {
		this.executionTicks = 0;

		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		if (this.plannedBlocks.isEmpty()) {
			return;
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
				this.minion.getNavigation().recalculatePath();
			}
		}
	}

	@Override
	public void stop() {
		this.minion.setClimbingScaffolding(false);
		this.minion.setJumping(false);
		this.state = SapperState.IDLE;
		this.obstacleType = ObstacleType.NONE;
		this.plannedBlocks.clear();
		this.activeColumnBase = null;
		this.targetClimbTopY = -1;
		this.executionTicks = 0;
		this.checkCooldown = 5;

		if (this.minion.getRole() == MinionRole.BUILDER) {
			ACTIVE_REQUESTS.remove(this.minion.getUuid());
		}
	}

	/**
	 * Deploys the computed scaffolding blocks into the world, plays placement audio,
	 * swings the minion's hand, registers blocks with {@link TraversalScaffoldingManager},
	 * and consumes scaffolding items if applicable.
	 */
	public void deployScaffoldingBlocks(ServerWorld world) {
		boolean placedAny = false;
		for (BlockPos pos : this.plannedBlocks) {
			BlockState current = world.getBlockState(pos);
			if (current.isAir() || current.isReplaceable()) {
				if (!consumeScaffoldingResource()) {
					break;
				}
				BlockState scaffoldState = Blocks.SCAFFOLDING.getDefaultState()
					.with(ScaffoldingBlock.DISTANCE, 0)
					.with(ScaffoldingBlock.BOTTOM, false);
				world.setBlockState(pos, scaffoldState, Block.NOTIFY_ALL);
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
		if (canRoleBuildZeroCost(this.minion.getRole())) {
			return true;
		}
		int slot = findScaffoldingSlot();
		if (slot != -1) {
			this.minion.getInventory().removeStack(slot, 1);
			return true;
		}
		return false;
	}

	private int findScaffoldingSlot() {
		SimpleInventory inv = this.minion.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(Items.SCAFFOLDING)) {
				return i;
			}
		}
		return -1;
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
		boolean forwardPassable = forwardState.isAir() || forwardState.isReplaceable() || forwardState.isOf(Blocks.SCAFFOLDING);

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

			BlockState stateAtBridge = world.getBlockState(bridgePos);
			if (stateAtBridge.isSolidBlock(world, bridgePos) && !stateAtBridge.isOf(Blocks.SCAFFOLDING)) {
				// Reached solid ground on the far side of the ravine!
				break;
			}

			bridgeCoords.add(bridgePos);
		}

		return bridgeCoords;
	}

	private static boolean isDropHazard(BlockState state) {
		return state.isAir() || state.isReplaceable() || state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
	}

	/**
	 * Scans 1.0–1.5 blocks ahead for vertical cliff faces rising > 1.0625 blocks above minion foot height.
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

		// Scan upward for walkable ledge elevation within MAX_CLIFF_HEIGHT
		int foundLedgeY = -1;
		for (int h = 2; h <= MAX_CLIFF_HEIGHT; h++) {
			int checkY = feetPos.getY() + h;
			BlockPos ledgeGround = new BlockPos(wallPos.getX(), checkY - 1, wallPos.getZ());
			BlockPos ledgeFeet = new BlockPos(wallPos.getX(), checkY, wallPos.getZ());
			BlockPos ledgeHead = new BlockPos(wallPos.getX(), checkY + 1, wallPos.getZ());

			BlockState groundState = world.getBlockState(ledgeGround);
			BlockState feetState = world.getBlockState(ledgeFeet);
			BlockState headState = world.getBlockState(ledgeHead);

			if (groundState.isSolidBlock(world, ledgeGround)
				&& (feetState.isAir() || feetState.isReplaceable() || feetState.isOf(Blocks.SCAFFOLDING))
				&& (headState.isAir() || headState.isReplaceable() || headState.isOf(Blocks.SCAFFOLDING))) {
				foundLedgeY = checkY;
				break;
			}
		}

		if (foundLedgeY == -1) {
			return Collections.emptyList();
		}

		// Place column immediately adjacent to the cliff wall in the passable air space
		BlockPos columnBase = feetPos;
		BlockState columnFeetState = world.getBlockState(columnBase);
		if (!columnFeetState.isAir() && !columnFeetState.isReplaceable() && !columnFeetState.isOf(Blocks.SCAFFOLDING)) {
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
