# BPMN Backend (Pure Java + JDK HttpServer)

Backend Java thuần (không dùng Spring), kiến trúc phân lớp `controller → service → repository → model`, chạy trên **JDK `HttpServer`** với Virtual Threads (Java 21). Đây là một **BPMN engine tự viết** (không dùng Camunda/Flowable/Zeebe): parse BPMN 2.0 XML thành graph và tự thực thi luồng (start event, user task, exclusive/parallel/inclusive gateway, service task, business rule task liên kết DMN, end event).

---

## Tech stack

- Java 21, Maven, không Spring — routing tự viết (`BaseController` / `Route` / `RequestContext`) trên `com.sun.net.httpserver.HttpServer`.
- PostgreSQL qua HikariCP, schema quản lý bằng **Flyway** (`src/main/resources/db/migration`).
- Jackson (JSON), JJWT + jBCrypt (auth), commons-jexl3 (đánh giá điều kiện gateway/DMN), SLF4J + Logback, JUnit 5.
- Deploy: Docker + `render.yaml` (Render + Neon Postgres).

---

## Cấu trúc thư mục

```
backend/
├── pom.xml
├── Dockerfile, render.yaml
├── src
│   ├── main
│   │   ├── java/com/example/bpmn
│   │   │   ├── Main.java              # Entry point: init DB (Flyway) rồi start HttpServer
│   │   │   ├── config/                # AppConfig, DatabaseConfig (Flyway), RouteConfig
│   │   │   ├── container/             # AppContainer - DI thủ công (repo → service → controller)
│   │   │   ├── controller/            # REST endpoints (Auth, BpmnProcess, DmnDecision, ProcessInstance, Task, User)
│   │   │   ├── service/ + service/impl/
│   │   │   ├── repository/ + repository/impl/   # JDBC thuần, không ORM
│   │   │   ├── model/                 # Domain entity: User, BpmnProcess(+Version), DmnDecision(+Version),
│   │   │   │                          #   ProcessInstance, Task, RefreshToken
│   │   │   ├── dto/                   # Request/Response DTO cho từng API
│   │   │   ├── mapper/                # model → DTO
│   │   │   ├── engine/                # BpmnGraphParser + ProcessEngine (BPMN engine thật)
│   │   │   ├── dmn/                   # DmnTableParser + DmnEvaluator (đánh giá DMN decision table)
│   │   │   ├── exception/             # AppException (message + HTTP status code)
│   │   │   ├── http/                  # BaseController/Route/RequestContext (mini routing framework)
│   │   │   └── util/                  # JwtUtil, PasswordUtil, RefreshTokenUtil, JsonUtil
│   │   └── resources
│   │       ├── application.properties
│   │       ├── logback.xml
│   │       └── db/migration/          # Flyway: V1__baseline_schema.sql, V2__..., ...
│   └── test/java/com/example/bpmn     # JUnit 5, không Mockito - repo fake bằng anonymous class + Map
```

---

## Database & migrations (Flyway)

Schema được quản lý bằng **Flyway**, không còn `CREATE TABLE IF NOT EXISTS` viết tay trong code. `DatabaseConfig.initDatabase()` (gọi từ `Main.java` khi khởi động) chạy `Flyway.migrate()` với `baselineOnMigrate(true)` — nghĩa là:

- Database mới hoàn toàn: chạy toàn bộ migration từ `V1` lên.
- Database đã có sẵn (dev/prod đang chạy trước khi đưa Flyway vào): tự baseline, không cần thao tác gì thêm.

Khi cần đổi schema: **thêm file migration mới** `V{n}__mo_ta.sql` vào `src/main/resources/db/migration`, không sửa file `V1`/`V2` đã chạy (Flyway kiểm checksum, sửa file cũ sẽ làm migrate thất bại ở môi trường đã áp dụng nó).

---

## Cách chạy dự án (local)

1. Có PostgreSQL chạy ở `localhost:5432`, tạo database (mặc định cấu hình trong `application.properties` là db tên `los`, user/pass tuỳ chỉnh theo máy bạn).
2. Chạy `Main.java` (hoặc `mvn compile exec:java` nếu có exec plugin, thường dùng IDE). Ứng dụng tự áp Flyway migration khi khởi động, không cần chạy SQL tay.
3. Server mặc định ở `http://localhost:8080`. `GET /health` → `{"status":"OK"}`.

Chạy test: `mvn test`.

---

## Danh sách API

Tất cả route yêu cầu JWT (`Authorization: Bearer <token>`) qua `authInterceptor`/`RequestContext`, trừ 3 route auth dưới đây (`postPublic`).

### Auth (`/api/auth`)

| Method | Endpoint            | Mô tả                                             |
| ------ | ------------------- | ------------------------------------------------- |
| POST   | `/api/auth/login`   | Đăng nhập, trả access token (JWT) + refresh token |
| POST   | `/api/auth/refresh` | Cấp access token mới từ refresh token còn hạn     |
| POST   | `/api/auth/logout`  | Revoke refresh token                              |

