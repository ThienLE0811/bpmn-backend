package com.example.bpmn.container;

import com.example.bpmn.connector.ConnectorRegistry;
import com.example.bpmn.connector.HttpConnector;
import com.example.bpmn.controller.AuthController;
import com.example.bpmn.controller.BpmnProcessController;
import com.example.bpmn.controller.DmnDecisionController;
import com.example.bpmn.controller.ProcessInstanceController;
import com.example.bpmn.controller.TaskController;
import com.example.bpmn.controller.UserController;
import com.example.bpmn.repository.BpmnProcessRepository;
import com.example.bpmn.repository.BpmnProcessStartTimerRepository;
import com.example.bpmn.repository.BpmnProcessVersionRepository;
import com.example.bpmn.repository.DmnDecisionRepository;
import com.example.bpmn.repository.DmnDecisionVersionRepository;
import com.example.bpmn.repository.ProcessInstanceRepository;
import com.example.bpmn.repository.ProcessInstanceTimerRepository;
import com.example.bpmn.repository.RefreshTokenRepository;
import com.example.bpmn.repository.TaskRepository;
import com.example.bpmn.repository.UserRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessStartTimerRepository;
import com.example.bpmn.repository.impl.PostgresBpmnProcessVersionRepository;
import com.example.bpmn.repository.impl.PostgresDmnDecisionRepository;
import com.example.bpmn.repository.impl.PostgresDmnDecisionVersionRepository;
import com.example.bpmn.repository.impl.PostgresProcessInstanceRepository;
import com.example.bpmn.repository.impl.PostgresProcessInstanceTimerRepository;
import com.example.bpmn.repository.impl.PostgresRefreshTokenRepository;
import com.example.bpmn.repository.impl.PostgresTaskRepository;
import com.example.bpmn.repository.impl.PostgresUserRepository;
import com.example.bpmn.service.AuthService;
import com.example.bpmn.service.BpmnProcessService;
import com.example.bpmn.service.DmnDecisionService;
import com.example.bpmn.service.ProcessInstanceService;
import com.example.bpmn.service.TaskService;
import com.example.bpmn.service.UserService;
import com.example.bpmn.service.impl.AuthServiceImpl;
import com.example.bpmn.service.impl.BpmnProcessServiceImpl;
import com.example.bpmn.service.impl.DmnDecisionServiceImpl;
import com.example.bpmn.service.impl.ProcessInstanceServiceImpl;
import com.example.bpmn.service.impl.TaskServiceImpl;
import com.example.bpmn.service.impl.UserServiceImpl;

import java.util.List;

/**
 * Dependency Injection container managing repositories, services, and controllers.
 */
public class AppContainer {

    // Repositories
    private final BpmnProcessRepository bpmnProcessRepository;
    private final BpmnProcessVersionRepository bpmnProcessVersionRepository;
    private final DmnDecisionRepository dmnDecisionRepository;
    private final DmnDecisionVersionRepository dmnDecisionVersionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskRepository taskRepository;
    private final ProcessInstanceTimerRepository processInstanceTimerRepository;
    private final BpmnProcessStartTimerRepository bpmnProcessStartTimerRepository;

    // Connectors available to service tasks
    private final ConnectorRegistry connectorRegistry;

    // Services
    private final BpmnProcessService bpmnProcessService;
    private final DmnDecisionService dmnDecisionService;
    private final UserService userService;
    private final AuthService authService;
    private final ProcessInstanceService processInstanceService;
    private final TaskService taskService;

    // Controllers
    private final BpmnProcessController bpmnProcessController;
    private final DmnDecisionController dmnDecisionController;
    private final UserController userController;
    private final AuthController authController;
    private final ProcessInstanceController processInstanceController;
    private final TaskController taskController;

