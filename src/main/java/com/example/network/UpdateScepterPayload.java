package com.example.network;

import com.example.ExampleMod;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched by the Command Hub GUI
 * when the commanding player selects a new operating mode, active blueprint,
 * target squad filter, structure rotation angle, or requests direct command execution.
 *
 * @param mode             The updated CommandMode to apply to the held scepter.
 * @param blueprintId      The identifier of the active blueprint selected in the catalog.
 * @param targetSquad      The target SquadGroup channel to filter command broadcasts.
 * @param rotation         The structure rotation index (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
 * @param executeDirective If true, immediately triggers tactical minion broadcast or mode action.
 */
public record UpdateScepterPayload(
	CommandMode mode,
	String blueprintId,
	SquadGroup targetSquad,
	int rotation,
	boolean executeDirective
) implements CustomPayload {

	public static final CustomPayload.Id<UpdateScepterPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "update_scepter")
	);

	public static final PacketCodec<RegistryByteBuf, UpdateScepterPayload> PACKET_CODEC = PacketCodec.tuple(
		CommandMode.PACKET_CODEC.cast(),
		UpdateScepterPayload::mode,
		PacketCodecs.STRING,
		UpdateScepterPayload::blueprintId,
		SquadGroup.PACKET_CODEC.cast(),
		UpdateScepterPayload::targetSquad,
		PacketCodecs.INTEGER,
		UpdateScepterPayload::rotation,
		PacketCodecs.BOOL,
		UpdateScepterPayload::executeDirective,
		UpdateScepterPayload::new
	);

	/**
	 * Backward compatibility constructor defaulting rotation to 0.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The target SquadGroup channel.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, SquadGroup targetSquad, boolean executeDirective) {
		this(mode, blueprintId, targetSquad, 0, executeDirective);
	}

	/**
	 * Backward compatibility constructor defaulting targetSquad to {@link SquadGroup#ALL} and rotation to 0.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, boolean executeDirective) {
		this(mode, blueprintId, SquadGroup.ALL, 0, executeDirective);
	}

	/**
	 * Constructor allowing specification of rotation with default targetSquad {@link SquadGroup#ALL}.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param rotation         The structure rotation index (0-3).
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, int rotation, boolean executeDirective) {
		this(mode, blueprintId, SquadGroup.ALL, rotation, executeDirective);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