### BPMN Process (`/api/bpmn-processes`)

| Method | Endpoint                                    | Mô tả                                                                |
| ------ | ------------------------------------------- | -------------------------------------------------------------------- |
| GET    | `/api/bpmn-processes`                       | List (phân trang)                                                    |
| POST   | `/api/bpmn-processes`                       | Tạo process definition mới (kèm BPMN XML)                            |
| GET    | `/api/bpmn-processes/key/:key`              | Lấy theo `processKey`                                                |
| GET    | `/api/bpmn-processes/:id`                   | Lấy theo id                                                          |
| PUT    | `/api/bpmn-processes/:id`                   | Cập nhật (tự tăng version, lưu snapshot vào `bpmn_process_versions`) |
| GET    | `/api/bpmn-processes/:id/versions`          | Lịch sử version                                                      |
| GET    | `/api/bpmn-processes/:id/versions/:version` | Snapshot XML của một version cụ thể                                  |
| DELETE | `/api/bpmn-processes/:id`                   | Xoá                                                                  |

### DMN Decision (`/api/dmn-decisions`)

| Method | Endpoint                      | Mô tả                            |
| ------ | ----------------------------- | -------------------------------- |
| GET    | `/api/dmn-decisions`          | List (phân trang)                |
| POST   | `/api/dmn-decisions`          | Tạo decision table mới (DMN XML) |
| GET    | `/api/dmn-decisions/key/:key` | Lấy theo `decisionKey`           |
| GET    | `/api/dmn-decisions/:id`      | Lấy theo id                      |
| PUT    | `/api/dmn-decisions/:id`      | Cập nhật (tự tăng version)       |
| DELETE | `/api/dmn-decisions/:id`      | Xoá                              |

### Process Instance / "Case" (`/api/process-instances`)

| Method | Endpoint                     | Mô tả                                                                                                                                          |
| ------ | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/process-instances`     | List (phân trang)                                                                                                                              |
| POST   | `/api/process-instances`     | Start một case mới từ `processId` (+ biến khởi tạo), engine tự advance tới user task đầu tiên (hoặc COMPLETED ngay nếu không có user task nào) |
| GET    | `/api/process-instances/:id` | Lấy chi tiết case                                                                                                                              |

### Task (`/api/tasks`)

| Method | Endpoint                   | Mô tả                                                               |
| ------ | -------------------------- | ------------------------------------------------------------------- |
| GET    | `/api/tasks?status=&mine=` | List, filter theo status và/hoặc chỉ task của người gọi             |
| GET    | `/api/tasks/:id`           | Chi tiết task                                                       |
| POST   | `/api/tasks/:id/claim`     | Nhận task (PENDING → CLAIMED)                                       |
| POST   | `/api/tasks/:id/complete`  | Hoàn thành task (kèm biến), engine advance tiếp tới task/gateway kế |

### User (`/api/users`, ADMIN cho phần đổi role)

| Method | Endpoint         | Mô tả             |
| ------ | ---------------- | ----------------- |
| GET    | `/api/users`     | List (phân trang) |
| POST   | `/api/users`     | Tạo user          |
| GET    | `/api/users/:id` | Chi tiết          |
| PUT    | `/api/users/:id` | Cập nhật          |
| DELETE | `/api/users/:id` | Xoá               |

---

## BPMN engine — phạm vi hỗ trợ hiện tại

Hỗ trợ: start event, end event, user task, exclusive gateway (điều kiện JEXL `${...}` + default flow), **parallel gateway** (fork/join thật, có token-walk + `pendingJoinArrivals`), **inclusive gateway** (OR-split/OR-join, xấp xỉ bằng reachability - xem code/comment trong `ProcessEngine` để biết giới hạn), service task & business rule task (chạy tự động, không tạo Task cho người dùng; business rule task có thể bind `camunda:decisionRef`/`camunda:resultVariable` để gọi DMN thật qua `DmnEvaluator`).

**Connector cho service task**: một `serviceTask` có thể bind vào connector bằng extension element kiểu Camunda 7:

```xml
<serviceTask id="call1" name="Check credit">
  <extensionElements>
    <camunda:connector>
      <camunda:connectorId>http</camunda:connectorId>
      <camunda:inputOutput>
        <camunda:inputParameter name="url">https://api.example.com/credit</camunda:inputParameter>
        <camunda:inputParameter name="method">POST</camunda:inputParameter>
        <camunda:inputParameter name="customerId">${customer.id}</camunda:inputParameter>
        <camunda:outputParameter name="creditScore">${json.score}</camunda:outputParameter>
      </camunda:inputOutput>
    </camunda:connector>
  </extensionElements>
