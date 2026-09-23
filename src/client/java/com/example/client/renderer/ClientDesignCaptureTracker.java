package com.example.client.renderer;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Client-side singleton tracker for in-world spatial capture corner selections in DESIGN mode.
 * Stores the two selected bounding corners (Pos1 and Pos2), computes cuboid dimensions and bounding boxes,
 * and validates volume safety limits (<= 64 blocks on any dimension, <= 65536 voxels total).
 */
public class ClientDesignCaptureTracker {

	public static final int MAX_DIMENSION = 64;
	public static final int MAX_HEIGHT = 96;
	public static final int MAX_VOLUME = 393216;

	private static BlockPos pos1 = null;
	private static BlockPos pos2 = null;

	public static synchronized void setPos1(BlockPos pos) {
		pos1 = pos != null ? pos.toImmutable() : null;
	}

	public static synchronized BlockPos getPos1() {
		return pos1;
	}

	public static synchronized void setPos2(BlockPos pos) {
		pos2 = pos != null ? pos.toImmutable() : null;
	}

	public static synchronized BlockPos getPos2() {
		return pos2;
	}

	public static synchronized void clear() {
		pos1 = null;
		pos2 = null;
		try {
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			if (mc != null && mc.player != null) {
				net.minecraft.item.ItemStack held = com.example.item.custom.CommandScepterItem.getHeldScepter(mc.player);
				if (!held.isEmpty()) {
					com.example.item.custom.CommandScepterItem.clearDesignCorners(held);
				}
			}
		} catch (Throwable ignored) {
			// Headless / mock environment safety
		}
	}

	public static synchronized boolean hasPos1() {
		return pos1 != null;
	}

	public static synchronized boolean hasPos2() {
		return pos2 != null;
	}

	public static synchronized boolean hasCompleteSelection() {
		return pos1 != null && pos2 != null;
	}

	/**
	 * Sequentially advances corner selection with a left-click:
	 * - If Pos1 is null or if both Pos1 and Pos2 are already set (completing a prior box),
	 *   assigns Pos1 = pos and clears Pos2 to initiate a fresh selection cycle (returns 1).
	 * - If Pos1 is present and Pos2 is null, assigns Pos2 = pos to complete the volume (returns 2).
	 *
	 * @param pos The clicked block position.
	 * @return 1 if Pos1 was set, 2 if Pos2 was set, or 0 if pos is null.
	 */
	public static synchronized int stepCorner(BlockPos pos) {
		if (pos == null) return 0;
		if (pos1 == null) {
			pos1 = pos.toImmutable();
			pos2 = null;
			return 1;
		} else if (pos2 == null) {
			pos2 = pos.toImmutable();
			return 2;
		} else {
			// Both Pos1 and Pos2 are established.
			// Guard: If clicking the same block as Pos2, retain the completed volume
			// to prevent duplicate click events/mouse bounce from immediately collapsing the selection.
			if (pos.equals(pos2)) {
				return 2;
			}
			pos1 = pos.toImmutable();
			pos2 = null;
			return 1;
		}
	}

	public static synchronized int getSizeX() {
		if (pos1 == null || pos2 == null) return 0;
		return Math.abs(pos1.getX() - pos2.getX()) + 1;
	}

	public static synchronized int getSizeY() {
		if (pos1 == null || pos2 == null) return 0;
		return Math.abs(pos1.getY() - pos2.getY()) + 1;
	}

	public static synchronized int getSizeZ() {
		if (pos1 == null || pos2 == null) return 0;
		return Math.abs(pos1.getZ() - pos2.getZ()) + 1;
	}

	public static synchronized long getVolume() {
		if (pos1 == null || pos2 == null) return 0L;
		return (long) getSizeX() * getSizeY() * getSizeZ();
	}

	public static synchronized boolean isWithinLimit() {
		if (!hasCompleteSelection()) return false;
		return getSizeX() <= MAX_DIMENSION
			&& getSizeY() <= MAX_HEIGHT
			&& getSizeZ() <= MAX_DIMENSION
			&& getVolume() <= MAX_VOLUME;
	}

	public static synchronized BlockBox getBlockBox() {
		if (pos1 == null || pos2 == null) return null;
		int minX = Math.min(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());
		return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static synchronized Box getBox() {
		if (pos1 == null || pos2 == null) return null;
		int minX = Math.min(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());
		return new Box(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
	}

	public static synchronized String getDimensionString() {
		if (!hasCompleteSelection()) {
			return "Incomplete Selection";
		}
		return getSizeX() + "x" + getSizeY() + "x" + getSizeZ() + " (" + getVolume() + "b)";
	}

	/**
	 * Adjusts the vertical height of the current design selection box by {@code delta} blocks.
	 * Clamps height to between 1 and MAX_HEIGHT (96).
	 * If both Pos1 and Pos2 are established, moves the upper Y coordinate up or down.
	 * If only Pos1 is set, initializes Pos2 at (Pos1.x, Pos1.y + delta, Pos1.z).
	 *
	 * @param delta Number of blocks to expand (+1, +5) or shrink (-1, -5).
	 * @return The new height in blocks (getSizeY()).
	 */
	public static synchronized int adjustHeight(int delta) {
		if (pos1 == null && pos2 == null) {
			return 0;
		}

		if (pos1 != null && pos2 == null) {
			int initialHeight = Math.max(1, Math.min(MAX_HEIGHT, 1 + delta));
			pos2 = new BlockPos(pos1.getX(), pos1.getY() + initialHeight - 1, pos1.getZ());
			syncWithHeldScepter();
			return getSizeY();
		}

		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int currentHeight = maxY - minY + 1;
		int newHeight = Math.max(1, Math.min(MAX_HEIGHT, currentHeight + delta));
		int targetMaxY = minY + newHeight - 1;

		if (pos1.getY() > pos2.getY()) {
			pos1 = new BlockPos(pos1.getX(), targetMaxY, pos1.getZ());
		} else {
			pos2 = new BlockPos(pos2.getX(), targetMaxY, pos2.getZ());
		}

		syncWithHeldScepter();
		return getSizeY();
	}

	/**
	 * Explicitly sets the height of the current design selection box to {@code targetHeight} blocks.
	 * Clamps height to between 1 and MAX_HEIGHT (96).
	 *
	 * @param targetHeight Desired height in blocks.
	 * @return The resulting height in blocks (getSizeY()).
	 */
	public static synchronized int setHeight(int targetHeight) {
		if (pos1 == null || pos2 == null) return 0;
		int clampedHeight = Math.max(1, Math.min(MAX_HEIGHT, targetHeight));
		int minY = Math.min(pos1.getY(), pos2.getY());
		int targetMaxY = minY + clampedHeight - 1;

		if (pos1.getY() > pos2.getY()) {
			pos1 = new BlockPos(pos1.getX(), targetMaxY, pos1.getZ());
		} else {
			pos2 = new BlockPos(pos2.getX(), targetMaxY, pos2.getZ());
		}

		syncWithHeldScepter();
		return getSizeY();
	}

	private static void syncWithHeldScepter() {
		try {
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			if (mc != null && mc.player != null) {
				net.minecraft.item.ItemStack held = com.example.item.custom.CommandScepterItem.getHeldScepter(mc.player);
				if (!held.isEmpty()) {
					com.example.item.custom.CommandScepterItem.setDesignCorners(held, pos1, pos2);
				}
			}
		} catch (Throwable ignored) {
			// Headless / mock environment safety
		}
	}
}
