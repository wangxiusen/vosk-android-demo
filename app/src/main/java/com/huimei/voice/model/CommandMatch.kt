package com.huimei.voice.model

import java.util.Objects

class CommandMatch(recognizedPhrase: String?, displayPhrase: String?, event: CommandEvent?) {
    val recognizedPhrase: String
    val displayPhrase: String
    val event: CommandEvent

    init {
        this.recognizedPhrase = Objects.requireNonNull<String>(recognizedPhrase)
        this.displayPhrase = Objects.requireNonNull<String>(displayPhrase)
        this.event = Objects.requireNonNull<CommandEvent>(event)
    }
}
