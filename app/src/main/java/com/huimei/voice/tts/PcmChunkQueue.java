package com.huimei.voice.tts;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class PcmChunkQueue {
    public static final float[] END_OF_STREAM = new float[0];

    private final int capacity;
    private final ArrayDeque<float[]> chunks = new ArrayDeque<>();
    private boolean finished;
    private boolean canceled;

    public PcmChunkQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized boolean offer(float[] samples) {
        if (samples == null
                || samples.length == 0
                || finished
                || canceled
                || chunks.size() >= capacity) {
            return false;
        }
        chunks.addLast(Arrays.copyOf(samples, samples.length));
        notifyAll();
        return true;
    }

    public synchronized float[] take() throws InterruptedException {
        while (chunks.isEmpty() && !finished && !canceled) {
            wait();
        }
        if (canceled) {
            return null;
        }
        if (!chunks.isEmpty()) {
            return chunks.removeFirst();
        }
        return END_OF_STREAM;
    }

    public synchronized void finish() {
        finished = true;
        notifyAll();
    }

    public synchronized void cancel() {
        canceled = true;
        chunks.clear();
        notifyAll();
    }
}
