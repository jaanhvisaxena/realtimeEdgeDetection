cmake_minimum_required(VERSION 3.22)
project(native-lib)

# ✅ Point to your OpenCV Android SDK
# Change this path if your OpenCV SDK is in a different location
set(OpenCV_DIR "C:/Users/jaanh/Downloads/opencv-4.5.5-android-sdk/OpenCV-android-sdk/sdk/native/jni")

# ✅ Find OpenCV
find_package(OpenCV REQUIRED)

# ✅ Native library
add_library(
        native-lib
        SHARED
        native-lib.cpp
)

# ✅ Include OpenCV headers
include_directories(${OpenCV_INCLUDE_DIRS})

# ✅ Link OpenCV and Android logging
target_link_libraries(
        native-lib
        ${OpenCV_LIBS}
        log
)
