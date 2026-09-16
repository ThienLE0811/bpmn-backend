package com.example.bpmn.dmn;

/** Shared DMN XML fixtures for {@link DmnTableParserTest} and {@link DmnEvaluatorTest}. */
final class DmnFixtures {

    private DmnFixtures() {
    }

    /** Mirrors the shape of the frontend's default DMN template: 1 input, 1 output, hitPolicy FIRST. */
    static final String SIMPLE_DECISION_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs" name="Decision Rules">
              <decision id="Decision_1" name="Risk Decision">
                <decisionTable id="DecisionTable_1" hitPolicy="FIRST">
                  <input id="Input_1" label="Amount">
                    <inputExpression id="InputExpression_1" typeRef="string">
                      <text>amount</text>
                    </inputExpression>
                  </input>
                  <output id="Output_1" label="Approved" name="approved" typeRef="string" />
                  <rule id="Rule_1">
                    <inputEntry id="UnaryTests_1"><text>&lt; 1000</text></inputEntry>
                    <outputEntry id="LiteralExpression_1"><text>"true"</text></outputEntry>
                  </rule>
                  <rule id="Rule_2">
                    <inputEntry id="UnaryTests_2"><text>-</text></inputEntry>
                    <outputEntry id="LiteralExpression_2"><text>"false"</text></outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """;

    /** Builds a 1-input ("amount"), 1-output ("result") decision table with the given hit policy and rules. */
    static String singleInputTable(String hitPolicy, String[]... rules) {
        StringBuilder rulesXml = new StringBuilder();
        for (String[] rule : rules) {
            rulesXml.append("<rule><inputEntry><text>").append(escapeXml(rule[0]))
                    .append("</text></inputEntry><outputEntry><text>").append(escapeXml(rule[1]))
                    .append("</text></outputEntry></rule>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs">
                  <decision id="Decision_1" name="Test Decision">
                    <decisionTable id="DecisionTable_1" hitPolicy="%s">
                      <input id="Input_1" label="Amount">
                        <inputExpression id="InputExpression_1" typeRef="string">
                          <text>amount</text>
                        </inputExpression>
                      </input>
                      <output id="Output_1" label="Result" name="result" typeRef="string" />
                      %s
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(hitPolicy, rulesXml);
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
