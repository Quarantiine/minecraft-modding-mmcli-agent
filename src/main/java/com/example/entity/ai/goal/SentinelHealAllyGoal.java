package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Combat-medic AI goal for {@link MinionEntity} units assigned the {@link MinionRole#SENTINEL} role.
 * <p>
 * Sentinels channel the "Aegis of Restoration" to heal wounded ally minions and players:
 * 1. Scans a 10-block radius for allied players (commander priority) and minions under 70% max health.
 * 2. Channels a 1-second (20 ticks) restorative beam with emerald and enchanted sparkle particles.
 * 3. Restores 6.0 HP (3 hearts), grants Regeneration II for 5 seconds (100 ticks), spawns heart particles,
 *    and triggers an arcane resonance chime.
 * 4. Enforces a 6-second (120 ticks) cooldown per Sentinel.
 * 5. Provides fallback self-healing if the Sentinel itself drops below 40% HP and no allies need aid.
 */
public class SentinelHealAllyGoal extends Goal {

	public static final double HEAL_RANGE = 10.0D;
	public static final double HEAL_RANGE_SQ = HEAL_RANGE * HEAL_RANGE; // 100.0D
	public static final float ALLY_HEALTH_THRESHOLD = 0.70F; // Below 70% max health
	public static final float SELF_HEALTH_THRESHOLD = 0.40F; // Below 40% max health
	public static final float HEAL_AMOUNT = 6.0F; // 3 hearts
	public static final int CHANNEL_TICKS_REQUIRED = 20; // 1 second
	public static final int HEAL_COOLDOWN_TICKS = 120; // 6 seconds
	public static final int REGEN_DURATION_TICKS = 100; // 5 seconds
	public static final int REGEN_AMPLIFIER = 1; // Regeneration II

	private final MinionEntity minion;
	private LivingEntity targetAlly;
	private int channelTicks;
	private long lastHealTime;

	public SentinelHealAllyGoal(MinionEntity minion) {
		this.minion = minion;
		this.channelTicks = 0;
		this.lastHealTime = -HEAL_COOLDOWN_TICKS;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (!this.minion.matchesRole(MinionRole.SENTINEL)) {
			return false;
		}

		long currentTime = this.minion.getWorld().getTime();
		if (currentTime < this.lastHealTime + HEAL_COOLDOWN_TICKS) {
			return false;
		}

		// 1. Search for lowest-health allied player or minion within 10 blocks
		LivingEntity bestAlly = findMostWoundedAlly();
		if (bestAlly != null) {
			this.targetAlly = bestAlly;
			return true;
		}

		// 2. Fallback: Self-healing if Sentinel itself is critically wounded and no allies need aid
		if (this.minion.getHealth() < this.minion.getMaxHealth() * SELF_HEALTH_THRESHOLD) {
			this.targetAlly = this.minion;
			return true;
		}

		return false;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (!this.minion.matchesRole(MinionRole.SENTINEL)) {
			return false;
		}
		if (this.targetAlly == null || !this.targetAlly.isAlive()) {
			return false;
		}
		if (this.channelTicks >= CHANNEL_TICKS_REQUIRED) {
			return false;
		}
		return this.minion.squaredDistanceTo(this.targetAlly) <= (HEAL_RANGE_SQ + 9.0D);
	}

	@Override
	public void start() {
		this.channelTicks = 0;
		this.minion.getNavigation().stop();

		this.minion.getWorld().playSound(
				null,
				this.minion.getX(),
				this.minion.getY(),
				this.minion.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
				SoundCategory.NEUTRAL,
				0.8F,
				1.4F
		);
	}

	@Override
	public void tick() {
		if (this.targetAlly == null) {
			return;
		}

		this.channelTicks++;

		// Face the recipient during channeling
		this.minion.getLookControl().lookAt(
				this.targetAlly.getX(),
				this.targetAlly.getY() + this.targetAlly.getStandingEyeHeight(),
				this.targetAlly.getZ(),
				30.0F,
				30.0F
		);

		// Emit beam particles toward target ally on server
		if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
			if (this.channelTicks % 2 == 0) {
				Vec3d start = this.minion.getEyePos().add(0, -0.2D, 0);
				Vec3d end = this.targetAlly.getEyePos().add(0, -0.2D, 0);
				Vec3d step = end.subtract(start);
				int segments = Math.max(3, (int) (step.length() * 2));

				for (int i = 1; i <= segments; i++) {
					double progress = (double) i / segments;
					Vec3d point = start.add(step.multiply(progress));
					serverWorld.spawnParticles(
							ParticleTypes.HAPPY_VILLAGER,
							point.x,
							point.y,
							point.z,
							1,
							0.05D,
							0.05D,
							0.05D,
							0.01D
					);
				}
			}

			// Channel completion: deliver heal and buffs
			if (this.channelTicks >= CHANNEL_TICKS_REQUIRED) {
				// Heal direct HP
				this.targetAlly.heal(HEAL_AMOUNT);

				// Apply sustained Regeneration II
				this.targetAlly.addStatusEffect(new StatusEffectInstance(
						StatusEffects.REGENERATION,
						REGEN_DURATION_TICKS,
						REGEN_AMPLIFIER
				));

				// Heart and emerald burst on the recipient
				serverWorld.spawnParticles(
						ParticleTypes.HEART,
						this.targetAlly.getX(),
						this.targetAlly.getY() + this.targetAlly.getHeight() + 0.3D,
						this.targetAlly.getZ(),
						7,
						0.3D,
						0.3D,
						0.3D,
						0.05D
				);
				serverWorld.spawnParticles(
						ParticleTypes.HAPPY_VILLAGER,
						this.targetAlly.getX(),
						this.targetAlly.getY() + 1.0D,
						this.targetAlly.getZ(),
						10,
						0.4D,
						0.4D,
						0.4D,
						0.08D
				);

				// Arcane levelup and resonance audio
				this.minion.getWorld().playSound(
						null,
						this.targetAlly.getX(),
						this.targetAlly.getY(),
						this.targetAlly.getZ(),
						SoundEvents.ENTITY_PLAYER_LEVELUP,
						SoundCategory.NEUTRAL,
						0.8F,
						1.8F
				);
				this.minion.getWorld().playSound(
						null,
						this.targetAlly.getX(),
						this.targetAlly.getY(),
						this.targetAlly.getZ(),
						SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
						SoundCategory.NEUTRAL,
						1.0F,
						1.4F
				);

				// Action bar notification if healing a player
				if (this.targetAlly instanceof ServerPlayerEntity serverPlayer) {
					serverPlayer.sendMessage(
							Text.literal("§a🛡 Sentinel channeled Aegis of Restoration to heal you!§r"),
							true
					);
				}

				this.lastHealTime = this.minion.getWorld().getTime();
			}
		}
	}

	@Override
	public void stop() {
		this.targetAlly = null;
		this.channelTicks = 0;
	}

	/**
	 * Finds the allied player or minion with the lowest health ratio under 70% within healing range.
	 * Prioritizes the player commander if wounded.
	 */
	private LivingEntity findMostWoundedAlly() {
		LivingEntity bestRecipient = null;
		float lowestRatio = ALLY_HEALTH_THRESHOLD;

		// 1. Check the owner player first (commander priority!)
		LivingEntity owner = this.minion.getOwner();
		if (owner instanceof PlayerEntity player && player.isAlive() && !player.isSpectator()) {
			double distSq = this.minion.squaredDistanceTo(player);
			if (distSq <= HEAL_RANGE_SQ) {
				float ratio = player.getHealth() / player.getMaxHealth();
				if (ratio < ALLY_HEALTH_THRESHOLD) {
					lowestRatio = ratio;
					bestRecipient = player;
				}
			}
		}

		// 2. Search nearby allied minions within 10 blocks
		Box searchBox = this.minion.getBoundingBox().expand(HEAL_RANGE);
		List<MinionEntity> nearbyMinions = this.minion.getWorld().getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m != null
						&& m.isAlive()
						&& m != this.minion
						&& m.isOwner(this.minion.getOwner())
						&& this.minion.squaredDistanceTo(m) <= HEAL_RANGE_SQ
						&& m.getHealth() < m.getMaxHealth() * ALLY_HEALTH_THRESHOLD
		);

		for (MinionEntity ally : nearbyMinions) {
			float ratio = ally.getHealth() / ally.getMaxHealth();
			if (ratio < lowestRatio) {
				lowestRatio = ratio;
				bestRecipient = ally;
			}
		}

		// 3. Also check any other allied players in the vicinity (multiplayer allies)
		if (bestRecipient == null || !(bestRecipient instanceof PlayerEntity)) {
			List<PlayerEntity> nearbyPlayers = this.minion.getWorld().getEntitiesByClass(
					PlayerEntity.class,
					searchBox,
					p -> p != null
							&& p.isAlive()
							&& !p.isSpectator()
							&& p != owner
							&& this.minion.squaredDistanceTo(p) <= HEAL_RANGE_SQ
							&& p.getHealth() < p.getMaxHealth() * ALLY_HEALTH_THRESHOLD
			);
			for (PlayerEntity player : nearbyPlayers) {
				float ratio = player.getHealth() / player.getMaxHealth();
				if (ratio < lowestRatio) {
					lowestRatio = ratio;
					bestRecipient = player;
				}
			}
		}

		return bestRecipient;
	}

	public LivingEntity getTargetAlly() {
		return this.targetAlly;
	}

	public int getChannelTicks() {
		return this.channelTicks;
	}

	public long getLastHealTime() {
		return this.lastHealTime;
	}

	public void setLastHealTime(long lastHealTime) {
		this.lastHealTime = lastHealTime;
	}
}
