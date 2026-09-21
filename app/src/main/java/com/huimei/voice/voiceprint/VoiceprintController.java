package com.huimei.voice.voiceprint;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VoiceprintController implements AutoCloseable {
    public static final int REQUIRED_ENROLLMENT_SAMPLES = 3;

    public enum State {
        NEW,
        LOADING_MODEL,
        IDLE,
        RECORDING,
        PROCESSING,
        CLOSED
    }

    private final Object lock = new Object();
    private final VoiceprintRepository repository;
    private final VoiceprintEngine engine;
    private final VoiceprintRecorder recorder;
    private final ExecutorService worker;
    private final Executor callbackExecutor;
    private final VoiceprintListener listener;
    private final List<short[]> pendingEnrollment = new ArrayList<>();

    private volatile State state = State.NEW;
    private long generation;
    private boolean initialized;
    private boolean closed;
    private String pendingDisplayName;

    public VoiceprintController(
            Context context,
            VoiceprintRepository repository,
            VoiceprintEngine engine,
            VoiceprintListener listener) {
        this(
                repository,
                engine,
                new VoiceprintAudioRecorder(),
                Executors.newSingleThreadExecutor(),
                new MainThreadExecutor(),
                listener);
    }

    VoiceprintController(
            VoiceprintRepository repository,
            VoiceprintEngine engine,
            VoiceprintRecorder recorder,
            ExecutorService worker,
            Executor callbackExecutor,
            VoiceprintListener listener) {
        if (repository == null
                || engine == null
                || recorder == null
                || worker == null
                || callbackExecutor == null
                || listener == null) {
            throw new IllegalArgumentException("Voiceprint controller dependencies are required");
        }
        this.repository = repository;
        this.engine = engine;
        this.recorder = recorder;
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
            state = State.LOADING_MODEL;
        }
        post(token, () -> listener.onStateChanged(State.LOADING_MODEL));
        worker.execute(() -> {
            long startedAt = System.nanoTime();
            try {
                if (!isActive(token)) {
                    return;
                }
                engine.initialize();
                int dimension = engine.dimension();
                if (dimension <= 0) {
                    throw new IllegalStateException("声纹模型维度无效");
                }
                List<VoiceprintProfile> profiles = repository.listProfiles();
                synchronized (lock) {
                    if (!isActiveLocked(token)) {
                        return;
                    }
                    initialized = true;
                    state = State.IDLE;
                }
                long loadMillis = elapsedMillis(startedAt);
                post(token, () -> listener.onModelReady(loadMillis, dimension));
                post(token, () -> listener.onProfilesChanged(profiles));
                post(token, () -> listener.onStateChanged(State.IDLE));
            } catch (Exception exception) {
                finishError(token, exception, State.NEW);
            }
        });
        return true;
    }

    public boolean recordEnrollment(String displayName) {
        String checkedName;
        try {
            checkedName = requireDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            postCurrentError(exception.getMessage());
            return false;
        }
        synchronized (lock) {
            if (!canStartLocked()) {
                return false;
            }
            if (pendingEnrollment.size() >= REQUIRED_ENROLLMENT_SAMPLES) {
                postCurrentError("已采集 3 条音频，请完成录入或取消后重录");
                return false;
            }
            if (pendingDisplayName != null
                    && !pendingDisplayName.toLowerCase(Locale.ROOT)
                    .equals(checkedName.toLowerCase(Locale.ROOT))) {
                postCurrentError("本轮录入姓名不能变更");
                return false;
            }
        }
        long token = beginOperation(State.RECORDING);
        if (token < 0) {
            return false;
        }
        worker.execute(() -> {
            try {
                requireActive(token);
                short[] pcm = recorder.capture();
                requireActive(token);
                VoiceprintAudioQuality.Result quality =
                        VoiceprintAudioQuality.analyze(pcm, VoiceprintAudioRecorder.SAMPLE_RATE);
                if (!quality.isAccepted()) {
                    post(token, () -> listener.onAudioRejected(quality));
                    post(token, () -> listener.onLog("录入音频未通过质量检查：" + quality.getReason()));
                    finishIdle(token);
                    return;
                }
                int count;
                synchronized (lock) {
                    if (!isActiveLocked(token)) {
                        return;
                    }
                    pendingDisplayName = checkedName;
                    pendingEnrollment.add(pcm.clone());
                    count = pendingEnrollment.size();
                }
                int acceptedCount = count;
                post(token, () -> listener.onEnrollmentProgress(
                        acceptedCount, REQUIRED_ENROLLMENT_SAMPLES, quality));
                post(token, () -> listener.onLog(
                        "录入样本 " + acceptedCount + "/" + REQUIRED_ENROLLMENT_SAMPLES + " 合格"));
                finishIdle(token);
            } catch (CancellationException ignored) {
                // Cancellation advances the generation, so this operation has no callbacks.
            } catch (Exception exception) {
                finishError(token, exception, State.IDLE);
            }
        });
        return true;
    }

    public boolean finishEnrollment() {
        final String displayName;
        final List<short[]> samples = new ArrayList<>();
        synchronized (lock) {
            if (!canStartLocked()) {
                return false;
            }
            if (pendingDisplayName == null
                    || pendingEnrollment.size() != REQUIRED_ENROLLMENT_SAMPLES) {
                postCurrentError("请先完成 3 条合格录音");
                return false;
            }
            displayName = pendingDisplayName;
            for (short[] sample : pendingEnrollment) {
                samples.add(sample.clone());
            }
        }
        long token = beginOperation(State.PROCESSING);
        if (token < 0) {
            return false;
        }
        worker.execute(() -> {
            try {
                VoiceprintProfile profile = repository.saveEnrollment(
                        displayName, samples, VoiceprintAudioRecorder.SAMPLE_RATE);
                List<float[]> vectors = new ArrayList<>();
                for (short[] sample : samples) {
                    requireActive(token);
                    vectors.add(engine.extract(sample, VoiceprintAudioRecorder.SAMPLE_RATE));
                }
                float[] averaged = VoiceprintMatcher.average(vectors);
                repository.saveEmbedding(
                        profile.getId(), engine.type(), engine.modelId(), averaged);
                List<VoiceprintProfile> profiles = repository.listProfiles();
                synchronized (lock) {
                    if (!isActiveLocked(token)) {
                        return;
                    }
                    pendingEnrollment.clear();
                    pendingDisplayName = null;
                }
                post(token, () -> listener.onProfilesChanged(profiles));
                post(token, () -> listener.onLog("已完成声纹录入：" + profile.getDisplayName()));
                finishIdle(token);
            } catch (CancellationException ignored) {
                // Cancellation advances the generation, so this operation has no callbacks.
            } catch (Exception exception) {
                finishError(token, exception, State.IDLE);
            }
        });
        return true;
    }

    public boolean identify() {
        long token = beginOperation(State.RECORDING);
        if (token < 0) {
            return false;
        }
        worker.execute(() -> runMatch(token, null));
        return true;
    }

    public boolean verify(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            postCurrentError("请选择需要验证的人员");
            return false;
        }
        long token = beginOperation(State.RECORDING);
        if (token < 0) {
            return false;
        }
        worker.execute(() -> runMatch(token, profileId));
        return true;
    }

    public boolean deleteProfile(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            postCurrentError("请选择需要删除的人员");
            return false;
        }
        long token = beginOperation(State.PROCESSING);
        if (token < 0) {
            return false;
        }
        worker.execute(() -> {
            try {
                repository.deleteProfile(profileId);
                List<VoiceprintProfile> profiles = repository.listProfiles();
                post(token, () -> listener.onProfilesChanged(profiles));
                post(token, () -> listener.onLog("已删除本地声纹资料"));
                finishIdle(token);
            } catch (Exception exception) {
                finishError(token, exception, State.IDLE);
            }
        });
        return true;
    }

    public boolean clearAll() {
        long token = beginOperation(State.PROCESSING);
        if (token < 0) {
            return false;
        }
        worker.execute(() -> {
            try {
                repository.clearAll();
                synchronized (lock) {
                    pendingEnrollment.clear();
                    pendingDisplayName = null;
                }
                List<VoiceprintProfile> profiles = repository.listProfiles();
                post(token, () -> listener.onProfilesChanged(profiles));
                post(token, () -> listener.onLog("已清空全部本地声纹资料"));
                finishIdle(token);
            } catch (Exception exception) {
                finishError(token, exception, State.IDLE);
            }
        });
        return true;
    }

    public void cancelActiveOperation() {
        final long token;
        final State nextState;
        synchronized (lock) {
            if (closed) {
                return;
            }
            token = ++generation;
            nextState = initialized ? State.IDLE : State.NEW;
            state = nextState;
        }
        recorder.cancel();
        post(token, () -> listener.onStateChanged(nextState));
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
        }
        recorder.cancel();
        worker.shutdownNow();
        engine.close();
    }

    private void runMatch(long token, String requestedProfileId) {
        long inferenceStartedAt = 0L;
        try {
            List<VoiceprintProfile> profiles = repository.listProfiles();
            if (profiles.isEmpty()) {
                throw new UserVisibleException("请先录入至少一位人员的声纹");
            }
            if (requestedProfileId != null) {
                VoiceprintProfile selected = findProfile(profiles, requestedProfileId);
                profiles = new ArrayList<>();
                profiles.add(selected);
            }
            requireActive(token);
            short[] pcm = recorder.capture();
            requireActive(token);
            VoiceprintAudioQuality.Result quality =
                    VoiceprintAudioQuality.analyze(pcm, VoiceprintAudioRecorder.SAMPLE_RATE);
            if (!quality.isAccepted()) {
                post(token, () -> listener.onAudioRejected(quality));
                finishIdle(token);
                return;
            }
            transition(token, State.PROCESSING);
            inferenceStartedAt = System.nanoTime();
            Map<VoiceprintProfile, float[]> enrolled = new LinkedHashMap<>();
            for (VoiceprintProfile profile : profiles) {
                requireActive(token);
                enrolled.put(profile, loadOrRegenerateEmbedding(profile, token));
            }
            float[] probe = engine.extract(pcm, VoiceprintAudioRecorder.SAMPLE_RATE);
            VoiceprintMatch match = VoiceprintMatcher.search(
                    enrolled, probe, engine.type().initialThreshold());
            long inferenceMillis = elapsedMillis(inferenceStartedAt);
            post(token, () -> listener.onMatch(match, inferenceMillis));
            post(token, () -> listener.onLog(match.isAccepted()
                    ? "识别通过：" + match.getProfile().getDisplayName()
                    : "未匹配到已录入人员"));
            finishIdle(token);
        } catch (CancellationException ignored) {
            // Cancellation advances the generation, so this operation has no callbacks.
        } catch (Exception exception) {
            finishError(token, exception, State.IDLE);
        }
    }

    private float[] loadOrRegenerateEmbedding(VoiceprintProfile profile, long token)
            throws Exception {
        Optional<float[]> cached = repository.loadEmbedding(
                profile.getId(), engine.type(), engine.modelId(), engine.dimension());
        if (cached.isPresent()) {
            return cached.get();
        }
        List<WavIO.Audio> audio = repository.loadEnrollment(profile.getId());
        List<float[]> vectors = new ArrayList<>();
        for (WavIO.Audio sample : audio) {
            requireActive(token);
            vectors.add(engine.extract(sample.getSamples(), sample.getSampleRate()));
        }
        float[] regenerated = VoiceprintMatcher.average(vectors);
        repository.saveEmbedding(
                profile.getId(), engine.type(), engine.modelId(), regenerated);
        return regenerated;
    }

    private long beginOperation(State operationState) {
        final long token;
        synchronized (lock) {
            if (!canStartLocked()) {
                return -1L;
            }
            token = ++generation;
            state = operationState;
        }
        post(token, () -> listener.onStateChanged(operationState));
        return token;
    }

    private boolean canStartLocked() {
        return !closed && initialized && state == State.IDLE;
    }

    private void transition(long token, State nextState) {
        synchronized (lock) {
            if (!isActiveLocked(token)) {
                throw new CancellationException();
            }
            state = nextState;
        }
        post(token, () -> listener.onStateChanged(nextState));
    }

    private void finishIdle(long token) {
        synchronized (lock) {
            if (!isActiveLocked(token)) {
                return;
            }
            state = State.IDLE;
        }
        post(token, () -> listener.onStateChanged(State.IDLE));
    }

    private void finishError(long token, Exception exception, State recoveryState) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        synchronized (lock) {
            if (!isActiveLocked(token)) {
                return;
            }
            state = recoveryState;
        }
        String finalMessage = message;
        post(token, () -> listener.onError(finalMessage));
        post(token, () -> listener.onLog("操作失败：" + finalMessage));
        post(token, () -> listener.onStateChanged(recoveryState));
    }

    private void postCurrentError(String message) {
        long token;
        synchronized (lock) {
            if (closed) {
                return;
            }
            token = generation;
        }
        post(token, () -> listener.onError(message));
    }

    private void post(long token, Runnable callback) {
        callbackExecutor.execute(() -> {
            if (isActive(token)) {
                callback.run();
            }
        });
    }

    private void requireActive(long token) {
        if (!isActive(token)) {
            throw new CancellationException();
        }
    }

    private boolean isActive(long token) {
        synchronized (lock) {
            return isActiveLocked(token);
        }
    }

    private boolean isActiveLocked(long token) {
        return !closed && generation == token;
    }

    private static VoiceprintProfile findProfile(
            List<VoiceprintProfile> profiles,
            String profileId) throws IOException {
        for (VoiceprintProfile profile : profiles) {
            if (profile.getId().equals(profileId)) {
                return profile;
            }
        }
        throw new IOException("所选人员不存在");
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入人员姓名");
        }
        return displayName.trim();
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }

    private static final class UserVisibleException extends Exception {
        private UserVisibleException(String message) {
            super(message);
        }
    }
}
