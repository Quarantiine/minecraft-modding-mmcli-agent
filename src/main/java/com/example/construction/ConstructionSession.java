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
import net.minecraft.block.BlockState;
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
		this(ownerUuid, dimension, anchorPos, blueprint, creative, currentTick, mode, null);
	}

	/**
	 * Creates a new ConstructionSession anchored at the specified world coordinate with an explicit session mode
	 * and world reference for terrain inspection. Indestructible blocks (hardness < 0.0F or Blocks.BEDROCK)
	 * are strictly filtered out during DISMANTLE mode so minions never dismantle or break them.
	 *
	 * @param ownerUuid   UUID of the player who initiated construction.
	 * @param dimension   World dimension key where construction is located.
	 * @param anchorPos   The world origin block position for this structure.
	 * @param blueprint   The structure blueprint to construct or dismantle.
	 * @param creative    True if free instant placement/breaking, false for survival inventory constraints.
	 * @param currentTick Current server tick count at session creation.
	 * @param mode        The operational mode: BUILD (bottom-up) or DISMANTLE (top-down reverse topological).
	 * @param world       Optional server world context for block hardness inspection.
	 */
	public ConstructionSession(
		UUID ownerUuid,
		RegistryKey<World> dimension,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		boolean creative,
		long currentTick,
		SessionMode mode,
		World world
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

		List<ConstructionTask> taskList = new ArrayList<>();
		if (this.mode == SessionMode.DISMANTLE && world != null) {
			// In DISMANTLE mode with active world:
			// Scan the entire selected/highlighted 3D area for real non-air blocks.
			// Miners must mine EVERYTHING in the selected area, but NEVER mine air!
			Map<BlockPos, BlockState> blocksToMine = new java.util.LinkedHashMap<>();

			// 1. Scan the full worldBoundingBox from top to bottom (highest Y to lowest Y)
			for (int y = this.worldBoundingBox.getMaxY(); y >= this.worldBoundingBox.getMinY(); y--) {
				for (int x = this.worldBoundingBox.getMinX(); x <= this.worldBoundingBox.getMaxX(); x++) {
					for (int z = this.worldBoundingBox.getMinZ(); z <= this.worldBoundingBox.getMaxZ(); z++) {
						BlockPos pos = new BlockPos(x, y, z);
						BlockState state = world.getBlockState(pos);
						if (!state.isAir() && !isIndestructible(state, world, pos)) {
							blocksToMine.put(pos, state);
						}
					}
				}
			}

			// 2. Also ensure any non-air blocks specified in blueprint are included if outside box
			for (BlueprintBlock bpBlock : blueprint.getBlocks()) {
				BlockPos pos = this.anchorPos.add(bpBlock.offset().getX(), bpBlock.offset().getY(), bpBlock.offset().getZ());
				BlockState state = world.getBlockState(pos);
				if (!state.isAir() && !isIndestructible(state, world, pos)) {
					blocksToMine.putIfAbsent(pos, state);
				}
			}

			// Sort strictly top-down (highest Y first, then X and Z)
			List<Map.Entry<BlockPos, BlockState>> sortedEntries = new ArrayList<>(blocksToMine.entrySet());
			sortedEntries.sort((a, b) -> {
				int cmpY = Integer.compare(b.getKey().getY(), a.getKey().getY());
				if (cmpY != 0) return cmpY;
				int cmpX = Integer.compare(a.getKey().getX(), b.getKey().getX());
				if (cmpX != 0) return cmpX;
				return Integer.compare(a.getKey().getZ(), b.getKey().getZ());
			});

			for (int i = 0; i < sortedEntries.size(); i++) {
				Map.Entry<BlockPos, BlockState> entry = sortedEntries.get(i);
				BlockPos pos = entry.getKey();
				BlockState state = entry.getValue();
				BlockPos localOffset = pos.subtract(this.anchorPos);
				BlueprintBlock bpBlock = new BlueprintBlock(localOffset, state);
				taskList.add(new ConstructionTask(i, bpBlock, this.anchorPos));
			}
		} else {
			// BUILD mode or offline/mock tests where world is null
			List<BlueprintBlock> blueprintBlocks = new ArrayList<>(blueprint.getBlocks());
			if (this.mode == SessionMode.DISMANTLE) {
				Collections.reverse(blueprintBlocks);
				blueprintBlocks.removeIf(bpBlock -> {
					BlockPos targetPos = this.anchorPos.add(bpBlock.offset().getX(), bpBlock.offset().getY(), bpBlock.offset().getZ());
					return isIndestructible(bpBlock.state(), world, targetPos);
				});
			}
			for (int i = 0; i < blueprintBlocks.size(); i++) {
				taskList.add(new ConstructionTask(i, blueprintBlocks.get(i), this.anchorPos));
			}
		}

		this.tasks = Collections.unmodifiableList(taskList);

		if (this.tasks.isEmpty() && this.mode == SessionMode.DISMANTLE) {
			this.status = SessionStatus.COMPLETED;
		}
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

	/**
	 * Checks whether any non-air, destructible blocks still remain within this session's
	 * 3D bounding box in the world. Used by DISMANTLE mode to verify complete area clearance.
	 *
	 * @param world The world to inspect.
	 * @return True if at least one non-air, non-indestructible block remains; false if completely cleared.
	 */
	public boolean hasRemainingBlocksInWorld(World world) {
		if (world == null) {
			return this.completedCount < this.tasks.size();
		}
		for (int y = this.worldBoundingBox.getMinY(); y <= this.worldBoundingBox.getMaxY(); y++) {
			for (int x = this.worldBoundingBox.getMinX(); x <= this.worldBoundingBox.getMaxX(); x++) {
				for (int z = this.worldBoundingBox.getMinZ(); z <= this.worldBoundingBox.getMaxZ(); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (!state.isAir() && !isIndestructible(state, world, pos)) {
						return true;
					}
				}
			}
		}
		return false;
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
			if (task.isPending()) {
				// Strict safeguard: if the target block is already air or indestructible/bedrock in DISMANTLE mode,
				// auto-complete it immediately so minions never mine empty air and the session completes cleanly!
				if (this.mode == SessionMode.DISMANTLE) {
					BlueprintBlock bpBlock = task.getBlueprintBlock();
					BlockPos targetPos = task.getWorldPos();
					if (world != null) {
						BlockState worldState = world.getBlockState(targetPos);
						if (worldState.isAir() || isIndestructible(worldState, world, targetPos)) {
							task.complete();
							this.completedCount++;
							if (this.completedCount >= this.tasks.size() || !hasRemainingBlocksInWorld(world)) {
								this.status = SessionStatus.COMPLETED;
								if (world instanceof ServerWorld serverWorld) {
									ConstructionManager.getInstance().completeSession(this, serverWorld);
								}
								return null;
							}
							continue;
						}
					} else if (isIndestructible(bpBlock.state(), null, targetPos)) {
						task.complete();
						this.completedCount++;
						if (this.completedCount >= this.tasks.size()) {
							this.status = SessionStatus.COMPLETED;
							return null;
						}
						continue;
					}
				}

				if (isTaskReady(task, world)) {
					if (task.claim(minionUuid, currentTick)) {
						this.lastActivityTick = currentTick;
						return task;
					}
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
			if (world.getBlockState(overhead).isAir()) {
				return false;
			}
		}

		// 2. Base layer (effective Y <= 0) can always be placed
		if (bpBlock.getEffectiveY() <= 0) {
			return true;
		}

		// 3. Elevated non-hanging blocks: either the block below is not air, or all tasks on lower effective layers are completed
		if (!bpBlock.isHanging()) {
			BlockPos underneath = targetPos.down();
			if (!world.getBlockState(underneath).isAir()) {
				return true;
			}
		}

		// Check if any unfinished tasks exist at strictly lower effective Y offsets
		int targetEffectiveY = bpBlock.getEffectiveY();
		for (ConstructionTask other : this.tasks) {
			if (other.getBlueprintBlock().getEffectiveY() < targetEffectiveY && !other.isCompleted()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Determines whether a block state is indestructible / unbreakable (e.g. Bedrock, barrier, end portal,
	 * or any block with negative hardness < 0.0F) and must never be broken or dismantled.
	 *
	 * @param state The block state to evaluate.
	 * @param world The world instance (may be null).
	 * @param pos   The block position (may be null).
	 * @return True if the block is indestructible or bedrock.
	 */
	public static boolean isIndestructible(BlockState state, World world, BlockPos pos) {
		if (state == null) {
			return false;
		}
		if (state.isOf(Blocks.BEDROCK)) {
			return true;
		}
		try {
			if (world != null && pos != null) {
				return state.getHardness(world, pos) < 0.0F;
			}
			return state.getBlock().getHardness() < 0.0F;
		} catch (Exception e) {
			return state.isOf(Blocks.BEDROCK);
		}
	}

	/**
	 * Determines whether a block at the given position in the world is indestructible or bedrock.
	 *
	 * @param world The server world.
	 * @param pos   The world position to check.
	 * @return True if the block at pos is indestructible or bedrock.
	 */
	public static boolean isIndestructible(World world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return isIndestructible(world.getBlockState(pos), world, pos);
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
		if (task == null) {
			return false;
		}

		BlueprintBlock bpBlock = task.getBlueprintBlock();
		BlockPos targetPos = task.getWorldPos();

		// Safeguard: Indestructible blocks (hardness < 0.0F or Blocks.BEDROCK) must never be marked ready for dismantling
		if (isIndestructible(bpBlock.state(), world, targetPos)) {
			return false;
		}
		if (world != null) {
			BlockState worldState = world.getBlockState(targetPos);
			if (isIndestructible(worldState, world, targetPos)) {
				return false;
			}
		}

		// 1. If any non-hanging block resting directly above this block in the blueprint is not yet completed (dismantled),
		// we cannot dismantle this supporting block.
		if (!bpBlock.isHanging()) {
			BlockPos abovePos = targetPos.up();
			for (ConstructionTask other : this.tasks) {
				if (!other.isCompleted() && !other.getBlueprintBlock().isHanging() && other.getWorldPos().equals(abovePos)) {
					if (world != null && (world.getBlockState(abovePos).isAir() || isIndestructible(world, abovePos))) {
						other.complete();
						this.completedCount++;
						continue;
					}
					return false;
				}
			}
		}

		// 2. If any hanging block (e.g. lantern) is attached underneath this block and not yet dismantled,
		// the hanging decoration must be removed first before the ceiling block is broken.
		BlockPos belowPos = targetPos.down();
		for (ConstructionTask other : this.tasks) {
			if (!other.isCompleted() && other.getBlueprintBlock().isHanging() && other.getWorldPos().equals(belowPos)) {
				if (world != null && (world.getBlockState(belowPos).isAir() || isIndestructible(world, belowPos))) {
					other.complete();
					this.completedCount++;
					continue;
				}
				return false;
			}
		}

		// 3. Enforce top-down reverse topological progression across the structure:
		// Any unfinished tasks at strictly higher effective Y levels must be completed first.
		int targetEffectiveY = bpBlock.getEffectiveY();
		for (ConstructionTask other : this.tasks) {
			if (!other.isCompleted() && other.getBlueprintBlock().getEffectiveY() > targetEffectiveY) {
				if (world != null && (world.getBlockState(other.getWorldPos()).isAir() || isIndestructible(world, other.getWorldPos()))) {
					other.complete();
					this.completedCount++;
					continue;
				}
				return false;
			}
		}

		// 4. On the same effective Y layer, hanging blocks must be dismantled before non-hanging ceiling blocks
		if (!bpBlock.isHanging()) {
			for (ConstructionTask other : this.tasks) {
				if (!other.isCompleted() && other.getBlueprintBlock().isHanging()
						&& other.getBlueprintBlock().getEffectiveY() == targetEffectiveY) {
					if (other.getWorldPos().getX() == targetPos.getX() && other.getWorldPos().getZ() == targetPos.getZ()) {
						if (world != null && (world.getBlockState(other.getWorldPos()).isAir() || isIndestructible(world, other.getWorldPos()))) {
							other.complete();
							this.completedCount++;
							continue;
						}
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

			boolean allDone = this.completedCount >= this.tasks.size();
			if (!allDone && this.mode == SessionMode.DISMANTLE) {
				allDone = !hasRemainingBlocksInWorld(world);
			}

			if (allDone) {
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
		return false;
	}
}