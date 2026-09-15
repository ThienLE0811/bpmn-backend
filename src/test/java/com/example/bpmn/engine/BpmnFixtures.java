package com.example.bpmn.engine;

/** Shared BPMN XML fixtures for engine tests. */
final class BpmnFixtures {

    private BpmnFixtures() {
    }

    /**
     * start1 -> task1 -> gw1 --(amount > 1000)--> task2 -> end2
     *                        \--(default)-------> end1
     */
    static final String APPROVAL_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="approval_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="flow1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Review Request" />
                <sequenceFlow id="flow2" sourceRef="task1" targetRef="gw1" />
                <exclusiveGateway id="gw1" name="Amount Check" default="flow4" />
                <sequenceFlow id="flow3" sourceRef="gw1" targetRef="task2">
                  <conditionExpression>${amount > 1000}</conditionExpression>
                </sequenceFlow>
                <userTask id="task2" name="Manager Approval" />
                <sequenceFlow id="flow5" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Approved (Manager)" />
                <sequenceFlow id="flow4" sourceRef="gw1" targetRef="end1" />
                <endEvent id="end1" name="Approved (Auto)" />
              </process>
            </definitions>
            """;
}
