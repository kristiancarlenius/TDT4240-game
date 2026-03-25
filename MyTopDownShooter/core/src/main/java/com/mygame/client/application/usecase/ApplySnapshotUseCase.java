package com.mygame.client.application.usecase;

import com.mygame.client.domain.model.WorldState;
import com.mygame.shared.dto.GameSnapshotDto;

/**
 * Updates the client WorldState with a new snapshot received from the server.
 */
public final class ApplySnapshotUseCase {

    private final WorldState worldState;

    public ApplySnapshotUseCase(WorldState worldState) {
        this.worldState = worldState;
    }

    public void execute(GameSnapshotDto snapshot) {
        worldState.applySnapshot(snapshot);
    }
}