</serviceTask>
```

Giá trị bọc `${...}` là biểu thức JEXL, còn lại là chuỗi literal. Input resolve theo process variables, output resolve theo **map kết quả của connector** rồi ghi vào biến mang tên đó. `ProcessEngine` không tự gọi I/O: nó nhận `ConnectorInvoker` (cùng kiểu inject như `DmnDecisionEvaluator`), implement thật là `ConnectorRegistry` trong `com.example.bpmn.connector`. Connector đăng ký bằng code, không lưu DB — hiện mới có `http` (input: `url`, `method`, `body`, `contentType`, `failOnError`, `header.*`; output: `statusCode`, `body`, `json`), timeout cấu hình qua `connector.http.*-timeout-seconds`.

Connector chạy **đồng bộ** ngay trong request start/complete. Khi nó lỗi, instance không bị mất: chuyển sang `FAILED` kèm `incident_node_id`/`incident_message` để xem được lỗi ở node nào. **Chưa có**: endpoint retry, secret store (đừng để API key thẳng trong BPMN XML — XML được version và trả về qua API), allowlist URL (một model bất kỳ hiện có thể gọi tới địa chỉ nội bộ), và connector không idempotent khi chạy lại.

**Chưa hỗ trợ**: subprocess/call activity, script task, message/signal event. Không có process nào hiện có trong DB dev dùng các phần tử này, nhưng nếu import BPMN có chúng, engine sẽ lỗi khi gặp node lạ.

**Giới hạn DMN**: chỉ đọc `<decisionTable>` đầu tiên trong file DMN, chỉ hỗ trợ hit policy `UNIQUE`/`FIRST`, input entry dạng so sánh đơn giản (`=`, `<`, `<=`, `>`, `>=`, `-` wildcard) — chưa hỗ trợ FEEL range (`[100..200]`) hay danh sách giá trị (`"A","B"`).

---

## Testing

`mvn test` — JUnit 5, không dùng Mockito. Repository được fake bằng anonymous class backed bởi `Map`/`ConcurrentHashMap` (xem `AuthServiceTest`, `TaskServiceTest`, `ProcessInstanceServiceTest`, `DmnDecisionServiceTest`, `BpmnProcessServiceTest` để theo đúng convention khi viết test mới). Engine có test riêng ở `engine/BpmnGraphParserTest`, `engine/ProcessEngineTest` (fixture BPMN XML dùng chung trong `engine/BpmnFixtures`).

Đã có test cho: Auth, User, Task, ProcessInstance, BpmnProcess, DmnDecision, toàn bộ engine (parser + advance), tầng repository (SQL thật) và tầng HTTP.

### Test tầng HTTP (`http/*Test`)

`RouteTest` test thuần `Route.split`/`match`/`matchesMethod`. `BaseControllerTest` và `RequestContextTest` dựng một `HttpServer` thật trên cổng trống (mount controller giả theo đúng cách `RouteConfig` mount ở production, kể cả virtual-thread executor) rồi bắn request bằng `HttpClient` của JDK — **không cần dependency mới, không cần database**.

Phủ: xác thực Bearer token (thiếu header / không phải Bearer / token hỏng / sai chữ ký / hết hạn đều phải là 401, và token hợp lệ phải set đúng `authUserId`/`authUsername`/`authRole`), 404 vs 405, thứ tự khớp route, map `HttpResult`/`null`/`AppException`/exception lạ ra status code, preflight `OPTIONS` + header CORS, JSON UTF-8, và phía `RequestContext`: decode path/query param, clamp `page`/`size`, body rỗng hoặc JSON sai → 400.

### Test tích hợp tầng repository (`repository/Postgres*RepositoryTest`)

Service test fake repository bằng `Map` nên không chạy một dòng SQL nào. Phần SQL/JDBC viết tay trong `repository/impl` được phủ riêng bởi nhóm test kế thừa `PostgresRepositoryTestBase`, chạy trên PostgreSQL thật:

- Dùng đúng `db.url`/`db.username`/`db.password` của app (override được bằng env `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`), nhưng **thay tên database thành `bpmn_repo_test`** — test `TRUNCATE` mọi bảng trước mỗi case nên tuyệt đối không được trỏ vào database thật.
- Database này được tạo tự động ở lần chạy đầu, schema dựng bằng chính Flyway migration của production → migration nào không còn apply được lên database rỗng sẽ fail ngay tại đây.
- Không có PostgreSQL thì cả nhóm test **bị skip** (JUnit assumption) chứ không fail, nên `mvn test` vẫn chạy được trên máy không có DB — và cũng vì vậy nhóm test này hiện **bị skip trên CI** (workflow chưa có service Postgres).
- `DatabaseConfig.useDataSource(...)` tồn tại chỉ để làm điểm nối cho nhóm test này (repository gọi `DatabaseConfig.getConnection()` trực tiếp, không inject `DataSource`).

---

## CI

GitHub Actions (`.github/workflows/ci.yml`) chạy `mvn test` trên mỗi push/PR vào `main`. Không tự động deploy — deploy vẫn qua Render Blueprint (xem phần dưới).

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

Ứng dụng tự chạy Flyway migration khi khởi động, kể cả trên Neon lần đầu — không cần chạy SQL tay trên production.

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

###
