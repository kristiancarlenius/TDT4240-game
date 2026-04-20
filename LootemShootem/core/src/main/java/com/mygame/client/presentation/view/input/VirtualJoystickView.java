package com.mygame.client.presentation.view.input;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.mygame.shared.util.Vec2;

public final class VirtualJoystickView {

    private static final float BASE_RADIUS_DEFAULT = 80f;
    private static final float THUMB_RATIO = 0.45f;

    private float baseX;
    private float baseY;
    private float baseRadius = BASE_RADIUS_DEFAULT;
    private float thumbRadius = BASE_RADIUS_DEFAULT * THUMB_RATIO;
    private float opacity = 0.78f;
    private boolean thumbVisible = true;

    private float thumbX = 0f;
    private float thumbY = 0f;
    private int ownerPointer = -1;

    public VirtualJoystickView(float centerX, float centerY) {
        setCenter(centerX, centerY);
    }

    public void setCenter(float centerX, float centerY) {
        this.baseX = centerX;
        this.baseY = centerY;
    }

    public void setVisuals(float scale, float opacity) {
        this.baseRadius = BASE_RADIUS_DEFAULT * scale;
        this.thumbRadius = baseRadius * THUMB_RATIO;
        this.opacity = opacity;
        clampThumb();
    }

    public void setBaseRadius(float baseRadius, float opacity) {
        this.baseRadius = Math.max(32f, baseRadius);
        this.thumbRadius = this.baseRadius * THUMB_RATIO;
        this.opacity = opacity;
        clampThumb();
    }

    public float getBaseX() {
        return baseX;
    }

    public float getBaseY() {
        return baseY;
    }

    public float getBaseRadius() {
        return baseRadius;
    }

    public float getThumbRadius() {
        return thumbRadius;
    }

    public void setThumbVisible(boolean thumbVisible) {
        this.thumbVisible = thumbVisible;
    }

    public boolean touchDown(int screenX, int screenY, int pointer, int screenH) {
        if (ownerPointer != -1) return false;
        float wx = screenX;
        float wy = screenH - screenY;
        float dx = wx - baseX;
        float dy = wy - baseY;
        if (dx * dx + dy * dy <= baseRadius * baseRadius) {
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
        releasePointer(pointer);
        return true;
    }

    public void releasePointer(int pointer) {
        if (ownerPointer != pointer) return;
        ownerPointer = -1;
        thumbX = 0f;
        thumbY = 0f;
    }

    public void reset() {
        ownerPointer = -1;
        thumbX = 0f;
        thumbY = 0f;
    }

    public Vec2 getDirection() {
        if (thumbX == 0f && thumbY == 0f) return new Vec2(0f, 0f);
        float len2 = thumbX * thumbX + thumbY * thumbY;
        if (len2 < 1e-6f) return new Vec2(0f, 0f);
        float inv = (float) (1.0 / Math.sqrt(len2));
        return new Vec2(thumbX * inv, thumbY * inv);
    }

    public float getMagnitude() {
        float maxR = Math.max(1f, baseRadius - thumbRadius);
        return Math.min(1f, (float) Math.sqrt(thumbX * thumbX + thumbY * thumbY) / maxR);
    }

    public boolean isActive() {
        return ownerPointer != -1;
    }

    public void render(ShapeRenderer shapes) {
        shapes.setColor(0.10f, 0.10f, 0.12f, opacity * 0.55f);
        shapes.circle(baseX, baseY, baseRadius, 32);

        shapes.setColor(0.80f, 0.86f, 0.95f, opacity * 0.28f);
        shapes.circle(baseX, baseY, baseRadius - 5f, 32);

        if (thumbVisible) {
            float tx = baseX + thumbX;
            float ty = baseY + thumbY;
            shapes.setColor(0.45f, 0.70f, 0.98f, opacity);
            shapes.circle(tx, ty, thumbRadius, 24);
        }
    }

    private void updateThumb(float wx, float wy) {
        thumbX = wx - baseX;
        thumbY = wy - baseY;
        clampThumb();
    }

    private void clampThumb() {
        float maxR = Math.max(1f, baseRadius - thumbRadius);
        float len2 = thumbX * thumbX + thumbY * thumbY;
        if (len2 > maxR * maxR) {
            float inv = maxR / (float) Math.sqrt(len2);
            thumbX *= inv;
            thumbY *= inv;
        }
    }
}
