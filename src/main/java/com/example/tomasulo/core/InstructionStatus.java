package com.example.tomasulo.core;

public class InstructionStatus {
    public enum Stage { QUEUED, ISSUED, EXECUTING, WRITTEN, COMMITTED }
    private final int pcIndex;
    private final String text;
    private final Stage stage;

    public InstructionStatus(int pcIndex, String text, Stage stage) {
        this.pcIndex = pcIndex;
        this.text = text;
        this.stage = stage;
    }

    public int getPcIndex() { return pcIndex; }
    public String getText() { return text; }
    public Stage getStage() { return stage; }
}
