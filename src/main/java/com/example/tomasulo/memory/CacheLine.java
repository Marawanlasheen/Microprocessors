package com.example.tomasulo.memory;

public class CacheLine {
    private final int tag; // simplified direct-mapped tag
    private final byte[] data;
    private final boolean valid;

    public CacheLine(int tag, byte[] data, boolean valid) {
        this.tag = tag;
        this.data = data;
        this.valid = valid;
    }
    public int getTag() { return tag; }
    public byte[] getData() { return data; }
    public boolean isValid() { return valid; }
}
