package com.example.network;

import com.example.ExampleMod;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import java.util.Optional;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched by the Command Hub GUI
 * when the commanding player selects a new operating mode, active blueprint,
 * target squad filter, structure rotation angle, target minion archetype role,
 * active patrol route channel, or requests direct command execution.
 */
public record UpdateScepterPayload(
	CommandMode mode,
	String blueprintId,
	SquadGroup targetSquad,
	int rotation,
	Optional<MinionRole> targetRole,
	int activePatrolRoute,
	boolean executeDirective
) implements CustomPayload {

	public static final CustomPayload.Id<UpdateScepterPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "update_scepter")
	);

	public static final PacketCodec<RegistryByteBuf, UpdateScepterPayload> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, UpdateScepterPayload payload) {
			CommandMode.PACKET_CODEC.encode(buf, payload.mode());
			PacketCodecs.STRING.encode(buf, payload.blueprintId());
			SquadGroup.PACKET_CODEC.encode(buf, payload.targetSquad());
			PacketCodecs.INTEGER.encode(buf, payload.rotation());
			PacketCodecs.optional(MinionRole.PACKET_CODEC.cast()).encode(buf, payload.targetRole());
			PacketCodecs.INTEGER.encode(buf, payload.activePatrolRoute());
			PacketCodecs.BOOL.encode(buf, payload.executeDirective());
		}

		@Override
		public UpdateScepterPayload decode(RegistryByteBuf buf) {
			return new UpdateScepterPayload(
				CommandMode.PACKET_CODEC.decode(buf),
				PacketCodecs.STRING.decode(buf),
				SquadGroup.PACKET_CODEC.decode(buf),
				PacketCodecs.INTEGER.decode(buf),
				PacketCodecs.optional(MinionRole.PACKET_CODEC.cast()).decode(buf),
				PacketCodecs.INTEGER.decode(buf),
				PacketCodecs.BOOL.decode(buf)
			);
		}
	};

	public UpdateScepterPayload(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, Optional<MinionRole> targetRole, boolean executeDirective) {
		this(mode, blueprintId, targetSquad, rotation, targetRole, 0, executeDirective);
	}

	public UpdateScepterPayload(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, boolean executeDirective) {
		this(mode, blueprintId, targetSquad, rotation, Optional.empty(), 0, executeDirective);
	}

	public UpdateScepterPayload(CommandMode mode, String blueprintId, SquadGroup targetSquad, boolean executeDirective) {
		this(mode, blueprintId, targetSquad, 0, Optional.empty(), 0, executeDirective);
	}

	public UpdateScepterPayload(CommandMode mode, String blueprintId, boolean executeDirective) {
		this(mode, blueprintId, SquadGroup.ALL, 0, Optional.empty(), 0, executeDirective);
	}

	public UpdateScepterPayload(CommandMode mode, String blueprintId, int rotation, boolean executeDirective) {
		this(mode, blueprintId, SquadGroup.ALL, rotation, Optional.empty(), 0, executeDirective);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

