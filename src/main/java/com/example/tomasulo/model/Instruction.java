package com.example.tomasulo.model;

public class Instruction {
    private final int pc; // sequential index (not actual byte PC yet)
    private final Opcode opcode;
    private final String dest; // register name or null
    private final String src1; // register name or immediate base
    private final String src2; // register name or immediate/offset
    private final Integer immediate; // optional immediate (ADDI, SUBI, branch offset)
    private final Integer offset; // for load/store base + offset form
    private final String rawText;

    public Instruction(int pc, Opcode opcode, String dest, String src1, String src2,
                       Integer immediate, Integer offset, String rawText) {
        this.pc = pc;
        this.opcode = opcode;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.immediate = immediate;
        this.offset = offset;
        this.rawText = rawText;
    }

    public int getPc() { return pc; }
    public Opcode getOpcode() { return opcode; }
    public String getDest() { return dest; }
    public String getSrc1() { return src1; }
    public String getSrc2() { return src2; }
    public Integer getImmediate() { return immediate; }
    public Integer getOffset() { return offset; }
    public String getRawText() { return rawText; }
}
