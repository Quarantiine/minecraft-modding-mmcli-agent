package com.example.client.renderer;

import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating overhead badge crest banner text formatting,
 * tactical icon mappings, Roman numeral designations, and status suffixes.
 */
public class MinionOverheadBadgeTest {

	@Test
	@DisplayName("Validate squad banner formatting and Roman numerals for all squads")
	void testSquadBanners() {
		Assertions.assertEquals("[I]", SquadGroup.ALPHA.getRomanNumeral());
		Assertions.assertEquals("[II]", SquadGroup.BRAVO.getRomanNumeral());
		Assertions.assertEquals("[III]", SquadGroup.CHARLIE.getRomanNumeral());
		Assertions.assertEquals("[IV]", SquadGroup.DELTA.getRomanNumeral());
		Assertions.assertEquals("[*]", SquadGroup.ALL.getRomanNumeral());

		Assertions.assertEquals("⚑ SQUAD ALPHA [I]", SquadGroup.ALPHA.getSquadBanner());
		Assertions.assertEquals("⚑ SQUAD BRAVO [II]", SquadGroup.BRAVO.getSquadBanner());
		Assertions.assertEquals("⚑ SQUAD CHARLIE [III]", SquadGroup.CHARLIE.getSquadBanner());
		Assertions.assertEquals("⚑ SQUAD DELTA [IV]", SquadGroup.DELTA.getSquadBanner());
		Assertions.assertEquals("⚑ SQUAD ALL [*]", SquadGroup.ALL.getSquadBanner());

		Text alphaBanner = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.ALPHA);
		Assertions.assertEquals("§c⚑ SQUAD ALPHA [I]", alphaBanner.getString());

		Text bravoBanner = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.BRAVO);
		Assertions.assertEquals("§9⚑ SQUAD BRAVO [II]", bravoBanner.getString());

		Text charlieBanner = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.CHARLIE);
		Assertions.assertEquals("§a⚑ SQUAD CHARLIE [III]", charlieBanner.getString());

		Text deltaBanner = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.DELTA);
		Assertions.assertEquals("§6⚑ SQUAD DELTA [IV]", deltaBanner.getString());

		Text allBanner = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.ALL);
		Assertions.assertEquals("§f⚑ SQUAD ALL [*]", allBanner.getString());
	}

	@Test
	@DisplayName("Validate role icons and lettering for all archetype roles")
	void testRoleCrests() {
		Assertions.assertEquals("⚔", MinionRole.WARRIOR.getIcon());
		Assertions.assertEquals("🛡", MinionRole.SENTINEL.getIcon());
		Assertions.assertEquals("🔨", MinionRole.BUILDER.getIcon());
		Assertions.assertEquals("⛏", MinionRole.MINER.getIcon());
		Assertions.assertEquals("🏹", MinionRole.RANGER.getIcon());

		Assertions.assertEquals("⚔ WARRIOR", MinionRole.WARRIOR.getBadgeLabel());
		Assertions.assertEquals("🛡 SENTINEL", MinionRole.SENTINEL.getBadgeLabel());
		Assertions.assertEquals("🔨 BUILDER", MinionRole.BUILDER.getBadgeLabel());
		Assertions.assertEquals("⛏ MINER", MinionRole.MINER.getBadgeLabel());
		Assertions.assertEquals("🏹 RANGER", MinionRole.RANGER.getBadgeLabel());

		// Standing / Active
		Text warriorCrest = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.WARRIOR, false);
		Assertions.assertEquals("§c⚔ WARRIOR", warriorCrest.getString());

		Text sentinelCrest = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.SENTINEL, false);
		Assertions.assertEquals("§a🛡 SENTINEL", sentinelCrest.getString());

		Text builderCrest = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.BUILDER, false);
		Assertions.assertEquals("§9🔨 BUILDER", builderCrest.getString());

		Text minerCrest = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.MINER, false);
		Assertions.assertEquals("§6⛏ MINER", minerCrest.getString());

		Text rangerCrest = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.RANGER, false);
		Assertions.assertEquals("§5🏹 RANGER", rangerCrest.getString());

		// Sitting / Holding Position
		Text warriorHold = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.WARRIOR, true);
		Assertions.assertEquals("§c⚔ WARRIOR §e[HOLD]", warriorHold.getString());

		Text sentinelHold = MinionOverheadBadgeFeatureRenderer.getRoleCrest(MinionRole.SENTINEL, true);
		Assertions.assertEquals("§a🛡 SENTINEL §e[HOLD]", sentinelHold.getString());
	}

	@Test
	@DisplayName("Validate renderer configuration constants")
	void testConstants() {
		Assertions.assertTrue(MinionOverheadBadgeFeatureRenderer.MAX_RENDER_DISTANCE_SQ > 0);
		Assertions.assertEquals(4096.0D, MinionOverheadBadgeFeatureRenderer.MAX_RENDER_DISTANCE_SQ);
		Assertions.assertEquals(0.025F, MinionOverheadBadgeFeatureRenderer.TEXT_SCALE);
		Assertions.assertEquals(0.9375F, MinionOverheadBadgeFeatureRenderer.MODEL_SCALE);
		Assertions.assertEquals(11.0F, MinionOverheadBadgeFeatureRenderer.LINE_SPACING);
	}

	@Test
	@DisplayName("Validate selected squad banner formatting with gold star prefix")
	void testSelectedSquadBanner() {
		Text unselected = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.ALPHA, false);
		Assertions.assertEquals("§c⚑ SQUAD ALPHA [I]", unselected.getString());

		Text selected = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.ALPHA, true);
		Assertions.assertEquals("§6★ §c⚑ SQUAD ALPHA [I]", selected.getString());

		Text selectedBravo = MinionOverheadBadgeFeatureRenderer.getSquadBanner(SquadGroup.BRAVO, true);
		Assertions.assertEquals("§6★ §9⚑ SQUAD BRAVO [II]", selectedBravo.getString());
	}

	@Test
	@DisplayName("Validate upright overhead Y-translation positioning above head")
	void testOverheadYTranslation() {
		float defaultHeight = 1.95F;

		// Base translation is in upright world space (above feet, just above top of head with 0.55F clearance to prevent mesh clipping)
		float standardY = MinionOverheadBadgeFeatureRenderer.getOverheadYTranslation(defaultHeight, false, false);
		Assertions.assertTrue(standardY > defaultHeight, "Standard overhead badge translation must be above entity head");
		float expectedStandard = 1.95F + 0.55F;
		Assertions.assertEquals(expectedStandard, standardY, 1e-4F);

		// With custom name tag, Y is elevated higher (0.85F clearance) to prevent nametag overlap
		float namedY = MinionOverheadBadgeFeatureRenderer.getOverheadYTranslation(defaultHeight, true, false);
		Assertions.assertTrue(namedY > standardY, "Custom name tag must elevate the badge higher");
		float expectedNamed = 1.95F + 0.85F;
		Assertions.assertEquals(expectedNamed, namedY, 1e-4F);

		// When sneaking, Y is lowered along with the crouching pose
		float sneakingY = MinionOverheadBadgeFeatureRenderer.getOverheadYTranslation(defaultHeight, false, true);
		Assertions.assertTrue(sneakingY < standardY, "Sneaking pose must lower the badge");
		float expectedSneaking = 1.95F + 0.55F - 0.20F;
		Assertions.assertEquals(expectedSneaking, sneakingY, 1e-4F);
	}
}
