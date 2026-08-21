package com.example.bpmn;

import com.example.bpmn.dto.WorkflowRequest;
import com.example.bpmn.dto.WorkflowResponse;
import com.example.bpmn.exception.AppException;
import com.example.bpmn.repository.impl.InMemoryWorkflowRepository;
import com.example.bpmn.service.WorkflowService;
import com.example.bpmn.service.impl.WorkflowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowServiceTest {

    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowServiceImpl(new InMemoryWorkflowRepository());
    }

    @Test
    @DisplayName("Should create workflow successfully")
    void testCreateWorkflowSuccess() {
        WorkflowRequest request = new WorkflowRequest("Loan_Approval", "Loan approval process");
        WorkflowResponse response = workflowService.createWorkflow(request);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("Loan_Approval", response.getName());
        assertEquals("Loan approval process", response.getDescription());
        assertEquals("CREATED", response.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when workflow name is blank")
    void testCreateWorkflowBlankNameThrowsException() {
        WorkflowRequest request = new WorkflowRequest("", "Description");

        AppException exception = assertThrows(AppException.class, () -> workflowService.createWorkflow(request));
        assertEquals(400, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should retrieve created workflow by id")
    void testGetWorkflowById() {
        WorkflowRequest request = new WorkflowRequest("Invoice_Workflow", "Processing invoices");
        WorkflowResponse created = workflowService.createWorkflow(request);

        WorkflowResponse found = workflowService.getWorkflowById(created.getId());
        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
    }

    @Test
    @DisplayName("Should list all workflows")
    void testGetAllWorkflows() {
        workflowService.createWorkflow(new WorkflowRequest("WF_1", "Desc 1"));
        workflowService.createWorkflow(new WorkflowRequest("WF_2", "Desc 2"));

        List<WorkflowResponse> list = workflowService.getAllWorkflows();
        assertEquals(2, list.size());
    }
}
