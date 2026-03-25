package com.mygame.client.presentation.view.input;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.mygame.shared.util.Vec2;

/**
 * Reads raw player input for a single frame and exposes move/aim/shoot/switch.
 *
 * On Android: delegates to VirtualJoystickView (touch joysticks).
 * On desktop:  reads WASD + mouse.
 *
 * Also owns the LibGDX InputProcessor registration for touch platforms.
 */
public final class InputHandler {

    // Touch controls (non-null only on Android)
    public final VirtualJoystickView moveStick;
    public final VirtualJoystickView aimStick;
    public final float               switchBtnX;
    public final float               switchBtnY;
    public final float               switchBtnR;

    private final OrthographicCamera camera;
    private boolean touchSwitchPending = false;

    public InputHandler(OrthographicCamera camera) {
        this.camera = camera;

        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            int sw = Gdx.graphics.getWidth();
            int sh = Gdx.graphics.getHeight();
            moveStick  = new VirtualJoystickView(sw * 0.18f, sh * 0.22f);
            aimStick   = new VirtualJoystickView(sw * 0.82f, sh * 0.22f);
            switchBtnX = sw * 0.82f;
            switchBtnY = sh * 0.42f;
            switchBtnR = 40f;
            registerTouchProcessor();
        } else {
            moveStick  = null;
            aimStick   = null;
            switchBtnX = 0f;
            switchBtnY = 0f;
            switchBtnR = 0f;
        }
    }

    public boolean isAndroid() { return moveStick != null; }

    public Vec2 getMove() {
        if (isAndroid()) return moveStick.getDirection();
        float mx = 0f, my = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f;
        return new Vec2(mx, my);
    }

    /**
     * @param playerWorldPos the local player's world-space position (for mouse aim).
     *                       Pass null if not yet known — returns (1,0) as fallback.
     */
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
        if (isAndroid()) return aimStick.isActive();
        return Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    }

    /** Returns true once per tap on the weapon-switch button (or SPACE on desktop). */
    public boolean consumeSwitchWeapon() {
        if (isAndroid()) {
            boolean v = touchSwitchPending;
            touchSwitchPending = false;
            return v;
        }
        return Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    }

    /** Clear the LibGDX InputProcessor (call from Screen.hide()). */
    public void clearInputProcessor() {
        Gdx.input.setInputProcessor(null);
    }

    // ---- Private ----

    private void registerTouchProcessor() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            private int switchPointer = -1;

            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                int sh = Gdx.graphics.getHeight();
                float bx = sx - switchBtnX;
                float by = (sh - sy) - switchBtnY;
                if (bx * bx + by * by <= switchBtnR * switchBtnR && switchPointer == -1) {
                    switchPointer      = pointer;
                    touchSwitchPending = true;
                    return true;
                }
                if (moveStick.touchDown(sx, sy, pointer, sh)) return true;
                if (aimStick.touchDown(sx, sy, pointer, sh))  return true;
                return false;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                int sh = Gdx.graphics.getHeight();
                if (moveStick.touchDragged(sx, sy, pointer, sh)) return true;
                if (aimStick.touchDragged(sx, sy, pointer, sh))  return true;
                return false;
            }

            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                if (pointer == switchPointer) { switchPointer = -1; return true; }
                if (moveStick.touchUp(sx, sy, pointer)) return true;
                if (aimStick.touchUp(sx, sy, pointer))  return true;
                return false;
            }
        });
    }
}
