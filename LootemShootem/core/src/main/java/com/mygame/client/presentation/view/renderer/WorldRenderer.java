package com.mygame.client.presentation.view.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.mygame.client.domain.model.WorldState;
import com.mygame.shared.dto.*;
import com.mygame.shared.dto.ChestDto;

import java.util.EnumMap;
import java.util.HashMap;
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
 *   assets/characters/skin_0.png … skin_3.png  (256×256, 4 rows × 4 cols of 64×64)
 *   assets/characters/player_local.png  (legacy fallback)
 *   assets/characters/player_enemy.png  (legacy fallback)
 *
 * Any missing texture falls back to ShapeRenderer geometry so the game always renders.
 *
 * ── Player animation ──────────────────────────────────────────────────────────
 * Each skin_N.png is a 256×256 grid: 4 rows (directions) × 4 columns (frames).
 *   Row 0 — DOWN  (moveDir 0)
 *   Row 1 — LEFT  (moveDir 1)
 *   Row 2 — UP    (moveDir 2)
 *   Row 3 — RIGHT (moveDir 3)
 *   Column 0 — idle; columns 1–3 — walk cycle
 *
 * The body sprite does NOT rotate — it switches row based on WASD/joystick direction.
 * The weapon sprite still rotates freely toward the mouse/aim direction.
 */
public final class WorldRenderer {

    // ── Walk-animation constants ──────────────────────────────────────────────
    private static final int   SKIN_COUNT          = 4;
    private static final int   DIR_COUNT           = 4;    // DOWN/LEFT/UP/RIGHT
    private static final int   WALK_FRAME_COUNT    = 4;    // 0=idle, 1-3=walk
    private static final float WALK_FRAME_DURATION = 0.13f;
    private static final float WALK_CYCLE_DURATION = WALK_FRAME_DURATION * 3;
    private static final float WALK_THRESHOLD      = 0.05f;
    /** World-units size (width = height) of the rendered player sprite. */
    private static final float CHAR_SIZE           = 1.72f;

    private final WorldState         worldState;
    private final OrthographicCamera camera;
    private final ShapeRenderer      shapes;
    private final SpriteBatch        batch;

    // Textures – null means "use shape fallback"
    private final Map<TileType,   Texture> tileTex    = new EnumMap<>(TileType.class);
    private final Map<PickupType, Texture> pickupTex  = new EnumMap<>(PickupType.class);
    private final Map<WeaponType, Texture> weaponTex  = new EnumMap<>(WeaponType.class);

    // ── Player skin / animation ───────────────────────────────────────────────
    /** Raw spritesheet textures; one per skin index. */
    private final Texture[]           skinTex    = new Texture[SKIN_COUNT];
    /** skinFrames[skinId][dir][frame] — dir: 0=DOWN 1=LEFT 2=UP 3=RIGHT; frame: 0=idle 1-3=walk. */
    private final TextureRegion[][][] skinFrames = new TextureRegion[SKIN_COUNT][DIR_COUNT][WALK_FRAME_COUNT];
    /** Per-player accumulated walk-cycle timer. */
    private final Map<String, Float>   playerAnimTimers = new HashMap<>();
    /** Last non-idle moveDir per player, so idle pose keeps the last facing direction. */
    private final Map<String, Integer> lastMoveDirs     = new HashMap<>();

    // Legacy single-sprite fallback (used if no skin sheets found)
    private Texture playerLocalTex;
    private Texture playerEnemyTex;
    private Texture overheadTex;       // single top-down sprite, faces up in PNG

    // Chest textures
    private final Texture[] chestTex = new Texture[2];
    private final Texture[][] chestOpenFrames = new Texture[2][3];
    private final Map<String, Float> chestOpenTimers = new HashMap<>();

    // Projectile textures
    private Texture projBulletTex;
    private Texture projArrowTex;
    private Texture projFlameTex;

    private Sound hurtSound;
    private boolean wasLocalPlayerHurt = false;

