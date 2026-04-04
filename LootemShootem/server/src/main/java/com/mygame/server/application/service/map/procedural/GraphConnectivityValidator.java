package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.BorderEdge;
import com.mygame.server.domain.model.proc.BorderState;
import com.mygame.server.domain.model.proc.MapGraph;
import com.mygame.server.domain.model.proc.RoomNode;

import java.util.*;

/**
 * Validates and repairs the connectivity of the room graph.
 * Ensures all rooms are reachable by carving doors or passages through blocking walls.
 * Uses breadth-first search to detect connected components and bridge them if needed.
 */
public final class GraphConnectivityValidator {

    /**
     * Checks if all rooms are traversable (connected graph).
     * Uses BFS from any starting room; returns true only if all rooms are visited.
     *
     * @param graph the room graph to check
     * @return true if all rooms are connected via traversable edges
     */
    public boolean isConnected(MapGraph graph) {
        if (graph.rooms().isEmpty()) {
            return true;
        }
        Set<String> visited = traverse(graph);
        return visited.size() == graph.rooms().size();
    }

    /**
     * Repairs the graph to full connectivity by forcibly opening blocked paths.
     * Repeatedly finds disconnected components and bridges them with traversable edges.
     * Priority: convert WALL edges that bridge components, then any edge that bridges.
     *
     * @param graph the room graph to repair
     * @param rng random generator for selecting which bridge to open when multiple exist
     */
    public void repairToConnected(MapGraph graph, Random rng) {
        if (graph.rooms().isEmpty()) {
            return;
        }
        while (true) {
            Set<String> visited = traverse(graph);
            if (visited.size() == graph.rooms().size()) {
                // All rooms connected.
                return;
            }
            // Find bridge edges that connect visited and unvisited components.
            List<BorderEdge> bridges = new ArrayList<>();
            for (BorderEdge edge : graph.edges()) {
                boolean a = visited.contains(edge.nodeA);
                boolean b = visited.contains(edge.nodeB);
                if (a != b && edge.state == BorderState.WALL) {
                    // Found a wall blocking a component bridge.
                    bridges.add(edge);
                }
            }
            if (bridges.isEmpty()) {
                // No walls are blocking; check any edge that bridges.
                for (BorderEdge edge : graph.edges()) {
                    boolean a = visited.contains(edge.nodeA);
                    boolean b = visited.contains(edge.nodeB);
                    if (a != b) {
                        bridges.add(edge);
                    }
                }
            }
            if (bridges.isEmpty()) {
                // Paradox; no bridges found but graph is disconnected.
                return;
            }
            // Pick a random bridge and open it.
            BorderEdge selected = bridges.get(rng.nextInt(bridges.size()));
            selected.state = BorderState.DOOR;
        }
    }

    /**
     * Breadth-first search to find all rooms reachable from any starting room.
     * Only traverses through edges marked as traversable (BorderState.isTraversable()).
     *
     * @param graph the room graph
     * @return set of reachable room IDs
     */
    private Set<String> traverse(MapGraph graph) {
        Iterator<RoomNode> it = graph.rooms().iterator();
        if (!it.hasNext()) {
            return Collections.emptySet();
        }

        String start = it.next().id;
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (BorderEdge edge : graph.incidentEdges(current)) {
                if (!edge.state.isTraversable()) {
                    continue;
                }
                String next = edge.other(current);
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }
}
