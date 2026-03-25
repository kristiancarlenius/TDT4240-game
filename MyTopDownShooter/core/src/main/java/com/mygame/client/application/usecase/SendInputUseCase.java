package com.mygame.client.application.usecase;

import com.mygame.client.application.service.GameSessionService;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.util.Vec2;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds and sends a player input message to the server via GameSessionService.
 */
public final class SendInputUseCase {

    private final GameSessionService session;
    private final AtomicInteger      seq = new AtomicInteger(1);

    public SendInputUseCase(GameSessionService session) {
        this.session = session;
    }

    public void execute(Vec2 move, Vec2 aim, boolean shoot, boolean switchWeapon) {
        if (!session.isConnected()) return;
        session.sendInput(new InputMessage(seq.getAndIncrement(), move, aim, shoot, switchWeapon));
    }
}
