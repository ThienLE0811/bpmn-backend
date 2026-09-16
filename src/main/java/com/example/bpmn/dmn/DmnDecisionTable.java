package com.example.bpmn.dmn;

import java.util.List;

/** Parsed DMN decision table - not persisted, rebuilt from XML each time it's evaluated (mirrors {@link com.example.bpmn.engine.BpmnProcessDefinition}). */
public class DmnDecisionTable {
    private final String hitPolicy;
    private final List<DmnInput> inputs;
    private final List<DmnOutput> outputs;
    private final List<DmnRule> rules;

    public DmnDecisionTable(String hitPolicy, List<DmnInput> inputs, List<DmnOutput> outputs, List<DmnRule> rules) {
        this.hitPolicy = hitPolicy;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.rules = List.copyOf(rules);
    }

    public String getHitPolicy() {
        return hitPolicy;
    }

    public List<DmnInput> getInputs() {
        return inputs;
    }

    public List<DmnOutput> getOutputs() {
        return outputs;
    }

    public List<DmnRule> getRules() {
        return rules;
    }
}
