package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FunctionGemmaDeviceSupportTest {
    @Test
    public void acceptsOnlySupported64BitAbisOnAndroidSevenOrLater() {
        assertTrue(FunctionGemmaDeviceSupport.isSupported(
                24, new String[]{"arm64-v8a", "armeabi-v7a"}));
        assertTrue(FunctionGemmaDeviceSupport.isSupported(
                36, new String[]{"x86_64"}));

        assertFalse(FunctionGemmaDeviceSupport.isSupported(
                23, new String[]{"arm64-v8a"}));
        assertFalse(FunctionGemmaDeviceSupport.isSupported(
                36, new String[]{"armeabi-v7a"}));
        assertFalse(FunctionGemmaDeviceSupport.isSupported(
                36, new String[]{"x86"}));
        assertFalse(FunctionGemmaDeviceSupport.isSupported(36, new String[0]));
    }
}
