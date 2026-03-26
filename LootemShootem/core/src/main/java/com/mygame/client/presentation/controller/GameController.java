package com.mygame.client.presentation.controller;

import com.mygame.client.application.usecase.SendInputUseCase;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.presentation.view.input.InputHandler;
import com.mygame.shared.dto.PlayerDto;
import com.mygame.shared.util.Vec2;

public final class GameController {

    private static final float SEND_HZ = 20f;
    private static final float SEND_DT = 1f / SEND_HZ;

    private final WorldState       worldState;
    private final InputHandler     inputHandler;
    private final SendInputUseCase sendInput;

    private float accumulator = 0f;

    public GameController(WorldState worldState,
                          InputHandler inputHandler,
                          SendInputUseCase sendInput) {
        this.worldState   = worldState;
        this.inputHandler = inputHandler;
        this.sendInput    = sendInput;
    }

    public void update(float delta) {
        PlayerDto me = worldState.getLocalPlayer();
        accumulator += delta;
        while (accumulator >= SEND_DT) {
            accumulator -= SEND_DT;
            if (me == null || !me.isDead) {
                Vec2    playerPos = (me != null) ? me.pos : null;
                Vec2    move      = inputHandler.getMove();
                Vec2    aim       = inputHandler.getAim(playerPos);
                boolean shoot     = inputHandler.isShoot();
                boolean sw        = inputHandler.consumeSwitchWeapon();
                boolean reload    = inputHandler.consumeReload();
                sendInput.execute(move, aim, shoot, sw, reload);
            }
        }
    }
}
