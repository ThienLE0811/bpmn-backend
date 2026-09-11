package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnProcessVersion;
import java.util.List;
import java.util.Optional;

public interface BpmnProcessVersionRepository {
    BpmnProcessVersion save(BpmnProcessVersion version);
    List<BpmnProcessVersion> findByProcessId(String processId);
    Optional<BpmnProcessVersion> findByProcessIdAndVersion(String processId, int version);
}
