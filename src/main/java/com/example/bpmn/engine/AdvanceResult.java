package com.example.bpmn.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outcome of walking a {@link BpmnProcessDefinition} forward from a given node. Because a
 * parallel gateway can fork one token into several concurrent branches, a single
 * {@link ProcessEngine#advance} call can land on zero, one or many user tasks at once.
 * {@code pendingJoinArrivals} is the caller's join-synchronization state (see
 * {@link ProcessEngine}) after this call - it must be persisted on the process instance
 * verbatim and passed back in on the next call, even when it is empty.
 *
 * <p>{@code updatedVariables} reflects any variables a business rule task set from its DMN
 * decision result during this walk - callers must persist it instead of the map they passed in,
 * since a downstream gateway reached in the same call may have branched on those new values.
 */
public class AdvanceResult {
    private final List<String> newUserTaskNodeIds;
    private final Set<String> pendingJoinArrivals;
    private final Map<String, Object> updatedVariables;

    AdvanceResult(List<String> newUserTaskNodeIds, Set<String> pendingJoinArrivals, Map<String, Object> updatedVariables) {
        this.newUserTaskNodeIds = List.copyOf(newUserTaskNodeIds);
        this.pendingJoinArrivals = Set.copyOf(pendingJoinArrivals);
        // Not Map.copyOf: process variables may legitimately contain null values, which it rejects.
        this.updatedVariables = Collections.unmodifiableMap(new HashMap<>(updatedVariables));
    }

    /** Node ids of the user tasks this call reached and is now waiting at - may be empty (all branches ended) or contain several (fork). */
    public List<String> getNewUserTaskNodeIds() {
        return newUserTaskNodeIds;
    }

    /** Updated set of sequence-flow ids that have arrived at a not-yet-satisfied parallel join, to persist on the process instance. */
    public Set<String> getPendingJoinArrivals() {
        return pendingJoinArrivals;
    }

    /** True when this call produced no new waiting tasks and left no join pending - the branches it walked all reached an end event. */
    public boolean isFullyResolved() {
        return newUserTaskNodeIds.isEmpty() && pendingJoinArrivals.isEmpty();
    }

    /** Process variables after this call, including anything a business rule task set from its DMN decision result - persist this, not the map passed in. */
    public Map<String, Object> getUpdatedVariables() {
        return updatedVariables;
    }
}
