package com.example.tomasulo.core;

import com.example.tomasulo.model.Opcode;
import com.example.tomasulo.model.ReservationStationType;

public class StationState {
    public final String name;
    public final ReservationStationType type;
    public final boolean busy;
    public final Opcode opcode;
    public final String Vj;
    public final String Vk;
    public final String Qj;
    public final String Qk;
    public final Integer address;
    public final int remainingCycles;
    public final boolean resultReady;
    public final Integer resultValue;
    public final String destRegister;

    public StationState(String name, ReservationStationType type, boolean busy, Opcode opcode, String vj, String vk,
                        String qj, String qk, Integer address, int remainingCycles, boolean resultReady,
                        Integer resultValue, String destRegister) {
        this.name = name;
        this.type = type;
        this.busy = busy;
        this.opcode = opcode;
        Vj = vj;
        Vk = vk;
        Qj = qj;
        Qk = qk;
        this.address = address;
        this.remainingCycles = remainingCycles;
        this.resultReady = resultReady;
        this.resultValue = resultValue;
        this.destRegister = destRegister;
    }
}
