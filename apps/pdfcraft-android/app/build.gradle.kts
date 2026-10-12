plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.storyteller.pdfcraft.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.storyteller.pdfcraft.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-prototype"
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { jniLibs.useLegacyPackaging = false }
}

// Do not produce a misleading APK with an absent/empty native engine.
tasks.register("verifyNativeLibraries") {
    doLast {
        val required = listOf(
            file("src/main/jniLibs/arm64-v8a/libpdfcraft_android_native.so"),
            file("src/main/jniLibs/x86_64/libpdfcraft_android_native.so"),
        )
        val missing = required.filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Rust JNI libraries are missing: ${missing.joinToString()}. " +
                    "Run scripts/build-native.sh before assembling the APK."
            )
        }
    }
}
tasks.named("preBuild").configure { dependsOn("verifyNativeLibraries") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
