package com.example.construction;

import com.example.entity.custom.MinionEntity;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Server-side singleton manager governing temporary traversal scaffolding placed by minion sappers.
 *
 * <p>Minion sappers dynamically erect scaffolding columns and bridges across cliffs, ravines, and chasms
 * during tactical movement. This manager tracks ephemeral scaffolding blocks and enforces an automatic decay
 * lifecycle (defaulting to 400 ticks / 20 seconds).
 *
 * <p>To prevent thralls and commanding players from plunging into chasms or lava, an entity safety detector
 * checks if any {@link MinionEntity} or {@link PlayerEntity} is currently standing on or inside the scaffold.
 * If occupied, block decay is postponed by an additional 40 ticks until all units have safely crossed.
 */
public class TraversalScaffoldingManager {

	private static final TraversalScaffoldingManager INSTANCE = new TraversalScaffoldingManager();

	public static final int DEFAULT_DECAY_TICKS = 400; // 20 seconds
	public static final int SAFETY_DELAY_TICKS = 40;   // 2 seconds delay extension when occupied

	private final Map<RegistryKey<World>, Map<BlockPos, TraversalEntry>> scaffoldingByDimension = new ConcurrentHashMap<>();

	private TraversalScaffoldingManager() {}

	/**
	 * Returns the singleton instance of the traversal scaffolding manager.
	 *
	 * @return The singleton manager instance.
	 */
	public static TraversalScaffoldingManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Registers a newly placed temporary traversal scaffolding block for decay tracking.
	 *
	 * @param world The server world where scaffolding was placed.
	 * @param pos   The immutable coordinates of the scaffolding block.
	 */
	public void registerScaffolding(ServerWorld world, BlockPos pos) {
		registerScaffolding(world, pos, DEFAULT_DECAY_TICKS);
	}

	/**
	 * Registers a newly placed temporary traversal scaffolding block with a specified lifetime.
	 *
	 * @param world         The server world where scaffolding was placed.
	 * @param pos           The immutable coordinates of the scaffolding block.
	 * @param lifetimeTicks The duration in ticks before the scaffold decays.
	 */
	public void registerScaffolding(ServerWorld world, BlockPos pos, int lifetimeTicks) {
		Objects.requireNonNull(world, "World cannot be null");
		Objects.requireNonNull(pos, "BlockPos cannot be null");

		BlockPos immutablePos = pos.toImmutable();
		long currentTick = world.getTime();
		long expiryTick = currentTick + Math.max(1, lifetimeTicks);

		Map<BlockPos, TraversalEntry> dimensionMap = this.scaffoldingByDimension.computeIfAbsent(
			world.getRegistryKey(),
			k -> new ConcurrentHashMap<>()
		);

		dimensionMap.compute(immutablePos, (p, existing) -> {
			if (existing == null) {
				return new TraversalEntry(immutablePos, currentTick, expiryTick);
			} else {
				existing.setExpiryTick(Math.max(existing.getExpiryTick(), expiryTick));
				return existing;
			}
		});
	}

	/**
	 * Checks if a given block coordinate is tracked as an active traversal scaffold in the world.
	 *
	 * @param world The server world to query.
	 * @param pos   The block position to inspect.
	 * @return True if actively registered and tracked as traversal scaffolding.
	 */
	public boolean isTraversalScaffolding(ServerWorld world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.get(world.getRegistryKey());
		return map != null && map.containsKey(pos);
	}

	/**
	 * Returns the remaining lifetime in ticks for a specific traversal scaffolding block,
	 * or -1 if not actively tracked.
	 *
	 * @param world The server world to query.
	 * @param pos   The block position to inspect.
	 * @return Remaining ticks until decay, or -1 if not tracked.
	 */
	public int getRemainingTicks(ServerWorld world, BlockPos pos) {
		if (world == null || pos == null) {
			return -1;
		}
		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.get(world.getRegistryKey());
		if (map == null) {
			return -1;
		}
		TraversalEntry entry = map.get(pos);
		if (entry == null) {
			return -1;
		}
		return (int) Math.max(0, entry.getExpiryTick() - world.getTime());
	}

	/**
	 * Checks whether any {@link MinionEntity} or {@link PlayerEntity} is currently occupying
	 * or standing on top of the given scaffolding block.
	 *
	 * @param world The server world to inspect.
	 * @param pos   The scaffolding block coordinates.
	 * @return True if a minion or player is inside or standing atop the block.
	 */
	public boolean isOccupied(ServerWorld world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		Box checkVolume = new Box(
			pos.getX() - 0.1D, pos.getY(), pos.getZ() - 0.1D,
			pos.getX() + 1.1D, pos.getY() + 2.1D, pos.getZ() + 1.1D
		);

		List<LivingEntity> occupants = world.getEntitiesByClass(
			LivingEntity.class,
			checkVolume,
			entity -> entity.isAlive() && (entity instanceof MinionEntity || entity instanceof PlayerEntity)
		);
		return !occupants.isEmpty();
	}

