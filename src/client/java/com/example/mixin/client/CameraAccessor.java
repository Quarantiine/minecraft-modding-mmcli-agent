package com.example.mixin.client;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin invoker exposing protected camera repositioning methods for tactical overhead view.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

	@Invoker("setPos")
	void callSetPos(double x, double y, double z);

	@Invoker("setRotation")
	void callSetRotation(float yaw, float pitch);
}
