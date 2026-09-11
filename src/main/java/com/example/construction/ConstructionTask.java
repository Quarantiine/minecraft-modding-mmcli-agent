package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;

/**
 * Represents an individual block placement task within an active {@link ConstructionSession}.
 * Tracks topological ordering, target world coordinates, assignment status,
 * and minion worker leases.
 */
public class ConstructionTask {

	/**
	 * State of the task in the construction lifecycle.
	 */
	public enum Status {
		PENDING,
		CLAIMED,
		COMPLETED
	}

	private final int id;
	private final BlueprintBlock blueprintBlock;
	private final BlockPos worldPos;
	private Status status = Status.PENDING;
	private UUID claimedBy = null;
	private long claimTick = 0L;

	/**
	 * Constructs a new ConstructionTask.
	 *
	 * @param id             Sequential index of this task in topological blueprint order.
	 * @param blueprintBlock The blueprint block specification.
	 * @param anchorPos      The world anchor position of the construction session.
	 */
	public ConstructionTask(int id, BlueprintBlock blueprintBlock, BlockPos anchorPos) {
		this.id = id;
		this.blueprintBlock = Objects.requireNonNull(blueprintBlock, "blueprintBlock cannot be null");
		this.worldPos = blueprintBlock.toWorldPos(anchorPos).toImmutable();
	}

	public int getId() {
		return this.id;
	}

	public BlueprintBlock getBlueprintBlock() {
		return this.blueprintBlock;
	}

	public BlockPos getWorldPos() {
		return this.worldPos;
	}

	public Status getStatus() {
		return this.status;
	}

	public UUID getClaimedBy() {
		return this.claimedBy;
	}

	public long getClaimTick() {
		return this.claimTick;
	}

	public boolean isPending() {
		return this.status == Status.PENDING;
	}

	public boolean isClaimed() {
		return this.status == Status.CLAIMED;
	}

	public boolean isCompleted() {
		return this.status == Status.COMPLETED;
	}

	/**
	 * Claims this task for the specified minion worker.
	 *
	 * @param minionUuid  UUID of the minion claiming the task.
	 * @param currentTick Current world tick for timeout tracking.
	 * @return True if successfully claimed, false if already claimed or completed.
	 */
	public synchronized boolean claim(UUID minionUuid, long currentTick) {
		if (this.status != Status.PENDING && !Objects.equals(this.claimedBy, minionUuid)) {
			return false;
		}
		this.status = Status.CLAIMED;
		this.claimedBy = minionUuid;
		this.claimTick = currentTick;
		return true;
	}

	/**
	 * Releases this task back to the pending pool, allowing other minions to claim it.
	 */
	public synchronized void release() {
		if (this.status == Status.CLAIMED) {
			this.status = Status.PENDING;
			this.claimedBy = null;
			this.claimTick = 0L;
		}
	}

	/**
	 * Marks this task as permanently completed.
	 */
	public synchronized void complete() {
		this.status = Status.COMPLETED;
		this.claimedBy = null;
		this.claimTick = 0L;
	}

	@Override
	public String toString() {
		return "ConstructionTask{" +
			"id=" + id +
			", block=" + blueprintBlock.state().getBlock().getName().getString() +
			", pos=" + worldPos.toShortString() +
			", status=" + status +
			(claimedBy != null ? ", claimedBy=" + claimedBy : "") +
			'}';
	}
}
