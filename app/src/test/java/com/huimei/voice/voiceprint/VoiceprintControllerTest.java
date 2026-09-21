package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class VoiceprintControllerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private VoiceprintRepository repository;
    private FakeEngine engine;
    private FakeRecorder recorder;
    private ManualExecutorService worker;
    private RecordingListener listener;
    private VoiceprintController controller;

    @Before
    public void setUp() throws Exception {
        repository = new VoiceprintRepository(temporaryFolder.newFolder("voiceprint"));
        engine = new FakeEngine("test-model-v1");
        recorder = new FakeRecorder();
        worker = new ManualExecutorService();
        listener = new RecordingListener();
        controller = new VoiceprintController(
                repository,
                engine,
                recorder,
                worker,
                Runnable::run,
                listener);
    }

    @After
    public void tearDown() {
        controller.close();
    }

    @Test
    public void initializesAndPersistsExactlyThreeAcceptedEnrollmentClips() throws Exception {
        initialize();
        recorder.enqueue(audible((short) 1000));
        recorder.enqueue(audible((short) 1100));
        recorder.enqueue(audible((short) 1200));

        assertTrue(controller.recordEnrollment("Alice"));
        worker.runNext();
        assertTrue(controller.recordEnrollment("Alice"));
        worker.runNext();
        assertTrue(controller.recordEnrollment("Alice"));
        worker.runNext();
        assertEquals(3, listener.lastEnrollmentCount);

        assertTrue(controller.finishEnrollment());
        worker.runNext();

        assertEquals(1, repository.listProfiles().size());
        assertEquals(3, repository.loadEnrollment(
                repository.listProfiles().get(0).getId()).size());
        assertTrue(repository.loadEmbedding(
                repository.listProfiles().get(0).getId(),
                VoiceprintEngineType.SHERPA,
                engine.modelId(),
                engine.dimension()).isPresent());
        assertEquals(3, engine.extractCalls);
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    @Test
    public void rejectedSilentClipDoesNotIncrementEnrollmentProgress() {
        initialize();
        recorder.enqueue(new short[5 * 16000]);

        assertTrue(controller.recordEnrollment("Alice"));
        worker.runNext();

        assertEquals(0, listener.lastEnrollmentCount);
        assertNotNull(listener.lastRejectedQuality);
        assertEquals(VoiceprintAudioQuality.Reason.TOO_QUIET,
                listener.lastRejectedQuality.getReason());
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    @Test
    public void identificationUsesCachedCurrentModelVector() throws Exception {
        initialize();
        VoiceprintProfile profile = repository.saveEnrollment(
                "Alice", enrollmentSamples(), 16000);
        repository.saveEmbedding(
                profile.getId(), engine.type(), engine.modelId(), new float[]{1f, 0f});
        recorder.enqueue(audible((short) 1000));

        assertTrue(controller.identify());
        worker.runNext();

        assertNotNull(listener.lastMatch);
        assertTrue(listener.lastMatch.isAccepted());
        assertEquals(profile, listener.lastMatch.getProfile());
        assertEquals(1, engine.extractCalls);
    }

    @Test
    public void modelVersionMismatchRegeneratesVectorFromSharedWavs() throws Exception {
        initialize();
        VoiceprintProfile profile = repository.saveEnrollment(
                "Alice", enrollmentSamples(), 16000);
        repository.saveEmbedding(
                profile.getId(), engine.type(), "old-model", new float[]{0f, 1f});
        recorder.enqueue(audible((short) 1000));

        assertTrue(controller.identify());
        worker.runNext();

        assertEquals(4, engine.extractCalls);
        assertTrue(repository.loadEmbedding(
                profile.getId(), engine.type(), engine.modelId(), engine.dimension()).isPresent());
        assertTrue(listener.lastMatch.isAccepted());
    }

    @Test
    public void identifyWithoutProfilesFailsBeforeRecordingAndRecoversToIdle() {
        initialize();

        assertTrue(controller.identify());
        worker.runNext();

        assertEquals(0, recorder.captureCalls);
        assertTrue(listener.lastError.contains("录入"));
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    @Test
    public void rejectsConcurrentOperationAndCancellationInvalidatesQueuedWork() {
        initialize();
        recorder.enqueue(audible((short) 1000));

        assertTrue(controller.recordEnrollment("Alice"));
        assertFalse(controller.identify());
        controller.cancelActiveOperation();
        worker.runNext();

        assertEquals(0, recorder.captureCalls);
        assertEquals(0, listener.lastEnrollmentCount);
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
        assertTrue(recorder.cancelCalls > 0);
    }

    @Test
    public void staleMainThreadCallbackIsIgnoredAfterCancellation() {
        ManualExecutor callbackExecutor = new ManualExecutor();
        controller.close();
        worker = new ManualExecutorService();
        listener = new RecordingListener();
        controller = new VoiceprintController(
                repository,
                engine,
                recorder,
                worker,
                callbackExecutor,
                listener);

        controller.initialize();
        worker.runNext();
        controller.cancelActiveOperation();
        callbackExecutor.runAll();

        assertEquals(0, listener.modelReadyCalls);
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    @Test
    public void recorderFailureReportsErrorAndRecoversToIdle() {
        initialize();
        recorder.failure = new IllegalStateException("microphone failed");

        assertTrue(controller.recordEnrollment("Alice"));
        worker.runNext();

        assertTrue(listener.lastError.contains("microphone failed"));
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    @Test
    public void verifyMatchesOnlyTheSelectedProfile() throws Exception {
        initialize();
        VoiceprintProfile alice = repository.saveEnrollment(
                "Alice", enrollmentSamples(), 16000);
        VoiceprintProfile bob = repository.saveEnrollment(
                "Bob", enrollmentSamples(), 16000);
        repository.saveEmbedding(
                alice.getId(), engine.type(), engine.modelId(), new float[]{1f, 0f});
        repository.saveEmbedding(
                bob.getId(), engine.type(), engine.modelId(), new float[]{0f, 1f});
        recorder.enqueue(audible((short) 1000));

        assertTrue(controller.verify(bob.getId()));
        worker.runNext();

        assertNotNull(listener.lastMatch);
        assertFalse(listener.lastMatch.isAccepted());
    }

    @Test
    public void deleteProfileRemovesOnlyTheSelectedIdentity() throws Exception {
        initialize();
        VoiceprintProfile alice = repository.saveEnrollment(
                "Alice", enrollmentSamples(), 16000);
        VoiceprintProfile bob = repository.saveEnrollment(
                "Bob", enrollmentSamples(), 16000);

        assertTrue(controller.deleteProfile(alice.getId()));
        worker.runNext();

        assertEquals(1, repository.listProfiles().size());
        assertEquals(bob, repository.listProfiles().get(0));
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    @Test
    public void clearAllRemovesEveryPersistedIdentity() throws Exception {
        initialize();
        repository.saveEnrollment("Alice", enrollmentSamples(), 16000);
        repository.saveEnrollment("Bob", enrollmentSamples(), 16000);

        assertTrue(controller.clearAll());
        worker.runNext();

        assertTrue(repository.listProfiles().isEmpty());
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    private void initialize() {
        assertTrue(controller.initialize());
        worker.runNext();
        assertEquals(1, listener.modelReadyCalls);
        assertEquals(VoiceprintController.State.IDLE, controller.getState());
    }

    private static List<short[]> enrollmentSamples() {
        List<short[]> samples = new ArrayList<>();
        samples.add(audible((short) 1000));
        samples.add(audible((short) 1100));
        samples.add(audible((short) 1200));
        return samples;
    }

    private static short[] audible(short amplitude) {
        short[] samples = new short[5 * 16000];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((i & 1) == 0 ? amplitude : -amplitude);
        }
        return samples;
    }

    private static final class FakeEngine implements VoiceprintEngine {
        private final String modelId;
        private boolean initialized;
        private int extractCalls;

        private FakeEngine(String modelId) {
            this.modelId = modelId;
        }

        @Override
        public VoiceprintEngineType type() {
            return VoiceprintEngineType.SHERPA;
        }

        @Override
        public String modelId() {
            return modelId;
        }

        @Override
        public int dimension() {
            return 2;
        }

        @Override
        public void initialize() {
            initialized = true;
        }

        @Override
        public float[] extract(short[] pcm16, int sampleRate) {
            if (!initialized) {
                throw new IllegalStateException("not initialized");
            }
            extractCalls++;
            return pcm16[0] >= 0 ? new float[]{1f, 0f} : new float[]{0f, 1f};
        }

        @Override
        public void close() {
            initialized = false;
        }
    }

    private static final class FakeRecorder implements VoiceprintRecorder {
        private final Deque<short[]> recordings = new ArrayDeque<>();
        private RuntimeException failure;
        private int captureCalls;
        private int cancelCalls;

        private void enqueue(short[] recording) {
            recordings.add(recording);
        }

        @Override
        public short[] capture() {
            captureCalls++;
            if (failure != null) {
                throw failure;
            }
            return recordings.removeFirst();
        }

        @Override
        public void cancel() {
            cancelCalls++;
        }
    }

    private static final class RecordingListener implements VoiceprintListener {
        private int modelReadyCalls;
        private int lastEnrollmentCount;
        private String lastError = "";
        private VoiceprintAudioQuality.Result lastRejectedQuality;
        private VoiceprintMatch lastMatch;

        @Override
        public void onStateChanged(VoiceprintController.State state) {
        }

        @Override
        public void onModelReady(long loadMillis, int dimension) {
            modelReadyCalls++;
        }

        @Override
        public void onProfilesChanged(List<VoiceprintProfile> profiles) {
        }

        @Override
        public void onEnrollmentProgress(
                int acceptedSamples,
                int requiredSamples,
                VoiceprintAudioQuality.Result quality) {
            lastEnrollmentCount = acceptedSamples;
        }

        @Override
        public void onAudioRejected(VoiceprintAudioQuality.Result quality) {
            lastRejectedQuality = quality;
        }

        @Override
        public void onMatch(VoiceprintMatch match, long inferenceMillis) {
            lastMatch = match;
        }

        @Override
        public void onError(String message) {
            lastError = message;
        }

        @Override
        public void onLog(String message) {
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
    }

    private static final class ManualExecutorService extends AbstractExecutorService {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new IllegalStateException("executor shut down");
            }
            tasks.addLast(command);
        }

        private void runNext() {
            assertFalse("No queued worker task", tasks.isEmpty());
            tasks.removeFirst().run();
        }
    }
}
