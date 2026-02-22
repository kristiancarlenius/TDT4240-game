package com.mygame.shared.util;

import java.util.Objects;
import java.util.UUID;

public final class Id {
    private final String value;

    public Id(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Id cannot be null/blank");
        this.value = value;
    }

    public static Id random() {
        return new Id(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override public String toString() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Id)) return false;
        Id id = (Id) o;
        return value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}