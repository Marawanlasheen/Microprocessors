package com.example.tomasulo.parser;

import com.example.tomasulo.model.Instruction;
import com.example.tomasulo.model.Opcode;

import java.util.ArrayList;
import java.util.List;

public class InstructionParser {
    public List<Instruction> parseLines(List<String> lines) {
        List<Instruction> out = new ArrayList<>();
        int pc = 0;
        for (String raw : lines) {
            String cleaned = raw.split("#")[0].trim();
            if (cleaned.isEmpty()) continue;
            Instruction inst = parseSingle(pc, cleaned);
            out.add(inst);
            pc++;
        }
        return out;
    }

    private Instruction parseSingle(int pc, String line) {
        String[] parts = line.replace(",", " ").replace("(", " ").replace(")", " ").split("\\s+");
        Opcode opcode = Opcode.valueOf(parts[0].toUpperCase());
        // Minimal patterns (expand later):
        String dest = null, src1 = null, src2 = null;
        Integer imm = null; Integer offset = null;
        switch (opcode) {
            case ADD, SUB, MUL, DIV -> { dest = parts[1]; src1 = parts[2]; src2 = parts[3]; }
            case ADDI, SUBI -> { dest = parts[1]; src1 = parts[2]; imm = Integer.parseInt(parts[3]); }
            case LW, LD, L_S, L_D -> { dest = parts[1]; offset = Integer.parseInt(parts[2]); src1 = parts[3]; }
            case SW, SD, S_S, S_D -> { src1 = parts[1]; offset = Integer.parseInt(parts[2]); src2 = parts[3]; }
            case BEQ, BNE -> { src1 = parts[1]; src2 = parts[2]; imm = Integer.parseInt(parts[3]); }
        }
        return new Instruction(pc, opcode, dest, src1, src2, imm, offset, line);
    }
}
