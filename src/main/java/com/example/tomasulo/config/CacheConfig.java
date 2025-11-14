package com.example.tomasulo.config;

public class CacheConfig {
    private final int blockSizeBytes; // size of a block in bytes
    private final int cacheSizeBytes; // total data cache size
    private final int hitLatency;     // cycles for a hit beyond base load latency
    private final int missPenalty;    // additional cycles for a miss (fetch block)

    public CacheConfig(int blockSizeBytes, int cacheSizeBytes, int hitLatency, int missPenalty) {
        this.blockSizeBytes = blockSizeBytes;
        this.cacheSizeBytes = cacheSizeBytes;
        this.hitLatency = hitLatency;
        this.missPenalty = missPenalty;
    }

    public int getBlockSizeBytes() { return blockSizeBytes; }
    public int getCacheSizeBytes() { return cacheSizeBytes; }
    public int getHitLatency() { return hitLatency; }
    public int getMissPenalty() { return missPenalty; }

    public int getBlockCount() { return cacheSizeBytes / blockSizeBytes; }
}
