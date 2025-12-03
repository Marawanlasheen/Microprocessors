package com.example.tomasulo.core;

public class InstructionStatus {
    public enum Stage { QUEUED, ISSUED, EXECUTING, WRITTEN, COMMITTED }
    private final int pcIndex;
    private final String text;
    private Stage stage;
    private int issueCycle = -1;
    private int executeStartCycle = -1;
    private int executeEndCycle = -1;
    private int commitCycle = -1;

    public InstructionStatus(int pcIndex, String text, Stage stage) {
        this.pcIndex = pcIndex;
        this.text = text;
        this.stage = stage;
    }

    public int getPcIndex() { return pcIndex; }
    public String getText() { return text; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    
    public void setIssueCycle(int cycle) { this.issueCycle = cycle; }
    public void setExecuteStartCycle(int cycle) { this.executeStartCycle = cycle; }
    public void setExecuteEndCycle(int cycle) { this.executeEndCycle = cycle; }
    public void setCommitCycle(int cycle) { this.commitCycle = cycle; }
    
    public int getIssueCycle() { return issueCycle; }
    public int getExecuteStartCycle() { return executeStartCycle; }
    public int getExecuteEndCycle() { return executeEndCycle; }
    public int getCommitCycle() { return commitCycle; }
}
