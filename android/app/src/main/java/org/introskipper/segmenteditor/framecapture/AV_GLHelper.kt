/*
 * https://stackoverflow.com/a/60633395/461982
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.framecapture

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import java.lang.RuntimeException
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap

class AV_GLHelper {
    private var mTextureRender: AV_TextureRender? = null

    private var mEglDisplay: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var mEglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var mEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var mPixelBuffer: ByteBuffer? = null
    private var mWidth: Int = 0
    private var mHeight: Int = 0

    fun init(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        initGL()

        makeCurrent()
        mTextureRender = AV_TextureRender()
    }

    private fun initGL() {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException(
                "eglGetdisplay failed : " +
                        GLUtils.getEGLErrorString(EGL14.eglGetError())
            )
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            mEglDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }

        // Configure EGL for pbuffer and OpenGL ES 2.0.  We want RGBA8888 to ensure
        // glReadPixels(GL_RGBA) can read without a performance-degrading format conversion.
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                mEglDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) {
            throw RuntimeException("unable to find EGL config matching RGBA8888 color format with pbuffer support for ES2")
        }

        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        mEglContext = EGL14.eglCreateContext(
            mEglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            attrib_list, 0
        )
        AV_GLUtil.checkEglError("eglCreateContext")

        // Create a pbuffer surface for off-screen rendering and reliable pixel readback.
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, mWidth,
            EGL14.EGL_HEIGHT, mHeight,
            EGL14.EGL_NONE
        )
        mEglSurface = EGL14.eglCreatePbufferSurface(
            mEglDisplay, configs[0],
            surfaceAttribs, 0
        )
        AV_GLUtil.checkEglError("eglCreatePbufferSurface")
    }

    fun release() {
        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(mEglDisplay, mEglSurface)
            EGL14.eglDestroyContext(mEglDisplay, mEglContext)
            EGL14.eglTerminate(mEglDisplay)
        }
        mEglDisplay = EGL14.EGL_NO_DISPLAY
        mEglContext = EGL14.EGL_NO_CONTEXT
        mEglSurface = EGL14.EGL_NO_SURFACE
        mPixelBuffer = null
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun createOESTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        val textureID = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID)
        AV_GLUtil.checkEglError("glBindTexture textureID")

        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        AV_GLUtil.checkEglError("glTexParameter")

        return textureID
    }

    fun drawFrame(st: SurfaceTexture, textureID: Int) {
        GLES20.glViewport(0, 0, mWidth, mHeight)
        st.updateTexImage()
        if (null != mTextureRender) mTextureRender!!.drawFrame(st, textureID)
    }

    fun readPixels(width: Int, height: Int): Bitmap {
        if (mPixelBuffer == null || mPixelBuffer!!.capacity() < 4 * width * height) {
            mPixelBuffer = ByteBuffer.allocateDirect(4 * width * height)
        }
        val pixelBuffer = mPixelBuffer!!
        pixelBuffer.position(0)
        GLES20.glReadPixels(
            0,
            0,
            width,
            height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixelBuffer
        )

        // Use ARGB_8888 as copyPixelsFromBuffer is not compatible with HARDWARE config
        val bmp = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        pixelBuffer.position(0)
        bmp.copyPixelsFromBuffer(pixelBuffer)

        return bmp
    }

    companion object {
        private const val EGL_OPENGL_ES2_BIT = 4
    }
}
