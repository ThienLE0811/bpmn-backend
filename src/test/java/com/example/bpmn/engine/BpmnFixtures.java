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

    /**
     * start1 -> task1 -> pgFork --> task2 --\
     *                        \--> task3 --> pgJoin -> end1
     */
    static final String PARALLEL_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="parallel_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Prepare" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="pgFork" />
                <parallelGateway id="pgFork" name="Fork" />
                <sequenceFlow id="f3" sourceRef="pgFork" targetRef="task2" />
                <sequenceFlow id="f4" sourceRef="pgFork" targetRef="task3" />
                <userTask id="task2" name="Branch A" />
                <userTask id="task3" name="Branch B" />
                <sequenceFlow id="f5" sourceRef="task2" targetRef="pgJoin" />
                <sequenceFlow id="f6" sourceRef="task3" targetRef="pgJoin" />
                <parallelGateway id="pgJoin" name="Join" />
                <sequenceFlow id="f7" sourceRef="pgJoin" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    /**
     * start1 -> task1 -> igFork --(highValue)--> task2 --\
     *                        \--(needsReview)--> task3 --> igJoin -> end1
     * Either, both, or (if neither condition matches) the default branch can activate.
     */
    static final String INCLUSIVE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="inclusive_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1" />
                <userTask id="task1" name="Prepare" />
                <sequenceFlow id="f2" sourceRef="task1" targetRef="igFork" />
                <inclusiveGateway id="igFork" name="Fork" />
                <sequenceFlow id="f3" sourceRef="igFork" targetRef="task2">
                  <conditionExpression>${highValue}</conditionExpression>
                </sequenceFlow>
                <sequenceFlow id="f4" sourceRef="igFork" targetRef="task3">
                  <conditionExpression>${needsReview}</conditionExpression>
                </sequenceFlow>
                <userTask id="task2" name="Finance Review" />
                <userTask id="task3" name="Compliance Review" />
                <sequenceFlow id="f5" sourceRef="task2" targetRef="igJoin" />
                <sequenceFlow id="f6" sourceRef="task3" targetRef="igJoin" />
                <inclusiveGateway id="igJoin" name="Join" />
                <sequenceFlow id="f7" sourceRef="igJoin" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    /**
     * start1 -> svc1 (serviceTask, auto) -> brt1 (businessRuleTask, auto) -> task1 -> end1
     * Mirrors real-world diagrams where these steps carry no implementation binding and
     * the engine is expected to walk straight through them without creating a task.
     */
    static final String SERVICE_TASK_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs" targetNamespace="http://example.com">
              <process id="service_task_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="svc1" />
                <serviceTask id="svc1" name="Auto Lookup" />
                <sequenceFlow id="f2" sourceRef="svc1" targetRef="brt1" />
                <businessRuleTask id="brt1" name="Auto Scoring" />
                <sequenceFlow id="f3" sourceRef="brt1" targetRef="task1" />
                <userTask id="task1" name="Manual Review" />
                <sequenceFlow id="f4" sourceRef="task1" targetRef="end1" />
                <endEvent id="end1" name="Done" />
              </process>
            </definitions>
            """;

    /**
     * start1 -> brt1 (businessRuleTask bound to DMN decision "riskDecision", result in
     * "riskLevel") -> gw1 --(riskLevel == "HIGH")--> task2 -> end2
     *                     \--(default)-------------> end1
     * Used to test that a business rule task's DMN result is visible to a gateway reached
     * later in the same advance() call.
     */
    static final String DMN_BUSINESS_RULE_PROCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                         id="defs" targetNamespace="http://example.com">
              <process id="dmn_business_rule_process" isExecutable="true">
                <startEvent id="start1" name="Start" />
                <sequenceFlow id="f1" sourceRef="start1" targetRef="brt1" />
                <businessRuleTask id="brt1" name="Assess Risk"
                                  camunda:decisionRef="riskDecision" camunda:resultVariable="riskLevel" />
                <sequenceFlow id="f2" sourceRef="brt1" targetRef="gw1" />
                <exclusiveGateway id="gw1" name="Risk Check" default="f4" />
                <sequenceFlow id="f3" sourceRef="gw1" targetRef="task2">
                  <conditionExpression>${riskLevel == "HIGH"}</conditionExpression>
                </sequenceFlow>
                <userTask id="task2" name="Manual Review" />
                <sequenceFlow id="f5" sourceRef="task2" targetRef="end2" />
                <endEvent id="end2" name="Escalated" />
                <sequenceFlow id="f4" sourceRef="gw1" targetRef="end1" />
                <endEvent id="end1" name="Auto Approved" />
              </process>
            </definitions>
            """;
}
