plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("org.java-websocket:Java-WebSocket:1.6.0") // WebSocket client
    // libGDX deps are usually already here via root gradle; keep what you already have
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}