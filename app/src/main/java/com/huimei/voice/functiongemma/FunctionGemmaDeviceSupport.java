package com.huimei.voice.functiongemma;

import android.os.Build;

public final class FunctionGemmaDeviceSupport {
    private FunctionGemmaDeviceSupport() {
    }

    public static boolean isSupported() {
        return isSupported(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS);
    }

    static boolean isSupported(int sdkInt, String[] supportedAbis) {
        if (sdkInt < Build.VERSION_CODES.N || supportedAbis == null) {
            return false;
        }
        for (String abi : supportedAbis) {
            if ("arm64-v8a".equals(abi) || "x86_64".equals(abi)) {
                return true;
            }
        }
        return false;
    }
}
