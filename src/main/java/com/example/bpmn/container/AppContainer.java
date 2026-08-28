package com.example.bpmn.container;

import com.example.bpmn.controller.BpmnProcessController;
import com.example.bpmn.controller.DmnDecisionController;
import com.example.bpmn.controller.WorkflowController;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.DmnDecisionRepository;
import com.example.bpmn.repository.WorkflowRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessRepository;
import com.example.bpmn.repository.impl.PostgresDmnDecisionRepository;
import com.example.bpmn.repository.impl.PostgresWorkflowRepository;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.WorkflowService;
import com.example.bpmn.service.impl.BpmnProcessServiceImpl;
import com.example.bpmn.service.impl.DmnDecisionServiceImpl;
import com.example.bpmn.service.impl.WorkflowServiceImpl;

/**
 * Dependency Injection container managing repositories, services, and controllers.
 */
public class AppContainer {

    // Repositories
    private final WorkflowRepository workflowRepository;
    private final BpmnProcessRepository bpmnProcessRepository;
    private final DmnDecisionRepository dmnDecisionRepository;

    // Services
    private final WorkflowService workflowService;
    private final BpmnProcessService bpmnProcessService;
    private final DmnDecisionService dmnDecisionService;

    // Controllers
    private final WorkflowController workflowController;
    private final BpmnProcessController bpmnProcessController;
    private final DmnDecisionController dmnDecisionController;

    public AppContainer() {
        // 1. Repositories initialization
        this.workflowRepository = new PostgresWorkflowRepository();
        this.bpmnProcessRepository = new PostgresBpmnProcessRepository();
        this.dmnDecisionRepository = new PostgresDmnDecisionRepository();

        // 2. Services initialization
        this.workflowService = new WorkflowServiceImpl(this.workflowRepository);
        this.bpmnProcessService = new BpmnProcessServiceImpl(this.bpmnProcessRepository);
        this.dmnDecisionService = new DmnDecisionServiceImpl(this.dmnDecisionRepository);

        // 3. Controllers initialization
        this.workflowController = new WorkflowController(this.workflowService);
        this.bpmnProcessController = new BpmnProcessController(this.bpmnProcessService);
        this.dmnDecisionController = new DmnDecisionController(this.dmnDecisionService);
    }

    public WorkflowController getWorkflowController() {
        return workflowController;
    }

    public BpmnProcessController getBpmnProcessController() {
        return bpmnProcessController;
    }

    public DmnDecisionController getDmnDecisionController() {
        return dmnDecisionController;
    }

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public BpmnProcessService getBpmnProcessService() {
        return bpmnProcessService;
    }

    public DmnDecisionService getDmnDecisionService() {
        return dmnDecisionService;
    }

    public WorkflowRepository getWorkflowRepository() {
        return workflowRepository;
    }

    public BpmnProcessRepository getBpmnProcessRepository() {
        return bpmnProcessRepository;
    }

    public DmnDecisionRepository getDmnDecisionRepository() {
        return dmnDecisionRepository;
    }
}
