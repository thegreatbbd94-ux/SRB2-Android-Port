# Custom FindMbedTLS that works with add_subdirectory() built mbedTLS targets
# Instead of searching for installed libraries, accept CMake target names.

if(TARGET mbedtls AND TARGET mbedx509 AND TARGET mbedcrypto)
    # mbedTLS was built via add_subdirectory, use target names directly
    set(MBEDTLS_FOUND TRUE)
    if(NOT MBEDTLS_INCLUDE_DIRS)
        get_target_property(MBEDTLS_INCLUDE_DIRS mbedtls INTERFACE_INCLUDE_DIRECTORIES)
    endif()
    if(NOT MBEDTLS_LIBRARY)
        set(MBEDTLS_LIBRARY mbedtls)
    endif()
    if(NOT MBEDX509_LIBRARY)
        set(MBEDX509_LIBRARY mbedx509)
    endif()
    if(NOT MBEDCRYPTO_LIBRARY)
        set(MBEDCRYPTO_LIBRARY mbedcrypto)
    endif()
    set(MBEDTLS_LIBRARIES ${MBEDTLS_LIBRARY} ${MBEDX509_LIBRARY} ${MBEDCRYPTO_LIBRARY})
else()
    # Fallback to standard find
    find_path(MBEDTLS_INCLUDE_DIRS mbedtls/ssl.h)
    find_library(MBEDTLS_LIBRARY mbedtls)
    find_library(MBEDX509_LIBRARY mbedx509)
    find_library(MBEDCRYPTO_LIBRARY mbedcrypto)
    set(MBEDTLS_LIBRARIES "${MBEDTLS_LIBRARY}" "${MBEDX509_LIBRARY}" "${MBEDCRYPTO_LIBRARY}")
    include(FindPackageHandleStandardArgs)
    find_package_handle_standard_args(MbedTLS DEFAULT_MSG
        MBEDTLS_INCLUDE_DIRS MBEDTLS_LIBRARY MBEDX509_LIBRARY MBEDCRYPTO_LIBRARY)
endif()

mark_as_advanced(MBEDTLS_INCLUDE_DIRS MBEDTLS_LIBRARY MBEDX509_LIBRARY MBEDCRYPTO_LIBRARY)
