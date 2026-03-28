package com.mygame.server.data.weapon;

import com.mygame.server.domain.model.WeaponSpec;
import com.mygame.shared.dto.WeaponType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class WeaponRegistry {

    private final Map<WeaponType, WeaponSpec> specs = new EnumMap<>(WeaponType.class);
    private final List<WeaponType> switchOrder;

    /** Construct from a list loaded externally (e.g. by WeaponLoader). */
    public WeaponRegistry(List<WeaponSpec> specList) {
        List<WeaponType> order = new ArrayList<>();
        for (WeaponSpec s : specList) {
            specs.put(s.type, s);
            order.add(s.type);
        }
        this.switchOrder = List.copyOf(order);
    }

    /** Fallback constructor with hardcoded defaults. */
    public WeaponRegistry() {
        this(defaultSpecs());
    }

    private static List<WeaponSpec> defaultSpecs() {
        // WeaponSpec(type, tier, dmg, projSpeed, projRadius, ttl, fireRate,
        //            maxAmmo, numMagazines, pellets, spreadRad, reloadSec)
        List<WeaponSpec> list = new ArrayList<>();

        // T0 – starter, never drops as weapon on death
        list.add(new WeaponSpec(WeaponType.CROSSBOW,    0,
                55f, 10f, 0.12f, 2.0f,  1.2f,  10, 2, 1, 0.00f, 1.35f));

        // T1
        list.add(new WeaponSpec(WeaponType.PISTOL,      1,
                28f, 14f, 0.10f, 2.2f,  3.5f,  15, 2, 1, 0.03f, 1.08f));

        // T2 – short-range brawlers
        list.add(new WeaponSpec(WeaponType.UZI,         2,
                 8f, 10f, 0.09f, 0.45f,15.0f,  32, 2, 1, 0.07f, 1.4f));
        list.add(new WeaponSpec(WeaponType.SHOTGUN,     2,
                10f, 11f, 0.11f, 0.55f,  1.5f,  8, 2, 6, 0.28f, 1.8f));

        // T3
        list.add(new WeaponSpec(WeaponType.AK,          3,
                22f, 13f, 0.10f, 2.0f,  9.0f,  30, 2, 1, 0.05f, 1.8f));
        list.add(new WeaponSpec(WeaponType.MACHINEGUN,  3,
                18f, 15f, 0.09f, 1.8f, 14.0f,  50, 2, 1, 0.08f, 2.25f));

        // T4
        list.add(new WeaponSpec(WeaponType.SNIPER,      4,
                90f, 22f, 0.08f, 3.5f, 0.36f,   5, 2, 1, 0.00f, 2.7f));

        // T5
        list.add(new WeaponSpec(WeaponType.FLAMETHROWER,5,
                 8f,  9f, 0.09f, 0.42f,12.0f,  60, 2,10, 0.70f, 2.25f));

        return list;
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