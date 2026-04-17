package com.mygame.server.data.weapon;

import com.mygame.server.domain.model.WeaponSpec;
import com.mygame.shared.dto.WeaponType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR7.1 — All players start with the same default weapon (CROSSBOW).
 * FR7.3 — Players shall be able to switch between weapons.
 * FR8.3 — Each weapon shall have configurable attributes (damage, speed, range).
 * M1    — Adding a new weapon should only require a new WeaponSpec entry.
 */
class WeaponRegistryTest {

    private WeaponRegistry registry;

    @BeforeEach
    void setup() {
        registry = new WeaponRegistry(); // uses hardcoded defaults
    }

    // FR7.1 — CROSSBOW is the starter weapon
    @Test
    void crossbow_isAvailableInRegistry() {
        assertNotNull(registry.get(WeaponType.CROSSBOW));
    }

    @Test
    void crossbow_isTierZero_neverDrops() {
        WeaponSpec crossbow = registry.get(WeaponType.CROSSBOW);
        assertEquals(0, crossbow.tier, "Crossbow tier must be 0 so it never drops on death");
    }

    // FR8.3 — weapons have configurable attributes
    @Test
    void allWeapons_haveDamageGreaterThanZero() {
        for (WeaponType type : WeaponType.values()) {
            WeaponSpec spec = registry.get(type);
            assertTrue(spec.damage > 0f, type + " must have damage > 0");
        }
    }

    @Test
    void allWeapons_havePositiveProjectileSpeed() {
        for (WeaponType type : WeaponType.values()) {
            WeaponSpec spec = registry.get(type);
            assertTrue(spec.projectileSpeed > 0f, type + " must have projectileSpeed > 0");
        }
    }

    @Test
    void allWeapons_havePositiveMaxAmmo() {
        for (WeaponType type : WeaponType.values()) {
            WeaponSpec spec = registry.get(type);
            assertTrue(spec.maxAmmo > 0, type + " must have maxAmmo > 0");
        }
    }

    @Test
    void allWeapons_havePositiveFireRate() {
        for (WeaponType type : WeaponType.values()) {
            WeaponSpec spec = registry.get(type);
            assertTrue(spec.fireRate > 0f, type + " must have fireRate > 0");
        }
    }

    @Test
    void allWeapons_havePositiveTtl() {
        for (WeaponType type : WeaponType.values()) {
            WeaponSpec spec = registry.get(type);
            assertTrue(spec.ttlSeconds > 0f, type + " must have ttlSeconds > 0");
        }
    }

    // FR8.3 — sniper deals more damage than pistol (tier differentiation)
    @Test
    void sniper_dealMoreDamageThanPistol() {
        assertTrue(registry.get(WeaponType.SNIPER).damage > registry.get(WeaponType.PISTOL).damage);
    }

    // FR7.3 — cycling through weapons
    @Test
    void nextInCycle_returnsDifferentWeapon() {
        WeaponType next = registry.nextInCycle(WeaponType.CROSSBOW);
        assertNotNull(next);
        assertNotEquals(WeaponType.CROSSBOW, next);
    }

    @Test
    void nextInCycle_wrapsAround() {
        List<WeaponType> order = registry.getSwitchOrder();
        WeaponType last = order.get(order.size() - 1);
        WeaponType first = order.get(0);
        assertEquals(first, registry.nextInCycle(last));
    }

    // M1 — Adding a new weapon only requires adding a WeaponSpec
    @Test
    void addingNewWeaponSpec_registryContainsIt() {
        List<WeaponSpec> specs = new ArrayList<>();
        specs.add(new WeaponSpec(WeaponType.PISTOL, 1,
                30f, 12f, 0.1f, 2f, 3f, 15, 2, 1, 0.03f, 1f));
        WeaponRegistry custom = new WeaponRegistry(specs);
        assertNotNull(custom.get(WeaponType.PISTOL));
    }

    @Test
    void unknownWeapon_throwsException() {
        List<WeaponSpec> empty = new ArrayList<>();
        empty.add(new WeaponSpec(WeaponType.CROSSBOW, 0,
                50f, 10f, 0.1f, 2f, 1f, 10, 2, 1, 0f, 1f));
        WeaponRegistry r = new WeaponRegistry(empty);
        assertThrows(IllegalArgumentException.class, () -> r.get(WeaponType.SNIPER));
    }
}
