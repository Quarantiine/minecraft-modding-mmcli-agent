package com.example.client.gui;

import com.example.entity.custom.MinionRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test suite validating the Command Hub archetype toggle selection mechanism
 * and state management in {@link CommandScepterScreen}.
 */
public class CommandScepterScreenRoleSelectionTest {

	@Test
	@DisplayName("Initial selectedRole defaults to null when opened without an assigned scepter role")
	void testDefaultSelectedRoleIsNull() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertNull(screen.getSelectedRole(), "Default selected role must be null when unassigned");
	}

	@Test
	@DisplayName("Toggling a role selects it, and toggling the same role again deselects it (toggle off)")
	void testToggleRoleSelectionAndDeselection() {
		CommandScepterScreen screen = new CommandScepterScreen();

		// Select Warrior
		screen.toggleRole(MinionRole.WARRIOR);
		Assertions.assertEquals(MinionRole.WARRIOR, screen.getSelectedRole(), "Toggling WARRIOR should select it");

		// Toggle Warrior again -> deselects to null
		screen.toggleRole(MinionRole.WARRIOR);
		Assertions.assertNull(screen.getSelectedRole(), "Toggling already selected WARRIOR must deselect it to null");
	}

	@Test
	@DisplayName("Toggling a different role switches active selection to the new archetype")
	void testToggleSwitchesBetweenRoles() {
		CommandScepterScreen screen = new CommandScepterScreen();

		screen.toggleRole(MinionRole.SENTINEL);
		Assertions.assertEquals(MinionRole.SENTINEL, screen.getSelectedRole(), "Initial toggle should select SENTINEL");

		// Toggling WARRIOR should switch selection to WARRIOR
		screen.toggleRole(MinionRole.WARRIOR);
		Assertions.assertEquals(MinionRole.WARRIOR, screen.getSelectedRole(), "Toggling WARRIOR should switch selection to WARRIOR");

		// Toggling MINER should switch selection to MINER
		screen.toggleRole(MinionRole.MINER);
		Assertions.assertEquals(MinionRole.MINER, screen.getSelectedRole(), "Toggling MINER should switch selection to MINER");

		// Toggling BUILDER should switch selection to BUILDER
		screen.toggleRole(MinionRole.BUILDER);
		Assertions.assertEquals(MinionRole.BUILDER, screen.getSelectedRole(), "Toggling BUILDER should switch selection to BUILDER");

		// Toggling BUILDER again should clear selection
		screen.toggleRole(MinionRole.BUILDER);
		Assertions.assertNull(screen.getSelectedRole(), "Toggling BUILDER again should clear selection to null");
	}

	@Test
	@DisplayName("Directly setting role via setSelectedRole updates active selection and handles null")
	void testSetSelectedRoleDirectly() {
		CommandScepterScreen screen = new CommandScepterScreen();

		screen.setSelectedRole(MinionRole.SENTINEL);
		Assertions.assertEquals(MinionRole.SENTINEL, screen.getSelectedRole(), "setSelectedRole should assign SENTINEL");

		screen.setSelectedRole(MinionRole.WARRIOR);
		Assertions.assertEquals(MinionRole.WARRIOR, screen.getSelectedRole(), "setSelectedRole should assign WARRIOR");

		screen.setSelectedRole(null);
		Assertions.assertNull(screen.getSelectedRole(), "setSelectedRole(null) should clear selection to null");
	}

	@Test
	@DisplayName("Verify all 4 MinionRole archetypes can be selected sequentially")
	void testAllArchetypeRolesSelectable() {
		CommandScepterScreen screen = new CommandScepterScreen();

		for (MinionRole role : MinionRole.values()) {
			screen.setSelectedRole(role);
			Assertions.assertEquals(role, screen.getSelectedRole(), "Archetype " + role + " must be selectable");
		}
	}
}
