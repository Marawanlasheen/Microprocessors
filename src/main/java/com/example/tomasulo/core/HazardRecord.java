package com.example.tomasulo.core;

public class HazardRecord {
    private final HazardType type;
    private final String causingStation;
    private final String affectedStation;
    private final String register;
    private final int cycle;

    public HazardRecord(HazardType type, String causingStation, String affectedStation, String register, int cycle) {
        this.type = type;
        this.causingStation = causingStation;
        this.affectedStation = affectedStation;
        this.register = register;
        this.cycle = cycle;
    }

    public HazardType getType() { return type; }
    public String getCausingStation() { return causingStation; }
    public String getAffectedStation() { return affectedStation; }
    public String getRegister() { return register; }
    public int getCycle() { return cycle; }
}
