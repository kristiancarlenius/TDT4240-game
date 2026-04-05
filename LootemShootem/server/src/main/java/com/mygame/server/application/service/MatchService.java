package com.mygame.server.application.service;

import com.mygame.server.data.map.MapProvider;
import com.mygame.server.data.weapon.WeaponLoader;
import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.server.domain.model.WeaponSpec;
import com.mygame.server.domain.ports.MapProviderPort;
import com.mygame.server.domain.system.*;
import com.mygame.shared.dto.*;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinAccepted;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchService {

    private final ServerGameState       state;
    private final WeaponRegistry        weaponRegistry;
    private final ServerWorldStepSystem worldStepSystem;
    private final Random                rng = new Random();

    private final Map<String, InputMessage> latestInput = new ConcurrentHashMap<>();

    public MatchService() {
        this(new MapProvider(), "map01", 6);
    }

    public MatchService(MapProviderPort mapProvider, String mapId) {
        this(mapProvider, mapId, 6);
    }

    public MatchService(MapProviderPort mapProvider, String mapId, int chestCount) {
        this.state          = mapProvider.provide(mapId);
        this.weaponRegistry = loadWeapons("weapons/weapons.json");

        PlayerSystem      player     = new PlayerSystem(state);
        CollisionSystem   collision  = new CollisionSystem(state);
        ProjectileSystem  projectile = new ProjectileSystem(state, player);
        PickupSpawnSystem pickup     = new PickupSpawnSystem(state, weaponRegistry);
        ChestSystem       chests     = new ChestSystem(state, weaponRegistry, rng, chestCount);

        this.worldStepSystem = new ServerWorldStepSystem(
                state, weaponRegistry, collision, projectile, pickup, player, chests);
    }

    // ── Player management ────────────────────────────────────────────────────

    public String addPlayer(String username) {
        String id    = UUID.randomUUID().toString();
        Vec2   spawn = state.findSafeSpawn();
        PlayerState ps    = new PlayerState(id, username, spawn);
        WeaponSpec  cross = weaponRegistry.get(WeaponType.CROSSBOW);
        ps.ammoBySlot[0]  = cross.maxAmmo;
        ps.magsBySlot[0]  = cross.numMagazines;
        ps.syncEquipped();
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

    public int getPlayerCount() { return state.players.size(); }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public void tick(float dt) {
        worldStepSystem.tick(dt, latestInput);
    }

    // ── Snapshot ─────────────────────────────────────────────────────────────

    public JoinAccepted buildJoinAccepted(String playerId) {
        return new JoinAccepted(playerId, state.toMapDto(), buildSnapshot());
    }

    public GameSnapshotDto buildSnapshot() {
        PlayerDto[] players = state.players.values().stream()
                .map(PlayerState::toDto)
                .toArray(PlayerDto[]::new);

        ProjectileDto[] projs = state.projectiles.stream()
                .map(pr -> pr.toDto())
                .toArray(ProjectileDto[]::new);

        PickupDto[] pickups = state.pickups.stream()
                .map(pk -> pk.toDto())
                .toArray(PickupDto[]::new);

        ChestDto[] chests = state.chests.stream()
                .map(c -> c.toDto())
                .toArray(ChestDto[]::new);

        String[] killFeed;
        synchronized (state.killFeedQueue) {
            killFeed = state.killFeedQueue.toArray(new String[0]);
            state.killFeedQueue.clear();
        }

        return new GameSnapshotDto(state.tick, players, projs, pickups, chests, killFeed);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WeaponRegistry loadWeapons(String resource) {
        try {
            return WeaponLoader.load(resource);
        } catch (Exception e) {
            System.err.println("[MATCH] Weapon load failed (" + e.getMessage() + "), using defaults");
            return new WeaponRegistry();
        }
    }
}
