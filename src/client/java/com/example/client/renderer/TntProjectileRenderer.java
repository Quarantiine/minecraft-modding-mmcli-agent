package com.example.client.renderer;

import com.example.entity.custom.TntProjectileEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Client entity renderer for {@link TntProjectileEntity}.
 * Extends {@link FlyingItemEntityRenderer} to render the thrown projectile as a spinning item.
 */
public class TntProjectileRenderer extends FlyingItemEntityRenderer<TntProjectileEntity> {

	/**
	 * Constructs a new renderer instance for TNT projectiles using standard scale and lighting.
	 *
	 * @param context The entity renderer factory context providing access to item renderers and models.
	 */
	public TntProjectileRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	/**
	 * Constructs a new renderer instance for TNT projectiles with custom scale and lighting parameters.
	 *
	 * @param context The entity renderer factory context.
	 * @param scale   The scale multiplier for the rendered item.
	 * @param lit     Whether the item should be rendered with full brightness.
	 */
	public TntProjectileRenderer(EntityRendererFactory.Context context, float scale, boolean lit) {
		super(context, scale, lit);
	}
}
