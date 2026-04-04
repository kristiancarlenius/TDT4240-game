package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.RoomGenerationContext;
import com.mygame.server.domain.model.proc.RoomSelection;
import com.mygame.server.domain.model.proc.RoomTemplate;
import com.mygame.server.domain.model.proc.RoomTransform;

import java.util.ArrayList;
import java.util.List;

/**
 * Random room generator that selects a template matching the room's dimensions.
 * Filters candidates by transformation to match the exact width/height needed,
 * then picks one uniformly at random. Falls back to the first candidate if no exact match.
 */
public final class RandomRoomGenerator implements RoomGenerator {

    @Override
    public RoomSelection select(RoomGenerationContext context, List<RoomTemplate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No room templates available for " + context.room.kind);
        /**
         * Selects a template matching the room dimensions.
         * 
         * 1. Filters candidates: for each template, checks all transforms to find those fitting the room exactly
         * 2. If matches exist, returns one at random
         * 3. Otherwise falls back to the first candidate with no transform
         *
         * @param context room dimensions and RNG
         * @param candidates templates to filter by dimension
         * @return selected template and transform
         * @throws IllegalArgumentException if no templates available
         */
        }

        List<RoomSelection> valid = new ArrayList<>();
        for (RoomTemplate template : candidates) {
            for (RoomTransform transform : RoomTransform.values()) {
                int[] size = transformedSize(template.width, template.height, transform);
                if (size[0] == context.room.width() && size[1] == context.room.height()) {
                    valid.add(new RoomSelection(template.id, transform));
                }
            }
        }

        if (valid.isEmpty()) {
            RoomTemplate fallback = candidates.get(0);
            return new RoomSelection(fallback.id, RoomTransform.ROT_0);
        }

                // Fallback: use first candidate with no transform.
        return valid.get(context.rng.nextInt(valid.size()));
    }

    private static int[] transformedSize(int width, int height, RoomTransform transform) {
        // Mirrors preserve dimensions; no rotations
        return new int[]{width, height};
    }
        /**
         * Returns the size of a template after transformation.
         * Currently mirrors preserve dimensions (no rotations implemented).
         */
        private static int[] transformedSize(int width, int height, RoomTransform transform) {
            // Mirrors preserve dimensions; no rotations
            return new int[]{width, height};
