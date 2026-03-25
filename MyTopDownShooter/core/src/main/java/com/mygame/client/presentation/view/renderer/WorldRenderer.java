package com.mygame.client.presentation.view.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.mygame.client.domain.model.WorldState;
import com.mygame.shared.dto.*;

/**
 * Renders the game world (tiles, players, projectiles, pickups) in world-space
 * coordinates. Reads from WorldState; never mutates it.
 */
public final class WorldRenderer {

    private final WorldState        worldState;
    private final OrthographicCamera camera;
    private final ShapeRenderer     shapes;

    public WorldRenderer(WorldState worldState,
                         OrthographicCamera camera,
                         ShapeRenderer shapes) {
        this.worldState = worldState;
        this.camera     = camera;
        this.shapes     = shapes;
    }

    /** Moves the camera to track the local player, clamped to map bounds. */
    public void updateCamera() {
        MapDto    map = worldState.getMap();
        PlayerDto me  = worldState.getLocalPlayer();
        if (me == null || me.pos == null || map == null) return;
        float halfH = 9f;
        float camY  = Math.max(halfH, Math.min(map.height - halfH, me.pos.y));
        camera.position.set(map.width / 2f, camY, 0);
        camera.update();
    }

    /**
     * Draw all world-space geometry.
     * Must NOT be called while a ShapeRenderer batch is already open.
     */
    public void render() {
        MapDto          map  = worldState.getMap();
        GameSnapshotDto snap = worldState.getSnapshot();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        if (map  != null) drawMap(map);
        if (snap != null) {
            drawPickups(snap);
            drawPlayers(snap);
            drawProjectiles(snap);
        }

        shapes.end();
    }

    // ---- Private helpers ----

    private void drawMap(MapDto map) {
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                setTileColor(map.tiles[y * map.width + x]);
                shapes.rect(x, y, 1f, 1f);
            }
        }
    }

    private void setTileColor(TileType t) {
        switch (t) {
            case WALL:   shapes.setColor(0.20f, 0.20f, 0.22f, 1f); break;
            case WINDOW: shapes.setColor(0.25f, 0.35f, 0.45f, 1f); break;
            case TRAP:   shapes.setColor(0.50f, 0.20f, 0.20f, 1f); break;
            default:     shapes.setColor(0.12f, 0.12f, 0.14f, 1f); break;
        }
    }

    private void drawPickups(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto p : snap.pickups) {
            if (p == null || p.pos == null) continue;
            shapes.setColor(0.10f, 0.10f, 0.10f, 1f);
            shapes.circle(p.pos.x, p.pos.y, 0.38f, 16);
            switch (p.type) {
                case HEALTH: shapes.setColor(0.20f, 0.85f, 0.35f, 1f); break;
                case SPEED:  shapes.setColor(0.20f, 0.80f, 0.95f, 1f); break;
                case WEAPON: shapes.setColor(0.95f, 0.55f, 0.10f, 1f); break;
                default:     shapes.setColor(0.80f, 0.80f, 0.80f, 1f); break;
            }
            shapes.circle(p.pos.x, p.pos.y, 0.28f, 16);
        }
    }

    private void drawPlayers(GameSnapshotDto snap) {
        String localId = worldState.getLocalPlayerId();
        for (PlayerDto p : snap.players) {
            if (p.pos == null || p.isDead) continue;
            boolean isMe = localId != null && localId.equals(p.playerId);
            shapes.setColor(isMe ? new Color(0.20f, 0.85f, 0.20f, 1f)
                                 : new Color(0.85f, 0.20f, 0.20f, 1f));
            shapes.circle(p.pos.x, p.pos.y, 0.30f, 16);
            if (p.facing != null) {
                shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
                shapes.rectLine(p.pos.x, p.pos.y,
                        p.pos.x + p.facing.x * 0.6f,
                        p.pos.y + p.facing.y * 0.6f, 0.06f);
            }
        }
    }

    private void drawProjectiles(GameSnapshotDto snap) {
        if (snap.projectiles == null) return;
        shapes.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (ProjectileDto pr : snap.projectiles) {
            if (pr == null || pr.pos == null) continue;
            float r = pr.radius > 0 ? pr.radius : 0.10f;
            shapes.circle(pr.pos.x, pr.pos.y, r, 12);
        }
    }
}
