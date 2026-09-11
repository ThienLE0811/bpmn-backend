package com.example.bpmn.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compares BPMN/DMN XML content while ignoring differences that don't change
 * the diagram itself (formatting whitespace, attribute order, namespace
 * prefix names, comments) - so re-saving a diagram unchanged doesn't bump
 * the version.
 */
public class XmlUtil {
    private static final Logger logger = LoggerFactory.getLogger(XmlUtil.class);

    private XmlUtil() {
    }

    public static boolean isEquivalent(String xmlA, String xmlB) {
        if (Objects.equals(xmlA, xmlB)) {
            return true;
        }
        if (xmlA == null || xmlB == null) {
            return false;
        }
        try {
            return normalize(xmlA).equals(normalize(xmlB));
        } catch (Exception e) {
            logger.warn("Failed to normalize XML for comparison, falling back to raw comparison: {}", e.getMessage());
            return xmlA.trim().equals(xmlB.trim());
        }
    }

    private static String normalize(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringComments(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        doc.getDocumentElement().normalize();

        StringBuilder sb = new StringBuilder();
        canonicalize(doc.getDocumentElement(), sb);
        return sb.toString();
    }

    private static void canonicalize(Node node, StringBuilder sb) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                Element element = (Element) node;
                String tagName = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();

                sb.append('<').append(element.getNamespaceURI() != null ? element.getNamespaceURI() + ":" : "")
                        .append(tagName);
                appendSortedAttributes(element, sb);
                sb.append('>');

                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    canonicalize(children.item(i), sb);
                }

                sb.append("</").append(tagName).append('>');
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                String text = node.getNodeValue();
                if (text != null) {
                    String trimmed = text.trim();
                    if (!trimmed.isEmpty()) {
                        sb.append(trimmed);
                    }
                }
                break;
            default:
                // ignore comments, processing instructions, etc. - they don't affect the diagram
        }
    }

    private static void appendSortedAttributes(Element element, StringBuilder sb) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null || attributes.getLength() == 0) {
            return;
        }

        List<Attr> sortedAttributes = new ArrayList<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getName();
            if (name.equals("xmlns") || name.startsWith("xmlns:")) {
                continue;
            }
            sortedAttributes.add(attr);
        }

        sortedAttributes.sort(
                Comparator.<Attr, String>comparing(a -> a.getNamespaceURI() == null ? "" : a.getNamespaceURI())
                        .thenComparing(a -> a.getLocalName() != null ? a.getLocalName() : a.getName()));

        for (Attr attr : sortedAttributes) {
            sb.append(' ');
            if (attr.getNamespaceURI() != null) {
                sb.append(attr.getNamespaceURI()).append(':');
            }
            sb.append(attr.getLocalName() != null ? attr.getLocalName() : attr.getName());
            sb.append("=\"").append(attr.getValue()).append('"');
        }
    }
}
