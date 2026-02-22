package com.mygame.shared.util

object Id {
    fun newId(): String = java.util.UUID.randomUUID().toString()
}
