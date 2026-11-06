package com.huimei.voice.functiongemma

import java.util.Collections
import java.util.LinkedHashMap

class FunctionGemmaToolCall(private val name: String, arguments: Map<String, Any?>?) {
    private val arguments = arguments?.let { Collections.unmodifiableMap(LinkedHashMap(it)) }

    fun name(): String = name
    fun arguments(): Map<String, Any?>? = arguments
    override fun toString(): String = name + arguments.toString()
}
