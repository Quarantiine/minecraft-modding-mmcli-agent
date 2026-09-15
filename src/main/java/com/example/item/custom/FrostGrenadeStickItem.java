package com.example.item.custom;

import com.example.entity.custom.FrostGrenadeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Custom handheld stick item that launches a FrostGrenadeEntity on right-click.
 * Detonates with zero block damage, flash-freezing water to ice, converting lava to obsidian,
 * summoning a perimeter ring of powder snow, and inflicting freezing ticks and slowness.
 */
public class FrostGrenadeStickItem extends Item {

	public static final int USAGE_COOLDOWN_TICKS = 10; // 0.5s cooldown

	public FrostGrenadeStickItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = user.getStackInHand(hand);

		// Play auditory launch feedback (chilly snowball throw + powder snow swoosh)
		world.playSound(
				null,
				user.getX(),
				user.getY(),
				user.getZ(),
				SoundEvents.ENTITY_SNOWBALL_THROW,
				SoundCategory.PLAYERS,
				0.8F,
				1.4F);
		world.playSound(
				null,
				user.getX(),
				user.getY(),
				user.getZ(),
				SoundEvents.BLOCK_POWDER_SNOW_STEP,
				SoundCategory.PLAYERS,
				0.6F,
				1.6F);

		// Server authoritative projectile spawning
		if (!world.isClient()) {
			FrostGrenadeEntity projectile = new FrostGrenadeEntity(world, user);
			projectile.setItem(new ItemStack(this));
			projectile.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.5F, 1.0F);
			world.spawnEntity(projectile);
		}

		user.incrementStat(Stats.USED.getOrCreateStat(this));
		user.getItemCooldownManager().set(this, USAGE_COOLDOWN_TICKS);

		return TypedActionResult.success(itemStack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.modid-mmcli-agent-modding.frost_grenade_stick.desc").formatted(Formatting.GRAY));
		tooltip.add(Text.empty());
		tooltip.add(Text.literal("§b• Water §7➔ §bIce §8| §c• Lava §7➔ §8Obsidian"));
		tooltip.add(Text.literal("§f• Outer Ring: §fPowder Snow"));
		tooltip.add(Text.literal("§9• Inflicts: §bFreezing Ticks §7+ §9Slowness III"));
	}
}
