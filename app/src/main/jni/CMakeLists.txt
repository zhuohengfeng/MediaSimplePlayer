#
# Created by hengfeng zhuo on 2019/3/25.
#
cmake_minimum_required(VERSION 3.4.1)

include_directories(${CMAKE_SOURCE_DIR}/include)

add_library(
        native-lib
        SHARED
        ${CMAKE_SOURCE_DIR}/native-lib.cpp)


add_library( libyuv
        STATIC
        IMPORTED)
set_target_properties( libyuv
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/lib/libyuv.a)

find_library( # Sets the name of the path variable.
        log-lib
        log)

target_link_libraries( # Specifies the target library.
        native-lib
        libyuv
        ${log-lib})