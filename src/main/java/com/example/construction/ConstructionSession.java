package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.entity.custom.MinionEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Represents an active multiblock construction session coordinated by the player.
 * Manages topological task distribution to minion thralls, dependency validation,
 * construction lifecycle status, and completion state.
 */
public class ConstructionSession {

	/**
	 * State of the construction session.
	 */
	public enum SessionStatus {
		ACTIVE,
		PAUSED,
		COMPLETED,
		CANCELLED
	}

	/**
	 * Operational mode of the construction session: erecting a structure or tearing it down.
	 */
	public enum SessionMode {
		BUILD,
		DISMANTLE
	}

	private final UUID id;
	private final UUID ownerUuid;
	private final RegistryKey<World> dimension;
	private final BlockPos anchorPos;
	private final StructureBlueprint blueprint;
	private final boolean creative;
	private final SessionMode mode;
	private final List<ConstructionTask> tasks;
	private final BlockBox worldBoundingBox;

	private SessionStatus status = SessionStatus.ACTIVE;
	private int completedCount = 0;
	private final long createdTick;
	private long lastActivityTick;
	private final Set<BlockPos> temporaryScaffolding = ConcurrentHashMap.newKeySet();
	private final Map<BlockPos, UUID> claimedScaffoldColumns = new ConcurrentHashMap<>();

