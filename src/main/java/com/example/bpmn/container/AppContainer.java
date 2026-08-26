package com.example.bpmn.container;

import com.example.bpmn.controller.BpmnProcessController;
import com.example.bpmn.controller.WorkflowController;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.WorkflowRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessRepository;
import com.example.bpmn.repository.impl.PostgresWorkflowRepository;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.service.WorkflowService;
import com.example.bpmn.service.impl.BpmnProcessServiceImpl;
import com.example.bpmn.service.impl.WorkflowServiceImpl;

/**
 * Dependency Injection container managing repositories, services, and controllers.
 */
public class AppContainer {

    // Repositories
    private final WorkflowRepository workflowRepository;
    private final BpmnProcessRepository bpmnProcessRepository;

    // Services
    private final WorkflowService workflowService;
    private final BpmnProcessService bpmnProcessService;

    // Controllers
    private final WorkflowController workflowController;
    private final BpmnProcessController bpmnProcessController;

    public AppContainer() {
        // 1. Repositories initialization
        this.workflowRepository = new PostgresWorkflowRepository();
        this.bpmnProcessRepository = new PostgresBpmnProcessRepository();

        // 2. Services initialization
        this.workflowService = new WorkflowServiceImpl(this.workflowRepository);
        this.bpmnProcessService = new BpmnProcessServiceImpl(this.bpmnProcessRepository);

        // 3. Controllers initialization
        this.workflowController = new WorkflowController(this.workflowService);
        this.bpmnProcessController = new BpmnProcessController(this.bpmnProcessService);
    }

    public WorkflowController getWorkflowController() {
        return workflowController;
    }

    public BpmnProcessController getBpmnProcessController() {
        return bpmnProcessController;
    }

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public BpmnProcessService getBpmnProcessService() {
        return bpmnProcessService;
    }

    public WorkflowRepository getWorkflowRepository() {
        return workflowRepository;
    }

    public BpmnProcessRepository getBpmnProcessRepository() {
        return bpmnProcessRepository;
    }
}
