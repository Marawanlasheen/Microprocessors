package com.example.tomasulo.config;

public class SimulationConfig {
    private final LatencyConfig latencyConfig;
    private final CacheConfig cacheConfig;
    private final StationSizeConfig stationSizeConfig;
    private final int integerRegisterCount;
    private final int floatRegisterCount;

    public SimulationConfig(LatencyConfig latencyConfig, CacheConfig cacheConfig, StationSizeConfig stationSizeConfig,
                            int integerRegisterCount, int floatRegisterCount) {
        this.latencyConfig = latencyConfig;
        this.cacheConfig = cacheConfig;
        this.stationSizeConfig = stationSizeConfig;
        this.integerRegisterCount = integerRegisterCount;
        this.floatRegisterCount = floatRegisterCount;
    }

    public LatencyConfig getLatencyConfig() { return latencyConfig; }
    public CacheConfig getCacheConfig() { return cacheConfig; }
    public StationSizeConfig getStationSizeConfig() { return stationSizeConfig; }
    public int getIntegerRegisterCount() { return integerRegisterCount; }
    public int getFloatRegisterCount() { return floatRegisterCount; }
}
