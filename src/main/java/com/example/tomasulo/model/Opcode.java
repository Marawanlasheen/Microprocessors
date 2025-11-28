package com.example.tomasulo.model;

public enum Opcode {
    ADD, SUB, MUL, DIV, // floating point assumed same latency categories
    ADD_D, SUB_D, MUL_D, DIV_D, // floating point .D variants
    ADDI, SUBI, DADDI, DSUBI, // integer immediate (32/64-bit)
    LW, LD, L_S, L_D,
    SW, SD, S_S, S_D,
    BEQ, BNE;

    public boolean isLoad() {
        return this == LW || this == LD || this == L_S || this == L_D;
    }
    public boolean isStore() {
        return this == SW || this == SD || this == S_S || this == S_D;
    }
    public boolean isBranch() { return this == BEQ || this == BNE; }
    public boolean isInteger() { return this == ADDI || this == SUBI; }
    public boolean isFPAddGroup() { return this == ADD || this == SUB; }
    public boolean isFPMulGroup() { return this == MUL || this == DIV; }
}