    /** Accumulated time used for periodic animations. */
    private float time = 0f;
    /** Per-player heal flash timer tracked locally. */
    private final Map<String, Float> healTimers = new HashMap<>();

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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void dispose() {
        tileTex.values().forEach(Texture::dispose);
        pickupTex.values().forEach(Texture::dispose);
        weaponTex.values().forEach(Texture::dispose);
        for (Texture t : skinTex) if (t != null) t.dispose();
        if (playerLocalTex != null) playerLocalTex.dispose();
        if (playerEnemyTex != null) playerEnemyTex.dispose();
        if (overheadTex    != null) overheadTex.dispose();
        for (Texture t : chestTex) if (t != null) t.dispose();
        for (Texture[] frames : chestOpenFrames)
            for (Texture t : frames) if (t != null) t.dispose();
        if (projBulletTex  != null) projBulletTex.dispose();
        if (projArrowTex   != null) projArrowTex.dispose();
        if (projFlameTex   != null) projFlameTex.dispose();
        if (hurtSound      != null) hurtSound.dispose();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    public void updateCamera() {
        MapDto    map = worldState.getMap();
        PlayerDto me  = worldState.getLocalPlayer();
        if (me == null || me.pos == null || map == null) return;
        float halfW = camera.viewportWidth * 0.5f;
        float halfH = camera.viewportHeight * 0.5f;

        float camX = (map.width <= camera.viewportWidth)
            ? map.width * 0.5f
            : Math.max(halfW, Math.min(map.width - halfW, me.pos.x));
        float camY = (map.height <= camera.viewportHeight)
            ? map.height * 0.5f
            : Math.max(halfH, Math.min(map.height - halfH, me.pos.y));

        camera.position.set(camX, camY, 0);
        camera.update();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void render() {
        time += Gdx.graphics.getDeltaTime();
        MapDto          map  = worldState.getMap();
        GameSnapshotDto snap = worldState.getSnapshot();

        // ── Shape pass ───────────────────────────────────────────────────────
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

        // ── Sprite pass ──────────────────────────────────────────────────────
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        if (map  != null) drawMapSprites(map);
        if (snap != null) {
            drawChestsSprites(snap);
            drawPickupsSprites(snap);
            drawPlayersSprites(snap);
            drawProjectilesSprites(snap);
        }

        batch.end();

        updateAudio();
    }

    private void updateAudio() {
        PlayerDto me = worldState.getLocalPlayer();
        if (me != null && hurtSound != null) {
            if (me.isHurt && !wasLocalPlayerHurt) {
                hurtSound.play();
            }
            wasLocalPlayerHurt = me.isHurt;
        }
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private void drawMapShapes(MapDto map) {
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                TileType t = map.tiles[y * map.width + x];
                if (tileTex.containsKey(t)) continue;
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
            case COBWEB: shapes.setColor(0.55f, 0.55f, 0.50f, 1f); break;
            default:     shapes.setColor(0.12f, 0.12f, 0.14f, 1f); break;
        }
    }

    // ── Chests ────────────────────────────────────────────────────────────────

    private void drawChestsShapes(GameSnapshotDto snap) {
        if (snap.chests == null) return;
        float dt = Gdx.graphics.getDeltaTime();
        for (ChestDto c : snap.chests) {
            if (c == null || c.pos == null) continue;
            if (c.isOpen) {
                chestOpenTimers.merge(c.chestId, dt, Float::sum);
            } else {
                chestOpenTimers.remove(c.chestId);
            }
            if (resolveChestTexture(c) != null) continue;
            shapes.setColor(c.isOpen ? new Color(0.25f, 0.18f, 0.10f, 1f)
                                     : new Color(0.75f, 0.55f, 0.15f, 1f));
            shapes.rect(c.pos.x - 0.55f, c.pos.y - 0.55f, 1.1f, 1.1f);
        }
    }

    private void drawChestsSprites(GameSnapshotDto snap) {
        if (snap.chests == null) return;
        for (ChestDto c : snap.chests) {
            if (c == null || c.pos == null) continue;
            Texture tx = resolveChestTexture(c);
            if (tx == null) continue;
            batch.draw(tx, c.pos.x - 0.55f, c.pos.y - 0.55f, 1.1f, 1.1f);
        }
    }

    private Texture resolveChestTexture(ChestDto c) {
        int v = Math.abs(c.chestId.hashCode()) % 2;
        if (c.isOpen) {
            float elapsed = chestOpenTimers.getOrDefault(c.chestId, 0f);
            int frame = elapsed < 0.2f ? 0 : elapsed < 0.4f ? 1 : 2;
            return chestOpenFrames[v][frame];
        }
        return chestTex[v];
    }

    // ── Pickups ───────────────────────────────────────────────────────────────

    private void drawPickupsShapes(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto p : snap.pickups) {
            if (p == null || p.pos == null) continue;
            if (resolvePickupTexture(p) != null) continue;
            float bobOffset = (float) Math.sin(time * 3.0f + p.pos.x + p.pos.y) * 0.12f;
            shapes.setColor(0.10f, 0.10f, 0.10f, 1f);
            shapes.circle(p.pos.x, p.pos.y + bobOffset, 0.38f, 16);
            setPickupColor(p.type);
            shapes.circle(p.pos.x, p.pos.y + bobOffset, 0.28f, 16);
        }
    }

    private void drawPickupsSprites(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto p : snap.pickups) {
            if (p == null || p.pos == null) continue;
            Texture tx = resolvePickupTexture(p);
            if (tx == null) continue;

            float bobSpeed  = (p.type == PickupType.HEALTH) ? 4.0f : 3.0f;
            float bobRange  = (p.type == PickupType.HEALTH) ? 0.18f : 0.12f;
            float bobOffset = (float) Math.sin(time * bobSpeed + p.pos.x + p.pos.y) * bobRange;

            if (p.type == PickupType.WEAPON && tx == weaponTex.get(p.weaponType)) {
                float angle = (time * 45f) % 360f;
                float h = 0.5f;
                float w = (tx.getHeight() > 0) ? h * tx.getWidth() / (float) tx.getHeight() : h;
                float cx = p.pos.x;
                float cy = p.pos.y + bobOffset;
                batch.draw(tx,
                    cx - w * 0.5f, cy - h * 0.5f,
                    w * 0.5f, h * 0.5f,
                    w, h,
                    1f, 1f,
                    angle,
                    0, 0, tx.getWidth(), tx.getHeight(), false, false);
            } else {
                batch.draw(tx, p.pos.x - 0.38f, p.pos.y + bobOffset - 0.38f, 0.76f, 0.76f);
            }
        }
    }

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

