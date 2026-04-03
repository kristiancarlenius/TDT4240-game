package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.*;

import java.util.*;

public final class GridSkeletonGraphBuilder implements SkeletonGraphBuilder {

    @Override
    public MapGraph build(MapGenerationSpec spec) {
        List<int[]> colRanges = buildRoomRanges(spec.playableWidth, spec.skeletonSpacingX);
        List<int[]> rowRanges = buildRoomRanges(spec.playableHeight, spec.skeletonSpacingY);

        String[][] cellNodeId = new String[rowRanges.size()][colRanges.size()];
        List<RoomNode> rooms = new ArrayList<>();

        int roomCounter = 0;
        for (int row = 0; row < rowRanges.size(); row++) {
            for (int col = 0; col < colRanges.size(); col++) {
                int[] xr = colRanges.get(col);
                int[] yr = rowRanges.get(row);
                if (insideCentral(spec, xr[0], yr[0], xr[1], yr[1])) {
                    continue;
                }
                String id = "S_" + (roomCounter++);
                cellNodeId[row][col] = id;
                rooms.add(new RoomNode(id, RoomKind.STANDARD, xr[0], yr[0], xr[1], yr[1], col, row));
            }
        }

        List<int[]> centralCells = new ArrayList<>();
        for (int row = 0; row < rowRanges.size(); row++) {
            for (int col = 0; col < colRanges.size(); col++) {
                int[] xr = colRanges.get(col);
                int[] yr = rowRanges.get(row);
                if (insideCentral(spec, xr[0], yr[0], xr[1], yr[1])) {
                    centralCells.add(new int[]{col, row});
                }
            }
        }
        if (!centralCells.isEmpty()) {
            String centralId = "C_0";
            rooms.add(new RoomNode(
                    centralId,
                    RoomKind.CENTRAL,
                    spec.centralStartX,
                    spec.centralStartY,
                    spec.centralStartX + spec.centralWidth - 1,
                    spec.centralStartY + spec.centralHeight - 1,
                    -1,
                    -1));
            for (int[] cell : centralCells) {
                cellNodeId[cell[1]][cell[0]] = centralId;
            }
        }

        Map<String, RoomNode> roomById = new HashMap<>();
        for (RoomNode room : rooms) {
            roomById.put(room.id, room);
        }

        List<BorderEdge> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        for (int row = 0; row < rowRanges.size(); row++) {
            for (int col = 0; col < colRanges.size(); col++) {
                String here = cellNodeId[row][col];
                if (here == null) {
                    continue;
                }
                if (col + 1 < colRanges.size()) {
                    String right = cellNodeId[row][col + 1];
                    if (right != null && !here.equals(right)) {
                        int lineX = colRanges.get(col)[1] + 1;
                        RoomNode a = roomById.get(here);
                        RoomNode b = roomById.get(right);
                        int spanStart = Math.max(a.minY, b.minY);
                        int spanEnd = Math.min(a.maxY, b.maxY);
                        addEdge(edges, edgeKeys, here, right, BorderOrientation.VERTICAL, lineX, spanStart, spanEnd);
                    }
                }
                if (row + 1 < rowRanges.size()) {
                    String up = cellNodeId[row + 1][col];
                    if (up != null && !here.equals(up)) {
                        int lineY = rowRanges.get(row)[1] + 1;
                        RoomNode a = roomById.get(here);
                        RoomNode b = roomById.get(up);
                        int spanStart = Math.max(a.minX, b.minX);
                        int spanEnd = Math.min(a.maxX, b.maxX);
                        addEdge(edges, edgeKeys, here, up, BorderOrientation.HORIZONTAL, lineY, spanStart, spanEnd);
                    }
                }
            }
        }

        return new MapGraph(rooms, edges);
    }

    private static void addEdge(
            List<BorderEdge> edges,
            Set<String> edgeKeys,
            String roomA,
            String roomB,
            BorderOrientation orientation,
            int lineCoord,
            int spanStart,
            int spanEnd) {
        if (spanStart > spanEnd) {
            return;
        }
        String first = roomA.compareTo(roomB) <= 0 ? roomA : roomB;
        String second = roomA.compareTo(roomB) <= 0 ? roomB : roomA;
        String key = first + "|" + second + "|" + orientation + "|" + lineCoord + "|" + spanStart + "|" + spanEnd;
        if (!edgeKeys.add(key)) {
            return;
        }
        edges.add(new BorderEdge("E_" + edges.size(), roomA, roomB, orientation, lineCoord, spanStart, spanEnd));
    }

    private static boolean insideCentral(MapGenerationSpec spec, int minX, int minY, int maxX, int maxY) {
        int cx2 = spec.centralStartX + spec.centralWidth - 1;
        int cy2 = spec.centralStartY + spec.centralHeight - 1;
        return minX >= spec.centralStartX
                && maxX <= cx2
                && minY >= spec.centralStartY
                && maxY <= cy2;
    }

    private static List<int[]> buildRoomRanges(int playableSize, int skeletonSpacing) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        int roomSize = skeletonSpacing - 1;
        while (start < playableSize) {
            int end = Math.min(start + roomSize - 1, playableSize - 1);
            ranges.add(new int[]{start, end});
            start = end + 2;
        }
        return ranges;
    }
}
