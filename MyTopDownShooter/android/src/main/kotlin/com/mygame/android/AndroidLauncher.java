package com.mygame.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.mygame.client.GameClientApp

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()
        // Replace the URL below with your server's address (e.g. ws://192.168.x.x:8080/ws)
        initialize(GameClientApp("ws://10.0.2.2:8080/ws", "Player"), config)
    }
}
