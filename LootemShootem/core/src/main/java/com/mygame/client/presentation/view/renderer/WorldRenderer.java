package com.mygame.client.presentation.view.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.mygame.client.domain.model.WorldState;
import com.mygame.shared.dto.*;
import com.mygame.shared.dto.ChestDto;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the game world (tiles, pickups, players, projectiles).
 *
 * Asset convention — drop a correctly-named PNG into the corresponding folder and
 * it will be picked up automatically at startup; no code change required:
 *
 *   assets/tiles/tile_floor.png       (or tile_wall, tile_window, tile_trap)
 *   assets/pickups/pickup_health.png  (or pickup_speed, pickup_weapon)
 *   assets/weapons/weapon_crossbow.png (lowercase WeaponType name, used as weapon-pickup icon)
 *   assets/characters/player_local.png
 *   assets/characters/player_enemy.png
 *
 * Any missing texture falls back to ShapeRenderer geometry so the game always renders.
 */
public final class WorldRenderer {

    private final WorldState         worldState;
    private final OrthographicCamera camera;
    private final ShapeRenderer      shapes;
    private final SpriteBatch        batch;

    // Textures – null means "use shape fallback"
    private final Map<TileType,   Texture> tileTex    = new EnumMap<>(TileType.class);
    private final Map<PickupType, Texture> pickupTex  = new EnumMap<>(PickupType.class);
    private final Map<WeaponType, Texture> weaponTex  = new EnumMap<>(WeaponType.class);
    private Texture playerLocalTex;
    private Texture playerEnemyTex;
    private final Texture[] chestTex = new Texture[2]; // chest_0 / chest_1 for closed variants
    private Texture chestOpenTex;

    public WorldRenderer(WorldState worldState,
                         OrthographicCamera camera,
                         ShapeRenderer shapes,
                         SpriteBatch batch) {
        this.worldState = worldState;
        this.camera     = camera;
        this.shapes     = shapes;
        this.batch      = batch;
        loadAssets();
    }

    // ---- Lifecycle ----

    public void dispose() {
        tileTex.values().forEach(Texture::dispose);
        pickupTex.values().forEach(Texture::dispose);
        weaponTex.values().forEach(Texture::dispose);
        if (playerLocalTex != null) playerLocalTex.dispose();
        if (playerEnemyTex != null) playerEnemyTex.dispose();
        for (Texture t : chestTex) if (t != null) t.dispose();
        if (chestOpenTex   != null) chestOpenTex.dispose();
    }

    // ---- Camera ----

    public void updateCamera() {
        MapDto    map = worldState.getMap();
        PlayerDto me  = worldState.getLocalPlayer();
        if (me == null || me.pos == null || map == null) return;
        float halfH = 9f;
        float camY  = Math.max(halfH, Math.min(map.height - halfH, me.pos.y));
        camera.position.set(map.width / 2f, camY, 0);
        camera.update();
    }

    // ---- Render ----

    public void render() {
        MapDto          map  = worldState.getMap();
        GameSnapshotDto snap = worldState.getSnapshot();

        // ── Shape pass: everything without a texture ──────────────────────────
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        if (map  != null) drawMapShapes(map);
        if (snap != null) {
            drawChestsShapes(snap);
            drawPickupsShapes(snap);
            drawPlayersShapes(snap);
            drawProjectilesShapes(snap);
        }

        shapes.end();

        // ── Sprite pass: textured elements drawn on top ───────────────────────
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        if (map  != null) drawMapSprites(map);
        if (snap != null) {
            drawChestsSprites(snap);
            drawPickupsSprites(snap);
            drawPlayersSprites(snap);
        }

        batch.end();
    }

    // ── Map ──────────────────────────────────────────────────────────────────

