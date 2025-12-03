package com.example.tomasulo.model;

import java.util.HashMap;
import java.util.Map;

public class RegisterFile {
    private final Map<String, Integer> values = new HashMap<>();
    private final Map<String, String> tags = new HashMap<>(); // Tomasulo: mapping register -> station tag producing it

    public void init(String name, int value) {
        // R0 is hardwired to 0 and cannot be changed
        if ("R0".equals(name)) {
            values.put(name, 0);
        } else {
            values.put(name, value);
        }
    }

    public Integer getValue(String name) { 
        // R0 always returns 0
        if ("R0".equals(name)) {
            return 0;
        }
        return values.get(name); 
    }

    public void setValue(String name, int value) {
        // R0 is hardwired to 0 and cannot be changed
        if ("R0".equals(name)) {
            return; // Ignore writes to R0
        }
        values.put(name, value);
        tags.remove(name); // value published
    }

    public void setTag(String name, String tag) { 
        // R0 is hardwired to 0 and cannot have a tag
        if ("R0".equals(name)) {
            return; // Ignore tag assignment to R0
        }
        tags.put(name, tag); 
    }
    public String getTag(String name) { return tags.get(name); }
    public boolean isPending(String name) { return tags.containsKey(name); }

    public Map<String, Integer> snapshotValues() { return new HashMap<>(values); }
    public Map<String, String> snapshotTags() { return new HashMap<>(tags); }
}
