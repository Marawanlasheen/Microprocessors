package com.example.tomasulo.memory;

import java.util.Arrays;

public class Memory {
    private final byte[] mem; // byte-addressable

    public Memory(int sizeBytes) {
        this.mem = new byte[sizeBytes];
    }

    public byte[] fetchBlock(int blockAddress, int blockSize) {
        byte[] out = new byte[blockSize];
        System.arraycopy(mem, blockAddress, out, 0, blockSize);
        return out;
    }

    public void storeBlock(int blockAddress, byte[] data) {
        System.arraycopy(data, 0, mem, blockAddress, data.length);
    }

    public void initWord(int address, int value) {
        // store 4 bytes big endian
        System.out.println("[MEMORY] initWord - Address: " + address + ", Value: " + value);
        mem[address] = (byte)((value >> 24) & 0xFF);
        mem[address+1] = (byte)((value >> 16) & 0xFF);
        mem[address+2] = (byte)((value >> 8) & 0xFF);
        mem[address+3] = (byte)(value & 0xFF);
        System.out.println("[MEMORY] Wrote bytes at [" + address + "-" + (address+3) + "]: " + 
            String.format("%02X %02X %02X %02X", mem[address] & 0xFF, mem[address+1] & 0xFF, 
                         mem[address+2] & 0xFF, mem[address+3] & 0xFF));
    }

    public void initDoubleWord(int address, long value) {
        // store 8 bytes big endian for L.D/S.D operations
        System.out.println("[MEMORY] initDoubleWord - Address: " + address + ", Value: " + value);
        for (int i = 0; i < 8; i++) {
            mem[address + i] = (byte)((value >> ((7 - i) * 8)) & 0xFF);
        }
        System.out.println("[MEMORY] Wrote 8 bytes at [" + address + "-" + (address+7) + "]");
    }

    public void storeDoubleWord(int address, long value) {
        // store 8 bytes big endian for S.D operations at runtime
        System.out.println("[MEMORY] storeDoubleWord - Address: " + address + ", Value: " + value);
        for (int i = 0; i < 8; i++) {
            mem[address + i] = (byte)((value >> ((7 - i) * 8)) & 0xFF);
        }
    }

    public int loadWordRaw(int address) {
        int b0 = Byte.toUnsignedInt(mem[address]);
        int b1 = Byte.toUnsignedInt(mem[address+1]);
        int b2 = Byte.toUnsignedInt(mem[address+2]);
        int b3 = Byte.toUnsignedInt(mem[address+3]);
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public long loadDoubleWordRaw(int address) {
        // load 8 bytes big endian for L.D/S.D operations
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((long)Byte.toUnsignedInt(mem[address + i])) << ((7 - i) * 8);
        }
        return result;
    }

    public int size() { return mem.length; }

    public byte[] dump(int start, int length) {
        return Arrays.copyOfRange(mem, start, start + length);
    }
}