    // ── Players ───────────────────────────────────────────────────────────────

    /** Returns true when at least the first skin sheet loaded successfully. */
    private boolean hasSkinFrames() {
        return skinFrames[0][0][0] != null;
    }

    private void drawPlayersShapes(GameSnapshotDto snap) {
        String localId = worldState.getLocalPlayerId();
        for (PlayerDto p : snap.players) {
            if (p.pos == null || p.isDead) continue;
            // Skip shape rendering if any textured sprite will handle this player
            if (hasSkinFrames() || overheadTex != null) continue;
            boolean isMe = localId != null && localId.equals(p.playerId);
            Texture tx = isMe ? playerLocalTex : playerEnemyTex;
            if (tx != null) continue;

            if (p.isHurt) {
                shapes.setColor(1.0f, 0.3f, 0.3f, 1f);
            } else if (p.isHealed) {
                boolean flashOn = ((int)(time * 16f) % 2) == 0;
                shapes.setColor(flashOn ? new Color(1f, 1f, 0f, 1f) : new Color(0f, 1f, 1f, 1f));
            } else {
                shapes.setColor(isMe ? new Color(0.20f, 0.85f, 0.20f, 1f)
                        : new Color(0.85f, 0.20f, 0.20f, 1f));
            }

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
        float dt = Gdx.graphics.getDeltaTime();
        String localId = worldState.getLocalPlayerId();

        for (PlayerDto p : snap.players) {
            if (p.pos == null || p.isDead) continue;

            // ── Heal flash timer ─────────────────────────────────────────────
            if (p.lastPickupNotice != null && p.lastPickupNotice.contains("HP")) {
                healTimers.put(p.playerId, 0.6f);
            }
            float healLeft = Math.max(0f, healTimers.getOrDefault(p.playerId, 0f) - dt);
            healTimers.put(p.playerId, healLeft);

            // ── Tint color (hurt / healed / normal) ──────────────────────────
            if (p.isHurt) {
                batch.setColor(1f, 0.3f, 0.3f, 1f);
            } else if (healLeft > 0f) {
                boolean flashOn = ((int)(time * 16f) % 2) == 0;
                batch.setColor(flashOn ? new Color(1f, 1f, 0f, 1f) : new Color(0f, 1f, 1f, 1f));
            } else {
                batch.setColor(Color.WHITE);
            }

            // ── Select rendering path ────────────────────────────────────────
            if (hasSkinFrames()) {
                drawAnimatedSkin(p, dt);
            } else if (overheadTex != null) {
                drawLegacyOverhead(p);
            } else {
                boolean isMe = localId != null && localId.equals(p.playerId);
                Texture bodyTex = isMe ? playerLocalTex : playerEnemyTex;
                if (bodyTex != null)
                    batch.draw(bodyTex, p.pos.x - 0.5f, p.pos.y - 0.5f, 1f, 1f);
            }

            drawWeaponOverlay(p);
            batch.setColor(Color.WHITE);
        }
    }

    /**
     * Draws the animated skin sprite for a player.
     * Body direction is driven by WASD/joystick (4-way, no free rotation).
     * Walk cycle advances when the player is moving.
     */
    private void drawAnimatedSkin(PlayerDto p, float dt) {
        int skinId = (p.skinId >= 0 && p.skinId < SKIN_COUNT) ? p.skinId : 0;

        // ── Resolve direction row ─────────────────────────────────────────────
        // moveDir: 0=DOWN 1=LEFT 2=UP 3=RIGHT; -1=idle (use last known)
        int dir;
        if (p.moveDir >= 0 && p.moveDir < DIR_COUNT) {
            dir = p.moveDir;
            lastMoveDirs.put(p.playerId, dir);
        } else {
            // Idle — keep last known direction so pose doesn't snap to default
            dir = lastMoveDirs.getOrDefault(p.playerId, 2); // default UP
        }

        TextureRegion[] dirFrames = skinFrames[skinId][dir];
        if (dirFrames[0] == null) return;

        // ── Walk cycle ────────────────────────────────────────────────────────
        float velMag = (p.vel != null)
                ? (float) Math.sqrt(p.vel.x * p.vel.x + p.vel.y * p.vel.y)
                : 0f;
        boolean walking = velMag > WALK_THRESHOLD;

        float timer = playerAnimTimers.getOrDefault(p.playerId, 0f);
        if (walking) {
            timer += dt;
            if (timer >= WALK_CYCLE_DURATION) timer -= WALK_CYCLE_DURATION;
        } else {
            timer = 0f;
        }
        playerAnimTimers.put(p.playerId, timer);

        int frame = walking ? 1 + (int)(timer / WALK_FRAME_DURATION) % 3 : 0;
        TextureRegion region = dirFrames[frame];

        // No rotation — the row already encodes the direction
        batch.draw(region,
                p.pos.x - CHAR_SIZE * 0.5f, p.pos.y - CHAR_SIZE * 0.5f,
                CHAR_SIZE, CHAR_SIZE);
    }

    /** Legacy renderer for the single overhead_top.png sprite (no animation). */
    private void drawLegacyOverhead(PlayerDto p) {
        float angle = (p.facing != null)
                ? (float) Math.toDegrees(Math.atan2(p.facing.y, p.facing.x)) - 90f
                : -90f;
        int crop  = 20;
        int srcX  = crop;
        int srcY  = crop;
        int srcW  = overheadTex.getWidth()  - crop * 2;
        int srcH  = overheadTex.getHeight() - crop * 2;
        float charH = 3.6f;
        float charW = charH * srcW / (float) srcH;
        batch.draw(overheadTex,
                p.pos.x - charW * 0.5f, p.pos.y - charH * 0.5f,
                charW * 0.5f, charH * 0.5f,
                charW, charH,
                1f, 1f,
                angle,
                srcX, srcY, srcW, srcH,
                false, false);
    }

    /** Per-weapon right-hand offset (perpendicular distance from player centre). */
    private static float weaponRightDist(WeaponType t) {
        if (t == null) return 0.45f;
        switch (t) {
            case CROSSBOW:     return 0.28f;
            case PISTOL:       return 0.50f;
            case UZI:          return 0.50f;
            case AK:           return 0.50f;
            case MACHINEGUN:   return 0.45f;
            case SHOTGUN:      return 0.45f;
            case SNIPER:       return 0.45f;
            case FLAMETHROWER: return 0.45f;
            default:           return 0.45f;
        }
    }

    /**
     * Draws the equipped weapon sprite at the player's position, rotated toward
     * {@code p.facing}.
     * All weapon PNGs are assumed to point straight RIGHT in their natural orientation.
     */
    private void drawWeaponOverlay(PlayerDto p) {
        if (p.facing == null || p.equippedWeaponType == null) return;
        Texture wt = weaponTex.get(p.equippedWeaponType);
        if (wt == null) return;

        float angle = (float) Math.toDegrees(Math.atan2(p.facing.y, p.facing.x));

        float rightDist = weaponRightDist(p.equippedWeaponType);
        float backDist  = 0.20f;
        float handX = p.pos.x + ( p.facing.y) * rightDist - p.facing.x * backDist;
        float handY = p.pos.y + (-p.facing.x) * rightDist - p.facing.y * backDist;

        float weapW = 2.3285f;
        float weapH = (wt.getWidth() > 0)
                ? weapW * wt.getHeight() / (float) wt.getWidth()
                : 0.7484f;
        float origX = 0f;
        float origY = weapH / 2f;

        batch.draw(wt,
                handX,
                handY - origY,
                origX, origY,
                weapW, weapH,
                1f, 1f,
                angle,
                0, 0, wt.getWidth(), wt.getHeight(),
                false, false);
    }

    // ── Projectiles ───────────────────────────────────────────────────────────

    private void drawProjectilesShapes(GameSnapshotDto snap) {
        if (snap.projectiles == null) return;
        shapes.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (ProjectileDto pr : snap.projectiles) {
            if (pr == null || pr.pos == null) continue;
            if (resolveProjectileTexture(pr, snap) != null) continue;
            float r = pr.radius > 0 ? pr.radius : 0.10f;
            shapes.circle(pr.pos.x, pr.pos.y, r, 12);
        }
    }

    private void drawProjectilesSprites(GameSnapshotDto snap) {
        if (snap.projectiles == null) return;
        for (ProjectileDto pr : snap.projectiles) {
            if (pr == null || pr.pos == null || pr.vel == null) continue;
            Texture tx = resolveProjectileTexture(pr, snap);
            if (tx == null) continue;

            float angle = (float) Math.toDegrees(Math.atan2(pr.vel.y, pr.vel.x)) - 90f;

            boolean isArrow = (tx == projArrowTex);
            float w = isArrow ? 0.20f : 0.28f;
            float h = isArrow ? 0.55f : 0.28f;
            float ox = w / 2f;
            float oy = h / 2f;

            batch.draw(tx,
                    pr.pos.x - ox, pr.pos.y - oy,
                    ox, oy, w, h,
                    1f, 1f, angle,
                    0, 0, tx.getWidth(), tx.getHeight(),
                    false, false);
        }
    }

    private Texture resolveProjectileTexture(ProjectileDto pr, GameSnapshotDto snap) {
        WeaponType wt = null;
        if (snap.players != null && pr.ownerPlayerId != null) {
            for (PlayerDto p : snap.players) {
                if (pr.ownerPlayerId.equals(p.playerId)) {
                    wt = p.equippedWeaponType;
                    break;
                }
            }
        }
        if (wt == WeaponType.CROSSBOW)     return projArrowTex;
        if (wt == WeaponType.FLAMETHROWER) return projFlameTex;
        return projBulletTex;
    }

    // ── Asset loading ─────────────────────────────────────────────────────────

    private void loadAssets() {
        // Tile textures (pixel art — Nearest filter)
        for (TileType t : TileType.values()) {
            Texture tx = tryLoad("tiles/tile_" + t.name().toLowerCase() + ".png");
            if (tx != null) {
                tx.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                tileTex.put(t, tx);
            }
        }

        // Pickup icons
        for (PickupType p : PickupType.values()) {
            Texture tx = tryLoad("pickups/pickup_" + p.name().toLowerCase() + ".png");
            if (tx != null) pickupTex.put(p, tx);
        }

        // Weapon icons
        for (WeaponType w : WeaponType.values()) {
            Texture tx = tryLoad("weapons/weapon_" + w.name().toLowerCase() + ".png");
            if (tx != null) weaponTex.put(w, tx);
        }

        // ── Player skins (256×256: 4 dir rows × 4 frame cols of 64×64) ──────
        for (int i = 0; i < SKIN_COUNT; i++) {
            Texture tx = tryLoad("characters/skin_" + i + ".png");
            if (tx != null) {
                tx.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                skinTex[i] = tx;
                for (int d = 0; d < DIR_COUNT; d++) {
                    for (int f = 0; f < WALK_FRAME_COUNT; f++) {
                        // srcX = column * 64, srcY = row * 64 (LibGDX Y=0 is top of texture)
                        skinFrames[i][d][f] = new TextureRegion(tx, f * 64, d * 64, 64, 64);
                    }
                }
            }
        }

        // Legacy single-sprite fallbacks (used only if skin sheets are absent)
        playerLocalTex = tryLoad("characters/player_local.png");
        playerEnemyTex = tryLoad("characters/player_enemy.png");
        overheadTex    = tryLoad("characters/overhead_top.png");

        // Chests
        chestTex[0]    = tryLoad("chests/chest_0.png");
        chestTex[1]    = tryLoad("chests/chest_1.png");
        for (int v = 0; v < 2; v++) {
            chestOpenFrames[v][0] = tryLoad("chests/chest_" + v + "_open_1.png");
            chestOpenFrames[v][1] = tryLoad("chests/chest_" + v + "_open_2.png");
            chestOpenFrames[v][2] = tryLoad("chests/chest_" + v + "_open.png");
        }

        // Projectiles
        projBulletTex  = tryLoad("projectiles/bullet.png");
        projArrowTex   = tryLoad("projectiles/arrow.png");
        projFlameTex   = tryLoad("projectiles/flame.png");

        // Sound
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal("sound\\damage_effect.mp3");
            if (fh.exists()) {
                hurtSound = Gdx.audio.newSound(fh);
            }
        } catch (Exception ignored) {}
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
