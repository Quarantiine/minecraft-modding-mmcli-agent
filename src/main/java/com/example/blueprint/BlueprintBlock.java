package com.example.blueprint;

import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

/**
 * Immutable representation of a single block within a structure blueprint.
 * Stores relative coordinates from the structure anchor and the target BlockState.
 *
 * Implements strict bottom-up topological sorting:
 * 1. Effective vertical height (Y coordinate, with dependent hanging blocks sorted after their ceiling anchor).
 * 2. Manhattan distance from origin (center-outward construction order).
 * 3. Lexicographical coordinate tie-breakers (X, then Z).
 */
public record BlueprintBlock(BlockPos offset, BlockState state) implements Comparable<BlueprintBlock> {

	public BlueprintBlock {
		Objects.requireNonNull(offset, "offset cannot be null");
		Objects.requireNonNull(state, "state cannot be null");
	}

	/**
	 * Computes the absolute world position for this blueprint block relative to an anchor point.
	 *
	 * @param anchor The origin block coordinate in world space.
	 * @return The target world position.
	 */
	public BlockPos toWorldPos(BlockPos anchor) {
		return anchor.add(this.offset);
	}

	/**
	 * Resolves the item needed by minions to construct this block in survival mode.
	 *
	 * @return The Item associated with this block state, or Items.AIR if not obtainable.
	 */
	public Item getRequiredItem() {
		return this.state.getBlock().asItem();
	}

	/**
	 * Creates a single-count ItemStack of the required item.
	 *
	 * @return An ItemStack representing one unit of the required resource.
	 */
	public ItemStack getRequiredStack() {
		return new ItemStack(getRequiredItem());
	}

	/**
	 * Determines whether this block relies on a supporting block above it (such as a hanging lantern).
	 *
	 * @return True if this block hangs from an overhead support.
	 */
	public boolean isHanging() {
		return this.state.contains(Properties.HANGING) && Boolean.TRUE.equals(this.state.get(Properties.HANGING));
	}

	/**
	 * Calculates the effective vertical construction layer.
	 * Hanging blocks are shifted up one layer so their overhead ceiling support is guaranteed
	 * to be placed first during topological bottom-up execution.
	 *
	 * @return The effective vertical layer for sorting.
	 */
	public int getEffectiveY() {
		return isHanging() ? this.offset.getY() + 1 : this.offset.getY();
	}

	/**
	 * Calculates the horizontal Manhattan distance from the blueprint origin (0, 0).
	 * Used to prioritize central structural cores before building outward overhangs and buttresses.
	 *
	 * @return Manhattan distance |X| + |Z|.
	 */
	public int getManhattanDistance() {
		return Math.abs(this.offset.getX()) + Math.abs(this.offset.getZ());
	}

	@Override
	public int compareTo(BlueprintBlock other) {
		// 1. Primary: Bottom-up layer ordering by effective Y
		int layerComp = Integer.compare(this.getEffectiveY(), other.getEffectiveY());
		if (layerComp != 0) {
			return layerComp;
		}

		// 2. Hanging dependency guard: non-hanging blocks precede hanging blocks on the same effective layer
		int hangingComp = Boolean.compare(this.isHanging(), other.isHanging());
		if (hangingComp != 0) {
			return hangingComp;
		}

		// 3. Secondary: Radial Manhattan distance from center outward
		int distComp = Integer.compare(this.getManhattanDistance(), other.getManhattanDistance());
		if (distComp != 0) {
			return distComp;
		}

		// 4. Tertiary: Coordinate tie-breakers for deterministic total ordering
		int xComp = Integer.compare(this.offset.getX(), other.offset.getX());
		if (xComp != 0) {
			return xComp;
		}

		int zComp = Integer.compare(this.offset.getZ(), other.offset.getZ());
		if (zComp != 0) {
			return zComp;
		}

		// 5. Quaternary: Actual Y coordinate (in case hanging shifted the effective Y)
		int rawYComp = Integer.compare(this.offset.getY(), other.offset.getY());
		if (rawYComp != 0) {
			return rawYComp;
		}

		// 6. Block state string comparison for complete total ordering
		return this.state.toString().compareTo(other.state.toString());
	}
}
