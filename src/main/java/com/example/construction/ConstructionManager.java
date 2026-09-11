package com.example.construction;

import com.example.blueprint.StructureBlueprint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

/**
 * Server-side singleton orchestrator managing all active {@link ConstructionSession}s.
 * Handles blueprint session instantiation, minion task dispatching, periodic
 * hologram particle projections, and session completion ceremonies.
 */
public class ConstructionManager {

	private static final ConstructionManager INSTANCE = new ConstructionManager();

	private final Map<UUID, ConstructionSession> activeSessions = new ConcurrentHashMap<>();
	private final Map<BlockPos, ConstructionSession> sessionsByAnchor = new ConcurrentHashMap<>();
	private final Map<UUID, List<ConstructionSession>> sessionsByOwner = new ConcurrentHashMap<>();

	private ConstructionManager() {}

	public static ConstructionManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Initiates a new construction session in BUILD mode anchored at the specified world coordinate.
	 *
	 * @param world     The server world where the structure will be built.
	 * @param anchorPos The base world coordinates for the structure.
	 * @param blueprint The blueprint to construct.
	 * @param owner     The player commissioning the construction.
	 * @return The newly initiated ConstructionSession.
	 */
	public ConstructionSession startSession(
		ServerWorld world,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		PlayerEntity owner
	) {
		return startSession(world, anchorPos, blueprint, owner, ConstructionSession.SessionMode.BUILD);
	}

	/**
	 * Initiates a new deconstruction session in DISMANTLE mode anchored at the specified world coordinate.
	 * Tasks will be executed in top-down reverse topological order, breaking blocks and recovering resources.
	 *
	 * @param world     The server world where the structure will be dismantled.
	 * @param anchorPos The base world coordinates for the structure.
	 * @param blueprint The blueprint to dismantle.
	 * @param owner     The player commissioning the deconstruction.
	 * @return The newly initiated ConstructionSession in DISMANTLE mode.
	 */
	public ConstructionSession startDismantleSession(
		ServerWorld world,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		PlayerEntity owner
	) {
		return startSession(world, anchorPos, blueprint, owner, ConstructionSession.SessionMode.DISMANTLE);
	}

	/**
	 * Initiates a new construction or deconstruction session anchored at the specified world coordinate.
	 *
	 * @param world     The server world where the session takes place.
	 * @param anchorPos The base world coordinates for the structure.
	 * @param blueprint The blueprint to construct or dismantle.
	 * @param owner     The player commissioning the task.
	 * @param mode      The operational mode (BUILD or DISMANTLE).
	 * @return The newly initiated ConstructionSession.
	 */
	public ConstructionSession startSession(
		ServerWorld world,
		BlockPos anchorPos,
		StructureBlueprint blueprint,
		PlayerEntity owner,
		ConstructionSession.SessionMode mode
	) {
		Objects.requireNonNull(world, "world cannot be null");
		Objects.requireNonNull(anchorPos, "anchorPos cannot be null");
		Objects.requireNonNull(blueprint, "blueprint cannot be null");
		Objects.requireNonNull(owner, "owner cannot be null");

		BlockPos immutableAnchor = anchorPos.toImmutable();

		// Check if an active session already exists at this exact anchor
		ConstructionSession existing = this.sessionsByAnchor.get(immutableAnchor);
		if (existing != null && existing.isActive()) {
			existing.clearScaffolding(world);
			existing.cancel();
			removeSession(existing);
		}

		boolean creative = owner.getAbilities().creativeMode;
		ConstructionSession session = new ConstructionSession(
			owner.getUuid(),
			world.getRegistryKey(),
			immutableAnchor,
			blueprint,
			creative,
			world.getTime(),
			mode
		);

		this.activeSessions.put(session.getId(), session);
		this.sessionsByAnchor.put(immutableAnchor, session);
		this.sessionsByOwner.computeIfAbsent(owner.getUuid(), k -> new ArrayList<>()).add(session);

		if (session.isDismantle()) {
			// Deconstruction initiation feedback
			world.playSound(
				null,
				immutableAnchor.getX() + 0.5,
				immutableAnchor.getY() + 0.5,
				immutableAnchor.getZ() + 0.5,
				SoundEvents.BLOCK_ANVIL_DESTROY,
				SoundCategory.BLOCKS,
				1.0F,
				1.2F
			);

			world.spawnParticles(
				ParticleTypes.FLAME,
				immutableAnchor.getX() + 0.5,
				immutableAnchor.getY() + 1.0,
				immutableAnchor.getZ() + 0.5,
				40,
				0.5,
				1.0,
				0.5,
				0.1
			);

			owner.sendMessage(
				Text.literal("§c✦ Initiated deconstruction: §f" + blueprint.getName() + " §cat §e[" +
					immutableAnchor.getX() + ", " + immutableAnchor.getY() + ", " + immutableAnchor.getZ() + "] " +
					"§c(§e" + blueprint.getBlockCount() + " blocks to dismantle§c)§r"),
				false
			);
		} else {
			// Construction start feedback
			world.playSound(
				null,
				immutableAnchor.getX() + 0.5,
				immutableAnchor.getY() + 0.5,
				immutableAnchor.getZ() + 0.5,
				SoundEvents.BLOCK_BEACON_ACTIVATE,
				SoundCategory.BLOCKS,
				1.2F,
				1.1F
			);

			world.spawnParticles(
				ParticleTypes.ENCHANT,
				immutableAnchor.getX() + 0.5,
				immutableAnchor.getY() + 1.0,
				immutableAnchor.getZ() + 0.5,
				40,
				0.5,
				1.0,
				0.5,
				0.5
			);

			owner.sendMessage(
				Text.literal("§6✦ Initiated construction: §f" + blueprint.getName() + " §6at §e[" +
					immutableAnchor.getX() + ", " + immutableAnchor.getY() + ", " + immutableAnchor.getZ() + "] " +
					"§6(§a" + blueprint.getBlockCount() + " blocks§6)§r"),
				false
			);
		}

		return session;
	}

