package com.huimei.voice.voiceprint

import org.json.JSONException
import org.json.JSONObject

object VoskSpeakerVectorParser {
    @JvmStatic
    fun parse(json: String, minimumFrames: Int): FloatArray {
        require(!(json == null || json.trim { it <= ' ' }.isEmpty())) { "Vosk result is required" }
        require(minimumFrames > 0) { "Minimum speaker frame count must be positive" }
        try {
            val `object` = JSONObject(json)
            val frameValue = `object`.opt("spk_frames")
            require(!(frameValue !is Number || frameValue.toInt() < minimumFrames)) { "Vosk result has insufficient speaker frames" }
            val array = `object`.optJSONArray("spk")
            require(!(array == null || array.length() == 0)) { "Vosk result has no speaker vector" }
            val vector = FloatArray(array!!.length())
            for (i in 0..<array.length()) {
                val value = array.get(i)
                require(value is Number) { "Vosk speaker vector must be numeric" }
                vector[i] = (value as Number).toFloat()
                require(vector[i].isFinite()) { "Vosk speaker vector must be finite" }
            }
            return vector
        } catch (exception: JSONException) {
            throw IllegalArgumentException("Malformed Vosk speaker result", exception)
        }
    }

    @JvmStatic
    fun parseBest(jsonResults: MutableList<String>, minimumFrames: Int): FloatArray {
        require(!(jsonResults == null || jsonResults.isEmpty())) { "Vosk results are required" }
        require(minimumFrames > 0) { "Minimum speaker frame count must be positive" }

        var bestVector: FloatArray? = null
        var bestFrameCount = -1
        for (json in jsonResults) {
            try {
                val frameCount = speakerFrameCount(json)
                val vector = parse(json, minimumFrames)
                if (frameCount > bestFrameCount) {
                    bestFrameCount = frameCount
                    bestVector = vector
                }
            } catch (ignored: IllegalArgumentException) {
                // Endpoint and final results without a speaker vector are expected.
            }
        }
        requireNotNull(bestVector) { "Vosk results have no usable speaker vector" }
        return bestVector
    }

    private fun speakerFrameCount(json: String): Int {
        require(!(json == null || json.trim { it <= ' ' }.isEmpty())) { "Vosk result is required" }
        try {
            val value = JSONObject(json).opt("spk_frames")
            require(value is Number) { "Vosk result has no speaker frames" }
            return (value as Number).toInt()
        } catch (exception: JSONException) {
            throw IllegalArgumentException("Malformed Vosk speaker result", exception)
        }
    }
}
