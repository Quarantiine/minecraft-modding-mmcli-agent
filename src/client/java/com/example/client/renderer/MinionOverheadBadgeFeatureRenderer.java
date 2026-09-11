package com.example.client.renderer;

import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Client-side feature renderer for rendering overhead billboard crest badges above {@link MinionEntity} heads.
 * <p>
 * Provides high-visibility battlefield visual hierarchy inspired by RTS and Dynasty Warriors:
 * <ul>
 *   <li><b>Squad Banner</b> (Top): Displays squad channel flag, name, and Roman numeral (e.g. {@code ⚑ SQUAD ALPHA [I]})
 *       tinted with the squad's distinctive banner color.</li>
 *   <li><b>Role Crest & Lettering</b> (Bottom): Displays tactical archetype icon and uppercase role lettering
 *       (e.g. {@code ⚔ WARRIOR}, {@code 🛡 SENTINEL}, {@code 🔨 BUILDER}, {@code ⛏ MINER}, {@code 🏹 RANGER}),
 *       with an optional status indicator ({@code [HOLD]}) when holding position.</li>
 * </ul>
 * <p>
 * Billboards directly toward the active player camera using {@link EntityRenderDispatcher#getRotation()},
 * neutralizing model body yaw and model scale to guarantee uniform label dimensions and orientation across all viewing angles.
 * <p>
 * Implements the vanilla two-pass nametag rendering pipeline:
 * <ul>
 *   <li><b>Pass 1 (See-Through)</b>: Translucent background plate and translucent text rendered with {@link net.minecraft.client.font.TextRenderer.TextLayerType#SEE_THROUGH}
 *       allowing visibility through obstructing walls and geometry.</li>
 *   <li><b>Pass 2 (Normal)</b>: High-contrast non-see-through pass rendered with {@link net.minecraft.client.font.TextRenderer.TextLayerType#NORMAL}
 *       when the minion is not in a sneaking pose, providing crisp depth-tested text over unobstructed sightlines.</li>
 * </ul>
 */
public class MinionOverheadBadgeFeatureRenderer extends FeatureRenderer<MinionEntity, PlayerEntityModel<MinionEntity>> {

	public static final double MAX_RENDER_DISTANCE_SQ = 4096.0D; // 64 blocks max render distance
	public static final float TEXT_SCALE = 0.025F;
	public static final float MODEL_SCALE = 0.9375F;
	public static final float LINE_SPACING = 11.0F;

	private final TextRenderer textRenderer;
	private final EntityRenderDispatcher dispatcher;

	/**
	 * Full constructor supplying explicit text renderer and render dispatcher instances.
	 *
	 * @param context      The parent feature renderer context.
	 * @param textRenderer Font text renderer for rasterizing badge text.
	 * @param dispatcher   Entity render dispatcher for camera rotation and distance queries.
	 */
	public MinionOverheadBadgeFeatureRenderer(
		FeatureRendererContext<MinionEntity, PlayerEntityModel<MinionEntity>> context,
		TextRenderer textRenderer,
		EntityRenderDispatcher dispatcher
	) {
		super(context);
		this.textRenderer = textRenderer != null ? textRenderer : MinecraftClient.getInstance().textRenderer;
		this.dispatcher = dispatcher != null ? dispatcher : MinecraftClient.getInstance().getEntityRenderDispatcher();
	}

	/**
	 * Simplified constructor resolving text renderer and dispatcher from {@link MinecraftClient}.
	 *
	 * @param context The parent feature renderer context.
	 */
	public MinionOverheadBadgeFeatureRenderer(FeatureRendererContext<MinionEntity, PlayerEntityModel<MinionEntity>> context) {
		this(context, MinecraftClient.getInstance().textRenderer, MinecraftClient.getInstance().getEntityRenderDispatcher());
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
		if (entity.isInvisible() || entity.isSpectator()) {
			return;
		}

		// Distance culling: Skip rendering if beyond maximum battlefield viewing range
		if (this.dispatcher.getSquaredDistanceToCamera(entity) > MAX_RENDER_DISTANCE_SQ) {
			return;
		}

		SquadGroup squad = entity.getSquad();
		MinionRole role = entity.getRole();
		if (squad == null) squad = SquadGroup.ALPHA;
		if (role == null) role = MinionRole.WARRIOR;

		Text squadBannerText = getSquadBanner(squad, entity.isSelected());
		Text roleCrestText = getRoleCrest(role, entity.isHoldingPosition());

		matrices.push();

		// LIFO Matrix Reversal of LivingEntityRenderer transformations:
		// LivingEntityRenderer applied:
		// 1. setupTransforms (body yaw rotation: 180.0F - bodyYaw)
		// 2. scale(-1.0F, -1.0F, 1.0F)
		// 3. scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE)
		// 4. translate(0.0F, -1.501F, 0.0F)
		//
		// Invert in strict reverse order to return to upright world-space at the entity's base:
		// Step 1: Invert translate(0.0F, -1.501F, 0.0F)
		matrices.translate(0.0F, 1.501F, 0.0F);

		// Step 2: Invert scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE)
		matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);

		// Step 3: Invert scale(-1.0F, -1.0F, 1.0F)
		matrices.scale(-1.0F, -1.0F, 1.0F);

		// Step 4: Invert body yaw rotation
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - 180.0F));

		// Step 5: Translate to overhead position above head in upright world space
		float yTranslation = getOverheadYTranslation(entity.getHeight(), entity.hasCustomName(), entity.isInSneakingPose());
		matrices.translate(0.0F, yTranslation, 0.0F);

		// Step 6: Billboard directly toward camera
		matrices.multiply(this.dispatcher.getRotation());

		// Step 7: Scale to font coordinate space (-Y so font glyphs render upright)
		matrices.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

		Matrix4f matrix4f = matrices.peek().getPositionMatrix();

		// Compute standard nametag translucent background plate
		MinecraftClient client = MinecraftClient.getInstance();
		int backgroundOpacity = (client != null && client.options != null)
			? (int)(client.options.getTextBackgroundOpacity(0.25F) * 255.0F)
			: 64;
		int backgroundColor = (backgroundOpacity > 0 ? backgroundOpacity : 64) << 24;

		// Calculate centered horizontal and vertical text positions
		float width1 = this.textRenderer.getWidth(squadBannerText);
		float x1 = -width1 / 2.0F;
		float y1 = -LINE_SPACING;

		float width2 = this.textRenderer.getWidth(roleCrestText);
		float x2 = -width2 / 2.0F;
		float y2 = 0.0F;

		// Vanilla two-pass nametag rendering pipeline:
		// Pass 1: SEE_THROUGH translucent pass with background plate (color 553648127 / 0x20FFFFFF)
		this.textRenderer.draw(
			squadBannerText,
			x1,
			y1,
			553648127,
			false,
			matrix4f,
			vertexConsumers,
			TextRenderer.TextLayerType.SEE_THROUGH,
			backgroundColor,
			LightmapTextureManager.MAX_LIGHT_COORDINATE
		);
		this.textRenderer.draw(
			roleCrestText,
			x2,
			y2,
			553648127,
			false,
			matrix4f,
			vertexConsumers,
			TextRenderer.TextLayerType.SEE_THROUGH,
			backgroundColor,
			LightmapTextureManager.MAX_LIGHT_COORDINATE
		);

		// Pass 2: NORMAL non-see-through pass with full opacity text and transparent background (when not sneaking)
		if (!entity.isInSneakingPose()) {
			this.textRenderer.draw(
				squadBannerText,
				x1,
				y1,
				-1,
				false,
				matrix4f,
				vertexConsumers,
				TextRenderer.TextLayerType.NORMAL,
				0,
				LightmapTextureManager.MAX_LIGHT_COORDINATE
			);
			this.textRenderer.draw(
				roleCrestText,
				x2,
				y2,
				-1,
				false,
				matrix4f,
				vertexConsumers,
				TextRenderer.TextLayerType.NORMAL,
				0,
				LightmapTextureManager.MAX_LIGHT_COORDINATE
			);
		}

		matrices.pop();
	}

	/**
	 * Formats the overhead squad banner text containing the flag icon, squad label, and Roman numeral designation.
	 *
	 * @param squad The squad group.
	 * @return Colored text component representing the squad banner.
	 */
	public static Text getSquadBanner(SquadGroup squad) {
		return getSquadBanner(squad, false);
	}

	/**
	 * Formats the overhead squad banner text containing the flag icon, squad label, Roman numeral designation,
	 * and an optional gold star indicator when the minion is actively selected.
	 *
	 * @param squad      The squad group.
	 * @param isSelected Whether the minion is currently selected by the commander.
	 * @return Colored text component representing the squad banner.
	 */
	public static Text getSquadBanner(SquadGroup squad, boolean isSelected) {
		String prefix = isSelected ? "§6★ " : "";
		return Text.literal(prefix + squad.getColorCode() + squad.getSquadBanner());
	}

	/**
	 * Formats the overhead role crest text containing the tactical icon, uppercase role name, and optional state.
	 *
	 * @param role      The archetype role.
	 * @param isSitting Whether the minion is currently holding position / sitting.
	 * @return Colored text component representing the role crest.
	 */
	public static Text getRoleCrest(MinionRole role, boolean isSitting) {
		String statusSuffix = isSitting ? " §e[HOLD]" : "";
		return Text.literal(role.getColorCode() + role.getBadgeLabel() + statusSuffix);
	}

	/**
	 * Resolves the tactical UTF-8 icon for the specified archetype role.
	 *
	 * @param role The role to query.
	 * @return UTF-8 icon symbol (⚔, 🛡, 🔨, ⛏, 🏹).
	 */
	public static String getRoleIcon(MinionRole role) {
		return role.getIcon();
	}

	/**
	 * Computes the world-space Y translation for overhead badges above the entity's feet.
	 * In upright world coordinates (after LIFO reversal of LivingEntityRenderer transformations),
	 * positive Y ascends upward above the entity's head.
	 *
	 * @param height        The base entity height.
	 * @param hasCustomName Whether the entity has a custom name tag rendered.
	 * @param isSneaking    Whether the entity is currently sneaking.
	 * @return The upright world-space Y offset directly above the entity head.
	 */
	public static float getOverheadYTranslation(float height, boolean hasCustomName, boolean isSneaking) {
		float headClearance = hasCustomName ? 0.85F : 0.55F;
		if (isSneaking) {
			headClearance -= 0.20F;
		}
		return height + headClearance;
	}

	public TextRenderer getTextRenderer() {
		return this.textRenderer;
	}

	public EntityRenderDispatcher getDispatcher() {
		return this.dispatcher;
	}
}
