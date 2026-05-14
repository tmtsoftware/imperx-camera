cmake_minimum_required(VERSION 3.18)
project(imperx_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_library(imperx_bridge SHARED
  src/imperx_bridge.cpp
)

target_include_directories(imperx_bridge
  PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include
  PRIVATE /opt/IpxCameraSDK-1.5.0.83/inc
)

target_link_directories(imperx_bridge PRIVATE /opt/IpxCameraSDK-1.5.0.83/lib/Linux64_x64)
target_link_libraries(imperx_bridge PRIVATE IpxCameraApi pthread)
target_link_options(imperx_bridge PRIVATE "-Wl,-rpath,/opt/IpxCameraSDK-1.5.0.83/lib/Linux64_x64")
