plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))

    // libGDX core API
    implementation("com.badlogicgames.gdx:gdx:1.12.1")

    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}