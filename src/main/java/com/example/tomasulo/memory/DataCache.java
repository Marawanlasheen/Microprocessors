package com.example.tomasulo.memory;

import com.example.tomasulo.config.CacheConfig;

import java.util.HashMap;
import java.util.Map;

public class DataCache {
    private final CacheConfig config;
    // Direct-mapped data cache: maps blockAddress -> bytes[]
    // Addressing strategy:
    //   - Memory is byte-addressable.
    //   - Each cache block holds 'blockSize' bytes.
    //   - blockAddress = address - (address % blockSize)
    //   - On access, if block is not present, fetch from memory (miss). Only data cache misses are considered.
    //   - Loads/stores operate on 4 bytes (a word) at a time.
    //   - Write-through policy for stores.
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
