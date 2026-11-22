cmake_minimum_required(VERSION 3.22.1)
project(farcast_re_lab)

# Each of these is a git submodule pointing at the project's own upstream
# repo, pinned to a specific commit (pin it, don't float on a branch — an
# upstream change could silently alter what this builds):
#   git submodule add https://github.com/capstone-engine/capstone.git app/src/main/cpp/third_party/capstone
#   git submodule add https://github.com/keystone-engine/keystone.git  app/src/main/cpp/third_party/keystone
#   git submodule add https://github.com/unicorn-engine/unicorn.git    app/src/main/cpp/third_party/unicorn
# None of these are checked into this scaffold — see RE_EMU_LAB.md for why.

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/capstone/CMakeLists.txt")
    set(CAPSTONE_BUILD_SHARED ON CACHE BOOL "" FORCE)
    add_subdirectory(third_party/capstone)
endif()

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/keystone/CMakeLists.txt")
    add_subdirectory(third_party/keystone)
endif()

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/unicorn/CMakeLists.txt")
    set(UNICORN_BUILD_SHARED ON CACHE BOOL "" FORCE)
    add_subdirectory(third_party/unicorn)
endif()

# JNI bridge — the actual Kotlin-facing surface. Intentionally not
# implemented beyond the function signatures: the body of each function is
# exactly where real verification against the built libraries above has to
# happen, not something to fill with placeholder logic that looks done.
add_library(farcast_re_bridge SHARED jni_bridge.cpp)
find_library(log-lib log)
target_link_libraries(farcast_re_bridge ${log-lib})
