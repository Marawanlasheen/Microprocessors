package com.example.tomasulo.core;

import java.util.LinkedList;
import java.util.Queue;

// Simplified CDB arbitration: single winner per cycle (FIFO priority)
public class CommonDataBus {
    private final Queue<CdbResult> pending = new LinkedList<>();

    public void requestPublish(String tag, long value) {
        pending.add(new CdbResult(tag, value));
    }

    public CdbResult arbitrate() {
        return pending.poll(); // one per cycle
    }

    public static class CdbResult {
        public final String tag;
        public final long value;
        public CdbResult(String tag, long value) { this.tag = tag; this.value = value; }
    }
}
