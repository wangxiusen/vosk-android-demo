package com.huimei.voice.functiongemma

import android.os.Build

class FunctionGemmaDeviceSupport private constructor() {
    companion object {
        @JvmStatic
        fun isSupported(): Boolean = isSupported(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS)

        @JvmStatic
        fun isSupported(sdkInt: Int, supportedAbis: Array<String>?): Boolean {
            if (sdkInt < Build.VERSION_CODES.N || supportedAbis == null) return false
            return supportedAbis.any { it == "arm64-v8a" || it == "x86_64" }
        }
    }
}
