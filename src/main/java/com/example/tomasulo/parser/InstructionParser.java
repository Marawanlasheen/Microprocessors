package com.example.tomasulo.parser;

import com.example.tomasulo.model.Instruction;
import com.example.tomasulo.model.Opcode;

import java.util.ArrayList;
import java.util.List;

public class InstructionParser {
    public List<Instruction> parseLines(List<String> lines) {
        // First pass: collect labels and their line numbers
        List<String> cleanedLines = new ArrayList<>();
        java.util.Map<String, Integer> labelToLine = new java.util.HashMap<>();
        int pc = 0;
        for (String raw : lines) {
            String cleaned = raw.split("#")[0].trim();
            if (cleaned.isEmpty()) continue;
            if (cleaned.matches("^[A-Za-z_][A-Za-z0-9_]*:.*")) {
                int idx = cleaned.indexOf(":");
                String label = cleaned.substring(0, idx).trim();
                labelToLine.put(label, pc);
                cleaned = cleaned.substring(idx + 1).trim();
                if (cleaned.isEmpty()) continue;
            }
            cleanedLines.add(cleaned);
            pc++;
        }
        // Second pass: parse instructions, replacing label operands in branches
        List<Instruction> out = new ArrayList<>();
        for (int i = 0; i < cleanedLines.size(); i++) {
            String cleaned = cleanedLines.get(i);
            // Map L.D/S.D (any case) to LD/SD for opcode compatibility
            String normalized = cleaned
                .replaceFirst("^(?i)l\\.d", "LD")
                .replaceFirst("^(?i)s\\.d", "SD")
                .replaceFirst("^(?i)add\\.d", "ADD_D")
                .replaceFirst("^(?i)sub\\.d", "SUB_D")
                .replaceFirst("^(?i)mul\\.d", "MUL_D")
                .replaceFirst("^(?i)div\\.d", "DIV_D")
                .replaceFirst("^(?i)daddi", "DADDI")
                .replaceFirst("^(?i)dsubi", "DSUBI");
            String[] parts = normalized.replace(",", " ").replace("(", " ").replace(")", " ").split("\\s+");
            String opcodeStr = parts[0].toUpperCase();
            if ((opcodeStr.equals("BEQ") || opcodeStr.equals("BNE")) && parts.length >= 4) {
                String label = parts[3];
                if (labelToLine.containsKey(label)) {
                    int target = labelToLine.get(label);
                    int offset = target - i;
                    parts[3] = Integer.toString(offset);
                    normalized = String.join(" ", parts);
                }
            }
            Instruction inst = parseSingle(i, normalized);
            out.add(inst);
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
            case ADD, SUB, MUL, DIV, ADD_D, SUB_D, MUL_D, DIV_D -> { dest = parts[1]; src1 = parts[2]; src2 = parts[3]; }
            case ADDI, SUBI, DADDI, DSUBI -> { dest = parts[1]; src1 = parts[2]; imm = Integer.parseInt(parts[3]); }
            case LW, LD, L_S, L_D -> { dest = parts[1]; offset = Integer.parseInt(parts[2]); src1 = parts[3]; }
            case SW, SD, S_S, S_D -> { src1 = parts[1]; offset = Integer.parseInt(parts[2]); src2 = parts[3]; }
            case BEQ, BNE -> { src1 = parts[1]; src2 = parts[2]; imm = Integer.parseInt(parts[3]); }
        }
        return new Instruction(pc, opcode, dest, src1, src2, imm, offset, line);
    }
}
