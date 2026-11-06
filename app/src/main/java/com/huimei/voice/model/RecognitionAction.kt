package com.huimei.voice.model

class RecognitionAction private constructor(
    val type: Type?,
    val match: CommandMatch?,
    val originalText: String?
) {
    enum class Type {
        NONE,
        WOKE_UP,
        COMMAND,
        TIMED_OUT
    }

    companion object {
        private val NONE = RecognitionAction(Type.NONE, null, null)
        private val TIMED_OUT = RecognitionAction(Type.TIMED_OUT, null, null)

        @JvmStatic
        fun none(): RecognitionAction {
            return NONE
        }

        @JvmStatic
        fun wokeUp(match: CommandMatch?, originalText: String?): RecognitionAction {
            return RecognitionAction(Type.WOKE_UP, match, originalText)
        }

        @JvmStatic
        fun command(match: CommandMatch?, originalText: String?): RecognitionAction {
            return RecognitionAction(Type.COMMAND, match, originalText)
        }

        @JvmStatic
        fun timedOut(): RecognitionAction {
            return TIMED_OUT
        }
    }
}
