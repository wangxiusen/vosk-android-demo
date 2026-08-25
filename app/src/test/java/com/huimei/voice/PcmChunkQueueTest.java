package com.huimei.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PcmChunkQueueTest {
    @Test
    public void deliversCopiedChunksInFifoOrderThenEndMarker() throws Exception {
        QueueAccess queue = QueueAccess.create(3);
        float[] first = {0.1f, 0.2f};
        float[] second = {0.3f};

        assertTrue(queue.offer(first));
        assertTrue(queue.offer(second));
        first[0] = 9f;
        queue.finish();

        assertArrayEquals(new float[]{0.1f, 0.2f}, queue.take(), 0f);
        assertArrayEquals(new float[]{0.3f}, queue.take(), 0f);
        assertSame(queue.endMarker(), queue.take());
    }

    @Test
    public void rejectsChunksBeyondItsFixedCapacity() throws Exception {
        QueueAccess queue = QueueAccess.create(1);

        assertTrue(queue.offer(new float[]{0.1f}));
        assertFalse(queue.offer(new float[]{0.2f}));
        assertArrayEquals(new float[]{0.1f}, queue.take(), 0f);
    }

    @Test
    public void cancellationClearsDataAndWakesAWaitingConsumer() throws Exception {
        QueueAccess queue = QueueAccess.create(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<float[]> waitingTake = executor.submit(queue::take);
            try {
                waitingTake.get(100, TimeUnit.MILLISECONDS);
                fail("take should wait while the queue is open and empty");
            } catch (TimeoutException expected) {
                // Waiting is the behavior under test.
            }

            queue.cancel();

            assertNull(waitingTake.get(1, TimeUnit.SECONDS));
            assertFalse(queue.offer(new float[]{0.1f}));
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class QueueAccess {
        private final Object queue;
        private final Method offer;
        private final Method take;
        private final Method finish;
        private final Method cancel;
        private final float[] endMarker;

        private QueueAccess(Class<?> type, Object queue) throws Exception {
            this.queue = queue;
            offer = type.getMethod("offer", float[].class);
            take = type.getMethod("take");
            finish = type.getMethod("finish");
            cancel = type.getMethod("cancel");
            endMarker = (float[]) type.getField("END_OF_STREAM").get(null);
        }

        static QueueAccess create(int capacity) throws Exception {
            Class<?> type;
            try {
                type = Class.forName("com.huimei.voice.tts.PcmChunkQueue");
            } catch (ClassNotFoundException error) {
                fail("PcmChunkQueue is required");
                return null;
            }
            return new QueueAccess(type, type.getConstructor(int.class).newInstance(capacity));
        }

        boolean offer(float[] samples) throws Exception {
            return (boolean) invoke(offer, samples);
        }

        float[] take() throws Exception {
            return (float[]) invoke(take);
        }

        void finish() throws Exception {
            invoke(finish);
        }

        void cancel() throws Exception {
            invoke(cancel);
        }

        float[] endMarker() {
            return endMarker;
        }

        private Object invoke(Method method, Object... arguments) throws Exception {
            try {
                return method.invoke(queue, arguments);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw error;
            }
        }
    }
}
