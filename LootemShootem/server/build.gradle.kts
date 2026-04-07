plugins {
    application
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
}

application {
    mainClass.set("com.mygame.server.util.ServerMain")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}