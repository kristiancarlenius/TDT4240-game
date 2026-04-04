package com.mygame.server.domain.model.proc;

public final class MapGenerationSpec {

    public final int playableWidth;
    public final int playableHeight;
    public final int skeletonSpacingX;
    public final int skeletonSpacingY;
    public final int centralStartX;
    public final int centralStartY;
    public final int centralWidth;
    public final int centralHeight;

    public final double wallWeight;
    public final double windowWeight;
    public final double doorWeight;
    public final double noneWeight;

    public MapGenerationSpec(
            int playableWidth,
            int playableHeight,
            int skeletonSpacingX,
            int skeletonSpacingY,
            int centralStartX,
            int centralStartY,
            int centralWidth,
            int centralHeight,
            double wallWeight,
            double windowWeight,
            double doorWeight,
            double noneWeight) {
        this.playableWidth = playableWidth;
        this.playableHeight = playableHeight;
        this.skeletonSpacingX = skeletonSpacingX;
        this.skeletonSpacingY = skeletonSpacingY;
        this.centralStartX = centralStartX;
        this.centralStartY = centralStartY;
        this.centralWidth = centralWidth;
        this.centralHeight = centralHeight;
        this.wallWeight = wallWeight;
        this.windowWeight = windowWeight;
        this.doorWeight = doorWeight;
        this.noneWeight = noneWeight;
    }

    public static MapGenerationSpec defaultSpec() {
        return new MapGenerationSpec(
                95,
                65,
                16,
                11,
                32,
                22,
                31,
                21,
                0.25,
                0.25,
                0.25,
                0.25);
    }

    public int worldWidth() {
        return playableWidth + 2;
    }

    public int worldHeight() {
        return playableHeight + 2;
    }
}
