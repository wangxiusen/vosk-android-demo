package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LatestOperationGateTest {
    @Test
    public void onlyTheLatestTokenRemainsCurrent() {
        LatestOperationGate gate = new LatestOperationGate();
        long first = gate.next();
        long second = gate.next();

        assertFalse(gate.isCurrent(first));
        assertTrue(gate.isCurrent(second));
    }

    @Test
    public void closeInvalidatesCurrentAndFutureOperations() {
        LatestOperationGate gate = new LatestOperationGate();
        long token = gate.next();

        gate.close();

        assertFalse(gate.isCurrent(token));
        assertFalse(gate.isCurrent(gate.next()));
    }
}
