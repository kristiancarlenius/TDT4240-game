package com.mygame.shared.util;

import java.util.Objects;

public final class Vec2 {
    public float x;
    public float y;

    // Jackson needs a no-arg constructor
    public Vec2() {}

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public static Vec2 zero() {
        return new Vec2(0f, 0f);
    }

    public float len2() {
        return x * x + y * y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec2)) return false;
        Vec2 vec2 = (Vec2) o;
        return Float.compare(vec2.x, x) == 0 && Float.compare(vec2.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override public String toString() {
        return "Vec2(" + x + "," + y + ")";
    }
}