	/**
	 * Creates a new ConstructionSession anchored at the specified world coordinate in default BUILD mode.
	 *
	 * @param ownerUuid   UUID of the player who initiated construction.
	 * @param dimension   World dimension key where construction is located.
	 * @param anchorPos   The world origin block position for this structure.
	 * @param blueprint   The structure blueprint to construct.
	 * @param creative    True if free instant placement (creative mode), false for survival inventory constraints.
	 * @param currentTick Current server tick count at session creation.
	 */
	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick
	) {
		this(ownerUuid, dimension, anchorPos, blueprint, creative, currentTick, SessionMode.BUILD);
	}

	/**
	 * Creates a new ConstructionSession anchored at the specified world coordinate with an explicit session mode.
	 *
	 * @param ownerUuid   UUID of the player who initiated construction.
	 * @param dimension   World dimension key where construction is located.
	 * @param anchorPos   The world origin block position for this structure.
	 * @param blueprint   The structure blueprint to construct or dismantle.
	 * @param creative    True if free instant placement/breaking, false for survival inventory constraints.
	 * @param currentTick Current server tick count at session creation.
	 * @param mode        The operational mode: BUILD (bottom-up) or DISMANTLE (top-down reverse topological).
	 */
	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick,
		SessionMode mode
	) {
		this.id = UUID.randomUUID();
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
		this.dimension = Objects.requireNonNull(dimension, "dimension cannot be null");
		this.anchorPos = Objects.requireNonNull(anchorPos, "anchorPos cannot be null").toImmutable();
		this.blueprint = Objects.requireNonNull(blueprint, "blueprint cannot be null");
		this.creative = creative;
		this.createdTick = currentTick;
		this.lastActivityTick = currentTick;
		this.mode = mode != null ? mode : SessionMode.BUILD;

		// Initialize construction tasks. In DISMANTLE mode, tasks execute in reverse topological order (top-down, roof-to-foundation).
		List<BlueprintBlock> blueprintBlocks = new ArrayList<>(blueprint.getBlocks());
		if (this.mode == SessionMode.DISMANTLE) {
			Collections.reverse(blueprintBlocks);
		}

		List<ConstructionTask> taskList = new ArrayList<>(blueprintBlocks.size());
		for (int i = 0; i < blueprintBlocks.size(); i++) {
			taskList.add(new ConstructionTask(i, blueprintBlocks.get(i), this.anchorPos));
		}
		this.tasks = Collections.unmodifiableList(taskList);

		// Calculate world-space bounding box
		BlockBox localBox = blueprint.getBoundingBox();
		this.worldBoundingBox = new BlockBox(
			this.anchorPos.getX() + localBox.getMinX(),
			this.anchorPos.getY() + localBox.getMinY(),
			this.anchorPos.getZ() + localBox.getMinZ(),
			this.anchorPos.getX() + localBox.getMaxX(),
			this.anchorPos.getY() + localBox.getMaxY(),
			this.anchorPos.getZ() + localBox.getMaxZ()
		);
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public RegistryKey<World> getDimension() {
		return this.dimension;
	}

	public BlockPos getAnchorPos() {
		return this.anchorPos;
	}

	public StructureBlueprint getBlueprint() {
		return this.blueprint;
	}

	public boolean isCreative() {
		return this.creative;
	}

	public SessionMode getMode() {
		return this.mode;
	}

	public boolean isDismantle() {
		return this.mode == SessionMode.DISMANTLE;
	}

	public boolean isBuild() {
		return this.mode == SessionMode.BUILD;
	}

	public SessionStatus getStatus() {
		return this.status;
	}

	public boolean isActive() {
		return this.status == SessionStatus.ACTIVE;
	}

	public boolean isCompleted() {
		return this.status == SessionStatus.COMPLETED;
	}

	public int getTotalBlocks() {
		return this.tasks.size();
	}

	public int getCompletedBlocks() {
		return this.completedCount;
	}

	public float getProgress() {
		if (this.tasks.isEmpty()) {
			return 1.0F;
		}
		return (float) this.completedCount / (float) this.tasks.size();
	}

	public int getProgressPercent() {
		return Math.round(getProgress() * 100.0F);
	}

	public List<ConstructionTask> getTasks() {
		return this.tasks;
	}

	public BlockBox getWorldBoundingBox() {
		return this.worldBoundingBox;
	}

	public long getCreatedTick() {
		return this.createdTick;
	}

	public long getLastActivityTick() {
		return this.lastActivityTick;
	}

	/**
	 * Attempts to claim the next available task in topological order for a minion worker.
	 *
	 * @param minionUuid  UUID of the minion requesting work.
	 * @param currentTick Current server world tick.
	 * @param world       The server world instance for structural support validation.
	 * @return The claimed ConstructionTask, or null if no tasks are currently ready or available.
	 */
	public synchronized ConstructionTask claimNextTask(UUID minionUuid, long currentTick, World world) {
		if (this.status != SessionStatus.ACTIVE) {
			return null;
		}

		// First check if this minion already has a claimed task in this session
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && Objects.equals(task.getClaimedBy(), minionUuid)) {
				return task;
			}
		}

		// Find the next ready pending task in topological sequence
		for (ConstructionTask task : this.tasks) {
			if (task.isPending() && isTaskReady(task, world)) {
				if (task.claim(minionUuid, currentTick)) {
					this.lastActivityTick = currentTick;
					return task;
				}
			}
		}

		return null;
	}

	/**
	 * Determines whether a construction or deconstruction task's physical prerequisites are satisfied.
	 *
	 * @param task  The task to evaluate.
	 * @param world The server world to inspect.
	 * @return True if the block can be physically operated on now.
	 */
	public boolean isTaskReady(ConstructionTask task, World world) {
		if (this.mode == SessionMode.DISMANTLE) {
			return isDismantleTaskReady(task, world);
		}
		return isBuildTaskReady(task, world);
	}

	/**
	 * Determines whether a construction task's physical prerequisites are satisfied.
	 * Ensures hanging blocks have ceiling support and elevated blocks have foundations or prior layers placed.
	 *
	 * @param task  The task to evaluate.
	 * @param world The server world to inspect.
	 * @return True if the block can be physically placed now.
	 */
	public boolean isBuildTaskReady(ConstructionTask task, World world) {
		BlueprintBlock bpBlock = task.getBlueprintBlock();
		BlockPos targetPos = task.getWorldPos();

		// 1. Hanging blocks MUST have overhead solid support already in the world
		if (bpBlock.isHanging()) {
			BlockPos overhead = targetPos.up();
			return !world.getBlockState(overhead).isAir();
		}

		// 2. Base layer (Y offset <= 0) can always be placed
		if (bpBlock.offset().getY() <= 0) {
			return true;
		}

		// 3. Elevated blocks: either the block below is not air, or all tasks on lower layers are completed
		BlockPos underneath = targetPos.down();
		if (!world.getBlockState(underneath).isAir()) {
			return true;
		}

		// Check if any unfinished tasks exist at strictly lower Y offsets
		int targetOffsetY = bpBlock.offset().getY();
		for (ConstructionTask other : this.tasks) {
			if (other.getBlueprintBlock().offset().getY() < targetOffsetY && !other.isCompleted()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Determines whether a deconstruction task's prerequisites are met.
	 * Executes in top-down reverse topological order, verifying that all upper
	 * blocks and hanging decorations are cleared before supporting structures are removed.
	 *
	 * @param task  The deconstruction task to evaluate.
	 * @param world The server world to inspect.
	 * @return True if the block is physically safe to dismantle now.
	 */
	public boolean isDismantleTaskReady(ConstructionTask task, World world) {
		BlueprintBlock bpBlock = task.getBlueprintBlock();
		BlockPos targetPos = task.getWorldPos();

		// 1. If any non-hanging block resting directly above this block in the blueprint is not yet completed (dismantled),
		// we cannot dismantle this supporting block.
		if (!bpBlock.isHanging()) {
			BlockPos abovePos = targetPos.up();
			for (ConstructionTask other : this.tasks) {
				if (!other.isCompleted() && !other.getBlueprintBlock().isHanging() && other.getWorldPos().equals(abovePos)) {
					return false;
				}
			}
		}

		// 2. If any hanging block (e.g. lantern) is attached underneath this block and not yet dismantled,
		// the hanging decoration must be removed first before the ceiling block is broken.
		BlockPos belowPos = targetPos.down();
		for (ConstructionTask other : this.tasks) {
			if (!other.isCompleted() && other.getBlueprintBlock().isHanging() && other.getWorldPos().equals(belowPos)) {
				return false;
			}
		}

		// 3. Enforce top-down reverse topological progression across the structure:
		// Any unfinished tasks at strictly higher effective Y levels must be completed first.
		int targetEffectiveY = bpBlock.getEffectiveY();
		for (ConstructionTask other : this.tasks) {
			if (!other.isCompleted() && other.getBlueprintBlock().getEffectiveY() > targetEffectiveY) {
				return false;
			}
		}

		// 4. On the same effective Y layer, hanging blocks must be dismantled before non-hanging ceiling blocks
		if (!bpBlock.isHanging()) {
			for (ConstructionTask other : this.tasks) {
				if (!other.isCompleted() && other.getBlueprintBlock().isHanging()
						&& other.getBlueprintBlock().getEffectiveY() == targetEffectiveY) {
					if (other.getWorldPos().getX() == targetPos.getX() && other.getWorldPos().getZ() == targetPos.getZ()) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/**
	 * Releases a previously claimed task back to the pending queue.
	 *
	 * @param task The task to release.
	 */
	public synchronized void releaseTask(ConstructionTask task) {
		if (task != null && this.tasks.contains(task)) {
			task.release();
		}
	}

	/**
	 * Marks a construction task as completed and checks for overall session completion.
	 *
	 * @param task  The completed construction task.
	 * @param world The server world where construction occurred.
	 */
	public synchronized void completeTask(ConstructionTask task, ServerWorld world) {
		if (task != null && this.tasks.contains(task) && !task.isCompleted()) {
			task.complete();
			this.completedCount++;
			this.lastActivityTick = world.getTime();

			if (this.completedCount >= this.tasks.size()) {
				this.status = SessionStatus.COMPLETED;
				ConstructionManager.getInstance().completeSession(this, world);
			}
		}
	}

	/**
	 * Re-queues tasks that have been claimed longer than the given timeout duration
	 * (e.g. minion worker was killed, fell into lava, or became unreachable).
	 *
	 * @param currentTick  Current server world tick.
	 * @param timeoutTicks Maximum tick lease before reclaiming (e.g. 300 ticks = 15s).
	 */
	public synchronized void cleanStaleClaims(long currentTick, long timeoutTicks) {
		if (this.status != SessionStatus.ACTIVE) {
			return;
		}

		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && (currentTick - task.getClaimTick() > timeoutTicks)) {
				task.release();
			}
		}

		// Prune orphaned column reservations for minions with no remaining claimed tasks
		java.util.Set<UUID> activeClaimants = new java.util.HashSet<>();
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && task.getClaimedBy() != null) {
				activeClaimants.add(task.getClaimedBy());
			}
		}
		this.claimedScaffoldColumns.values().removeIf(claimant -> !activeClaimants.contains(claimant));
	}

	/**
	 * Cancels this construction session.
	 */
	public synchronized void cancel() {
		this.status = SessionStatus.CANCELLED;
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed()) {
				task.release();
			}
		}
		this.claimedScaffoldColumns.clear();
	}

	/**
	 * Pauses this construction session.
	 */
	public synchronized void pause() {
		if (this.status == SessionStatus.ACTIVE) {
			this.status = SessionStatus.PAUSED;
		}
	}

	/**
	 * Resumes a paused construction session.
	 */
	public synchronized void resume() {
		if (this.status == SessionStatus.PAUSED) {
			this.status = SessionStatus.ACTIVE;
		}
	}

	/**
	 * Registers a temporary zero-cost scaffolding block placed to assist construction.
	 *
	 * @param pos The world block position of the scaffolding.
	 */
	public void addTemporaryScaffolding(BlockPos pos) {
		this.temporaryScaffolding.add(pos.toImmutable());
	}

	/**
	 * Returns an unmodifiable set of all active temporary scaffolding positions for this session.
	 *
	 * @return Set of BlockPos.
	 */
	public Set<BlockPos> getTemporaryScaffolding() {
		return Collections.unmodifiableSet(this.temporaryScaffolding);
	}

	/**
	 * Checks if a specific position contains temporary scaffolding placed by this session.
	 *
	 * @param pos The block position to test.
	 * @return True if tracked as temporary scaffolding.
	 */
	public boolean isTemporaryScaffolding(BlockPos pos) {
		return this.temporaryScaffolding.contains(pos);
	}

	/**
	 * Removes a specific temporary scaffolding position from tracking.
	 *
	 * @param pos The world block position of the scaffolding.
	 */
	public void removeTemporaryScaffolding(BlockPos pos) {
		this.temporaryScaffolding.remove(pos);
	}

	/**
	 * Removes all temporary scaffolding blocks erected during this construction session,
	 * restoring the positions to air and spawning breaking particle effects.
	 *
	 * @param world The server world where construction occurred.
	 */
	public synchronized void clearScaffolding(ServerWorld world) {
		if (this.temporaryScaffolding.isEmpty()) {
			return;
		}

		// Ground any minions currently on or near temporary scaffolding to prevent falling
		for (BlockPos pos : this.temporaryScaffolding) {
			Box checkArea = new Box(pos).expand(0.5D, 1.2D, 0.5D);
			List<MinionEntity> nearbyMinions = world.getEntitiesByClass(MinionEntity.class, checkArea, m -> true);
			for (MinionEntity m : nearbyMinions) {
				m.fallDistance = 0.0F;
				BlockPos ground = pos.down();
				while (ground.getY() > world.getBottomY() && (this.temporaryScaffolding.contains(ground) || world.getBlockState(ground).isOf(Blocks.SCAFFOLDING) || world.getBlockState(ground).isAir())) {
					ground = ground.down();
				}
				if (world.getBlockState(ground).isSolidBlock(world, ground)) {
					m.refreshPositionAndAngles(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D, m.getYaw(), m.getPitch());
					m.setVelocity(0.0D, 0.0D, 0.0D);
					m.velocityModified = true;
					m.fallDistance = 0.0F;
				}
			}
		}

		for (BlockPos pos : this.temporaryScaffolding) {
			if (world.getBlockState(pos).isOf(Blocks.SCAFFOLDING)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				world.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SCAFFOLDING.getDefaultState()),
					pos.getX() + 0.5,
					pos.getY() + 0.5,
					pos.getZ() + 0.5,
					6,
					0.2,
					0.2,
					0.2,
					0.05
				);
			}
		}
		this.temporaryScaffolding.clear();
		this.claimedScaffoldColumns.clear();
	}

	/**
	 * Checks whether a vertical scaffolding column at the specified coordinate is available
	 * for reservation by a minion thrall (unclaimed, or already claimed by this exact minion).
	 *
	 * @param pos        World position identifying the column (matched by X and Z).
	 * @param minionUuid Requesting minion thrall UUID.
	 * @return True if available for reservation or already held by this minion.
	 */
	public synchronized boolean isScaffoldColumnAvailable(BlockPos pos, UUID minionUuid) {
		if (pos == null) {
			return false;
		}
		for (Map.Entry<BlockPos, UUID> entry : this.claimedScaffoldColumns.entrySet()) {
			BlockPos claimed = entry.getKey();
			if (claimed.getX() == pos.getX() && claimed.getZ() == pos.getZ()) {
				return minionUuid != null && minionUuid.equals(entry.getValue());
			}
		}
		return true;
	}

	/**
	 * Checks whether a scaffolding column coordinate is currently claimed by any minion.
	 *
	 * @param pos World position identifying the column (matched by X and Z).
	 * @return True if claimed by any minion.
	 */
	public synchronized boolean isScaffoldColumnClaimed(BlockPos pos) {
		if (pos == null) {
			return false;
		}
		for (BlockPos claimed : this.claimedScaffoldColumns.keySet()) {
			if (claimed.getX() == pos.getX() && claimed.getZ() == pos.getZ()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieves the UUID of the minion worker holding a reservation for the column at (X, Z).
	 *
	 * @param pos World position identifying the column.
	 * @return The holding minion's UUID, or null if unreserved.
	 */
	public synchronized UUID getScaffoldColumnClaimant(BlockPos pos) {
		if (pos == null) {
			return null;
		}
		for (Map.Entry<BlockPos, UUID> entry : this.claimedScaffoldColumns.entrySet()) {
			BlockPos claimed = entry.getKey();
			if (claimed.getX() == pos.getX() && claimed.getZ() == pos.getZ()) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Attempts to reserve a vertical scaffolding column at the given position for a minion.
	 * Multiple builders working on the same structure reserve separate column bases on
	 * different perimeter faces to prevent stacking, kinematic collisions, and ladder blocking.
	 *
	 * @param pos        World position of the column base.
	 * @param minionUuid UUID of the minion claiming this column.
	 * @return True if the claim succeeded or was already held by this minion; false if claimed by another.
	 */
	public synchronized boolean claimScaffoldColumn(BlockPos pos, UUID minionUuid) {
		if (pos == null || minionUuid == null) {
			return false;
		}
		for (Map.Entry<BlockPos, UUID> entry : this.claimedScaffoldColumns.entrySet()) {
			BlockPos claimed = entry.getKey();
			if (claimed.getX() == pos.getX() && claimed.getZ() == pos.getZ()) {
				if (minionUuid.equals(entry.getValue())) {
					return true;
				}
				return false;
			}
		}
		this.claimedScaffoldColumns.put(pos.toImmutable(), minionUuid);
		return true;
	}

	/**
	 * Releases a previously claimed scaffolding column reservation.
	 *
	 * @param pos        World position of the column base.
	 * @param minionUuid UUID of the minion releasing the column, or null to force-release.
	 * @return True if a reservation was removed.
	 */
	public synchronized boolean releaseScaffoldColumn(BlockPos pos, UUID minionUuid) {
		if (pos == null) {
			return false;
		}
		BlockPos toRemove = null;
		for (Map.Entry<BlockPos, UUID> entry : this.claimedScaffoldColumns.entrySet()) {
			BlockPos claimed = entry.getKey();
			if (claimed.getX() == pos.getX() && claimed.getZ() == pos.getZ()) {
				if (minionUuid == null || minionUuid.equals(entry.getValue())) {
					toRemove = claimed;
					break;
				}
			}
		}
		if (toRemove != null) {
			this.claimedScaffoldColumns.remove(toRemove);
			return true;
		}
		return false;
	}

	/**
	 * Releases a previously claimed scaffolding column reservation regardless of holder.
	 *
	 * @param pos World position of the column base.
	 * @return True if a reservation was removed.
	 */
	public synchronized boolean releaseScaffoldColumn(BlockPos pos) {
		return releaseScaffoldColumn(pos, null);
	}

	/**
	 * Releases all scaffolding column reservations held by the specified minion.
	 *
	 * @param minionUuid UUID of the minion whose column claims should be released.
	 */
	public synchronized void releaseScaffoldColumnsForMinion(UUID minionUuid) {
		if (minionUuid == null) {
			return;
}
		this.claimedScaffoldColumns.values().removeIf(uuid -> uuid.equals(minionUuid));
	}

	/**
	 * Checks if the specified minion currently holds any active scaffold column reservation.
	 *
	 * @param minionUuid UUID of the minion thrall.
	 * @return True if the minion has reserved at least one column.
	 */
	public synchronized boolean isScaffoldColumnClaimedBy(UUID minionUuid) {
		if (minionUuid == null) {
			return false;
		}
		return this.claimedScaffoldColumns.containsValue(minionUuid);
	}

	/**
	 * Returns an unmodifiable map view of all claimed scaffolding columns and their worker UUIDs.
	 *
	 * @return Map of BlockPos to minion UUID.
	 */
	public Map<BlockPos, UUID> getClaimedScaffoldColumns() {
		return Collections.unmodifiableMap(this.claimedScaffoldColumns);
	}

	/**
	 * Checks if a minion thrall is actively engaged in this session, either holding a claimed
	 * task or a reserved scaffolding column.
	 *
	 * @param minionUuid The minion's unique ID.
	 * @return True if actively engaged in this session.
	 */
	public synchronized boolean isMinionEngaged(UUID minionUuid) {
		if (minionUuid == null || this.status != SessionStatus.ACTIVE) {
			return false;
		}
		for (ConstructionTask task : this.tasks) {
			if (task.isClaimed() && minionUuid.equals(task.getClaimedBy())) {
				return true;
			}
		}
		return this.claimedScaffoldColumns.containsValue(minionUuid);
	}
}