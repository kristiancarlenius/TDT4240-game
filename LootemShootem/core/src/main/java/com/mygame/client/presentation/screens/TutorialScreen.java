package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.mygame.client.presentation.navigation.Navigator;

public final class TutorialScreen implements Screen {

    private static final int BTN_W = 180;
    private static final int BTN_H = 44;
    private static final int PAGE_COUNT = 3;

    private static final TutorialWeapon[] WEAPONS = {
            new TutorialWeapon("Pistol", "Balanced starter weapon with solid accuracy.", "weapons/weapon_pistol.png"),
            new TutorialWeapon("Uzi", "Fast fire rate, but burns through ammo quickly.", "weapons/weapon_uzi.png"),
            new TutorialWeapon("Shotgun", "Huge close-range damage, weak at long range.", "weapons/weapon_shotgun.png"),
            new TutorialWeapon("AK", "Reliable mid-range rifle with steady damage.", "weapons/weapon_ak.png"),
            new TutorialWeapon("Sniper", "High precision and damage if you control your shots.", "weapons/weapon_sniper.png"),
            new TutorialWeapon("Crossbow", "Slow but accurate. Great for careful shots.", "weapons/weapon_crossbow.png"),
            new TutorialWeapon("Flamethrower", "Short range area pressure that is strong in tight spaces.", "weapons/weapon_flamethrower.png"),
            new TutorialWeapon("Machinegun", "Sustained fire weapon for heavy pressure.", "weapons/weapon_machinegun.png")
    };

    private static final TutorialTile[] TILES = {
            new TutorialTile("Wall", "Blocks both movement and bullets.", "tiles/tile_wall.png"),
            new TutorialTile("Window", "Blocks movement, but bullets can pass through.", "tiles/tile_window.png"),
            new TutorialTile("Trap", "Hazard tile. Avoid standing on it.", "tiles/tile_trap.png"),
            new TutorialTile("Cobweb", "Slows movement when you cross it.", "tiles/tile_cobweb.png"),
            new TutorialTile("Chest", "Open it for loot, healing, speed boosts, or weapons.", "chests/chest_0.png")
    };

    private final Navigator navigator;
    private final String serverUrl;
    private final String username;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont titleFont;
    private BitmapFont font;
    private GlyphLayout layout;
    private Matrix4 proj;

    private int currentPage = 0;

    private Texture pistolTex;
    private Texture uziTex;
    private Texture shotgunTex;
    private Texture akTex;
    private Texture sniperTex;
    private Texture crossbowTex;
    private Texture flamethrowerTex;
    private Texture machinegunTex;
    private Texture wallTileTex;
    private Texture windowTileTex;
    private Texture trapTileTex;
    private Texture cobwebTileTex;
    private Texture chestTex;
    private Texture pickupHealTex;
    private Texture pickupSpeedTex;
    private Texture pickupWeaponTex;

    public TutorialScreen(Navigator navigator, String serverUrl, String username) {
        this.navigator = navigator;
        this.serverUrl = serverUrl;
        this.username = username;
    }

