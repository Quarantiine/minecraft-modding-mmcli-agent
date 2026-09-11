package com.example.item.custom;

import com.example.blueprint.BlueprintRegistry;
import com.example.blueprint.StructureBlueprint;
import com.example.component.CommandMode;
import com.example.component.ModDataComponents;
import com.example.construction.ConstructionManager;
import com.example.entity.ModEntities;
import com.example.entity.custom.MinionEntity;
import java.util.List;
import java.util.Objects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.InventoryOwner;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Loki Command Scepter: A high-tier tactical relic allowing the player to orchestrate
 * minion thralls and multiblock construction.
 *
 * Capabilities:
 * - Sneak + Right-Click: Cycles operating {@link CommandMode} with pitch-shifted chime audio feedback.
 * - BUILD Mode:
 *     - Right-Click Ground: Anchors a new multiblock construction session at the clicked block face.
 *     - Right-Click Air / Sneak + Left-Click: Cycles active architectural blueprint.
 * - RECRUIT Mode:
 *     - Right-Click Living Mob: Transfigures target mob into an obedient {@link MinionEntity} thrall,
 *       preserving armor/equipment, binding owner UUID, and triggering arcane conversion VFX.
 * - FOLLOW / STAY Modes:
 *     - Right-Click Air or Ground: Broadcasts follow or hold-position commands to all owned minions within 32 blocks.
 * - ATTACK Mode:
 *     - Right-Click Mob: Focus-fires all owned minions onto the target.
 *     - Right-Click Air/Ground: Directs minions to engage nearby hostiles.
 */
public class CommandScepterItem extends Item {

	public static final double MINION_COMMAND_RADIUS = 32.0D;

	@FunctionalInterface
	public interface ScreenOpener {
		void openScreen(PlayerEntity player, Hand hand, ItemStack stack);
	}

	public static ScreenOpener SCREEN_OPENER = null;

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
	 * Resolves the active blueprint identifier stored in the item's data component.
	 *
	 * @param stack The scepter ItemStack.
	 * @return The active blueprint ID string, defaulting to {@link BlueprintRegistry#WATCHTOWER_ID}.
	 */
	public static String getBlueprintId(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.ACTIVE_BLUEPRINT, BlueprintRegistry.WATCHTOWER_ID);
	}

	/**
	 * Sets the active blueprint identifier on the item stack.
	 *
	 * @param stack       The scepter ItemStack.
	 * @param blueprintId The blueprint identifier string.
	 */
	public static void setBlueprintId(ItemStack stack, String blueprintId) {
		stack.set(ModDataComponents.ACTIVE_BLUEPRINT, Objects.requireNonNull(blueprintId, "blueprintId cannot be null"));
	}

	/**
	 * Cycles to the next architectural blueprint in catalog order, playing a bell chime sound
	 * and projecting an action-bar notification.
	 *
	 * @param stack  The scepter ItemStack.
	 * @param player The commanding player.
	 * @param world  The world instance.
	 * @return The newly selected StructureBlueprint.
	 */
	public static StructureBlueprint cycleBlueprint(ItemStack stack, PlayerEntity player, World world) {
		String currentId = getBlueprintId(stack);
		StructureBlueprint next = BlueprintRegistry.getNext(currentId);
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

	// -----------------------------------------------------------------------------------------
	// RIGHT-CLICK ON BLOCK (GROUND ANCHORING & COMMANDS)
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

		// Sneak + Right-Click: Open Command Hub GUI on client
		if (player.isSneaking()) {
			if (world.isClient() && SCREEN_OPENER != null) {
				SCREEN_OPENER.openScreen(player, context.getHand(), stack);
			}
			return ActionResult.success(world.isClient());
		}

		CommandMode mode = getMode(stack);

		// BUILD Mode: Anchor construction session at clicked block face
		if (mode == CommandMode.BUILD) {
			if (!world.isClient() && world instanceof ServerWorld serverWorld) {
				BlockPos anchorPos = world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
				String bpId = getBlueprintId(stack);
				StructureBlueprint blueprint = BlueprintRegistry.getOrDefault(bpId);
				ConstructionManager.getInstance().startSession(serverWorld, anchorPos, blueprint, player);
			}
			return ActionResult.success(world.isClient());
		}

		// Non-build modes: handle tactical broadcasts on right-click ground
		switch (mode) {
			case FOLLOW -> {
				broadcastFollow(player, world);
				return ActionResult.success(world.isClient());
			}
			case STAY -> {
				broadcastStay(player, world);
				return ActionResult.success(world.isClient());
			}
			case ATTACK -> {
				broadcastAttack(player, world);
				return ActionResult.success(world.isClient());
			}
			case MINE -> {
				broadcastMine(player, world);
				return ActionResult.success(world.isClient());
			}
			case RECRUIT -> {
				sendRecruitTip(player, world);
				return ActionResult.success(world.isClient());
			}
		}

		return ActionResult.PASS;
	}

