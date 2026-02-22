package com.mygame.shared.protocol.messages;

import com.mygame.shared.protocol.ClientMessage;
import com.mygame.shared.protocol.ProtocolConstants;
import com.mygame.shared.util.Vec2;

public final class InputMessage implements ClientMessage {
    // Client-side sequence number (monotonic)
    public int seq;

    // Movement from left joystick (-1..1 each axis)
    public Vec2 move;

    // Aim from right joystick (-1..1 each axis)
    public Vec2 aim;

    public boolean shoot;
    public boolean switchWeapon;

    public InputMessage() {}

    public InputMessage(int seq, Vec2 move, Vec2 aim, boolean shoot, boolean switchWeapon) {
        this.seq = seq;
        this.move = move;
        this.aim = aim;
        this.shoot = shoot;
        this.switchWeapon = switchWeapon;
    }

    @Override
    public String type() {
        return ProtocolConstants.T_INPUT;
    }
}