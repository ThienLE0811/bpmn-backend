package com.example.bpmn.service.impl;

import com.example.bpmn.dto.BpmnProcessRequest;
import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.dto.BpmnProcessUpdateRequest;
import com.example.bpmn.dto.BpmnProcessVersionResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.BpmnProcessMapper;
import com.example.bpmn.mapper.BpmnProcessVersionMapper;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.model.BpmnProcessVersion;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.util.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BpmnProcessServiceImpl implements BpmnProcessService {
    private static final Logger logger = LoggerFactory.getLogger(BpmnProcessServiceImpl.class);
    private final BpmnProcessRepository bpmnProcessRepository;
    private final BpmnProcessVersionRepository bpmnProcessVersionRepository;

    public BpmnProcessServiceImpl(BpmnProcessRepository bpmnProcessRepository,
                                   BpmnProcessVersionRepository bpmnProcessVersionRepository) {
        this.bpmnProcessRepository = bpmnProcessRepository;
        this.bpmnProcessVersionRepository = bpmnProcessVersionRepository;
    }

    @Override
    public List<BpmnProcessResponse> getAllProcesses() {
        logger.info("Fetching all BPMN processes from database");
        return bpmnProcessRepository.findAll().stream()
                .map(BpmnProcessMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BpmnProcessResponse getProcessById(String id) {
        BpmnProcess process = bpmnProcessRepository.findById(id)
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + id, 404));
        return BpmnProcessMapper.toResponse(process);
    }

    @Override
    public BpmnProcessResponse getProcessByKey(String processKey) {
        BpmnProcess process = bpmnProcessRepository.findByProcessKey(processKey)
                .orElseThrow(() -> new AppException("BPMN process not found with key: " + processKey, 404));
        return BpmnProcessMapper.toResponse(process);
    }

    @Override
    public BpmnProcessResponse createProcess(BpmnProcessRequest request, String requesterUsername) {
        if (request.getProcessKey() == null || request.getProcessKey().isBlank()) {
            throw new AppException("BPMN process key must not be empty", 400);
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new AppException("BPMN process name must not be empty", 400);
        }

        LocalDateTime now = LocalDateTime.now();
        BpmnProcess process = new BpmnProcess();
        process.setId(UUID.randomUUID().toString());
        process.setProcessKey(request.getProcessKey());
        process.setName(request.getName());
        process.setDescription(request.getDescription());
        process.setCategory(request.getCategory());
        process.setVersion(1);
        process.setBpmnXml(request.getBpmnXml());
        process.setStatus("DRAFT");
        process.setCreatedBy(requesterUsername);
        process.setCreatedAt(now);
        process.setUpdatedAt(now);

        BpmnProcess saved = bpmnProcessRepository.save(process);
        saveVersionSnapshot(saved, saved.getCreatedBy());
        logger.info("Saved new BPMN process with ID: {}", saved.getId());
        return BpmnProcessMapper.toResponse(saved);
    }

    @Override
    public BpmnProcessResponse updateProcess(String id, BpmnProcessUpdateRequest request,
                                              String requesterUsername, String requesterRole) {
        BpmnProcess existing = bpmnProcessRepository.findById(id)
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + id, 404));
        requireOwnerOrAdmin(existing.getCreatedBy(), requesterUsername, requesterRole,
                "Only the creator or an administrator can modify this process");

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new AppException("BPMN process name must not be empty", 400);
            }
            existing.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            existing.setCategory(request.getCategory());
        }
        boolean versionBumped = false;
        if (request.getBpmnXml() != null) {
            if (!XmlUtil.isEquivalent(existing.getBpmnXml(), request.getBpmnXml())) {
                existing.setVersion(existing.getVersion() + 1);
                versionBumped = true;
            }
            existing.setBpmnXml(request.getBpmnXml());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        existing.setUpdatedBy(requesterUsername);
        existing.setUpdatedAt(LocalDateTime.now());

        BpmnProcess saved = bpmnProcessRepository.save(existing);
        if (versionBumped) {
            saveVersionSnapshot(saved, saved.getUpdatedBy());
        }
        logger.info("Updated BPMN process with ID: {}", saved.getId());
        return BpmnProcessMapper.toResponse(saved);
    }

    private void saveVersionSnapshot(BpmnProcess process, String createdBy) {
        BpmnProcessVersion snapshot = new BpmnProcessVersion();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setProcessId(process.getId());
        snapshot.setVersion(process.getVersion());
        snapshot.setBpmnXml(process.getBpmnXml());
        snapshot.setCreatedBy(createdBy);
        snapshot.setCreatedAt(LocalDateTime.now());
        bpmnProcessVersionRepository.save(snapshot);
    }

    @Override
    public void deleteProcess(String id, String requesterUsername, String requesterRole) {
        BpmnProcess existing = bpmnProcessRepository.findById(id)
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + id, 404));
        requireOwnerOrAdmin(existing.getCreatedBy(), requesterUsername, requesterRole,
                "Only the creator or an administrator can delete this process");

        bpmnProcessRepository.deleteById(id);
        logger.info("Deleted BPMN process with ID: {}", id);
    }

    private void requireOwnerOrAdmin(String createdBy, String requesterUsername, String requesterRole, String message) {
        boolean isOwner = createdBy != null && createdBy.equals(requesterUsername);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        if (!isOwner && !isAdmin) {
            throw new AppException(message, 403);
        }
    }

    @Override
    public List<BpmnProcessVersionResponse> getVersionHistory(String processId) {
        bpmnProcessRepository.findById(processId)
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + processId, 404));
        return bpmnProcessVersionRepository.findByProcessId(processId).stream()
                .map(BpmnProcessVersionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BpmnProcessVersionResponse getVersion(String processId, int version) {
        bpmnProcessRepository.findById(processId)
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + processId, 404));
        return bpmnProcessVersionRepository.findByProcessIdAndVersion(processId, version)
                .map(BpmnProcessVersionMapper::toResponse)
                .orElseThrow(() -> new AppException(
                        "Version " + version + " not found for BPMN process id: " + processId, 404));
    }
}
