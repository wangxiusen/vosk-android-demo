package com.huimei.voice.functiongemma

import com.huimei.voice.model.CommandEvent
import java.util.Optional

class HybridCommandResult private constructor(
    private val route: Route,
    private val event: CommandEvent?,
    private val inference: FunctionGemmaInference?,
    private val elapsedMillis: Long,
    private val reason: String
) {
    enum class Route { EXACT, LOCAL_NATURAL, MODEL_ACCEPTED, REJECTED }

    fun route(): Route = route
    fun event(): Optional<CommandEvent> = Optional.ofNullable(event)
    fun inference(): Optional<FunctionGemmaInference> = Optional.ofNullable(inference)
    fun elapsedMillis(): Long = elapsedMillis
    fun reason(): String = reason

    companion object {
        @JvmStatic
        fun exact(event: CommandEvent) = HybridCommandResult(Route.EXACT, event, null, 0L, "exact")

        @JvmStatic
        fun localNatural(event: CommandEvent) =
            HybridCommandResult(Route.LOCAL_NATURAL, event, null, 0L, "local_natural")

        @JvmStatic
        fun modelAccepted(event: CommandEvent, inference: FunctionGemmaInference, elapsedMillis: Long) =
            HybridCommandResult(Route.MODEL_ACCEPTED, event, inference, elapsedMillis, "accepted")

        @JvmStatic
        fun rejected(reason: String, inference: FunctionGemmaInference?, elapsedMillis: Long) =
            HybridCommandResult(Route.REJECTED, null, inference, elapsedMillis, reason)
    }
}
