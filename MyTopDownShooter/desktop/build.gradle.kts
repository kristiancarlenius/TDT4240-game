plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-desktop")
}

application {
    mainClass.set("com.mygame.desktop.DesktopLauncher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}