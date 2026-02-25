/*
 * https://stackoverflow.com/a/60633395/461982
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.framecapture

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.suspendCancellableCoroutine
import org.introskipper.segmenteditor.toPx
import kotlin.coroutines.resume

class AV_FrameCapture {
    private val mGLThread: HandlerThread = HandlerThread("AV_FrameCapture")
    private val mGLHandler: Handler
    private val mGLHelper: AV_GLHelper = AV_GLHelper()

    private var mWidth = 176.toPx
    private var mHeight = 96.toPx

    private var mPath: String? = null
    private var mHeaders: Map<String, String>? = null
    private var mVideoDecoder: AV_VideoDecoder? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mSurface: Surface? = null
    private var mTextureID: Int = -1

    init {
        mGLThread.start()
        mGLHandler = Handler(mGLThread.looper)
    }

    fun setDataSource(path: String?, headers: Map<String, String>? = null) {
        mPath = path
        mHeaders = headers
    }

    fun setTargetSize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
    }

    suspend fun init(): Boolean = suspendCancellableCoroutine { continuation ->
        if (mPath.isNullOrEmpty()) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        mGLHandler.post {
            var success = false
            try {
                mGLHelper.init(mWidth, mHeight)

                mTextureID = mGLHelper.createOESTexture()
                mSurfaceTexture = SurfaceTexture(mTextureID)
                mSurfaceTexture!!.setDefaultBufferSize(mWidth, mHeight)
                mSurface = Surface(mSurfaceTexture)

                val decoder = AV_VideoDecoder(mPath!!, mSurface!!, mHeaders)
                if (decoder.isReady) {
                    mVideoDecoder = decoder
                    success = true
                } else {
                    decoder.release()
                    mSurface?.release()
                    mSurfaceTexture?.release()
                    mSurface = null
                    mSurfaceTexture = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AV_FrameCapture", e)
            } finally {
                if (continuation.isActive) continuation.resume(success)
            }
        }
    }

    fun release() {
        mGLHandler.post {
            mVideoDecoder?.release()
            mVideoDecoder = null
            mSurface?.release()
            mSurface = null
            mSurfaceTexture?.release()
            mSurfaceTexture = null
            mGLHelper.release()
            mGLThread.quit()
        }
    }

    suspend fun getFrameAtTime(frameTime: Long): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (mPath.isNullOrEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        mGLHandler.post {
            if (!continuation.isActive) return@post

            val decoder = mVideoDecoder ?: run {
                if (continuation.isActive) continuation.resume(null)
                return@post
            }
            val st = mSurfaceTexture ?: run {
                if (continuation.isActive) continuation.resume(null)
                return@post
            }

            val timeoutRunnable = Runnable {
                st.setOnFrameAvailableListener(null)
                Log.w(TAG, "getFrameAtTime timed out at $frameTime")
                if (continuation.isActive) continuation.resume(null)
            }

            st.setOnFrameAvailableListener({
                mGLHandler.removeCallbacks(timeoutRunnable)
                st.setOnFrameAvailableListener(null)
                Log.i(TAG, "onFrameAvailable")
                try {
                    mGLHelper.drawFrame(st, mTextureID)
                    val bitmap = mGLHelper.readPixels(mWidth, mHeight)
                    if (continuation.isActive) continuation.resume(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading pixels", e)
                    if (continuation.isActive) continuation.resume(null)
                }
            }, mGLHandler)

            try {
                if (!decoder.prepare(frameTime)) {
                    st.setOnFrameAvailableListener(null)
                    if (continuation.isActive) continuation.resume(null)
                } else {
                    mGLHandler.postDelayed(timeoutRunnable, 2000)
                }
            } catch (e: Exception) {
                st.setOnFrameAvailableListener(null)
                Log.e(TAG, "Decoder prepare failed", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    companion object {
        const val TAG: String = "AV_FrameCapture"
    }
}
