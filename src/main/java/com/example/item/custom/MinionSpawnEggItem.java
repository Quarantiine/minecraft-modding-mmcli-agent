package com.example.item.custom;

import com.example.entity.custom.MinionEntity;
import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.Spawner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * Custom spawn egg for summoning {@link MinionEntity}.
 * Automatically binds the spawned minion to the summoning player upon spawning,
 * establishing ownership, initializing in standby/sitting guard mode to prevent
 * instant aggressive charges, and producing level-up chime and heart particle effects.
 */
public class MinionSpawnEggItem extends SpawnEggItem {

	/**
	 * Constructs a new MinionSpawnEggItem.
	 *
	 * @param type The entity type to spawn (typically {@code ModEntities.MINION}).
	 * @param primaryColor The primary background egg color.
	 * @param secondaryColor The secondary dot egg color.
	 * @param settings The item configuration settings.
	 */
	public MinionSpawnEggItem(EntityType<? extends MobEntity> type, int primaryColor, int secondaryColor, Item.Settings settings) {
		super(type, primaryColor, secondaryColor, settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.SUCCESS;
		}

		ItemStack itemStack = context.getStack();
		BlockPos blockPos = context.getBlockPos();
		Direction direction = context.getSide();
		BlockState blockState = world.getBlockState(blockPos);

		// Handle vanilla mob spawner reconfiguration if clicking on a spawner
		BlockEntity blockEntity = world.getBlockEntity(blockPos);
		if (blockEntity instanceof Spawner spawner) {
			EntityType<?> entityType = this.getEntityType(itemStack);
			spawner.setEntityType(entityType, world.getRandom());
			blockEntity.markDirty();
			world.updateListeners(blockPos, blockState, blockState, 3);
			world.emitGameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, blockPos);
			itemStack.decrementUnlessCreative(1, context.getPlayer());
			return ActionResult.CONSUME;
		}

		// Calculate spawn position based on collision shape
		BlockPos spawnPos = blockState.getCollisionShape(world, blockPos).isEmpty() ? blockPos : blockPos.offset(direction);
		EntityType<?> entityType = this.getEntityType(itemStack);
		Entity entity = entityType.spawnFromItemStack(
			serverWorld,
			itemStack,
			context.getPlayer(),
			spawnPos,
			SpawnReason.SPAWN_EGG,
			true,
			!Objects.equals(blockPos, spawnPos) && direction == Direction.UP
		);

		if (entity != null) {
			bindSpawnedMinion(serverWorld, entity, context.getPlayer());
			itemStack.decrementUnlessCreative(1, context.getPlayer());
			world.emitGameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, blockPos);
		}

		return ActionResult.CONSUME;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = user.getStackInHand(hand);
		BlockHitResult hitResult = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
		if (hitResult.getType() != HitResult.Type.BLOCK) {
			return TypedActionResult.pass(itemStack);
		}
		if (!(world instanceof ServerWorld serverWorld)) {
			return TypedActionResult.success(itemStack);
		}

		BlockPos blockPos = hitResult.getBlockPos();
		if (!(world.getBlockState(blockPos).getBlock() instanceof FluidBlock)) {
			return TypedActionResult.pass(itemStack);
		}
		if (!world.canPlayerModifyAt(user, blockPos) || !user.canPlaceOn(blockPos, hitResult.getSide(), itemStack)) {
			return TypedActionResult.fail(itemStack);
		}

		EntityType<?> entityType = this.getEntityType(itemStack);
		Entity entity = entityType.spawnFromItemStack(
			serverWorld,
			itemStack,
			user,
			blockPos,
			SpawnReason.SPAWN_EGG,
			false,
			false
		);

		if (entity == null) {
			return TypedActionResult.pass(itemStack);
		}

		bindSpawnedMinion(serverWorld, entity, user);
		itemStack.decrementUnlessCreative(1, user);
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		world.emitGameEvent(user, GameEvent.ENTITY_PLACE, entity.getPos());
		return TypedActionResult.consume(itemStack);
	}

	/**
	 * Automatically tames and binds a newly spawned minion to the player owner,
	 * initializing it in standby/sitting guard mode with a guard anchor at its spawn position
	 * to prevent instant aggressive charges against nearby hostiles.
	 *
	 * @param world The server world where the entity exists.
	 * @param entity The newly spawned entity.
	 * @param player The player who summoned the minion.
	 */
	private void bindSpawnedMinion(ServerWorld world, Entity entity, PlayerEntity player) {
		if (entity instanceof MinionEntity minion && player != null) {
			minion.setOwner(player);
			minion.setSitting(true);
			minion.setGuardAnchorPos(minion.getBlockPos());
			minion.getNavigation().stop();
			minion.setTarget(null);

			// Auditory feedback: Level up fanfare
			world.playSound(
				null,
				minion.getX(),
				minion.getY(),
				minion.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP,
				SoundCategory.PLAYERS,
				0.8F,
				1.2F
			);

			// Visual feedback: Heart particles and entity tame status
			world.spawnParticles(
				ParticleTypes.HEART,
				minion.getX(),
				minion.getY() + 1.2D,
				minion.getZ(),
				10,
				0.35D,
				0.35D,
				0.35D,
				0.1D
			);
			world.sendEntityStatus(minion, (byte) 7);

			player.sendMessage(Text.literal("§6✦ You have bound a new Minion to your will! (Standby / Guard Mode)§r"), false);
		}
	}
}
