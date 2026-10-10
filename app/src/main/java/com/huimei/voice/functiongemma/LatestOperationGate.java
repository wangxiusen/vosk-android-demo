package com.huimei.voice.functiongemma;

public final class LatestOperationGate {
    private long generation;
    private boolean closed;

    public synchronized long next() {
        return ++generation;
    }

    public synchronized boolean isCurrent(long token) {
        return !closed && token == generation;
    }

    public synchronized void close() {
        if (!closed) {
            closed = true;
            generation++;
        }
    }
}
