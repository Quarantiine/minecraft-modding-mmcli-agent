package com.example.network;

import com.example.ExampleMod;
import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client (S2C) payload synchronizing all custom player-designed blueprints
 * to clients for building catalog selection, 3D hologram rendering, and editor management.
 *
 * @param blueprints List of StructureBlueprints.
 */
public record SyncCustomBlueprintsPayload(
	List<StructureBlueprint> blueprints
) implements CustomPayload {

	public static final CustomPayload.Id<SyncCustomBlueprintsPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_custom_blueprints")
	);

	public static final PacketCodec<RegistryByteBuf, StructureBlueprint> BLUEPRINT_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, StructureBlueprint blueprint) {
			PacketCodecs.STRING.encode(buf, blueprint.getId());
			PacketCodecs.STRING.encode(buf, blueprint.getName());
			PacketCodecs.STRING.encode(buf, blueprint.getDescription() != null ? blueprint.getDescription() : "");
			BlueprintBlock.PACKET_CODEC.collect(PacketCodecs.toList()).encode(buf, blueprint.getBlocks());
		}

		@Override
		public StructureBlueprint decode(RegistryByteBuf buf) {
			String id = PacketCodecs.STRING.decode(buf);
			String name = PacketCodecs.STRING.decode(buf);
			String description = PacketCodecs.STRING.decode(buf);
			List<BlueprintBlock> blocks = BlueprintBlock.PACKET_CODEC.collect(PacketCodecs.toList()).decode(buf);

			StructureBlueprint.Builder builder = StructureBlueprint.builder(id, name)
				.description(description);
			if (blocks != null) {
				for (BlueprintBlock block : blocks) {
					if (block != null && block.state() != null && !block.state().isAir()) {
						builder.addBlock(block.offset(), block.state());
					}
				}
			}
			return builder.build();
		}
	};

	public static final PacketCodec<RegistryByteBuf, SyncCustomBlueprintsPayload> PACKET_CODEC = PacketCodec.tuple(
		BLUEPRINT_CODEC.collect(PacketCodecs.toList()),
		SyncCustomBlueprintsPayload::blueprints,
		SyncCustomBlueprintsPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
