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
        // store 4 bytes little endian simplified
        System.out.println("[MEMORY] initWord - Address: " + address + ", Value: " + value);
        mem[address] = (byte)(value & 0xFF);
        mem[address+1] = (byte)((value >> 8) & 0xFF);
        mem[address+2] = (byte)((value >> 16) & 0xFF);
        mem[address+3] = (byte)((value >> 24) & 0xFF);
        System.out.println("[MEMORY] Wrote bytes at [" + address + "-" + (address+3) + "]: " + 
            String.format("%02X %02X %02X %02X", mem[address] & 0xFF, mem[address+1] & 0xFF, 
                         mem[address+2] & 0xFF, mem[address+3] & 0xFF));
    }

    public int loadWordRaw(int address) {
        int b0 = Byte.toUnsignedInt(mem[address]);
        int b1 = Byte.toUnsignedInt(mem[address+1]);
        int b2 = Byte.toUnsignedInt(mem[address+2]);
        int b3 = Byte.toUnsignedInt(mem[address+3]);
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    public int size() { return mem.length; }

    public byte[] dump(int start, int length) {
        return Arrays.copyOfRange(mem, start, start + length);
    }
}
