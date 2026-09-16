package com.example.bpmn.dmn;

/** A decision table output column - {@code name} is the key used when the winning rule's output entry is merged into process variables. */
public class DmnOutput {
    private final String id;
    private final String name;

    public DmnOutput(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
