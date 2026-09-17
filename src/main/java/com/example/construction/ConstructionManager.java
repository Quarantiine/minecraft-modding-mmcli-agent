package com.example.construction;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.example.network.SyncConstructionSessionPayload;
import com.example.network.EndConstructionSessionPayload;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

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
	 * Validates whether a block state is indestructible (hardness < 0.0F or Blocks.BEDROCK).
	 * Indestructible blocks can never be dismantled or broken by minions.
	 *
	 * @param state The block state to evaluate.
	 * @param world The world instance (may be null).
	 * @param pos   The world block position (may be null).
	 * @return True if indestructible or bedrock.
	 */
	public static boolean isIndestructible(BlockState state, World world, BlockPos pos) {
		return ConstructionSession.isIndestructible(state, world, pos);
	}

	/**
	 * Determines whether the block at the specified position in the world is indestructible or bedrock.
	 *
	 * @param world The world instance.
	 * @param pos   Position to check.
	 * @return True if indestructible or bedrock.
	 */
	public static boolean isIndestructibleAt(World world, BlockPos pos) {
		return ConstructionSession.isIndestructible(world, pos);
	}

	/**
	 * Validates whether a block at the given position can be safely dismantled by minions.
	 * Returns false if the block is air, bedrock, or has negative hardness.
	 *
	 * @param world The server world.
	 * @param pos   Position to dismantle.
	 * @return True if safe and valid to dismantle.
	 */
	public static boolean canDismantleBlock(World world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		BlockState state = world.getBlockState(pos);
		return !state.isAir() && !isIndestructible(state, world, pos);
	}

	/**
	 * Counts how many blocks in the specified blueprint at the anchor position are safe to dismantle
	 * (strictly excluding bedrock and indestructible blocks).
	 *
	 * @param world     The world instance.
	 * @param anchorPos Origin anchor position.
	 * @param blueprint Structure blueprint.
	 * @return Count of dismantleable blocks.
	 */
	public static int countDismantleableBlocks(World world, BlockPos anchorPos, StructureBlueprint blueprint) {
		if (blueprint == null || anchorPos == null) {
			return 0;
		}
		int count = 0;
		for (BlueprintBlock bpBlock : blueprint.getBlocks()) {
			BlockPos targetPos = anchorPos.add(bpBlock.offset().getX(), bpBlock.offset().getY(), bpBlock.offset().getZ());
			if (isIndestructible(bpBlock.state(), world, targetPos)) {
				continue;
			}
			if (world != null) {
				BlockState worldState = world.getBlockState(targetPos);
				if (worldState.isAir() || isIndestructible(worldState, world, targetPos)) {
					continue;
				}
			}
			count++;
		}
		return count;
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

		// Check if an active session already exists at this exact anchor or overlapping bounding box
		ConstructionSession existing = this.sessionsByAnchor.get(immutableAnchor);
		if (existing != null && existing.isActive()) {
			existing.cancel();
			removeSession(existing);
			EndConstructionSessionPayload endPayload = new EndConstructionSessionPayload(existing.getId());
			for (ServerPlayerEntity p : world.getPlayers()) {
				ServerPlayNetworking.send(p, endPayload);
			}
		}

		// Cancel any previous session from the same owner that overlaps the new blueprint bounds
		BlockBox newBox = blueprint.getBoundingBox().offset(immutableAnchor.getX(), immutableAnchor.getY(), immutableAnchor.getZ());
		List<ConstructionSession> ownerSessions = this.sessionsByOwner.get(owner.getUuid());
		if (ownerSessions != null) {
			for (ConstructionSession s : new ArrayList<>(ownerSessions)) {
				if (s.isActive() && (s.getAnchorPos().equals(immutableAnchor) || s.getWorldBoundingBox().intersects(newBox))) {
					s.cancel();
					removeSession(s);
					EndConstructionSessionPayload endPayload = new EndConstructionSessionPayload(s.getId());
					for (ServerPlayerEntity p : world.getPlayers()) {
						ServerPlayNetworking.send(p, endPayload);
					}
				}
			}
		}

		boolean creative = owner.getAbilities().creativeMode;
		ConstructionSession session = new ConstructionSession(
			owner.getUuid(),
			world.getRegistryKey(),
			immutableAnchor,
			blueprint,
			creative,
			world.getTime(),
			mode,
			world
		);

		this.activeSessions.put(session.getId(), session);
		this.sessionsByAnchor.put(immutableAnchor, session);
		this.sessionsByOwner.computeIfAbsent(owner.getUuid(), k -> new ArrayList<>()).add(session);

		// Synchronize persistent active blueprint wireframe with tracking clients
		SyncConstructionSessionPayload syncPayload = new SyncConstructionSessionPayload(
			session.getId(),
			immutableAnchor,
			blueprint.getId(),
			blueprint.getRotationIndex(),
			session.isDismantle()
		);
		for (ServerPlayerEntity p : world.getPlayers()) {
			ServerPlayNetworking.send(p, syncPayload);
		}

		if (session.isDismantle()) {
			if (session.getTotalBlocks() == 0) {
				owner.sendMessage(
					Text.literal("§e⚠ No dismantleable blocks found for §f" + blueprint.getName() +
						" §e(all blocks are indestructible bedrock or already cleared).§r"),
					false
				);
				completeSession(session, world);
				return session;
			}

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
					"§c(§e" + session.getTotalBlocks() + " blocks to dismantle§c)§r"),
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

		// Mobilize nearby stationed builder minions within 64 blocks of the new construction/deconstruction blueprint
		Box mobilizationBox = new Box(
			immutableAnchor.getX() - 64.0D, immutableAnchor.getY() - 32.0D, immutableAnchor.getZ() - 64.0D,
			immutableAnchor.getX() + 64.0D, immutableAnchor.getY() + 32.0D, immutableAnchor.getZ() + 64.0D
		);
		List<com.example.entity.custom.MinionEntity> stationedBuilders = world.getEntitiesByClass(
			com.example.entity.custom.MinionEntity.class,
			mobilizationBox,
			m -> m.isAlive() && m.isTamed() && owner.getUuid().equals(m.getOwnerUuid()) && m.getRole() == com.example.entity.custom.MinionRole.BUILDER
		);
		for (com.example.entity.custom.MinionEntity builder : stationedBuilders) {
			if (builder.isSitting() || builder.getGuardAnchorPos() != null) {
				builder.setSitting(false);
				builder.setGuardAnchorPos(null);
			}
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

		removeSession(session);

		// Inform tracking clients that the session has completed so persistent wireframe outline is removed
		EndConstructionSessionPayload endPayload = new EndConstructionSessionPayload(session.getId());
		for (ServerPlayerEntity p : world.getPlayers()) {
			ServerPlayNetworking.send(p, endPayload);
		}

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

		// Signal all builder minions working nearby to complete, station at perimeter waypoints, and egress structure
		Box searchBox = new Box(box.getMinX() - 12, box.getMinY() - 6, box.getMinZ() - 12, box.getMaxX() + 12, box.getMaxY() + 10, box.getMaxZ() + 12);
		List<com.example.entity.custom.MinionEntity> nearbyMinions = world.getEntitiesByClass(
			com.example.entity.custom.MinionEntity.class,
			searchBox,
			m -> m.isAlive() && (session.getOwnerUuid() == null || session.getOwnerUuid().equals(m.getOwnerUuid()))
		);

		List<BlockPos> perimeterWaypoints = new ArrayList<>();
		if (!session.isDismantle()) {
			int minX = box.getMinX();
			int maxX = box.getMaxX();
			int minZ = box.getMinZ();
			int maxZ = box.getMaxZ();
			int baseY = box.getMinY();

			// 1. South perimeter (Front / maxZ + 2): West to East
			for (int x = minX; x <= maxX; x++) {
				perimeterWaypoints.add(findSafePerimeterGround(world, new BlockPos(x, baseY, maxZ + 2)));
			}
			// 2. East flank (maxX + 2): South to North
			for (int z = maxZ + 1; z >= minZ - 1; z--) {
				perimeterWaypoints.add(findSafePerimeterGround(world, new BlockPos(maxX + 2, baseY, z)));
			}
			// 3. North perimeter (Back / minZ - 2): East to West
			for (int x = maxX; x >= minX; x--) {
				perimeterWaypoints.add(findSafePerimeterGround(world, new BlockPos(x, baseY, minZ - 2)));
			}
			// 4. West flank (minX - 2): North to South
			for (int z = minZ - 1; z <= maxZ + 1; z++) {
				perimeterWaypoints.add(findSafePerimeterGround(world, new BlockPos(minX - 2, baseY, z)));
			}
		}

		int startIdx = 0;
		if (!perimeterWaypoints.isEmpty()) {
			double bestDistSq = Double.MAX_VALUE;
			for (int i = 0; i < perimeterWaypoints.size(); i++) {
				double d = perimeterWaypoints.get(i).getSquaredDistance(anchor);
				if (d < bestDistSq) {
					bestDistSq = d;
					startIdx = i;
				}
			}
		}

		java.util.Set<BlockPos> assignedWaypoints = new java.util.HashSet<>();
		int totalMinions = nearbyMinions.size();
		int minionIdx = 0;

		for (com.example.entity.custom.MinionEntity minion : nearbyMinions) {
			minion.setActivelyBuilding(false);

			if (!session.isDismantle() && !perimeterWaypoints.isEmpty()) {
				int ringSize = perimeterWaypoints.size();
				int targetIdx = (startIdx + (int) Math.round(minionIdx * ((double) ringSize / (double) Math.max(1, totalMinions)))) % ringSize;
				int probe = 0;
				BlockPos waypoint = perimeterWaypoints.get(targetIdx);
				while (assignedWaypoints.contains(waypoint) && probe < ringSize) {
					probe++;
					targetIdx = (targetIdx + 1) % ringSize;
					waypoint = perimeterWaypoints.get(targetIdx);
				}
				assignedWaypoints.add(waypoint);
				minionIdx++;

				// Golden beacon beam at perimeter waypoint
				double wx = waypoint.getX() + 0.5D;
				double wz = waypoint.getZ() + 0.5D;
				for (int y = 0; y <= 5; y++) {
					double wy = waypoint.getY() + 0.2D + (y * 0.75D);
					world.spawnParticles(ParticleTypes.END_ROD, wx, wy, wz, 3, 0.08, 0.1, 0.08, 0.01);
					world.spawnParticles(ParticleTypes.GLOW, wx, wy, wz, 4, 0.12, 0.15, 0.12, 0.02);
				}
				world.playSound(null, wx, waypoint.getY(), wz, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.3F);

				// Station minion at this perimeter waypoint
				minion.requestTeleport(wx, waypoint.getY(), wz);
				minion.setSelected(false);
				minion.setSitting(true);
				minion.setGuardAnchorPos(waypoint);
				minion.startEgressFromStructure(box, anchor, Vec3d.ofBottomCenter(waypoint));
				minion.finishBuildingEgress(world);
			} else {
				if (minion.isInsideStructure(box)) {
					minion.startEgressFromStructure(box, anchor, null);
				} else {
					if (minion.isArcaneLevitating()) {
						minion.setArcaneLevitating(false);
					}
					minion.getNavigation().stop();
				}
			}
		}

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

	private BlockPos findSafePerimeterGround(ServerWorld world, BlockPos pos) {
		for (int dy = 3; dy >= -4; dy--) {
			BlockPos check = pos.add(0, dy, 0);
			BlockPos ground = check.down();
			if (world.getBlockState(ground).isSolidBlock(world, ground)
					&& !world.getBlockState(check).isSolidBlock(world, check)
					&& !world.getBlockState(check.up()).isSolidBlock(world, check.up())) {
				return check;
			}
		}
		return pos;
	}

	/**
	 * Cancels all active construction or deconstruction sessions belonging to the specified owner UUID.
	 *
	 * @param ownerUuid The UUID of the player.
	 * @param world     The server world.
	 */
	public void cancelActiveSessionsForOwner(UUID ownerUuid, ServerWorld world) {
		if (ownerUuid == null || world == null) {
			return;
		}
		List<ConstructionSession> ownerSessions = this.sessionsByOwner.get(ownerUuid);
		if (ownerSessions != null) {
			for (ConstructionSession session : new ArrayList<>(ownerSessions)) {
				if (session.isActive()) {
					cancelSession(session.getId(), world);
				}
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
			session.cancel();
			removeSession(session);

			EndConstructionSessionPayload endPayload = new EndConstructionSessionPayload(sessionId);
			for (ServerPlayerEntity p : world.getPlayers()) {
				ServerPlayNetworking.send(p, endPayload);
			}

			BlockPos anchor = session.getAnchorPos();
			BlockBox box = session.getWorldBoundingBox();
			Box searchBox = new Box(box.getMinX() - 12, box.getMinY() - 6, box.getMinZ() - 12, box.getMaxX() + 12, box.getMaxY() + 10, box.getMaxZ() + 12);
			List<com.example.entity.custom.MinionEntity> nearbyMinions = world.getEntitiesByClass(
				com.example.entity.custom.MinionEntity.class,
				searchBox,
				m -> m.isAlive() && (session.getOwnerUuid() == null || session.getOwnerUuid().equals(m.getOwnerUuid()))
			);
			for (com.example.entity.custom.MinionEntity minion : nearbyMinions) {
				minion.setActivelyBuilding(false);
				if (minion.isInsideStructure(box)) {
					minion.startEgressFromStructure(box, anchor, null);
				} else {
					if (minion.isArcaneLevitating()) {
						minion.setArcaneLevitating(false);
					}
					minion.getNavigation().stop();
				}
			}

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

	/**
	 * Cancels all active construction and deconstruction sessions belonging to the specified owner,
	 * broadcasting termination packets to clients and destroying visual wireframes.
	 *
	 * @param ownerUuid The UUID of the session owner.
	 * @param world     The server world.
	 */
	public void cancelSessionsForOwner(UUID ownerUuid, ServerWorld world) {
		if (ownerUuid == null || world == null) {
			return;
		}
		List<ConstructionSession> ownerList = this.sessionsByOwner.get(ownerUuid);
		if (ownerList == null || ownerList.isEmpty()) {
			return;
		}
		List<UUID> toCancel = new ArrayList<>();
		for (ConstructionSession session : ownerList) {
			if (session.isActive()) {
				toCancel.add(session.getId());
			}
		}
		for (UUID sessionId : toCancel) {
			cancelSession(sessionId, world);
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
			// Builder role participates in both BUILD and DISMANTLE sessions
			if (role != null && role != MinionRole.BUILDER) {
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
	 * Checks whether a minion thrall is actively engaged in an ongoing construction or deconstruction
	 * session by holding a claimed building task.
	 *
	 * @param minionUuid The minion's unique ID.
	 * @return True if actively engaged in an active session.
	 */
	public boolean isMinionEngagedInConstruction(UUID minionUuid) {
		if (minionUuid == null) {
			return false;
		}
		for (ConstructionSession session : this.activeSessions.values()) {
			if (session.isActive() && session.isMinionEngaged(minionUuid)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether a minion thrall is actively engaged in construction or situated within proximity
	 * of an active construction/deconstruction session owned by their master.
	 *
	 * @param minion The minion thrall to evaluate.
	 * @return True if actively engaged or near an active construction site for their role.
	 */
	public boolean isMinionEngagedInConstruction(MinionEntity minion) {
		return isMinionEngagedInConstruction(minion, 48.0D);
	}

	/**
	 * Checks whether a minion thrall is actively engaged in construction or situated within the specified
	 * distance of an active construction/deconstruction session owned by their master.
	 *
	 * @param minion      The minion thrall to evaluate.
	 * @param maxDistance Maximum proximity distance in blocks.
	 * @return True if actively engaged or within proximity of a compatible session.
	 */
	public boolean isMinionEngagedInConstruction(MinionEntity minion, double maxDistance) {
		if (minion == null) {
			return false;
		}
		if (isMinionEngagedInConstruction(minion.getUuid())) {
			return true;
		}
		MinionRole role = minion.getRole();
		if (role == MinionRole.BUILDER
				&& minion.getWorld() instanceof ServerWorld serverWorld
				&& minion.getOwnerUuid() != null) {
			return isMinionNearActiveSession(serverWorld, minion.getBlockPos(), minion.getOwnerUuid(), maxDistance, role);
		}
		return false;
	}

	/**
	 * Checks whether a coordinate is within proximity of an active construction or deconstruction session
	 * for the specified owner, either by anchor distance or structure bounding box proximity.
	 *
	 * @param world       The server world.
	 * @param pos         Position to test.
	 * @param ownerUuid   Master player's UUID.
	 * @param maxDistance Maximum distance in blocks.
	 * @param role        Minion role (BUILDER or MINER).
	 * @return True if an active compatible session is nearby.
	 */
	public boolean isMinionNearActiveSession(
		ServerWorld world,
		BlockPos pos,
		UUID ownerUuid,
		double maxDistance,
		MinionRole role
	) {
		if (ownerUuid == null) {
			return false;
		}
		List<ConstructionSession> ownerSessions = this.sessionsByOwner.get(ownerUuid);
		if (ownerSessions == null || ownerSessions.isEmpty()) {
			return false;
		}

		double maxDistSq = maxDistance * maxDistance;
		for (ConstructionSession session : ownerSessions) {
			if (!session.isActive() || !session.getDimension().equals(world.getRegistryKey())) {
				continue;
			}
			if (role != null && role != MinionRole.BUILDER) {
				continue;
			}

			// Check anchor distance
			if (pos.getSquaredDistance(session.getAnchorPos()) <= maxDistSq) {
				return true;
			}

			// Check bounding box expanded by maxDistance
			BlockBox box = session.getWorldBoundingBox();
			if (pos.getX() >= box.getMinX() - maxDistance && pos.getX() <= box.getMaxX() + maxDistance
					&& pos.getY() >= box.getMinY() - maxDistance && pos.getY() <= box.getMaxY() + maxDistance
					&& pos.getZ() >= box.getMinZ() - maxDistance && pos.getZ() <= box.getMaxZ() + maxDistance) {
				return true;
			}
		}
		return false;
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

		// 2. Every 20 ticks (1 second): check active dismantle sessions for full clearance and render particles
		if (currentTick % 20L == 0L) {
			List<ConstructionSession> finishedDismantle = new ArrayList<>();
			for (ConstructionSession session : this.activeSessions.values()) {
				if (session.isActive() && session.isDismantle() && session.getDimension().equals(world.getRegistryKey())) {
					if (!session.hasRemainingBlocksInWorld(world)) {
						finishedDismantle.add(session);
					}
				}
			}
			for (ConstructionSession session : finishedDismantle) {
				completeSession(session, world);
			}
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
