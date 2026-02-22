plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
}

application {
    mainClass.set("com.mygame.server.ServerMain")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}