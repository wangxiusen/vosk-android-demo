package com.huimei.voice.functiongemma;

import com.huimei.voice.model.CommandEvent;

import java.util.Optional;

public final class HybridCommandResult {
    public enum Route {
        EXACT,
        LOCAL_NATURAL,
        MODEL_ACCEPTED,
        REJECTED
    }

    private final Route route;
    private final CommandEvent event;
    private final FunctionGemmaInference inference;
    private final long elapsedMillis;
    private final String reason;

    private HybridCommandResult(
            Route route,
            CommandEvent event,
            FunctionGemmaInference inference,
            long elapsedMillis,
            String reason) {
        this.route = route;
        this.event = event;
        this.inference = inference;
        this.elapsedMillis = elapsedMillis;
        this.reason = reason;
    }

    public static HybridCommandResult exact(CommandEvent event) {
        return new HybridCommandResult(Route.EXACT, event, null, 0L, "exact");
    }

    public static HybridCommandResult localNatural(CommandEvent event) {
        return new HybridCommandResult(
                Route.LOCAL_NATURAL, event, null, 0L, "local_natural");
    }

    public static HybridCommandResult modelAccepted(
            CommandEvent event, FunctionGemmaInference inference, long elapsedMillis) {
        return new HybridCommandResult(
                Route.MODEL_ACCEPTED, event, inference, elapsedMillis, "accepted");
    }

    public static HybridCommandResult rejected(
            String reason, FunctionGemmaInference inference, long elapsedMillis) {
        return new HybridCommandResult(
                Route.REJECTED, null, inference, elapsedMillis, reason);
    }

    public Route route() {
        return route;
    }

    public Optional<CommandEvent> event() {
        return Optional.ofNullable(event);
    }

    public Optional<FunctionGemmaInference> inference() {
        return Optional.ofNullable(inference);
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public String reason() {
        return reason;
    }
}
