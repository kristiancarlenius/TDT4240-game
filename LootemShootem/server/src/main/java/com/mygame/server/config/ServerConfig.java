package com.mygame.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Immutable server configuration loaded from {@code server.conf} on the classpath.
 * Keeping config external means changing port/tick-rate needs no recompile.
 */
public final class ServerConfig {

    public final int port;
    public final int tickHz;
    public final int maxPlayers;
    public final String mapMode;
    public final String mapId;
    public final long mapSeed;

    private ServerConfig(int port, int tickHz, int maxPlayers, String mapMode, String mapId, long mapSeed) {
        this.port       = port;
        this.tickHz     = tickHz;
        this.maxPlayers = maxPlayers;
        this.mapMode    = mapMode;
        this.mapId      = mapId;
        this.mapSeed    = mapSeed;
    }

    public static ServerConfig load() {
        Properties props = new Properties();
        try (InputStream in = ServerConfig.class.getClassLoader()
                .getResourceAsStream("server.conf")) {
            if (in != null) {
                props.load(in);
            } else {
                System.err.println("[CONFIG] server.conf not found on classpath — using defaults");
            }
        } catch (IOException e) {
            System.err.println("[CONFIG] Failed to read server.conf: " + e.getMessage());
        }

        int port       = Integer.parseInt(props.getProperty("port",       "8080"));
        int tickHz     = Integer.parseInt(props.getProperty("tickHz",     "30"));
        int maxPlayers = Integer.parseInt(props.getProperty("maxPlayers", "8"));
        String mapMode = props.getProperty("mapMode", "static").trim().toLowerCase();
        String mapId = props.getProperty("mapId", "map01").trim();
        long mapSeed = Long.parseLong(props.getProperty("mapSeed", "4240"));

        System.out.println("[CONFIG] port=" + port + "  tickHz=" + tickHz
            + "  maxPlayers=" + maxPlayers
            + "  mapMode=" + mapMode
            + "  mapId=" + mapId
            + "  mapSeed=" + mapSeed);
        return new ServerConfig(port, tickHz, maxPlayers, mapMode, mapId, mapSeed);
    }
}
