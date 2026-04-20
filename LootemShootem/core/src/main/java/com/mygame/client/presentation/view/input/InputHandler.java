package com.mygame.client.presentation.view.input;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.mygame.client.data.repository.PreferencesRepository;
import com.mygame.client.domain.ports.PreferencesPort;
import com.mygame.shared.util.Vec2;

public final class InputHandler {

    private static final float PAUSE_BTN_SIZE = 56f;
    private static final float PAUSE_BTN_MARGIN = 16f;
    private static final long FIRE_HOLD_MS = 180L;

    private final OrthographicCamera camera;
    private final PreferencesPort prefs;

    private VirtualJoystickView moveStick;
    private VirtualJoystickView aimStick;

    private float switchBtnX;
    private float switchBtnY;
    private float switchBtnR;
    private float reloadBtnX;
    private float reloadBtnY;
    private float reloadBtnR;
    private float fireBtnX;
    private float fireBtnY;
    private float fireBtnR;
    private float fireBtnTouchR;
    private float pauseBtnX;
    private float pauseBtnY;
    private float pauseBtnSize;

    private boolean touchSwitchPending = false;
    private boolean touchReloadPending = false;
    private boolean touchPausePending = false;
    private boolean touchFireHeld = false;
    private boolean touchFireBurstPending = false;
    private boolean desktopSwitchLatch = false;
    private boolean desktopReloadLatch = false;
    private int aimPointer = -1;
    private int switchPointer = -1;
    private int reloadPointer = -1;
    private int firePointer = -1;
    private long fireTouchDownAtMs = 0L;

    public InputHandler(OrthographicCamera camera) {
        this.camera = camera;
        this.prefs = new PreferencesRepository();

        if (isAndroidApp()) {
            moveStick = new VirtualJoystickView(0f, 0f);
            aimStick = new VirtualJoystickView(0f, 0f);
            aimStick.setThumbVisible(false);
            refreshMobileLayout();
            registerTouchProcessor();
        }
    }

    public boolean isAndroid() {
        return moveStick != null && aimStick != null;
    }

    public VirtualJoystickView getMoveStick() {
        return moveStick;
    }

    public VirtualJoystickView getAimStick() {
        return aimStick;
    }

    public float getSwitchBtnX() {
        return switchBtnX;
    }

    public float getSwitchBtnY() {
        return switchBtnY;
    }

    public float getSwitchBtnR() {
        return switchBtnR;
    }

    public float getReloadBtnX() {
        return reloadBtnX;
    }

    public float getReloadBtnY() {
        return reloadBtnY;
    }

    public float getReloadBtnR() {
        return reloadBtnR;
    }

    public float getFireBtnX() {
        return fireBtnX;
    }

    public float getFireBtnY() {
        return fireBtnY;
    }

    public float getFireBtnR() {
        return fireBtnR;
    }

    public float getPauseBtnX() {
        return pauseBtnX;
    }

    public float getPauseBtnY() {
        return pauseBtnY;
    }

    public float getPauseBtnSize() {
        return pauseBtnSize;
    }

    public void refreshMobileLayout() {
        if (!isAndroid()) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        boolean swapped = prefs.isControlsSwapped();
        float scale = prefs.getTouchJoystickScale() * mobileUiScale();

        float moveRadius = 76f * scale;
        float aimRadius = 108f * scale;
        float stickY = Math.max(aimRadius + 42f, sh * 0.24f);
        float moveMargin = Math.max(moveRadius + 28f, sw * 0.11f);
        float aimMargin = Math.max(aimRadius + 30f, sw * 0.13f);
        float moveX = swapped ? sw - moveMargin : moveMargin;
        float aimX = swapped ? aimMargin : sw - aimMargin;

        moveStick.setCenter(moveX, stickY);
        aimStick.setCenter(aimX, stickY);
        moveStick.setBaseRadius(moveRadius, prefs.getTouchJoystickOpacity());
        aimStick.setBaseRadius(aimRadius, prefs.getTouchJoystickOpacity());

        switchBtnX = aimX;
        switchBtnY = Math.min(sh - 136f, stickY + aimRadius + 34f);
        switchBtnR = 38f * scale;

        reloadBtnX = aimX - (swapped ? -108f : 108f) * scale;
        reloadBtnY = Math.min(sh - 148f, stickY + aimRadius + 24f);
        reloadBtnR = 34f * scale;

        fireBtnX = aimX;
        fireBtnY = stickY;
        fireBtnR = aimRadius * 0.49f;
        fireBtnTouchR = fireBtnR * 1.24f;

        pauseBtnSize = PAUSE_BTN_SIZE;
        pauseBtnX = PAUSE_BTN_MARGIN;
        pauseBtnY = sh - PAUSE_BTN_MARGIN - pauseBtnSize;
    }

    public void resetTouchState() {
        if (!isAndroid()) return;
        moveStick.reset();
        aimStick.reset();
        aimPointer = -1;
        switchPointer = -1;
        reloadPointer = -1;
        firePointer = -1;
        touchFireHeld = false;
        touchFireBurstPending = false;
        fireTouchDownAtMs = 0L;
    }

