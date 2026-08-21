# BPMN Backend (Pure Java + JDK HttpServer)

Dự án Java thuần áp dụng kiến trúc phân lớp (Layered Architecture) và tích hợp sẵn **JDK HttpServer** (sử dụng Virtual Threads của Java 21) để phục vụ REST API cho xử lý luồng BPMN.

---

## Cấu trúc thư mục

```
D:/works/bpmn/code/backend/
├── pom.xml                                  # Quản lý thư viện và cấu hình Maven (Java 21)
├── .gitignore                               # Quy tắc bỏ qua file của Git
├── README.md                                # Hướng dẫn dự án
├── src
│   ├── main
│   │   ├── java/com/example/bpmn
│   │   │   ├── Main.java                    # Entry point khởi động HTTP Server
│   │   │   ├── config/                      # Đọc file cấu hình (AppConfig)
│   │   │   ├── controller/                  # Nơi viết API & định tuyến HTTP (WorkflowController)
│   │   │   ├── service/                     # Xử lý nghiệp vụ (WorkflowService & WorkflowServiceImpl)
│   │   │   ├── repository/                  # Quản lý dữ liệu (WorkflowRepository & InMemoryWorkflowRepository)
│   │   │   ├── model/                       # Domain Entity (Workflow)
│   │   │   ├── dto/                         # DTO Request/Response (WorkflowRequest, WorkflowResponse)
│   │   │   ├── exception/                   # Xử lý lỗi (AppException)
│   │   │   └── util/                        # Tiện ích JSON (JsonUtil)
│   │   └── resources
│   │       ├── application.properties       # Cấu hình server port, host
│   │       └── logback.xml                  # Cấu hình log SLF4J / Logback
│   └── test
│       └── java/com/example/bpmn
│           └── WorkflowServiceTest.java     # Unit test (JUnit 5)
```

---

## Danh sách API (`WorkflowController.java`)

Server lắng nghe tại cổng mặc định: `http://localhost:8080`

| Phương thức | Endpoint | Mô tả | Body mẫu (JSON) |
|---|---|---|---|
| `GET` | `/api/workflows` | Lấy danh sách tất cả workflows | Không |
| `GET` | `/api/workflows/{id}` | Lấy chi tiết workflow theo ID | Không |
| `POST` | `/api/workflows` | Tạo mới một workflow | `{"name": "Order Process", "description": "Xử lý đơn hàng"}` |
| `DELETE` | `/api/workflows/{id}` | Xóa workflow theo ID | Không |

---

## Ví dụ gọi API (cURL / Postman)

### 1. Tạo mới Workflow (POST):
```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"Approval_Process\", \"description\": \"Duyet don hang tu dong\"}"
```

### 2. Lấy danh sách Workflows (GET):
```bash
curl -X GET http://localhost:8080/api/workflows
```

### 3. Lấy chi tiết Workflow theo ID (GET):
```bash
curl -X GET http://localhost:8080/api/workflows/<ID_CUA_WORKFLOW>
```

### 4. Xóa Workflow (DELETE):
```bash
curl -X DELETE http://localhost:8080/api/workflows/<ID_CUA_WORKFLOW>
```

---

## Cách chạy dự án

1. Mở dự án trong **IntelliJ IDEA**.
2. Chạy hàm `main()` tại [`Main.java`](file:///D:/works/bpmn/code/backend/src/main/java/com/example/bpmn/Main.java).
3. Server sẽ mở tại cổng `8080` và sẵn sàng nhận request từ Postman hoặc trình duyệt!
