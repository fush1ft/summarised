import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// M0 lab: a throwaway app that answers the three questions the real app depends
// on (power-button trigger, Czech ASR speed/quality, Czech LLM speed/quality) on
// the reference phone. Not shipped; see docs/M0.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.fush1ft.summarised.lab"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "io.github.fush1ft.summarised.lab"
        // The lab only has to run on the reference phone (Pixel 10a, Android 16+).
        minSdk = 33
        targetSdk = 37
        // Every CI build is a strict upgrade of the previous one.
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "m0-lab"

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // Inference at -O0 would make every benchmark meaningless, so the
                // native code is optimised even in the debug APK.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    // One fixed, deliberately public key for the lab. Without it every CI run
    // signs with a freshly generated debug key, and Android refuses to install a
    // new build over the previous one ("App not installed"). It protects
    // nothing and must never sign the real app.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Throwaway spike: report lint findings, never block CI on them.
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
