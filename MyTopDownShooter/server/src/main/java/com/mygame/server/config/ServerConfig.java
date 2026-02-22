package com.mygame.server.config

data class ServerConfig(
    val port: Int = 8080,
    val tickHz: Int = 20,
    val maxPlayers: Int = 8
)
