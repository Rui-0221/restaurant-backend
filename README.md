# 在线餐饮管理平台 · 后端

面向餐厅堂食场景的个人全栈项目。本仓库提供 Java 后端，配套 Vue 顾客端与员工后台，覆盖扫码点餐、同桌加菜、后厨通知、订单流转和结账，并提供需要顾客确认的 AI 点餐推荐。

- **后端**：Java 17、Spring Boot、MyBatis、MySQL、Redis。
- **前端**：[restaurant-frontend](https://github.com/Rui-0221/restaurant-frontend)，包含 Vue 3 + Vant 顾客端、Vue 3 + Element Plus 员工端。
- **AI 接入**：通过 Spring RestClient 调用 DeepSeek，结合本地推荐规则与服务端校验。

本项目用于学习和功能演示。下文说明当前实现、运行方式与已知限制；测试代码的存在不代表所有环境下已经验证通过。

## 功能与业务流程

| 使用者 | 主要功能 |
| --- | --- |
| 顾客 | 注册登录、浏览在售菜品、扫码下单、同桌加菜、查看个人历史订单、AI 推荐与确认 |
| 服务员 | 代顾客点餐、处理上菜与用餐状态、结账 |
| 后厨 | 接收订单通知、开始制作 |
| 管理员 | 员工、分类、菜品、桌台管理，查看营业额，维护菜品 AI 资料 |

```text
顾客扫码入座 → 浏览菜单 → 手动选菜 / AI 推荐后确认
                              ↓
                       创建订单 / 同桌加菜
                              ↓
                      事务提交后通知后厨
                              ↓
             待制作 → 制作中 → 已上菜 → 用餐中 → 已结账
                                                      ↓
                                                 释放桌台
```

订单状态编码为：0 已取消、1 待制作、2 制作中、3 已上菜、4 用餐中、5 已结账。状态 1～4 属于活跃订单；同一桌台的后续下单会合并到现有活跃订单。

## 技术与结构

| 技术 | 本项目中的用途 |
| --- | --- |
| Java 17 / Spring Boot 3.2.5 | Web 接口、依赖注入、配置与事务 |
| MyBatis Spring Boot Starter 3.0.3 | Mapper 接口与注解 SQL；这里的版本是 Starter 版本 |
| MySQL 8.0.16+ | 业务数据、事务、行锁、唯一索引与 CHECK 约束 |
| Redis | 在售菜单缓存、AI 会话、待确认方案与请求限流 |
| Spring WebSocket | 向后厨连接推送新订单、加菜等通知 |
| JJWT / Spring Security Crypto | JWT 签发与校验、BCrypt 密码哈希 |
| Jakarta Validation / Knife4j | 请求参数校验、交互式 API 文档 |
| RestClient / Jackson | DeepSeek HTTP 调用与结构化响应解析 |
| JUnit 5 / Mockito / Spring Test | 单元测试、模拟 HTTP 测试、数据库与并发集成测试 |

依赖版本以 [pom.xml](pom.xml) 为准。当前认证使用自定义 MVC 拦截器；引入的 Spring Security Crypto 用于密码处理。

项目采用单体应用与技术分层，AI 协议、模型适配器和会话实现放在独立的 `ai` 包中：

```text
src/main/java/org/example/restaurant/
├── controller/     HTTP 入口、请求校验、响应组装
├── service/        业务接口
│   └── impl/       订单、菜品、用户、AI 点餐等业务实现
├── mapper/         MyBatis 接口与 SQL
├── entity/         数据库实体及查询结果对象
├── dto/            请求与响应对象
├── ai/             AI 协议、候选菜品选择、DeepSeek 适配器
│   └── state/      Redis 会话、方案与并发轮次管理
├── interceptor/    员工与顾客 JWT 校验
├── websocket/      后厨通知处理
├── common/         Result、异常处理、JWT、请求用户上下文
└── config/         Web、Redis、WebSocket、AI 等配置

src/main/resources/db/
├── init.sql        重建演示数据库
└── migration/      手工执行的增量迁移

src/test/           单元测试与集成测试
```

典型调用链：

```text
HTTP → JWT 拦截器 → Controller → Service → Mapper → MySQL
                                  │
                                  ├── Redis
                                  └── AI 推荐 → DeepSeek（需要时）
```

## 本地运行

### 1. 准备环境

- JDK 17。
- MySQL 8.0.16 或更高版本，以执行项目中的 CHECK 约束。
- Redis 服务。
- Maven 可使用仓库自带的 Wrapper；首次使用需要下载 Maven 与依赖。

以下命令在本仓库根目录运行，Windows 示例使用 PowerShell。

### 2. 初始化演示数据

**[init.sql](src/main/resources/db/init.sql) 会先删除已有业务表，再重新建表并写入演示数据。它不是升级脚本，只能对可丢弃的本地演示数据库执行。脚本中的目标库固定为 `restaurant_management`。**

确认该实例中没有需要保留的同名库数据后，进入 MySQL 客户端：

```powershell
mysql -u root -p
```

在 MySQL 客户端执行，路径替换为实际仓库路径：

```sql
SOURCE C:/your/path/restaurant-backend/src/main/resources/db/init.sql;
```

已有数据的数据库应先检查当前表结构，再选择尚未应用的 [增量迁移](src/main/resources/db/migration)。订单数量约束和活跃订单唯一索引的迁移包含检查 SQL，需先处理检查结果。完整初始化已包含这些结构，不要重复执行对应的 ALTER TABLE。

项目没有接入自动数据库迁移工具，启动应用不会自动完成这些迁移。

### 3. 填写本地配置

创建 `src/main/resources/application-local.yml`，按实际环境填写：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/restaurant_management?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8
    username: root
    password: "填写本机 MySQL 密码"
  data:
    redis:
      host: localhost
      port: 6379
      password: ""

jwt:
  secret: "替换为自己生成的随机密钥"

restaurant:
  ai:
    enabled: false
```

`application-local.yml` 已被 Git 忽略。JWT 密钥按 UTF-8 编码至少需要 32 字节，不可使用公共默认值；缺失或不合格时启动会失败，校验代码见 [JwtUtil](src/main/java/org/example/restaurant/common/JwtUtil.java)。

也可以使用 [application.yml](src/main/resources/application.yml) 中的环境变量，例如 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`JWT_SECRET`。已在本地配置中写死的属性，应同步修改，避免配置来源不一致。

### 4. 启动后端与前端

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS 使用 `./mvnw spring-boot:run`。应用默认启用 `local` Profile，后端默认端口为 8080。

- Knife4j：<http://localhost:8080/doc.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 公开菜单接口：<http://localhost:8080/dishes/on-sale>

配套前端的安装与启动见 [前端 README](https://github.com/Rui-0221/restaurant-frontend#readme)。顾客端默认端口 5173，员工端 5174；开发代理将 `/api` 转发到后端并去掉该前缀。因此直连后端时请求 `/orders/scan-order`，经过前端代理时请求 `/api/orders/scan-order`。

### 5. 走通演示流程

初始化脚本提供员工账号 `admin`、`waiter`、`chef`，演示密码均为 `123456`。顾客账号通过顾客端注册。

1. 管理员登录员工端，选择桌台并生成二维码。
2. 顾客扫码注册、登录，选择菜品并下单。
3. 后厨登录 `chef`，查看新订单并开始制作。
4. 顾客在同一桌台再次提交菜品，观察是否追加到原订单。
5. 服务员依次执行上菜、用餐中、结账，查看桌台释放和营业额变化。
6. 顾客发送“推荐一下”，预览 AI 点餐方案，再点击确认；管理员可以修改 AI 菜品资料后重新推荐。

手机扫码时需使用手机能够访问的电脑局域网地址；二维码中的 `localhost` 指向手机自身。

## 关键接口

请求体采用 JSON，需要认证的请求携带 `Authorization: Bearer <token>`。员工与顾客使用不同类型的 JWT。

| 方法与路径 | 用途 |
| --- | --- |
| `POST /employees/login` | 员工登录 |
| `POST /users/register`、`POST /users/login` | 顾客注册、登录 |
| `GET /categories`、`GET /dishes/on-sale` | 公开分类与在售菜单 |
| `POST /orders/scan-order` | 顾客下单或员工代点，同桌自动加菜 |
| `GET /orders/table/{tableId}/active` | 查询桌台活跃订单 |
| `GET /orders/user/history` | 查询当前顾客的历史订单 |
| `GET /orders`、`GET /orders/{id}` | 员工查询订单列表与详情 |
| `PUT /orders/{id}/status?status=2` | 更新订单状态，服务层校验角色和流转规则 |
| `GET /orders/statistics/today` | 管理员查询当日营业额 |
| `POST /users/ai-order/chat` | 顾客获取推荐或补充信息提示 |
| `POST /users/ai-order/confirm` | 顾客确认有效方案 |
| `GET /admin/dish-ai-profiles` | 管理员查询 AI 菜品资料 |
| `GET /admin/dish-ai-profiles/{dishId}`、`PUT /admin/dish-ai-profiles/{dishId}` | 管理员查询、维护单个菜品资料 |

完整字段与管理接口请查看运行后的 API 文档。扫码下单和桌台活跃订单查询接受顾客或员工 Token；AI 点餐接口只接受顾客身份。

响应使用 [Result<T>](src/main/java/org/example/restaurant/common/Result.java)：`code=1` 表示业务成功，`code=0` 表示业务失败。客户端还需要处理 HTTP 状态码，例如 DTO 校验失败返回 400、未通过认证返回 401，不能仅以 HTTP 200 判断业务成功。

### 手动下单

`POST /orders/scan-order` 请求示例，ID 需替换为当前数据库中的有效值：

```json
{
  "tableId": 1,
  "items": [
    { "dishId": 1, "amount": 2 },
    { "dishId": 2, "amount": 1 }
  ]
}
```

顾客身份从 JWT 取得。前端不提交菜品价格与总价；后端查询数据库价格、校验在售状态，再计算订单金额。每次请求最多 50 个明细项，每项数量为 1～99。员工代点示例同样不需要传 `userId`。

### AI 推荐与确认

`POST /users/ai-order/chat` 首轮请求：

```json
{
  "tableId": 1,
  "message": "推荐一下"
}
```

后续对话携带响应中的 `conversationId`。响应中的 `action` 有三种：

| action | 客户端处理 |
| --- | --- |
| `ASK_CLARIFICATION` | 展示提示，继续输入，例如补充整桌点餐人数 |
| `PROPOSAL` | 展示菜品、数量、数据库价格、总价和推荐理由，等待确认 |
| `MANUAL_ORDER` | 展示失败原因并提供手动点餐入口，此时没有可确认方案 |

收到 `PROPOSAL` 后，使用响应中的两个 ID 调用 `POST /users/ai-order/confirm`：

```json
{
  "tableId": 1,
  "conversationId": "从聊天响应复制",
  "proposalId": "从推荐响应复制"
}
```

上面的中文 ID 是说明占位符，发送时必须替换为实际返回值。确认成功的 `data` 包含 `proposalId`、`order` 和 `replayed`；重复成功确认时 `replayed=true`，不会再次加菜。

## 核心实现与取舍

### 订单、金额与并发

入口为 [OrdersService.placeOrder](src/main/java/org/example/restaurant/service/OrdersService.java)，实现见 [OrdersServiceImpl](src/main/java/org/example/restaurant/service/impl/OrdersServiceImpl.java)。

- **事务边界**：首次下单中的桌台占用、订单主表与明细写入在同一订单事务中执行，避免只占桌却没有订单。
- **金额来源**：使用数据库中的菜品价格，以 `BigDecimal` 计算；明细保存成交单价，避免菜品调价后改变已有明细的金额。
- **首次占桌**：通过桌台状态与版本号进行条件更新，根据受影响行数判断竞争结果。条件更新仍涉及数据库锁，不代表完全不会等待。
- **同桌加菜**：通过 `SELECT ... FOR UPDATE` 锁定已有订单，再检查状态、累计金额并插入明细，避免并发覆盖总额。
- **数据库约束**：`orders.active_table_id` 生成列与唯一索引限制一桌只有一个活跃订单；`order_detail.amount` 的 CHECK 约束限制数量。
- **状态变更**：校验角色与允许的流转，通过带旧状态的条件更新检测冲突，并记录状态日志；结账或取消时检查并释放桌台。

当日营业额按结账状态日志的日期统计，相关 SQL 见 [OrdersMapper](src/main/java/org/example/restaurant/mapper/OrdersMapper.java)。这里的“结账”是业务状态操作，未接入第三方支付。

### 菜单缓存与后厨通知

[DishServiceImpl](src/main/java/org/example/restaurant/service/impl/DishServiceImpl.java) 对在售菜单使用 Redis 缓存：普通结果缓存 1 小时，空结果缓存 60 秒；菜品变更后删除缓存。Redis 读写失败时，菜单查询可回到数据库。删除缓存失败可能导致旧菜单保留到过期，当前不提供严格一致性保证。

订单通知在事务提交后发送，通知失败不会撤销已提交订单。[KitchenWebSocketHandler](src/main/java/org/example/restaurant/websocket/KitchenWebSocketHandler.java) 使用内存中的连接集合，通过 `/ws/kitchen?token=<JWT>` 向后厨推送；连接建立后校验员工类型和厨师角色，不合格连接会关闭。当前没有跨实例广播或持久化消息重投机制。

### AI 点餐边界

对外入口为 [AiOrderingService](src/main/java/org/example/restaurant/service/AiOrderingService.java) 的 `chat` 与 `confirm`，确认复用已有下单流程。

```text
chat
 ├── 明确菜名、数量且无额外约束 → 本地匹配
 ├── 不含额外偏好的“推荐一下” → 本地招牌排序
 └── 口味、菜系等需求 → DeepSeek 结构化选择
             ↓
       服务端校验并生成预览 → Redis 保存方案
                                      ↓
confirm → MySQL 提交记录去重 → 领取有效方案 → OrdersService.placeOrder
```

- 候选集仅包含在售且 AI 资料标记为 `VERIFIED` 的菜品；缺少 AI 资料不影响手动点餐。
- 模型选择候选菜品 ID、数量并给出理由。后端校验响应结构、ID、数量、合并后的重复项及已识别的忌口、菜系条件，价格由数据库提供。
- 过敏原资料为空或 `UNKNOWN` 表示未知，不等同于没有过敏原；有相关忌口时会拒绝缺少资料的候选。规则依赖资料与文本识别，不能覆盖所有自然语言表达。
- 模型调用发生在推荐阶段，订单事务只在确认阶段开启。远程调用失败返回手动点餐提示，不会因失败自动生成招牌菜方案。
- Redis 会话绑定顾客与桌台，默认 30 分钟滑动过期，保留最近 20 轮；方案默认 10 分钟过期。新一轮请求开始时旧方案即失效，晚到的旧轮次结果不会覆盖新结果。
- 默认每位顾客每分钟最多 10 次聊天请求、单条最多 500 字。状态与限流配置见 [AiOrderingStateProperties](src/main/java/org/example/restaurant/config/AiOrderingStateProperties.java)。
- `ai_order_submission.proposal_id` 唯一约束用于确认去重，提交记录与订单写入处于同一 MySQL 事务。成功重试返回关联订单的**当前详情**，没有保存首次响应快照。
- Redis 领取方案不随 MySQL 事务回滚；若领取后下单失败，原方案不能继续确认，需要重新推荐。

实现入口：[推荐规则](src/main/java/org/example/restaurant/service/impl/AiOrderingServiceImpl.java)、[DeepSeek 适配器](src/main/java/org/example/restaurant/ai/DeepSeekDishSelectionAdapter.java)、[会话与方案](src/main/java/org/example/restaurant/ai/state/RedisAiOrderConversationManager.java)、[确认与去重](src/main/java/org/example/restaurant/service/impl/AiOrderConfirmationServiceImpl.java)。

### 可选：启用 DeepSeek

未配置 API Key 时可以运行普通点餐和本地推荐规则；需要远程模型的请求会返回 `MANUAL_ORDER`。MySQL、Redis 和 JWT 等基础配置仍需正确。

在本地配置中加入，或使用对应环境变量：

```yaml
restaurant:
  ai:
    enabled: true
    base-url: https://api.deepseek.com
    api-key: ${DEEPSEEK_API_KEY:}
    model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
    connect-timeout: 3s
    read-timeout: 15s
    max-tokens: 1024
```

环境变量还包括 `DEEPSEEK_ENABLED`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_CONNECT_TIMEOUT`、`DEEPSEEK_READ_TIMEOUT` 和 `DEEPSEEK_MAX_TOKENS`。模型名是当前仓库配置默认值，实际可用性取决于服务提供方。

当前使用 RestClient + Jackson，未引入 Spring AI，也没有 RAG、向量数据库或自主工具执行链路。

## 测试与验证

### 不连接真实数据库或模型的测试

可以先运行一组不需要 MySQL、Redis 或真实 DeepSeek 的测试：

```powershell
.\mvnw.cmd "-Dtest=ScanOrderDTOValidationTest,OrdersServiceImplUnitTest,DeepSeekDishSelectionAdapterTest,AiClientConfigTest" test
```

它们分别检查 DTO 约束、使用 Mock Mapper 的订单规则、模拟 HTTP 响应的模型适配器、AI 客户端配置。首次执行仍需要下载 Maven 依赖。

### 数据库、Redis 与并发集成测试

[测试目录](src/test/java/org/example/restaurant) 包含订单创建与加菜、状态流转、活跃订单唯一约束、AI 多轮会话、过期替换、身份校验和重复确认等测试。

**部分集成测试使用 `test` Profile，部分使用 `local` Profile；`application-test.yml` 还可能导入本地配置。Profile 名称不代表数据库已经隔离。** 运行前必须确认实际数据源指向可丢弃的测试库，Redis 也使用独立测试实例或配置；测试可能写入、修改和清理数据。

完成隔离配置后，可排除真实模型测试运行其余测试：

```powershell
.\mvnw.cmd "-Dtest=*,!DeepSeekLiveDishSelectionTest" test
```

测试报告位于 `target/surefire-reports/`。运行结果以当次输出与报告为准，本 README 不维护固定测试数量或未经验证的“全量通过”声明。

### 真实模型冒烟测试

[DeepSeekLiveDishSelectionTest](src/test/java/org/example/restaurant/ai/DeepSeekLiveDishSelectionTest.java) 在环境变量 `DEEPSEEK_API_KEY` 为非空且不是 `NOT_SET` 时启用；该条件不会预先检查 Key 是否有效。它会访问真实模型并可能产生费用。

```powershell
.\mvnw.cmd "-Dtest=DeepSeekLiveDishSelectionTest" test
```

直接执行未排除该类的全量 `test`，也可能在已设置 Key 时触发真实调用；`live-ai` 标签本身不会自动排除它。

## 当前限制与后续改进

- **预算约束**：当前会根据数据库价格计算推荐总额，但尚未实现独立解析预算并强制拒绝超预算方案的完整校验，不能承诺推荐始终满足预算。
- **确认时的数据变化**：下单会重新读取菜品价格并检查在售状态，但没有锁定预览价格，也未在确认阶段重新校验整套 AI 资料和忌口条件。
- **错误分类**：模型超时、限流、非法响应等当前主要统一为 `AI_UNAVAILABLE`；会话层有独立错误码，尚未细分全部模型错误。
- **部署范围**：当前后厨通知基于单进程连接集合；WebSocket 配置允许所有 Origin，API 文档默认开放，尚需按实际部署环境收紧配置。
- **推荐评估**：已有规则与失败场景测试，尚无基于真实顾客数据的推荐效果评估或生产容量指标。

进一步完善时，可优先补齐预算与确认校验、统一测试环境隔离，再按部署需求考虑通知可靠性和多实例支持。

## 阅读代码的建议顺序

1. [ScanOrderDTO](src/main/java/org/example/restaurant/dto/ScanOrderDTO.java) → [OrdersController](src/main/java/org/example/restaurant/controller/OrdersController.java)：请求怎样进入后端。
2. [OrdersServiceImpl](src/main/java/org/example/restaurant/service/impl/OrdersServiceImpl.java) → [OrdersMapper](src/main/java/org/example/restaurant/mapper/OrdersMapper.java)、[OrderDetailMapper](src/main/java/org/example/restaurant/mapper/OrderDetailMapper.java)：首次下单、加菜与金额计算。
3. [init.sql](src/main/resources/db/init.sql) → [订单测试](src/test/java/org/example/restaurant/service/OrdersServiceTest.java)：表结构与业务约束如何对应。
4. [WebConfig](src/main/java/org/example/restaurant/config/WebConfig.java) → [UserJwtInterceptor](src/main/java/org/example/restaurant/interceptor/UserJwtInterceptor.java)：登录身份如何进入请求上下文。
5. [AiOrderingServiceImpl](src/main/java/org/example/restaurant/service/impl/AiOrderingServiceImpl.java) → [AiOrderConfirmationServiceImpl](src/main/java/org/example/restaurant/service/impl/AiOrderConfirmationServiceImpl.java)：推荐、状态管理与真正下单如何衔接。