	/**
	 * Ticks all registered traversal scaffolding blocks in the world, processing decay lifecycles,
	 * entity safety extensions, and break SFX on expiration.
	 *
	 * @param world The server world being ticked.
	 */
	public void tick(ServerWorld world) {
		if (world == null) {
			return;
		}

		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.get(world.getRegistryKey());
		if (map == null || map.isEmpty()) {
			return;
		}

		long currentTick = world.getTime();
		Iterator<Map.Entry<BlockPos, TraversalEntry>> iterator = map.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<BlockPos, TraversalEntry> mapEntry = iterator.next();
			BlockPos pos = mapEntry.getKey();
			TraversalEntry entry = mapEntry.getValue();

			// If the block is no longer scaffolding (e.g. broken manually or replaced), untrack it
			if (!world.getBlockState(pos).isOf(Blocks.SCAFFOLDING)) {
				iterator.remove();
				continue;
			}

			// Entity safety guard: extend decay timer if a player or minion is on/within the scaffold
			if (isOccupied(world, pos)) {
				entry.setExpiryTick(Math.max(entry.getExpiryTick(), currentTick + SAFETY_DELAY_TICKS));
				continue;
			}

			// Check for decay expiration
			if (currentTick >= entry.getExpiryTick()) {
				iterator.remove();
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				world.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SCAFFOLDING.getDefaultState()),
					pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
					8, 0.25D, 0.25D, 0.25D, 0.05D
				);
				world.playSound(
					null, pos,
					SoundEvents.BLOCK_SCAFFOLDING_BREAK,
					SoundCategory.BLOCKS,
					0.8F, 1.0F
				);
			}
		}
	}

	/**
	 * Manually removes a scaffolding block from active tracking.
	 *
	 * @param world The server world.
	 * @param pos   The block position to untrack.
	 */
	public void removeScaffolding(ServerWorld world, BlockPos pos) {
		if (world == null || pos == null) {
			return;
		}
		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.get(world.getRegistryKey());
		if (map != null) {
			map.remove(pos);
		}
	}

	/**
	 * Returns an unmodifiable set of all actively tracked scaffolding positions in the specified world.
	 *
	 * @param world The server world to query.
	 * @return An unmodifiable set of coordinates.
	 */
	public Set<BlockPos> getActiveScaffoldingPositions(ServerWorld world) {
		if (world == null) {
			return Collections.emptySet();
		}
		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.get(world.getRegistryKey());
		return map == null ? Collections.emptySet() : Collections.unmodifiableSet(map.keySet());
	}

	/**
	 * Returns the number of active traversal scaffolding blocks tracked in the specified world.
	 *
	 * @param world The server world.
	 * @return The active count.
	 */
	public int getActiveCount(ServerWorld world) {
		if (world == null) {
			return 0;
		}
		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.get(world.getRegistryKey());
		return map == null ? 0 : map.size();
	}

	/**
	 * Clears and breaks all active traversal scaffolding blocks across the world.
	 *
	 * @param world The server world to clear.
	 */
	public void clearAll(ServerWorld world) {
		if (world == null) {
			return;
		}
		Map<BlockPos, TraversalEntry> map = this.scaffoldingByDimension.remove(world.getRegistryKey());
		if (map == null || map.isEmpty()) {
			return;
		}

		for (BlockPos pos : map.keySet()) {
			if (world.getBlockState(pos).isOf(Blocks.SCAFFOLDING)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				world.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SCAFFOLDING.getDefaultState()),
					pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
					6, 0.2D, 0.2D, 0.2D, 0.05D
				);
			}
		}
	}

	/**
	 * Internal data container holding tracking metadata for an individual traversal scaffold.
	 */
	public static class TraversalEntry {
		private final BlockPos pos;
		private final long createdTick;
		private long expiryTick;

		public TraversalEntry(BlockPos pos, long createdTick, long expiryTick) {
			this.pos = pos;
			this.createdTick = createdTick;
			this.expiryTick = expiryTick;
		}

		public BlockPos getPos() {
			return this.pos;
		}

		public long getCreatedTick() {
			return this.createdTick;
		}

		public long getExpiryTick() {
			return this.expiryTick;
		}

		public void setExpiryTick(long expiryTick) {
			this.expiryTick = expiryTick;
		}
	}
}
