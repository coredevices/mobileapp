cmake_minimum_required(VERSION 3.18.1)
project(cactus_cpu LANGUAGES C)
add_library(cactus_cpu SHARED cactus_cpu.c)
# Support Android devices with 16KB memory pages
target_link_options(cactus_cpu PRIVATE "-Wl,-z,max-page-size=16384")
