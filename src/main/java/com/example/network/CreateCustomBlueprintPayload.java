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
 * Client-to-server (C2S) networking payload dispatched from the custom blueprint editor
 * to save or compile a player-designed voxel blueprint.
 *
 * @param id          Unique custom blueprint identifier (e.g. "custom_12345").
 * @param name        User-facing blueprint display name.
 * @param description Optional user-facing blueprint description.
 * @param blocks      List of voxel block offsets and BlockStates.
 */
public record CreateCustomBlueprintPayload(
	String id,
	String name,
	String description,
	List<BlueprintBlock> blocks
) implements CustomPayload {

	public static final CustomPayload.Id<CreateCustomBlueprintPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "create_custom_blueprint")
	);

	public static final PacketCodec<RegistryByteBuf, CreateCustomBlueprintPayload> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, CreateCustomBlueprintPayload payload) {
			PacketCodecs.STRING.encode(buf, payload.id());
			PacketCodecs.STRING.encode(buf, payload.name());
			PacketCodecs.STRING.encode(buf, payload.description() != null ? payload.description() : "");
			BlueprintBlock.PACKET_CODEC.collect(PacketCodecs.toList()).encode(buf, payload.blocks());
		}

		@Override
		public CreateCustomBlueprintPayload decode(RegistryByteBuf buf) {
			String id = PacketCodecs.STRING.decode(buf);
			String name = PacketCodecs.STRING.decode(buf);
			String description = PacketCodecs.STRING.decode(buf);
			List<BlueprintBlock> blocks = BlueprintBlock.PACKET_CODEC.collect(PacketCodecs.toList()).decode(buf);
			return new CreateCustomBlueprintPayload(id, name, description, blocks != null ? blocks : new ArrayList<>());
		}
	};

	public CreateCustomBlueprintPayload(String id, String name, List<BlueprintBlock> blocks) {
		this(id, name, "", blocks);
	}

	public CreateCustomBlueprintPayload(StructureBlueprint blueprint) {
		this(
			blueprint.getId(),
			blueprint.getName(),
			blueprint.getDescription() != null ? blueprint.getDescription() : "",
			new ArrayList<>(blueprint.getBlocks())
		);
	}

	/**
	 * Compiles this payload's block specifications into a fully normalized, sorted {@link StructureBlueprint}.
	 *
	 * @return Topologically sorted and normalized StructureBlueprint.
	 */
	public StructureBlueprint toStructureBlueprint() {
		List<BlueprintBlock> normalizedBlocks = StructureBlueprint.normalizeBlocks(this.blocks);
		StructureBlueprint.Builder builder = StructureBlueprint.builder(this.id, this.name)
			.description(this.description != null ? this.description : "");
		for (BlueprintBlock block : normalizedBlocks) {
			if (block != null && block.state() != null && !block.state().isAir()) {
				builder.addBlock(block.offset(), block.state());
			}
		}
		return builder.build();
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
