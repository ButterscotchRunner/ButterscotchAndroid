plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "net.perfectdreams.butterscotch"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.perfectdreams.butterscotch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Start small. Add x86_64 if you want to run in the emulator on an Intel/AMD host.
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DPLATFORM=android",
                    "-DENABLE_GLES=ON",
                    "-DENABLE_MODERN_GL=ON",
                    "-DENABLE_LEGACY_GL=OFF",
                    "-DAUDIO_BACKEND=miniaudio"
                )
                cppFlags += "-std=c++17"
                cFlags += "-std=gnu99"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            // Point straight at the Butterscotch repo's root CMakeLists.txt.
            // Adjust if your checkout layout differs.
            path = file("../../Butterscotch/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Keep music/data uncompressed: faster first-launch extraction, and Undertale's .ogg files are
    // already compressed so APK size gain from re-compressing is negligible.
    androidResources {
        noCompress += listOf("ogg", "win")
    }
}

// Mirror the Butterscotch repo's `undertale/` folder into a generated assets dir so it ends up at
// `assets/undertale/...` inside the APK without forcing the user to duplicate ~270 MB into the
// app's own assets/ folder. Skips cleanly if the source folder is missing.
val butterscotchUndertaleDir = file("../../Butterscotch/undertale")
val butterscotchAssetsStagingDir = layout.buildDirectory.get().asFile.resolve("generated/butterscotchAssets")

val stageButterscotchAssets = tasks.register<Sync>("stageButterscotchAssets") {
    into(butterscotchAssetsStagingDir.resolve("undertale"))
    if (butterscotchUndertaleDir.isDirectory) {
        from(butterscotchUndertaleDir)
    } else {
        doFirst {
            logger.warn("Butterscotch undertale/ folder not found at $butterscotchUndertaleDir; the app will fail at runtime until you add it.")
        }
    }
}

// Plain File (not Provider) because AGP 9 forbids Provider here. The task dependency is wired
// below via tasks.matching so AGP's merge*Assets task always runs the staging copy first.
android.sourceSets["main"].assets.srcDir(butterscotchAssetsStagingDir)

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(stageButterscotchAssets) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}