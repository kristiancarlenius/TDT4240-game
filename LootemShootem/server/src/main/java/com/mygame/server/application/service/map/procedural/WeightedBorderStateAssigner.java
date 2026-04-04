package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.*;

import java.util.*;

/**
 * Assigns border states to room boundaries using a weighted random approach.
 * Strategy:
 * 1. Build a minimum spanning tree (MST) of the room graph to ensure connectivity
 * 2. MST edges get doors (75%) or open passages (25%), guaranteeing traversability
 * 3. Non-MST edges are assigned probabilistically: wall, window, door, or open based on weights
 * 4. If any rooms become unreachable, repair by forcibly opening walls to bridge them
 */
public final class WeightedBorderStateAssigner implements BorderStateAssigner {

    private final GraphConnectivityValidator connectivityValidator;

    /**
     * Creates a border state assigner with the given connectivity validator.
     *
     * @param connectivityValidator ensures the final graph remains traversable
     */
    public WeightedBorderStateAssigner(GraphConnectivityValidator connectivityValidator) {
        this.connectivityValidator = connectivityValidator;
    }

    /**
     * Assigns border states to ensure connectivity while respecting weighted probabilities.
     * 
     * Algorithm:
     * 1. Initialize all edges as WALL
     * 2. Use Kruskal's algorithm to build an MST (ensures all rooms reachable)
     * 3. MST edges: 75% DOOR, 25% NONE (open)
     * 4. Non-MST edges: sample from weighted distribution
     * 5. Repair any disconnected components by forcing DOOR on bridge edges
     *
     * @param graph room graph with uninitialized edge states
     * @param rng seeded random generator
     * @param spec generation spec with border weighting parameters
     */
    @Override
    public void assign(MapGraph graph, Random rng, MapGenerationSpec spec) {
        // Initialize all edges as walls.
        List<RoomNode> rooms = new ArrayList<>(graph.rooms());
        List<BorderEdge> edges = new ArrayList<>(graph.edges());

        for (BorderEdge edge : edges) {
            edge.state = BorderState.WALL;
        }

        if (rooms.isEmpty() || edges.isEmpty()) {
            return;
        }

        // Step 1: Build minimum spanning tree using Kruskal's algorithm.
        // Shuffle edges for randomness; edges in MST are guaranteed traversable.
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
                // This edge connects two previously-unconnected components.
                edge.state = rng.nextDouble() < 0.75 ? BorderState.DOOR : BorderState.NONE;
                spanningTreeEdges.add(edge.id);
            }
        }

        // Step 2: Probabilistically assign non-MST edge states.
        for (BorderEdge edge : edges) {
            if (spanningTreeEdges.contains(edge.id)) {
                continue;
            }
            edge.state = sampleWeighted(spec, rng);
        }

        // Step 3: Ensure full connectivity; repair any isolated components.
        connectivityValidator.repairToConnected(graph, rng);
    }

    /**
     * Samples a border state from the weighted distribution defined in the spec.
     * Uses cumulative probability: if roll < wallWeight, return WALL; etc.
     */
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

    /**
     * Classic disjoint set (union-find) with path compression and union by rank.
     * Used in Kruskal's algorithm to detect connectivity in the MST building phase.
     */
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
