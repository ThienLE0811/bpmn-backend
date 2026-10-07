package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a BPMN 2.0 XML document into an in-memory {@link BpmnProcessDefinition} graph
 * that {@link ProcessEngine} can walk. Understands startEvent (optionally with a timer -
 * timeDuration/timeDate/timeCycle), endEvent, userTask, exclusiveGateway, parallelGateway,
 * inclusiveGateway, serviceTask (optionally bound to a connector via a
 * {@code <camunda:connector>} extension element), businessRuleTask, sequenceFlow, timer
 * boundaryEvents (attachedToRef + timeDuration/timeDate/timeCycle, either interrupting or
 * non-interrupting via cancelActivity), and standalone timer intermediateCatchEvents
 * (timeDuration/timeDate only - timeCycle is rejected there, repeating a plain wait point has no
 * coherent semantics) - any other flow-node type (subprocess, script task, message/signal events,
 * etc.) is silently skipped when building nodes, which means a sequenceFlow referencing one will
 * fail the "unknown node" validation below with a clear error rather than executing incorrectly.
 */
public class BpmnGraphParser {
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    private BpmnGraphParser() {
    }

    public static BpmnProcessDefinition parse(String bpmnXml) {
        Document doc = parseXml(bpmnXml);

        NodeList processElements = doc.getElementsByTagNameNS("*", "process");
        if (processElements.getLength() == 0) {
            throw new AppException("BPMN XML does not contain a <process> element", 400);
        }
        Element processElement = (Element) processElements.item(0);

        Map<String, BpmnNode> nodesById = new HashMap<>();
        List<BpmnSequenceFlow> flows = new ArrayList<>();
        String startNodeId = null;
        int startEventCount = 0;
        int endEventCount = 0;

        for (Element element : childElements(processElement)) {
            switch (localName(element)) {
                case "startEvent" -> {
                    String id = element.getAttribute("id");
                    String name = nullIfBlank(element.getAttribute("name"));
                    if (hasTimerEventDefinition(element)) {
                        TimerDefinition timer = parseTimerEventDefinition(element, id);
                        nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.START_EVENT).name(name)
                                .timer(timer.duration(), timer.date(), timer.cycle()).build());
                    } else {
                        nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.START_EVENT).name(name).build());
                    }
                    startNodeId = id;
                    startEventCount++;
                }
                case "endEvent" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.END_EVENT)
                            .name(nullIfBlank(element.getAttribute("name"))).build());
                    endEventCount++;
                }
                case "userTask" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.USER_TASK)
                            .name(nullIfBlank(element.getAttribute("name"))).build());
                }
                case "exclusiveGateway" -> {
                    String id = element.getAttribute("id");
                    String defaultFlowId = nullIfBlank(element.getAttribute("default"));
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.EXCLUSIVE_GATEWAY)
                            .name(nullIfBlank(element.getAttribute("name"))).defaultFlowId(defaultFlowId).build());
                }
                case "parallelGateway" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.PARALLEL_GATEWAY)
                            .name(nullIfBlank(element.getAttribute("name"))).build());
                }
                case "inclusiveGateway" -> {
                    String id = element.getAttribute("id");
                    String defaultFlowId = nullIfBlank(element.getAttribute("default"));
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.INCLUSIVE_GATEWAY)
                            .name(nullIfBlank(element.getAttribute("name"))).defaultFlowId(defaultFlowId).build());
                }
                case "serviceTask" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.SERVICE_TASK)
                            .name(nullIfBlank(element.getAttribute("name")))
                            .connector(parseConnector(element, id)).build());
                }
                case "businessRuleTask" -> {
                    String id = element.getAttribute("id");
                    String decisionRef = camundaAttribute(element, "decisionRef");
                    String resultVariable = camundaAttribute(element, "resultVariable");
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.BUSINESS_RULE_TASK)
                            .name(nullIfBlank(element.getAttribute("name")))
                            .decision(decisionRef, resultVariable).build());
                }
                case "boundaryEvent" -> {
                    String id = element.getAttribute("id");
                    String attachedToRef = nullIfBlank(element.getAttribute("attachedToRef"));
                    if (attachedToRef == null) {
                        throw new AppException("Boundary event " + id + " has no attachedToRef", 400);
                    }
                    boolean interrupting = !"false".equalsIgnoreCase(element.getAttribute("cancelActivity"));
                    TimerDefinition timer = parseTimerEventDefinition(element, id);
                    if (timer.cycle() != null && interrupting) {
                        throw new AppException("Boundary event " + id
                                + " has timeCycle but is interrupting - repeating boundary timers require cancelActivity=\"false\"", 400);
                    }
                    nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.BOUNDARY_TIMER_EVENT)
                            .name(nullIfBlank(element.getAttribute("name")))
                            .attachedToNodeId(attachedToRef)
                            .timer(timer.duration(), timer.date(), timer.cycle())
                            .interrupting(interrupting).build());
                }
                case "intermediateCatchEvent" -> {
                    String id = element.getAttribute("id");
                    if (hasTimerEventDefinition(element)) {
                        TimerDefinition timer = parseTimerEventDefinition(element, id);
                        if (timer.cycle() != null) {
                            throw new AppException("Intermediate catch event " + id
                                    + " has timeCycle - repeating intermediate timers are not supported, only timeDuration/timeDate are", 400);
                        }
                        nodesById.put(id, BpmnNode.builder(id, BpmnNodeType.INTERMEDIATE_CATCH_TIMER_EVENT)
                                .name(nullIfBlank(element.getAttribute("name")))
                                .timer(timer.duration(), timer.date(), null).build());
                    }
                    // Non-timer intermediate catch events (message/signal/etc.) are not supported yet -
                    // intentionally not added as a node, same as other unsupported element types.
                }
                case "sequenceFlow" -> flows.add(parseSequenceFlow(element));
                default -> {
                    // Unsupported element type for v1 - intentionally not added as a node.
                }
            }
        }

        if (startEventCount != 1) {
            throw new AppException("BPMN process must have exactly one start event, found " + startEventCount, 400);
        }
        if (endEventCount == 0) {
            throw new AppException("BPMN process must have at least one end event", 400);
        }

        Map<String, List<BpmnSequenceFlow>> outgoingFlowsByNodeId = new HashMap<>();
        Map<String, List<BpmnSequenceFlow>> incomingFlowsByNodeId = new HashMap<>();
        for (BpmnSequenceFlow flow : flows) {
            if (!nodesById.containsKey(flow.getSourceRef())) {
                throw new AppException("Sequence flow " + flow.getId() + " references unknown source node: " + flow.getSourceRef(), 400);
            }
            if (!nodesById.containsKey(flow.getTargetRef())) {
                throw new AppException("Sequence flow " + flow.getId() + " references unknown target node: " + flow.getTargetRef(), 400);
            }
            outgoingFlowsByNodeId.computeIfAbsent(flow.getSourceRef(), k -> new ArrayList<>()).add(flow);
            incomingFlowsByNodeId.computeIfAbsent(flow.getTargetRef(), k -> new ArrayList<>()).add(flow);
        }

        Map<String, BpmnNode> boundaryTimersByAttachedToNodeId = new HashMap<>();
        for (BpmnNode node : nodesById.values()) {
            if (node.getType() != BpmnNodeType.BOUNDARY_TIMER_EVENT) {
                continue;
            }
            if (!nodesById.containsKey(node.getAttachedToNodeId())) {
                throw new AppException("Boundary event " + node.getId() + " is attached to unknown node: " + node.getAttachedToNodeId(), 400);
            }
            if (boundaryTimersByAttachedToNodeId.containsKey(node.getAttachedToNodeId())) {
                throw new AppException("Node " + node.getAttachedToNodeId()
                        + " has more than one boundary timer event attached - only one is supported per task", 400);
            }
            boundaryTimersByAttachedToNodeId.put(node.getAttachedToNodeId(), node);
        }

        String processId = nullIfBlank(processElement.getAttribute("id"));
        return new BpmnProcessDefinition(processId, nodesById, outgoingFlowsByNodeId, incomingFlowsByNodeId,
                boundaryTimersByAttachedToNodeId, startNodeId);
    }

    private record TimerDefinition(String duration, String date, String cycle) {
    }

    /**
     * Reads a service task's {@code <extensionElements><camunda:connector>} binding, or returns
     * null when it has none - an unbound service task stays valid and is walked straight through.
     * Parameter values are deliberately kept as raw text and only evaluated at execution time, see
     * {@link ConnectorBinding}.
     */
    private static ConnectorBinding parseConnector(Element taskElement, String taskId) {
        Element extensionElements = firstChildByLocalName(taskElement, "extensionElements");
        if (extensionElements == null) {
            return null;
        }
        Element connector = firstChildByLocalName(extensionElements, "connector");
        if (connector == null) {
            return null;
        }

        Element connectorIdElement = firstChildByLocalName(connector, "connectorId");
        String connectorId = connectorIdElement != null ? nullIfBlank(connectorIdElement.getTextContent()) : null;
        if (connectorId == null) {
            throw new AppException("Service task " + taskId + " has a <connector> without a non-empty <connectorId>", 400);
        }

        Map<String, String> inputs = new LinkedHashMap<>();
        Map<String, String> outputs = new LinkedHashMap<>();
        Element inputOutput = firstChildByLocalName(connector, "inputOutput");
        if (inputOutput != null) {
            for (Element parameter : childElements(inputOutput)) {
                String parameterType = localName(parameter);
                Map<String, String> target = switch (parameterType) {
                    case "inputParameter" -> inputs;
                    case "outputParameter" -> outputs;
                    default -> null;
                };
                if (target == null) {
                    continue;
                }
                String name = nullIfBlank(parameter.getAttribute("name"));
                if (name == null) {
                    throw new AppException("Service task " + taskId + " has a <" + parameterType
                            + "> without a name attribute", 400);
                }
                if (!childElements(parameter).isEmpty()) {
                    throw new AppException("Service task " + taskId + " " + parameterType + " \"" + name
                            + "\" has nested elements - only plain text and ${...} expressions are supported", 400);
                }
                if (target.containsKey(name)) {
                    throw new AppException("Service task " + taskId + " declares " + parameterType + " \""
                            + name + "\" more than once", 400);
                }
                target.put(name, parameter.getTextContent().trim());
            }
        }

        return new ConnectorBinding(connectorId, inputs, outputs);
    }

    /** Checks for a {@code <timerEventDefinition>} child without requiring/parsing it - used by event types where a timer is optional (startEvent, intermediateCatchEvent), unlike boundaryEvent where it's mandatory. */
    private static boolean hasTimerEventDefinition(Element element) {
        return firstChildByLocalName(element, "timerEventDefinition") != null;
    }

    /** Reads the {@code <timerEventDefinition>} child of a boundary event and validates its {@code timeDuration}/{@code timeDate}/{@code timeCycle} eagerly. */
    private static TimerDefinition parseTimerEventDefinition(Element boundaryEventElement, String boundaryEventId) {
        Element timerEventDefinition = firstChildByLocalName(boundaryEventElement, "timerEventDefinition");
        if (timerEventDefinition == null) {
            throw new AppException("Boundary event " + boundaryEventId
                    + " has no timerEventDefinition - only timer boundary events are supported", 400);
        }

        String duration = null;
        String date = null;
        String cycle = null;
        for (Element childElement : childElements(timerEventDefinition)) {
            String text = stripExpressionWrapper(childElement.getTextContent());
            switch (localName(childElement)) {
                case "timeDuration" -> duration = text;
                case "timeDate" -> date = text;
                case "timeCycle" -> cycle = text;
                default -> {
                    // Any other timerEventDefinition child is irrelevant here.
                }
            }
        }

        int specifiedCount = (duration != null ? 1 : 0) + (date != null ? 1 : 0) + (cycle != null ? 1 : 0);
        if (specifiedCount == 0) {
            throw new AppException("Boundary event " + boundaryEventId
                    + " timerEventDefinition has none of timeDuration/timeDate/timeCycle", 400);
        }
        if (specifiedCount > 1) {
            throw new AppException("Boundary event " + boundaryEventId
                    + " timerEventDefinition has more than one of timeDuration/timeDate/timeCycle - only one is supported", 400);
        }

        try {
            if (duration != null) {
                Duration.parse(duration);
            } else if (date != null) {
                LocalDateTime.parse(date);
            } else {
                TimerCycle.parse(cycle);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("Boundary event " + boundaryEventId + " has an invalid timer value: " + e.getMessage(), 400);
        }

        return new TimerDefinition(duration, date, cycle);
    }

    private static BpmnSequenceFlow parseSequenceFlow(Element element) {
        String id = element.getAttribute("id");
        String sourceRef = element.getAttribute("sourceRef");
        String targetRef = element.getAttribute("targetRef");

        Element conditionElement = firstChildByLocalName(element, "conditionExpression");
        String condition = conditionElement != null ? stripExpressionWrapper(conditionElement.getTextContent()) : null;

        return new BpmnSequenceFlow(id, sourceRef, targetRef, condition);
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) child);
            }
        }
        return elements;
    }

    private static Element firstChildByLocalName(Element parent, String localName) {
        for (Element child : childElements(parent)) {
            if (localName.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    /** Falls back to the qualified node name for documents parsed without a resolvable namespace declaration. */
    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }

    private static String stripExpressionWrapper(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** Reads a camunda-namespaced attribute, falling back to a literal "camunda:x" attribute lookup for documents that don't resolve the namespace declaration as expected. */
    private static String camundaAttribute(Element element, String localName) {
        String value = element.getAttributeNS(CAMUNDA_NS, localName);
        if (value == null || value.isBlank()) {
            value = element.getAttribute("camunda:" + localName);
        }
        return nullIfBlank(value);
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setCoalescing(true);
            factory.setIgnoringComments(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new AppException("Invalid BPMN XML: " + e.getMessage(), 400);
        }
    }
}
