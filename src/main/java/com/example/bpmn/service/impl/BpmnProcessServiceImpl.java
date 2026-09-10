package com.example.bpmn.service.impl;

import com.example.bpmn.dto.BpmnProcessRequest;
import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.dto.BpmnProcessUpdateRequest;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.BpmnProcessMapper;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.service.BpmnProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BpmnProcessServiceImpl implements BpmnProcessService {
    private static final Logger logger = LoggerFactory.getLogger(BpmnProcessServiceImpl.class);
    private final BpmnProcessRepository bpmnProcessRepository;

    public BpmnProcessServiceImpl(BpmnProcessRepository bpmnProcessRepository) {
        this.bpmnProcessRepository = bpmnProcessRepository;
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
    public BpmnProcessResponse createProcess(BpmnProcessRequest request) {
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
        process.setCreatedBy(request.getCreatedBy());
        process.setCreatedAt(now);
        process.setUpdatedAt(now);

        BpmnProcess saved = bpmnProcessRepository.save(process);
        logger.info("Saved new BPMN process with ID: {}", saved.getId());
        return BpmnProcessMapper.toResponse(saved);
    }

    @Override
    public BpmnProcessResponse updateProcess(String id, BpmnProcessUpdateRequest request) {
        BpmnProcess existing = bpmnProcessRepository.findById(id)
                .orElseThrow(() -> new AppException("BPMN process not found with id: " + id, 404));

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
        if (request.getBpmnXml() != null) {
            existing.setBpmnXml(request.getBpmnXml());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getUpdatedBy() != null) {
            existing.setUpdatedBy(request.getUpdatedBy());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        BpmnProcess saved = bpmnProcessRepository.save(existing);
        logger.info("Updated BPMN process with ID: {}", saved.getId());
        return BpmnProcessMapper.toResponse(saved);
    }

    @Override
    public void deleteProcess(String id) {
        boolean deleted = bpmnProcessRepository.deleteById(id);
        if (!deleted) {
            throw new AppException("BPMN process not found with id: " + id, 404);
        }
        logger.info("Deleted BPMN process with ID: {}", id);
    }
}
