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
 * or requests direct command execution.
 *
 * @param mode             The updated CommandMode to apply to the held scepter.
 * @param blueprintId      The identifier of the active blueprint selected in the catalog.
 * @param targetSquad      The target SquadGroup channel to filter command broadcasts.
 * @param rotation         The structure rotation index (0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°).
 * @param targetRole       Optional target MinionRole archetype to transform minions into upon channeled rally release.
 * @param executeDirective If true, immediately triggers tactical minion broadcast or mode action.
 */
public record UpdateScepterPayload(
	CommandMode mode,
	String blueprintId,
	SquadGroup targetSquad,
	int rotation,
	Optional<MinionRole> targetRole,
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
		PacketCodecs.optional(MinionRole.PACKET_CODEC.cast()),
		UpdateScepterPayload::targetRole,
		PacketCodecs.BOOL,
		UpdateScepterPayload::executeDirective,
		UpdateScepterPayload::new
	);

	/**
	 * Backward compatibility constructor without optional target role, defaulting targetRole to empty.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The target SquadGroup channel.
	 * @param rotation         The structure rotation index (0-3).
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, SquadGroup targetSquad, int rotation, boolean executeDirective) {
		this(mode, blueprintId, targetSquad, rotation, Optional.empty(), executeDirective);
	}

	/**
	 * Backward compatibility constructor defaulting rotation to 0 and targetRole to empty.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param targetSquad      The target SquadGroup channel.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, SquadGroup targetSquad, boolean executeDirective) {
		this(mode, blueprintId, targetSquad, 0, Optional.empty(), executeDirective);
	}

	/**
	 * Backward compatibility constructor defaulting targetSquad to {@link SquadGroup#ALL}, rotation to 0, and targetRole to empty.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, boolean executeDirective) {
		this(mode, blueprintId, SquadGroup.ALL, 0, Optional.empty(), executeDirective);
	}

	/**
	 * Constructor allowing specification of rotation with default targetSquad {@link SquadGroup#ALL} and empty targetRole.
	 *
	 * @param mode             The updated CommandMode.
	 * @param blueprintId      The active architectural blueprint identifier.
	 * @param rotation         The structure rotation index (0-3).
	 * @param executeDirective True if immediate directive execution is requested.
	 */
	public UpdateScepterPayload(CommandMode mode, String blueprintId, int rotation, boolean executeDirective) {
		this(mode, blueprintId, SquadGroup.ALL, rotation, Optional.empty(), executeDirective);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

