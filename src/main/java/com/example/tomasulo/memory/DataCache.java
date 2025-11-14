package com.example.tomasulo.memory;

import com.example.tomasulo.config.CacheConfig;

import java.util.HashMap;
import java.util.Map;

public class DataCache {
    private final CacheConfig config;
    // Simplified: map blockAddress -> bytes
    private final Map<Integer, byte[]> blocks = new HashMap<>();

    public DataCache(CacheConfig config) {
        this.config = config;
    }

    public boolean isHit(int address) {
        int blockAddress = blockAddress(address);
        return blocks.containsKey(blockAddress);
    }

    public byte[] loadWord(int address, Memory memory) {
        int blockAddress = blockAddress(address);
        if (!blocks.containsKey(blockAddress)) {
            // miss: fetch full block
            byte[] fetched = memory.fetchBlock(blockAddress, config.getBlockSizeBytes());
            blocks.put(blockAddress, fetched);
        }
        byte[] block = blocks.get(blockAddress);
        int offset = address % config.getBlockSizeBytes();
        byte[] word = new byte[4];
        System.arraycopy(block, offset, word, 0, 4);
        return word;
    }

    public void storeWord(int address, byte[] data, Memory memory) {
        int blockAddress = blockAddress(address);
        if (!blocks.containsKey(blockAddress)) {
            byte[] fetched = memory.fetchBlock(blockAddress, config.getBlockSizeBytes());
            blocks.put(blockAddress, fetched);
        }
        byte[] block = blocks.get(blockAddress);
        int offset = address % config.getBlockSizeBytes();
        System.arraycopy(data, 0, block, offset, 4);
        // write-through simplified
        memory.storeBlock(blockAddress, block);
    }

    private int blockAddress(int address) {
        return address - (address % config.getBlockSizeBytes());
    }
}