	// -----------------------------------------------------------------------------------------
	// RIGHT-CLICK IN AIR (BLUEPRINT CYCLING & MINION BROADCASTS)
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

		switch (mode) {
			case BUILD -> {
				// Right-Click Air in BUILD mode: Cycles active blueprint
				cycleBlueprint(stack, player, world);
				return TypedActionResult.success(stack, world.isClient());
			}
			case FOLLOW -> {
				broadcastFollow(player, world);
				return TypedActionResult.success(stack, world.isClient());
			}
			case STAY -> {
				broadcastStay(player, world);
				return TypedActionResult.success(stack, world.isClient());
			}
			case ATTACK -> {
				broadcastAttack(player, world);
				return TypedActionResult.success(stack, world.isClient());
			}
			case MINE -> {
				broadcastMine(player, world);
				return TypedActionResult.success(stack, world.isClient());
			}
			case RECRUIT -> {
				sendRecruitTip(player, world);
				return TypedActionResult.success(stack, world.isClient());
			}
		}

		return TypedActionResult.pass(stack);
	}

	// -----------------------------------------------------------------------------------------
	// RIGHT-CLICK ON ENTITY (RECRUIT ENTHRALLMENT & TARGETING)
	// -----------------------------------------------------------------------------------------

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		World world = user.getWorld();
		CommandMode mode = getMode(stack);

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

		// ATTACK Mode: Focus fire owned minions onto this target entity
		if (mode == CommandMode.ATTACK && entity.isAlive() && !(entity instanceof MinionEntity) && !(entity instanceof PlayerEntity)) {
			if (!world.isClient()) {
				directMinionsToAttack(user, entity, world);
			}
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 1.0F, 1.5F);
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

		// 3. Preserve equipment across all 6 EquipmentSlots
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack equip = target.getEquippedStack(slot);
			if (!equip.isEmpty()) {
				minion.equipStack(slot, equip.copy());
			}
		}

		// 4. Transfer any inventory if target was an InventoryOwner
		if (target instanceof InventoryOwner invOwner) {
			Inventory targetInv = invOwner.getInventory();
			for (int i = 0; i < targetInv.size(); i++) {
				ItemStack item = targetInv.getStack(i);
				if (!item.isEmpty()) {
					minion.getInventory().addStack(item.copy());
				}
			}
		}

		// 5. Bind ownership and prime minion state
		minion.setOwner(owner);
		minion.setSitting(false);
		minion.getNavigation().stop();

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
	// TACTICAL BROADCAST HELPERS & DIRECTIVE EXECUTION
	// -----------------------------------------------------------------------------------------

	public static void executeDirective(PlayerEntity player, World world, CommandMode mode) {
		ItemStack stack = player.getMainHandStack().getItem() instanceof CommandScepterItem ? player.getMainHandStack()
			: (player.getOffHandStack().getItem() instanceof CommandScepterItem ? player.getOffHandStack() : ItemStack.EMPTY);
		switch (mode) {
			case FOLLOW -> broadcastFollow(player, world);
			case STAY -> broadcastStay(player, world);
			case ATTACK -> broadcastAttack(player, world);
			case MINE -> broadcastMine(player, world);
			case RECRUIT -> sendRecruitTip(player, world);
			case BUILD -> {
				if (!world.isClient()) {
					String bpId = stack.isEmpty() ? BlueprintRegistry.WATCHTOWER_ID : getBlueprintId(stack);
					String bpName = BlueprintRegistry.getOrDefault(bpId).getName();
					player.sendMessage(Text.literal("§b✦ Build Mode Active: Right-click ground to anchor " + bpName + "!§r"), true);
				}
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.2F);
			}
		}
	}

	public static void broadcastFollow(PlayerEntity player, World world) {
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player)
			);

			for (MinionEntity minion : minions) {
				minion.setSitting(false);
				minion.getNavigation().startMovingTo(player, 1.25D);
			}

			player.sendMessage(Text.literal("§a✦ Command: " + minions.size() + " Minion(s) Following!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.2F);
	}

	public static void broadcastStay(PlayerEntity player, World world) {
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player)
			);

			for (MinionEntity minion : minions) {
				minion.setSitting(true);
				minion.getNavigation().stop();
				minion.setTarget(null);
			}

			player.sendMessage(Text.literal("§e✦ Command: " + minions.size() + " Minion(s) Holding Position!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}

	public static void broadcastAttack(PlayerEntity player, World world) {
		if (!world.isClient()) {
			Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
			List<MinionEntity> minions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player)
			);

			// Find nearest hostile mob within 16 blocks
			Box hostileBox = player.getBoundingBox().expand(16.0D);
			List<MobEntity> hostiles = world.getEntitiesByClass(
				MobEntity.class,
				hostileBox,
				e -> e.isAlive() && !(e instanceof MinionEntity)
			);

			if (!hostiles.isEmpty()) {
				MobEntity nearest = hostiles.get(0);
				for (MinionEntity minion : minions) {
					minion.setSitting(false);
					minion.setTarget(nearest);
				}
				player.sendMessage(Text.literal("§c✦ " + minions.size() + " Minion(s) attacking " + nearest.getName().getString() + "!§r"), true);
			} else {
				player.sendMessage(Text.literal("§c✦ Attack Mode: Minions primed! Target an enemy or click near hostiles.§r"), true);
			}
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.5F, 1.8F);
	}

	public static void directMinionsToAttack(PlayerEntity player, LivingEntity target, World world) {
		Box searchBox = player.getBoundingBox().expand(MINION_COMMAND_RADIUS);
		List<MinionEntity> minions = world.getEntitiesByClass(
			MinionEntity.class,
			searchBox,
			m -> m.isAlive() && m.isOwner(player)
		);

		for (MinionEntity minion : minions) {
			minion.setSitting(false);
			minion.setTarget(target);
		}

		player.sendMessage(Text.literal("§c✦ Minions commanded to attack: " + target.getName().getString() + "!§r"), true);
	}

	public static void broadcastMine(PlayerEntity player, World world) {
		if (!world.isClient()) {
			player.sendMessage(Text.literal("§6✦ Mine Mode: Minions primed for resource harvesting!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.4F, 1.6F);
	}

	public static void sendRecruitTip(PlayerEntity player, World world) {
		if (!world.isClient()) {
			player.sendMessage(Text.literal("§d✦ Recruit Mode: Right-click any living mob to bind them as your minion!§r"), true);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.6F, 1.4F);
	}

	// -----------------------------------------------------------------------------------------
	// ITEM PRESENTATION & TOOLTIPS
	// -----------------------------------------------------------------------------------------

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		CommandMode mode = getMode(stack);
		tooltip.add(Text.literal("§7Command Mode: §r" + mode.getFormattedName()));

		if (mode == CommandMode.BUILD) {
			String bpId = getBlueprintId(stack);
			StructureBlueprint bp = BlueprintRegistry.getOrDefault(bpId);
			tooltip.add(Text.literal("§7Active Blueprint: §b" + bp.getName() + " §8(" + bp.getBlockCount() + " blocks)"));
		}

		tooltip.add(Text.empty());
		tooltip.add(Text.literal("§8• Shift + Right-Click / Press [V]: Open Command Hub GUI"));
		tooltip.add(Text.literal("§8• Shift + Left-Click (Build): Cycle Blueprint"));
		tooltip.add(Text.literal("§8• Right-Click Air (Build): Cycle Blueprint"));
		tooltip.add(Text.literal("§8• Right-Click Ground (Build): Anchor Construction"));
		tooltip.add(Text.literal("§8• Right-Click Mob (Recruit): Enthrall into Minion"));
		tooltip.add(Text.literal("§8• Right-Click (Follow/Stay): Broadcast to Minions"));
	}
}
