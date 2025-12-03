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
        boolean hit = blocks.containsKey(blockAddress);
        System.out.println("[CACHE] isHit check - Address: " + address + ", BlockAddress: " + blockAddress + ", Result: " + (hit ? "HIT" : "MISS"));
        return hit;
    }

    public byte[] loadWord(int address, Memory memory) {
        int blockAddress = blockAddress(address);
        System.out.println("[CACHE] loadWord called - Address: " + address + ", BlockAddress: " + blockAddress);
        if (!blocks.containsKey(blockAddress)) {
            // miss: fetch full block
            System.out.println("[CACHE] MISS - Fetching block from memory at blockAddress: " + blockAddress);
            byte[] fetched = memory.fetchBlock(blockAddress, config.getBlockSizeBytes());
            blocks.put(blockAddress, fetched);
            System.out.println("[CACHE] Block stored in cache. Cache now contains blocks: " + blocks.keySet());
        } else {
            System.out.println("[CACHE] HIT - Block already in cache");
        }
        byte[] block = blocks.get(blockAddress);
        int offset = address % config.getBlockSizeBytes();
        byte[] word = new byte[4];
        
        // Check if the 4-byte word spans across block boundary
        int bytesInCurrentBlock = config.getBlockSizeBytes() - offset;
        if (bytesInCurrentBlock >= 4) {
            // Word fits entirely in current block
            System.arraycopy(block, offset, word, 0, 4);
        } else {
            // Word spans two blocks - copy from current block
            System.arraycopy(block, offset, word, 0, bytesInCurrentBlock);
            // Fetch next block and copy remaining bytes
            int nextBlockAddress = blockAddress + config.getBlockSizeBytes();
            if (!blocks.containsKey(nextBlockAddress)) {
                byte[] fetchedNext = memory.fetchBlock(nextBlockAddress, config.getBlockSizeBytes());
                blocks.put(nextBlockAddress, fetchedNext);
            }
            byte[] nextBlock = blocks.get(nextBlockAddress);
            System.arraycopy(nextBlock, 0, word, bytesInCurrentBlock, 4 - bytesInCurrentBlock);
        }
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
        
        // Check if the 4-byte word spans across block boundary
        int bytesInCurrentBlock = config.getBlockSizeBytes() - offset;
        if (bytesInCurrentBlock >= 4) {
            // Word fits entirely in current block
            System.arraycopy(data, 0, block, offset, 4);
            memory.storeBlock(blockAddress, block);
        } else {
            // Word spans two blocks - write to current block
            System.arraycopy(data, 0, block, offset, bytesInCurrentBlock);
            memory.storeBlock(blockAddress, block);
            // Fetch next block and write remaining bytes
            int nextBlockAddress = blockAddress + config.getBlockSizeBytes();
            if (!blocks.containsKey(nextBlockAddress)) {
                byte[] fetchedNext = memory.fetchBlock(nextBlockAddress, config.getBlockSizeBytes());
                blocks.put(nextBlockAddress, fetchedNext);
            }
            byte[] nextBlock = blocks.get(nextBlockAddress);
            System.arraycopy(data, bytesInCurrentBlock, nextBlock, 0, 4 - bytesInCurrentBlock);
            memory.storeBlock(nextBlockAddress, nextBlock);
        }
    }

    private int blockAddress(int address) {
        return address - (address % config.getBlockSizeBytes());
    }
    
    public Map<Integer, byte[]> getBlocks() {
        System.out.println("[CACHE] getBlocks() called - Current cache contains: " + blocks.keySet());
        return new HashMap<>(blocks);
    }
    
    public CacheConfig getConfig() {
        return config;
    }
}
