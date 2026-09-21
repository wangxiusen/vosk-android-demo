package com.huimei.voice.face;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FaceRecognitionController implements AutoCloseable {
    public static final int REQUIRED_ENROLLMENT_SAMPLES = 5;
    public static final long MIN_FRAME_INTERVAL_MILLIS = 150L;
    public static final float INITIAL_MATCH_THRESHOLD = 0.363f;

    public enum State {
        NEW,
        LOADING,
        IDLE,
        ENROLLING,
        RECOGNIZING,
        CLOSED
    }

    private final Object lock = new Object();
    private final FaceRepository repository;
    private final FaceEngine engine;
    private final ExecutorService worker;
    private final Executor callbackExecutor;
    private final FaceRecognitionListener listener;
    private final FaceRecognitionStabilizer stabilizer = new FaceRecognitionStabilizer(3);
    private final List<float[]> enrollmentVectors = new ArrayList<>();

    private volatile State state = State.NEW;
    private long generation;
    private boolean initialized;
    private boolean closed;
    private boolean frameInFlight;
    private long lastFrameTimestamp = Long.MIN_VALUE;
    private String enrollmentName;
    private byte[] enrollmentPortrait;
    private List<FaceProfile> profiles = new ArrayList<>();
    private Map<String, float[]> embeddings = new LinkedHashMap<>();

    public FaceRecognitionController(
            FaceRepository repository,
            FaceEngine engine,
            FaceRecognitionListener listener) {
        this(
                repository,
                engine,
                Executors.newSingleThreadExecutor(),
                new MainThreadExecutor(),
                listener);
    }

    FaceRecognitionController(
            FaceRepository repository,
            FaceEngine engine,
            ExecutorService worker,
            Executor callbackExecutor,
            FaceRecognitionListener listener) {
        if (repository == null || engine == null || worker == null
                || callbackExecutor == null || listener == null) {
            throw new IllegalArgumentException("Face controller dependencies are required");
        }
        this.repository = repository;
        this.engine = engine;
        this.worker = worker;
        this.callbackExecutor = callbackExecutor;
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public boolean initialize() {
        final long token;
        synchronized (lock) {
            if (closed || state != State.NEW) {
                return false;
            }
            token = ++generation;
            state = State.LOADING;
        }
        post(token, () -> listener.onStateChanged(State.LOADING));
        worker.execute(() -> {
            long startedAt = System.nanoTime();
            try {
                requireActive(token);
                engine.initialize();
                int dimension = engine.dimension();
                if (dimension <= 0) {
                    throw new IllegalStateException("人脸模型维度无效");
                }
                Cache loaded = loadCache(dimension);
                synchronized (lock) {
                    requireActiveLocked(token);
                    profiles = loaded.profiles;
                    embeddings = loaded.embeddings;
                    initialized = true;
                    state = State.IDLE;
                }
                post(token, () -> listener.onModelReady(elapsedMillis(startedAt), dimension));
                postProfiles(token, loaded.profiles);
                post(token, () -> listener.onStateChanged(State.IDLE));
            } catch (CancellationException ignored) {
                // Generation changed; stale callbacks are intentionally suppressed.
            } catch (Exception exception) {
                finishError(token, exception, State.NEW);
            }
        });
        return true;
    }

    public boolean startEnrollment(String displayName) {
        String checkedName;
        try {
            checkedName = requireDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            postCurrentError(exception.getMessage());
            return false;
        }
        final long token;
        synchronized (lock) {
            if (!canStartLocked()) {
                return false;
            }
            token = ++generation;
            state = State.ENROLLING;
            resetFrameFlowLocked();
            enrollmentVectors.clear();
            enrollmentName = checkedName;
            enrollmentPortrait = null;
            stabilizer.reset();
        }
        post(token, () -> listener.onStateChanged(State.ENROLLING));
        post(token, () -> listener.onEnrollmentProgress(0, REQUIRED_ENROLLMENT_SAMPLES));
        post(token, () -> listener.onLog("开始录入人脸：" + checkedName));
        return true;
    }

    public boolean startRecognition() {
        final long token;
        synchronized (lock) {
            if (!canStartLocked()) {
                return false;
            }
            if (profiles.isEmpty()) {
                postCurrentError("请先录入至少一位人员的人脸");
                return false;
            }
            token = ++generation;
            state = State.RECOGNIZING;
            resetFrameFlowLocked();
            stabilizer.reset();
        }
        post(token, () -> listener.onStateChanged(State.RECOGNIZING));
        post(token, () -> listener.onLog("开始实时人脸识别"));
        return true;
    }

    public boolean acceptFrame(FaceFrame frame, long timestampMillis) {
        if (frame == null) {
            throw new IllegalArgumentException("Face frame is required");
        }
        final long token;
        final State mode;
        synchronized (lock) {
            if (closed || frameInFlight
                    || (state != State.ENROLLING && state != State.RECOGNIZING)) {
                return false;
            }
            if (lastFrameTimestamp != Long.MIN_VALUE
                    && timestampMillis - lastFrameTimestamp < MIN_FRAME_INTERVAL_MILLIS) {
                return false;
            }
            token = generation;
            mode = state;
            lastFrameTimestamp = timestampMillis;
            frameInFlight = true;
        }
        worker.execute(() -> analyzeFrame(token, mode, frame));
        return true;
    }

    public void stop() {
        final long token;
        synchronized (lock) {
            if (closed) {
                return;
            }
            token = ++generation;
            state = initialized ? State.IDLE : State.NEW;
            enrollmentVectors.clear();
            enrollmentName = null;
            enrollmentPortrait = null;
            resetFrameFlowLocked();
            stabilizer.reset();
        }
        State nextState = state;
        post(token, () -> listener.onStateChanged(nextState));
    }

    public boolean deleteProfile(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            postCurrentError("请选择需要删除的人员");
            return false;
        }
        long token = beginStorageOperation();
        if (token < 0L) {
            return false;
        }
        worker.execute(() -> {
            try {
                repository.deleteProfile(profileId);
                Cache loaded = loadCache(engine.dimension());
                applyCache(token, loaded);
                post(token, () -> listener.onLog("已删除本地人脸资料"));
                finishIdle(token);
            } catch (Exception exception) {
                finishError(token, exception, State.IDLE);
            }
        });
        return true;
    }

    public boolean clearAll() {
        long token = beginStorageOperation();
        if (token < 0L) {
            return false;
        }
        worker.execute(() -> {
            try {
                repository.clearAll();
                Cache loaded = loadCache(engine.dimension());
                applyCache(token, loaded);
                post(token, () -> listener.onLog("已清空全部本地人脸资料"));
                finishIdle(token);
            } catch (Exception exception) {
                finishError(token, exception, State.IDLE);
            }
        });
        return true;
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            generation++;
            state = State.CLOSED;
            resetFrameFlowLocked();
            enrollmentVectors.clear();
        }
        worker.shutdownNow();
        engine.close();
    }

    private void analyzeFrame(long token, State mode, FaceFrame frame) {
        try {
            requireActive(token);
            FaceObservation observation = engine.analyze(frame);
            requireActive(token);
            if (!observation.isQualityAccepted()) {
                if (mode == State.RECOGNIZING) {
                    stabilizer.reset();
                }
                post(token, () -> listener.onFrameRejected(observation.qualityReason()));
                return;
            }
            if (observation.embedding().length != engine.dimension()) {
                throw new IllegalStateException("人脸特征维度与模型不一致");
            }
            if (mode == State.ENROLLING) {
                handleEnrollment(token, observation);
            } else {
                handleRecognition(token, observation);
            }
        } catch (CancellationException ignored) {
            // Generation changed; stale callbacks are intentionally suppressed.
        } catch (Exception exception) {
            finishError(token, exception, State.IDLE);
        } finally {
            synchronized (lock) {
                if (isActiveLocked(token)) {
                    frameInFlight = false;
                }
            }
        }
    }

    private void handleEnrollment(long token, FaceObservation observation) throws Exception {
        final int acceptedCount;
        final String displayName;
        final List<float[]> completedVectors;
        final byte[] portrait;
        synchronized (lock) {
            requireActiveLocked(token);
            enrollmentVectors.add(observation.embedding());
            enrollmentPortrait = observation.portraitJpeg();
            acceptedCount = enrollmentVectors.size();
            displayName = enrollmentName;
            if (acceptedCount == REQUIRED_ENROLLMENT_SAMPLES) {
                completedVectors = cloneVectors(enrollmentVectors);
                portrait = enrollmentPortrait.clone();
            } else {
                completedVectors = null;
                portrait = null;
            }
        }
        post(token, () -> listener.onEnrollmentProgress(
                acceptedCount, REQUIRED_ENROLLMENT_SAMPLES));
        if (completedVectors == null) {
            return;
        }

        float[] averaged = FaceMatcher.averageNormalized(completedVectors);
        FaceProfile profile = repository.saveEnrollment(displayName, averaged, portrait);
        Cache loaded = loadCache(engine.dimension());
        synchronized (lock) {
            requireActiveLocked(token);
            profiles = loaded.profiles;
            embeddings = loaded.embeddings;
            enrollmentVectors.clear();
            enrollmentName = null;
            enrollmentPortrait = null;
            state = State.IDLE;
        }
        postProfiles(token, loaded.profiles);
        post(token, () -> listener.onLog("已完成人脸录入：" + profile.getDisplayName()));
        post(token, () -> listener.onStateChanged(State.IDLE));
    }

    private void handleRecognition(long token, FaceObservation observation) {
        List<FaceProfile> profileSnapshot;
        Map<String, float[]> embeddingSnapshot;
        synchronized (lock) {
            requireActiveLocked(token);
            profileSnapshot = new ArrayList<>(profiles);
            embeddingSnapshot = cloneEmbeddingMap(embeddings);
        }
        FaceMatch match = FaceMatcher.best(
                observation.embedding(), profileSnapshot, embeddingSnapshot,
                INITIAL_MATCH_THRESHOLD);
        post(token, () -> listener.onCandidate(match, observation.inferenceMillis()));
        Optional<FaceMatch> confirmed = stabilizer.accept(match);
        if (confirmed.isPresent()) {
            FaceMatch value = confirmed.get();
            post(token, () -> listener.onMatchConfirmed(value));
            post(token, () -> listener.onLog(
                    "人脸识别通过：" + value.getProfile().getDisplayName()));
        }
    }

    private Cache loadCache(int dimension) throws Exception {
        List<FaceProfile> loadedProfiles = repository.listProfiles();
        Map<String, float[]> loadedEmbeddings = new LinkedHashMap<>();
        for (FaceProfile profile : loadedProfiles) {
            loadedEmbeddings.put(
                    profile.getId(), repository.loadEmbedding(profile.getId(), dimension));
        }
        return new Cache(loadedProfiles, loadedEmbeddings);
    }

    private void applyCache(long token, Cache cache) {
        synchronized (lock) {
            requireActiveLocked(token);
            profiles = cache.profiles;
            embeddings = cache.embeddings;
        }
        postProfiles(token, cache.profiles);
    }

    private long beginStorageOperation() {
        final long token;
        synchronized (lock) {
            if (!canStartLocked()) {
                return -1L;
            }
            token = ++generation;
            state = State.LOADING;
            resetFrameFlowLocked();
        }
        post(token, () -> listener.onStateChanged(State.LOADING));
        return token;
    }

    private void finishIdle(long token) {
        synchronized (lock) {
            requireActiveLocked(token);
            state = State.IDLE;
            resetFrameFlowLocked();
        }
        post(token, () -> listener.onStateChanged(State.IDLE));
    }

    private void finishError(long token, Exception exception, State fallback) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        synchronized (lock) {
            if (!isActiveLocked(token)) {
                return;
            }
            state = fallback;
            resetFrameFlowLocked();
            enrollmentVectors.clear();
            enrollmentName = null;
            enrollmentPortrait = null;
            stabilizer.reset();
        }
        String visibleMessage = message;
        post(token, () -> listener.onError(visibleMessage));
        post(token, () -> listener.onStateChanged(fallback));
    }

    private void postProfiles(long token, List<FaceProfile> values) {
        List<FaceProfile> snapshot = new ArrayList<>(values);
        post(token, () -> listener.onProfilesChanged(snapshot));
    }

    private void postCurrentError(String message) {
        long token;
        synchronized (lock) {
            token = generation;
        }
        post(token, () -> listener.onError(message));
    }

    private void post(long token, Runnable callback) {
        callbackExecutor.execute(() -> {
            synchronized (lock) {
                if (!isActiveLocked(token)) {
                    return;
                }
            }
            callback.run();
        });
    }

    private boolean canStartLocked() {
        return !closed && initialized && state == State.IDLE;
    }

    private void requireActive(long token) {
        synchronized (lock) {
            requireActiveLocked(token);
        }
    }

    private void requireActiveLocked(long token) {
        if (!isActiveLocked(token)) {
            throw new CancellationException();
        }
    }

    private boolean isActiveLocked(long token) {
        return !closed && generation == token;
    }

    private void resetFrameFlowLocked() {
        frameInFlight = false;
        lastFrameTimestamp = Long.MIN_VALUE;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入人员姓名");
        }
        return displayName.trim();
    }

    private static List<float[]> cloneVectors(List<float[]> values) {
        List<float[]> copy = new ArrayList<>();
        for (float[] value : values) {
            copy.add(value.clone());
        }
        return copy;
    }

    private static Map<String, float[]> cloneEmbeddingMap(Map<String, float[]> values) {
        Map<String, float[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : values.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static final class Cache {
        private final List<FaceProfile> profiles;
        private final Map<String, float[]> embeddings;

        private Cache(List<FaceProfile> profiles, Map<String, float[]> embeddings) {
            this.profiles = new ArrayList<>(profiles);
            this.embeddings = cloneEmbeddingMap(embeddings);
        }
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }
}
