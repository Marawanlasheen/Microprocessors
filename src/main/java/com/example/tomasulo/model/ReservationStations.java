package com.example.tomasulo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ReservationStations {
    private final List<ReservationStationEntry> entries = new ArrayList<>();

    public void add(ReservationStationEntry entry) { entries.add(entry); }
    public List<ReservationStationEntry> all() { return Collections.unmodifiableList(entries); }

    public ReservationStationEntry findFree(ReservationStationType type) {
        for (ReservationStationEntry e : entries) {
            // Station must be not busy AND not freed in the current cycle
            if (e.getType() == type && !e.isBusy() && !e.isFreedThisCycle()) {
                return e;
            }
        }
        return null;
    }

    public List<ReservationStationEntry> busyEntries() {
        return entries.stream().filter(ReservationStationEntry::isBusy).collect(Collectors.toList());
    }
}
