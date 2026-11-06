package com.huimei.voice.face

import kotlin.math.max
import kotlin.math.min

import android.content.Context
import com.huimei.voice.face.FaceMatcher.normalize
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF

class OpenCvFaceEngine(context: Context) : FaceEngine {
    private val installer: FaceModelInstaller
    private var detector: FaceDetectorYN? = null
    private var recognizer: FaceRecognizerSF? = null

    init {
        installer = FaceModelInstaller(context)
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(Exception::class)
    override fun initialize() {
        if (detector != null && recognizer != null) {
            return
        }
        check(OpenCVLoader.initLocal()) { "OpenCV native library initialization failed" }
        val models = installer.install()
        detector = FaceDetectorYN.create(
            models.detector()!!.getAbsolutePath(), "", INITIAL_INPUT_SIZE,
            FaceFrameQuality.MIN_CONFIDENCE, 0.30f, 5000
        )
        recognizer = FaceRecognizerSF.create(
            models.recognizer()!!.getAbsolutePath(), ""
        )
        if (detector == null || recognizer == null) {
            close()
            throw IllegalStateException("OpenCV face model initialization failed")
        }
    }

    override fun dimension(): Int {
        return EMBEDDING_DIMENSION
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(Exception::class)
    override fun analyze(frame: FaceFrame): FaceObservation {
        check(!(detector == null || recognizer == null)) { "OpenCV face engine is not initialized" }
        requireNotNull(frame) { "Face frame is required" }
        val startedNanos = System.nanoTime()
        val rgba = Mat(frame.height(), frame.width(), CvType.CV_8UC4)
        val bgr = Mat()
        val faces = Mat()
        var faceRow: Mat? = null
        var faceRegion: Mat? = null
        val gray = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val deviation = MatOfDouble()
        val aligned = Mat()
        val feature = Mat()
        val encoded = MatOfByte()
        val jpegOptions = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 90)
        try {
            rgba.put(0, 0, frame.rgba())
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            detector!!.setInputSize(Size(bgr.cols().toDouble(), bgr.rows().toDouble()))
            detector!!.detect(bgr, faces)
            val faceCount = faces.rows()
            if (faceCount != 1) {
                val decision = FaceFrameQuality.evaluate(
                    faceCount, 0f, 0f, 0.0, 0.0
                )
                return FaceObservation.rejected(
                    faceCount, decision.reason()!!, 0f, elapsedMillis(startedNanos)
                )
            }

            faceRow = faces.row(0)
            val detection = FloatArray(faceRow.total().toInt())
            faceRow.get(0, 0, detection)
            check(detection.size >= 15) { "YuNet returned an invalid face row" }
            val score = detection[14]
            val widthRatio = detection[2] / bgr.cols()
            val bounds: Rect = clampRect(
                detection[0], detection[1], detection[2], detection[3],
                bgr.cols(), bgr.rows()
            )
            if (bounds.width <= 0 || bounds.height <= 0) {
                return FaceObservation.rejected(
                    1, FaceFrameQuality.Reason.FACE_TOO_SMALL,
                    score, elapsedMillis(startedNanos)
                )
            }
            faceRegion = bgr.submat(bounds)
            Imgproc.cvtColor(faceRegion, gray, Imgproc.COLOR_BGR2GRAY)
            val brightness = Core.mean(gray).`val`[0]
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, deviation)
            val standardDeviation = deviation.toArray()[0]
            val sharpness = standardDeviation * standardDeviation
            val decision = FaceFrameQuality.evaluate(
                1, score, widthRatio, brightness, sharpness
            )
            if (!decision.accepted()) {
                return FaceObservation.rejected(
                    1, decision.reason()!!, score, elapsedMillis(startedNanos)
                )
            }

            recognizer!!.alignCrop(bgr, faceRow, aligned)
            recognizer!!.feature(aligned, feature)
            check(feature.total() == EMBEDDING_DIMENSION.toLong()) { "SFace returned an unexpected feature dimension" }
            var embedding = FloatArray(EMBEDDING_DIMENSION)
            feature.get(0, 0, embedding)
            embedding = normalize(embedding)
            check(
                Imgcodecs.imencode(
                    ".jpg",
                    aligned,
                    encoded,
                    jpegOptions
                )
            ) { "Cannot encode aligned face portrait" }
            return FaceObservation.accepted(
                embedding, encoded.toArray(), score, elapsedMillis(startedNanos)
            )
        } finally {
            if (faceRow != null) {
                faceRow.release()
            }
            if (faceRegion != null) {
                faceRegion.release()
            }
            rgba.release()
            bgr.release()
            faces.release()
            gray.release()
            laplacian.release()
            mean.release()
            deviation.release()
            aligned.release()
            feature.release()
            encoded.release()
            jpegOptions.release()
        }
    }

    @kotlin.jvm.Synchronized
    override fun close() {
        // OpenCV's Java wrappers expose native destruction only through finalize().
        detector = null
        recognizer = null
    }

    companion object {
        private const val EMBEDDING_DIMENSION = 128
        private val INITIAL_INPUT_SIZE = Size(320.0, 320.0)

        private fun clampRect(
            x: Float, y: Float, width: Float, height: Float,
            imageWidth: Int, imageHeight: Int
        ): Rect {
            val left: Int = max(0, min(imageWidth, Math.round(x)))
            val top: Int = max(0, min(imageHeight, Math.round(y)))
            val right: Int = max(left, min(imageWidth, Math.round(x + width)))
            val bottom: Int = max(top, min(imageHeight, Math.round(y + height)))
            return Rect(left, top, right - left, bottom - top)
        }

        private fun elapsedMillis(startedNanos: Long): Long {
            return max(0L, (System.nanoTime() - startedNanos) / 1000000L)
        }
    }
}