    public AppContainer() {
        // 1. Repositories initialization
        this.bpmnProcessRepository = new PostgresBpmnProcessRepository();
        this.bpmnProcessVersionRepository = new PostgresBpmnProcessVersionRepository();
        this.dmnDecisionRepository = new PostgresDmnDecisionRepository();
        this.dmnDecisionVersionRepository = new PostgresDmnDecisionVersionRepository();
        this.userRepository = new PostgresUserRepository();
        this.refreshTokenRepository = new PostgresRefreshTokenRepository();
        this.processInstanceRepository = new PostgresProcessInstanceRepository();
        this.taskRepository = new PostgresTaskRepository();
        this.processInstanceTimerRepository = new PostgresProcessInstanceTimerRepository();
        this.bpmnProcessStartTimerRepository = new PostgresBpmnProcessStartTimerRepository();

        // 2. Connectors a serviceTask can bind to via <camunda:connectorId>
        this.connectorRegistry = new ConnectorRegistry(List.of(new HttpConnector()));

        // 3. Services initialization
        this.bpmnProcessService = new BpmnProcessServiceImpl(this.bpmnProcessRepository, this.bpmnProcessVersionRepository, this.bpmnProcessStartTimerRepository);
        this.dmnDecisionService = new DmnDecisionServiceImpl(this.dmnDecisionRepository, this.dmnDecisionVersionRepository);
        this.userService = new UserServiceImpl(this.userRepository);
        this.authService = new AuthServiceImpl(this.userRepository, this.refreshTokenRepository);
        this.processInstanceService = new ProcessInstanceServiceImpl(this.bpmnProcessRepository, this.processInstanceRepository, this.taskRepository, this.dmnDecisionService, this.processInstanceTimerRepository, this.bpmnProcessStartTimerRepository, this.connectorRegistry);
        this.taskService = new TaskServiceImpl(this.taskRepository, this.processInstanceRepository, this.bpmnProcessVersionRepository, this.dmnDecisionService, this.processInstanceTimerRepository, this.connectorRegistry);

        // 4. Controllers initialization
        this.bpmnProcessController = new BpmnProcessController(this.bpmnProcessService);
        this.dmnDecisionController = new DmnDecisionController(this.dmnDecisionService);
        this.userController = new UserController(this.userService);
        this.authController = new AuthController(this.authService);
        this.processInstanceController = new ProcessInstanceController(this.processInstanceService);
        this.taskController = new TaskController(this.taskService);
    }

    public BpmnProcessController getBpmnProcessController() {
        return bpmnProcessController;
    }

    public DmnDecisionController getDmnDecisionController() {
        return dmnDecisionController;
    }

    public UserController getUserController() {
        return userController;
    }

    public AuthController getAuthController() {
        return authController;
    }

    public ProcessInstanceController getProcessInstanceController() {
        return processInstanceController;
    }

    public TaskController getTaskController() {
        return taskController;
    }

    public ConnectorRegistry getConnectorRegistry() {
        return connectorRegistry;
    }

    public BpmnProcessService getBpmnProcessService() {
        return bpmnProcessService;
    }

    public DmnDecisionService getDmnDecisionService() {
        return dmnDecisionService;
    }

    public UserService getUserService() {
        return userService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public ProcessInstanceService getProcessInstanceService() {
        return processInstanceService;
    }

    public TaskService getTaskService() {
        return taskService;
    }

    public BpmnProcessRepository getBpmnProcessRepository() {
        return bpmnProcessRepository;
    }

    public BpmnProcessVersionRepository getBpmnProcessVersionRepository() {
        return bpmnProcessVersionRepository;
    }

    public DmnDecisionRepository getDmnDecisionRepository() {
        return dmnDecisionRepository;
    }

    public DmnDecisionVersionRepository getDmnDecisionVersionRepository() {
        return dmnDecisionVersionRepository;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public RefreshTokenRepository getRefreshTokenRepository() {
        return refreshTokenRepository;
    }

    public ProcessInstanceRepository getProcessInstanceRepository() {
        return processInstanceRepository;
    }

    public TaskRepository getTaskRepository() {
        return taskRepository;
    }

    public ProcessInstanceTimerRepository getProcessInstanceTimerRepository() {
        return processInstanceTimerRepository;
    }

    public BpmnProcessStartTimerRepository getBpmnProcessStartTimerRepository() {
        return bpmnProcessStartTimerRepository;
    }
}
