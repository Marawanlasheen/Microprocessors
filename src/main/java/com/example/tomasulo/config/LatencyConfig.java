package com.example.tomasulo.config;

public class LatencyConfig {
    private final int addLatency;
    private final int subLatency;
    private final int mulLatency;
    private final int divLatency;
    private final int branchLatency;

    public LatencyConfig(int addLatency, int subLatency, int mulLatency, int divLatency,
                         int branchLatency) {
        this.addLatency = addLatency;
        this.subLatency = subLatency;
        this.mulLatency = mulLatency;
        this.divLatency = divLatency;
        this.branchLatency = branchLatency;
    }

    public int getAddLatency() { return addLatency; }
    public int getSubLatency() { return subLatency; }
    public int getMulLatency() { return mulLatency; }
    public int getDivLatency() { return divLatency; }
    public int getBranchLatency() { return branchLatency; }
}
