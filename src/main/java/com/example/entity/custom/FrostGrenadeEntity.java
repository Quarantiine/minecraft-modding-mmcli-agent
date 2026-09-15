package com.example.entity.custom;

import com.example.entity.ModEntities;
import com.example.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Cryogenic projectile entity launched by the Frost Grenade Projectile Stick.
 * Leaves an active snowflake and frost particle trail in flight, and triggers
 * a flash-freeze detonation upon impact:
 * 1. Zero block explosion destruction
 * 2. Flash-freezes water into ice
 * 3. Converts lava into obsidian (and cobblestone for flowing lava)
 * 4. Extinguishes surface fires and lit campfires
 * 5. Deploys a perimeter ring of powder snow
 * 6. Inflicts freezing ticks, Slowness III, and extinguishes burning entities
 */
public class FrostGrenadeEntity extends ThrownItemEntity {

	public static final float FREEZE_RADIUS = 3.5F;
	public static final float RING_MIN_RADIUS = 2.0F;
	public static final float RING_MAX_RADIUS = 3.5F;
	public static final float ENTITY_EFFECT_RADIUS = 5.0F;
	public static final int FREEZE_TICKS = 360;
	public static final int SLOWNESS_DURATION_TICKS = 160;
	public static final int SLOWNESS_AMPLIFIER = 2; // Slowness III

