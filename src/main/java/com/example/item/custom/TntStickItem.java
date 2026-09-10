package com.example.item.custom;

import com.example.entity.custom.TntProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Custom handheld stick item that launches a TntProjectileEntity on
 * right-click.
 * Provides auditory feedback, adds a usage cooldown to prevent spam, and
 * registers stats.
 */
public class TntStickItem extends Item {

	public TntStickItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = user.getStackInHand(hand);

		// Play TNT priming sound for auditory feedback
		world.playSound(
				null,
				user.getX(),
				user.getY(),
				user.getZ(),
				SoundEvents.ENTITY_TNT_PRIMED,
				SoundCategory.PLAYERS,
				1.0F,
				1.0F);

		// Server authoritative projectile spawning
		if (!world.isClient()) {
			TntProjectileEntity projectile = new TntProjectileEntity(world, user);
			projectile.setItem(new ItemStack(Items.TNT));
			projectile.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.5F, 1.0F);
			world.spawnEntity(projectile);
		}

		user.incrementStat(Stats.USED.getOrCreateStat(this));

		// Set a 5-tick (0.25s) cooldown on this item to prevent runaway spam
		user.getItemCooldownManager().set(this, 5);

		return TypedActionResult.success(itemStack, world.isClient());
	}
}
