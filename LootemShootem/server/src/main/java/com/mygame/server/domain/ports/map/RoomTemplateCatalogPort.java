package com.mygame.server.domain.ports.map;

import com.mygame.server.domain.model.proc.RoomKind;
import com.mygame.server.domain.model.proc.RoomTemplate;

import java.util.List;

public interface RoomTemplateCatalogPort {
    List<RoomTemplate> templatesFor(RoomKind kind);
}