    @Override
    public void show() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.2f * uiScale());
        font = new BitmapFont();
        font.getData().setScale(1.15f * uiScale());
        layout = new GlyphLayout();
        proj = new Matrix4();
        rebuildProj();
        loadTextures();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Keys.ESCAPE || keycode == Keys.BACK) {
                    navigator.showMainMenu(serverUrl, username);
                    return true;
                }
                if (keycode == Keys.RIGHT || keycode == Keys.D) {
                    currentPage = Math.min(PAGE_COUNT - 1, currentPage + 1);
                    return true;
                }
                if (keycode == Keys.LEFT || keycode == Keys.A) {
                    currentPage = Math.max(0, currentPage - 1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                int sw = Gdx.graphics.getWidth();
                int sh = Gdx.graphics.getHeight();
                int worldY = sh - sy;

                if (inside(backBtnX(sw), backBtnY(), BTN_W, BTN_H, sx, worldY)) {
                    navigator.showMainMenu(serverUrl, username);
                    return true;
                }
                if (inside(prevBtnX(sw), navBtnY(), BTN_W, BTN_H, sx, worldY)) {
                    currentPage = Math.max(0, currentPage - 1);
                    return true;
                }
                if (inside(nextBtnX(sw), navBtnY(), BTN_W, BTN_H, sx, worldY)) {
                    currentPage = Math.min(PAGE_COUNT - 1, currentPage + 1);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawButtons(sw);

        batch.setProjectionMatrix(proj);
        batch.begin();

        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "HOW TO PLAY";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh - 48f);

        font.setColor(Color.LIGHT_GRAY);
        String pageLabel = "Page " + (currentPage + 1) + " / " + PAGE_COUNT;
        layout.setText(font, pageLabel);
        font.draw(batch, pageLabel, (sw - layout.width) / 2f, sh - 88f);

        switch (currentPage) {
            case 0:
                drawControlsPage(sw, sh);
                break;
            case 1:
                drawWeaponsPage(sw, sh);
                break;
            default:
                drawTilesPage(sw, sh);
                break;
        }

        drawButtonLabel("BACK", backBtnX(sw), backBtnY(), BTN_W, BTN_H);
        drawButtonLabel("PREV", prevBtnX(sw), navBtnY(), BTN_W, BTN_H);
        drawButtonLabel("NEXT", nextBtnX(sw), navBtnY(), BTN_W, BTN_H);

        batch.end();
    }

    private void drawControlsPage(int sw, int sh) {
        float marginX = sw * 0.06f;
        float tableTop = sh - 138f;
        float tableWidth = sw * 0.88f;
        float sectionGap = 26f * uiScale();
        float rowGap = 30f * uiScale();
        float keyColW = tableWidth * 0.19f;
        float actionColW = tableWidth * 0.22f;
        float columnGap = tableWidth * 0.09f;

        float desktopX = marginX;
        float androidX = desktopX + keyColW + actionColW + columnGap;

        String[][] desktopRows = {
                { "WASD", "Move" },
                { "Mouse", "Aim" },
                { "LMB", "Shoot" },
                { "Space", "Switch weapon" },
                { "R", "Reload" },
                { "Escape", "Pause / back" },
        };
        String[][] androidRows = {
                { "Left joystick", "Move" },
                { "Right outer ring", "Aim" },
                { "Center FIRE button", "Shoot" },
                { "SWAP button", "Switch weapon" },
                { "LOAD button", "Reload" },
                { "Pause button", "Open menu" },
        };

        drawSectionHeader("DESKTOP", desktopX, tableTop);
        drawSectionHeader("ANDROID", androidX, tableTop);
        drawControlTable(desktopRows, desktopX, tableTop - sectionGap, keyColW, actionColW, rowGap);
        drawControlTable(androidRows, androidX, tableTop - sectionGap, keyColW, actionColW, rowGap);

        float lowerY = tableTop - rowGap * 7.0f;
        drawSectionHeader("PICKUPS", marginX, lowerY);
        drawPickupRow(pickupHealTex, "Health restore", marginX, lowerY - sectionGap);
        drawPickupRow(pickupSpeedTex, "Speed boost (5 s)", marginX, lowerY - sectionGap - rowGap);
        drawPickupRow(pickupWeaponTex, "New weapon (fills slot 2)", marginX, lowerY - sectionGap - rowGap * 2f);

        float tipsY = lowerY - rowGap * 3.9f;
        drawSectionHeader("TIPS", marginX, tipsY);
        drawBulletLine("You carry up to 2 weapons. Switch at any time.", marginX, tipsY - sectionGap, sw * 0.86f);
        drawBulletLine("Dying drops your secondary weapon for other players.", marginX, tipsY - sectionGap - rowGap, sw * 0.86f);
        drawBulletLine("Health slowly regenerates while alive.", marginX, tipsY - sectionGap - rowGap * 2f, sw * 0.86f);
    }

    private void drawWeaponsPage(int sw, int sh) {
        float leftX = sw * 0.06f;
        float rightX = sw * 0.53f;
        float startY = sh - 138f;
        float rowGap = 96f * uiScale();

        drawSectionHeader("WEAPON INFO", leftX, startY);

        for (int i = 0; i < WEAPONS.length; i++) {
            float columnX = i < 4 ? leftX : rightX;
            float y = startY - 30f * uiScale() - rowGap * (i % 4);
            drawWeaponRow(WEAPONS[i], columnX, y);
        }
    }

    private void drawTilesPage(int sw, int sh) {
        float x = sw * 0.08f;
        float y = sh - 138f;
        float rowGap = 92f * uiScale();

        drawSectionHeader("TILE INFO", x, y);
        y -= 30f * uiScale();

        for (TutorialTile tile : TILES) {
            drawTileRow(tile, x, y);
            y -= rowGap;
        }
    }

    private void drawWeaponRow(TutorialWeapon weapon, float x, float y) {
        Texture texture = textureForWeapon(weapon.assetPath);
        if (texture != null) {
            drawImageFit(texture, x, y - 46f * uiScale(), 108f * uiScale(), 52f * uiScale());
        }
        font.setColor(Color.WHITE);
        font.draw(batch, weapon.name, x + 120f * uiScale(), y);
        font.setColor(Color.LIGHT_GRAY);
        drawWrapped(weapon.description, x + 120f * uiScale(), y - 20f * uiScale(), 330f * uiScale());
    }

    private void drawTileRow(TutorialTile tile, float x, float y) {
        Texture texture = textureForTile(tile.assetPath);
        if (texture != null) {
            drawImageFit(texture, x, y - 42f * uiScale(), 56f * uiScale(), 56f * uiScale());
        }
        font.setColor(Color.WHITE);
        font.draw(batch, tile.name, x + 76f * uiScale(), y);
        font.setColor(Color.LIGHT_GRAY);
        drawWrapped(tile.description, x + 76f * uiScale(), y - 20f * uiScale(), Gdx.graphics.getWidth() * 0.78f);
    }

    private void drawSectionHeader(String label, float x, float y) {
        font.setColor(new Color(0.6f, 0.8f, 1f, 1f));
        font.draw(batch, label, x, y);
    }

    private void drawControlTable(String[][] rows, float x, float y, float keyWidth, float actionWidth, float rowGap) {
        for (String[] row : rows) {
            font.setColor(Color.WHITE);
            font.draw(batch, row[0], x, y);
            font.setColor(Color.LIGHT_GRAY);
            drawWrapped(row[1], x + keyWidth, y, actionWidth);
            y -= rowGap;
        }
    }

    private void drawPickupRow(Texture texture, String text, float x, float y) {
        if (texture != null) {
            drawImageFit(texture, x, y - 24f * uiScale(), 28f * uiScale(), 28f * uiScale());
        }
        font.setColor(Color.LIGHT_GRAY);
        drawWrapped(text, x + 38f * uiScale(), y, Gdx.graphics.getWidth() * 0.72f);
    }

    private void drawBulletLine(String text, float x, float y, float width) {
        font.setColor(Color.LIGHT_GRAY);
        drawWrapped(text, x, y, width);
    }

    private void drawWrapped(String text, float x, float y, float width) {
        layout.setText(font, text, Color.LIGHT_GRAY, width, Align.left, true);
        font.draw(batch, layout, x, y);
    }

    private void drawImageFit(Texture texture, float x, float y, float maxW, float maxH) {
        float texW = texture.getWidth();
        float texH = texture.getHeight();
        float scale = Math.min(maxW / texW, maxH / texH);
        float drawW = texW * scale;
        float drawH = texH * scale;
        batch.draw(texture, x + (maxW - drawW) * 0.5f, y + (maxH - drawH) * 0.5f, drawW, drawH);
    }

    private void drawButtons(int sw) {
        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonRect(backBtnX(sw), backBtnY(), new Color(0.15f, 0.50f, 0.80f, 1f));
        drawButtonRect(prevBtnX(sw), navBtnY(), new Color(0.18f, 0.18f, 0.22f, 1f));
        drawButtonRect(nextBtnX(sw), navBtnY(), new Color(0.18f, 0.18f, 0.22f, 1f));
        shapes.end();
    }

    private void drawButtonRect(int x, int y, Color color) {
        shapes.setColor(color);
        shapes.rect(x, y, BTN_W, BTN_H);
    }

    private void drawButtonLabel(String label, int x, int y, int w, int h) {
        font.setColor(Color.WHITE);
        layout.setText(font, label);
        font.draw(batch, label, x + (w - layout.width) / 2f, y + h - 10f);
    }

    private int backBtnX(int sw) {
        return (sw - BTN_W) / 2;
    }

    private int backBtnY() {
        return 34;
    }

    private int prevBtnX(int sw) {
        return sw / 2 - BTN_W - 20;
    }

    private int nextBtnX(int sw) {
        return sw / 2 + 20;
    }

    private int navBtnY() {
        return 90;
    }

    private boolean inside(int x, int y, int w, int h, int px, int py) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void loadTextures() {
        pistolTex = new Texture(Gdx.files.internal("weapons/weapon_pistol.png"));
        uziTex = new Texture(Gdx.files.internal("weapons/weapon_uzi.png"));
        shotgunTex = new Texture(Gdx.files.internal("weapons/weapon_shotgun.png"));
        akTex = new Texture(Gdx.files.internal("weapons/weapon_ak.png"));
        sniperTex = new Texture(Gdx.files.internal("weapons/weapon_sniper.png"));
        crossbowTex = new Texture(Gdx.files.internal("weapons/weapon_crossbow.png"));
        flamethrowerTex = new Texture(Gdx.files.internal("weapons/weapon_flamethrower.png"));
        machinegunTex = new Texture(Gdx.files.internal("weapons/weapon_machinegun.png"));
        wallTileTex = new Texture(Gdx.files.internal("tiles/tile_wall.png"));
        windowTileTex = new Texture(Gdx.files.internal("tiles/tile_window.png"));
        trapTileTex = new Texture(Gdx.files.internal("tiles/tile_trap.png"));
        cobwebTileTex = new Texture(Gdx.files.internal("tiles/tile_cobweb.png"));
        chestTex = new Texture(Gdx.files.internal("chests/chest_0.png"));
        pickupHealTex = new Texture(Gdx.files.internal("pickups/pickup_health.png"));
        pickupSpeedTex = new Texture(Gdx.files.internal("pickups/pickup_speed.png"));
        pickupWeaponTex = new Texture(Gdx.files.internal("pickups/pickup_ammo.png"));
    }

    private Texture textureForWeapon(String assetPath) {
        switch (assetPath) {
            case "weapons/weapon_pistol.png": return pistolTex;
            case "weapons/weapon_uzi.png": return uziTex;
            case "weapons/weapon_shotgun.png": return shotgunTex;
            case "weapons/weapon_ak.png": return akTex;
            case "weapons/weapon_sniper.png": return sniperTex;
            case "weapons/weapon_crossbow.png": return crossbowTex;
            case "weapons/weapon_flamethrower.png": return flamethrowerTex;
            case "weapons/weapon_machinegun.png": return machinegunTex;
            default: return null;
        }
    }

    private Texture textureForTile(String assetPath) {
        switch (assetPath) {
            case "tiles/tile_wall.png": return wallTileTex;
            case "tiles/tile_window.png": return windowTileTex;
            case "tiles/tile_trap.png": return trapTileTex;
            case "tiles/tile_cobweb.png": return cobwebTileTex;
            case "chests/chest_0.png": return chestTex;
            default: return null;
        }
    }

    @Override public void resize(int w, int h) { rebuildProj(); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { Gdx.input.setInputProcessor(null); }

    @Override
    public void dispose() {
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (font != null) font.dispose();
        disposeTexture(pistolTex);
        disposeTexture(uziTex);
        disposeTexture(shotgunTex);
        disposeTexture(akTex);
        disposeTexture(sniperTex);
        disposeTexture(crossbowTex);
        disposeTexture(flamethrowerTex);
        disposeTexture(machinegunTex);
        disposeTexture(wallTileTex);
        disposeTexture(windowTileTex);
        disposeTexture(trapTileTex);
        disposeTexture(cobwebTileTex);
        disposeTexture(chestTex);
        disposeTexture(pickupHealTex);
        disposeTexture(pickupSpeedTex);
        disposeTexture(pickupWeaponTex);
    }

    private void disposeTexture(Texture texture) {
        if (texture != null) texture.dispose();
    }

    private void rebuildProj() {
        if (proj == null) proj = new Matrix4();
        proj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private float uiScale() {
        float shortSide = Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float scale = Math.max(1.10f, Math.min(1.55f, shortSide / 700f));
        return isMobileLike() ? scale : 1.0f;
    }

    private boolean isMobileLike() {
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }

    private static final class TutorialWeapon {
        private final String name;
        private final String description;
        private final String assetPath;

        private TutorialWeapon(String name, String description, String assetPath) {
            this.name = name;
            this.description = description;
            this.assetPath = assetPath;
        }
    }

    private static final class TutorialTile {
        private final String name;
        private final String description;
        private final String assetPath;

        private TutorialTile(String name, String description, String assetPath) {
            this.name = name;
            this.description = description;
            this.assetPath = assetPath;
        }
    }
}
