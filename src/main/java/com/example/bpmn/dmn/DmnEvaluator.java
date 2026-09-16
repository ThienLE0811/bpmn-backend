package com.example.bpmn.dmn;

import com.example.bpmn.exception.AppException;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure evaluation logic for a {@link DmnDecisionTable}: given process variables, finds the
 * winning rule(s) per hit policy and returns the output columns to merge into process variables.
 * No persistence, no I/O - mirrors {@link com.example.bpmn.engine.ProcessEngine}.
 *
 * <p>v1 supports only the {@code UNIQUE} and {@code FIRST} hit policies, and only unary tests
 * that are a literal equality, a comparison ({@code <}, {@code <=}, {@code >}, {@code >=},
 * {@code !=}), or the {@code -} ("any value") wildcard. FEEL ranges ({@code [100..200]}) and
 * comma-separated value lists ({@code "A","B"}) are not supported and raise a clear error rather
 * than being evaluated incorrectly.
 */
public class DmnEvaluator {
    private static final JexlEngine JEXL = new JexlBuilder().create();
    private static final Pattern COMPARISON_PREFIX = Pattern.compile("^(<=|>=|!=|<|>)\\s*(.+)$", Pattern.DOTALL);

    private DmnEvaluator() {
    }

    public static Map<String, Object> evaluate(DmnDecisionTable table, Map<String, Object> variables) {
        Map<String, Object> vars = variables != null ? variables : Map.of();
        List<DmnRule> matched = new ArrayList<>();
        for (DmnRule rule : table.getRules()) {
            if (ruleMatches(table, rule, vars)) {
                matched.add(rule);
            }
        }

        DmnRule winner;
        String hitPolicy = table.getHitPolicy();
        if ("UNIQUE".equals(hitPolicy)) {
            if (matched.isEmpty()) {
                return Map.of();
            }
            if (matched.size() > 1) {
                throw new AppException("DMN hit policy UNIQUE expected at most one matching rule but found "
                        + matched.size(), 400);
            }
            winner = matched.get(0);
        } else if ("FIRST".equals(hitPolicy)) {
            if (matched.isEmpty()) {
                return Map.of();
            }
            winner = matched.get(0);
        } else {
            throw new AppException("DMN hit policy \"" + hitPolicy + "\" is not supported", 400);
        }

        Map<String, Object> result = new HashMap<>();
        List<DmnOutput> outputs = table.getOutputs();
        List<String> outputEntries = winner.getOutputEntries();
        for (int i = 0; i < outputs.size() && i < outputEntries.size(); i++) {
            result.put(outputs.get(i).getName(), parseLiteral(outputEntries.get(i)));
        }
        return result;
    }

    private static boolean ruleMatches(DmnDecisionTable table, DmnRule rule, Map<String, Object> variables) {
        List<DmnInput> inputs = table.getInputs();
        List<String> inputEntries = rule.getInputEntries();
        for (int i = 0; i < inputs.size() && i < inputEntries.size(); i++) {
            String entry = inputEntries.get(i);
            if (entry == null || entry.isBlank() || "-".equals(entry.trim())) {
                continue;
            }
            if (!evaluateUnaryTest(inputs.get(i).getExpression(), entry, variables)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateUnaryTest(String inputExpression, String entry, Map<String, Object> variables) {
        String trimmed = entry.trim();
        if (isUnsupportedRange(trimmed) || isUnsupportedCommaList(trimmed)) {
            throw new AppException("Unsupported DMN input entry \"" + entry
                    + "\" - v1 only supports literal equality, comparisons and the \"-\" wildcard", 400);
        }

        String operator = "==";
        String literal = trimmed;
        Matcher matcher = COMPARISON_PREFIX.matcher(trimmed);
        if (matcher.matches()) {
            operator = matcher.group(1);
            literal = matcher.group(2).trim();
        }

        String jexlExpression = "(" + inputExpression + ") " + operator + " " + literal;
        try {
            JexlContext context = new MapContext(new HashMap<>(variables));
            Object result = JEXL.createExpression(jexlExpression).evaluate(context);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            throw new AppException("Failed to evaluate DMN input entry \"" + entry + "\": " + e.getMessage(), 400);
        }
    }

    private static boolean isUnsupportedRange(String trimmed) {
        return trimmed.startsWith("[") || trimmed.startsWith("(") || trimmed.startsWith("]");
    }

    private static boolean isUnsupportedCommaList(String trimmed) {
        boolean fullyQuoted = trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                && trimmed.indexOf('"', 1) == trimmed.length() - 1;
        return trimmed.contains(",") && !fullyQuoted;
    }

    private static Object parseLiteral(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if ("true".equals(trimmed) || "false".equals(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignoredLong) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignoredDouble) {
                return trimmed;
            }
        }
    }
}
