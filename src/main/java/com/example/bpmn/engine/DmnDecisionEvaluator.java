package com.example.bpmn.engine;

import java.util.Map;

/**
 * Callback {@link ProcessEngine#advance} uses to evaluate a business rule task's DMN decision.
 * Kept as an injected interface (rather than a direct repository/service call from inside the
 * engine) so {@code ProcessEngine} stays free of I/O and easy to unit test - the real
 * implementation (backed by {@code DmnDecisionService}) is wired in by the caller.
 */
@FunctionalInterface
public interface DmnDecisionEvaluator {
    /** Evaluates the DMN decision identified by {@code decisionRef} (a {@code DmnDecision.decisionKey}) against {@code variables} and returns its output columns. */
    Map<String, Object> evaluate(String decisionRef, Map<String, Object> variables);
}
