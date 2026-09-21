package com.huimei.voice.face;

import android.content.Context;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;

import java.io.IOException;

public final class OpenCvFaceEngine implements FaceEngine {
    private static final int EMBEDDING_DIMENSION = 128;
    private static final Size INITIAL_INPUT_SIZE = new Size(320, 320);

    private final FaceModelInstaller installer;
    private FaceDetectorYN detector;
    private FaceRecognizerSF recognizer;

    public OpenCvFaceEngine(Context context) throws IOException {
        installer = new FaceModelInstaller(context);
    }

    @Override
    public synchronized void initialize() throws Exception {
        if (detector != null && recognizer != null) {
            return;
        }
        if (!OpenCVLoader.initLocal()) {
            throw new IllegalStateException("OpenCV native library initialization failed");
        }
        FaceModelInstaller.ModelFiles models = installer.install();
        detector = FaceDetectorYN.create(
                models.detector().getAbsolutePath(), "", INITIAL_INPUT_SIZE,
                FaceFrameQuality.MIN_CONFIDENCE, 0.30f, 5000);
        recognizer = FaceRecognizerSF.create(
                models.recognizer().getAbsolutePath(), "");
        if (detector == null || recognizer == null) {
            close();
            throw new IllegalStateException("OpenCV face model initialization failed");
        }
    }

    @Override
    public int dimension() {
        return EMBEDDING_DIMENSION;
    }

    @Override
    public synchronized FaceObservation analyze(FaceFrame frame) throws Exception {
        if (detector == null || recognizer == null) {
            throw new IllegalStateException("OpenCV face engine is not initialized");
        }
        if (frame == null) {
            throw new IllegalArgumentException("Face frame is required");
        }
        long startedNanos = System.nanoTime();
        Mat rgba = new Mat(frame.height(), frame.width(), CvType.CV_8UC4);
        Mat bgr = new Mat();
        Mat faces = new Mat();
        Mat faceRow = null;
        Mat faceRegion = null;
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble deviation = new MatOfDouble();
        Mat aligned = new Mat();
        Mat feature = new Mat();
        MatOfByte encoded = new MatOfByte();
        MatOfInt jpegOptions = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 90);
        try {
            rgba.put(0, 0, frame.rgba());
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR);
            detector.setInputSize(new Size(bgr.cols(), bgr.rows()));
            detector.detect(bgr, faces);
            int faceCount = faces.rows();
            if (faceCount != 1) {
                FaceFrameQuality.Decision decision = FaceFrameQuality.evaluate(
                        faceCount, 0f, 0f, 0d, 0d);
                return FaceObservation.rejected(
                        faceCount, decision.reason(), 0f, elapsedMillis(startedNanos));
            }

            faceRow = faces.row(0);
            float[] detection = new float[(int) faceRow.total()];
            faceRow.get(0, 0, detection);
            if (detection.length < 15) {
                throw new IllegalStateException("YuNet returned an invalid face row");
            }
            float score = detection[14];
            float widthRatio = detection[2] / bgr.cols();
            Rect bounds = clampRect(
                    detection[0], detection[1], detection[2], detection[3],
                    bgr.cols(), bgr.rows());
            if (bounds.width <= 0 || bounds.height <= 0) {
                return FaceObservation.rejected(
                        1, FaceFrameQuality.Reason.FACE_TOO_SMALL,
                        score, elapsedMillis(startedNanos));
            }
            faceRegion = bgr.submat(bounds);
            Imgproc.cvtColor(faceRegion, gray, Imgproc.COLOR_BGR2GRAY);
            double brightness = Core.mean(gray).val[0];
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            Core.meanStdDev(laplacian, mean, deviation);
            double standardDeviation = deviation.toArray()[0];
            double sharpness = standardDeviation * standardDeviation;
            FaceFrameQuality.Decision decision = FaceFrameQuality.evaluate(
                    1, score, widthRatio, brightness, sharpness);
            if (!decision.accepted()) {
                return FaceObservation.rejected(
                        1, decision.reason(), score, elapsedMillis(startedNanos));
            }

            recognizer.alignCrop(bgr, faceRow, aligned);
            recognizer.feature(aligned, feature);
            if (feature.total() != EMBEDDING_DIMENSION) {
                throw new IllegalStateException("SFace returned an unexpected feature dimension");
            }
            float[] embedding = new float[EMBEDDING_DIMENSION];
            feature.get(0, 0, embedding);
            embedding = FaceMatcher.normalize(embedding);
            if (!Imgcodecs.imencode(".jpg", aligned, encoded, jpegOptions)) {
                throw new IllegalStateException("Cannot encode aligned face portrait");
            }
            return FaceObservation.accepted(
                    embedding, encoded.toArray(), score, elapsedMillis(startedNanos));
        } finally {
            if (faceRow != null) {
                faceRow.release();
            }
            if (faceRegion != null) {
                faceRegion.release();
            }
            rgba.release();
            bgr.release();
            faces.release();
            gray.release();
            laplacian.release();
            mean.release();
            deviation.release();
            aligned.release();
            feature.release();
            encoded.release();
            jpegOptions.release();
        }
    }

    @Override
    public synchronized void close() {
        // OpenCV's Java wrappers expose native destruction only through finalize().
        detector = null;
        recognizer = null;
    }

    private static Rect clampRect(
            float x, float y, float width, float height,
            int imageWidth, int imageHeight) {
        int left = Math.max(0, Math.min(imageWidth, Math.round(x)));
        int top = Math.max(0, Math.min(imageHeight, Math.round(y)));
        int right = Math.max(left, Math.min(imageWidth, Math.round(x + width)));
        int bottom = Math.max(top, Math.min(imageHeight, Math.round(y + height)));
        return new Rect(left, top, right - left, bottom - top);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
