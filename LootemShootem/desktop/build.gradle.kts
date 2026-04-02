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
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-desktop")
}

application {
    mainClass.set("com.mygame.desktop.DesktopLauncher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

// LibGDX internal files resolve relative to the working directory.
// Point the run task at the shared assets folder so textures are found.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}