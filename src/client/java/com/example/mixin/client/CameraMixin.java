package com.example.mixin.client;

import com.example.client.camera.TacticalBuildCameraController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into camera positioning to apply tactical overhead adjustments in BUILD mode.
 */
@Mixin(Camera.class)
public class CameraMixin {

	@Inject(method = "update", at = @At("TAIL"))
	private void onCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		TacticalBuildCameraController.updateCamera((Camera) (Object) this, focusedEntity, tickDelta);
	}
}
