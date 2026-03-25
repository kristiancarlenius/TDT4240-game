package com.mygame.server.application.service;

import com.mygame.server.data.map.MapProvider;
import com.mygame.server.data.weapon.WeaponLoader;
import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.server.domain.system.CollisionSystem;
import com.mygame.server.domain.system.PickupSpawnSystem;
import com.mygame.server.domain.system.PlayerSystem;
import com.mygame.server.domain.system.ProjectileSystem;
import com.mygame.server.domain.system.ServerWorldStepSystem;
import com.mygame.shared.dto.*;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinAccepted;
import com.mygame.shared.util.Vec2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchService {

    private final ServerGameState       state;
    private final WeaponRegistry        weaponRegistry;
    private final ServerWorldStepSystem worldStepSystem;
    private final Random                rng = new Random();

    // Latest input per player (last-write-wins within each tick window)
    private final Map<String, InputMessage> latestInput = new ConcurrentHashMap<>();

    public MatchService() {
        this.state          = new MapProvider().provide("map01");
        this.weaponRegistry = loadWeapons("weapons/weapons.json");

        PlayerSystem      player     = new PlayerSystem(state);
        CollisionSystem   collision  = new CollisionSystem(state);
        ProjectileSystem  projectile = new ProjectileSystem(state, player);
        PickupSpawnSystem pickup     = new PickupSpawnSystem(state, weaponRegistry, rng);

        this.worldStepSystem = new ServerWorldStepSystem(
                state, weaponRegistry, collision, projectile, pickup, player);
    }

    // ---- Player management ----

    public String addPlayer(String username) {
        String id    = UUID.randomUUID().toString();
        Vec2   spawn = state.findNextSpawn();
        PlayerState ps = new PlayerState(id, username, spawn);
        // Set starter ammo from registry
        int startAmmo = weaponRegistry.get(ps.equippedWeaponType).maxAmmo;
        ps.equippedAmmo    = startAmmo;
        ps.ammoBySlot[0]   = startAmmo;
        state.players.put(id, ps);
        System.out.println("[MATCH] Joined: " + username + "  id=" + id + "  spawn=" + spawn);
        return id;
    }

    public void removePlayer(String playerId) {
        state.players.remove(playerId);
        latestInput.remove(playerId);
        System.out.println("[MATCH] Left: id=" + playerId);
    }

    public void submitInput(String playerId, InputMessage input) {
        latestInput.put(playerId, input);
    }

    // ---- Tick ----

    public void tick(float dt) {
        worldStepSystem.tick(dt, latestInput);
    }

    // ---- Snapshot / join payload ----

    public JoinAccepted buildJoinAccepted(String playerId) {
        return new JoinAccepted(playerId, state.toMapDto(), buildSnapshot());
    }

    public GameSnapshotDto buildSnapshot() {
        List<PlayerDto> players = new ArrayList<>();
        for (PlayerState p : state.players.values()) {
            players.add(p.toDto());
        }

        ProjectileDto[] projs = state.projectiles.stream()
                .map(pr -> pr.toDto())
                .toArray(ProjectileDto[]::new);

        PickupDto[] pickups = state.pickups.stream()
                .map(pk -> pk.toDto())
                .toArray(PickupDto[]::new);

        // Drain kill feed for this tick
        String[] killFeed;
        synchronized (state.killFeedQueue) {
            killFeed = state.killFeedQueue.toArray(new String[0]);
            state.killFeedQueue.clear();
        }

        return new GameSnapshotDto(
                state.tick,
                players.toArray(new PlayerDto[0]),
                projs,
                pickups,
                killFeed);
    }

    // ---- Helpers ----

    private static WeaponRegistry loadWeapons(String resource) {
        try {
            return WeaponLoader.load(resource);
        } catch (Exception e) {
            System.err.println("[MATCH] Weapon load failed (" + e.getMessage() + "), using defaults");
            return new WeaponRegistry();
        }
    }
}
