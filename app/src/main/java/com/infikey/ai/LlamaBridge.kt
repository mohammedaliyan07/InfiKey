package com.infikey.ai

import android.util.Log

/** JNI bridge to the native llama.cpp engine (libinfikey.so). */
object LlamaBridge {
    private const val TAG = "LlamaBridge"

    @Volatile
    var loaded: Boolean = false
        private set

    fun loadLibrary(): Boolean {
        return try {
            if (!loaded) System.loadLibrary("infikey")
            loaded = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "native lib load failed", t)
            false
        }
    }

    fun interface TokenCallback {
        /** Return false to stop generation. */
        fun onToken(token: String): Boolean
    }

    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    external fun nativeGenerate(
        handle: Long, prompt: String, maxTokens: Int,
        temperature: Float, topP: Float, topK: Int,
        stop: String?, callback: TokenCallback
    ): Boolean
    external fun nativeCancel(handle: Long)
    external fun nativeModelInfo(handle: Long): String?
    external fun nativeFree(handle: Long)
}
