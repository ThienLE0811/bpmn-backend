package com.example.bpmn;

import com.example.bpmn.dto.BpmnProcessResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.model.BpmnProcess;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.service.impl.BpmnProcessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class BpmnProcessServiceTest {

    private BpmnProcessService bpmnProcessService;
    private final Map<String, BpmnProcess> storage = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        storage.clear();
        BpmnProcessRepository mockRepo = new BpmnProcessRepository() {
            @Override
            public BpmnProcess save(BpmnProcess process) {
                storage.put(process.getId(), process);
                return process;
            }

            @Override
            public Optional<BpmnProcess> findById(String id) {
                return Optional.ofNullable(storage.get(id));
            }

            @Override
            public Optional<BpmnProcess> findByProcessKey(String processKey) {
                return storage.values().stream()
                        .filter(p -> processKey.equals(p.getProcessKey()))
                        .findFirst();
            }

            @Override
            public List<BpmnProcess> findAll() {
                return new ArrayList<>(storage.values());
            }

            @Override
            public boolean deleteById(String id) {
                return storage.remove(id) != null;
            }
        };

        bpmnProcessService = new BpmnProcessServiceImpl(mockRepo);
    }

    @Test
    @DisplayName("Should return all bpmn processes")
    void testGetAllProcesses() {
        BpmnProcess p1 = new BpmnProcess("1", "loan_approval", "Loan Approval", 1, "<xml/>", "ACTIVE");
        BpmnProcess p2 = new BpmnProcess("2", "credit_card", "Credit Card Application", 1, "<xml/>", "ACTIVE");
        storage.put("1", p1);
        storage.put("2", p2);

        List<BpmnProcessResponse> result = bpmnProcessService.getAllProcesses();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should get process by id")
    void testGetProcessById() {
        BpmnProcess p = new BpmnProcess("proc-1", "kyc_process", "KYC Process", 1, "<xml/>", "ACTIVE");
        storage.put("proc-1", p);

        BpmnProcessResponse response = bpmnProcessService.getProcessById("proc-1");

        assertNotNull(response);
        assertEquals("proc-1", response.getId());
        assertEquals("kyc_process", response.getProcessKey());
        assertEquals("KYC Process", response.getName());
    }

    @Test
    @DisplayName("Should throw exception if process not found by id")
    void testGetProcessByIdNotFound() {
        AppException ex = assertThrows(AppException.class, () -> bpmnProcessService.getProcessById("unknown"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should get process by processKey")
    void testGetProcessByKey() {
        BpmnProcess p = new BpmnProcess("proc-1", "kyc_process", "KYC Process", 1, "<xml/>", "ACTIVE");
        storage.put("proc-1", p);

        BpmnProcessResponse response = bpmnProcessService.getProcessByKey("kyc_process");

        assertNotNull(response);
        assertEquals("kyc_process", response.getProcessKey());
    }
}
