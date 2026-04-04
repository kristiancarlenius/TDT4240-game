package com.mygame.server.data.map.procedural;

import com.mygame.server.domain.model.proc.RoomKind;
import com.mygame.server.domain.model.proc.RoomTemplate;
import com.mygame.server.domain.ports.map.RoomTemplateCatalogPort;

import java.util.*;

/**
 * Data layer catalog loader for room templates from JSON files.
 * Initializes templates for each room kind (standard, central) from classpath resources.
 */
public final class JsonRoomTemplateCatalog implements RoomTemplateCatalogPort {

    private final Map<RoomKind, List<RoomTemplate>> templates;

    public JsonRoomTemplateCatalog(JsonRoomParser parser) {
        Map<RoomKind, List<RoomTemplate>> map = new EnumMap<>(RoomKind.class);

        // Load standard room templates from JSON files.
        List<RoomTemplate> standard = new ArrayList<>();
        standard.add(parser.load("rooms/standard/empty_standard_room.json", RoomKind.STANDARD, "empty_standard_room"));
        standard.add(parser.load("rooms/standard/template_standard_15x10.json", RoomKind.STANDARD, "template_standard_15x10"));

        // Load central (boss) room templates.
        List<RoomTemplate> central = new ArrayList<>();
        central.add(parser.load("rooms/central/empty_central_room.json", RoomKind.CENTRAL, "empty_central_room"));

        map.put(RoomKind.STANDARD, Collections.unmodifiableList(standard));
        map.put(RoomKind.CENTRAL, Collections.unmodifiableList(central));

        this.templates = Collections.unmodifiableMap(map);
    }

    /**
     * Retrieves all templates for the given room kind.
     *
     * @param kind the room kind (STANDARD, CENTRAL)
     * @return immutable list of templates
     * @throws IllegalStateException if no templates defined for the kind
     */
    @Override
    public List<RoomTemplate> templatesFor(RoomKind kind) {
        List<RoomTemplate> list = templates.get(kind);
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("No templates available for room kind=" + kind);
        }
        return list;
    }
}
