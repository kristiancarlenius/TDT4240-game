package com.mygame.server.domain.model;

import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR7   — Players shall have an inventory for storing multiple weapons.
 * FR7.1 — All players shall start with the same default weapon.
 * FR7.2 — The inventory shall limit the maximum number of weapons a player can carry.
 * FR7.3 — Players shall be able to switch between weapons.
 * FR8.4 — System shall allow the player to change the weapon they are using.
 */
class InventoryTest {

    private PlayerState player;

    @BeforeEach
    void setup() {
        player = new PlayerState("p1", "TestPlayer", new Vec2(5f, 5f));
    }

    // FR7.1 — default weapon is CROSSBOW
    @Test
    void newPlayer_equippedWithCrossbow() {
        assertEquals(WeaponType.CROSSBOW, player.inventory[0]);
    }

    @Test
    void newPlayer_secondarySlotEmpty() {
        assertNull(player.inventory[1]);
    }

    // FR7.2 — max 2 weapons
    @Test
    void inventory_hasExactlyTwoSlots() {
        assertEquals(2, player.inventory.length);
    }

    @Test
    void inventory_hasExactlyTwoAmmoSlots() {
        assertEquals(2, player.ammoBySlot.length);
    }

    // FR7.3 / FR8.4 — switch between slots
    @Test
    void switchSlot_changesCurrentSlot() {
        player.inventory[1] = WeaponType.PISTOL;
        player.currentSlot = 1;
        player.syncEquipped();
        assertEquals(WeaponType.PISTOL, player.equippedWeaponType);
    }

    @Test
    void syncEquipped_reflectsCurrentSlot() {
        player.inventory[0] = WeaponType.CROSSBOW;
        player.inventory[1] = WeaponType.AK;
        player.ammoBySlot[1] = 30;
        player.currentSlot = 1;
        player.syncEquipped();

        assertEquals(WeaponType.AK, player.equippedWeaponType);
        assertEquals(30, player.equippedAmmo);
    }

    @Test
    void switchBackToFirstSlot_equippedUpdated() {
        player.inventory[1] = WeaponType.SNIPER;
        player.currentSlot = 1;
        player.syncEquipped();
        assertEquals(WeaponType.SNIPER, player.equippedWeaponType);

        player.currentSlot = 0;
        player.syncEquipped();
        assertEquals(WeaponType.CROSSBOW, player.equippedWeaponType);
    }

    // FR6 — player status includes equipped weapon in DTO
    @Test
    void toDto_includesEquippedWeapon() {
        player.ammoBySlot[0] = 10;
        player.syncEquipped();
        var dto = player.toDto();
        assertEquals(WeaponType.CROSSBOW, dto.equippedWeaponType);
    }

    @Test
    void toDto_includesSecondaryWeapon() {
        player.inventory[1] = WeaponType.UZI;
        player.ammoBySlot[1] = 32;
        var dto = player.toDto();
        assertEquals(WeaponType.UZI, dto.secondaryWeaponType);
    }
}
