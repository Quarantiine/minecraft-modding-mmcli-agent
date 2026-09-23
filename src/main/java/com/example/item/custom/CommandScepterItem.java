package com.example.item.custom;

import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.component.CommandMode;
import com.example.component.MiningMode;
import com.example.component.ModDataComponents;
import com.example.component.SquadGroup;
import com.example.construction.ConstructionManager;
import com.example.construction.ConstructionSession;
import com.example.entity.ModEntities;
import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.patrol.PatrolRoute;
import com.example.patrol.PatrolRouteManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.InventoryOwner;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The Loki Command Scepter: A high-tier tactical relic allowing the player to orchestrate
 * minion thralls and multiblock construction.
 *
 * Capabilities:
 * - Sneak + Right-Click: Cycles operating {@link CommandMode} with pitch-shifted chime audio feedback.
 * - BUILD Mode:
 *     - Right-Click Ground: Anchors a new multiblock construction session at the clicked block face.
 *     - Right-Click Air / Sneak + Left-Click: Cycles active architectural blueprint / rotates structure 90°.
 * - MINE Mode:
 *     - Right-Click Ground / Block: Anchors a 3D area deconstruction & mining session, mining all destructible blocks top-to-bottom with zero air-mining.
 * - RECRUIT Mode:
 *     - Right-Click Living Mob: Transfigures target mob into an obedient {@link MinionEntity} thrall,
 *       preserving armor/equipment, binding owner UUID, and triggering arcane conversion VFX.
 * - FOLLOW / STAY Modes:
 *     - Right-Click Air or Ground: Broadcasts follow or hold-position commands to all owned minions within 64 blocks.
 * - Contextual Combat:
 *     - Quick-Tap Hostile: Focus-fires squad minions onto the target with war drums and crit particles.
 *     - Channeled Banner of Courage (Hold Right-Click): Projects a 90° forward sector; on release rallies/transfigures minions and launches a coordinated Mass Attack on enclosed hostiles.
 * - Tactical Panic Retreat (Keybind R):
 *     - Sounds a warning bell, clears combat targets, and recalls all minions back into ranked army lines.
 */
public class CommandScepterItem extends Item {

	public static final double MINION_COMMAND_RADIUS = 64.0D;

	@FunctionalInterface
	public interface ScreenOpener {
		void openScreen(PlayerEntity player, Hand hand, ItemStack stack);
	}

	public static ScreenOpener SCREEN_OPENER = null;

	@FunctionalInterface
	public interface CaptureModalOpener {
		void openCaptureModal(PlayerEntity player, Hand hand, ItemStack stack, BlockPos pos1, BlockPos pos2);
	}

	public static CaptureModalOpener CAPTURE_MODAL_OPENER = null;

	@FunctionalInterface
	public interface MineModalOpener {
		void openMineModal(PlayerEntity player, Hand hand, ItemStack stack, BlockPos pos1, BlockPos pos2);
	}

	public static MineModalOpener MINE_MODAL_OPENER = null;

	@FunctionalInterface
	public interface DesignCornerStepper {
		int stepCorner(BlockPos pos);
	}

	public static DesignCornerStepper DESIGN_CORNER_STEPPER = null;

	@FunctionalInterface
	public interface DesignCornerResetter {
		void resetCorners();
	}

	public static DesignCornerResetter DESIGN_CORNER_RESETTER = null;
	public static Runnable DESIGN_CLICK_CONSUMER = null;

	@FunctionalInterface
	public interface MineCornerStepper {
		int stepCorner(BlockPos pos);
	}

	public static MineCornerStepper MINE_CORNER_STEPPER = null;

	@FunctionalInterface
	public interface MineCornerResetter {
		void resetCorners();
	}

	public static MineCornerResetter MINE_CORNER_RESETTER = null;
	public static Runnable MINE_CLICK_CONSUMER = null;

	@FunctionalInterface
	public interface ClientTargetResolver {
		BlockHitResult getTarget(PlayerEntity player);
	}

	public static ClientTargetResolver CLIENT_TARGET_RESOLVER = null;

	@FunctionalInterface
	public interface ClientWaypointChecker {
		boolean isWaypoint(int routeId, BlockPos pos);
	}

	public static ClientWaypointChecker CLIENT_WAYPOINT_CHECKER = null;

	public static boolean isClientWaypoint(ItemStack stack, BlockPos pos) {
		if (CLIENT_WAYPOINT_CHECKER != null) {
			int routeId = getActivePatrolRoute(stack);
			return CLIENT_WAYPOINT_CHECKER.isWaypoint(routeId, pos);
		}
		return false;
	}

	public CommandScepterItem(Settings settings) {
		super(settings);
	}

	// -----------------------------------------------------------------------------------------
	// COMPONENT ACCESSORS & MODIFIERS
	// -----------------------------------------------------------------------------------------

