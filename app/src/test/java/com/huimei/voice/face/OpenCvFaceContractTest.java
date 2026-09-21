package com.huimei.voice.face;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;

public final class OpenCvFaceContractTest {
    @Test
    public void requiredOpenCvJavaMethodsExist() throws Exception {
        assertEquals(boolean.class,
                OpenCVLoader.class.getMethod("initLocal").getReturnType());
        assertEquals(FaceDetectorYN.class,
                FaceDetectorYN.class.getMethod(
                        "create", String.class, String.class, Size.class,
                        float.class, float.class, int.class).getReturnType());
        assertEquals(void.class,
                FaceRecognizerSF.class.getMethod(
                        "alignCrop", Mat.class, Mat.class, Mat.class).getReturnType());
        assertEquals(void.class,
                FaceRecognizerSF.class.getMethod(
                        "feature", Mat.class, Mat.class).getReturnType());
    }

    @Test
    public void faceFrameDefensivelyCopiesRgbaBytes() {
        byte[] rgba = new byte[]{1, 2, 3, 4};
        FaceFrame frame = new FaceFrame(1, 1, rgba);
        rgba[0] = 9;
        byte[] returned = frame.rgba();
        returned[1] = 9;

        assertEquals(1, frame.width());
        assertEquals(1, frame.height());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, frame.rgba());
    }
}
