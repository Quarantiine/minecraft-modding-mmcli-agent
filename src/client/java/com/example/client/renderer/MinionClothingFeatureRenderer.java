package com.example.client.renderer;

import com.example.entity.custom.MinionEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Feature renderer for rendering secondary clothing and outer layers on player-model minion entities.
 * Decoupled from legacy villager robes and adapted for {@link PlayerEntityModel}.
 * Ensures outer biped clothing layers (jacket, sleeves, pants, hat) are actively enabled and rendered.
 */
public class MinionClothingFeatureRenderer extends FeatureRenderer<MinionEntity, PlayerEntityModel<MinionEntity>> {

	public MinionClothingFeatureRenderer(FeatureRendererContext<MinionEntity, PlayerEntityModel<MinionEntity>> context) {
		super(context);
	}

	@Override
	public void render(
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		int light,
		MinionEntity entity,
		float limbAngle,
		float limbDistance,
		float tickDelta,
		float animationProgress,
		float headYaw,
		float headPitch
	) {
		if (entity.isInvisible()) {
			return;
		}

		PlayerEntityModel<MinionEntity> model = this.getContextModel();
		model.hat.visible = true;
		model.jacket.visible = true;
		model.leftSleeve.visible = true;
		model.rightSleeve.visible = true;
		model.leftPants.visible = true;
		model.rightPants.visible = true;
	}
}

