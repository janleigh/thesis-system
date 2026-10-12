cmake_minimum_required(VERSION 3.22.1)
project("keystroke_engine")

# Include Eigen headers
# Make sure you have a folder named 'eigen' inside 'cpp' containing the Eigen headers
include_directories(${CMAKE_SOURCE_DIR}/eigen)

add_library(
        keystroke_engine
        SHARED
        native-lib.cpp
        svm.cpp
)

find_library(log-lib log)

target_link_libraries(
        keystroke_engine
        ${log-lib}
)