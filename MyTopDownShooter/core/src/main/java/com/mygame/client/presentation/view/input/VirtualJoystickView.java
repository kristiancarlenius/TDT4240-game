package com.mygame.client.presentation.view.input;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.mygame.shared.util.Vec2;

/**
 * Self-contained on-screen joystick for touch input.
 *
 * Usage per frame:
 *   1. Route touch events: touchDown / touchDragged / touchUp
 *      (all coordinates in LibGDX screen space: origin top-left, Y grows down)
 *   2. Call render() during the HUD (screen-space) ShapeRenderer pass.
 *   3. Call getDirection() to read the normalised Vec2 (zero when idle).
 *
 * The component converts from LibGDX screen-Y (top-left origin) to
 * bottom-left origin internally so callers need not care.
 */
public final class VirtualJoystickView {

    // Visual radii (pixels)
    private static final float BASE_RADIUS  = 80f;
    private static final float THUMB_RADIUS = 36f;

    // Centre of the base circle in bottom-left-origin pixels
    private final float baseX;
    private final float baseY;

    // Current thumb offset (bottom-left-origin) — zero when idle
    private float thumbX = 0f;
    private float thumbY = 0f;

    // Which pointer owns this stick (-1 = free)
    private int ownerPointer = -1;

    /**
     * @param centerX  X of the base centre in screen pixels (left = 0)
     * @param centerY  Y of the base centre in bottom-left-origin pixels
     */
    public VirtualJoystickView(float centerX, float centerY) {
        this.baseX = centerX;
        this.baseY = centerY;
    }

    // ---- Touch routing ----

    /**
     * @param screenY LibGDX screen Y (top-left origin); pass Gdx.input.getY() directly.
     * @param screenH Gdx.graphics.getHeight() — needed for Y-flip.
     * @return true if this stick claimed the pointer.
     */
    public boolean touchDown(int screenX, int screenY, int pointer, int screenH) {
        if (ownerPointer != -1) return false;
        float wx = screenX;
        float wy = screenH - screenY;
        float dx = wx - baseX;
        float dy = wy - baseY;
        if (dx * dx + dy * dy <= BASE_RADIUS * BASE_RADIUS) {
            ownerPointer = pointer;
            updateThumb(wx, wy);
            return true;
        }
        return false;
    }

    public boolean touchDragged(int screenX, int screenY, int pointer, int screenH) {
        if (ownerPointer != pointer) return false;
        float wx = screenX;
        float wy = screenH - screenY;
        updateThumb(wx, wy);
        return true;
    }

    public boolean touchUp(int screenX, int screenY, int pointer) {
        if (ownerPointer != pointer) return false;
        ownerPointer = -1;
        thumbX = 0f;
        thumbY = 0f;
        return true;
    }

    // ---- Query ----

    /** Returns the current direction as a unit vector, or zero if idle. */
    public Vec2 getDirection() {
        if (thumbX == 0f && thumbY == 0f) return new Vec2(0f, 0f);
        float len2 = thumbX * thumbX + thumbY * thumbY;
        if (len2 < 1e-6f) return new Vec2(0f, 0f);
        float inv = (float) (1.0 / Math.sqrt(len2));
        return new Vec2(thumbX * inv, thumbY * inv);
    }

    public boolean isActive() {
        return ownerPointer != -1;
    }

    // ---- Render ----

    /**
     * Draw the joystick using a ShapeRenderer that is already begun with
     * ShapeType.Filled and has a screen-space projection matrix set.
     */
    public void render(ShapeRenderer shapes) {
        // Base circle (dark)
        shapes.setColor(0.15f, 0.15f, 0.18f, 0.70f);
        shapes.circle(baseX, baseY, BASE_RADIUS, 32);

        // Base ring
        shapes.setColor(0.35f, 0.35f, 0.45f, 0.85f);
        shapes.circle(baseX, baseY, BASE_RADIUS, 32);
        shapes.setColor(0.15f, 0.15f, 0.18f, 0.70f);
        shapes.circle(baseX, baseY, BASE_RADIUS - 4f, 32);

        // Thumb
        float tx = baseX + thumbX;
        float ty = baseY + thumbY;
        shapes.setColor(0.55f, 0.65f, 0.90f, 0.90f);
        shapes.circle(tx, ty, THUMB_RADIUS, 24);
    }

    // ---- Private ----

    private void updateThumb(float wx, float wy) {
        float dx = wx - baseX;
        float dy = wy - baseY;
        float len2 = dx * dx + dy * dy;
        float maxR = BASE_RADIUS - THUMB_RADIUS;
        if (len2 > maxR * maxR) {
            float inv = maxR / (float) Math.sqrt(len2);
            dx *= inv;
            dy *= inv;
        }
        thumbX = dx;
        thumbY = dy;
    }
}