	/**
	 * Concludes a construction session upon all tasks being finished.
	 * Plays triumphant fanfare and emits celebratory particles.
	 *
	 * @param session The completed session.
	 * @param world   The server world where construction occurred.
	 */
	public void completeSession(ConstructionSession session, ServerWorld world) {
		Objects.requireNonNull(session, "session cannot be null");
		Objects.requireNonNull(world, "world cannot be null");

		// Automatically despawn all temporary scaffolding erected during construction
		session.clearScaffolding(world);

		removeSession(session);

		BlockPos anchor = session.getAnchorPos();
		BlockBox box = session.getWorldBoundingBox();

		// Triumphant sound at structure center
		double centerX = (box.getMinX() + box.getMaxX()) / 2.0;
		double centerY = (box.getMinY() + box.getMaxY()) / 2.0;
		double centerZ = (box.getMinZ() + box.getMaxZ()) / 2.0;

		world.playSound(
			null,
			centerX,
			centerY,
			centerZ,
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
			SoundCategory.PLAYERS,
			1.5F,
			1.0F
		);

		// Grand celebratory particle eruption across structure perimeter
		world.spawnParticles(
			ParticleTypes.HAPPY_VILLAGER,
			centerX,
			centerY,
			centerZ,
			80,
			(box.getMaxX() - box.getMinX()) / 2.0,
			(box.getMaxY() - box.getMinY()) / 2.0,
			(box.getMaxZ() - box.getMinZ()) / 2.0,
			0.2
		);

		world.spawnParticles(
			ParticleTypes.TOTEM_OF_UNDYING,
			centerX,
			centerY + 1.0,
			centerZ,
			50,
			1.0,
			2.0,
			1.0,
			0.3
		);

		// Notify owner player if online
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.getOwnerUuid());
		if (player != null) {
			if (session.isDismantle()) {
				player.sendMessage(
					Text.literal("§a✔ Deconstruction Complete! §f" + session.getBlueprint().getName() +
						" §ahas been successfully dismantled and cleared by your minions!§r"),
					false
				);
				player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
			} else {
				player.sendMessage(
					Text.literal("§a✔ Construction Complete! §f" + session.getBlueprint().getName() +
						" §ahas been successfully raised by your minions!§r"),
					false
				);
				player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
			}
		}
	}

	/**
	 * Cancels an active construction session by session ID.
	 *
	 * @param sessionId Unique session ID.
	 * @param world     Server world.
	 */
	public void cancelSession(UUID sessionId, ServerWorld world) {
		ConstructionSession session = this.activeSessions.get(sessionId);
		if (session != null) {
			session.clearScaffolding(world);
			session.cancel();
			removeSession(session);

			BlockPos anchor = session.getAnchorPos();
			world.playSound(
				null,
				anchor.getX() + 0.5,
				anchor.getY() + 0.5,
				anchor.getZ() + 0.5,
				SoundEvents.BLOCK_ANVIL_DESTROY,
				SoundCategory.BLOCKS,
				0.8F,
				0.9F
			);
		}
	}

	private void removeSession(ConstructionSession session) {
		this.activeSessions.remove(session.getId());
		this.sessionsByAnchor.remove(session.getAnchorPos());
		List<ConstructionSession> ownerList = this.sessionsByOwner.get(session.getOwnerUuid());
		if (ownerList != null) {
			ownerList.remove(session);
			if (ownerList.isEmpty()) {
				this.sessionsByOwner.remove(session.getOwnerUuid());
			}
		}
	}

	/**
	 * Looks up an active session by its unique ID.
	 */
	public Optional<ConstructionSession> getSession(UUID sessionId) {
		return Optional.ofNullable(this.activeSessions.get(sessionId));
	}

	/**
	 * Looks up an active session anchored at the specified world coordinate.
	 */
	public Optional<ConstructionSession> getSessionAt(BlockPos anchorPos) {
		return Optional.ofNullable(this.sessionsByAnchor.get(anchorPos));
	}

	/**
	 * Returns all active sessions owned by the specified player UUID.
	 */
	public List<ConstructionSession> getSessionsForOwner(UUID ownerUuid) {
		List<ConstructionSession> list = this.sessionsByOwner.get(ownerUuid);
		if (list == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(list);
	}

	/**
	 * Finds the nearest active construction session owned by the minion's master within the specified range.
	 *
	 * @param world       Current server world.
	 * @param minionPos   Current minion coordinates.
	 * @param ownerUuid   Master player's UUID.
	 * @param maxDistance Maximum search distance in blocks.
	 * @return Optional containing nearest active session, or empty if none in range.
	 */
	public Optional<ConstructionSession> findNearestSessionForMinion(
		ServerWorld world,
		BlockPos minionPos,
		UUID ownerUuid,
		double maxDistance
	) {
		return findNearestSessionForMinion(world, minionPos, ownerUuid, maxDistance, null);
	}

	/**
	 * Finds the nearest active construction or deconstruction session owned by the minion's master
	 * within the specified range, filtering by minion role compatibility.
	 *
	 * @param world       Current server world.
	 * @param minionPos   Current minion coordinates.
	 * @param ownerUuid   Master player's UUID.
	 * @param maxDistance Maximum search distance in blocks.
	 * @param role        The requesting minion's role, or null for any session.
	 * @return Optional containing nearest active session, or empty if none in range.
	 */
	public Optional<ConstructionSession> findNearestSessionForMinion(
		ServerWorld world,
		BlockPos minionPos,
		UUID ownerUuid,
		double maxDistance,
		com.example.entity.custom.MinionRole role
	) {
		if (ownerUuid == null) {
			return Optional.empty();
		}

		List<ConstructionSession> ownerSessions = this.sessionsByOwner.get(ownerUuid);
		if (ownerSessions == null || ownerSessions.isEmpty()) {
			return Optional.empty();
		}

		double maxDistSq = maxDistance * maxDistance;
		ConstructionSession nearest = null;
		double bestDistSq = Double.MAX_VALUE;

		for (ConstructionSession session : ownerSessions) {
			if (!session.isActive() || !session.getDimension().equals(world.getRegistryKey())) {
				continue;
			}
			// Miners only participate in DISMANTLE sessions
			if (role == com.example.entity.custom.MinionRole.MINER && !session.isDismantle()) {
				continue;
			}
			double distSq = minionPos.getSquaredDistance(session.getAnchorPos());
			if (distSq <= maxDistSq && distSq < bestDistSq) {
				bestDistSq = distSq;
				nearest = session;
			}
		}

		return Optional.ofNullable(nearest);
	}

	/**
	 * Ticks the construction manager. Invoked from ServerTickEvents.END_WORLD_TICK.
	 * Handles stale task reclamation, hologram bounding-box projection, and collection pruning.
	 *
	 * @param world The server world currently ticking.
	 */
	public void tick(ServerWorld world) {
		long currentTick = world.getTime();

		// 1. Every 100 ticks (5 seconds): reclaim stale worker task claims (15-second timeout)
		if (currentTick % 100L == 0L) {
			for (ConstructionSession session : this.activeSessions.values()) {
				if (session.isActive() && session.getDimension().equals(world.getRegistryKey())) {
					session.cleanStaleClaims(currentTick, 300L);
				}
			}
		}

		// 2. Every 20 ticks (1 second): render holographic blueprint boundary particles
		if (currentTick % 20L == 0L) {
			renderHologramParticles(world);
		}

		// 3. Prune dead or cancelled sessions
		pruneInactiveSessions(world);
	}

	private void renderHologramParticles(ServerWorld world) {
		for (ConstructionSession session : this.activeSessions.values()) {
			if (!session.isActive() || !session.getDimension().equals(world.getRegistryKey())) {
				continue;
			}

			BlockBox box = session.getWorldBoundingBox();
			BlockPos anchor = session.getAnchorPos();

			// Anchor point focal aura
			world.spawnParticles(
				session.isDismantle() ? ParticleTypes.FLAME : ParticleTypes.PORTAL,
				anchor.getX() + 0.5,
				anchor.getY() + 0.2,
				anchor.getZ() + 0.5,
				4,
				0.2,
				0.1,
				0.2,
				0.02
			);

			// Holographic boundary corners
			int minX = box.getMinX();
			int minY = box.getMinY();
			int minZ = box.getMinZ();
			int maxX = box.getMaxX() + 1;
			int maxY = box.getMaxY() + 1;
			int maxZ = box.getMaxZ() + 1;

			// Spawn glow / flame particles at the 8 bounding box corners
			spawnHologramCorner(world, minX, minY, minZ, session.isDismantle());
			spawnHologramCorner(world, minX, minY, maxZ, session.isDismantle());
			spawnHologramCorner(world, minX, maxY, minZ, session.isDismantle());
			spawnHologramCorner(world, minX, maxY, maxZ, session.isDismantle());
			spawnHologramCorner(world, maxX, minY, minZ, session.isDismantle());
			spawnHologramCorner(world, maxX, minY, maxZ, session.isDismantle());
			spawnHologramCorner(world, maxX, maxY, minZ, session.isDismantle());
			spawnHologramCorner(world, maxX, maxY, maxZ, session.isDismantle());

			// Accentuate active building/dismantling tasks
			int highlighted = 0;
			for (ConstructionTask task : session.getTasks()) {
				if (task.isClaimed()) {
					BlockPos p = task.getWorldPos();
					world.spawnParticles(
						session.isDismantle() ? ParticleTypes.CRIT : ParticleTypes.WAX_ON,
						p.getX() + 0.5,
						p.getY() + 0.5,
						p.getZ() + 0.5,
						2,
						0.1,
						0.1,
						0.1,
						0.02
					);
					highlighted++;
					if (highlighted > 8) {
						break;
					}
				}
			}
		}
	}

	private void spawnHologramCorner(ServerWorld world, double x, double y, double z, boolean isDismantle) {
		world.spawnParticles(isDismantle ? ParticleTypes.SMALL_FLAME : ParticleTypes.GLOW, x, y, z, 1, 0.02, 0.02, 0.02, 0.01);
	}

	private void spawnHologramCorner(ServerWorld world, double x, double y, double z) {
		spawnHologramCorner(world, x, y, z, false);
	}

	private void pruneInactiveSessions(ServerWorld world) {
		Iterator<Map.Entry<UUID, ConstructionSession>> iter = this.activeSessions.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<UUID, ConstructionSession> entry = iter.next();
			ConstructionSession session = entry.getValue();
			if (!session.isActive()) {
				if (session.getDimension().equals(world.getRegistryKey())) {
					session.clearScaffolding(world);
				}
				this.sessionsByAnchor.remove(session.getAnchorPos());
				List<ConstructionSession> list = this.sessionsByOwner.get(session.getOwnerUuid());
				if (list != null) {
					list.remove(session);
					if (list.isEmpty()) {
						this.sessionsByOwner.remove(session.getOwnerUuid());
					}
				}
				iter.remove();
			}
		}
	}
}
