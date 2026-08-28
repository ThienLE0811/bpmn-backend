package com.example.bpmn.service.impl;

import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.repository.DmnDecisionRepository;
import com.example.bpmn.service.DmnDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DmnDecisionServiceImpl implements DmnDecisionService {
    private static final Logger logger = LoggerFactory.getLogger(DmnDecisionServiceImpl.class);
    private final DmnDecisionRepository dmnDecisionRepository;

    public DmnDecisionServiceImpl(DmnDecisionRepository dmnDecisionRepository) {
        this.dmnDecisionRepository = dmnDecisionRepository;
    }

    @Override
    public List<DmnDecisionResponse> getAllDecisions() {
        logger.info("Fetching all DMN decisions from database");
        return dmnDecisionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DmnDecisionResponse getDecisionById(String id) {
        DmnDecision decision = dmnDecisionRepository.findById(id)
                .orElseThrow(() -> new AppException("DMN decision not found with id: " + id, 404));
        return mapToResponse(decision);
    }

    @Override
    public DmnDecisionResponse getDecisionByKey(String decisionKey) {
        DmnDecision decision = dmnDecisionRepository.findByDecisionKey(decisionKey)
                .orElseThrow(() -> new AppException("DMN decision not found with key: " + decisionKey, 404));
        return mapToResponse(decision);
    }

    private DmnDecisionResponse mapToResponse(DmnDecision decision) {
        return new DmnDecisionResponse(
                decision.getId(),
                decision.getDecisionKey(),
                decision.getName(),
                decision.getDescription(),
                decision.getHitPolicy(),
                decision.getCategory(),
                decision.getVersion(),
                decision.getDmnXml(),
                decision.getStatus(),
                decision.getCreatedBy(),
                decision.getUpdatedBy(),
                decision.getCreatedAt(),
                decision.getUpdatedAt()
        );
    }
}
