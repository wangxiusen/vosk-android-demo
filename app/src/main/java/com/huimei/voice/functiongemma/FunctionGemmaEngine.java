package com.huimei.voice.functiongemma;

import java.io.File;

public interface FunctionGemmaEngine extends AutoCloseable {
    void initialize(File modelFile) throws Exception;

    FunctionGemmaInference infer(String text) throws Exception;

    @Override
    void close();
}
