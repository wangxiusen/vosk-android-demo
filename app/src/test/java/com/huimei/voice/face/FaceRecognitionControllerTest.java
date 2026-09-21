package com.huimei.voice.face;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class FaceRecognitionControllerTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File root;
    private FaceRepository repository;
    private FakeEngine engine;
    private ManualExecutorService worker;
    private RecordingListener listener;
    private FaceRecognitionController controller;

    @Before
    public void setUp() throws Exception {
        root = temporaryFolder.newFolder("faces");
        repository = new FaceRepository(root);
        engine = new FakeEngine();
        worker = new ManualExecutorService();
        listener = new RecordingListener();
        controller = new FaceRecognitionController(
                repository, engine, worker, Runnable::run, listener);
    }

    @After
    public void tearDown() {
        controller.close();
    }

    @Test
    public void fiveAcceptedSamplesPersistAverageAndReturnToIdle() throws Exception {
        initialize();
        for (int i = 0; i < 5; i++) {
            engine.enqueue(accepted(vector(1f, i * 0.01f), i));
        }

        assertTrue(controller.startEnrollment("Alice"));
        for (int i = 0; i < 5; i++) {
            assertTrue(controller.acceptFrame(frame(), 1000L + i * 150L));
            worker.runNext();
        }

        assertEquals(5, listener.lastEnrollmentCount);
        assertEquals(FaceRecognitionController.State.IDLE, controller.getState());
        FaceProfile profile = repository.listProfiles().get(0);
        assertEquals(5, profile.getSampleCount());
        assertEquals(128, repository.loadEmbedding(profile.getId(), 128).length);
        assertEquals(5, engine.analyzeCalls);
    }

    @Test
    public void rejectedSampleDoesNotIncrementAndFrameThrottleDropsWork() {
        initialize();
        engine.enqueue(FaceObservation.rejected(
                0, FaceFrameQuality.Reason.NO_FACE, 0f, 10L));
        engine.enqueue(accepted(vector(1f, 0f), 1));

        assertTrue(controller.startEnrollment("Alice"));
        assertTrue(controller.acceptFrame(frame(), 1000L));
        assertFalse(controller.acceptFrame(frame(), 1200L));
        worker.runNext();
        assertEquals(0, listener.lastEnrollmentCount);
        assertEquals(FaceFrameQuality.Reason.NO_FACE, listener.lastRejectedReason);
        assertFalse(controller.acceptFrame(frame(), 1100L));
        assertTrue(controller.acceptFrame(frame(), 1150L));
        worker.runNext();

        assertEquals(1, listener.lastEnrollmentCount);
        assertEquals(2, engine.analyzeCalls);
    }

    @Test
    public void wrongEngineDimensionFailsBeforePersistingEnrollment() throws Exception {
        initialize();
        engine.enqueue(accepted(new float[]{1f, 0f}, 1));

        assertTrue(controller.startEnrollment("Alice"));
        assertTrue(controller.acceptFrame(frame(), 1000L));
        worker.runNext();

        assertTrue(repository.listProfiles().isEmpty());
        assertTrue(listener.lastError.contains("维度"));
        assertEquals(FaceRecognitionController.State.IDLE, controller.getState());
    }

    @Test
    public void confirmsThirdConsecutiveMatchingPersonOnce() throws Exception {
        saveProfile("Alice", vector(1f, 0f));
        initialize();
        engine.enqueue(accepted(vector(1f, 0f), 1));
        engine.enqueue(accepted(vector(1f, 0f), 2));
        engine.enqueue(accepted(vector(1f, 0f), 3));

        assertTrue(controller.startRecognition());
        analyzeThreeFrames();

        assertEquals(1, listener.confirmed.size());
        assertEquals("Alice", listener.confirmed.get(0).getProfile().getDisplayName());
    }

    @Test
    public void candidateChangesResetConfirmationProgress() throws Exception {
        saveProfile("Alice", vector(1f, 0f));
        saveProfile("Bob", vector(0f, 1f));
        initialize();
        engine.enqueue(accepted(vector(1f, 0f), 1));
        engine.enqueue(accepted(vector(1f, 0f), 2));
        engine.enqueue(accepted(vector(0f, 1f), 3));
        engine.enqueue(accepted(vector(1f, 0f), 4));
        engine.enqueue(accepted(vector(1f, 0f), 5));

        assertTrue(controller.startRecognition());
        for (int i = 0; i < 5; i++) {
            assertTrue(controller.acceptFrame(frame(), 1000L + i * 150L));
            worker.runNext();
        }

        assertTrue(listener.confirmed.isEmpty());
    }

    @Test
    public void recognitionWithoutProfilesIsRejectedBeforeAnalysis() {
        initialize();

        assertFalse(controller.startRecognition());

        assertTrue(listener.lastError.contains("录入"));
        assertEquals(0, engine.analyzeCalls);
        assertEquals(FaceRecognitionController.State.IDLE, controller.getState());
    }

    @Test
    public void repositoryFailureReportsErrorAndRecoversToIdle() throws Exception {
        FaceProfile profile = saveProfile("Alice", vector(1f, 0f));
        initialize();
        try (FileOutputStream output = new FileOutputStream(new File(root, "profiles.json"))) {
            output.write("broken".getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(controller.deleteProfile(profile.getId()));
        worker.runNext();

        assertNotNull(listener.lastError);
        assertEquals(FaceRecognitionController.State.IDLE, controller.getState());
    }

    @Test
    public void closeSuppressesQueuedCallbacks() {
        controller.close();
        worker = new ManualExecutorService();
        ManualExecutor callback = new ManualExecutor();
        listener = new RecordingListener();
        controller = new FaceRecognitionController(
                repository, engine, worker, callback, listener);

        assertTrue(controller.initialize());
        worker.runNext();
        controller.close();
        callback.runAll();

        assertEquals(0, listener.modelReadyCalls);
        assertEquals(FaceRecognitionController.State.CLOSED, controller.getState());
    }

    private void initialize() {
        assertTrue(controller.initialize());
        worker.runNext();
        assertEquals(FaceRecognitionController.State.IDLE, controller.getState());
    }

    private FaceProfile saveProfile(String name, float[] embedding) throws Exception {
        FaceProfile profile = repository.saveEnrollment(name, embedding, new byte[]{1, 2});
        return profile;
    }

    private void analyzeThreeFrames() {
        for (int i = 0; i < 3; i++) {
            assertTrue(controller.acceptFrame(frame(), 1000L + i * 150L));
            worker.runNext();
        }
    }

    private static FaceFrame frame() {
        return new FaceFrame(1, 1, new byte[]{0, 0, 0, (byte) 255});
    }

    private static FaceObservation accepted(float[] vector, int portraitValue) {
        return FaceObservation.accepted(
                vector, new byte[]{(byte) portraitValue}, 0.99f, 10L);
    }

    private static float[] vector(float first, float second) {
        float[] value = new float[128];
        value[0] = first;
        value[1] = second;
        return FaceMatcher.normalize(value);
    }

    private static final class FakeEngine implements FaceEngine {
        private final Deque<FaceObservation> values = new ArrayDeque<>();
        private boolean initialized;
        private int analyzeCalls;

        void enqueue(FaceObservation value) {
            values.add(value);
        }

        @Override public void initialize() {
            initialized = true;
        }

        @Override public int dimension() {
            return 128;
        }

        @Override public FaceObservation analyze(FaceFrame frame) {
            if (!initialized) {
                throw new IllegalStateException("not initialized");
            }
            analyzeCalls++;
            return values.removeFirst();
        }

        @Override public void close() {
            initialized = false;
        }
    }

    private static final class RecordingListener implements FaceRecognitionListener {
        private int modelReadyCalls;
        private int lastEnrollmentCount;
        private FaceFrameQuality.Reason lastRejectedReason;
        private String lastError;
        private final List<FaceMatch> confirmed = new ArrayList<>();

        @Override public void onStateChanged(FaceRecognitionController.State state) {
        }

        @Override public void onModelReady(long loadMillis, int dimension) {
            modelReadyCalls++;
        }

        @Override public void onProfilesChanged(List<FaceProfile> profiles) {
        }

        @Override public void onEnrollmentProgress(int accepted, int required) {
            lastEnrollmentCount = accepted;
        }

        @Override public void onFrameRejected(FaceFrameQuality.Reason reason) {
            lastRejectedReason = reason;
        }

        @Override public void onCandidate(FaceMatch match, long inferenceMillis) {
        }

        @Override public void onMatchConfirmed(FaceMatch match) {
            confirmed.add(match);
        }

        @Override public void onLog(String message) {
        }

        @Override public void onError(String message) {
            lastError = message;
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Deque<Runnable> values = new ArrayDeque<>();

        @Override public void execute(Runnable command) {
            values.add(command);
        }

        void runAll() {
            while (!values.isEmpty()) {
                values.removeFirst().run();
            }
        }
    }

    private static final class ManualExecutorService extends AbstractExecutorService {
        private final Deque<Runnable> values = new ArrayDeque<>();
        private boolean shutdown;

        @Override public void shutdown() {
            shutdown = true;
        }

        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(values);
            values.clear();
            return pending;
        }

        @Override public boolean isShutdown() {
            return shutdown;
        }

        @Override public boolean isTerminated() {
            return shutdown && values.isEmpty();
        }

        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override public void execute(Runnable command) {
            if (shutdown) {
                throw new IllegalStateException("executor shut down");
            }
            values.add(command);
        }

        void runNext() {
            values.removeFirst().run();
        }
    }
}
