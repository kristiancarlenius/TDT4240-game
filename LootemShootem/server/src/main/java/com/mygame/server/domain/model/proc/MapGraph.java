package com.mygame.server.domain.model.proc;

import java.util.*;

public final class MapGraph {

    private final Map<String, RoomNode> roomById;
    private final List<BorderEdge> edges;
    private final Map<String, List<BorderEdge>> adjacency;

    public MapGraph(List<RoomNode> rooms, List<BorderEdge> edges) {
        Map<String, RoomNode> byId = new LinkedHashMap<>();
        for (RoomNode room : rooms) {
            byId.put(room.id, room);
        }
        this.roomById = Collections.unmodifiableMap(byId);
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));

        Map<String, List<BorderEdge>> adj = new HashMap<>();
        for (RoomNode room : rooms) {
            adj.put(room.id, new ArrayList<>());
        }
        for (BorderEdge edge : edges) {
            adj.computeIfAbsent(edge.nodeA, key -> new ArrayList<>()).add(edge);
            adj.computeIfAbsent(edge.nodeB, key -> new ArrayList<>()).add(edge);
        }
        Map<String, List<BorderEdge>> frozen = new HashMap<>();
        for (Map.Entry<String, List<BorderEdge>> e : adj.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        this.adjacency = Collections.unmodifiableMap(frozen);
    }

    public Collection<RoomNode> rooms() {
        return roomById.values();
    }

    public List<BorderEdge> edges() {
        return edges;
    }

    public RoomNode room(String roomId) {
        return roomById.get(roomId);
    }

    public List<BorderEdge> incidentEdges(String roomId) {
        return adjacency.getOrDefault(roomId, Collections.emptyList());
    }

    public List<String> traversableNeighbors(String roomId) {
        List<String> out = new ArrayList<>();
        for (BorderEdge edge : incidentEdges(roomId)) {
            if (edge.state.isTraversable()) {
                out.add(edge.other(roomId));
            }
        }
        return out;
    }
}
