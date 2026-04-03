package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.*;

import java.util.*;

public final class WeightedBorderStateAssigner implements BorderStateAssigner {

    private final GraphConnectivityValidator connectivityValidator;

    public WeightedBorderStateAssigner(GraphConnectivityValidator connectivityValidator) {
        this.connectivityValidator = connectivityValidator;
    }

    @Override
    public void assign(MapGraph graph, Random rng, MapGenerationSpec spec) {
        List<RoomNode> rooms = new ArrayList<>(graph.rooms());
        List<BorderEdge> edges = new ArrayList<>(graph.edges());

        for (BorderEdge edge : edges) {
            edge.state = BorderState.WALL;
        }

        if (rooms.isEmpty() || edges.isEmpty()) {
            return;
        }

        Collections.shuffle(edges, rng);
        DisjointSet dsu = new DisjointSet(rooms.size());
        Map<String, Integer> roomIndex = new HashMap<>();
        for (int i = 0; i < rooms.size(); i++) {
            roomIndex.put(rooms.get(i).id, i);
        }

        Set<String> spanningTreeEdges = new HashSet<>();
        for (BorderEdge edge : edges) {
            int a = roomIndex.get(edge.nodeA);
            int b = roomIndex.get(edge.nodeB);
            if (dsu.union(a, b)) {
                edge.state = rng.nextDouble() < 0.75 ? BorderState.DOOR : BorderState.NONE;
                spanningTreeEdges.add(edge.id);
            }
        }

        for (BorderEdge edge : edges) {
            if (spanningTreeEdges.contains(edge.id)) {
                continue;
            }
            edge.state = sampleWeighted(spec, rng);
        }

        connectivityValidator.repairToConnected(graph, rng);
    }

    private static BorderState sampleWeighted(MapGenerationSpec spec, Random rng) {
        double total = spec.wallWeight + spec.windowWeight + spec.doorWeight + spec.noneWeight;
        if (total <= 0) {
            return BorderState.WALL;
        }
        double roll = rng.nextDouble() * total;
        if ((roll -= spec.wallWeight) < 0) {
            return BorderState.WALL;
        }
        if ((roll -= spec.windowWeight) < 0) {
            return BorderState.WINDOW;
        }
        if ((roll -= spec.doorWeight) < 0) {
            return BorderState.DOOR;
        }
        return BorderState.NONE;
    }

    private static final class DisjointSet {
        private final int[] parent;
        private final int[] rank;

        private DisjointSet(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return false;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[rb] < rank[ra]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
            return true;
        }
    }
}
