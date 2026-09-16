package com.example.bpmn.engine;

public enum BpmnNodeType {
    START_EVENT,
    END_EVENT,
    USER_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    INCLUSIVE_GATEWAY,
    /** Automatic step with no implementation binding in the XML - the engine walks straight through it, same as a start event. */
    SERVICE_TASK,
    /** Automatic step, same handling as {@link #SERVICE_TASK} - kept distinct because it models a DMN/rule evaluation, not a generic call. */
    BUSINESS_RULE_TASK
}
