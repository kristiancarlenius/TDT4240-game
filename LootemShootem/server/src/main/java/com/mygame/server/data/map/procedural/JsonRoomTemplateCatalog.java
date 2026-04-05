package com.mygame.server.data.map.procedural;

import com.mygame.server.domain.model.proc.RoomKind;
import com.mygame.server.domain.model.proc.RoomTemplate;
import com.mygame.server.domain.ports.map.RoomTemplateCatalogPort;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Auto-loading catalog for room templates stored as JSON resources.
 *
 * Discovery rules:
 * - Standard rooms are loaded from rooms/standard
 * - Central rooms are loaded from rooms/central
 * - Only files ending in .json are considered
 * - Files are loaded in sorted order for deterministic behavior
 */
public final class JsonRoomTemplateCatalog implements RoomTemplateCatalogPort {

    private static final String STANDARD_FOLDER = "rooms/standard";
    private static final String CENTRAL_FOLDER = "rooms/central";

    private final Map<RoomKind, List<RoomTemplate>> templates;

    public JsonRoomTemplateCatalog(JsonRoomParser parser) {
        Map<RoomKind, List<RoomTemplate>> byKind = new EnumMap<>(RoomKind.class);
        byKind.put(RoomKind.STANDARD, Collections.unmodifiableList(loadTemplatesForKind(parser, STANDARD_FOLDER, RoomKind.STANDARD)));
        byKind.put(RoomKind.CENTRAL, Collections.unmodifiableList(loadTemplatesForKind(parser, CENTRAL_FOLDER, RoomKind.CENTRAL)));
        this.templates = Collections.unmodifiableMap(byKind);
    }

    /**
     * Loads all valid templates for one room kind from a single folder.
     * Invalid JSON files are skipped with a warning instead of failing startup.
     */
    private static List<RoomTemplate> loadTemplatesForKind(JsonRoomParser parser, String folder, RoomKind kind) {
        List<String> resourcePaths = discoverJsonResources(folder);
        List<RoomTemplate> loaded = new ArrayList<>();

        for (String resourcePath : resourcePaths) {
            String templateId = toTemplateId(resourcePath);
            try {
                loaded.add(parser.load(resourcePath, kind, templateId));
            } catch (RuntimeException e) {
                // Keep loading other templates if one file is malformed.
                System.err.println("[MAP] Skipping invalid room template: " + resourcePath + " (" + e.getMessage() + ")");
            }
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("No valid templates found in " + folder + " for room kind=" + kind);
        }
        return loaded;
    }

    /**
     * Discovers all .json resources in a classpath folder.
     * Supports both IDE/dev runs (file URLs) and packaged JAR runs (jar URLs).
     */
    private static List<String> discoverJsonResources(String folder) {
        Set<String> discovered = new TreeSet<>(); // sorted + de-duplicated
        ClassLoader classLoader = JsonRoomTemplateCatalog.class.getClassLoader();

        try {
            Enumeration<URL> urls = classLoader.getResources(folder);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    collectFromFileUrl(folder, url, discovered);
                } else if ("jar".equals(url.getProtocol())) {
                    collectFromJarUrl(folder, url, discovered);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan templates in folder: " + folder, e);
        }

        return new ArrayList<>(discovered);
    }

    /**
     * Collects .json files from a folder when resources are available directly on the filesystem.
     */
    private static void collectFromFileUrl(String folder, URL folderUrl, Set<String> out) {
        try {
            Path dir = Paths.get(folderUrl.toURI());
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (var paths = Files.list(dir)) {
                paths
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".json"))
                        .forEach(name -> out.add(folder + "/" + name));
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to read room template directory: " + folder, e);
        }
    }

    /**
     * Collects .json files from a folder when resources are inside a packaged JAR.
     */
    private static void collectFromJarUrl(String folder, URL folderUrl, Set<String> out) {
        try {
            URLConnection connection = folderUrl.openConnection();
            if (!(connection instanceof JarURLConnection)) {
                return;
            }
            String prefix = folder + "/";
            try (JarFile jar = ((JarURLConnection) connection).getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (name.startsWith(prefix) && name.endsWith(".json")) {
                        out.add(name);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read room templates from JAR: " + folder, e);
        }
    }

    private static String toTemplateId(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
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
