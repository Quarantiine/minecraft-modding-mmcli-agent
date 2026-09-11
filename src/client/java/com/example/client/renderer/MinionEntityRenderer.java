package com.example.client.renderer;

import com.example.entity.custom.MinionEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side renderer for {@link MinionEntity}.
 * Extends {@link BipedEntityRenderer} using a player model ({@link PlayerEntityModel}),
 * enabling full equipment visualization:
 * - Helmet, Chestplate, Leggings, and Boots via {@link ArmorFeatureRenderer}
 * - Mainhand weapons/tools/blocks and Offhand items/shields via {@link net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer}
 * - Outer clothing layers via {@link MinionClothingFeatureRenderer}
 * - Dynamic arm poses for melee attacks, archery, blocking, and item usage
 * - Sneaking and sitting/staying pose adjustments
 */
public class MinionEntityRenderer extends BipedEntityRenderer<MinionEntity, PlayerEntityModel<MinionEntity>> {

	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	public MinionEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);

		// Armor feature layers (inner = leggings, outer = helmet/chestplate/boots)
		this.addFeature(new ArmorFeatureRenderer<>(
			this,
			new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
			new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
			context.getModelManager()
		));

		// Outer clothing feature renderer adapted for player biped models
		this.addFeature(new MinionClothingFeatureRenderer(this));

		// Overhead billboard crest badge feature renderer (squad banner + role crest)
		this.addFeature(new MinionOverheadBadgeFeatureRenderer(this, context.getTextRenderer(), context.getRenderDispatcher()));
	}

	@Override
	public Identifier getTexture(MinionEntity entity) {
		return TEXTURE;
	}

	@Override
	public Vec3d getPositionOffset(MinionEntity entity, float tickDelta) {
		if (entity.isSitting() && !entity.hasVehicle()) {
			return new Vec3d(0.0D, -0.3125D, 0.0D);
		}
		if (entity.isInSneakingPose()) {
			return new Vec3d(0.0D, -2.0F * entity.getScale() / 16.0F, 0.0D);
		}
		return super.getPositionOffset(entity, tickDelta);
	}

	@Override
	protected void scale(MinionEntity entity, MatrixStack matrices, float amount) {
		float scale = 0.9375F;
		matrices.scale(scale, scale, scale);
		super.scale(entity, matrices, amount);
	}

	@Override
	public void render(
		MinionEntity entity,
		float entityYaw,
		float tickDelta,
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		int light
	) {
		this.setModelPose(entity);
		super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
	}

	/**
	 * Configures arm poses, sitting posture, and crouching states for the minion's biped player model.
	 *
	 * @param entity The minion entity being rendered.
	 */
	private void setModelPose(MinionEntity entity) {
		PlayerEntityModel<MinionEntity> model = this.getModel();
		if (entity.isSpectator()) {
			model.setVisible(false);
			model.head.visible = true;
			model.hat.visible = true;
		} else {
			model.setVisible(true);
		}

		model.sneaking = entity.isInSneakingPose();
		model.riding = entity.isSitting();

		BipedEntityModel.ArmPose mainArmPose = getArmPose(entity, Hand.MAIN_HAND);
		BipedEntityModel.ArmPose offArmPose = getArmPose(entity, Hand.OFF_HAND);
		if (mainArmPose.isTwoHanded()) {
			offArmPose = entity.getOffHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		}

		if (entity.getMainArm() == Arm.RIGHT) {
			model.rightArmPose = mainArmPose;
			model.leftArmPose = offArmPose;
		} else {
			model.rightArmPose = offArmPose;
			model.leftArmPose = mainArmPose;
		}
	}

	/**
	 * Resolves the appropriate {@link BipedEntityModel.ArmPose} for a given hand stack and activity.
	 *
	 * @param entity The minion entity.
	 * @param hand   The hand to check.
	 * @return The arm pose corresponding to the item held or in use.
	 */
	private static BipedEntityModel.ArmPose getArmPose(MinionEntity entity, Hand hand) {
		ItemStack stack = entity.getStackInHand(hand);
		if (stack.isEmpty()) {
			return BipedEntityModel.ArmPose.EMPTY;
		}

		if (entity.getActiveHand() == hand && entity.getItemUseTimeLeft() > 0) {
			UseAction useAction = stack.getUseAction();
			if (useAction == UseAction.BLOCK) {
				return BipedEntityModel.ArmPose.BLOCK;
			}
			if (useAction == UseAction.BOW) {
				return BipedEntityModel.ArmPose.BOW_AND_ARROW;
			}
			if (useAction == UseAction.SPEAR) {
				return BipedEntityModel.ArmPose.THROW_SPEAR;
			}
			if (useAction == UseAction.CROSSBOW) {
				return BipedEntityModel.ArmPose.CROSSBOW_CHARGE;
			}
			if (useAction == UseAction.SPYGLASS) {
				return BipedEntityModel.ArmPose.SPYGLASS;
			}
			if (useAction == UseAction.TOOT_HORN) {
				return BipedEntityModel.ArmPose.TOOT_HORN;
			}
			if (useAction == UseAction.BRUSH) {
				return BipedEntityModel.ArmPose.BRUSH;
			}
		} else if (!entity.handSwinging && stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
			return BipedEntityModel.ArmPose.CROSSBOW_HOLD;
		}

		return BipedEntityModel.ArmPose.ITEM;
	}
}

