package com.mygame.server.data.weapon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mygame.server.domain.model.WeaponSpec;
import com.mygame.shared.dto.WeaponType;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads weapon definitions from a classpath JSON file and produces a
 * populated {@link WeaponRegistry}.
 *
 * JSON format: {"weapons": [{...}, ...]}
 * Each entry maps to a {@link WeaponSpec}.
 */
public final class WeaponLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static WeaponRegistry load(String classpathResource) {
        String path = classpathResource.startsWith("/") ? classpathResource.substring(1) : classpathResource;
        try (InputStream in = WeaponLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Weapons resource not found on classpath: " + path);
            }
            JsonNode root    = MAPPER.readTree(in);
            JsonNode weapons = root.get("weapons");
            if (weapons == null || !weapons.isArray()) {
                throw new IllegalArgumentException("weapons.json missing 'weapons' array");
            }

            List<WeaponSpec> specs = new ArrayList<>();
            for (JsonNode w : weapons) {
                WeaponType type = WeaponType.valueOf(w.get("type").asText());
                specs.add(new WeaponSpec(
                        type,
                        (float) w.get("damage").asDouble(),
                        (float) w.get("projectileSpeed").asDouble(),
                        (float) w.get("projectileRadius").asDouble(),
                        (float) w.get("ttlSeconds").asDouble(),
                        (float) w.get("fireRate").asDouble(),
                        w.get("maxAmmo").asInt(),
                        w.get("pellets").asInt(),
                        (float) w.get("spreadRadians").asDouble()
                ));
            }
            return new WeaponRegistry(specs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load weapons: " + classpathResource, e);
        }
    }
}
