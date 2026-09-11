package com.example.bpmn.service.impl;

import com.example.bpmn.dto.DmnDecisionRequest;
import com.example.bpmn.dto.DmnDecisionResponse;
import com.example.bpmn.dto.DmnDecisionUpdateRequest;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.DmnDecisionMapper;
import com.example.bpmn.model.DmnDecision;
import com.example.bpmn.model.DmnDecisionVersion;
import com.example.bpmn.repository.DmnDecisionRepository;
import com.example.bpmn.repository.DmnDecisionVersionRepository;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.util.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DmnDecisionServiceImpl implements DmnDecisionService {
    private static final Logger logger = LoggerFactory.getLogger(DmnDecisionServiceImpl.class);
    private final DmnDecisionRepository dmnDecisionRepository;
    private final DmnDecisionVersionRepository dmnDecisionVersionRepository;

    public DmnDecisionServiceImpl(DmnDecisionRepository dmnDecisionRepository,
                                   DmnDecisionVersionRepository dmnDecisionVersionRepository) {
        this.dmnDecisionRepository = dmnDecisionRepository;
        this.dmnDecisionVersionRepository = dmnDecisionVersionRepository;
    }

    @Override
    public List<DmnDecisionResponse> getAllDecisions() {
        logger.info("Fetching all DMN decisions from database");
        return dmnDecisionRepository.findAll().stream()
                .map(DmnDecisionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DmnDecisionResponse getDecisionById(String id) {
        DmnDecision decision = dmnDecisionRepository.findById(id)
                .orElseThrow(() -> new AppException("DMN decision not found with id: " + id, 404));
        return DmnDecisionMapper.toResponse(decision);
    }

    @Override
    public DmnDecisionResponse getDecisionByKey(String decisionKey) {
        DmnDecision decision = dmnDecisionRepository.findByDecisionKey(decisionKey)
                .orElseThrow(() -> new AppException("DMN decision not found with key: " + decisionKey, 404));
        return DmnDecisionMapper.toResponse(decision);
    }

    @Override
    public DmnDecisionResponse createDecision(DmnDecisionRequest request) {
        if (request.getDecisionKey() == null || request.getDecisionKey().isBlank()) {
            throw new AppException("DMN decision key must not be empty", 400);
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new AppException("DMN decision name must not be empty", 400);
        }

        LocalDateTime now = LocalDateTime.now();
        DmnDecision decision = new DmnDecision();
        decision.setId(UUID.randomUUID().toString());
        decision.setDecisionKey(request.getDecisionKey());
        decision.setName(request.getName());
        decision.setDescription(request.getDescription());
        decision.setHitPolicy(request.getHitPolicy() != null && !request.getHitPolicy().isBlank()
                ? request.getHitPolicy() : "UNIQUE");
        decision.setCategory(request.getCategory());
        decision.setVersion(1);
        decision.setDmnXml(request.getDmnXml());
        decision.setStatus("DRAFT");
        decision.setCreatedBy(request.getCreatedBy() != null && !request.getCreatedBy().isBlank()
                ? request.getCreatedBy() : "system");
        decision.setCreatedAt(now);
        decision.setUpdatedAt(now);

        DmnDecision saved = dmnDecisionRepository.save(decision);
        saveVersionSnapshot(saved, saved.getCreatedBy());
        logger.info("Saved new DMN decision with ID: {}", saved.getId());
        return DmnDecisionMapper.toResponse(saved);
    }

    @Override
    public DmnDecisionResponse updateDecision(String id, DmnDecisionUpdateRequest request) {
        DmnDecision existing = dmnDecisionRepository.findById(id)
                .orElseThrow(() -> new AppException("DMN decision not found with id: " + id, 404));

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new AppException("DMN decision name must not be empty", 400);
            }
            existing.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getHitPolicy() != null) {
            existing.setHitPolicy(request.getHitPolicy());
        }
        if (request.getCategory() != null) {
            existing.setCategory(request.getCategory());
        }
        boolean versionBumped = false;
        if (request.getDmnXml() != null) {
            if (!XmlUtil.isEquivalent(existing.getDmnXml(), request.getDmnXml())) {
                existing.setVersion(existing.getVersion() + 1);
                versionBumped = true;
            }
            existing.setDmnXml(request.getDmnXml());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getUpdatedBy() != null) {
            existing.setUpdatedBy(request.getUpdatedBy());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        DmnDecision saved = dmnDecisionRepository.save(existing);
        if (versionBumped) {
            saveVersionSnapshot(saved, saved.getUpdatedBy());
        }
        logger.info("Updated DMN decision with ID: {}", saved.getId());
        return DmnDecisionMapper.toResponse(saved);
    }

    private void saveVersionSnapshot(DmnDecision decision, String createdBy) {
        DmnDecisionVersion snapshot = new DmnDecisionVersion();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setDecisionId(decision.getId());
        snapshot.setVersion(decision.getVersion());
        snapshot.setDmnXml(decision.getDmnXml());
        snapshot.setCreatedBy(createdBy);
        snapshot.setCreatedAt(LocalDateTime.now());
        dmnDecisionVersionRepository.save(snapshot);
    }

    @Override
    public void deleteDecision(String id) {
        boolean deleted = dmnDecisionRepository.deleteById(id);
        if (!deleted) {
            throw new AppException("DMN decision not found with id: " + id, 404);
        }
        logger.info("Deleted DMN decision with ID: {}", id);
    }
}
