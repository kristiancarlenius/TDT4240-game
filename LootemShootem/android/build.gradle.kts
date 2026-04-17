plugins {
    id("com.android.application") version "8.5.2"
    kotlin("android") version "1.9.24"
}

val natives by configurations.creating

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "com.mygame.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mygame.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs(rootProject.file("assets"))
            jniLibs.srcDirs(layout.buildDirectory.dir("generated/jniLibs"))
        }
    }
}

val copyAndroidNatives by tasks.registering(Sync::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    natives.files.forEach { jar ->
        val abi = when {
            jar.name.contains("natives-arm64-v8a") -> "arm64-v8a"
            jar.name.contains("natives-armeabi-v7a") -> "armeabi-v7a"
            jar.name.contains("natives-x86_64") -> "x86_64"
            jar.name.contains("natives-x86") -> "x86"
            else -> null
        }
        if (abi != null) {
            from(zipTree(jar)) {
                include("*.so")
                into(abi)
            }
        }
    }
    into(layout.buildDirectory.dir("generated/jniLibs"))
}

tasks.named("preBuild") {
    dependsOn(copyAndroidNatives)
}

dependencies {
    implementation(project(":core"))

    implementation("com.badlogicgames.gdx:gdx:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")

    natives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86_64")
}
