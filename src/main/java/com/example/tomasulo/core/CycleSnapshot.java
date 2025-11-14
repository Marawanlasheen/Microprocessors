package com.example.tomasulo.core;

import java.util.List;
import java.util.Map;

public class CycleSnapshot {
    private final int cycle;
    private final int pcIndex;
    private final List<StationState> stations;
    private final Map<String, Integer> intRegValues;
    private final Map<String, String> intRegTags;
    private final Map<String, Integer> floatRegValues;
    private final Map<String, String> floatRegTags;
    private final List<HazardRecord> hazards;
    private final List<InstructionStatus> instructionQueue;

    public CycleSnapshot(int cycle, int pcIndex, List<StationState> stations, Map<String, Integer> intRegValues,
                         Map<String, String> intRegTags, Map<String, Integer> floatRegValues,
                         Map<String, String> floatRegTags, List<HazardRecord> hazards,
                         List<InstructionStatus> instructionQueue) {
        this.cycle = cycle;
        this.pcIndex = pcIndex;
        this.stations = stations;
        this.intRegValues = intRegValues;
        this.intRegTags = intRegTags;
        this.floatRegValues = floatRegValues;
        this.floatRegTags = floatRegTags;
        this.hazards = hazards;
        this.instructionQueue = instructionQueue;
    }

    public int getCycle() { return cycle; }
    public int getPcIndex() { return pcIndex; }
    public List<StationState> getStations() { return stations; }
    public Map<String, Integer> getIntRegValues() { return intRegValues; }
    public Map<String, String> getIntRegTags() { return intRegTags; }
    public Map<String, Integer> getFloatRegValues() { return floatRegValues; }
    public Map<String, String> getFloatRegTags() { return floatRegTags; }
    public List<HazardRecord> getHazards() { return hazards; }
    public List<InstructionStatus> getInstructionQueue() { return instructionQueue; }
}
