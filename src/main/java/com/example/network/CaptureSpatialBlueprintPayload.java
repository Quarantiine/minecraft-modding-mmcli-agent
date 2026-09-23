package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server (C2S) networking payload dispatched when a player triggers an in-world
 * spatial area capture in DESIGN mode to compile a custom blueprint from corner bounds.
 *
 * @param id          Unique custom blueprint identifier (e.g. "custom_12345").
 * @param name        User-facing blueprint display name.
 * @param description Optional user-facing blueprint description.
 * @param pos1        First corner position of the bounding volume.
 * @param pos2        Second corner position of the bounding volume.
 */
public record CaptureSpatialBlueprintPayload(
	String id,
	String name,
	String description,
	BlockPos pos1,
	BlockPos pos2
) implements CustomPayload {

	public static final CustomPayload.Id<CaptureSpatialBlueprintPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "capture_spatial_blueprint")
	);

	public static final PacketCodec<RegistryByteBuf, CaptureSpatialBlueprintPayload> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, CaptureSpatialBlueprintPayload payload) {
			PacketCodecs.STRING.encode(buf, payload.id());
			PacketCodecs.STRING.encode(buf, payload.name());
			PacketCodecs.STRING.encode(buf, payload.description() != null ? payload.description() : "");
			BlockPos.PACKET_CODEC.encode(buf, payload.pos1());
			BlockPos.PACKET_CODEC.encode(buf, payload.pos2());
		}

		@Override
		public CaptureSpatialBlueprintPayload decode(RegistryByteBuf buf) {
			String id = PacketCodecs.STRING.decode(buf);
			String name = PacketCodecs.STRING.decode(buf);
			String description = PacketCodecs.STRING.decode(buf);
			BlockPos pos1 = BlockPos.PACKET_CODEC.decode(buf);
			BlockPos pos2 = BlockPos.PACKET_CODEC.decode(buf);
			return new CaptureSpatialBlueprintPayload(id, name, description, pos1, pos2);
		}
	};

	public CaptureSpatialBlueprintPayload(String id, String name, BlockPos pos1, BlockPos pos2) {
		this(id, name, "", pos1, pos2);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
