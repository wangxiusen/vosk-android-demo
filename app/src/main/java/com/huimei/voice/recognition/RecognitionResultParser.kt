package com.huimei.voice.recognition

import org.json.JSONException
import org.json.JSONObject
import java.util.Optional

object RecognitionResultParser {
    @JvmStatic
    fun parseText(json: String?): Optional<String> {
        if (json == null || json.trim { it <= ' ' }.isEmpty()) {
            return Optional.empty()
        }
        try {
            val text: String = JSONObject(json).optString("text", "").trim { it <= ' ' }
            return if (text.isEmpty()) Optional.empty() else Optional.of(text)
        } catch (ignored: JSONException) {
            return Optional.empty()
        }
    }
}
