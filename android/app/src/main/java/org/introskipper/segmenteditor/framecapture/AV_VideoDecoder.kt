/*
 * https://stackoverflow.com/a/60633395/461982
 * Copyright (c) 2024 AbandonedCart.
 */

package org.introskipper.segmenteditor.framecapture

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.IOException

class AV_VideoDecoder(
    private val mPath: String,
    private val mSurface: Surface,
    private val mHeaders: Map<String, String>? = null
) {
    private var mMediaExtractor: MediaExtractor? = null
    private var mMediaCodec: MediaCodec? = null

    private var mVideoTrackIndex = -1
    private var mLastTimeUs: Long = -1L
    private var mIsInputEOS = false

    var isReady: Boolean = false
        private set

    init {
        isReady = initCodec()
    }

    fun prepare(time: Long): Boolean {
        return decodeFrameAt(time)
    }

    fun release() {
        try {
            mMediaCodec?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
            mMediaCodec = null

            mMediaExtractor?.release()
            mMediaExtractor = null
        } catch (e: Exception) {
            Log.e(TAG, "Release failed", e)
        }
    }

    private fun initCodec(): Boolean {
        Log.i(TAG, "initCodec")
        mMediaExtractor = MediaExtractor()
        try {
            if (mHeaders != null) {
                mMediaExtractor?.setDataSource(mPath, mHeaders)
            } else {
                mMediaExtractor?.setDataSource(mPath)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to set data source: $mPath", e)
            return false
        }

        val trackCount = mMediaExtractor!!.trackCount
        for (i in 0 until trackCount) {
            val mf = mMediaExtractor!!.getTrackFormat(i)
            val mime = mf.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(VIDEO_MIME_PREFIX) == true) {
                mVideoTrackIndex = i
                break
            }
        }
        if (mVideoTrackIndex < 0) return false

        mMediaExtractor!!.selectTrack(mVideoTrackIndex)
        val mf = mMediaExtractor!!.getTrackFormat(mVideoTrackIndex)
        val mime = mf.getString(MediaFormat.KEY_MIME) ?: return false
        
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mime)
            mMediaCodec!!.configure(mf, mSurface, null, 0)
            mMediaCodec!!.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            mMediaCodec!!.start()
        } catch (e: Exception) {
            Log.e(TAG, "Codec configure/start failed", e)
            mMediaCodec?.release()
            mMediaCodec = null
            return false
        }

        mLastTimeUs = -1L
        mIsInputEOS = false
        Log.i(TAG, "initCodec end")
        return true
    }

    private fun decodeFrameAt(timeUs: Long): Boolean {
        Log.i(TAG, "decodeFrameAt $timeUs (last: $mLastTimeUs)")
        
        // If we're scrubbing forward and within 2 seconds of the last decoded frame,
        // we can continue decoding instead of flushing and seeking.
        val canContinue = mLastTimeUs != -1L && timeUs > mLastTimeUs && timeUs < mLastTimeUs + 2_000_000L

        if (!canContinue) {
            try {
                mMediaCodec?.flush()
                mIsInputEOS = false
            } catch (e: Exception) {
                Log.e(TAG, "Codec flush failed, attempting to re-init", e)
                release()
                if (!initCodec()) return false
            }
            mMediaExtractor!!.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }

        val info = MediaCodec.BufferInfo()
        var retryCount = 0
        val maxRetries = 150

        while (retryCount < maxRetries) {
            if (!mIsInputEOS) {
                val inputIndex = mMediaCodec!!.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = mMediaCodec!!.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = mMediaExtractor!!.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            mMediaCodec!!.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            mIsInputEOS = true
                        } else {
                            mMediaCodec!!.queueInputBuffer(inputIndex, 0, sampleSize, mMediaExtractor!!.sampleTime, 0)
                            mMediaExtractor!!.advance()
                        }
                    }
                }
            }

            val outputIndex = mMediaCodec!!.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outputIndex >= 0) {
                val isEOS = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                val presentationTimeUs = info.presentationTimeUs
                
                // We want the first frame that is >= target time
                if (presentationTimeUs >= timeUs || isEOS) {
                    val reachTarget = presentationTimeUs >= timeUs
                    mMediaCodec!!.releaseOutputBuffer(outputIndex, reachTarget)
                    if (reachTarget) {
                        Log.i(TAG, "Reached target frame at $presentationTimeUs")
                        mLastTimeUs = presentationTimeUs
                        return true
                    }
                    if (isEOS) {
                        Log.i(TAG, "Reached EOS at $presentationTimeUs")
                        mLastTimeUs = -1L
                        break
                    }
                } else {
                    // Skip frames before target
                    mMediaCodec!!.releaseOutputBuffer(outputIndex, false)
                    mLastTimeUs = presentationTimeUs
                }
                retryCount = 0
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                retryCount++
            }
        }

        return false
    }

    companion object {
        const val TAG: String = "VideoDecoder"
        const val VIDEO_MIME_PREFIX: String = "video/"
        const val TIMEOUT_US: Long = 5000
    }
}
