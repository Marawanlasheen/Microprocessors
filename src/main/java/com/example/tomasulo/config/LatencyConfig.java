package com.example.tomasulo.config;

public class LatencyConfig {
    private final int addLatency;
    private final int subLatency;
    private final int mulLatency;
    private final int divLatency;
    private final int loadLatency; // time to access cache (hit)
    private final int storeLatency;
    private final int branchLatency;

    public LatencyConfig(int addLatency, int subLatency, int mulLatency, int divLatency,
                         int loadLatency, int storeLatency, int branchLatency) {
        this.addLatency = addLatency;
        this.subLatency = subLatency;
        this.mulLatency = mulLatency;
        this.divLatency = divLatency;
        this.loadLatency = loadLatency;
        this.storeLatency = storeLatency;
        this.branchLatency = branchLatency;
    }

    public int getAddLatency() { return addLatency; }
    public int getSubLatency() { return subLatency; }
    public int getMulLatency() { return mulLatency; }
    public int getDivLatency() { return divLatency; }
    public int getLoadLatency() { return loadLatency; }
    public int getStoreLatency() { return storeLatency; }
    public int getBranchLatency() { return branchLatency; }
}
