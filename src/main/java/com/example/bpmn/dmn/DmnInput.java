package com.example.bpmn.dmn;

/** A decision table input column - {@code expression} is the process variable (or simple expression) tested by each rule's input entry. */
public class DmnInput {
    private final String id;
    private final String expression;

    public DmnInput(String id, String expression) {
        this.id = id;
        this.expression = expression;
    }

    public String getId() {
        return id;
    }

    public String getExpression() {
        return expression;
    }
}
