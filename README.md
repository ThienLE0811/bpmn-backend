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

---

## Deploy online miễn phí (Render + Neon)

Kiến trúc: **Render** host app (Docker) + **Neon** host Postgres. Cả hai đều có free tier vĩnh viễn, không cần thẻ.

### Bước 1: Tạo database trên Neon

1. Tạo tài khoản tại [neon.com](https://neon.com), tạo project mới (chọn region gần VN, ví dụ Singapore).
2. Vào **Dashboard → Connection Details**, copy chuỗi kết nối dạng:
   ```
   postgresql://<user>:<password>@<host>/<database>?sslmode=require
   ```
3. Giữ lại chuỗi này, dùng làm `DATABASE_URL` ở bước 3.

### Bước 2: Push code lên GitHub

Repo đã có sẵn `Dockerfile` và `render.yaml` ở thư mục gốc.

### Bước 3: Tạo Web Service trên Render

1. Tạo tài khoản tại [render.com](https://render.com), đăng nhập bằng GitHub.
2. **New → Blueprint**, chọn repo này. Render sẽ tự đọc `render.yaml`.
3. Khi được hỏi giá trị `DATABASE_URL`, dán chuỗi kết nối Neon ở Bước 1.
4. Chọn plan **Free** (đã set sẵn trong `render.yaml`) và deploy.
5. Sau khi build xong, Render cấp một URL dạng `https://bpmn-backend-xxxx.onrender.com`.

Gọi thử: `curl https://bpmn-backend-xxxx.onrender.com/health` → `{"status":"OK"}`.

### Lưu ý free tier

- **Render**: app tự ngủ sau 15 phút không có request, request đầu tiên sau đó mất ~30-60s để "thức dậy". Có 750 giờ chạy/tháng (dư dùng vì lúc ngủ không tính giờ).
- **Neon**: compute tự về 0 sau 5 phút idle, có 100 compute-hours/tháng. Cấu hình `db.pool.minimum-idle=0` (`application.properties`) đã được chỉnh để không giữ connection treo, giúp Neon ngủ được đúng lúc.
- Không cấu hình nào tính phí khi vượt quota — chỉ bị tạm ngưng đến kỳ sau, không mất data.
- Muốn đổi cấu hình DB pool khi chạy trên Render mà không sửa code: set thêm env var, ví dụ `DB_POOL_MAXIMUM_POOL_SIZE=5` (biến môi trường luôn được ưu tiên hơn `application.properties`, xem `AppConfig.getProperty`).

### Build & chạy Docker image ở local (tuỳ chọn, để test trước khi deploy)

```bash
docker build -t bpmn-backend .
docker run -p 8080:8080 -e DATABASE_URL="postgresql://user:pass@host/db?sslmode=require" bpmn-backend
```
