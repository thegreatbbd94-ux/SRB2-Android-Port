/*
 * Android build shim for SDL_opengl.h
 *
 * SDL's SDL_opengl.h intentionally includes nothing on Android (it's #ifdef'd
 * out for __ANDROID__), because Android targets OpenGL ES, not desktop GL.
 *
 * SRB2's hardware renderer (r_opengl.c / r_opengl.h) includes SDL_opengl.h
 * to get desktop GL types and function declarations.  On Android we use gl4es
 * instead — a compatibility layer that translates OpenGL 1.x/2.x (including
 * fixed-function pipeline and GLSL 1.x built-ins like gl_ProjectionMatrix) to
 * OpenGL ES 2.0 at runtime.
 *
 * This shim is picked up before SDL2/include/SDL_opengl.h because the
 * android-port/app/src/main/jni/include/ directory is placed first in the
 * CMake include path list.
 */
#pragma once

#ifdef ANDROID
#  include <GL/gl.h>     /* gl4es: full desktop GL 1.x/2.x declarations */
#  include <GL/glext.h>  /* gl4es: extension function prototypes          */
#else
#  error "This SDL_opengl.h shim is only for Android builds. \
For other platforms use the real SDL_opengl.h from SDL2/include/."
#endif
