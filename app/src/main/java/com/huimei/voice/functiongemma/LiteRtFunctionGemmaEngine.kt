package com.huimei.voice.functiongemma

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

class LiteRtFunctionGemmaEngine(private val cacheDir: File) : FunctionGemmaEngine {
    private var engine: Engine? = null

    @Synchronized
    override fun initialize(modelFile: File) {
        close()
        ensureCacheDirectory(cacheDir)
        val candidate = Engine(createEngineConfig(modelFile, cacheDir))
        try {
            candidate.initialize()
            engine = candidate
        } catch (failure: Throwable) {
            closeAfterFailedInitialization(candidate, failure)
            throw failure
        }
    }

    @Synchronized
    override fun infer(text: String): FunctionGemmaInference {
        val current = engine
        if (current == null || !current.isInitialized()) {
            throw IllegalStateException("FunctionGemma engine is not initialized")
        }
        require(text.isNotBlank()) { "text must not be blank" }

        current.createConversation(createConversationConfig()).use { conversation ->
            val response = conversation.sendMessage(text.trim())
            val calls = response.toolCalls.map { FunctionGemmaToolCall(it.name, it.arguments) }
            return FunctionGemmaInference(calls, response.toString())
        }
    }

    @Synchronized
    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        private const val FUNCTION_CALLING_SYSTEM_INSTRUCTION =
            "You are a model that can do function calling with the following functions"

        @JvmStatic
        fun createEngineConfig(modelFile: File, cacheDir: File): EngineConfig = EngineConfig(
            modelFile.absolutePath,
            Backend.CPU(4, null),
            null,
            null,
            1024,
            null,
            cacheDir.absolutePath
        )

        @JvmStatic
        fun createConversationConfig(): ConversationConfig = ConversationConfig(
            Contents.of(FUNCTION_CALLING_SYSTEM_INSTRUCTION),
            emptyList(),
            FunctionGemmaToolSchema.toolProviders(),
            SamplerConfig(1, 1.0, 0.0, 0),
            false,
            emptyList(),
            emptyMap(),
            null,
            false,
            64
        )

        @JvmStatic
        fun closeAfterFailedInitialization(candidate: AutoCloseable, initializeFailure: Throwable) {
            try {
                candidate.close()
            } catch (closeFailure: Throwable) {
                initializeFailure.addSuppressed(closeFailure)
            }
        }

        @JvmStatic
        fun ensureCacheDirectory(cacheDir: File) {
            if (cacheDir.isDirectory) return
            if (cacheDir.exists() || !cacheDir.mkdirs()) {
                throw IllegalStateException("Unable to create LiteRT-LM cache directory: $cacheDir")
            }
        }
    }
}