    public void pollLatching() {
        if (!isAndroid()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) desktopSwitchLatch = true;
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) desktopReloadLatch = true;
        }
    }

    public Vec2 getMove() {
        if (isAndroid()) return moveStick.getDirection();
        float mx = 0f, my = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f;
        return new Vec2(mx, my);
    }

    public Vec2 getAim(Vec2 playerWorldPos) {
        if (isAndroid()) return aimStick.getDirection();
        if (playerWorldPos == null) return new Vec2(1f, 0f);
        Vector3 mw = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mw);
        float ax = mw.x - playerWorldPos.x;
        float ay = mw.y - playerWorldPos.y;
        float len2 = ax * ax + ay * ay;
        if (len2 < 1e-6f) return new Vec2(0f, 0f);
        float inv = (float) (1.0 / Math.sqrt(len2));
        return new Vec2(ax * inv, ay * inv);
    }

    public boolean isShoot() {
        if (isAndroid()) {
            if (touchFireBurstPending) {
                touchFireBurstPending = false;
                return true;
            }
            return touchFireHeld;
        }
        return Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    }

    public boolean consumeSwitchWeapon() {
        if (isAndroid()) {
            boolean v = touchSwitchPending;
            touchSwitchPending = false;
            return v;
        }
        boolean v = desktopSwitchLatch;
        desktopSwitchLatch = false;
        return v;
    }

    public boolean consumeReload() {
        if (isAndroid()) {
            boolean v = touchReloadPending;
            touchReloadPending = false;
            return v;
        }
        boolean v = desktopReloadLatch;
        desktopReloadLatch = false;
        return v;
    }

    public boolean consumePauseToggle() {
        if (!isAndroid()) return false;
        boolean v = touchPausePending;
        touchPausePending = false;
        return v;
    }

    public void clearInputProcessor() {
        Gdx.input.setInputProcessor(null);
    }

    private void registerTouchProcessor() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                int sh = Gdx.graphics.getHeight();
                float worldY = sh - sy;

                if (sx >= pauseBtnX && sx <= pauseBtnX + pauseBtnSize
                        && worldY >= pauseBtnY && worldY <= pauseBtnY + pauseBtnSize) {
                    touchPausePending = true;
                    return true;
                }

                if (insideCircle(sx, worldY, switchBtnX, switchBtnY, switchBtnR) && switchPointer == -1) {
                    switchPointer = pointer;
                    touchSwitchPending = true;
                    return true;
                }

                if (insideCircle(sx, worldY, reloadBtnX, reloadBtnY, reloadBtnR) && reloadPointer == -1) {
                    reloadPointer = pointer;
                    touchReloadPending = true;
                    return true;
                }

                if (insideCircle(sx, worldY, fireBtnX, fireBtnY, fireBtnTouchR) && firePointer == -1) {
                    firePointer = pointer;
                    touchFireHeld = false;
                    fireTouchDownAtMs = System.currentTimeMillis();
                    return true;
                }

                if (moveStick.touchDown(sx, sy, pointer, sh)) return true;
                if (isInsideAimRing(sx, worldY) && aimStick.touchDown(sx, sy, pointer, sh)) {
                    aimPointer = pointer;
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                int sh = Gdx.graphics.getHeight();
                if (moveStick.touchDragged(sx, sy, pointer, sh)) return true;
                if (aimStick.touchDragged(sx, sy, pointer, sh)) return true;
                return false;
            }

            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                if (pointer == switchPointer) {
                    switchPointer = -1;
                    return true;
                }
                if (pointer == reloadPointer) {
                    reloadPointer = -1;
                    return true;
                }
                if (pointer == firePointer) {
                    long heldMs = System.currentTimeMillis() - fireTouchDownAtMs;
                    firePointer = -1;
                    if (!touchFireHeld && heldMs < FIRE_HOLD_MS) {
                        touchFireBurstPending = true;
                    }
                    touchFireHeld = false;
                    fireTouchDownAtMs = 0L;
                    return true;
                }
                if (moveStick.touchUp(sx, sy, pointer)) return true;
                if (aimStick.touchUp(sx, sy, pointer)) {
                    if (pointer == aimPointer) aimPointer = -1;
                    return true;
                }
                return false;
            }
        });
    }

    public void update(float delta) {
        if (!isAndroid()) return;
        if (firePointer != -1 && fireTouchDownAtMs != 0L && !touchFireHeld) {
            long heldMs = System.currentTimeMillis() - fireTouchDownAtMs;
            if (heldMs >= FIRE_HOLD_MS) {
                touchFireHeld = true;
            }
        }
    }

    public float getAimFireZoneRadius() {
        return !isAndroid() ? 0f : fireBtnR;
    }

    private boolean isInsideAimRing(float x, float y) {
        float dx = x - aimStick.getBaseX();
        float dy = y - aimStick.getBaseY();
        float dist2 = dx * dx + dy * dy;
        return dist2 <= aimStick.getBaseRadius() * aimStick.getBaseRadius()
                && dist2 > fireBtnTouchR * fireBtnTouchR;
    }

    private boolean insideCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private float mobileUiScale() {
        float shortSide = Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return Math.max(1.0f, Math.min(1.75f, shortSide / 720f));
    }

    private boolean isAndroidApp() {
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }
}
