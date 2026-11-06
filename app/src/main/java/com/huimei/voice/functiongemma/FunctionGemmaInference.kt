package com.huimei.voice.functiongemma

import java.util.Collections

class FunctionGemmaInference(toolCalls: List<FunctionGemmaToolCall>?, rawOutput: String?) {
    private val calls = Collections.unmodifiableList(ArrayList(toolCalls ?: emptyList()))
    private val output = rawOutput ?: ""

    fun toolCalls(): List<FunctionGemmaToolCall> = calls
    fun rawOutput(): String = output
}
