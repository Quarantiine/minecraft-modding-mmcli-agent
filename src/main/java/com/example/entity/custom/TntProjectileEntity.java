package com.example.entity.custom;

import com.example.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Custom projectile entity that launches like a thrown item (rendering as TNT),
 * leaves an active smoke and flame particle trail in flight, and triggers a TNT-strength
 * explosion upon impact with any block or entity.
 */
public class TntProjectileEntity extends ThrownItemEntity {

	public TntProjectileEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
		super(entityType, world);
	}

	public TntProjectileEntity(EntityType<? extends ThrownItemEntity> entityType, LivingEntity owner, World world) {
		super(entityType, owner, world);
	}

	public TntProjectileEntity(World world, LivingEntity owner) {
		super(ModEntities.TNT_PROJECTILE, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.TNT;
	}

	@Override
	public void tick() {
		super.tick();

		// Spawn visual particle trails while in flight on the client side
		if (this.getWorld().isClient()) {
			this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			if (this.random.nextFloat() < 0.6f) {
				this.getWorld().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);

		// Handle explosion authoritatively on the server side to avoid desync
		if (!this.getWorld().isClient()) {
			this.getWorld().createExplosion(
				this,
				this.getX(),
				this.getY(),
				this.getZ(),
				4.0F,
				World.ExplosionSourceType.TNT
			);
			this.discard();
		}
	}
}
