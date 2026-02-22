package com.mygame.shared.protocol;

/**
 * Simple wrapper used on the wire:
 * { "type": "...", "payload": { ... } }
 */
public final class Envelope {
    public String type;
    public Object payload;

    public Envelope() {}

    public Envelope(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }
}