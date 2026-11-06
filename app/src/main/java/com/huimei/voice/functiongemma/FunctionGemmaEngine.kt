package com.huimei.voice.functiongemma

import java.io.File

interface FunctionGemmaEngine : AutoCloseable {
    fun initialize(modelFile: File)
    fun infer(text: String): FunctionGemmaInference
    override fun close()
}
