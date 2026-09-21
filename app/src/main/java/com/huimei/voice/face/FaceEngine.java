package com.huimei.voice.face;

public interface FaceEngine extends AutoCloseable {
    void initialize() throws Exception;

    int dimension();

    FaceObservation analyze(FaceFrame frame) throws Exception;

    @Override
    void close();
}
