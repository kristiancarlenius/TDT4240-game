package com.mygame.server.util

object IdGenerator {
    fun newId(): String = java.util.UUID.randomUUID().toString()
}