	public FrostGrenadeEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
		super(entityType, world);
	}

	public FrostGrenadeEntity(EntityType<? extends ThrownItemEntity> entityType, LivingEntity owner, World world) {
		super(entityType, owner, world);
	}

	public FrostGrenadeEntity(World world, LivingEntity owner) {
		super(ModEntities.FROST_PROJECTILE, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.FROST_GRENADE_STICK;
	}

	@Override
	public void tick() {
		super.tick();

		// In-flight cryogenic visual particle trails on client side
		if (this.getWorld().isClient()) {
			this.getWorld().addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			if (this.random.nextFloat() < 0.35F) {
				this.getWorld().addParticle(ParticleTypes.ITEM_SNOWBALL, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);

		Entity target = entityHitResult.getEntity();
		if (!this.getWorld().isClient() && target instanceof LivingEntity livingTarget) {
			float directDamage = 3.0F;
			// Vulnerability for fire elemental mobs (Blazes, Magma Cubes)
			if (livingTarget instanceof BlazeEntity || livingTarget instanceof MagmaCubeEntity) {
				directDamage += 5.0F;
			}
			livingTarget.damage(this.getDamageSources().thrown(this, this.getOwner()), directDamage);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);

		if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverWorld) {
			Vec3d hitPos = hitResult.getPos();
			BlockPos centerPos = BlockPos.ofFloored(hitPos);

			applyFluidAndFireTransmutation(serverWorld, centerPos);
			deployPowderSnowRing(serverWorld, centerPos);
			applyEntityDebuffs(serverWorld, hitPos);
			spawnDetonationVfxAndAudio(serverWorld, hitPos);

			this.discard();
		}
	}

	/**
	 * Flash-freezes water into ice, turns lava into obsidian, and extinguishes fires/campfires.
	 * Causes zero block damage.
	 */
	public static void applyFluidAndFireTransmutation(ServerWorld world, BlockPos centerPos) {
		int r = (int) Math.ceil(FREEZE_RADIUS);
		double maxDistSq = FREEZE_RADIUS * FREEZE_RADIUS;

		for (BlockPos pos : BlockPos.iterate(centerPos.add(-r, -r, -r), centerPos.add(r, r, r))) {
			if (pos.getSquaredDistance(centerPos) <= maxDistSq) {
				BlockState state = world.getBlockState(pos);

				// 1. Water -> Ice
				if (state.getFluidState().isIn(FluidTags.WATER) || state.isOf(Blocks.WATER)) {
					if (state.isOf(Blocks.WATER)) {
						world.setBlockState(pos, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
					} else if (state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS)
							|| state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)
							|| state.isOf(Blocks.BUBBLE_COLUMN)) {
						world.setBlockState(pos, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
					}
				}

				// 2. Lava -> Obsidian / Cobblestone
				if (state.getFluidState().isIn(FluidTags.LAVA) || state.isOf(Blocks.LAVA)) {
					if (state.getFluidState().isStill()) {
						world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
					} else {
						world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
					}
					world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.4F);
				}

				// 3. Fire and Campfires -> Extinguished
				if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
					world.removeBlock(pos, false);
					world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.8F);
				} else if (state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)) {
					if (state.contains(CampfireBlock.LIT) && state.get(CampfireBlock.LIT)) {
						world.setBlockState(pos, state.with(CampfireBlock.LIT, false), Block.NOTIFY_ALL);
						world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.8F);
					}
				}
			}
		}
	}

	/**
	 * Deploys a perimeter ring of powder snow at ground level around the impact zone.
	 * Only replaces air or non-solid replaceable blocks with a solid base underneath.
	 */
	public static void deployPowderSnowRing(ServerWorld world, BlockPos centerPos) {
		int maxR = (int) Math.ceil(RING_MAX_RADIUS);
		double minRadiusSq = RING_MIN_RADIUS * RING_MIN_RADIUS;
		double maxRadiusSq = RING_MAX_RADIUS * RING_MAX_RADIUS;

		for (int dx = -maxR; dx <= maxR; dx++) {
			for (int dz = -maxR; dz <= maxR; dz++) {
				double distSq = dx * dx + dz * dz;
				if (distSq >= minRadiusSq && distSq <= maxRadiusSq) {
					int x = centerPos.getX() + dx;
					int z = centerPos.getZ() + dz;

					// Scan vertical window around impact center to locate ground level
					for (int dy = 2; dy >= -2; dy--) {
						BlockPos candidatePos = new BlockPos(x, centerPos.getY() + dy, z);
						BlockState state = world.getBlockState(candidatePos);

						if (state.isAir() || (state.isReplaceable() && !state.isLiquid())) {
							BlockPos groundBelow = candidatePos.down();
							BlockState stateBelow = world.getBlockState(groundBelow);

							// Place powder snow only on solid ground or ice surfaces
							if (stateBelow.isSolidBlock(world, groundBelow)
									|| stateBelow.isSideSolidFullSquare(world, groundBelow, Direction.UP)
									|| stateBelow.isOf(Blocks.ICE) || stateBelow.isOf(Blocks.PACKED_ICE)) {
								world.setBlockState(candidatePos, Blocks.POWDER_SNOW.getDefaultState(), Block.NOTIFY_ALL);
								break; // Placed at top surface for this (x,z) column
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Inflicts freezing ticks and Slowness III on caught living entities, while extinguishing fires on them.
	 */
	public static void applyEntityDebuffs(ServerWorld world, Vec3d hitPos) {
		Box searchBox = new Box(
				hitPos.x - ENTITY_EFFECT_RADIUS, hitPos.y - ENTITY_EFFECT_RADIUS, hitPos.z - ENTITY_EFFECT_RADIUS,
				hitPos.x + ENTITY_EFFECT_RADIUS, hitPos.y + ENTITY_EFFECT_RADIUS, hitPos.z + ENTITY_EFFECT_RADIUS
		);
		double radiusSq = ENTITY_EFFECT_RADIUS * ENTITY_EFFECT_RADIUS;

		List<LivingEntity> targets = world.getEntitiesByClass(
				LivingEntity.class,
				searchBox,
				e -> e.isAlive() && e.squaredDistanceTo(hitPos) <= radiusSq
		);

		for (LivingEntity target : targets) {
			// Extinguish any burning entities
			if (target.isOnFire()) {
				target.extinguishWithSound();
			}

			// Freezing ticks (instantly triggers freeze vignette and cold damage if eligible)
			if (target.canFreeze()) {
				target.setFrozenTicks(Math.max(target.getFrozenTicks() + 300, FREEZE_TICKS));
			}

			// Slowness III debuff
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SLOWNESS_DURATION_TICKS, SLOWNESS_AMPLIFIER));

			world.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		}
	}

	/**
	 * Spawns snowflake, snowball, and cloud impact particles along with crystalline shatter audio.
	 */
	public static void spawnDetonationVfxAndAudio(ServerWorld world, Vec3d hitPos) {
		world.spawnParticles(ParticleTypes.SNOWFLAKE, hitPos.x, hitPos.y + 0.5, hitPos.z, 60, 1.2, 0.6, 1.2, 0.08);
		world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, hitPos.x, hitPos.y + 0.5, hitPos.z, 30, 0.8, 0.4, 0.8, 0.05);
		world.spawnParticles(ParticleTypes.CLOUD, hitPos.x, hitPos.y + 0.2, hitPos.z, 20, 0.8, 0.2, 0.8, 0.03);
		world.spawnParticles(ParticleTypes.FLASH, hitPos.x, hitPos.y + 0.5, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);

		world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2F, 1.2F);
		world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BLOCK_POWDER_SNOW_STEP, SoundCategory.PLAYERS, 1.5F, 0.8F);
		world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 1.2F, 1.0F);
	}
}
