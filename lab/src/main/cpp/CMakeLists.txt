# Native side of the M0 lab: whisper.cpp (Whisper + Parakeet) and llama.cpp built
# against ONE copy of ggml — llama.cpp's — so the app ships a single inference core.
cmake_minimum_required(VERSION 3.22.1)
project(summarised_lab C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Belt and braces with the Gradle argument: whatever build type AGP picks, ggml is
# compiled with optimisation, or the benchmark numbers mean nothing.
if(NOT CMAKE_BUILD_TYPE STREQUAL "Release")
    foreach(lang C CXX)
        set(CMAKE_${lang}_FLAGS_DEBUG "-O3 -DNDEBUG")
        set(CMAKE_${lang}_FLAGS_RELWITHDEBINFO "-O3 -DNDEBUG")
    endforeach()
endif()

set(THIRD_PARTY ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party)

# Everything static, so the APK carries one .so and no symbol clashes.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)

# No OpenMP: the NDK's libomp.so is not packaged automatically, and ggml's own
# thread pool is fine on phones.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Tensor G4/G5 (Pixel 10a) are Armv9.2: dot-product and int8 matmul are there.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)
endif()

add_subdirectory(${THIRD_PARTY}/llama.cpp build-llama)

set(W ${THIRD_PARTY}/whisper.cpp)
add_library(whisper STATIC ${W}/src/whisper.cpp ${W}/src/aneforge/whisper-aneforge.cpp)
target_include_directories(whisper PUBLIC ${W}/src ${W}/include)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.6")
target_link_libraries(whisper PUBLIC ggml)

add_library(parakeet STATIC ${W}/src/parakeet.cpp)
target_include_directories(parakeet PUBLIC ${W}/src ${W}/include)
target_compile_definitions(parakeet PRIVATE PARAKEET_VERSION="1.9.6")
target_link_libraries(parakeet PUBLIC ggml)

add_library(summarised_lab SHARED lab_jni.cpp)
target_link_libraries(summarised_lab PRIVATE whisper parakeet llama ggml $<$<BOOL:${ANDROID}>:android> $<$<BOOL:${ANDROID}>:log>)
# Keep only the JNI entry points exported.
target_link_options(summarised_lab PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
