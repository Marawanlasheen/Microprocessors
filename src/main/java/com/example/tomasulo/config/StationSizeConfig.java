package com.example.tomasulo.config;

public class StationSizeConfig {
    private final int fpAddStations;
    private final int fpMulStations;
    private final int intStations;
    private final int loadBuffers;
    private final int storeBuffers;
    private final int branchStations;

    public StationSizeConfig(int fpAddStations, int fpMulStations, int intStations,
                             int loadBuffers, int storeBuffers, int branchStations) {
        this.fpAddStations = fpAddStations;
        this.fpMulStations = fpMulStations;
        this.intStations = intStations;
        this.loadBuffers = loadBuffers;
        this.storeBuffers = storeBuffers;
        this.branchStations = branchStations;
    }

    public int getFpAddStations() { return fpAddStations; }
    public int getFpMulStations() { return fpMulStations; }
    public int getIntStations() { return intStations; }
    public int getLoadBuffers() { return loadBuffers; }
    public int getStoreBuffers() { return storeBuffers; }
    public int getBranchStations() { return branchStations; }
}
