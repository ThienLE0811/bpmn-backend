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
import java.util.List;
import java.util.Map;

/**
 * Parses a BPMN 2.0 XML document into an in-memory {@link BpmnProcessDefinition} graph
 * that {@link ProcessEngine} can walk. Understands startEvent, endEvent, userTask,
 * exclusiveGateway, parallelGateway, inclusiveGateway, serviceTask, businessRuleTask,
 * sequenceFlow, and timer boundaryEvents (attachedToRef + timeDuration/timeDate/timeCycle,
 * either interrupting or non-interrupting via cancelActivity) - any other flow-node type
 * (subprocess, script task, message/signal events, standalone timer start/intermediate
 * events, etc.) is silently skipped when building nodes, which means a sequenceFlow
 * referencing one will fail the "unknown node" validation below with a clear error rather
 * than executing incorrectly.
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

        NodeList children = processElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String localName = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();

            switch (localName) {
                case "startEvent" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.START_EVENT, nullIfBlank(element.getAttribute("name")), null));
                    startNodeId = id;
                    startEventCount++;
                }
                case "endEvent" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.END_EVENT, nullIfBlank(element.getAttribute("name")), null));
                    endEventCount++;
                }
                case "userTask" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.USER_TASK, nullIfBlank(element.getAttribute("name")), null));
                }
                case "exclusiveGateway" -> {
                    String id = element.getAttribute("id");
                    String defaultFlowId = nullIfBlank(element.getAttribute("default"));
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.EXCLUSIVE_GATEWAY, nullIfBlank(element.getAttribute("name")), defaultFlowId));
                }
                case "parallelGateway" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.PARALLEL_GATEWAY, nullIfBlank(element.getAttribute("name")), null));
                }
                case "inclusiveGateway" -> {
                    String id = element.getAttribute("id");
                    String defaultFlowId = nullIfBlank(element.getAttribute("default"));
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.INCLUSIVE_GATEWAY, nullIfBlank(element.getAttribute("name")), defaultFlowId));
                }
                case "serviceTask" -> {
                    String id = element.getAttribute("id");
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.SERVICE_TASK, nullIfBlank(element.getAttribute("name")), null));
                }
                case "businessRuleTask" -> {
                    String id = element.getAttribute("id");
                    String decisionRef = camundaAttribute(element, "decisionRef");
                    String resultVariable = camundaAttribute(element, "resultVariable");
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.BUSINESS_RULE_TASK,
                            nullIfBlank(element.getAttribute("name")), null, decisionRef, resultVariable));
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
                    nodesById.put(id, new BpmnNode(id, BpmnNodeType.BOUNDARY_TIMER_EVENT,
                            nullIfBlank(element.getAttribute("name")), null, attachedToRef,
                            timer.duration(), timer.date(), timer.cycle(), interrupting));
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

    /** Reads the {@code <timerEventDefinition>} child of a boundary event and validates its {@code timeDuration}/{@code timeDate}/{@code timeCycle} eagerly. */
    private static TimerDefinition parseTimerEventDefinition(Element boundaryEventElement, String boundaryEventId) {
        Element timerEventDefinition = null;
        NodeList children = boundaryEventElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String localName = childElement.getLocalName() != null ? childElement.getLocalName() : childElement.getNodeName();
            if ("timerEventDefinition".equals(localName)) {
                timerEventDefinition = childElement;
                break;
            }
        }
        if (timerEventDefinition == null) {
            throw new AppException("Boundary event " + boundaryEventId
                    + " has no timerEventDefinition - only timer boundary events are supported", 400);
        }

        String duration = null;
        String date = null;
        String cycle = null;
        NodeList timerChildren = timerEventDefinition.getChildNodes();
        for (int i = 0; i < timerChildren.getLength(); i++) {
            Node child = timerChildren.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String localName = childElement.getLocalName() != null ? childElement.getLocalName() : childElement.getNodeName();
            String text = stripExpressionWrapper(childElement.getTextContent());
            if ("timeDuration".equals(localName)) {
                duration = text;
            } else if ("timeDate".equals(localName)) {
                date = text;
            } else if ("timeCycle".equals(localName)) {
                cycle = text;
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

        String condition = null;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String localName = childElement.getLocalName() != null ? childElement.getLocalName() : childElement.getNodeName();
            if ("conditionExpression".equals(localName)) {
                condition = stripExpressionWrapper(childElement.getTextContent());
                break;
            }
        }

        return new BpmnSequenceFlow(id, sourceRef, targetRef, condition);
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
