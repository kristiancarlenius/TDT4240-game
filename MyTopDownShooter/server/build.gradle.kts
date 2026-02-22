plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("org.java-websocket:Java-WebSocket:1.6.0") // WebSocket server/client lib
}

application {
    mainClass.set("com.mygame.server.ServerMain")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}