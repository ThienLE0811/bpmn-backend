package com.example.bpmn.dmn;

import java.util.ArrayList;
import java.util.List;

/**
 * A single decision table row. {@code inputEntries}/{@code outputEntries} are parallel to the
 * table's input/output columns; an input entry may be {@code null} or blank meaning "any value".
 * Uses plain array-backed lists (not {@link List#copyOf}) because input entries may be null.
 */
public class DmnRule {
    private final List<String> inputEntries;
    private final List<String> outputEntries;

    public DmnRule(List<String> inputEntries, List<String> outputEntries) {
        this.inputEntries = new ArrayList<>(inputEntries);
        this.outputEntries = new ArrayList<>(outputEntries);
    }

    public List<String> getInputEntries() {
        return inputEntries;
    }

    public List<String> getOutputEntries() {
        return outputEntries;
    }
}
