package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.BorderEdge;
import com.mygame.server.domain.model.proc.BorderState;
import com.mygame.server.domain.model.proc.MapGraph;
import com.mygame.server.domain.model.proc.RoomNode;

import java.util.*;

public final class GraphConnectivityValidator {

    public boolean isConnected(MapGraph graph) {
        if (graph.rooms().isEmpty()) {
            return true;
        }
        Set<String> visited = traverse(graph);
        return visited.size() == graph.rooms().size();
    }

    public void repairToConnected(MapGraph graph, Random rng) {
        if (graph.rooms().isEmpty()) {
            return;
        }
        while (true) {
            Set<String> visited = traverse(graph);
            if (visited.size() == graph.rooms().size()) {
                return;
            }
            List<BorderEdge> bridges = new ArrayList<>();
            for (BorderEdge edge : graph.edges()) {
                boolean a = visited.contains(edge.nodeA);
                boolean b = visited.contains(edge.nodeB);
                if (a != b && edge.state == BorderState.WALL) {
                    bridges.add(edge);
                }
            }
            if (bridges.isEmpty()) {
                for (BorderEdge edge : graph.edges()) {
                    boolean a = visited.contains(edge.nodeA);
                    boolean b = visited.contains(edge.nodeB);
                    if (a != b) {
                        bridges.add(edge);
                    }
                }
            }
            if (bridges.isEmpty()) {
                return;
            }
            BorderEdge selected = bridges.get(rng.nextInt(bridges.size()));
            selected.state = BorderState.DOOR;
        }
    }

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
