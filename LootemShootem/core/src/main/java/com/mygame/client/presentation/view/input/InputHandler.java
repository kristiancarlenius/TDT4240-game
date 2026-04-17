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
        float scale = prefs.getTouchJoystickScale();

        float stickY = sh * 0.24f;
        float edgeMargin = Math.max(88f, 112f * scale);
        float moveX = swapped ? sw - edgeMargin : edgeMargin;
        float aimX = swapped ? edgeMargin : sw - edgeMargin;

        moveStick.setCenter(moveX, stickY);
        aimStick.setCenter(aimX, stickY);
        moveStick.setVisuals(scale, prefs.getTouchJoystickOpacity());
        aimStick.setVisuals(scale, prefs.getTouchJoystickOpacity());

        switchBtnX = aimX;
        switchBtnY = Math.min(sh - 120f, stickY + 130f * scale);
        switchBtnR = 38f * scale;

        reloadBtnX = aimX - (swapped ? -92f : 92f) * scale;
        reloadBtnY = Math.min(sh - 132f, stickY + 122f * scale);
        reloadBtnR = 34f * scale;

        float fireOffset = 78f * scale;
        fireBtnX = aimX + (swapped ? -fireOffset : fireOffset);
        fireBtnY = Math.max(96f, stickY + 6f * scale);
        fireBtnR = 48f * scale;
        fireBtnX = Math.max(fireBtnR + 24f, Math.min(sw - fireBtnR - 24f, fireBtnX));
        fireBtnY = Math.max(fireBtnR + 24f, Math.min(sh - fireBtnR - 24f, fireBtnY));

        pauseBtnSize = PAUSE_BTN_SIZE;
        pauseBtnX = PAUSE_BTN_MARGIN;
        pauseBtnY = sh - PAUSE_BTN_MARGIN - pauseBtnSize;
    }

    public void resetTouchState() {
        if (!isAndroid()) return;
        moveStick.reset();
        aimStick.reset();
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

                float bx = sx - switchBtnX;
                float by = worldY - switchBtnY;
                if (switchPointer == -1 && bx * bx + by * by <= switchBtnR * switchBtnR) {
                    switchPointer = pointer;
                    touchSwitchPending = true;
                    return true;
                }

                float rx = sx - reloadBtnX;
                float ry = worldY - reloadBtnY;
                if (reloadPointer == -1 && rx * rx + ry * ry <= reloadBtnR * reloadBtnR) {
                    reloadPointer = pointer;
                    touchReloadPending = true;
                    return true;
                }

                float fx = sx - fireBtnX;
                float fy = worldY - fireBtnY;
                if (firePointer == -1 && fx * fx + fy * fy <= fireBtnR * fireBtnR) {
                    firePointer = pointer;
                    touchFireHeld = false;
                    fireTouchDownAtMs = System.currentTimeMillis();
                    return true;
                }

                if (moveStick.touchDown(sx, sy, pointer, sh)) return true;
                if (aimStick.touchDown(sx, sy, pointer, sh)) return true;
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
                if (aimStick.touchUp(sx, sy, pointer)) return true;
                return false;
            }
        });
    }

    public void update(float delta) {
        if (!isAndroid()) return;
        if (firePointer != -1 && !touchFireHeld) {
            long heldMs = System.currentTimeMillis() - fireTouchDownAtMs;
            if (heldMs >= FIRE_HOLD_MS) {
                touchFireHeld = true;
            }
        }
    }

    private boolean isAndroidApp() {
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }
}
