package com.example.bpmn.dmn;

import com.example.bpmn.exception.AppException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a DMN 1.x XML document into an in-memory {@link DmnDecisionTable} that
 * {@link DmnEvaluator} can evaluate. Only the first {@code <decision>}'s first
 * {@code <decisionTable>} is read - multiple decisions per file / decision chaining
 * ({@code <informationRequirement>}) is not supported in v1.
 */
public class DmnTableParser {

    private DmnTableParser() {
    }

    public static DmnDecisionTable parse(String dmnXml) {
        Document doc = parseXml(dmnXml);

        NodeList decisionElements = doc.getElementsByTagNameNS("*", "decision");
        if (decisionElements.getLength() == 0) {
            throw new AppException("DMN XML does not contain a <decision> element", 400);
        }
        Element decisionElement = (Element) decisionElements.item(0);

        NodeList decisionTableElements = decisionElement.getElementsByTagNameNS("*", "decisionTable");
        if (decisionTableElements.getLength() == 0) {
            throw new AppException("DMN decision does not contain a <decisionTable> element", 400);
        }
        Element decisionTableElement = (Element) decisionTableElements.item(0);

        String hitPolicy = nullIfBlank(decisionTableElement.getAttribute("hitPolicy"));
        if (hitPolicy == null) {
            hitPolicy = "UNIQUE";
        }

        List<DmnInput> inputs = new ArrayList<>();
        List<DmnOutput> outputs = new ArrayList<>();
        List<DmnRule> rules = new ArrayList<>();

        NodeList children = decisionTableElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String localName = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();

            switch (localName) {
                case "input" -> {
                    String id = element.getAttribute("id");
                    Element inputExpression = firstChildElementByLocalName(element, "inputExpression");
                    String expression = inputExpression != null ? textOfChild(inputExpression, "text") : null;
                    inputs.add(new DmnInput(id, expression));
                }
                case "output" -> {
                    String id = element.getAttribute("id");
                    String name = nullIfBlank(element.getAttribute("name"));
                    outputs.add(new DmnOutput(id, name));
                }
                case "rule" -> rules.add(parseRule(element));
                default -> {
                    // annotation, etc. - not part of the evaluated table structure
                }
            }
        }

        return new DmnDecisionTable(hitPolicy, inputs, outputs, rules);
    }

    private static DmnRule parseRule(Element ruleElement) {
        List<String> inputEntries = new ArrayList<>();
        List<String> outputEntries = new ArrayList<>();

        NodeList children = ruleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String localName = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();

            if ("inputEntry".equals(localName)) {
                inputEntries.add(textOfChild(element, "text"));
            } else if ("outputEntry".equals(localName)) {
                outputEntries.add(textOfChild(element, "text"));
            }
        }

        return new DmnRule(inputEntries, outputEntries);
    }

    private static Element firstChildElementByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String childLocalName = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
            if (localName.equals(childLocalName)) {
                return element;
            }
        }
        return null;
    }

    private static String textOfChild(Element parent, String childLocalName) {
        Element textElement = firstChildElementByLocalName(parent, childLocalName);
        if (textElement == null) {
            return null;
        }
        return nullIfBlank(textElement.getTextContent());
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
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
            throw new AppException("Invalid DMN XML: " + e.getMessage(), 400);
        }
    }
}