    private void drawMapShapes(MapDto map) {
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                TileType t = map.tiles[y * map.width + x];
                if (tileTex.containsKey(t)) continue; // textured – drawn in sprite pass
                setTileColor(t);
                shapes.rect(x, y, 1f, 1f);
            }
        }
    }

    private void drawMapSprites(MapDto map) {
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                TileType t = map.tiles[y * map.width + x];
                Texture tx = tileTex.get(t);
                if (tx != null) batch.draw(tx, x, y, 1f, 1f);
            }
        }
    }

    private void setTileColor(TileType t) {
        switch (t) {
            case WALL:   shapes.setColor(0.20f, 0.20f, 0.22f, 1f); break;
            case WINDOW: shapes.setColor(0.25f, 0.35f, 0.45f, 1f); break;
            case TRAP:   shapes.setColor(0.50f, 0.20f, 0.20f, 1f); break;
            case COBWEB: shapes.setColor(0.55f, 0.55f, 0.50f, 1f); break; // grey-white
            default:     shapes.setColor(0.12f, 0.12f, 0.14f, 1f); break;
        }
    }

    // ── Chests ───────────────────────────────────────────────────────────────

    private void drawChestsShapes(GameSnapshotDto snap) {
        if (snap.chests == null) return;
        for (ChestDto c : snap.chests) {
            if (c == null || c.pos == null) continue;
            if (resolveChestTexture(c) != null) continue; // drawn in sprite pass
            if (c.isOpen) {
                shapes.setColor(0.25f, 0.18f, 0.10f, 1f); // dark open chest
            } else {
                shapes.setColor(0.75f, 0.55f, 0.15f, 1f); // golden closed
            }
            shapes.rect(c.pos.x - 0.5f, c.pos.y - 0.5f, 1f, 1f);
        }
    }

    private void drawChestsSprites(GameSnapshotDto snap) {
        if (snap.chests == null) return;
        for (ChestDto c : snap.chests) {
            if (c == null || c.pos == null) continue;
            Texture tx = resolveChestTexture(c);
            if (tx == null) continue;
            batch.draw(tx, c.pos.x - 0.5f, c.pos.y - 0.5f, 1f, 1f);
        }
    }

    private Texture resolveChestTexture(ChestDto c) {
        if (c.isOpen) return chestOpenTex;
        // Pick variant deterministically from chestId — no protocol change needed
        int v = Math.abs(c.chestId.hashCode()) % 2;
        return chestTex[v];
    }

    // ── Pickups ──────────────────────────────────────────────────────────────

    private void drawPickupsShapes(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto p : snap.pickups) {
            if (p == null || p.pos == null) continue;
            // Resolve best texture: weapon-specific > generic pickup > shape fallback
            if (resolvePickupTexture(p) != null) continue;
            shapes.setColor(0.10f, 0.10f, 0.10f, 1f);
            shapes.circle(p.pos.x, p.pos.y, 0.38f, 16);
            setPickupColor(p.type);
            shapes.circle(p.pos.x, p.pos.y, 0.28f, 16);
        }
    }

    private void drawPickupsSprites(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto p : snap.pickups) {
            if (p == null || p.pos == null) continue;
            Texture tx = resolvePickupTexture(p);
            if (tx != null) batch.draw(tx, p.pos.x - 0.38f, p.pos.y - 0.38f, 0.76f, 0.76f);
        }
    }

    /** Returns weapon-specific sprite, then pickup-type sprite, then null (shape fallback). */
    private Texture resolvePickupTexture(PickupDto p) {
        if (p.type == PickupType.WEAPON && p.weaponType != null) {
            Texture wt = weaponTex.get(p.weaponType);
            if (wt != null) return wt;
        }
        return pickupTex.get(p.type);
    }

    private void setPickupColor(PickupType type) {
        switch (type) {
            case HEALTH: shapes.setColor(0.20f, 0.85f, 0.35f, 1f); break;
            case SPEED:  shapes.setColor(0.20f, 0.80f, 0.95f, 1f); break;
            case WEAPON: shapes.setColor(0.95f, 0.55f, 0.10f, 1f); break;
            default:     shapes.setColor(0.80f, 0.80f, 0.80f, 1f); break;
        }
    }

    // ── Players ──────────────────────────────────────────────────────────────

    private void drawPlayersShapes(GameSnapshotDto snap) {
        String localId = worldState.getLocalPlayerId();
        for (PlayerDto p : snap.players) {
            if (p.pos == null || p.isDead) continue;
            boolean isMe = localId != null && localId.equals(p.playerId);
            Texture tx   = isMe ? playerLocalTex : playerEnemyTex;
            if (tx != null) continue; // drawn in sprite pass
            shapes.setColor(isMe ? new Color(0.20f, 0.85f, 0.20f, 1f)
                                 : new Color(0.85f, 0.20f, 0.20f, 1f));
            shapes.circle(p.pos.x, p.pos.y, 0.50f, 16);
            if (p.facing != null) {
                shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
                shapes.rectLine(p.pos.x, p.pos.y,
                        p.pos.x + p.facing.x * 0.6f,
                        p.pos.y + p.facing.y * 0.6f, 0.06f);
            }
        }
    }

    private void drawPlayersSprites(GameSnapshotDto snap) {
        String localId = worldState.getLocalPlayerId();
        for (PlayerDto p : snap.players) {
            if (p.pos == null || p.isDead) continue;
            boolean isMe = localId != null && localId.equals(p.playerId);

            // Player body — drawn at full tile size (1×1)
            Texture bodyTex = isMe ? playerLocalTex : playerEnemyTex;
            if (bodyTex != null) {
                batch.draw(bodyTex, p.pos.x - 0.5f, p.pos.y - 0.5f, 1f, 1f);
            }

            // Equipped weapon drawn on top, rotated toward aim direction
            drawWeaponOverlay(p);
        }
    }

    /**
     * Draws the equipped weapon sprite at the player's position, rotated toward
     * {@code p.facing}. When the weapon would end up pointing left (|angle| > 90°)
     * it is flipped horizontally so it never appears upside-down.
     *
     * All weapon PNGs are assumed to point straight RIGHT in their natural orientation.
     */
    private void drawWeaponOverlay(PlayerDto p) {
        if (p.facing == null || p.equippedWeaponType == null) return;
        Texture wt = weaponTex.get(p.equippedWeaponType);
        if (wt == null) return;

        float angle  = (float) Math.toDegrees(Math.atan2(p.facing.y, p.facing.x));
        boolean flip = Math.abs(angle) > 90f;
        // When flipped the gun points left at 0°; mirror the angle around 180° so it still
        // lands on the correct side.
        float drawAngle = flip ? (180f - angle) : angle;

        float weapW = 1.2936f;
        float weapH = 0.4158f;
        // Pivot at the grip end; swap to the other edge when flipped so the grip
        // stays at the player's centre regardless of direction.
        float origX = flip ? weapW : 0f;
        float origY = weapH / 2f;

        batch.draw(wt,
                p.pos.x - origX,   // bottom-left x so that origX lands on player centre
                p.pos.y - origY,   // bottom-left y so that origY is at vertical centre
                origX, origY,
                weapW, weapH,
                1f, 1f,
                drawAngle,
                0, 0, wt.getWidth(), wt.getHeight(),
                flip, false);
    }

    // ── Projectiles ──────────────────────────────────────────────────────────

    /** Projectiles always use ShapeRenderer (fast, no texture needed). */
    private void drawProjectilesShapes(GameSnapshotDto snap) {
        if (snap.projectiles == null) return;
        shapes.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (ProjectileDto pr : snap.projectiles) {
            if (pr == null || pr.pos == null) continue;
            float r = pr.radius > 0 ? pr.radius : 0.10f;
            shapes.circle(pr.pos.x, pr.pos.y, r, 12);
        }
    }

    // ── Asset loading ─────────────────────────────────────────────────────────

    private void loadAssets() {
        for (TileType t : TileType.values()) {
            Texture tx = tryLoad("tiles/tile_" + t.name().toLowerCase() + ".png");
            if (tx != null) tileTex.put(t, tx);
        }
        for (PickupType p : PickupType.values()) {
            Texture tx = tryLoad("pickups/pickup_" + p.name().toLowerCase() + ".png");
            if (tx != null) pickupTex.put(p, tx);
        }
        for (WeaponType w : WeaponType.values()) {
            Texture tx = tryLoad("weapons/weapon_" + w.name().toLowerCase() + ".png");
            if (tx != null) weaponTex.put(w, tx);
        }
        playerLocalTex = tryLoad("characters/player_local.png");
        playerEnemyTex = tryLoad("characters/player_enemy.png");
        chestTex[0]    = tryLoad("chests/chest_0.png");
        chestTex[1]    = tryLoad("chests/chest_1.png");
        chestOpenTex   = tryLoad("chests/chest_open.png");
    }

    /**
     * Tries to load a texture from {@code assets/<path>}.
     * Returns {@code null} silently if the file does not exist so callers use the shape fallback.
     */
    private static Texture tryLoad(String path) {
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal(path);
            if (fh.exists()) {
                Texture t = new Texture(fh);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                return t;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
