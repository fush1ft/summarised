import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's on-device inference core: Parakeet/Whisper speech recognition and
// llama.cpp, built from the pinned submodules in third_party/ against one ggml.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.fush1ft.summarised.core.inference"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 33
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // Optimised even in debug builds: unoptimised inference is unusable.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
