package com.example.tomasulo.model;

public class ReservationStationEntry {
        // Used to prevent execution starting in the same cycle operands become ready
        private boolean becameReadyThisCycle = false;
        // Used to prevent reusing a station in the same cycle it was freed
        private boolean freedThisCycle = false;

    private final String name; // tag
    private final ReservationStationType type;
    private boolean busy;
    private Opcode opcode;
    private String Vj;
    private String Vk;
    private String Qj; // tag if waiting
    private String Qk;
    private Integer address; // for load/store effective address
    private int remainingCycles; // execution countdown
    private boolean resultReady;
    private Long resultValue; // simplified as integer
    private String destRegister; // track destination register (if any) for hazard resolution
    private String src1Register;
    private String src2Register;
    private Integer immediate; // for ADDI/SUBI/branches
    private boolean startedExecution;
    private boolean cacheLatencyApplied; // for loads/stores to account for hit/miss
    private Integer effectiveAddress; // computed when base ready for memory ops
    private Integer instructionIndex; // index in program for ordering & UI
    private String rawText;
    private byte[] loadedData; // cached data from memory/cache for LOAD operations

    public ReservationStationEntry(String name, ReservationStationType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public ReservationStationType getType() { return type; }
    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }
    public Opcode getOpcode() { return opcode; }
    public void setOpcode(Opcode opcode) { this.opcode = opcode; }
    public String getVj() { return Vj; }
    public void setVj(String vj) { Vj = vj; }
    public String getVk() { return Vk; }
    public void setVk(String vk) { Vk = vk; }
    public String getQj() { return Qj; }
    public void setQj(String qj) { Qj = qj; }
    public String getQk() { return Qk; }
    public void setQk(String qk) { Qk = qk; }
    public Integer getAddress() { return address; }
    public void setAddress(Integer address) { this.address = address; }
    public int getRemainingCycles() { return remainingCycles; }
    public void setRemainingCycles(int remainingCycles) { this.remainingCycles = remainingCycles; }
    public boolean isResultReady() { return resultReady; }
    public void setResultReady(boolean resultReady) { this.resultReady = resultReady; }
    public Long getResultValue() { return resultValue; }
    public void setResultValue(Long resultValue) { this.resultValue = resultValue; }
    public String getDestRegister() { return destRegister; }
    public void setDestRegister(String destRegister) { this.destRegister = destRegister; }
    public String getSrc1Register() { return src1Register; }
    public void setSrc1Register(String src1Register) { this.src1Register = src1Register; }
    public String getSrc2Register() { return src2Register; }
    public void setSrc2Register(String src2Register) { this.src2Register = src2Register; }
    public Integer getImmediate() { return immediate; }
    public void setImmediate(Integer immediate) { this.immediate = immediate; }
    public boolean isStartedExecution() { return startedExecution; }
    public void setStartedExecution(boolean startedExecution) { this.startedExecution = startedExecution; }
    public boolean isCacheLatencyApplied() { return cacheLatencyApplied; }
    public void setCacheLatencyApplied(boolean cacheLatencyApplied) { this.cacheLatencyApplied = cacheLatencyApplied; }
    public Integer getEffectiveAddress() { return effectiveAddress; }
    public void setEffectiveAddress(Integer effectiveAddress) { this.effectiveAddress = effectiveAddress; }
    public Integer getInstructionIndex() { return instructionIndex; }
    public void setInstructionIndex(Integer instructionIndex) { this.instructionIndex = instructionIndex; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public boolean isBecameReadyThisCycle() { return becameReadyThisCycle; }
    public void setBecameReadyThisCycle(boolean val) { this.becameReadyThisCycle = val; }
    public byte[] getLoadedData() { return loadedData; }
    public void setLoadedData(byte[] loadedData) { this.loadedData = loadedData; }
    public boolean isFreedThisCycle() { return freedThisCycle; }
    public void setFreedThisCycle(boolean freedThisCycle) { this.freedThisCycle = freedThisCycle; }
}
