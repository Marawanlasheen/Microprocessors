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
        // Check if block starting from the effective address exists in cache
        boolean hit = blocks.containsKey(address);
        System.out.println("[CACHE] isHit check - Address: " + address + ", Result: " + (hit ? "HIT" : "MISS"));
        return hit;
    }

    public byte[] loadWord(int address, Memory memory) {
        // Use the effective address as the block starting address (no alignment)
        int blockAddress = address;
        System.out.println("[CACHE] loadWord called - Address: " + address + ", BlockAddress: " + blockAddress);
        if (!blocks.containsKey(blockAddress)) {
            // miss: fetch full block starting from the effective address
            System.out.println("[CACHE] MISS - Fetching block from memory starting at effective address: " + blockAddress);
            byte[] fetched = memory.fetchBlock(blockAddress, config.getBlockSizeBytes());
            blocks.put(blockAddress, fetched);
            System.out.println("[CACHE] Block stored in cache. Cache now contains blocks: " + blocks.keySet());
        } else {
            System.out.println("[CACHE] HIT - Block already in cache");
        }

        byte[] block = blocks.get(blockAddress);
        // Since we're starting from the effective address, the word is at offset 0
        byte[] word = new byte[4];
        
        // Check if the 4-byte word fits in the fetched block
        if (config.getBlockSizeBytes() >= 4) {
            // Word fits entirely in the fetched block
            System.arraycopy(block, 0, word, 0, 4);
        } else {
            // Block is smaller than 4 bytes - copy what we have
            int bytesAvailable = Math.min(config.getBlockSizeBytes(), 4);
            System.arraycopy(block, 0, word, 0, bytesAvailable);
            // Fetch remaining bytes if needed
            if (bytesAvailable < 4) {
                int nextBlockAddress = blockAddress + bytesAvailable;
                if (!blocks.containsKey(nextBlockAddress)) {
                    byte[] fetchedNext = memory.fetchBlock(nextBlockAddress, config.getBlockSizeBytes());
                    blocks.put(nextBlockAddress, fetchedNext);
                }
                byte[] nextBlock = blocks.get(nextBlockAddress);
                System.arraycopy(nextBlock, 0, word, bytesAvailable, 4 - bytesAvailable);
            }
        }
        return word;
    }

    public void storeWord(int address, byte[] data, Memory memory) {
        // Use the effective address as the block starting address (no alignment)
        int blockAddress = address;
        if (!blocks.containsKey(blockAddress)) {
            byte[] fetched = memory.fetchBlock(blockAddress, config.getBlockSizeBytes());
            blocks.put(blockAddress, fetched);
        }
        byte[] block = blocks.get(blockAddress);
        
        // Check if the 4-byte word fits in the fetched block
        if (config.getBlockSizeBytes() >= 4) {
            // Word fits entirely in the fetched block
            System.arraycopy(data, 0, block, 0, 4);
            memory.storeBlock(blockAddress, block);
        } else {
            // Block is smaller than 4 bytes - write what fits
            int bytesAvailable = Math.min(config.getBlockSizeBytes(), 4);
            System.arraycopy(data, 0, block, 0, bytesAvailable);
            memory.storeBlock(blockAddress, block);
            // Write remaining bytes if needed
            if (bytesAvailable < 4) {
                int nextBlockAddress = blockAddress + bytesAvailable;
                if (!blocks.containsKey(nextBlockAddress)) {
                    byte[] fetchedNext = memory.fetchBlock(nextBlockAddress, config.getBlockSizeBytes());
                    blocks.put(nextBlockAddress, fetchedNext);
                }
                byte[] nextBlock = blocks.get(nextBlockAddress);
                System.arraycopy(data, bytesAvailable, nextBlock, 0, 4 - bytesAvailable);
                memory.storeBlock(nextBlockAddress, nextBlock);
            }
        }
    }
    
    public Map<Integer, byte[]> getBlocks() {
        System.out.println("[CACHE] getBlocks() called - Current cache contains: " + blocks.keySet());
        return new HashMap<>(blocks);
    }
    
    public CacheConfig getConfig() {
        return config;
    }
}
