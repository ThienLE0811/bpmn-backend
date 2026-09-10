package com.example.bpmn.service.impl;

import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.mapper.BpmnProcessMapper;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.service.BpmnProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
}