	/**
	 * Resolves the active {@link CommandMode} stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The active CommandMode, defaulting to {@link CommandMode#FOLLOW}.
	 */
	public static CommandMode getMode(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.COMMAND_MODE, CommandMode.FOLLOW);
	}

	/**
	 * Sets the active {@link CommandMode} on the item stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param mode  The CommandMode to assign.
	 */
	public static void setMode(ItemStack stack, CommandMode mode) {
		stack.set(ModDataComponents.COMMAND_MODE, Objects.requireNonNull(mode, "mode cannot be null"));
	}

	/**
	 * Cycles to the next {@link CommandMode} in sequence, playing a pitch-shifted chime sound
	 * and projecting an action-bar overlay notification.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @return The newly assigned CommandMode.
	 */
	public static CommandMode cycleMode(ItemStack stack, PlayerEntity player, World world) {
		CommandMode nextMode = getMode(stack).next();
		setMode(stack, nextMode);

		world.playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.BLOCK_NOTE_BLOCK_CHIME,
			SoundCategory.PLAYERS,
			1.0F,
			nextMode.getPitch()
		);

		player.sendMessage(Text.literal("§6✦ Scepter Mode: §r" + nextMode.getFormattedName()), true);
		return nextMode;
	}

	/**
	 * Resolves the active target {@link SquadGroup} filter stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The active SquadGroup, defaulting to {@link SquadGroup#ALL}.
	 */
	public static SquadGroup getTargetSquad(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.TARGET_SQUAD, SquadGroup.ALL);
	}

	/**
	 * Sets the active target {@link SquadGroup} filter on the item stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param squad The SquadGroup filter to assign.
	 */
	public static void setTargetSquad(ItemStack stack, SquadGroup squad) {
		stack.set(ModDataComponents.TARGET_SQUAD, Objects.requireNonNull(squad, "squad cannot be null"));
	}

	/**
	 * Resolves the active {@link MiningMode} stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The active MiningMode, defaulting to {@link MiningMode#AREA}.
	 */
	public static MiningMode getMiningMode(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return MiningMode.AREA;
		return stack.getOrDefault(ModDataComponents.MINING_MODE, MiningMode.AREA);
	}

	/**
	 * Sets the active {@link MiningMode} on the item stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param mode  The MiningMode to assign.
	 */
	public static void setMiningMode(ItemStack stack, MiningMode mode) {
		if (stack == null || stack.isEmpty()) return;
		stack.set(ModDataComponents.MINING_MODE, Objects.requireNonNull(mode, "mining mode cannot be null"));
	}

	/**
	 * Cycles to the next {@link MiningMode} in sequence (DIRECT -> AREA -> DIRECT),
	 * playing audio feedback and projecting an action-bar overlay notification.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @return The newly assigned MiningMode.
	 */
	public static MiningMode cycleMiningMode(ItemStack stack, PlayerEntity player, World world) {
		MiningMode nextMode = getMiningMode(stack).next();
		setMiningMode(stack, nextMode);

		if (world != null && player != null) {
			world.playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(),
				SoundCategory.PLAYERS,
				1.0F,
				nextMode == MiningMode.AREA ? 1.4F : 1.0F
			);

			player.sendMessage(Text.literal("§6✦ Mining Sub-Mode: §r" + nextMode.getFormattedName()), true);
		}
		return nextMode;
	}

	/**
	 * Cycles to the next target {@link SquadGroup} filter channel in sequence,
	 * playing harp audio feedback and projecting an action-bar notification.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @return The newly assigned target SquadGroup.
	 */
	public static SquadGroup cycleTargetSquad(ItemStack stack, PlayerEntity player, World world) {
		SquadGroup nextSquad = getTargetSquad(stack).next();
		setTargetSquad(stack, nextSquad);

		world.playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.BLOCK_NOTE_BLOCK_HARP,
			SoundCategory.PLAYERS,
			1.0F,
			1.0F + (nextSquad.getId() * 0.15F)
		);

		player.sendMessage(Text.literal("§6✦ Target Squad: §r" + nextSquad.getFormattedName()), true);
		return nextSquad;
	}

	/**
	 * Resolves the active target {@link MinionRole} archetype stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The target MinionRole, or null if no archetype is selected.
	 */
	public static MinionRole getTargetRole(ItemStack stack) {
		return stack.get(ModDataComponents.TARGET_ROLE);
	}

	/**
	 * Sets or clears the active target {@link MinionRole} archetype on the item stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param role  The MinionRole archetype to assign, or null to clear.
	 */
	public static void setTargetRole(ItemStack stack, MinionRole role) {
		if (role == null) {
			stack.remove(ModDataComponents.TARGET_ROLE);
		} else {
			stack.set(ModDataComponents.TARGET_ROLE, role);
		}
	}

	/**
	 * Resolves the target role archetype from the player's held scepter.
	 *
	 * @param player The commanding player.
	 * @return The active target MinionRole archetype, or null if unassigned.
	 */
	public static MinionRole getHeldTargetRole(PlayerEntity player) {
		ItemStack stack = getHeldScepter(player);
		return stack.isEmpty() ? null : getTargetRole(stack);
	}

	/**
	 * Resolves the held Command Scepter ItemStack from player's main hand or off hand.
	 *
	 * @param player The commanding player.
	 * @return The held scepter ItemStack, or {@link ItemStack#EMPTY} if neither hand holds one.
	 */
	public static ItemStack getHeldScepter(PlayerEntity player) {
		if (player.getMainHandStack().getItem() instanceof CommandScepterItem) {
			return player.getMainHandStack();
		} else if (player.getOffHandStack().getItem() instanceof CommandScepterItem) {
			return player.getOffHandStack();
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Resolves the target squad filter from the player's held scepter, defaulting to {@link SquadGroup#ALL}.
	 *
	 * @param player The commanding player.
	 * @return The active target SquadGroup.
	 */
	public static SquadGroup getHeldTargetSquad(PlayerEntity player) {
		ItemStack stack = getHeldScepter(player);
		return stack.isEmpty() ? SquadGroup.ALL : getTargetSquad(stack);
	}

	// -----------------------------------------------------------------------------------------
	// DESIGN MODE SPATIAL CORNER SELECTION & COORDINATE TRACKING
	// -----------------------------------------------------------------------------------------

	/**
	 * Resolves the active DESIGN mode corner 1 (Pos1) stored on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The first corner BlockPos, or null if unassigned.
	 */
	public static BlockPos getDesignPos1(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		return stack.get(ModDataComponents.DESIGN_POS1);
	}

	/**
	 * Sets or clears the active DESIGN mode corner 1 (Pos1) on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param pos   The corner position to assign, or null to clear.
	 */
	public static void setDesignPos1(ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty()) return;
		if (pos == null) {
			stack.remove(ModDataComponents.DESIGN_POS1);
		} else {
			stack.set(ModDataComponents.DESIGN_POS1, pos.toImmutable());
		}
	}

	/**
	 * Resolves the active DESIGN mode corner 2 (Pos2) stored on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The second corner BlockPos, or null if unassigned.
	 */
	public static BlockPos getDesignPos2(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		return stack.get(ModDataComponents.DESIGN_POS2);
	}

	/**
	 * Sets or clears the active DESIGN mode corner 2 (Pos2) on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param pos   The corner position to assign, or null to clear.
	 */
	public static void setDesignPos2(ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty()) return;
		if (pos == null) {
			stack.remove(ModDataComponents.DESIGN_POS2);
		} else {
			stack.set(ModDataComponents.DESIGN_POS2, pos.toImmutable());
		}
	}

	/**
	 * Sets both DESIGN mode corners on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param p1    The first corner BlockPos, or null to clear.
	 * @param p2    The second corner BlockPos, or null to clear.
	 */
	public static void setDesignCorners(ItemStack stack, BlockPos p1, BlockPos p2) {
		setDesignPos1(stack, p1);
		setDesignPos2(stack, p2);
	}

	/**
	 * Clears both DESIGN mode corners on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 */
	public static void clearDesignCorners(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		stack.remove(ModDataComponents.DESIGN_POS1);
		stack.remove(ModDataComponents.DESIGN_POS2);
	}

	/**
	 * Checks if both Pos1 and Pos2 are selected on the scepter.
	 *
	 * @param stack The scepter ItemStack.
	 * @return true if both corner coordinates are non-null.
	 */
	public static boolean hasCompleteDesignSelection(ItemStack stack) {
		return getDesignPos1(stack) != null && getDesignPos2(stack) != null;
	}

	/**
	 * Handles selecting Corner 1 (Pos1) in DESIGN mode.
	 * Updates the data component, plays resonant chime SFX, spawns particle cues, and sends actionbar feedback.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @param stack  The scepter ItemStack.
	 * @param pos    The selected corner position.
	 */
	public static void handleDesignPos1(PlayerEntity player, World world, ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty() || pos == null) return;
		setDesignPos1(stack, pos);

		// Audio feedback
		world.playSound(
			null,
			pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
			SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
			SoundCategory.PLAYERS,
			1.2F,
			1.4F
		);
		if (player != null) {
			world.playSound(
				null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
				SoundCategory.PLAYERS,
				0.8F,
				1.4F
			);
		}

		// Corner 1 particles: AMETHYST / END_ROD / ENCHANT column
		if (world instanceof ServerWorld serverWorld) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			serverWorld.spawnParticles(ParticleTypes.END_ROD, cx, cy + 0.3D, cz, 6, 0.15D, 0.25D, 0.15D, 0.02D);
			serverWorld.spawnParticles(ParticleTypes.ENCHANT, cx, cy + 0.5D, cz, 12, 0.25D, 0.35D, 0.25D, 0.1D);
		} else if (world.isClient()) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			for (int i = 0; i < 6; i++) {
				world.addParticle(ParticleTypes.END_ROD, cx, cy + 0.3D, cz, 0.0D, 0.02D, 0.0D);
				world.addParticle(ParticleTypes.ENCHANT, cx, cy + 0.5D, cz, 0.0D, 0.05D, 0.0D);
			}
		}

		// Actionbar feedback with dimensions if Pos2 is present
		if (player != null) {
			BlockPos pos2 = getDesignPos2(stack);
			if (pos2 != null) {
				int sx = Math.abs(pos.getX() - pos2.getX()) + 1;
				int sy = Math.abs(pos.getY() - pos2.getY()) + 1;
				int sz = Math.abs(pos.getZ() - pos2.getZ()) + 1;
				int volume = sx * sy * sz;
				player.sendMessage(
					Text.literal("§d✦ Pos1 (Corner 1) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §8(§e" + sx + "x" + sy + "x" + sz + " §7" + volume + "b§8) §a[Right-Click to Capture]§r"),
					true
				);
			} else {
				player.sendMessage(
					Text.literal("§d✦ Pos1 (Corner 1) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §7(Left-click next block for Pos2)§r"),
					true
				);
			}
		}
	}

	private static volatile long lastDesignClickTimestamp = 0L;
	public static final long DESIGN_CLICK_DEBOUNCE_MS = 350L;

	/**
	 * Checks whether the DESIGN mode click is currently debounced.
	 */
	public static boolean isDesignClickDebounced() {
		return System.currentTimeMillis() - lastDesignClickTimestamp < DESIGN_CLICK_DEBOUNCE_MS;
	}

	/**
	 * Records a design click timestamp to enforce debounce against held attack key repeats.
	 */
	public static void recordDesignClick() {
		lastDesignClickTimestamp = System.currentTimeMillis();
	}

	/**
	 * Resets debounce timestamp (primarily for tests).
	 */
	public static void resetDesignClickDebounce() {
		lastDesignClickTimestamp = 0L;
	}

	/**
	 * Sequentially handles selecting corners (Pos1 -> Pos2 -> restart) in DESIGN mode with a left-click.
	 * If sneaking, resets both corners and prepares for Pos1.
	 *
	 * @param player     The commanding player.
	 * @param world      The world instance.
	 * @param stack      The scepter ItemStack.
	 * @param pos        The selected corner position.
	 * @param isSneaking True if player is sneaking (Shift + Left-Click) to reset corners.
	 * @return 1 if Pos1 was set, 2 if Pos2 was set, 0 if reset or null.
	 */
	public static int handleDesignClick(PlayerEntity player, World world, ItemStack stack, BlockPos pos, boolean isSneaking) {
		if (stack == null || stack.isEmpty()) return 0;
		if (isSneaking) {
			handleDesignReset(player, world, stack);
			return 0;
		}
		if (pos == null) return 0;

		long now = System.currentTimeMillis();
		if (now - lastDesignClickTimestamp < DESIGN_CLICK_DEBOUNCE_MS) {
			return 0; // Debounced: ignore repeated tick invocations from held attack key
		}
		lastDesignClickTimestamp = now;

		int step;
		if (DESIGN_CORNER_STEPPER != null) {
			step = DESIGN_CORNER_STEPPER.stepCorner(pos);
		} else {
			BlockPos currentPos1 = getDesignPos1(stack);
			BlockPos currentPos2 = getDesignPos2(stack);
			if (currentPos1 == null) {
				step = 1;
			} else if (currentPos2 == null) {
				step = 2;
			} else if (pos.equals(currentPos2)) {
				step = 2;
			} else {
				step = 1;
			}
		}

		if (step == 1) {
			setDesignPos2(stack, null);
			handleDesignPos1(player, world, stack, pos);
			return 1;
		} else {
			handleDesignPos2(player, world, stack, pos);
			return 2;
		}
	}

	public static int handleDesignClick(PlayerEntity player, World world, ItemStack stack, BlockPos pos) {
		return handleDesignClick(player, world, stack, pos, player != null && player.isSneaking());
	}

	/**
	 * Clears both DESIGN mode corners, plays reset audio feedback, and informs the player.
	 * Resets state so the next click will place Pos1 first.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @param stack  The scepter ItemStack.
	 */
	public static void handleDesignReset(PlayerEntity player, World world, ItemStack stack) {
		if (DESIGN_CORNER_RESETTER != null) {
			try {
				DESIGN_CORNER_RESETTER.resetCorners();
			} catch (Throwable ignored) {}
		}
		clearDesignCorners(stack);

		if (world != null && player != null) {
			world.playSound(
				null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
				SoundCategory.PLAYERS,
				1.0F,
				0.8F
			);
		}
		if (player != null) {
			player.sendMessage(
				Text.literal("§6✦ Cleared DESIGN corner selections (Pos1 & Pos2) - Ready for Pos1!§r"),
				true
			);
		}
	}

	/**
	 * Handles selecting Corner 2 (Pos2) in DESIGN mode.
	 * Updates the data component, plays resonant chime SFX, spawns particle cues, and sends actionbar feedback.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @param stack  The scepter ItemStack.
	 * @param pos    The selected corner position.
	 */
	public static void handleDesignPos2(PlayerEntity player, World world, ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty() || pos == null) return;
		setDesignPos2(stack, pos);

		// Audio feedback
		world.playSound(
			null,
			pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
			SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
			SoundCategory.PLAYERS,
			1.2F,
			1.6F
		);
		if (player != null) {
			world.playSound(
				null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
				SoundCategory.PLAYERS,
				0.8F,
				1.6F
			);
		}

		// Corner 2 particles: HAPPY_VILLAGER / GLOW / PORTAL column
		if (world instanceof ServerWorld serverWorld) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy + 0.3D, cz, 6, 0.15D, 0.25D, 0.15D, 0.02D);
			serverWorld.spawnParticles(ParticleTypes.GLOW, cx, cy + 0.5D, cz, 10, 0.25D, 0.35D, 0.25D, 0.05D);
		} else if (world.isClient()) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			for (int i = 0; i < 6; i++) {
				world.addParticle(ParticleTypes.HAPPY_VILLAGER, cx, cy + 0.3D, cz, 0.0D, 0.02D, 0.0D);
				world.addParticle(ParticleTypes.GLOW, cx, cy + 0.5D, cz, 0.0D, 0.05D, 0.0D);
			}
		}

		// Actionbar feedback with dimensions if Pos1 is present
		if (player != null) {
			BlockPos pos1 = getDesignPos1(stack);
			if (pos1 != null) {
				int sx = Math.abs(pos1.getX() - pos.getX()) + 1;
				int sy = Math.abs(pos1.getY() - pos.getY()) + 1;
				int sz = Math.abs(pos1.getZ() - pos.getZ()) + 1;
				int volume = sx * sy * sz;
				player.sendMessage(
					Text.literal("§d✦ Pos2 (Corner 2) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §8(§e" + sx + "x" + sy + "x" + sz + " §7" + volume + "b§8) §a[Right-Click to Capture]§r"),
					true
				);
			} else {
				player.sendMessage(
					Text.literal("§d✦ Pos2 (Corner 2) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §7(Left-click block for Pos1)§r"),
					true
				);
			}
		}
	}

	// -----------------------------------------------------------------------------------------
	// MINE MODE SPATIAL CORNER SELECTION & COORDINATE TRACKING (AREA MINING)
	// -----------------------------------------------------------------------------------------

	/**
	 * Resolves the active MINE mode corner 1 (Pos1) stored on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The first corner BlockPos, or null if unassigned.
	 */
	public static BlockPos getMinePos1(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		return stack.get(ModDataComponents.MINE_POS1);
	}

	/**
	 * Sets or clears the active MINE mode corner 1 (Pos1) on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param pos   The corner position to assign, or null to clear.
	 */
	public static void setMinePos1(ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty()) return;
		if (pos == null) {
			stack.remove(ModDataComponents.MINE_POS1);
		} else {
			stack.set(ModDataComponents.MINE_POS1, pos.toImmutable());
		}
	}

	/**
	 * Resolves the active MINE mode corner 2 (Pos2) stored on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The second corner BlockPos, or null if unassigned.
	 */
	public static BlockPos getMinePos2(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		return stack.get(ModDataComponents.MINE_POS2);
	}

	/**
	 * Sets or clears the active MINE mode corner 2 (Pos2) on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param pos   The corner position to assign, or null to clear.
	 */
	public static void setMinePos2(ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty()) return;
		if (pos == null) {
			stack.remove(ModDataComponents.MINE_POS2);
		} else {
			stack.set(ModDataComponents.MINE_POS2, pos.toImmutable());
		}
	}

	/**
	 * Sets both MINE mode corners on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @param p1    The first corner BlockPos, or null to clear.
	 * @param p2    The second corner BlockPos, or null to clear.
	 */
	public static void setMineCorners(ItemStack stack, BlockPos p1, BlockPos p2) {
		setMinePos1(stack, p1);
		setMinePos2(stack, p2);
	}

	/**
	 * Clears both MINE mode corners on the scepter stack.
	 *
	 * @param stack The scepter ItemStack.
	 */
	public static void clearMineCorners(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		stack.remove(ModDataComponents.MINE_POS1);
		stack.remove(ModDataComponents.MINE_POS2);
	}

	/**
	 * Checks if both Pos1 and Pos2 are selected for MINE mode on the scepter.
	 *
	 * @param stack The scepter ItemStack.
	 * @return true if both corner coordinates are non-null.
	 */
	public static boolean hasCompleteMineSelection(ItemStack stack) {
		return getMinePos1(stack) != null && getMinePos2(stack) != null;
	}

	/**
	 * Handles selecting Corner 1 (Pos1) in MINE AREA mode.
	 * Updates the data component, plays resonant chime SFX, spawns particle cues, and sends actionbar feedback.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @param stack  The scepter ItemStack.
	 * @param pos    The selected corner position.
	 */
	public static void handleMinePos1(PlayerEntity player, World world, ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty() || pos == null) return;
		setMinePos1(stack, pos);

		// Audio feedback
		world.playSound(
			null,
			pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
			SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
			SoundCategory.PLAYERS,
			1.2F,
			1.2F
		);
		if (player != null) {
			world.playSound(
				null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
				SoundCategory.PLAYERS,
				0.8F,
				1.2F
			);
		}

		// Corner 1 particles: FLAME / CRIT column
		if (world instanceof ServerWorld serverWorld) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			serverWorld.spawnParticles(ParticleTypes.FLAME, cx, cy + 0.3D, cz, 6, 0.15D, 0.25D, 0.15D, 0.02D);
			serverWorld.spawnParticles(ParticleTypes.CRIT, cx, cy + 0.5D, cz, 10, 0.25D, 0.35D, 0.25D, 0.05D);
		} else if (world.isClient()) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			for (int i = 0; i < 6; i++) {
				world.addParticle(ParticleTypes.FLAME, cx, cy + 0.3D, cz, 0.0D, 0.02D, 0.0D);
				world.addParticle(ParticleTypes.CRIT, cx, cy + 0.5D, cz, 0.0D, 0.05D, 0.0D);
			}
		}

		// Actionbar feedback with dimensions if Pos2 is present
		if (player != null) {
			BlockPos pos2 = getMinePos2(stack);
			if (pos2 != null) {
				int sx = Math.abs(pos.getX() - pos2.getX()) + 1;
				int sy = Math.abs(pos.getY() - pos2.getY()) + 1;
				int sz = Math.abs(pos.getZ() - pos2.getZ()) + 1;
				int volume = sx * sy * sz;
				player.sendMessage(
					Text.literal("§6✦ Mine Pos1 (Corner 1) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §8(§e" + sx + "x" + sy + "x" + sz + " §7" + volume + "b§8) §a[Right-Click to Confirm Area]§r"),
					true
				);
			} else {
				player.sendMessage(
					Text.literal("§6✦ Mine Pos1 (Corner 1) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §7(Left-click next block for Pos2)§r"),
					true
				);
			}
		}
	}

	/**
	 * Handles selecting Corner 2 (Pos2) in MINE AREA mode.
	 * Updates the data component, plays resonant chime SFX, spawns particle cues, and sends actionbar feedback.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @param stack  The scepter ItemStack.
	 * @param pos    The selected corner position.
	 */
	public static void handleMinePos2(PlayerEntity player, World world, ItemStack stack, BlockPos pos) {
		if (stack == null || stack.isEmpty() || pos == null) return;
		setMinePos2(stack, pos);

		// Audio feedback
		world.playSound(
			null,
			pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
			SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
			SoundCategory.PLAYERS,
			1.2F,
			1.4F
		);
		if (player != null) {
			world.playSound(
				null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
				SoundCategory.PLAYERS,
				0.8F,
				1.4F
			);
		}

		// Corner 2 particles: LAVA / FLAME column
		if (world instanceof ServerWorld serverWorld) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			serverWorld.spawnParticles(ParticleTypes.FLAME, cx, cy + 0.3D, cz, 8, 0.15D, 0.25D, 0.15D, 0.02D);
			serverWorld.spawnParticles(ParticleTypes.LAVA, cx, cy + 0.5D, cz, 4, 0.25D, 0.35D, 0.25D, 0.02D);
		} else if (world.isClient()) {
			double cx = pos.getX() + 0.5D;
			double cy = pos.getY() + 0.5D;
			double cz = pos.getZ() + 0.5D;
			for (int i = 0; i < 6; i++) {
				world.addParticle(ParticleTypes.FLAME, cx, cy + 0.3D, cz, 0.0D, 0.02D, 0.0D);
				world.addParticle(ParticleTypes.LAVA, cx, cy + 0.5D, cz, 0.0D, 0.02D, 0.0D);
			}
		}

		// Actionbar feedback with dimensions if Pos1 is present
		if (player != null) {
			BlockPos pos1 = getMinePos1(stack);
			if (pos1 != null) {
				int sx = Math.abs(pos1.getX() - pos.getX()) + 1;
				int sy = Math.abs(pos1.getY() - pos.getY()) + 1;
				int sz = Math.abs(pos1.getZ() - pos.getZ()) + 1;
				int volume = sx * sy * sz;
				player.sendMessage(
					Text.literal("§6✦ Mine Pos2 (Corner 2) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §8(§e" + sx + "x" + sy + "x" + sz + " §7" + volume + "b§8) §a[Right-Click to Confirm Area]§r"),
					true
				);
			} else {
				player.sendMessage(
					Text.literal("§6✦ Mine Pos2 (Corner 2) Set: §f[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] §7(Left-click block for Pos1)§r"),
					true
				);
			}
		}
	}

	private static volatile long lastMineClickTimestamp = 0L;
	public static final long MINE_CLICK_DEBOUNCE_MS = 350L;

	/**
	 * Checks whether the MINE AREA click is currently debounced.
	 */
	public static boolean isMineClickDebounced() {
		return System.currentTimeMillis() - lastMineClickTimestamp < MINE_CLICK_DEBOUNCE_MS;
	}

	/**
	 * Records a mine click timestamp to enforce debounce against held attack key repeats.
	 */
	public static void recordMineClick() {
		lastMineClickTimestamp = System.currentTimeMillis();
	}

	/**
	 * Resets mine debounce timestamp (primarily for tests).
	 */
	public static void resetMineClickDebounce() {
		lastMineClickTimestamp = 0L;
	}

	/**
	 * Sequentially handles selecting corners (Pos1 -> Pos2 -> restart) in MINE AREA mode with a left-click.
	 * If sneaking, resets both corners and prepares for Pos1.
	 *
	 * @param player     The commanding player.
	 * @param world      The world instance.
	 * @param stack      The scepter ItemStack.
	 * @param pos        The selected corner position.
	 * @param isSneaking True if player is sneaking (Shift + Left-Click) to reset corners.
	 * @return 1 if Pos1 was set, 2 if Pos2 was set, 0 if reset or null.
	 */
	public static int handleMineClick(PlayerEntity player, World world, ItemStack stack, BlockPos pos, boolean isSneaking) {
		if (stack == null || stack.isEmpty()) return 0;
		if (isSneaking) {
			handleMineReset(player, world, stack);
			return 0;
		}
		if (pos == null) return 0;

		long now = System.currentTimeMillis();
		if (now - lastMineClickTimestamp < MINE_CLICK_DEBOUNCE_MS) {
			return 0; // Debounced: ignore repeated tick invocations from held attack key
		}
		lastMineClickTimestamp = now;

		int step;
		if (MINE_CORNER_STEPPER != null) {
			step = MINE_CORNER_STEPPER.stepCorner(pos);
		} else {
			BlockPos currentPos1 = getMinePos1(stack);
			BlockPos currentPos2 = getMinePos2(stack);
			if (currentPos1 == null) {
				step = 1;
			} else if (currentPos2 == null) {
				step = 2;
			} else if (pos.equals(currentPos2)) {
				step = 2;
			} else {
				step = 1;
			}
		}

		if (step == 1) {
			setMinePos2(stack, null);
			handleMinePos1(player, world, stack, pos);
			return 1;
		} else {
			handleMinePos2(player, world, stack, pos);
			return 2;
		}
	}

	public static int handleMineClick(PlayerEntity player, World world, ItemStack stack, BlockPos pos) {
		return handleMineClick(player, world, stack, pos, player != null && player.isSneaking());
	}

	/**
	 * Clears both MINE mode corners, plays reset audio feedback, and informs the player.
	 * Resets state so the next click will place Pos1 first.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @param stack  The scepter ItemStack.
	 */
	public static void handleMineReset(PlayerEntity player, World world, ItemStack stack) {
		if (MINE_CORNER_RESETTER != null) {
			try {
				MINE_CORNER_RESETTER.resetCorners();
			} catch (Throwable ignored) {}
		}
		clearMineCorners(stack);

		if (world != null && player != null) {
			world.playSound(
				null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
				SoundCategory.PLAYERS,
				1.0F,
				0.8F
			);
		}
		if (player != null) {
			player.sendMessage(
				Text.literal("§6✦ Cleared MINE corner selections (Pos1 & Pos2) - Ready for Pos1!§r"),
				true
			);
		}
	}

	// -----------------------------------------------------------------------------------------
	// PATROL ROUTE & ESCORT MANAGEMENT
	// -----------------------------------------------------------------------------------------

	private static final Map<UUID, UUID> PRIMED_ESCORT_MINIONS = new ConcurrentHashMap<>();

	/**
	 * Resolves the active patrol route channel index stored on the scepter.
	 */
	public static int getActivePatrolRoute(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.ACTIVE_PATROL_ROUTE, 0);
	}

	/**
	 * Sets the active patrol route channel index on the scepter stack.
	 */
	public static void setActivePatrolRoute(ItemStack stack, int routeId) {
		int clamped = Math.max(0, routeId);
		stack.set(ModDataComponents.ACTIVE_PATROL_ROUTE, clamped);
	}

	/**
	 * Cycles the active patrol route channel in sequence across available routes.
	 */
	public static int cyclePatrolRoute(ItemStack stack, PlayerEntity player) {
		int current = getActivePatrolRoute(stack);
		List<PatrolRoute> availableRoutes = null;
		if (player != null && !player.getWorld().isClient()) {
			availableRoutes = PatrolRouteManager.getInstance().getAllRoutes(player.getUuid());
		}

		int next;
		if (availableRoutes != null && !availableRoutes.isEmpty()) {
			List<Integer> ids = availableRoutes.stream().map(PatrolRoute::routeId).sorted().toList();
			int idx = ids.indexOf(current);
			if (idx >= 0 && idx < ids.size() - 1) {
				next = ids.get(idx + 1);
			} else {
				next = ids.get(0);
			}
		} else {
			next = (current + 1) % PatrolRoute.CHANNEL_COUNT;
		}

		setActivePatrolRoute(stack, next);

		if (player != null) {
			player.getWorld().playSound(
				null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.0F + (Math.floorMod(next, 5) * 0.15F)
			);
			String routeName = "Route " + (next + 1);
			if (!player.getWorld().isClient()) {
				PatrolRoute r = PatrolRouteManager.getInstance().getRoute(player.getUuid(), next);
				if (r != null) routeName = r.getFormattedName();
			} else if (next >= 0 && next < PatrolRoute.CHANNEL_FORMATTED_NAMES.length) {
				routeName = PatrolRoute.CHANNEL_FORMATTED_NAMES[next];
			}
			player.sendMessage(Text.literal("§6✦ Active Patrol Channel: §r" + routeName), true);
		}
		return next;
	}

	/**
	 * Primes a minion as an escort to be bound to a squad leader upon next click.
	 */
	public static void primeEscortMinion(PlayerEntity player, MinionEntity minion) {
		if (player == null || minion == null) return;
		PRIMED_ESCORT_MINIONS.put(player.getUuid(), minion.getUuid());
		World world = player.getWorld();
		world.playSound(
			null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.8F, 1.4F
		);
		world.playSound(
			null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.8F, 1.2F
		);
		world.playSound(
			null, minion.getX(), minion.getY(), minion.getZ(),
			SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.8F, 1.4F
		);
		player.sendMessage(Text.literal("§6✦ " + minion.getRole().getDisplayName() + " primed as Escort! Right-click another minion to assign as Leader."), true);
	}

	/**
	 * Binds any currently primed escort minion to the clicked leader minion.
	 */
	public static boolean handlePrimedEscort(PlayerEntity player, MinionEntity targetLeader) {
		if (player == null || targetLeader == null) return false;
		UUID primedUuid = PRIMED_ESCORT_MINIONS.remove(player.getUuid());
		if (primedUuid == null) return false;

		if (player.getWorld() instanceof ServerWorld serverWorld) {
			Entity entity = serverWorld.getEntity(primedUuid);
			if (entity instanceof MinionEntity follower && follower.isAlive() && follower.isOwner(player)) {
				if (follower.equals(targetLeader)) {
					follower.clearLeader();
					follower.setSelected(true);
					player.sendMessage(Text.literal("§e✦ Escort cleared; minion follows master."), true);
					return true;
				}
				// Break reciprocal circular escort loop if targetLeader was escorting follower
				if (targetLeader.getLeaderMinionUuid() != null && targetLeader.getLeaderMinionUuid().equals(follower.getUuid())) {
					targetLeader.clearLeader();
				}
				follower.setLeaderMinionUuid(targetLeader.getUuid());
				follower.setSelected(false); // Automatically deselect from player so it only follows the leader!
				follower.setPatrolRouteId(-1);
				follower.setSitting(false);
				follower.setGuardAnchorPos(null);
				follower.getNavigation().stop();
				serverWorld.spawnParticles(
					ParticleTypes.HAPPY_VILLAGER,
					follower.getX(), follower.getY() + 1.2D, follower.getZ(),
					10, 0.3D, 0.3D, 0.3D, 0.1D
				);
				serverWorld.playSound(
					null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.8F, 1.4F
				);
				serverWorld.playSound(
					null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.8F, 1.2F
				);
				serverWorld.playSound(
					null, follower.getX(), follower.getY(), follower.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.8F, 1.4F
				);
				player.sendMessage(Text.literal("§a✦ " + follower.getRole().getDisplayName() + " is now escorting " + targetLeader.getRole().getDisplayName() + "! (Deselected from you)§r"), true);
				return true;
			}
		}
		return false;
	}

	/**
	 * Assigns all currently selected minions (except the target leader itself) to escort the target leader minion.
	 * All assigned escort minions are automatically deselected from the player so they follow only the leader.
	 *
	 * @param player       The commanding player.
	 * @param targetLeader The minion designated as the squad leader.
	 * @return The number of minions tethered to the leader.
	 */
	public static int assignSelectedMinionsToLeader(PlayerEntity player, MinionEntity targetLeader) {
		if (player == null || targetLeader == null || !targetLeader.isAlive() || !targetLeader.isOwner(player)) {
			return 0;
		}

		World world = player.getWorld();
		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			List<MinionEntity> selectedMinions = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected() && !m.getUuid().equals(targetLeader.getUuid())
			);

			if (selectedMinions.isEmpty()) {
				return 0;
			}

			// Break reciprocal circular loop if targetLeader was escorting any of the followers
			if (targetLeader.getLeaderMinionUuid() != null) {
				for (MinionEntity f : selectedMinions) {
					if (targetLeader.getLeaderMinionUuid().equals(f.getUuid())) {
						targetLeader.clearLeader();
						break;
					}
				}
			}

			for (MinionEntity follower : selectedMinions) {
				follower.setLeaderMinionUuid(targetLeader.getUuid());
				follower.setSelected(false); // Automatically deselect follower so it only follows the leader!
				follower.setPatrolRouteId(-1);
				follower.setSitting(false);
				follower.setGuardAnchorPos(null);
				follower.getNavigation().stop();
				serverWorld.spawnParticles(
					ParticleTypes.HAPPY_VILLAGER,
					follower.getX(), follower.getY() + 1.2D, follower.getZ(),
					8, 0.3D, 0.3D, 0.3D, 0.1D
				);
			}

			// Resonant audible chime at the player and leader
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.8F, 1.4F);
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.8F, 1.2F);
			serverWorld.playSound(null, targetLeader.getX(), targetLeader.getY(), targetLeader.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.8F, 1.4F);
			serverWorld.spawnParticles(
				ParticleTypes.HAPPY_VILLAGER,
				targetLeader.getX(), targetLeader.getY() + 1.2D, targetLeader.getZ(),
				12, 0.4D, 0.4D, 0.4D, 0.1D
			);

			player.sendMessage(
				Text.literal("§a✦ Assigned " + selectedMinions.size() + " selected minion(s) to escort " + targetLeader.getRole().getDisplayName() + "! (Deselected from you)§r"),
				true
			);
			return selectedMinions.size();
		}
		return 0;
	}

	/**
	 * Resolves the active blueprint identifier stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The active blueprint ID string, defaulting to {@link BlueprintRegistry#WATCHTOWER_ID}.
	 */
	public static String getBlueprintId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return stack.getOrDefault(ModDataComponents.ACTIVE_BLUEPRINT, "");
	}

	/**
	 * Sets the active blueprint identifier on the item stack.
	 *
	 * @param stack       The scepter ItemStack.
	 * @param blueprintId The blueprint identifier string.
	 */
	public static void setBlueprintId(ItemStack stack, String blueprintId) {
		if (stack != null && !stack.isEmpty()) {
			stack.set(ModDataComponents.ACTIVE_BLUEPRINT, Objects.requireNonNull(blueprintId, "blueprintId cannot be null"));
		}
	}

	/**
	 * Cycles to the next custom blueprint in catalog order, playing a bell chime sound
	 * and projecting an action-bar notification.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @return The newly selected StructureBlueprint, or null if no blueprints exist.
	 */
	public static StructureBlueprint cycleBlueprint(ItemStack stack, PlayerEntity player, World world) {
		String currentId = getBlueprintId(stack);
		StructureBlueprint next = BlueprintRegistry.getNext(currentId);
		if (next == null || next.getBlockCount() == 0) {
			if (player != null) {
				player.sendMessage(Text.literal("§c⚠ No custom blueprints captured yet! Use DESIGN mode to capture one first.§r"), true);
			}
			return null;
		}
		setBlueprintId(stack, next.getId());

		world.playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.BLOCK_NOTE_BLOCK_BELL,
			SoundCategory.PLAYERS,
			1.0F,
			1.2F
		);

		player.sendMessage(
			Text.literal("§b✦ Active Blueprint: §f" + next.getName() + " §7(" + next.getBlockCount() + " blocks)§r"),
			true
		);
		return next;
	}

	/**
	 * Resolves the active blueprint rotation index stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return Integer rotation index modulo 4 (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
	 */
	public static int getRotationIndex(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		return Math.floorMod(stack.getOrDefault(ModDataComponents.STRUCTURE_ROTATION, 0), 4);
	}

	/**
	 * Sets the active blueprint rotation index on the item stack.
	 *
	 * @param stack         The scepter ItemStack.
	 * @param rotationIndex The rotation index (normalized modulo 4).
	 */
	public static void setRotationIndex(ItemStack stack, int rotationIndex) {
		if (stack != null && !stack.isEmpty()) {
			stack.set(ModDataComponents.STRUCTURE_ROTATION, Math.floorMod(rotationIndex, 4));
		}
	}

	/**
	 * Resolves the active {@link BlockRotation} from the item stack.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The corresponding BlockRotation enum value.
	 */
	public static BlockRotation getRotation(ItemStack stack) {
		int index = getRotationIndex(stack);
		return switch (index) {
			case 1 -> BlockRotation.CLOCKWISE_90;
			case 2 -> BlockRotation.CLOCKWISE_180;
			case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
			default -> BlockRotation.NONE;
		};
	}

	/**
	 * Sets the active {@link BlockRotation} on the item stack.
	 *
	 * @param stack    The scepter ItemStack.
	 * @param rotation The target BlockRotation.
	 */
	public static void setRotation(ItemStack stack, BlockRotation rotation) {
		int index = switch (rotation == null ? BlockRotation.NONE : rotation) {
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
			default -> 0;
		};
		setRotationIndex(stack, index);
	}

	/**
	 * Cycles to the next 90-degree structure rotation (0° -> 90° -> 180° -> 270° -> 0°),
	 * emits a chime sound, and projects an actionbar notification.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @return The newly selected BlockRotation.
	 */
	public static BlockRotation cycleRotation(ItemStack stack, PlayerEntity player) {
		int nextIndex = Math.floorMod(getRotationIndex(stack) + 1, 4);
		setRotationIndex(stack, nextIndex);
		BlockRotation newRot = getRotation(stack);

		if (player != null) {
			if (player.getWorld() != null) {
				player.getWorld().playSound(
					null,
					player.getX(),
					player.getY(),
					player.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_CHIME,
					SoundCategory.PLAYERS,
					0.8F,
					1.0F + (nextIndex * 0.15F)
				);
			}
			int degrees = nextIndex * 90;
			player.sendMessage(
				Text.literal("§6🏗 Rotation: §b" + degrees + "° §7(" + newRot.name() + ")§r"),
				true
			);
		}
		return newRot;
	}

	/**
	 * Convenience overload cycling structure rotation with world context.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @param world  The interaction world.
	 * @return The newly selected BlockRotation.
	 */
	public static BlockRotation cycleRotation(ItemStack stack, PlayerEntity player, World world) {
		return cycleRotation(stack, player);
	}

	// -----------------------------------------------------------------------------------------
	// ITEM USAGE & CHANNELED RALLY RING (BANNER OF COURAGE)
	// -----------------------------------------------------------------------------------------

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BLOCK;
	}

	/**
	 * Evaluates whether the given horizontal target coordinates lie within the user's
	 * 90-degree forward conical sector (fan) extending up to maxRadius blocks away.
	 * Uses the horizontal dot product between the look vector and the target displacement vector.
	 *
	 * @param center    The player or entity at the center of the sector.
	 * @param targetX   Target coordinate X.
	 * @param targetZ   Target coordinate Z.
	 * @param maxRadius Maximum radial distance of the sector.
	 * @return true if target is within radial distance and within +-45 degrees of center's horizontal look direction.
	 */
	public static boolean isWithinSector(LivingEntity center, double targetX, double targetZ, double maxRadius) {
		double dx = targetX - center.getX();
		double dz = targetZ - center.getZ();
		double distSq = dx * dx + dz * dz;
		if (distSq > maxRadius * maxRadius) {
			return false;
		}
		double dist = Math.sqrt(distSq);
		if (dist < 0.001D) {
			return true;
		}

		// Horizontal look direction derived from player's yaw
		float yawRad = center.getYaw() * 0.017453292F;
		double lookX = -Math.sin(yawRad);
		double lookZ = Math.cos(yawRad);

		// Horizontal dot product normalized
		double dot = (dx * lookX + dz * lookZ) / dist;

		// 90° forward cone (+-45°): cos(45°) = ~0.70710678D
		return dot >= 0.70710678D;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		int useTicks = getMaxUseTime(stack, user) - remainingUseTicks;
		if (useTicks < 4) {
			return;
		}

		// Expanding 90° forward sector (ParticleTypes.PORTAL / FLAME)
		// Radius expands from 3.0 up to 16.0 blocks over ~36 ticks
		float chargeProgress = Math.min(1.0F, (float) (useTicks - 4) / 36.0F);
		double radius = 3.0D + (13.0D * chargeProgress);

		float yawRad = user.getYaw() * 0.017453292F;
		double lookX = -Math.sin(yawRad);
		double lookZ = Math.cos(yawRad);

		if (world.isClient()) {
			// 1. Curved outer arc (+-45 degrees around look angle)
			int arcPoints = (int) Math.max(10, radius * 1.4D);
			for (int i = 0; i < arcPoints; i++) {
				double offsetAngle = (-Math.PI / 4.0D) + ((double) i / (arcPoints - 1)) * (Math.PI / 2.0D);
				double rotX = lookX * Math.cos(offsetAngle) - lookZ * Math.sin(offsetAngle);
				double rotZ = lookX * Math.sin(offsetAngle) + lookZ * Math.cos(offsetAngle);
				double px = user.getX() + radius * rotX;
				double pz = user.getZ() + radius * rotZ;
				double py = user.getY() + 0.15D;
				if (i % 2 == 0) {
					world.addParticle(ParticleTypes.PORTAL, px, py, pz, 0.0D, 0.04D, 0.0D);
				} else {
					world.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0D, 0.02D, 0.0D);
				}
			}

			// 2. Boundary rays along the left (-45 deg) and right (+45 deg) edges
			double[] rayAngles = { -Math.PI / 4.0D, Math.PI / 4.0D };
			for (double rayAngle : rayAngles) {
				double rotX = lookX * Math.cos(rayAngle) - lookZ * Math.sin(rayAngle);
				double rotZ = lookX * Math.sin(rayAngle) + lookZ * Math.cos(rayAngle);
				for (double r = 1.5D; r < radius; r += 1.2D) {
					double px = user.getX() + r * rotX;
					double pz = user.getZ() + r * rotZ;
					double py = user.getY() + 0.15D;
					world.addParticle(ParticleTypes.PORTAL, px, py, pz, 0.0D, 0.02D, 0.0D);
				}
			}
		} else if (world instanceof ServerWorld serverWorld) {
			if (useTicks % 4 == 0) {
				int arcPoints = (int) Math.max(8, radius * 1.0D);
				for (int i = 0; i < arcPoints; i++) {
					double offsetAngle = (-Math.PI / 4.0D) + ((double) i / (arcPoints - 1)) * (Math.PI / 2.0D);
					double rotX = lookX * Math.cos(offsetAngle) - lookZ * Math.sin(offsetAngle);
					double rotZ = lookX * Math.sin(offsetAngle) + lookZ * Math.cos(offsetAngle);
					double px = user.getX() + radius * rotX;
					double pz = user.getZ() + radius * rotZ;
					double py = user.getY() + 0.15D;
					if (i % 2 == 0) {
						serverWorld.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0.0D, 0.04D, 0.0D, 0.02D);
					} else {
						serverWorld.spawnParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.0D, 0.02D, 0.0D, 0.01D);
					}
				}
				double[] rayAngles = { -Math.PI / 4.0D, Math.PI / 4.0D };
				for (double rayAngle : rayAngles) {
					double rotX = lookX * Math.cos(rayAngle) - lookZ * Math.sin(rayAngle);
					double rotZ = lookX * Math.sin(rayAngle) + lookZ * Math.cos(rayAngle);
					for (double r = 1.5D; r < radius; r += 2.0D) {
						double px = user.getX() + r * rotX;
						double pz = user.getZ() + r * rotZ;
						double py = user.getY() + 0.15D;
						serverWorld.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0.0D, 0.02D, 0.0D, 0.01D);
					}
				}
			}

			// Real-time targeting illumination: highlight candidate minions and hostiles in sector
			if (user instanceof PlayerEntity player) {
				Box queryBox = player.getBoundingBox().expand(18.0D);
				List<MinionEntity> nearbyMinions = serverWorld.getEntitiesByClass(
					MinionEntity.class,
					queryBox,
					m -> m.isAlive() && m.isOwner(player)
				);
				for (MinionEntity minion : nearbyMinions) {
					if (isWithinSector(player, minion.getX(), minion.getZ(), radius)) {
						minion.setPreviewGlowing(true);
						if (useTicks % 6 == 0) {
							serverWorld.spawnParticles(ParticleTypes.ENCHANT, minion.getX(), minion.getY() + 0.8D, minion.getZ(), 2, 0.15D, 0.2D, 0.15D, 0.05D);
						}
					} else if (minion.isPreviewGlowing()) {
						minion.setPreviewGlowing(false);
					}
				}

				// Highlight candidate hostiles in sector with angry particles and crits
				if (useTicks % 6 == 0) {
					List<LivingEntity> nearbyLiving = serverWorld.getEntitiesByClass(
						LivingEntity.class,
						queryBox,
						e -> isTargetableEntity(player, e)
					);
					for (LivingEntity hostile : nearbyLiving) {
						if (isWithinSector(player, hostile.getX(), hostile.getZ(), radius)) {
							serverWorld.spawnParticles(ParticleTypes.ANGRY_VILLAGER, hostile.getX(), hostile.getY() + hostile.getHeight() + 0.3D, hostile.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
							serverWorld.spawnParticles(ParticleTypes.CRIT, hostile.getX(), hostile.getY() + (hostile.getHeight() * 0.6D), hostile.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.05D);
						}
					}
				}
			}
		}

		// Audio feedback while charging: rising pitch chime every 10 ticks
		if (useTicks % 10 == 0) {
			world.playSound(
				null,
				user.getX(),
				user.getY(),
				user.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_CHIME,
				SoundCategory.PLAYERS,
				0.6F,
				0.8F + (chargeProgress * 0.8F)
			);
		}
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (!(user instanceof PlayerEntity player)) {
			return;
		}

		int useTicks = getMaxUseTime(stack, user) - remainingUseTicks;
		CommandMode mode = getMode(stack);
		SquadGroup targetSquad = getTargetSquad(stack);

		// Quick tap (< 8 ticks): evaluate 32-block crosshair raycasting
		if (useTicks < 8) {
			// Clear any lingering preview glow states
			if (!world.isClient() && world instanceof ServerWorld serverWorld) {
				Box clearBox = player.getBoundingBox().expand(18.0D);
				for (MinionEntity m : serverWorld.getEntitiesByClass(MinionEntity.class, clearBox, minion -> minion.isOwner(player))) {
					if (m.isPreviewGlowing()) {
						m.setPreviewGlowing(false);
					}
				}
			}

			if (mode == CommandMode.BUILD) {
				cycleBlueprint(stack, player, world);
				return;
			}

			HitResult hit = raycastTarget(player, MINION_COMMAND_RADIUS);

			// 1. Entity Hit: Individual Follow, Focus-fire, Pathway Assignment, or Transfigure (RECRUIT)
			if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity targetEntity) {
				// Owned Minion Hit: Pathway Assignment or Individual Follow
				if (targetEntity instanceof MinionEntity minion && minion.isOwner(player)) {
					if (mode == CommandMode.PATHWAY) {
						if (!player.isSneaking()) {
							assignMinionToActivePatrolRoute(player, minion, stack);
						}
						return;
					}
					commandIndividualMinionFollow(player, minion);
					return;
				}

				if (mode == CommandMode.RECRUIT) {
					if (targetEntity instanceof MobEntity mob && !(targetEntity instanceof MinionEntity) && !(targetEntity instanceof PlayerEntity) && targetEntity.isAlive()) {
						if (!world.isClient() && world instanceof ServerWorld serverWorld) {
							transfigureEntityToMinion(serverWorld, player, mob);
						}
					} else if (!world.isClient()) {
						player.sendMessage(Text.literal("§c✦ Cannot enthrall this entity! Only living non-minion mobs can be recruited.§r"), true);
					}
				} else if (targetEntity.isAlive() && !(targetEntity instanceof MinionEntity minion && minion.isOwner(player)) && !(targetEntity instanceof PlayerEntity)) {
					executeHostileEntityPing(player, world, targetEntity, targetSquad);
				}
				return;
			}

			// In PATHWAY mode: if no owned minion was hit, quick right-click cycles route (or clears waypoints if sneaking)
			if (mode == CommandMode.PATHWAY) {
				if (player.isSneaking()) {
					if (!world.isClient() && world instanceof ServerWorld serverWorld) {
						int routeId = getActivePatrolRoute(stack);
						PatrolRoute updated = PatrolRouteManager.getInstance().clearRoute(player.getUuid(), routeId);
						PatrolRouteManager.getInstance().syncToPlayer((ServerPlayerEntity) player);
						List<MinionEntity> routeMinions = serverWorld.getEntitiesByClass(
							MinionEntity.class,
							player.getBoundingBox().expand(256.0D),
							m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() == routeId
						);
						for (MinionEntity m : routeMinions) {
							m.setPatrolRouteId(-1);
							m.getNavigation().stop();
						}
						player.sendMessage(Text.literal("§c✦ Cleared all waypoints for " + updated.getFormattedName()), true);
						world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0F, 0.6F);
					}
				} else {
					cyclePatrolRoute(stack, player);
				}
				return;
			}

			// 2. Block Hit: Long-range RTS ground waypoint ping (up to 64 blocks) or MINE dismantle
			if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
				if (mode == CommandMode.MINE) {
					return;
				}
				BlockPos hitPos = blockHit.getBlockPos();
				Direction hitSide = blockHit.getSide();
				BlockPos waypointPos = world.getBlockState(hitPos).isReplaceable() ? hitPos : hitPos.offset(hitSide);
				executeGroundWaypointPing(player, world, waypointPos, targetSquad);
				return;
			}

			// 3. Sky / Miss: Fall back to global scepter directives
			executeDirective(player, world, mode, targetSquad);
			return;
		}

		// Channeled Banner of Courage / Mass Attack 90° Forward Sector:
		// Releasing after charging evaluates entities in the forward sector:
		// - If hostiles are present: Signals Mass Assault on all enclosed enemies with smart target distribution.
		// - If minions are also present: Selects, transfigures, and marshals them to join the assault.
		// - If only minions are present: Standard Banner of Courage rally into squad/formation.
		float chargeProgress = Math.min(1.0F, (float) (useTicks - 8) / 36.0F);
		double rallyRadius = 3.0D + (13.0D * chargeProgress);

		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box rallyBox = player.getBoundingBox().expand(rallyRadius + 1.0D);
			List<MinionEntity> candidates = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				rallyBox,
				m -> m.isAlive() && m.isOwner(player) && (mode == CommandMode.PATHWAY || m.getPatrolRouteId() < 0)
			);

			List<MinionEntity> enclosedMinions = new ArrayList<>();
			for (MinionEntity minion : candidates) {
				if (isWithinSector(player, minion.getX(), minion.getZ(), rallyRadius)) {
					enclosedMinions.add(minion);
				}
				minion.setPreviewGlowing(false);
			}

			// Gather enclosed hostiles in the sector
			List<LivingEntity> potentialHostiles = serverWorld.getEntitiesByClass(
				LivingEntity.class,
				rallyBox,
				e -> isTargetableEntity(player, e)
			);
			List<LivingEntity> enclosedHostiles = new ArrayList<>();
			for (LivingEntity hostile : potentialHostiles) {
				if (isWithinSector(player, hostile.getX(), hostile.getZ(), rallyRadius)) {
					enclosedHostiles.add(hostile);
				}
			}

			// PATHWAY Mode: Sneak + Hold Right-Click dispatches forward sector (or selected minions) to active patrol route
			// When not sneaking, hold right-click performs the standard 90° cone sweep selection!
			if (mode == CommandMode.PATHWAY) {
				if (player.isSneaking()) {
					int routeId = getActivePatrolRoute(stack);
					PatrolRoute route = PatrolRouteManager.getInstance().getRoute(player.getUuid(), routeId);
					if (route.waypoints().isEmpty()) {
						player.sendMessage(Text.literal("§c✦ Cannot assign patrol: " + route.getFormattedName() + " has no waypoints! Right-click blocks to set points."), true);
						return;
					}

					List<MinionEntity> pathwayMinions = new ArrayList<>(enclosedMinions);
					if (pathwayMinions.isEmpty()) {
						Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
						pathwayMinions = serverWorld.getEntitiesByClass(
							MinionEntity.class,
							searchBox,
							m -> m.isAlive() && m.isOwner(player) && m.isSelected() && targetSquad.matches(m.getSquad())
						);
					}

					if (pathwayMinions.isEmpty()) {
						player.sendMessage(Text.literal("§e✦ No minions found in forward sector to assign to patrol."), true);
						return;
					}

					for (MinionEntity minion : pathwayMinions) {
						minion.setSelected(false);
						minion.setPatrolRouteId(routeId);
						minion.setCurrentWaypointIndex(0);
						minion.clearLeader();
						minion.setSitting(false);
						minion.setGuardAnchorPos(null);
						minion.getNavigation().stop();
						serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, minion.getX(), minion.getY() + 1.2D, minion.getZ(), 8, 0.25, 0.4, 0.25, 0.05);
					}

					serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.4F);
					player.sendMessage(
						Text.literal("§6📯 Banner of Courage! Dispatched " + pathwayMinions.size() + " minion(s) to " + route.getFormattedName() + " (" + route.waypoints().size() + " waypoints)!"),
						true
					);
					return;
				}
			}

			MinionRole targetRole = getTargetRole(stack);
			for (MinionEntity minion : enclosedMinions) {
				if (!targetSquad.isWildcard()) {
					minion.setSquad(targetSquad);
				}
				if (targetRole != null) {
					minion.setRole(targetRole);
					minion.autoEquipFromInventory();
				}
				minion.setSelected(true);
				minion.setGuardAnchorPos(null);
				minion.setSitting(false);
			}

			// Arcane release burst: 90° forward sector perimeter arc and boundary rays burst
			float yawRad = player.getYaw() * 0.017453292F;
			double lookX = -Math.sin(yawRad);
			double lookZ = Math.cos(yawRad);
			int burstCount = (int) Math.max(16, rallyRadius * 2.5D);
			for (int i = 0; i < burstCount; i++) {
				double offsetAngle = (-Math.PI / 4.0D) + ((double) i / (burstCount - 1)) * (Math.PI / 2.0D);
				double rotX = lookX * Math.cos(offsetAngle) - lookZ * Math.sin(offsetAngle);
				double rotZ = lookX * Math.sin(offsetAngle) + lookZ * Math.cos(offsetAngle);
				double px = player.getX() + rallyRadius * rotX;
				double pz = player.getZ() + rallyRadius * rotZ;
				serverWorld.spawnParticles(ParticleTypes.FLAME, px, player.getY() + 0.2D, pz, 2, 0.05, 0.05, 0.05, 0.02);
				serverWorld.spawnParticles(ParticleTypes.PORTAL, px, player.getY() + 0.2D, pz, 2, 0.05, 0.1, 0.05, 0.05);
			}
			double[] rayAngles = { -Math.PI / 4.0D, Math.PI / 4.0D };
			for (double rayAngle : rayAngles) {
				double rotX = lookX * Math.cos(rayAngle) - lookZ * Math.sin(rayAngle);
				double rotZ = lookX * Math.sin(rayAngle) + lookZ * Math.cos(rayAngle);
				for (double r = 1.5D; r <= rallyRadius; r += 1.5D) {
					double px = player.getX() + r * rotX;
					double pz = player.getZ() + r * rotZ;
					serverWorld.spawnParticles(ParticleTypes.FLAME, px, player.getY() + 0.2D, pz, 1, 0.02, 0.05, 0.02, 0.02);
					serverWorld.spawnParticles(ParticleTypes.PORTAL, px, player.getY() + 0.2D, pz, 1, 0.02, 0.05, 0.02, 0.02);
				}
			}

			String squadLabel = targetSquad.getFormattedName();

			if (!enclosedHostiles.isEmpty()) {
				// MASS ASSAULT: Distribute combat tasks across active forces
				List<MinionEntity> attackMinions = new ArrayList<>(enclosedMinions);
				if (attackMinions.isEmpty()) {
					Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
					attackMinions = serverWorld.getEntitiesByClass(
						MinionEntity.class,
						searchBox,
						m -> m.isAlive() && m.isOwner(player) && m.isSelected() && targetSquad.matches(m.getSquad())
					);
					if (attackMinions.isEmpty()) {
						attackMinions = serverWorld.getEntitiesByClass(
							MinionEntity.class,
							searchBox,
							m -> m.isAlive() && m.isOwner(player) && !m.isSitting() && m.getPatrolRouteId() < 0 && targetSquad.matches(m.getSquad())
						);
					}
				}

				// Sort hostiles by proximity to player (frontline to backline)
				enclosedHostiles.sort(Comparator.comparingDouble(h -> h.squaredDistanceTo(player)));

				for (int i = 0; i < attackMinions.size(); i++) {
					MinionEntity minion = attackMinions.get(i);
					minion.setSitting(false);
					minion.setSelected(true);
					minion.setGuardAnchorPos(null);
					minion.setAssaultTargets(enclosedHostiles);
					boolean isArcher = minion.matchesRole(MinionRole.WARRIOR) && MinionEntity.isRangedWeapon(minion.getMainHandStack());
					LivingEntity assignedTarget;
					if (isArcher && enclosedHostiles.size() > 1) {
						int backIndex = enclosedHostiles.size() - 1 - (i % Math.max(1, enclosedHostiles.size() / 2));
						assignedTarget = enclosedHostiles.get(Math.max(0, backIndex));
					} else {
						assignedTarget = enclosedHostiles.get(i % enclosedHostiles.size());
					}
					minion.setTarget(assignedTarget);
					minion.setAttacking(true);
					minion.getNavigation().startMovingTo(assignedTarget, 1.35D);
				}

				for (LivingEntity hostile : enclosedHostiles) {
					serverWorld.spawnParticles(ParticleTypes.CRIT, hostile.getX(), hostile.getY() + hostile.getHeight() * 0.6D, hostile.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.1D);
					serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK, hostile.getX(), hostile.getY() + 0.5D, hostile.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
					serverWorld.spawnParticles(ParticleTypes.FLAME, hostile.getX(), hostile.getY() + 0.2D, hostile.getZ(), 4, 0.1D, 0.1D, 0.1D, 0.02D);
				}

				SoundEvent hornSound = !SoundEvents.GOAT_HORN_SOUNDS.isEmpty()
					? SoundEvents.GOAT_HORN_SOUNDS.get(0).value()
					: SoundEvents.ITEM_GOAT_HORN_PLAY;
				world.playSound(null, player.getX(), player.getY(), player.getZ(), hornSound, SoundCategory.PLAYERS, 1.8F, 0.8F);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.8F, 1.6F);

				player.sendMessage(
					Text.literal("§c⚔ Mass Assault! " + attackMinions.size() + " Minion(s) [" + squadLabel + "§c] engaging " + enclosedHostiles.size() + " target(s)!§r"),
					true
				);
			} else {
				// STANDARD BANNER OF COURAGE RALLY
				SoundEvent hornSound = !SoundEvents.GOAT_HORN_SOUNDS.isEmpty()
					? SoundEvents.GOAT_HORN_SOUNDS.get(0).value()
					: SoundEvents.ITEM_GOAT_HORN_PLAY;

				world.playSound(
					null,
					player.getX(),
					player.getY(),
					player.getZ(),
					hornSound,
					SoundCategory.PLAYERS,
					1.6F,
					1.0F
				);

				for (MinionEntity minion : enclosedMinions) {
					minion.getNavigation().startMovingTo(player, 1.35D);
					serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, minion.getX(), minion.getY() + 1.0D, minion.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
					if (targetRole != null) {
						serverWorld.spawnParticles(ParticleTypes.ENCHANT, minion.getX(), minion.getY() + 1.2D, minion.getZ(), 10, 0.25, 0.4, 0.25, 0.1);
					}
				}

				MinionFormationFollowGoal.refreshFormationAnchor(player);

				if (targetRole != null) {
					serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9F, 1.4F);
					player.sendMessage(
						Text.literal("§6📯 Banner of Courage! Transfigured " + enclosedMinions.size() + " minion(s) to " + targetRole.getFormattedName() + "§6 into " + squadLabel + "§6!§r"),
						true
					);
				} else {
					player.sendMessage(
						Text.literal("§6📯 Banner of Courage! Gathered and selected " + enclosedMinions.size() + " minion(s) into " + squadLabel + "§6!§r"),
						true
					);
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------
	// CLIENT INVENTORY TICK: BLUEPRINT PREVIEW PARTICLES & ACTION-BAR READOUT
	// -----------------------------------------------------------------------------------------

	/**
	 * Checks if the player is currently touching water or flying directly above water.
	 *
	 * @param world  The world instance.
	 * @param player The player entity.
	 * @return true if player is touching water or the terrain/fluid surface directly below is water.
	 */
	public static boolean isPlayerOverWater(World world, PlayerEntity player) {
		if (player == null || world == null) {
			return false;
		}
		if (player.isTouchingWater() || player.isSubmergedInWater()) {
			return true;
		}
		// Scan vertical column beneath player down to the highest terrain surface
		BlockPos.Mutable mut = player.getBlockPos().mutableCopy();
		int bottomY = world.getBottomY();
		while (mut.getY() > bottomY) {
			BlockState state = world.getBlockState(mut);
			if (state.getFluidState().isIn(FluidTags.WATER) || state.isOf(Blocks.WATER)) {
				return true;
			}
			if (state.isSolidBlock(world, mut)) {
				return false;
			}
			mut.move(0, -1, 0);
		}
		return false;
	}

	private static final java.util.Set<java.util.UUID> ACTIVE_SERVER_BUILD_FLIERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}

		boolean isHeld = selected || player.getOffHandStack() == stack;
		boolean isBuildMode = isHeld && getMode(stack) == CommandMode.BUILD;

		if (entity instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			java.util.UUID uuid = serverPlayer.getUuid();
			if (isBuildMode) {
				// Water Flight Check: If player is flying or hovering over water in BUILD mode, cancel build & exit BUILD mode
				if (isPlayerOverWater(world, serverPlayer)) {
					com.example.construction.ConstructionManager.getInstance().cancelActiveSessionsForOwner(uuid, serverPlayer.getServerWorld());
					setMode(stack, CommandMode.FOLLOW);

					if (!serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
						serverPlayer.getAbilities().flying = false;
						serverPlayer.getAbilities().allowFlying = false;
						serverPlayer.sendAbilitiesUpdate();
					}
					ACTIVE_SERVER_BUILD_FLIERS.remove(uuid);

					serverPlayer.sendMessage(Text.literal("§c⚠ Construction cancelled: Flying over water is prohibited in BUILD mode!§r"), true);
					serverPlayer.getServerWorld().playSound(null, serverPlayer.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 1.0F, 1.0F);
					serverPlayer.getServerWorld().spawnParticles(ParticleTypes.SPLASH, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), 20, 0.4D, 0.2D, 0.4D, 0.1D);
					return;
				}

				if (!serverPlayer.getAbilities().allowFlying || !serverPlayer.getAbilities().flying) {
					serverPlayer.getAbilities().allowFlying = true;
					serverPlayer.getAbilities().flying = true;
					serverPlayer.sendAbilitiesUpdate();
				}
				serverPlayer.fallDistance = 0.0F;
				ACTIVE_SERVER_BUILD_FLIERS.add(uuid);
			} else if (ACTIVE_SERVER_BUILD_FLIERS.contains(uuid)) {
				if (!serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
					serverPlayer.getAbilities().flying = false;
					serverPlayer.getAbilities().allowFlying = false;
					serverPlayer.sendAbilitiesUpdate();
				}
				serverPlayer.fallDistance = 0.0F;
				if (serverPlayer.isOnGround()) {
					ACTIVE_SERVER_BUILD_FLIERS.remove(uuid);
				}
			}
			return;
		}

		if (!world.isClient()) {
			return;
		}

		// Active only when the scepter is held in main hand or off hand
		if (!isHeld) {
			return;
		}

		// Active only in BUILD mode
		if (getMode(stack) != CommandMode.BUILD) {
			return;
		}

		// Throttle particle and HUD updates to every 4 ticks
		if (player.age % 4 != 0) {
			return;
		}

		// Perform 64-block crosshair raycast against terrain
		BlockHitResult hitResult = raycastBlockTarget(player, MINION_COMMAND_RADIUS);
		if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos clickedPos = hitResult.getBlockPos();
		Direction side = hitResult.getSide();
		BlockPos anchorPos = world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);

		String bpId = getBlueprintId(stack);
		StructureBlueprint blueprint = BlueprintRegistry.getOrDefault(bpId);
		if (blueprint == null || blueprint.getBlockCount() == 0) {
			return;
		}

		BlockRotation rotation = getRotation(stack);
		StructureBlueprint rotatedBlueprint = blueprint.rotate(rotation);

		// Render perimeter ground bounding particles
		renderPerimeterParticles(world, anchorPos, rotatedBlueprint);

		// Project real-time HUD action-bar readout
		int angle = getRotationIndex(stack) * 90;
		String doorDir = resolveDoorDirection(rotatedBlueprint, rotation);
		player.sendMessage(
			Text.literal("§6🏗 " + rotatedBlueprint.getName() + " §8| §bRotation: " + angle + "° §8| §a🚪 Door: " + doorDir),
			true
		);
	}

	private static void renderPerimeterParticles(World world, BlockPos anchorPos, StructureBlueprint blueprint) {
		BlockBox box = blueprint.getBoundingBox();
		double minX = anchorPos.getX() + box.getMinX();
		double minY = anchorPos.getY() + box.getMinY() + 0.05D;
		double minZ = anchorPos.getZ() + box.getMinZ();
		double maxX = anchorPos.getX() + box.getMaxX() + 1.0D;
		double maxZ = anchorPos.getZ() + box.getMaxZ() + 1.0D;

		for (double x = minX; x <= maxX; x += 1.0D) {
			world.addParticle(ParticleTypes.WAX_ON, x, minY, minZ, 0.0D, 0.01D, 0.0D);
			world.addParticle(ParticleTypes.WAX_ON, x, minY, maxZ, 0.0D, 0.01D, 0.0D);
		}
		for (double z = minZ; z <= maxZ; z += 1.0D) {
			world.addParticle(ParticleTypes.WAX_ON, minX, minY, z, 0.0D, 0.01D, 0.0D);
			world.addParticle(ParticleTypes.WAX_ON, maxX, minY, z, 0.0D, 0.01D, 0.0D);
		}
	}

	private static String resolveDoorDirection(StructureBlueprint blueprint, BlockRotation rotation) {
		for (BlueprintBlock block : blueprint.getBlocks()) {
			if (block.state().getBlock() instanceof DoorBlock && block.state().contains(DoorBlock.FACING)) {
				return block.state().get(DoorBlock.FACING).getName().toUpperCase();
			}
		}
		Direction frontDir = switch (rotation) {
			case CLOCKWISE_90 -> Direction.WEST;
			case CLOCKWISE_180 -> Direction.NORTH;
			case COUNTERCLOCKWISE_90 -> Direction.EAST;
			default -> Direction.SOUTH;
		};
		return frontDir.getName().toUpperCase();
	}

	// -----------------------------------------------------------------------------------------
	// RIGHT-CLICK ON BLOCK (GROUND ANCHORING & WAYPOINT PINGS)
	// -----------------------------------------------------------------------------------------

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		if (player == null) {
			return ActionResult.PASS;
		}

		World world = context.getWorld();
		ItemStack stack = context.getStack();
		BlockPos clickedPos = context.getBlockPos();
		Direction side = context.getSide();
		CommandMode mode = getMode(stack);
		SquadGroup squad = getTargetSquad(stack);

		// Sneak + Right-Click: Open Command Hub GUI on client (or remove waypoint if clicking a waypoint in PATHWAY mode)
		if (player.isSneaking()) {
			if (mode == CommandMode.PATHWAY) {
				BlockPos targetPos = world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
				if (!world.isClient() && world instanceof ServerWorld serverWorld) {
					PatrolRouteManager.WaypointMatch match = PatrolRouteManager.getInstance().findWaypoint(
						player.getUuid(), targetPos, clickedPos, clickedPos.up(), clickedPos.offset(side)
					);

					if (match != null) {
						int matchedRouteId = match.routeId();
						BlockPos existingWp = match.pos();
						PatrolRoute updated = PatrolRouteManager.getInstance().removeWaypoint(player.getUuid(), matchedRouteId, existingWp);
						PatrolRouteManager.getInstance().syncToPlayer((ServerPlayerEntity) player);
						if (updated.waypoints().isEmpty()) {
							List<MinionEntity> routeMinions = serverWorld.getEntitiesByClass(
								MinionEntity.class,
								player.getBoundingBox().expand(256.0D),
								m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() == matchedRouteId
							);
							for (MinionEntity m : routeMinions) {
								m.setPatrolRouteId(-1);
								m.getNavigation().stop();
							}
						}
						player.sendMessage(Text.literal("§c✦ Removed Waypoint from " + updated.getFormattedName()), true);
						world.playSound(null, existingWp.getX(), existingWp.getY(), existingWp.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
						serverWorld.spawnParticles(ParticleTypes.SMOKE, existingWp.getX() + 0.5D, existingWp.getY() + 0.5D, existingWp.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.05D);
						return ActionResult.SUCCESS;
					}
				} else if (world.isClient()) {
					if (isClientWaypoint(stack, targetPos) || isClientWaypoint(stack, clickedPos) || isClientWaypoint(stack, clickedPos.up())) {
						return ActionResult.SUCCESS;
					}
				}
			}
			if (world.isClient() && SCREEN_OPENER != null) {
				SCREEN_OPENER.openScreen(player, context.getHand(), stack);
			}
			return ActionResult.success(world.isClient());
		}

		// 1. Crosshair Entity Check: if an entity is aligned with the cursor within 64 blocks
		// (and not obstructed by terrain), prioritize entity interaction over ground waypoint.
		EntityHitResult cursorEntityHit = raycastEntityTarget(player, MINION_COMMAND_RADIUS);
		if (cursorEntityHit != null && cursorEntityHit.getEntity() instanceof LivingEntity targetEntity) {
			// Individual Minion Follow or Pathway Assignment: Raycast hitting an owned minion
			if (targetEntity instanceof MinionEntity minion && minion.isOwner(player)) {
				if (mode == CommandMode.PATHWAY) {
					if (!player.isSneaking()) {
						assignMinionToActivePatrolRoute(player, minion, stack);
					}
					return ActionResult.success(world.isClient());
				}
				commandIndividualMinionFollow(player, minion);
				return ActionResult.success(world.isClient());
			}

			if (mode == CommandMode.RECRUIT) {
				if (targetEntity instanceof MobEntity mob && !(targetEntity instanceof MinionEntity) && !(targetEntity instanceof PlayerEntity) && targetEntity.isAlive()) {
					if (!world.isClient() && world instanceof ServerWorld serverWorld) {
						transfigureEntityToMinion(serverWorld, player, mob);
					}
					return ActionResult.success(world.isClient());
				} else {
					if (!world.isClient()) {
						player.sendMessage(Text.literal("§c✦ Cannot enthrall this entity! Only living non-minion mobs can be recruited.§r"), true);
					}
					return ActionResult.FAIL;
				}
			} else if (targetEntity.isAlive() && !(targetEntity instanceof MinionEntity minion && minion.isOwner(player)) && !(targetEntity instanceof PlayerEntity)) {
				executeHostileEntityPing(player, world, targetEntity, squad);
				return ActionResult.success(world.isClient());
			}
		}

		// BUILD Mode: Anchor construction session at clicked block face (or deconstruction if sneaking)
		if (mode == CommandMode.BUILD) {
			if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
				return ActionResult.success(world.isClient());
			}
			if (!world.isClient() && world instanceof ServerWorld serverWorld) {
				executeBuildPlacement(serverWorld, player, stack, clickedPos, side, player.isSneaking());
			}
			return ActionResult.success(world.isClient());
		}

		// PATHWAY Mode: Anchor waypoint block on the active route channel (or toggle off if already placed)
		if (mode == CommandMode.PATHWAY) {
			BlockPos targetPos = world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
			if (!world.isClient() && world instanceof ServerWorld serverWorld) {
				PatrolRouteManager.WaypointMatch match = PatrolRouteManager.getInstance().findWaypoint(
					player.getUuid(), targetPos, clickedPos, clickedPos.up(), clickedPos.offset(side)
				);

				if (match != null) {
					// Universal cross-channel removal: remove from whichever route channel it belongs to
					int matchedRouteId = match.routeId();
					BlockPos existingWp = match.pos();
					PatrolRoute updated = PatrolRouteManager.getInstance().removeWaypoint(player.getUuid(), matchedRouteId, existingWp);
					PatrolRouteManager.getInstance().syncToPlayer((ServerPlayerEntity) player);
					if (updated.waypoints().isEmpty()) {
						List<MinionEntity> routeMinions = serverWorld.getEntitiesByClass(
							MinionEntity.class,
							player.getBoundingBox().expand(256.0D),
							m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() == matchedRouteId
						);
						for (MinionEntity m : routeMinions) {
							m.setPatrolRouteId(-1);
							m.getNavigation().stop();
						}
					}
					world.playSound(null, existingWp.getX(), existingWp.getY(), existingWp.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
					serverWorld.spawnParticles(ParticleTypes.SMOKE, existingWp.getX() + 0.5D, existingWp.getY() + 0.5D, existingWp.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.05D);
					player.sendMessage(Text.literal("§c✦ Removed Waypoint from " + updated.getFormattedName()), true);
				} else {
					// Zero-overlap guarantee: no route contains this position, add to active route channel
					int routeId = getActivePatrolRoute(stack);
					PatrolRoute updated = PatrolRouteManager.getInstance().addWaypoint(player.getUuid(), routeId, targetPos);
					PatrolRouteManager.getInstance().syncToPlayer((ServerPlayerEntity) player);
					world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0F, 1.2F + (updated.waypoints().size() * 0.05F));
					serverWorld.spawnParticles(ParticleTypes.ENCHANT, targetPos.getX() + 0.5D, targetPos.getY() + 1.2D, targetPos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.1D);
					player.sendMessage(Text.literal("§6✦ Added Waypoint §e#" + updated.waypoints().size() + " §6to " + updated.getFormattedName()), true);
				}
			}
			return ActionResult.success(world.isClient());
		}

		// DESIGN Mode: Right-click triggers custom blueprint capture modal if corners are set
		if (mode == CommandMode.DESIGN) {
			if (world.isClient() && CAPTURE_MODAL_OPENER != null) {
				BlockPos p1 = getDesignPos1(stack);
				BlockPos p2 = getDesignPos2(stack);
				CAPTURE_MODAL_OPENER.openCaptureModal(player, context.getHand(), stack, p1, p2);
			}
			return ActionResult.success(world.isClient());
		}

		// MINE Mode: Anchor deconstruction session at clicked block or structure (DIRECT) or open confirm modal (AREA)
		if (mode == CommandMode.MINE) {
			MiningMode miningMode = getMiningMode(stack);
			if (miningMode == MiningMode.AREA) {
				if (world.isClient() && MINE_MODAL_OPENER != null) {
					BlockPos p1 = getMinePos1(stack);
					BlockPos p2 = getMinePos2(stack);
					MINE_MODAL_OPENER.openMineModal(player, context.getHand(), stack, p1, p2);
				}
				return ActionResult.success(world.isClient());
			}
			if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
				return ActionResult.success(world.isClient());
			}
			if (!world.isClient() && world instanceof ServerWorld serverWorld) {
				executeMinePlacement(serverWorld, player, stack, clickedPos);
			}
			return ActionResult.success(world.isClient());
		}

		// Point-and-Click Waypoint Ping:
		// Right-clicking ground places a temporary beacon marker:
		// Squad marches to and holds that position.
		BlockPos waypointPos = world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
		executeGroundWaypointPing(player, world, waypointPos, squad);
		return ActionResult.success(world.isClient());
	}

	/**
	 * Anchors a construction or dismantle session on the server at the specified clicked position and face.
	 * Resolves category blueprints, procedural seeds, rotations, dynamic organic weathering, and foundation snapping.
	 */
	public static boolean executeBuildPlacement(
		ServerWorld serverWorld,
		PlayerEntity player,
		ItemStack stack,
		BlockPos clickedPos,
		Direction side,
		boolean isDismantle
	) {
		if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
			return false;
		}
		player.getItemCooldownManager().set(stack.getItem(), 10);

		BlockPos anchorPos = serverWorld.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
		String bpId = getBlueprintId(stack);

		StructureBlueprint blueprint = com.example.blueprint.BlueprintRegistry.get(bpId).orElse(null);
		if (blueprint == null || blueprint.getBlockCount() == 0) {
			player.sendMessage(Text.literal("§c⚠ No valid custom blueprint selected! Capture a structure in DESIGN mode first.§r"), true);
			return false;
		}

		// Rotate to commander's orientation with 100% exact captured blocks
		blueprint = blueprint.rotate(getRotation(stack));

		if (isDismantle) {
			ConstructionManager.getInstance().startDismantleSession(serverWorld, anchorPos, blueprint, player);
		} else {
			ConstructionManager.getInstance().startSession(serverWorld, anchorPos, blueprint, player);
		}
		return true;
	}

	/**
	 * Anchors a dismantle session on the server at the specified clicked position.
	 */
	public static boolean executeMinePlacement(
		ServerWorld serverWorld,
		PlayerEntity player,
		ItemStack stack,
		BlockPos clickedPos
	) {
		if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
			return false;
		}
		player.getItemCooldownManager().set(stack.getItem(), 10);

		BlockPos anchorPos = clickedPos;
		Optional<ConstructionSession> existing = ConstructionManager.getInstance().getSessionAt(anchorPos);
		if (existing.isEmpty()) {
			for (ConstructionSession s : ConstructionManager.getInstance().getSessionsForOwner(player.getUuid())) {
				if (s.isActive() && s.getWorldBoundingBox().contains(clickedPos)) {
					existing = Optional.of(s);
					break;
				}
			}
		}
		String bpId = getBlueprintId(stack);
		StructureBlueprint blueprint = existing.map(ConstructionSession::getBlueprint).orElseGet(() -> {
			StructureBlueprint raw = BlueprintRegistry.getOrDefault(bpId);
			return raw != null ? raw.rotate(getRotation(stack)) : null;
		});
		BlockPos targetAnchor = existing.map(ConstructionSession::getAnchorPos).orElse(anchorPos);
		ConstructionManager.getInstance().startDismantleSession(serverWorld, targetAnchor, blueprint, player);
		return true;
	}

	// -----------------------------------------------------------------------------------------
	// RIGHT-CLICK IN AIR (CHANNELED RALLY & DIRECTIVES)
	// -----------------------------------------------------------------------------------------

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		// Sneak + Right-Click: Open Command Hub GUI on client
		if (player.isSneaking()) {
			if (world.isClient() && SCREEN_OPENER != null) {
				SCREEN_OPENER.openScreen(player, hand, stack);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		CommandMode mode = getMode(stack);
		if (mode == CommandMode.DESIGN) {
			if (world.isClient() && CAPTURE_MODAL_OPENER != null) {
				BlockPos p1 = getDesignPos1(stack);
				BlockPos p2 = getDesignPos2(stack);
				CAPTURE_MODAL_OPENER.openCaptureModal(player, hand, stack, p1, p2);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		if (mode == CommandMode.MINE && getMiningMode(stack) == MiningMode.AREA) {
			if (world.isClient() && MINE_MODAL_OPENER != null) {
				BlockPos p1 = getMinePos1(stack);
				BlockPos p2 = getMinePos2(stack);
				MINE_MODAL_OPENER.openMineModal(player, hand, stack, p1, p2);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		player.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	// -----------------------------------------------------------------------------------------
	// RIGHT-CLICK ON ENTITY (RECRUIT ENTHRALLMENT & FOCUS-FIRE PINGS)
	// -----------------------------------------------------------------------------------------

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		World world = user.getWorld();
		CommandMode mode = getMode(stack);
		SquadGroup squad = getTargetSquad(stack);

		// Check primed escort first
		if (entity instanceof MinionEntity minion && minion.isOwner(user)) {
			if (handlePrimedEscort(user, minion)) {
				return ActionResult.success(world.isClient());
			}
		}

		// Sneak + Right-Click on an owned minion passes through to open the Minion Screen GUI
		if (user.isSneaking() && entity instanceof MinionEntity minion && minion.isOwner(user)) {
			return ActionResult.PASS;
		}

		// PATHWAY Mode: Right-clicking an owned minion while not shifting assigns/unassigns to the active route
		if (mode == CommandMode.PATHWAY && entity instanceof MinionEntity minion && minion.isOwner(user)) {
			assignMinionToActivePatrolRoute(user, minion, stack);
			return ActionResult.success(world.isClient());
		}

		// Individual Minion Follow: Direct right-click on an owned minion
		if (entity instanceof MinionEntity minion && minion.isOwner(user)) {
			commandIndividualMinionFollow(user, minion);
			return ActionResult.success(world.isClient());
		}

		// RECRUIT Mode: Enthrall living mob into MinionEntity
		if (mode == CommandMode.RECRUIT) {
			// Validate target: must be a living vanilla mob, not a minion, not a player
			if (!(entity instanceof MobEntity mob) || (entity instanceof MinionEntity) || (entity instanceof PlayerEntity) || !entity.isAlive()) {
				if (!world.isClient()) {
					user.sendMessage(Text.literal("§c✦ Cannot enthrall this entity! Only living non-minion mobs can be recruited.§r"), true);
				}
				return ActionResult.FAIL;
			}

			if (!world.isClient() && world instanceof ServerWorld serverWorld) {
				transfigureEntityToMinion(serverWorld, user, mob);
			}

			return ActionResult.success(world.isClient());
		}

		// Hostile entity focus-fire ping:
		// Right-clicking a hostile/attackable entity focus-fires that specific target
		if (isTargetableEntity(user, entity)) {
			executeHostileEntityPing(user, world, entity, squad);
			return ActionResult.success(world.isClient());
		}

		return ActionResult.PASS;
	}

	// -----------------------------------------------------------------------------------------
	// ENTHRALLMENT TRANSFIGURATION LIFECYCLE
	// -----------------------------------------------------------------------------------------

	/**
	 * Transfigures an arbitrary living mob into an obedient {@link MinionEntity} thrall.
	 * Copies spatial coordinates, orientation, custom name, and equipment across all 6 slots.
	 * Discards original entity, binds owner, and emits conversion VFX/SFX.
	 *
	 * @param world  The server world.
	 * @param owner  The player claiming the thrall.
	 * @param target The original mob being transfigured.
	 * @return The newly spawned MinionEntity, or null if instantiation failed.
	 */
	public static MinionEntity transfigureEntityToMinion(ServerWorld world, PlayerEntity owner, MobEntity target) {
		MinionEntity minion = ModEntities.MINION.create(world);
		if (minion == null) {
			return null;
		}

		// 1. Copy position and orientation
		minion.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
		minion.setVelocity(target.getVelocity());

		// 2. Copy custom name if present
		if (target.hasCustomName()) {
			minion.setCustomName(target.getCustomName());
			minion.setCustomNameVisible(target.isCustomNameVisible());
		}

		// 3. Clean-slate equipment & inventory: Newly converted minions start completely empty
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			minion.equipStack(slot, ItemStack.EMPTY);
		}

		// 5. Bind ownership and prime minion state in standby
		minion.setOwner(owner);
		SquadGroup heldSquad = getHeldTargetSquad(owner);
		if (!heldSquad.isWildcard()) {
			minion.setSquad(heldSquad);
		}
		minion.setRole(MinionRole.AUTO);
		minion.setSitting(true);
		minion.setGuardAnchorPos(minion.getBlockPos());
		minion.getNavigation().stop();
		minion.setTarget(null);

		// 6. Arcane particle beams and conversion audio
		world.spawnParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0, target.getZ(), 50, 0.5, 0.5, 0.5, 0.5);
		world.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.5, 0.5, 0.5, 0.2);

		world.playSound(
			null,
			target.getX(),
			target.getY(),
			target.getZ(),
			SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED,
			SoundCategory.PLAYERS,
			1.2F,
			1.0F
		);

		// 7. Discard original mob and spawn minion
		target.discard();
		world.spawnEntity(minion);

		// 8. Visual and textual user feedback
		String targetName = target.hasCustomName() ? target.getCustomName().getString() : target.getType().getName().getString();
		owner.sendMessage(Text.literal("§d✦ Enthrallment successful! Bound " + targetName + " as your minion thrall!§r"), true);

		return minion;
	}

	// -----------------------------------------------------------------------------------------
	// TACTICAL CROSSHAIR RAYCASTING & HOSTILE ACQUISITION
	// -----------------------------------------------------------------------------------------

	/**
	 * Checks if the entity is a valid hostile / attackable target for the player commander.
	 * Excludes spectators, dead entities, the commander player, player thralls owned by the commander,
	 * and other players (unless PvP rules apply).
	 *
	 * @param commander The commanding player.
	 * @param entity    The candidate entity.
	 * @return true if the entity is an attackable non-owned living entity.
	 */
	public static boolean isTargetableEntity(PlayerEntity commander, Entity entity) {
		if (entity == null || entity.isSpectator() || !entity.isAlive() || entity.equals(commander)) {
			return false;
		}
		if (entity instanceof MinionEntity minion && minion.isOwner(commander)) {
			return false;
		}
		if (entity instanceof PlayerEntity) {
			return false;
		}
		return entity instanceof LivingEntity;
	}

	/**
	 * Checks if an entity is an interactable candidate for crosshair raycasting.
	 * Returns true for owned minions (enabling individual minion follow commands)
	 * as well as valid hostile/attackable living entities.
	 *
	 * @param commander The commanding player.
	 * @param entity    The candidate entity.
	 * @return true if the entity is an owned minion or a valid targetable entity.
	 */
	public static boolean isRaycastInteractableEntity(PlayerEntity commander, Entity entity) {
		if (entity == null || entity.isSpectator() || !entity.isAlive() || entity.equals(commander)) {
			return false;
		}
		if (entity instanceof MinionEntity minion && minion.isOwner(commander)) {
			return true;
		}
		return isTargetableEntity(commander, entity);
	}

	/**
	 * Resolves the 3D center coordinate of an entity for precise line-of-sight and distance calculations.
	 *
	 * @param entity The target entity.
	 * @return Center coordinate vector.
	 */
	public static Vec3d getEntityCenter(Entity entity) {
		return new Vec3d(entity.getX(), entity.getY() + (entity.getHeight() * 0.5D), entity.getZ());
	}

	/**
	 * Calculates the squared perpendicular distance from a 3D point to a finite ray segment [rayStart, rayStart + rayDir * maxRange].
	 * Clamps projection parameter t to [0, maxRange] to correctly penalize points behind the ray or past its tip.
	 *
	 * @param rayStart Starting origin of the ray.
	 * @param rayDir   Direction vector of the ray.
	 * @param maxRange Maximum range of the ray.
	 * @param point    The target point in space.
	 * @return Squared distance from the point to the ray segment.
	 */
	public static double calculateDistanceSqToRay(Vec3d rayStart, Vec3d rayDir, double maxRange, Vec3d point) {
		if (rayDir.lengthSquared() < 1e-7) {
			return point.squaredDistanceTo(rayStart);
		}
		Vec3d dir = rayDir.normalize();
		Vec3d toPoint = point.subtract(rayStart);
		double t = toPoint.dotProduct(dir);
		double tClamped = Math.max(0.0D, Math.min(maxRange, t));
		Vec3d closestPointOnRay = rayStart.add(dir.multiply(tClamped));
		return point.squaredDistanceTo(closestPointOnRay);
	}

	/**
	 * Projects player crosshair line of sight up to {@code range} blocks, clipping against solid terrain
	 * and returning an {@link EntityHitResult} if an interactable living entity (owned minion or hostile)
	 * is aligned with the cursor.
	 *
	 * @param player The commanding player.
	 * @param range  Maximum raycast distance (typically {@link #MINION_COMMAND_RADIUS}).
	 * @return EntityHitResult if a valid entity intersects the crosshair, or null otherwise.
	 */
	public static EntityHitResult raycastEntityTarget(PlayerEntity player, double range) {
		Vec3d start = player.getCameraPosVec(1.0F);
		Vec3d rotation = player.getRotationVec(1.0F);
		Vec3d end = start.add(rotation.multiply(range));

		// 1. Clip against solid blocks in line of sight so entities behind solid walls are obstructed
		RaycastContext blockContext = new RaycastContext(
			start,
			end,
			RaycastContext.ShapeType.OUTLINE,
			RaycastContext.FluidHandling.NONE,
			player
		);
		BlockHitResult blockHit = player.getWorld().raycast(blockContext);
		double maxDistanceSq = range * range;
		if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
			maxDistanceSq = start.squaredDistanceTo(blockHit.getPos());
		}

		// 2. Query entities within reach bounded by the block obstruction
		Box box = player.getBoundingBox().stretch(rotation.multiply(range)).expand(1.0D);
		return ProjectileUtil.raycast(
			player,
			start,
			end,
			box,
			entity -> isRaycastInteractableEntity(player, entity),
			maxDistanceSq
		);
	}

	/**
	 * Raycasts ground, structures, and blocks along the player's crosshair up to {@code range} blocks.
	 * In client contexts, respects the active camera position and orientation if available.
	 *
	 * @param player The commanding player.
	 * @param range  Maximum raycast distance (typically {@link #MINION_COMMAND_RADIUS}).
	 * @return BlockHitResult representing block contact or miss.
	 */
	public static BlockHitResult raycastBlockTarget(PlayerEntity player, double range) {
		if (player.getWorld().isClient() && CLIENT_TARGET_RESOLVER != null) {
			BlockHitResult clientHit = CLIENT_TARGET_RESOLVER.getTarget(player);
			if (clientHit != null) {
				return clientHit;
			}
		}
		Vec3d start = player.getCameraPosVec(1.0F);
		Vec3d rotation = player.getRotationVec(1.0F);
		Vec3d end = start.add(rotation.multiply(range));
		RaycastContext context = new RaycastContext(
			start,
			end,
			RaycastContext.ShapeType.OUTLINE,
			RaycastContext.FluidHandling.NONE,
			player
		);
		return player.getWorld().raycast(context);
	}

	/**
	 * Combined crosshair raycast targeting: prioritizes entity targets unless obstructed by terrain,
	 * falling back to block hits up to {@code range} blocks.
	 *
	 * @param player The commanding player.
	 * @param range  Maximum raycast distance.
	 * @return EntityHitResult if an entity is in crosshairs, BlockHitResult if block is hit, or null if miss.
	 */
	public static HitResult raycastTarget(PlayerEntity player, double range) {
		EntityHitResult entityHit = raycastEntityTarget(player, range);
		if (entityHit != null && entityHit.getType() != HitResult.Type.MISS) {
			return entityHit;
		}
		BlockHitResult blockHit = raycastBlockTarget(player, range);
		if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
			return blockHit;
		}
		return blockHit;
	}

	/**
	 * Finds hostile mobs within {@code searchRadius} of an origin point, sorted by proximity
	 * to the commanding player's crosshair line-of-sight raycast vector.
	 *
	 * @param world        The world instance.
	 * @param player       The commanding player.
	 * @param origin       The center coordinates for bounding box search.
	 * @param searchRadius Search radius.
	 * @return The best aligned hostile MobEntity, or null if none found.
	 */
	public static MobEntity findBestHostileTargetNear(World world, PlayerEntity player, Vec3d origin, double searchRadius) {
		Box searchBox = new Box(
			origin.x - searchRadius, origin.y - searchRadius, origin.z - searchRadius,
			origin.x + searchRadius, origin.y + searchRadius, origin.z + searchRadius
		);
		List<MobEntity> hostiles = world.getEntitiesByClass(
			MobEntity.class,
			searchBox,
			e -> isTargetableEntity(player, e) && (e instanceof MobEntity)
		);
		if (hostiles.isEmpty()) {
			return null;
		}

		Vec3d rayStart = player.getCameraPosVec(1.0F);
		Vec3d rayDir = player.getRotationVec(1.0F);
		hostiles.sort(Comparator
			.comparingDouble((MobEntity mob) -> calculateDistanceSqToRay(rayStart, rayDir, MINION_COMMAND_RADIUS, getEntityCenter(mob)))
			.thenComparingDouble(mob -> mob.squaredDistanceTo(player))
		);
		return hostiles.get(0);
	}

	// -----------------------------------------------------------------------------------------
	// INDIVIDUAL MINION DIRECTIVES
	// -----------------------------------------------------------------------------------------

	/**
	 * Assigns or unassigns an owned minion (or all currently selected minions) to the player's active patrol route.
	 *
	 * - If route has no waypoints: alerts the player and plays warning bass sound.
	 * - If player has minions selected: assigns all selected minions + targetMinion to the active route,
	 *   clears their leader, clears anchor/sitting, deselects them from following the player, and begins patrolling.
	 * - If player has no minions selected: toggles targetMinion onto/off the active route.
	 *
	 * @param player The commanding player.
	 * @param targetMinion The target owned minion.
	 * @param stack The Command Scepter item stack holding active route channel info.
	 * @return true if an assignment or warning was handled, false if invalid target.
	 */
	public static boolean assignMinionToActivePatrolRoute(PlayerEntity player, MinionEntity targetMinion, ItemStack stack) {
		if (player == null || targetMinion == null || !targetMinion.isAlive() || !targetMinion.isOwner(player)) {
			return false;
		}

		World world = player.getWorld();
		int routeId = getActivePatrolRoute(stack);
		PatrolRoute route = PatrolRouteManager.getInstance().getRoute(player.getUuid(), routeId);
		if (route == null || route.waypoints().isEmpty()) {
			if (!world.isClient()) {
				player.sendMessage(Text.literal("§c⚠ Cannot assign minion to " + (route != null ? route.getFormattedName() : "Route") + " — no waypoints placed yet!"), true);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0F, 0.6F);
			}
			return true;
		}

		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			List<MinionEntity> selectedMinions = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected()
			);

			if (!selectedMinions.isEmpty()) {
				java.util.Set<MinionEntity> toAssign = new java.util.LinkedHashSet<>(selectedMinions);
				toAssign.add(targetMinion);
				for (MinionEntity m : toAssign) {
					m.setPatrolRouteId(routeId);
					m.setCurrentWaypointIndex(0);
					m.clearLeader();
					m.setSitting(false);
					m.setGuardAnchorPos(null);
					m.setSelected(false);
					m.getNavigation().stop();
					serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1.2D, m.getZ(), 8, 0.25, 0.4, 0.25, 0.05);
				}
				serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.4F);
				player.sendMessage(Text.literal("§a✦ Assigned " + toAssign.size() + " minion(s) to " + route.getFormattedName() + " (" + route.waypoints().size() + " waypoints)!"), true);
				return true;
			}

			if (targetMinion.getPatrolRouteId() == routeId) {
				targetMinion.setPatrolRouteId(-1);
				targetMinion.getNavigation().stop();
				serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
				player.sendMessage(Text.literal("§e✦ " + targetMinion.getRole().getDisplayName() + " removed from patrol duty."), true);
			} else {
				targetMinion.setPatrolRouteId(routeId);
				targetMinion.setCurrentWaypointIndex(0);
				targetMinion.clearLeader();
				targetMinion.setSitting(false);
				targetMinion.setGuardAnchorPos(null);
				targetMinion.setSelected(false);
				targetMinion.getNavigation().stop();
				serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetMinion.getX(), targetMinion.getY() + 1.2D, targetMinion.getZ(), 8, 0.25, 0.4, 0.25, 0.05);
				serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.4F);
				player.sendMessage(Text.literal("§a✦ Assigned " + targetMinion.getRole().getDisplayName() + " to " + route.getFormattedName() + " (" + route.waypoints().size() + " waypoints)!"), true);
			}
		}
		return true;
	}

	/**
	 * Commands an individual owned minion to break its guard/sitting station and follow the commanding player.
	 * Toggles selection of an individual minion:
	 * If unselected, selects the minion, clears anchor, and orders it to follow.
	 * If already selected, deselects the minion and anchors it to hold its current post without sitting.
	 *
	 * @param player The commanding player.
	 * @param minion The owned minion to command.
	 */
	public static void commandIndividualMinionFollow(PlayerEntity player, MinionEntity minion) {
		toggleMinionSelection(player, minion);
	}

	/**
	 * Toggles the tactical selection status of an owned minion:
	 * - Selecting: Clears guard anchor, activates following navigation, plays chime SFX, and emits heart particles.
	 * - Deselecting: Anchors the minion at its current block position so it holds its post without sitting,
	 *   plays bass SFX, and emits smoke particles.
	 *
	 * @param player The commanding player.
	 * @param minion The owned minion.
	 */
	public static void toggleMinionSelection(PlayerEntity player, MinionEntity minion) {
		if (minion == null || !minion.isAlive() || !minion.isOwner(player)) {
			return;
		}

		World world = minion.getWorld();
		boolean hadLeader = minion.hasLeader();
		boolean hadPatrolRoute = minion.getPatrolRouteId() >= 0;
		boolean willSelect = !minion.isSelected();
		minion.setSelected(willSelect);

		if (willSelect) {
			if (hadPatrolRoute) {
				minion.setPatrolRouteId(-1);
			}
			minion.setSitting(false);
			minion.setGuardAnchorPos(null);
			minion.clearAssaultTargets();
			minion.setTarget(null);
			minion.getNavigation().startMovingTo(player, 1.35D);
		} else {
			if (minion.getPatrolRouteId() < 0 && !minion.hasLeader()) {
				minion.setGuardAnchorPos(minion.getBlockPos());
			}
			minion.clearAssaultTargets();
			minion.setTarget(null);
			minion.getNavigation().stop();
		}

		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			int selectedCount = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected()
			).size();

			String name = minion.hasCustomName() ? minion.getCustomName().getString() : "Minion (" + minion.getRole().getDisplayName() + ")";

			if (willSelect) {
				serverWorld.spawnParticles(
					ParticleTypes.HEART,
					minion.getX(),
					minion.getY() + minion.getHeight() + 0.25D,
					minion.getZ(),
					6,
					0.25D,
					0.25D,
					0.25D,
					0.1D
				);
				serverWorld.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.5F);
				serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 1.5F);
				String escortNote = hadLeader ? " §e(Detached from escort)§r" : (hadPatrolRoute ? " §e(Detached from patrol route)§r" : "");
				player.sendMessage(
					Text.literal("§a✦ Minion Selected: §f" + name + escortNote + " §8[Selected: " + selectedCount + "]§r"),
					true
				);
			} else {
				serverWorld.spawnParticles(
					ParticleTypes.SMOKE,
					minion.getX(),
					minion.getY() + minion.getHeight() + 0.25D,
					minion.getZ(),
					6,
					0.2D,
					0.2D,
					0.2D,
					0.05D
				);
				serverWorld.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0F, 0.8F);
				serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8F, 0.8F);
				player.sendMessage(
					Text.literal("§7✦ Minion Deselected: §f" + name + " §8[Selected: " + selectedCount + "]§r"),
					true
				);
			}
		}
	}

	/**
	 * Deselects all owned minions within the command radius (64 blocks),
	 * anchoring each standing minion at its current position so they hold their ground without sitting.
	 *
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @return The number of minions deselected.
	 */
	public static int deselectAllMinions(PlayerEntity player, World world) {
		if (player == null) {
			return 0;
		}

		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS * 2.0D);
			List<MinionEntity> selectedMinions = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected()
			);

			for (MinionEntity minion : selectedMinions) {
				minion.setSelected(false);
				minion.setGuardAnchorPos(minion.getBlockPos());
				minion.clearAssaultTargets();
				minion.setTarget(null);
				minion.getNavigation().stop();
			}

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.9F, 0.6F);

			if (!selectedMinions.isEmpty()) {
				player.sendMessage(
					Text.literal("§e✦ Deselected all minions (" + selectedMinions.size() + " unit(s) stationed at their posts).§r"),
					true
				);
			} else {
				player.sendMessage(
					Text.literal("§7✦ No minions were currently selected.§r"),
					true
				);
			}
			return selectedMinions.size();
		}
		return 0;
	}

	// -----------------------------------------------------------------------------------------
	// TACTICAL WAYPOINT & FOCUS-FIRE PINGS
	// -----------------------------------------------------------------------------------------

	/**
	 * Executes a tactical ground waypoint ping:
	 * Moves ONLY currently selected minions matching the active squad channel filter.
	 * Commands selected minions to sprint to the target position, and anchors them to hold that post.
	 * If no minions are selected, emits audio-visual fail feedback and does not move unselected units.
	 *
	 * @param player      The commanding player.
	 * @param world       The world instance.
	 * @param targetPos   The targeted block coordinates.
	 * @param targetSquad The squad channel filter.
	 */
	public static void executeGroundWaypointPing(PlayerEntity player, World world, BlockPos targetPos, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}

		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> minions = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected() && filterSquad.matches(m.getSquad())
			);

			if (minions.isEmpty()) {
				serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.2F);
				player.sendMessage(
					Text.literal("§e✦ No minions selected! Aim at an owned minion with the Scepter to select them first.§r"),
					true
				);
				return;
			}

			// Calculate commander facing angle towards targetPos (or player yaw fallback)
			double dirX = (targetPos.getX() + 0.5D) - player.getX();
			double dirZ = (targetPos.getZ() + 0.5D) - player.getZ();
			float facingYaw = (dirX * dirX + dirZ * dirZ > 1.0D)
				? (float) Math.toDegrees(Math.atan2(-dirX, dirZ))
				: player.getYaw();

			for (MinionEntity minion : minions) {
				int rank = MinionFormationFollowGoal.resolveRank(minions, minion, MinionEntity::getRole, Entity::getId);
				Vec3d rawStation = MinionFormationFollowGoal.calculateFormationStation(
					targetPos.getX() + 0.5D,
					targetPos.getY(),
					targetPos.getZ() + 0.5D,
					facingYaw,
					minion.getRole(),
					rank
				);

				BlockPos groundStationPos = findSafeWaypointGround(serverWorld, BlockPos.ofFloored(rawStation.x, targetPos.getY(), rawStation.z), targetPos.getY());
				double stationX = rawStation.x;
				double stationY = groundStationPos.getY();
				double stationZ = rawStation.z;

				minion.setSelected(false);
				minion.setSitting(false);
				minion.clearAssaultTargets();
				minion.setTarget(null);
				minion.setGuardAnchorPos(groundStationPos);
				minion.setActiveTraversalDestination(new Vec3d(stationX, stationY, stationZ));
				minion.getNavigation().startMovingTo(
					stationX,
					stationY,
					stationZ,
					1.35D
				);

				// Subtle formation footprint particle marker
				serverWorld.spawnParticles(ParticleTypes.PORTAL, stationX, stationY + 0.1D, stationZ, 3, 0.05, 0.05, 0.05, 0.02);
			}

			// Beacon beam particle column (vertical column of END_ROD and GLOW)
			double px = targetPos.getX() + 0.5D;
			double pz = targetPos.getZ() + 0.5D;
			for (int y = 0; y <= 6; y++) {
				double py = targetPos.getY() + 0.2D + (y * 0.75D);
				serverWorld.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 3, 0.08, 0.1, 0.08, 0.01);
				serverWorld.spawnParticles(ParticleTypes.GLOW, px, py, pz, 4, 0.12, 0.15, 0.12, 0.02);
			}

			// Beacon activation SFX
			serverWorld.playSound(
				null,
				px,
				targetPos.getY() + 0.5D,
				pz,
				SoundEvents.BLOCK_BEACON_ACTIVATE,
				SoundCategory.PLAYERS,
				1.2F,
				1.2F
			);

			String squadLabel = filterSquad.getFormattedName();
			player.sendMessage(
				Text.literal("§b✦ Waypoint Formation [" + squadLabel + "§b]: " + minions.size() + " selected minion(s) deployed into formation around [" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + "]!§r"),
				true
			);
		}
	}

	/**
	 * Scans vertically within a small window around baseY to locate a solid standing block with 2 blocks of air clearance.
	 */
	public static BlockPos findSafeWaypointGround(World world, BlockPos pos, int baseY) {
		int startY = Math.min(baseY + 3, world.getTopY() - 2);
		int endY = Math.max(baseY - 4, world.getBottomY() + 1);
		for (int y = startY; y >= endY; y--) {
			BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
			BlockState groundState = world.getBlockState(check);
			BlockState feetState = world.getBlockState(check.up());
			BlockState headState = world.getBlockState(check.up(2));
			if (!groundState.isAir() && groundState.isSolidBlock(world, check) && feetState.isAir() && headState.isAir()) {
				return check.up();
			}
		}
		return new BlockPos(pos.getX(), baseY, pos.getZ());
	}

	/**
	 * Executes a tactical hostile entity focus-fire ping:
	 * Spawns lock-on particles (ANGRY_VILLAGER and CRIT) on the target, plays note block drum SFX,
	 * and directs all matching squad minions to focus-fire that specific entity.
	 *
	 * @param player      The commanding player.
	 * @param world       The world instance.
	 * @param target      The target entity to assault.
	 * @param targetSquad The squad channel filter.
	 */
	public static void executeHostileEntityPing(PlayerEntity player, World world, LivingEntity target, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}

		if (!world.isClient() && world instanceof ServerWorld serverWorld) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> minions = serverWorld.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected() && filterSquad.matches(m.getSquad())
			);
			if (minions.isEmpty()) {
				minions = serverWorld.getEntitiesByClass(
					MinionEntity.class,
					searchBox,
					m -> m.isAlive() && m.isOwner(player) && !m.isSitting() && m.getPatrolRouteId() < 0 && filterSquad.matches(m.getSquad())
				);
			}

			for (MinionEntity minion : minions) {
				minion.setSitting(false);
				minion.setTarget(target);
				minion.getNavigation().startMovingTo(target, 1.35D);
			}

			// Lock-on particles: ANGRY_VILLAGER and CRIT around the target
			double tx = target.getX();
			double ty = target.getY() + target.getHeight() * 0.5D;
			double tz = target.getZ();
			serverWorld.spawnParticles(ParticleTypes.ANGRY_VILLAGER, tx, ty, tz, 8, 0.3, 0.4, 0.3, 0.02);
			serverWorld.spawnParticles(ParticleTypes.CRIT, tx, ty, tz, 20, 0.4, 0.5, 0.4, 0.15);

			// Note block drum SFX
			serverWorld.playSound(
				null,
				tx,
				ty,
				tz,
				SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(),
				SoundCategory.PLAYERS,
				1.5F,
				0.8F
			);
			serverWorld.playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(),
				SoundCategory.PLAYERS,
				1.2F,
				1.0F
			);

			String squadLabel = filterSquad.getFormattedName();
			String targetName = target.getName().getString();
			player.sendMessage(
				Text.literal("§c⚔ Focus-Fire Ping [" + squadLabel + "§c]: " + minions.size() + " unit(s) focus-firing " + targetName + "!§r"),
				true
			);
		}
	}

	// -----------------------------------------------------------------------------------------
	// TACTICAL BROADCAST HELPERS & DIRECTIVE EXECUTION
	// -----------------------------------------------------------------------------------------

	public static void executeDirective(PlayerEntity player, World world, CommandMode mode) {
		executeDirective(player, world, mode, getHeldTargetSquad(player));
	}

	public static void executeDirective(PlayerEntity player, World world, CommandMode mode, SquadGroup targetSquad) {
		ItemStack stack = getHeldScepter(player);
		switch (mode) {
			case FOLLOW -> broadcastFollow(player, world, targetSquad);
			case STAY -> broadcastStay(player, world, targetSquad);
			case MINE -> broadcastMine(player, world, targetSquad);
			case RECRUIT -> sendRecruitTip(player, world);
			case PATHWAY -> broadcastPathway(player, world, targetSquad);
			case DESIGN -> {
				if (!world.isClient()) {
					player.sendMessage(Text.literal("§d✦ Design Mode Active: Left-click twice for Pos1 and Pos2, Right-click to capture blueprint.§r"), true);
				}
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.7F);
			}
			case BUILD -> {
				if (!world.isClient()) {
					String bpId = stack.isEmpty() ? "" : getBlueprintId(stack);
					StructureBlueprint bp = BlueprintRegistry.get(bpId).orElse(null);
					if (bp != null && bp.getBlockCount() > 0) {
						player.sendMessage(Text.literal("§b✦ Build Mode Active: Right-click ground to anchor " + bp.getName() + "!§r"), true);
					} else {
						player.sendMessage(Text.literal("§b✦ Build Mode Active: No blueprint selected. Capture a structure in DESIGN mode!§r"), true);
					}
				}
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.2F);
			}
		}
	}

	public static void broadcastPathway(PlayerEntity player, World world, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		if (!world.isClient()) {
			ItemStack stack = getHeldScepter(player);
			int routeId = getActivePatrolRoute(stack);
			PatrolRoute route = PatrolRouteManager.getInstance().getRoute(player.getUuid(), routeId);
			if (route.waypoints().isEmpty()) {
				player.sendMessage(Text.literal("§c✦ Cannot assign patrol: " + route.getFormattedName() + " has no waypoints! Right-click blocks to set points."), true);
				return;
			}
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && filterSquad.matches(m.getSquad())
			);

			for (MinionEntity minion : minions) {
				minion.setSelected(false);
				minion.setPatrolRouteId(routeId);
				minion.setCurrentWaypointIndex(0);
				minion.clearLeader();
				minion.setSitting(false);
				minion.setGuardAnchorPos(null);
				minion.getNavigation().stop();
			}

			String squadLabel = filterSquad.getFormattedName();
			player.sendMessage(Text.literal("§a✦ Assigned " + minions.size() + " Minion(s) [" + squadLabel + "§a] to " + route.getFormattedName() + " (" + route.waypoints().size() + " waypoints)!"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.4F);
	}

	public static void broadcastFollow(PlayerEntity player, World world) {
		broadcastFollow(player, world, getHeldTargetSquad(player));
	}

	public static void broadcastFollow(PlayerEntity player, World world, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && !m.hasLeader() && m.getPatrolRouteId() < 0 && filterSquad.matches(m.getSquad())
			);

			for (MinionEntity minion : minions) {
				minion.setSelected(true);
				minion.setGuardAnchorPos(null);
				minion.setSitting(false);
				minion.clearAssaultTargets();
				minion.setTarget(null);
				minion.getNavigation().startMovingTo(player, 1.25D);
			}

			MinionFormationFollowGoal.refreshFormationAnchor(player);

			String squadLabel = filterSquad.getFormattedName();
			player.sendMessage(Text.literal("§a✦ Command: " + minions.size() + " Minion(s) [" + squadLabel + "§a] Selected & Following!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.2F);
	}

	public static void broadcastStay(PlayerEntity player, World world) {
		broadcastStay(player, world, getHeldTargetSquad(player));
	}

	public static void broadcastStay(PlayerEntity player, World world, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() < 0 && filterSquad.matches(m.getSquad())
			);

			for (MinionEntity minion : minions) {
				minion.setSelected(false);
				minion.setSitting(true);
				minion.setGuardAnchorPos(minion.getBlockPos());
				minion.clearAssaultTargets();
				minion.getNavigation().stop();
				minion.setTarget(null);
			}

			String squadLabel = filterSquad.getFormattedName();
			player.sendMessage(Text.literal("§e✦ Command: " + minions.size() + " Minion(s) [" + squadLabel + "§e] Holding Position!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}

	public static void broadcastAttack(PlayerEntity player, World world) {
		broadcastAttack(player, world, getHeldTargetSquad(player));
	}

	public static void broadcastAttack(PlayerEntity player, World world, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected() && filterSquad.matches(m.getSquad())
			);
			if (minions.isEmpty()) {
				minions = world.getEntitiesByClass(
					MinionEntity.class,
					searchBox,
					m -> m.isAlive() && m.isOwner(player) && !m.isSitting() && filterSquad.matches(m.getSquad())
				);
			}

			// Find hostiles within 64 blocks (MINION_COMMAND_RADIUS) sorted by crosshair vector proximity
			MobEntity primaryTarget = findBestHostileTargetNear(world, player, player.getCameraPosVec(1.0F), MINION_COMMAND_RADIUS);

			String squadLabel = filterSquad.getFormattedName();
			if (primaryTarget != null) {
				for (MinionEntity minion : minions) {
					minion.setSitting(false);
					minion.setTarget(primaryTarget);
					minion.getNavigation().startMovingTo(primaryTarget, 1.35D);
				}
				player.sendMessage(Text.literal("§c✦ " + minions.size() + " Minion(s) [" + squadLabel + "§c] attacking " + primaryTarget.getName().getString() + "!§r"), true);
			} else {
				player.sendMessage(Text.literal("§c✦ Attack Mode [" + squadLabel + "§c]: Minions primed! Target an enemy or click near hostiles.§r"), true);
			}
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.5F, 1.8F);
	}

	public static void directMinionsToAttack(PlayerEntity player, LivingEntity target, World world) {
		directMinionsToAttack(player, target, world, getHeldTargetSquad(player));
	}

	public static void directMinionsToAttack(PlayerEntity player, LivingEntity target, World world, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
		SquadGroup filterSquad = targetSquad;
		List<MinionEntity> minions = world.getEntitiesByClass(
			MinionEntity.class,
			searchBox,
			m -> m.isAlive() && m.isOwner(player) && m.isSelected() && filterSquad.matches(m.getSquad())
		);
		if (minions.isEmpty()) {
			minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && !m.isSitting() && filterSquad.matches(m.getSquad())
			);
		}

		for (MinionEntity minion : minions) {
			minion.setSitting(false);
			minion.setTarget(target);
		}

		String squadLabel = filterSquad.getFormattedName();
		player.sendMessage(Text.literal("§c✦ Minions [" + squadLabel + "§c] (" + minions.size() + ") commanded to attack: " + target.getName().getString() + "!§r"), true);
	}

	public static void executeRetreat(PlayerEntity player, World world) {
		executeRetreat(player, world, getHeldTargetSquad(player));
	}

	public static void executeRetreat(PlayerEntity player, World world, SquadGroup targetSquad) {
		executeRetreat(player, world, targetSquad, false);
	}

	public static void executeRetreat(PlayerEntity player, World world, SquadGroup targetSquad, boolean isEmergencyCitadelCall) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		SquadGroup filterSquad = targetSquad;
		double radius = isEmergencyCitadelCall ? 128.0D : 64.0D;
		Box searchBox = player.getBoundingBox().expand(radius);
		List<MinionEntity> minions = world.getEntitiesByClass(
			MinionEntity.class,
			searchBox,
			m -> m.isAlive() && m.isOwner(player) && (isEmergencyCitadelCall || (!m.hasLeader() && m.getPatrolRouteId() < 0 && filterSquad.matches(m.getSquad())))
		);

		MinionFormationFollowGoal.refreshFormationAnchor(player);

		Map<MinionRole, List<MinionEntity>> byRole = new HashMap<>();
		for (MinionEntity minion : minions) {
			byRole.computeIfAbsent(minion.getEffectiveRole(), r -> new ArrayList<>()).add(minion);
		}

		for (List<MinionEntity> roleMinions : byRole.values()) {
			roleMinions.sort(Comparator.comparingInt(MinionEntity::getId));
			for (int rank = 0; rank < roleMinions.size(); rank++) {
				MinionEntity minion = roleMinions.get(rank);
				minion.setSitting(false);
				minion.setGuardAnchorPos(null);
				minion.clearAssaultTargets();
				minion.setTarget(null);
				minion.setAttacking(false);
				minion.setSelected(true);
				if (isEmergencyCitadelCall) {
					minion.setPatrolRouteId(-1); // Recall sentries from patrol routes
					minion.clearLeader(); // Recall escorting units to stronghold master
				}
				Vec3d station = MinionFormationFollowGoal.calculateFormationStation(player, minion.getEffectiveRole(), rank);
				double walkableY = MinionFormationFollowGoal.resolveWalkableY(world, station.x, player.getY(), station.z);
				minion.getNavigation().startMovingTo(station.x, walkableY, station.z, 1.50D);
				if (world instanceof ServerWorld serverWorld) {
					serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, minion.getX(), minion.getY() + 0.8D, minion.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.02D);
				}
			}
		}

		if (world instanceof ServerWorld serverWorld) {
			com.example.construction.ConstructionManager.getInstance().cancelSessionsForOwner(player.getUuid(), serverWorld);
		}

		if (isEmergencyCitadelCall) {
			SoundEvent hornSound = !SoundEvents.GOAT_HORN_SOUNDS.isEmpty()
				? SoundEvents.GOAT_HORN_SOUNDS.get(0).value()
				: SoundEvents.ITEM_GOAT_HORN_PLAY;
			world.playSound(null, player.getX(), player.getY(), player.getZ(), hornSound, SoundCategory.PLAYERS, 2.0F, 0.8F);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.8F, 1.0F);
			player.sendMessage(Text.literal("§c🚨 EMERGENCY CITADEL CALL! Recalled all " + minions.size() + " base units to stronghold!§r"), true);
		} else {
			world.playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.BLOCK_BELL_USE,
				SoundCategory.PLAYERS,
				1.5F,
				1.1F
			);
			String squadLabel = filterSquad.getFormattedName();
			player.sendMessage(Text.literal("§e🔔 Tactical Retreat! Recalled " + minions.size() + " minion(s) [" + squadLabel + "§e] to formation!§r"), true);
		}
	}

	public static void broadcastMine(PlayerEntity player, World world) {
		broadcastMine(player, world, getHeldTargetSquad(player));
	}

	public static void broadcastMine(PlayerEntity player, World world, SquadGroup targetSquad) {
		if (targetSquad == null) {
			targetSquad = SquadGroup.ALL;
		}
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			SquadGroup filterSquad = targetSquad;
			List<MinionEntity> miners = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.matchesRole(MinionRole.BUILDER) && filterSquad.matches(m.getSquad())
			);

			String squadLabel = filterSquad.getFormattedName();
			player.sendMessage(
				Text.literal("§6⛏ Mine Mode [" + squadLabel + "§6]: " + miners.size() + " Builder(s) ready for excavation directives!§r"),
				true
			);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}

	private static void sendRecruitTip(PlayerEntity player, World world) {
		if (!world.isClient()) {
			player.sendMessage(Text.literal("§d✦ Recruit Mode Active: Right-click living mobs to transfigure them into Minions!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.PLAYERS, 0.8F, 1.2F);
	}

	// -----------------------------------------------------------------------------------------
	// ITEM PROPERTIES & TOOLTIP
	// -----------------------------------------------------------------------------------------

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		CommandMode mode = getMode(stack);
		SquadGroup squad = getTargetSquad(stack);
		MinionRole targetRole = getTargetRole(stack);
		tooltip.add(Text.literal("§7Command Mode: §r" + mode.getFormattedName()));
		tooltip.add(Text.literal("§7Target Squad: §r" + squad.getFormattedName()));
		if (targetRole != null) {
			tooltip.add(Text.literal("§7Target Archetype: §r" + targetRole.getFormattedName()));
		}

		if (mode == CommandMode.DESIGN) {
			BlockPos p1 = getDesignPos1(stack);
			BlockPos p2 = getDesignPos2(stack);
			tooltip.add(Text.literal("§7Pos1 (Corner 1): §d" + (p1 != null ? "[" + p1.toShortString() + "]" : "Not Set")));
			tooltip.add(Text.literal("§7Pos2 (Corner 2): §d" + (p2 != null ? "[" + p2.toShortString() + "]" : "Not Set")));
			if (p1 != null && p2 != null) {
				int sx = Math.abs(p1.getX() - p2.getX()) + 1;
				int sy = Math.abs(p1.getY() - p2.getY()) + 1;
				int sz = Math.abs(p1.getZ() - p2.getZ()) + 1;
				tooltip.add(Text.literal("§7Volume: §e" + sx + "x" + sy + "x" + sz + " §8(" + (sx * sy * sz) + " blocks)"));
			}
		}

		if (mode == CommandMode.MINE) {
			MiningMode miningMode = getMiningMode(stack);
			tooltip.add(Text.literal("§7Mining Mode: §r" + miningMode.getFormattedName()));
			if (miningMode == MiningMode.AREA) {
				BlockPos p1 = getMinePos1(stack);
				BlockPos p2 = getMinePos2(stack);
				tooltip.add(Text.literal("§7Mine Pos1: §e" + (p1 != null ? "[" + p1.toShortString() + "]" : "Not Set")));
				tooltip.add(Text.literal("§7Mine Pos2: §e" + (p2 != null ? "[" + p2.toShortString() + "]" : "Not Set")));
				if (p1 != null && p2 != null) {
					int sx = Math.abs(p1.getX() - p2.getX()) + 1;
					int sy = Math.abs(p1.getY() - p2.getY()) + 1;
					int sz = Math.abs(p1.getZ() - p2.getZ()) + 1;
					tooltip.add(Text.literal("§7Area Volume: §e" + sx + "x" + sy + "x" + sz + " §8(" + (sx * sy * sz) + " blocks)"));
				}
			}
		}

		if (mode == CommandMode.BUILD) {
			String bpId = getBlueprintId(stack);
			StructureBlueprint bp = BlueprintRegistry.get(bpId).orElse(null);
			if (bp != null && bp.getBlockCount() > 0) {
				int degrees = getRotationIndex(stack) * 90;
				tooltip.add(Text.literal("§7Active Blueprint: §b" + bp.getName() + " §8(" + bp.getBlockCount() + " blocks)"));
				tooltip.add(Text.literal("§7Rotation: §b" + degrees + "° §7(" + getRotation(stack).name() + ")"));
			} else {
				tooltip.add(Text.literal("§7Active Blueprint: §cNone (Use DESIGN mode)"));
			}
		}

		if (mode == CommandMode.PATHWAY) {
			int routeId = getActivePatrolRoute(stack);
			tooltip.add(Text.literal("§7Active Route: " + PatrolRoute.CHANNEL_FORMATTED_NAMES[routeId]));
		}

		tooltip.add(Text.empty());
		tooltip.add(Text.literal("§8• Shift + Right-Click / Press [V]: Open Command Hub GUI"));
		tooltip.add(Text.literal("§8• Hold Right-Click: Channel Banner of Courage Rally Ring"));
		tooltip.add(Text.literal("§8• Right-Click Minion: Select / Deselect Unit (Squad Glowing Outline)"));
		tooltip.add(Text.literal("§8• Right-Click Ground: 32b Waypoint Ping (Selected Units Move & Hold)"));
		tooltip.add(Text.literal("§8• Right-Click Hostile: 32b Focus-Fire Ping (Crosshair Aiming & Drum SFX)"));
		tooltip.add(Text.literal("§8• Shift + Left-Click: Deselect All Minions (Cycle Rotation in Build)"));
		tooltip.add(Text.literal("§8• Shift + Left-Click Minion: Prime as Escort (Right-Click leader to bind)"));
		tooltip.add(Text.literal("§8• DESIGN: Left-Click for Pos1 then Pos2, Right-Click to Capture Blueprint"));
		tooltip.add(Text.literal("§8• PATHWAY: Right-Click block to add waypoint, Sneak + Right-Click to remove"));
		tooltip.add(Text.literal("§8• PATHWAY: Right-Click minion to assign to route, Right-Click air to cycle route"));
		tooltip.add(Text.literal("§8• Right-Click Air (Build): Cycle Blueprint"));
		tooltip.add(Text.literal("§8• Right-Click Ground (Build): Anchor Construction"));
		tooltip.add(Text.literal("§8• Right-Click Mob (Recruit): Enthrall into Minion"));
	}
}
