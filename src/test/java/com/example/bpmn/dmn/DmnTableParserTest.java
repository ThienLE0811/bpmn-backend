package com.example.bpmn.dmn;

import com.example.bpmn.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmnTableParserTest {

    @Test
    @DisplayName("Should parse hit policy, inputs, outputs and rules")
    void parsesDecisionTableStructure() {
        DmnDecisionTable table = DmnTableParser.parse(DmnFixtures.SIMPLE_DECISION_XML);

        assertEquals("FIRST", table.getHitPolicy());
        assertEquals(1, table.getInputs().size());
        assertEquals("amount", table.getInputs().get(0).getExpression());
        assertEquals(1, table.getOutputs().size());
        assertEquals("approved", table.getOutputs().get(0).getName());
        assertEquals(2, table.getRules().size());
        assertEquals("< 1000", table.getRules().get(0).getInputEntries().get(0));
        assertEquals("\"true\"", table.getRules().get(0).getOutputEntries().get(0));
        assertEquals("-", table.getRules().get(1).getInputEntries().get(0));
    }

    @Test
    @DisplayName("Should default to UNIQUE hit policy when the attribute is missing")
    void defaultsToUniqueHitPolicyWhenMissing() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs">
                  <decision id="Decision_1" name="Test">
                    <decisionTable id="DecisionTable_1">
                      <input id="Input_1">
                        <inputExpression id="InputExpression_1" typeRef="string"><text>amount</text></inputExpression>
                      </input>
                      <output id="Output_1" name="result" typeRef="string" />
                      <rule id="Rule_1"><inputEntry><text>-</text></inputEntry><outputEntry><text>"x"</text></outputEntry></rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """;

        DmnDecisionTable table = DmnTableParser.parse(xml);

        assertEquals("UNIQUE", table.getHitPolicy());
    }

    @Test
    @DisplayName("Should reject XML without a <decision> element")
    void rejectsXmlWithoutDecisionElement() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs" />
                """;

        AppException ex = assertThrows(AppException.class, () -> DmnTableParser.parse(xml));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should reject a decision without a <decisionTable> element")
    void rejectsDecisionWithoutDecisionTable() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs">
                  <decision id="Decision_1" name="Empty" />
                </definitions>
                """;

        AppException ex = assertThrows(AppException.class, () -> DmnTableParser.parse(xml));
        assertEquals(400, ex.getStatusCode());
    }
}
