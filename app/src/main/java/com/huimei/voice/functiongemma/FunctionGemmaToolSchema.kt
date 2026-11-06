package com.huimei.voice.functiongemma

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool

class FunctionGemmaToolSchema private constructor() {
    companion object {
        private val definitions = listOf(
            """{"name":"power_on","description":"Turn on the oxygen concentrator. 开启制氧机。","parameters":{"type":"object","properties":{},"required":[],"additionalProperties":false}}""",
            """{"name":"power_off","description":"Turn off the oxygen concentrator. 关闭制氧机。","parameters":{"type":"object","properties":{},"required":[],"additionalProperties":false}}""",
            """{"name":"set_gas_duration","description":"Set gas production duration in minutes. 设置制氧时长（分钟）。","parameters":{"type":"object","properties":{"duration_minutes":{"type":"integer","enum":[30,60,120,480]}},"required":["duration_minutes"],"additionalProperties":false}}"""
        )

        @JvmStatic
        fun jsonDefinitions(): String = definitions.joinToString(prefix = "[", postfix = "]", separator = ",")

        @JvmStatic
        fun toolProviders(): List<ToolProvider> = definitions.map { tool(SchemaOnlyTool(it)) }
    }

    private class SchemaOnlyTool(private val definition: String) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = definition

        override fun execute(paramsJsonString: String): String {
            throw UnsupportedOperationException("Automatic tool execution is disabled")
        }
    }
}
