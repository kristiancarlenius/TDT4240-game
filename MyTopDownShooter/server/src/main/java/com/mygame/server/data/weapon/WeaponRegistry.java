package com.mygame.server.data.weapon;

import com.mygame.server.domain.model.WeaponSpec;
import com.mygame.shared.dto.WeaponType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class WeaponRegistry {

    private final Map<WeaponType, WeaponSpec> specs = new EnumMap<>(WeaponType.class);

    // Weapon switch cycle for MVP (later replace with inventory)
    private final List<WeaponType> switchOrder = List.of(
            WeaponType.CROSSBOW,
            WeaponType.PISTOL,
            WeaponType.SHOTGUN
    );

    public WeaponRegistry() {
        // MVP numbers (tune later)
        specs.put(WeaponType.CROSSBOW, new WeaponSpec(
                WeaponType.CROSSBOW,
                55f,   // damage
                10f,   // speed
                0.12f, // radius
                2.0f,  // ttl
                1.2f,  // fireRate (slow)
                25,    // maxAmmo
                1,     // pellets
                0.00f  // spread
        ));

        specs.put(WeaponType.PISTOL, new WeaponSpec(
                WeaponType.PISTOL,
                25f,
                12f,
                0.12f,
                2.0f,
                6.0f,
                120,
                1,
                0.03f // slight spread
        ));

        specs.put(WeaponType.SHOTGUN, new WeaponSpec(
                WeaponType.SHOTGUN,
                12f,
                11f,
                0.11f,
                1.2f,
                1.0f,
                40,
                6,      // pellets
                0.25f   // wider cone
        ));
    }

    public WeaponSpec get(WeaponType type) {
        WeaponSpec spec = specs.get(type);
        if (spec == null) throw new IllegalArgumentException("Unknown weapon: " + type);
        return spec;
    }

    public List<WeaponType> getSwitchOrder() {
        return switchOrder;
    }

    public WeaponType nextInCycle(WeaponType current) {
        int idx = switchOrder.indexOf(current);
        if (idx < 0) return switchOrder.get(0);
        return switchOrder.get((idx + 1) % switchOrder.size());
    }
}