package com.example.client.renderer;

import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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

		Text squadBannerText = getSquadBanner(squad);
		Text roleCrestText = getRoleCrest(role, entity.isSitting());

		matrices.push();

		// Compute vertical head clearance and invert Y translation so badge renders overhead above head rather than at feet
		float yTranslation = getOverheadYTranslation(entity.getHeight(), entity.hasCustomName(), entity.isInSneakingPose());

		// 1. Translate vertically in model space (negative Y projects upward above entity head in LivingEntityRenderer space)
		matrices.translate(0.0F, yTranslation, 0.0F);

		// 2. Counteract model scale so badge scale is independent of entity scaling
		matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE);

		// 3. Counteract body yaw rotation applied by LivingEntityRenderer.setupTransforms
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - 180.0F));

		// 4. Apply camera rotation to achieve full 3D billboarding
		matrices.multiply(this.dispatcher.getRotation());

		// 5. Scale matrix to text coordinate space (invert Y so text is upright)
		matrices.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

		Matrix4f matrix4f = matrices.peek().getPositionMatrix();

		// Compute standard nametag translucent background plate
		MinecraftClient client = MinecraftClient.getInstance();
		int backgroundOpacity = (client != null && client.options != null)
			? (int)(client.options.getTextBackgroundOpacity(0.25F) * 255.0F)
			: 64;
		int backgroundColor = (backgroundOpacity > 0 ? backgroundOpacity : 64) << 24;

		// Render Line 1: Squad Banner (Top line)
		float width1 = this.textRenderer.getWidth(squadBannerText);
		float x1 = -width1 / 2.0F;
		float y1 = -LINE_SPACING;
		this.textRenderer.draw(
			squadBannerText,
			x1,
			y1,
			-1,
			false,
			matrix4f,
			vertexConsumers,
			TextRenderer.TextLayerType.NORMAL,
			backgroundColor,
			light
		);

		// Render Line 2: Role Crest & Lettering (Bottom line)
		float width2 = this.textRenderer.getWidth(roleCrestText);
		float x2 = -width2 / 2.0F;
		float y2 = 0.0F;
		this.textRenderer.draw(
			roleCrestText,
			x2,
			y2,
			-1,
			false,
			matrix4f,
			vertexConsumers,
			TextRenderer.TextLayerType.NORMAL,
			backgroundColor,
			light
		);

		matrices.pop();
	}

	/**
	 * Formats the overhead squad banner text containing the flag icon, squad label, and Roman numeral designation.
	 *
	 * @param squad The squad group.
	 * @return Colored text component representing the squad banner.
	 */
	public static Text getSquadBanner(SquadGroup squad) {
		return Text.literal(squad.getColorCode() + squad.getSquadBanner());
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
	 * Computes the model-space Y translation for overhead badges.
	 * In entity model space (which has Y inverted by LivingEntityRenderer.setupTransforms),
	 * negative Y ascends upward toward and above the entity's head.
	 *
	 * @param height        The base entity height.
	 * @param hasCustomName Whether the entity has a custom name tag rendered.
	 * @param isSneaking    Whether the entity is currently sneaking.
	 * @return The inverted model-space Y offset (strictly negative for overhead positioning).
	 */
	public static float getOverheadYTranslation(float height, boolean hasCustomName, boolean isSneaking) {
		float baseHeight = height + 0.35F;
		if (hasCustomName) {
			baseHeight += 0.30F; // Elevate badge so it stacks cleanly above custom name tags
		}
		if (isSneaking) {
			baseHeight -= 0.20F;
		}
		return -(baseHeight / MODEL_SCALE);
	}

	public TextRenderer getTextRenderer() {
		return this.textRenderer;
	}

	public EntityRenderDispatcher getDispatcher() {
		return this.dispatcher;
	}
}
