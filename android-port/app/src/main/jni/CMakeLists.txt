cmake_minimum_required(VERSION 3.22.1)

project(SRB2Android LANGUAGES C CXX)

# ============================================================
# SRB2 Android Native Build
# Uses SDL2 as the platform abstraction layer
# ============================================================

set(SRB2_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../src")
set(SRB2_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")

# --- SDL2 ---
# SDL2 source must be placed at android-port/app/src/main/jni/SDL2/
# Clone it: git clone --branch release-2.32.10 --depth 1 https://github.com/libsdl-org/SDL.git SDL2
set(SDL2_DIR "${CMAKE_CURRENT_SOURCE_DIR}/SDL2")
if(NOT EXISTS "${SDL2_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "SDL2 source not found at ${SDL2_DIR}\n"
        "Please clone SDL2:\n"
        "  cd android-port/app/src/main/jni\n"
        "  git clone --branch release-2.28.5 --depth 1 https://github.com/libsdl-org/SDL.git SDL2"
    )
endif()
set(SDL2_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
set(SDL2_DISABLE_UNINSTALL ON CACHE BOOL "" FORCE)
add_subdirectory(${SDL2_DIR} SDL2-build)

# --- SDL2_mixer ---
# Clone it: git clone --branch release-2.6.3 --depth 1 https://github.com/libsdl-org/SDL_mixer.git SDL2_mixer
set(SDL2_MIXER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/SDL2_mixer")
set(HAVE_SDL2_MIXER OFF)
if(EXISTS "${SDL2_MIXER_DIR}/CMakeLists.txt")
    set(SDL2MIXER_VENDORED ON CACHE BOOL "" FORCE)
    set(SDL2MIXER_FLAC OFF CACHE BOOL "" FORCE)
    set(SDL2MIXER_MOD OFF CACHE BOOL "" FORCE)
    set(SDL2MIXER_MIDI OFF CACHE BOOL "" FORCE)
    set(SDL2MIXER_OPUS OFF CACHE BOOL "" FORCE)
    set(SDL2MIXER_OGG ON CACHE BOOL "" FORCE)
    set(SDL2MIXER_MP3 ON CACHE BOOL "" FORCE)
    set(SDL2MIXER_INSTALL OFF CACHE BOOL "" FORCE)
    set(SDL2MIXER_SAMPLES OFF CACHE BOOL "" FORCE)
    # Prevent SDL2_mixer from generating install(EXPORT) rules that reference SDL2
    set(CMAKE_SKIP_INSTALL_RULES ON)
    add_subdirectory(${SDL2_MIXER_DIR} SDL2_mixer-build)
    set(CMAKE_SKIP_INSTALL_RULES OFF)
    set(HAVE_SDL2_MIXER ON)
    message(STATUS "SDL2_mixer found - audio enabled")
else()
    message(WARNING
        "SDL2_mixer not found at ${SDL2_MIXER_DIR}\n"
        "Audio will use basic SDL2 audio. For full music support:\n"
        "  cd android-port/app/src/main/jni\n"
        "  git clone --branch release-2.6.3 --depth 1 https://github.com/libsdl-org/SDL_mixer.git SDL2_mixer"
    )
endif()

# --- libpng (bundled source) ---
set(LIBPNG_SRC_DIR "${SRB2_ROOT_DIR}/libs/libpng-src")

# Copy the prebuilt pnglibconf.h so png.h can find it
file(COPY "${LIBPNG_SRC_DIR}/scripts/pnglibconf.h.prebuilt"
     DESTINATION "${CMAKE_CURRENT_BINARY_DIR}/libpng")
file(RENAME "${CMAKE_CURRENT_BINARY_DIR}/libpng/pnglibconf.h.prebuilt"
     "${CMAKE_CURRENT_BINARY_DIR}/libpng/pnglibconf.h")

set(LIBPNG_SOURCES
    ${LIBPNG_SRC_DIR}/png.c
    ${LIBPNG_SRC_DIR}/pngerror.c
    ${LIBPNG_SRC_DIR}/pngget.c
    ${LIBPNG_SRC_DIR}/pngmem.c
    ${LIBPNG_SRC_DIR}/pngpread.c
    ${LIBPNG_SRC_DIR}/pngread.c
    ${LIBPNG_SRC_DIR}/pngrio.c
    ${LIBPNG_SRC_DIR}/pngrtran.c
    ${LIBPNG_SRC_DIR}/pngrutil.c
    ${LIBPNG_SRC_DIR}/pngset.c
    ${LIBPNG_SRC_DIR}/pngtrans.c
    ${LIBPNG_SRC_DIR}/pngwio.c
    ${LIBPNG_SRC_DIR}/pngwrite.c
    ${LIBPNG_SRC_DIR}/pngwtran.c
    ${LIBPNG_SRC_DIR}/pngwutil.c
    # ARM NEON optimizations
    ${LIBPNG_SRC_DIR}/arm/arm_init.c
    ${LIBPNG_SRC_DIR}/arm/filter_neon_intrinsics.c
    ${LIBPNG_SRC_DIR}/arm/palette_neon_intrinsics.c
)

# Build libpng as a static library
add_library(png_static STATIC ${LIBPNG_SOURCES})
target_include_directories(png_static PUBLIC
    ${LIBPNG_SRC_DIR}
    ${CMAKE_CURRENT_BINARY_DIR}/libpng
)
target_link_libraries(png_static PRIVATE z)
message(STATUS "libpng (bundled 1.6.37) enabled for Android build")

# --- libcurl (for master server) ---
set(CURL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/curl")
set(MBEDTLS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/mbedtls")
set(HAVE_CURL OFF)

# Build mbedTLS first (needed by curl for HTTPS)
if(EXISTS "${MBEDTLS_DIR}/CMakeLists.txt")
    set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
    set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
    set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
    add_subdirectory(${MBEDTLS_DIR} mbedtls-build)
    message(STATUS "mbedTLS enabled for HTTPS support")
endif()

if(EXISTS "${CURL_DIR}/CMakeLists.txt")
    # Use our custom FindMbedTLS.cmake that works with add_subdirectory targets
    list(INSERT CMAKE_MODULE_PATH 0 "${CMAKE_CURRENT_SOURCE_DIR}")
    # Build curl with HTTPS via mbedTLS for SRB2 master server
    set(BUILD_CURL_EXE OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
    set(CURL_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
    # SSL: use mbedTLS, disable all others
    set(CURL_USE_OPENSSL OFF CACHE BOOL "" FORCE)
    set(CURL_USE_MBEDTLS ON CACHE BOOL "" FORCE)
    set(MBEDTLS_INCLUDE_DIRS "${MBEDTLS_DIR}/include" CACHE STRING "" FORCE)
    set(MBEDTLS_LIBRARY mbedtls CACHE STRING "" FORCE)
    set(MBEDX509_LIBRARY mbedx509 CACHE STRING "" FORCE)
    set(MBEDCRYPTO_LIBRARY mbedcrypto CACHE STRING "" FORCE)
    set(CURL_USE_BEARSSL OFF CACHE BOOL "" FORCE)
    set(CURL_USE_WOLFSSL OFF CACHE BOOL "" FORCE)
    set(CURL_USE_GNUTLS OFF CACHE BOOL "" FORCE)
    set(CURL_USE_NSS OFF CACHE BOOL "" FORCE)
    set(CURL_USE_LIBSSH2 OFF CACHE BOOL "" FORCE)
    set(CURL_USE_LIBPSL OFF CACHE BOOL "" FORCE)
    set(USE_LIBIDN2 OFF CACHE BOOL "" FORCE)
    set(CURL_ZLIB OFF CACHE BOOL "" FORCE)
    set(CURL_BROTLI OFF CACHE BOOL "" FORCE)
    set(CURL_ZSTD OFF CACHE BOOL "" FORCE)
    set(CURL_DISABLE_LDAP ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_LDAPS ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_FTP ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_FTPS ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_TELNET ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_DICT ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_TFTP ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_GOPHER ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_POP3 ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_IMAP ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_SMB ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_SMTP ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_RTSP ON CACHE BOOL "" FORCE)
    set(CURL_DISABLE_MQTT ON CACHE BOOL "" FORCE)
    set(HTTP_ONLY ON CACHE BOOL "" FORCE)
    set(ENABLE_UNIX_SOCKETS OFF CACHE BOOL "" FORCE)
    set(CURL_ENABLE_EXPORT_TARGET OFF CACHE BOOL "" FORCE)
    set(CURL_CA_BUNDLE "none" CACHE STRING "" FORCE)
    set(CURL_CA_PATH "/system/etc/security/cacerts" CACHE STRING "" FORCE)
    set(CURL_CA_FALLBACK ON CACHE BOOL "" FORCE)
    add_subdirectory(${CURL_DIR} curl-build)
    set(HAVE_CURL ON)
    message(STATUS "libcurl + mbedTLS enabled for master server HTTPS")
else()
    message(WARNING "libcurl not found at ${CURL_DIR} -- master server disabled")
endif()

# ============================================================
# SRB2 Core Source Files
# ============================================================

set(SRB2_CORE_SOURCES
    ${SRB2_SOURCE_DIR}/comptime.c
    ${SRB2_SOURCE_DIR}/md5.c
    ${SRB2_SOURCE_DIR}/string.c
    ${SRB2_SOURCE_DIR}/d_main.c
    ${SRB2_SOURCE_DIR}/dehacked.c
    ${SRB2_SOURCE_DIR}/deh_soc.c
    ${SRB2_SOURCE_DIR}/deh_lua.c
    ${SRB2_SOURCE_DIR}/deh_tables.c
    ${SRB2_SOURCE_DIR}/z_zone.c
    ${SRB2_SOURCE_DIR}/f_finale.c
    ${SRB2_SOURCE_DIR}/f_wipe.c
    ${SRB2_SOURCE_DIR}/g_demo.c
    ${SRB2_SOURCE_DIR}/g_game.c
    ${SRB2_SOURCE_DIR}/g_input.c
    ${SRB2_SOURCE_DIR}/am_map.c
    ${SRB2_SOURCE_DIR}/command.c
    ${SRB2_SOURCE_DIR}/console.c
    ${SRB2_SOURCE_DIR}/hu_stuff.c
    ${SRB2_SOURCE_DIR}/i_time.c
    ${SRB2_SOURCE_DIR}/y_inter.c
    ${SRB2_SOURCE_DIR}/st_stuff.c
    ${SRB2_SOURCE_DIR}/m_aatree.c
    ${SRB2_SOURCE_DIR}/m_anigif.c
    ${SRB2_SOURCE_DIR}/m_argv.c
    ${SRB2_SOURCE_DIR}/m_bbox.c
    ${SRB2_SOURCE_DIR}/m_cheat.c
    ${SRB2_SOURCE_DIR}/m_cond.c
    ${SRB2_SOURCE_DIR}/m_easing.c
    ${SRB2_SOURCE_DIR}/m_fixed.c
    ${SRB2_SOURCE_DIR}/m_menu.c
    ${SRB2_SOURCE_DIR}/m_misc.c
    ${SRB2_SOURCE_DIR}/m_perfstats.c
    ${SRB2_SOURCE_DIR}/m_random.c
    ${SRB2_SOURCE_DIR}/m_tokenizer.c
    ${SRB2_SOURCE_DIR}/m_queue.c
    ${SRB2_SOURCE_DIR}/m_vector.c
    ${SRB2_SOURCE_DIR}/info.c
    ${SRB2_SOURCE_DIR}/p_ceilng.c
    ${SRB2_SOURCE_DIR}/p_enemy.c
    ${SRB2_SOURCE_DIR}/p_floor.c
    ${SRB2_SOURCE_DIR}/p_inter.c
    ${SRB2_SOURCE_DIR}/p_lights.c
    ${SRB2_SOURCE_DIR}/p_map.c
    ${SRB2_SOURCE_DIR}/p_maputl.c
    ${SRB2_SOURCE_DIR}/p_mobj.c
    ${SRB2_SOURCE_DIR}/p_polyobj.c
    ${SRB2_SOURCE_DIR}/p_saveg.c
    ${SRB2_SOURCE_DIR}/p_setup.c
    ${SRB2_SOURCE_DIR}/p_sight.c
    ${SRB2_SOURCE_DIR}/p_spec.c
    ${SRB2_SOURCE_DIR}/p_telept.c
    ${SRB2_SOURCE_DIR}/p_tick.c
    ${SRB2_SOURCE_DIR}/p_user.c
    ${SRB2_SOURCE_DIR}/p_slopes.c
    ${SRB2_SOURCE_DIR}/tables.c
    ${SRB2_SOURCE_DIR}/r_bsp.c
    ${SRB2_SOURCE_DIR}/r_data.c
    ${SRB2_SOURCE_DIR}/r_draw.c
    ${SRB2_SOURCE_DIR}/r_fps.c
    ${SRB2_SOURCE_DIR}/r_main.c
    ${SRB2_SOURCE_DIR}/r_plane.c
    ${SRB2_SOURCE_DIR}/r_segs.c
    ${SRB2_SOURCE_DIR}/r_skins.c
    ${SRB2_SOURCE_DIR}/r_sky.c
    ${SRB2_SOURCE_DIR}/r_splats.c
    ${SRB2_SOURCE_DIR}/r_things.c
    ${SRB2_SOURCE_DIR}/r_bbox.c
    ${SRB2_SOURCE_DIR}/r_textures.c
    ${SRB2_SOURCE_DIR}/r_translation.c
    ${SRB2_SOURCE_DIR}/r_patch.c
    ${SRB2_SOURCE_DIR}/r_patchrotation.c
    ${SRB2_SOURCE_DIR}/r_picformats.c
    ${SRB2_SOURCE_DIR}/r_portal.c
    ${SRB2_SOURCE_DIR}/screen.c
    ${SRB2_SOURCE_DIR}/taglist.c
    ${SRB2_SOURCE_DIR}/v_video.c
    ${SRB2_SOURCE_DIR}/s_sound.c
    ${SRB2_SOURCE_DIR}/sounds.c
    ${SRB2_SOURCE_DIR}/w_wad.c
    ${SRB2_SOURCE_DIR}/filesrch.c
    ${SRB2_SOURCE_DIR}/lzf.c
    ${SRB2_SOURCE_DIR}/b_bot.c
    ${SRB2_SOURCE_DIR}/u_list.c
    ${SRB2_SOURCE_DIR}/snake.c
    ${SRB2_SOURCE_DIR}/apng.c
    # Lua
    ${SRB2_SOURCE_DIR}/lua_script.c
    ${SRB2_SOURCE_DIR}/lua_baselib.c
    ${SRB2_SOURCE_DIR}/lua_mathlib.c
    ${SRB2_SOURCE_DIR}/lua_hooklib.c
    ${SRB2_SOURCE_DIR}/lua_consolelib.c
    ${SRB2_SOURCE_DIR}/lua_infolib.c
    ${SRB2_SOURCE_DIR}/lua_mobjlib.c
    ${SRB2_SOURCE_DIR}/lua_playerlib.c
    ${SRB2_SOURCE_DIR}/lua_skinlib.c
    ${SRB2_SOURCE_DIR}/lua_thinkerlib.c
    ${SRB2_SOURCE_DIR}/lua_maplib.c
    ${SRB2_SOURCE_DIR}/lua_taglib.c
    ${SRB2_SOURCE_DIR}/lua_polyobjlib.c
    ${SRB2_SOURCE_DIR}/lua_blockmaplib.c
    ${SRB2_SOURCE_DIR}/lua_hudlib.c
    ${SRB2_SOURCE_DIR}/lua_hudlib_drawlist.c
    ${SRB2_SOURCE_DIR}/lua_colorlib.c
    ${SRB2_SOURCE_DIR}/lua_inputlib.c
)

# SDL2 platform backend
set(SRB2_SDL_SOURCES
    ${SRB2_SOURCE_DIR}/sdl/i_main.c
    ${SRB2_SOURCE_DIR}/sdl/i_system.c
    ${SRB2_SOURCE_DIR}/sdl/i_video.c
    ${SRB2_SOURCE_DIR}/sdl/i_net.c
    ${SRB2_SOURCE_DIR}/sdl/i_threads.c
    ${SRB2_SOURCE_DIR}/sdl/dosstr.c
    ${SRB2_SOURCE_DIR}/sdl/endtxt.c
    ${SRB2_SOURCE_DIR}/sdl/hwsym_sdl.c
    ${SRB2_SOURCE_DIR}/sdl/ogl_sdl.c
)

# Sound backend - depends on SDL2_mixer availability
if(HAVE_SDL2_MIXER)
    list(APPEND SRB2_SDL_SOURCES ${SRB2_SOURCE_DIR}/sdl/mixer_sound.c)
else()
    list(APPEND SRB2_SDL_SOURCES ${SRB2_SOURCE_DIR}/sdl/sdl_sound.c)
endif()

# ============================================================
# Embedded Lua (blua)
# ============================================================

file(GLOB SRB2_BLUA_SOURCES "${SRB2_SOURCE_DIR}/blua/*.c")

# ============================================================
# Netcode
# ============================================================

file(GLOB SRB2_NETCODE_SOURCES "${SRB2_SOURCE_DIR}/netcode/*.c")

# ============================================================
# Hardware renderer - DISABLED on Android
# Desktop OpenGL 1.x fixed-function pipeline is incompatible
# with OpenGL ES 2.0. Software renderer is used instead,
# with SDL_Renderer providing GLES2-accelerated display.
# ============================================================

# ============================================================
# Android-specific shim
# ============================================================

set(SRB2_ANDROID_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/srb2_android.c
)

# ============================================================
# Build the main shared library
# ============================================================

add_library(main SHARED
    ${SRB2_CORE_SOURCES}
    ${SRB2_SDL_SOURCES}
    ${SRB2_BLUA_SOURCES}
    ${SRB2_NETCODE_SOURCES}
    ${SRB2_ANDROID_SOURCES}
)

# Generate a minimal config.h for the build
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/config.h"
    "// Auto-generated config.h for Android build\n"
    "#define SRB2VERSION \"2.2.15\"\n"
    "#define MODVERSION 56\n"
    "#define MODID 18\n"
    "#define VERSIONSTRING \"v2.2.15\"\n"
    "#define VERSIONSTRINGW L\"v2.2.15\"\n"
    "#define COMPVERSION 206\n"
    "#define ASSET_HASH_SRB2_PK3 \"3182ce524acc2072ddaa81acf4b6a9aa\"\n"
    "#define ASSET_HASH_ZONES_PK3 \"88ff4c300851ccdb0406698eadd89907\"\n"
    "#define ASSET_HASH_CHARACTERS_PK3 \"5c5936b8a690e007c0939bd0785a41fb\"\n"
    "#define ASSET_HASH_PATCH_PK3 \"00000000000000000000000000000000\"\n"
    "#define GIT_SHA1 \"android-port\"\n"
    "#define SRB2_COMP_REVISION \"android\"\n"
    "#define SRB2_COMP_BRANCH \"android-port\"\n"
    "#define SRB2_COMP_NOTE \"\"\n"
    "#define SRB2_COMP_TYPE \"Release\"\n"
    "#define CMAKE_BUILD_TYPE \"Release\"\n"
    "#define SRB2_COMP_OPTIMIZED 1\n"
)

target_include_directories(main PRIVATE
    ${SRB2_SOURCE_DIR}
    ${SRB2_SOURCE_DIR}/blua
    ${SRB2_SOURCE_DIR}/sdl
    ${SRB2_SOURCE_DIR}/netcode
    ${CMAKE_CURRENT_BINARY_DIR}  # For config.h
    ${LIBPNG_SRC_DIR}            # For png.h
    ${CMAKE_CURRENT_BINARY_DIR}/libpng  # For pnglibconf.h
)

if(HAVE_CURL)
    target_include_directories(main PRIVATE ${CURL_DIR}/include)
endif()

# Compile definitions
target_compile_definitions(main PRIVATE
    # Platform
    ANDROID
    ANDROID_JAVA_TOUCH_OVERLAY
    UNIXCOMMON
    LINUX
    NOEXECINFO
    HAVE_SDL
    DIRECTFULLSCREEN
    CMAKECONFIG
    HAVE_THREADS
    _LARGEFILE64_SOURCE

    # Hardware renderer (desktop OpenGL) is DISABLED on Android.
    # Software rendering + SDL_Renderer (GLES2 backend) is used instead.

    # SDL_main entry point required on Android
    FORCESDLMAIN

    # zlib is available in Android NDK
    HAVE_ZLIB

    # libpng (bundled source)
    HAVE_PNG

    # No features that aren't available on Android
    NOMD5
    NOUPNP
    NOMUMBLE
    STDC_HEADERS
)

# curl / master server support
if(HAVE_CURL)
    target_compile_definitions(main PRIVATE HAVE_CURL CURL_STATICLIB)
else()
    target_compile_definitions(main PRIVATE NOCURL)
endif()

# Sound definitions
if(HAVE_SDL2_MIXER)
    target_compile_definitions(main PRIVATE
        HAVE_MIXER
        SOUND=SOUND_MIXER
    )
endif()

# Compiler options
target_compile_options(main PRIVATE
    -Wall
    -Wno-trigraphs
    -fwrapv
    -fPIC
    -O2
    # Suppress warnings that would break the build
    -Wno-unused-variable
    -Wno-unused-function
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-missing-field-initializers
    -Wno-implicit-function-declaration
    -Wno-incompatible-pointer-types
)

# Disable FORTIFY_SOURCE for Lua files — Z_Malloc custom allocator causes
# false positive "strchr: prevented read past end of buffer" SIGABRT on Android
file(GLOB BLUA_SOURCES "${SRB2_SOURCE_DIR}/blua/*.c")
set_source_files_properties(${BLUA_SOURCES} PROPERTIES COMPILE_FLAGS "-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0")
set_source_files_properties(${SRB2_SOURCE_DIR}/lua_script.c PROPERTIES COMPILE_FLAGS "-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0")

target_compile_features(main PRIVATE c_std_11 cxx_std_17)

# Link libraries
target_link_libraries(main PRIVATE
    SDL2
    png_static
    GLESv2
    EGL
    android
    log
    dl
    m
    z
)

if(HAVE_CURL)
    target_link_libraries(main PRIVATE libcurl_static)
endif()

if(HAVE_SDL2_MIXER)
    target_link_libraries(main PRIVATE SDL2_mixer)
endif()

# Set output name
set_target_properties(main PROPERTIES
    OUTPUT_NAME "srb2"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)
