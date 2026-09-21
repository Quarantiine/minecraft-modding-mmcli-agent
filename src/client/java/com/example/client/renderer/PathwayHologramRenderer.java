package com.example.client.renderer;

import com.example.component.CommandMode;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import com.example.patrol.PatrolRoute;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Client-side 3D holographic wireframe and waypoint renderer for designated patrol routes.
 * <p>
 * Visual Features:
 * <ul>
 *   <li><b>Surface Tile Outlines:</b> Displays color-coded bounding wireframes hovering on top of blocks.</li>
 *   <li><b>Floating Checkpoint Tags:</b> Holographic billboarding badges (`[ 1 ]`, `[ 2 ]`, etc.) hovering above tiles.</li>
 *   <li><b>Flow Laser Vectors:</b> Direct 3D laser tether lines connecting consecutive waypoints on top of surfaces.</li>
 *   <li><b>Channel Color Identity:</b> Distinct neon palette per route channel (Gold, Cyan, Emerald, Purple, Crimson).</li>
 *   <li><b>Selective Visibility:</b> Activates exclusively while holding the Command Scepter in PATHWAY mode.</li>
 * </ul>
 */
public class PathwayHologramRenderer {

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PathwayHologramRenderer::render);
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || client.player == null) {
			return;
		}

		MatrixStack matrices = context.matrixStack();
		Camera camera = context.camera();
		if (matrices == null || camera == null) {
			return;
		}

		ClientPlayerEntity player = client.player;
		ItemStack scepterStack = null;
		if (player.getMainHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getMainHandStack();
		} else if (player.getOffHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getOffHandStack();
		}

		// Only project pathway holograms and tethers when holding the Command Scepter in PATHWAY mode
		if (scepterStack == null) {
			return;
		}

		boolean isPathwayMode = CommandScepterItem.getMode(scepterStack) == CommandMode.PATHWAY;
		if (!isPathwayMode) {
			return;
		}

		int activeRouteId = CommandScepterItem.getActivePatrolRoute(scepterStack);
		Collection<PatrolRoute> routes = ClientPatrolRouteTracker.getAllRoutes();
		if (routes.isEmpty()) {
			return;
		}

		Vec3d cameraPos = camera.getPos();
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
		}
		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		// -------------------------------------------------------------
		// PASS 1: Line and Wireframe Box Geometry (RenderLayer.getLines)
		// -------------------------------------------------------------
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
		MatrixStack.Entry entry = matrices.peek();

		for (PatrolRoute route : routes) {
			List<BlockPos> waypoints = route.waypoints();
			if (waypoints.isEmpty()) {
				continue;
			}

			boolean isActiveChannel = (route.routeId() == activeRouteId);

			int colorRgb = route.colorRgb();
			float r = ((colorRgb >> 16) & 0xFF) / 255.0F;
			float g = ((colorRgb >> 8) & 0xFF) / 255.0F;
			float b = (colorRgb & 0xFF) / 255.0F;

			float alpha = isActiveChannel ? 0.90F : 0.65F;
			if (isActiveChannel) {
				float pulse = MathHelper.sin(client.player.age * 0.15F) * 0.10F;
				alpha = MathHelper.clamp(alpha + pulse, 0.40F, 1.0F);
			}

			// 1. Draw 3D Waypoint Tile Wireframes (elevated to sit on top of surface)
			for (int i = 0; i < waypoints.size(); i++) {
				BlockPos rawPos = waypoints.get(i);
				BlockPos renderPos = (client.world != null && !client.world.getBlockState(rawPos).isAir()) ? rawPos.up() : rawPos;
				Box box = new Box(renderPos);

				// Draw bounding wireframe
				WorldRenderer.drawBox(matrices, buffer, box, r, g, b, alpha);

				// Draw ground landing pad outline at bottom of tile space
				Box groundPad = new Box(
					renderPos.getX() + 0.05D, renderPos.getY() + 0.02D, renderPos.getZ() + 0.05D,
					renderPos.getX() + 0.95D, renderPos.getY() + 0.10D, renderPos.getZ() + 0.95D
				);
				WorldRenderer.drawBox(matrices, buffer, groundPad, r, g, b, alpha * 0.85F);

				// Inner pulse core on active channel
				if (isActiveChannel) {
					Box coreBox = box.shrink(0.15D, 0.15D, 0.15D);
					WorldRenderer.drawBox(matrices, buffer, coreBox, r, g, b, alpha * 0.5F);
				}
			}

			// 2. Draw direct 3D laser tether lines between consecutive waypoints (elevated on top of blocks)
			if (waypoints.size() > 1) {
				boolean isLoop = (route.patrolMode() == PatrolRoute.PatrolMode.LOOP);
				int segments = isLoop ? waypoints.size() : (waypoints.size() - 1);
				for (int i = 0; i < segments; i++) {
					BlockPos p1 = waypoints.get(i);
					BlockPos p2 = waypoints.get((i + 1) % waypoints.size());

					double y1 = (client.world != null && !client.world.getBlockState(p1).isAir()) ? p1.getY() + 1.15D : p1.getY() + 0.15D;
					double y2 = (client.world != null && !client.world.getBlockState(p2).isAir()) ? p2.getY() + 1.15D : p2.getY() + 0.15D;

					Vec3d c1 = new Vec3d(p1.getX() + 0.5D, y1, p1.getZ() + 0.5D);
					Vec3d c2 = new Vec3d(p2.getX() + 0.5D, y2, p2.getZ() + 0.5D);

					drawVectorLine(entry, buffer, c1, c2, r, g, b, alpha * 0.85F);
				}
			}
		}

		// 3. Render Escort Arcane Tether Beams connecting escorts to their squad leaders
		List<com.example.entity.custom.MinionEntity> nearbyMinions = client.world.getEntitiesByClass(
			com.example.entity.custom.MinionEntity.class,
			player.getBoundingBox().expand(64.0D),
			m -> m.isAlive() && m.hasLeader()
		);

		for (com.example.entity.custom.MinionEntity escort : nearbyMinions) {
			java.util.UUID leaderUuid = escort.getLeaderMinionUuid();
			if (leaderUuid == null) continue;

			com.example.entity.custom.MinionEntity leader = null;
			for (com.example.entity.custom.MinionEntity candidate : client.world.getEntitiesByClass(
				com.example.entity.custom.MinionEntity.class,
				escort.getBoundingBox().expand(64.0D),
				m -> m.isAlive() && leaderUuid.equals(m.getUuid()))) {
				leader = candidate;
				break;
			}
			if (leader == null) continue;

			Vec3d startPos = escort.getPos().add(0, escort.getHeight() * 0.6D, 0);
			Vec3d endPos = leader.getPos().add(0, leader.getHeight() * 0.6D, 0);

			float tetherPulse = 0.6F + MathHelper.sin((client.player.age + escort.getId() * 7) * 0.2F) * 0.25F;
			drawVectorLine(entry, buffer, startPos, endPos, 0.2F, 0.9F, 1.0F, tetherPulse);
		}

		// Flush line rendering before text rendering starts
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}

		// -------------------------------------------------------------
		// PASS 2: Floating Billboarding Number Badges (TextRenderer)
		// -------------------------------------------------------------
		for (PatrolRoute route : routes) {
			List<BlockPos> waypoints = route.waypoints();
			if (waypoints.isEmpty()) {
				continue;
			}
			int colorRgb = route.colorRgb();

			for (int i = 0; i < waypoints.size(); i++) {
				BlockPos pos = waypoints.get(i);
				double badgeY = (client.world != null && !client.world.getBlockState(pos).isAir()) ? pos.getY() + 1.60D : pos.getY() + 1.25D;

				matrices.push();
				matrices.translate(pos.getX() + 0.5D, badgeY, pos.getZ() + 0.5D);
				matrices.multiply(camera.getRotation());
				matrices.scale(-0.025F, -0.025F, 0.025F);

				Matrix4f matrix = matrices.peek().getPositionMatrix();
				String badgeText = "§l[ " + (i + 1) + " ]";
				Text label = Text.literal(badgeText);
				float xOffset = -client.textRenderer.getWidth(label) / 2.0F;

				client.textRenderer.draw(
					label,
					xOffset,
					0,
					colorRgb,
					false,
					matrix,
					consumers,
					TextRenderer.TextLayerType.SEE_THROUGH,
					0x40000000,
					0xF000F0
				);

				matrices.pop();
			}
		}

		matrices.pop();
	}

	/**
	 * Draws a true 3D vector laser line between two world space coordinates with a subtle glowing aura.
	 */
	private static void drawVectorLine(
		MatrixStack.Entry entry,
		VertexConsumer buffer,
		Vec3d start,
		Vec3d end,
		float r,
		float g,
		float b,
		float alpha
	) {
		float dx = (float) (end.x - start.x);
		float dy = (float) (end.y - start.y);
		float dz = (float) (end.z - start.z);
		float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = len > 0.0001F ? dx / len : 0.0F;
		float ny = len > 0.0001F ? dy / len : 1.0F;
		float nz = len > 0.0001F ? dz / len : 0.0F;

		// Core laser beam
		buffer.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
			.color(r, g, b, alpha)
			.normal(entry, nx, ny, nz);
		buffer.vertex(entry, (float) end.x, (float) end.y, (float) end.z)
			.color(r, g, b, alpha)
			.normal(entry, nx, ny, nz);

		// Slightly elevated parallel glow to ensure high visibility across surfaces
		float offset = 0.035F;
		buffer.vertex(entry, (float) start.x, (float) start.y + offset, (float) start.z)
			.color(r, g, b, alpha * 0.70F)
			.normal(entry, nx, ny, nz);
		buffer.vertex(entry, (float) end.x, (float) end.y + offset, (float) end.z)
			.color(r, g, b, alpha * 0.70F)
			.normal(entry, nx, ny, nz);
	}
}
