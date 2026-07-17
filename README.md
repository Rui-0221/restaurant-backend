# 🍽️ 在线餐饮管理平台 — 后端服务

> Spring Boot 3.2 + MyBatis | 扫码点餐 · 后厨协作 · 实时通知 · 收银结账

---

## 📋 项目概述

这是一套面向线下餐厅的**扫码点餐后端系统**，覆盖从顾客入座到结账离店的全链路业务。

### 业务流程

```
顾客入座 → 扫桌上二维码 → 浏览在售菜品（Redis缓存）
         → 提交订单（后端强制重算金额）
         → 桌台自动占用（乐观锁防并发）
         → 后厨实时收到通知（WebSocket推送）+ 打印小票
         → 后厨开始制作(1→2) → 服务员上菜(2→3)
         → 客人用餐中(4) → 中途加菜（再扫码自动追加）
         → 服务员结账(4→5) → 桌台自动释放
```

### 项目定位

- **场景**：线下餐厅扫码点餐、后厨协作、收银结账
- **类型**：简历核心后端项目，面试可深度讲解 15 分钟
- **规模**：68 个 Java 源文件，8 个 Controller，21 个单元测试

---

## 🛠️ 技术栈

| 分类 | 技术 | 版本 | 选型理由 |
|------|------|:--:|------|
| 框架 | Spring Boot | 3.2.5 | 生态成熟，自动配置减少样板代码 |
| ORM | MyBatis | 3.0.3 | 全注解方式，零 XML 配置，SQL 可控 |
| 数据库 | MySQL | 8.0+ | 事务支持（ACID），行锁支持 SELECT FOR UPDATE |
| 缓存 | Redis | 7.0+ | 高性能，支持 TTL 过期，用于菜品缓存 |
| 实时通信 | WebSocket | Spring 内置 | 低延迟推送，频道隔离设计 |
| JWT | JJWT | 0.12.6 | 自包含 Token，含角色声明 |
| API 文档 | Knife4j | 4.5.0 | Swagger 增强版，中文界面 |
| 校验 | Hibernate Validator | 8.0+ | JSR-380 标准，注解式参数校验 |
| 加密 | Spring Security Crypto | 6.2+ | BCrypt 密码哈希 |

---

## 🏗️ 系统架构

### 分层架构

```
┌─────────────────────────────────────────────────────────┐
│                     Controller 层                       │
│  Employee · User · TableInfo · Dish · Orders            │
│  REST API (6个Controller) + WebSocket 端点              │
├─────────────────────────────────────────────────────────┤
│                     Interceptor 层                      │
│  JwtInterceptor (员工认证+角色解析 → /employees, /orders) │
│  UserJwtInterceptor (用户认证 → /users, /orders/scan-order)      │
│  → ThreadLocal (UserContext)                             │
├─────────────────────────────────────────────────────────┤
│                     Service 层                          │
│  核心业务: placeOrder(首次/加菜), updateOrderStatus     │
│           printOrder, listOnSale(缓存)                  │
├─────────────────────────────────────────────────────────┤
│                     Mapper 层                           │
│  MyBatis 全注解: @Select @Insert @Update @Delete       │
│  复杂SQL: CAS乐观锁, 行锁, 批量插入, 聚合查询          │
├─────────────────────────────────────────────────────────┤
│                   基础设施层                             │
│  MySQL · Redis · WebSocket · JWT · BCrypt               │
└─────────────────────────────────────────────────────────┘
```

### 项目目录结构

```
src/main/java/org/example/restaurant/
├── controller/              # REST API (6个)
│   ├── EmployeeController       # 员工管理 + 登录（写操作限管理员）
│   ├── UserController           # 顾客注册/登录/查个人信息
│   ├── TableInfoController      # 桌台CRUD + 乐观锁状态变更
│   ├── DishController           # 菜品CRUD + Redis缓存在售列表
│   ├── CategoryController       # 分类CRUD
│   └── OrdersController         # 订单CRUD + 扫码点餐 + 状态流转 + 营业额统计
├── service/                 # 接口 (6个)
├── service/impl/            # 实现 (6个)
│   ├── OrdersServiceImpl        # ⭐核心：placeOrder + addItemsToOrder + updateOrderStatus
│   ├── TableInfoServiceImpl     # ⭐乐观锁CAS + 状态流转校验
│   ├── DishServiceImpl          # ⭐Redis Cache-Aside + 穿透防护
│   └── ...
├── mapper/                  # MyBatis 注解式数据访问 (8个)
├── entity/                  # 数据库实体 (8个)
├── dto/                     # 请求/响应对象 (7个)
├── interceptor/             # JWT拦截器 (2个)
├── config/                  # 配置类 (4个)
│   ├── WebConfig                # 拦截器注册 + 路径白名单
│   ├── RedisConfig              # StringRedisTemplate 序列化
│   ├── WebSocketConfig          # /ws/kitchen 端点注册
│   └── SwaggerConfig            # Knife4j 文档配置
├── common/                  # 公共组件 (7个)
│   ├── JwtUtil                  # Token 生成/解析（含角色+类型）
│   ├── UserContext              # ThreadLocal 用户上下文
│   ├── Result                   # 统一响应格式
│   ├── ResponseUtil             # 拦截器401 JSON 响应工具
│   ├── BusinessException        # 业务异常
│   ├── PasswordEncoderUtil      # BCrypt 密码加密
│   └── GlobalExceptionHandler   # 全局异常处理
├── websocket/               # WebSocket 处理器
│   └── KitchenWebSocketHandler  # 频道隔离 + 新订单/加菜通知
└── RestaurantApplication    # 启动类
```

---

## 🚀 快速开始

### 环境要求

| 组件 | 版本 | 用途 |
|------|:--:|------|
| JDK | 17+ | 编译运行 |
| MySQL | 8.0+ | 数据存储 |
| Redis | 7.0+ | 菜品缓存 |
| Maven | 3.6+ | 构建 |

### 第一步：创建数据库

```sql
CREATE DATABASE restaurant_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 第二步：执行初始化脚本

运行 `src/main/resources/db/init.sql`，自动完成：
- 员工表添加 `role` 字段
- 订单表添加 `table_id`、移除配送字段
- 创建 `table_info`（桌台信息，含 version 乐观锁字段）
- 创建 `order_status_log`（订单状态审计日志，营业额统计依据）
- 插入 6 张测试桌台（A1~C1）

### 第三步：配置数据库密码

创建 `src/main/resources/application-local.yml`：

```yaml
spring:
  datasource:
    password: 你的MySQL密码
  data:
    redis:
      password: 你的Redis密码  # Redis无密码则删除此行
```

### 第四步：启动

```powershell
cd restaurant-backend
mvn spring-boot:run
```

### 第五步：访问文档

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/doc.html` | Knife4j 接口文档 |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |

---

## 🔌 接口文档

### 通用说明

- **Base URL**: `http://localhost:8080`
- **认证方式**: `Authorization: Bearer <JWT Token>`
- **统一成功响应**: `{"code": 1, "msg": "success", "data": {...}}`
- **统一失败响应**: `{"code": 0, "msg": "错误描述", "data": null}`

---

### 🪑 桌台管理 `⭐核心`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tables` | 查询所有桌台 |
| GET | `/tables/{id}` | 查询单个桌台 |
| POST | `/tables` | 新增桌台 |
| PUT | `/tables` | 修改桌台（名称、容量） |
| DELETE | `/tables/{id}` | 删除桌台 |
| **PUT** | **`/tables/{id}/status?status=1`** | **变更状态（CAS乐观锁）** |

**状态枚举**: `0`空闲 `1`占用

**状态流转规则**:
```
0空闲 → 1占用
1占用 → 0空闲
```

**变更状态请求示例**:
```
PUT /tables/1/status?status=1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

响应:
{
  "code": 1,
  "msg": "success",
  "data": {
    "tableId": 1,
    "status": 1,
    "operatorId": 1
  }
}
```

**并发冲突时响应**:
```json
{
  "code": 0,
  "msg": "桌台状态已被其他操作变更，请刷新后重试",
  "data": null
}
```

---

### 📝 订单管理 `⭐核心`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/orders?page=1&size=20` | 分页查询订单（按创建时间倒序） |
| GET | `/orders/{id}` | 查询单个订单 |
| **POST** | **`/orders/scan-order`** | **🔑 扫码点餐（核心接口）** |
| **GET** | **`/orders/table/{tableId}/active`** | **查询桌台活跃订单** |
| **PUT** | **`/orders/{id}/status?status=2`** | **订单状态流转** |
| **GET** | **`/orders/statistics/today`** | **今日营业额（仅管理员）** |

#### 🔑 扫码点餐 `POST /orders/scan-order`

这是系统最核心的接口，**一个接口智能处理两种场景**：

```
请求到达 → 查该桌台有无活跃订单（状态 IN 1,2,3,4）
        ├── 无活跃订单 → 首次点餐：占桌台 + 建订单 + 打完整小票
        └── 有活跃订单 → 加菜：追加明细 + 重算总价 + 打加菜单
```

**请求体**:
```json
{
  "tableId": 1,
  "userId": 1,
  "items": [
    {"dishId": 1, "amount": 2},
    {"dishId": 3, "amount": 1}
  ]
}
```
> 注意：`items` 中没有 `price` 字段 — 金额完全由后端根据数据库价格重算

**响应（首次点餐）**:
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 42,
    "tableId": 1,
    "status": 1,
    "statusName": "待制作",
    "totalAmount": 84.80,
    "createTime": "2026-06-12T19:30:00",
    "details": [
      {"dishId": 1, "dishName": "鱼香肉丝", "amount": 2, "price": 29.90},
      {"dishId": 3, "dishName": "番茄蛋汤", "amount": 1, "price": 25.00}
    ]
  }
}
```

**响应（加菜 — 同一桌再次调用）**:
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 42,                    // ← 同一个订单ID
    "totalAmount": 134.80,       // ← 84.80 + 50.00 = 新总价
    "details": [                 // ← 包含旧明细 + 新明细
      {"dishId": 1, "dishName": "鱼香肉丝", "amount": 2, "price": 29.90},
      {"dishId": 3, "dishName": "番茄蛋汤", "amount": 1, "price": 25.00},
      {"dishId": 5, "dishName": "宫保鸡丁", "amount": 2, "price": 25.00}
    ]
  }
}
```

**真实场景示例**:
```
张三扫桌号1的码，点了鱼香肉丝×2     → 创建订单#42，桌台1→占用
李四同桌扫桌号1的码，点了番茄蛋汤×1  → 加菜到订单#42
王五同桌扫桌号1的码，点了宫保鸡丁×2  → 继续加菜到订单#42
中途张三想加菜，再扫一次码           → 继续加菜
服务员结账                           → 订单#42→已结账，桌台1→空闲
下一批客人扫桌号1的码               → 创建新订单#43
```

#### 订单状态流转 `PUT /orders/{id}/status`

**状态枚举**: `0`已取消 `1`待制作 `2`制作中 `3`上菜 `4`用餐中 `5`已结账

**角色权限矩阵**:

| 角色 | 允许操作 | 不允许 |
|:--:|------|:--:|
| 管理员(1) | 所有合法流转 + 取消 | — |
| 服务员(2) | 上菜(2→3)、结账(4→5) | 开始制作(1→2) |
| 后厨(3) | 开始制作(1→2) | 上菜、结账 |

**附加行为**：状态变为 `5`（已结账）或 `0`（已取消）时，**自动释放桌台**（1→0）

**请求示例**:
```
PUT /orders/42/status?status=2
Authorization: Bearer <后厨Token>  // role=3

响应:
{
  "code": 1,
  "msg": "success",
  "data": {
    "orderId": 42,
    "status": 2,
    "operatorId": 3,
    "operatorRole": 3
  }
}
```

**越权示例**:
```
PUT /orders/42/status?status=2
Authorization: Bearer <服务员Token>  // role=2

响应:
{
  "code": 0,
  "msg": "无权或非法状态变更: 1 → 2",
  "data": null
}
```

#### 查询桌台活跃订单 `GET /orders/table/{tableId}/active`

前端扫码后可以先调此接口判断是首次点餐还是加菜：

```json
// 无活跃订单 → 返回 null，前端展示"请点餐"
{ "code": 1, "msg": "success", "data": null }

// 有活跃订单 → 返回订单信息，前端展示"已有点餐，是否加菜？"
{ "code": 1, "msg": "success", "data": { "id": 42, "tableId": 1, "status": 1, ... } }
```

---

### 🍳 菜品管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dishes` | 查询所有菜品 |
| GET | `/dishes/{id}` | 查询单个菜品 |
| POST | `/dishes` | 新增菜品 |
| PUT | `/dishes` | 修改菜品 |
| DELETE | `/dishes/{id}` | 删除菜品 |
| **GET** | **`/dishes/on-sale`** | **查询在售菜品（Redis缓存）** |

**`GET /dishes/on-sale` 缓存策略**:
- 首次请求 → 查 MySQL → 写入 Redis（TTL 1小时）
- 后续请求 → 直接读 Redis（Cache-Aside 模式）
- 增/改/删菜品 → 自动清除缓存
- 数据库无数据 → 缓存空列表 60 秒（穿透防护）

---

### 👥 员工管理

| 方法 | 路径 | 说明 | 认证 | 权限 |
|------|------|------|:--:|:--:|
| POST | `/employees/login` | 员工登录，返回含角色的JWT | ❌ | — |
| GET | `/employees` | 查询员工列表 | ✅ | 全员 |
| GET | `/employees/{id}` | 查询单个员工 | ✅ | 全员 |
| POST | `/employees` | 新增员工 | ✅ | 🔒管理员 |
| PUT | `/employees` | 修改员工信息 | ✅ | 🔒管理员 |
| PUT | `/employees/password` | 修改密码 | ✅ | 全员 |
| DELETE | `/employees/{id}` | 删除员工 | ✅ | 🔒管理员 |

**登录请求**:
```json
POST /employees/login
{"username": "admin", "password": "123456"}

响应:
{
  "code": 1,
  "msg": "success",
  "data": {
    "token": "eyJhbGci...",
    "name": "管理员"
  }
}
```

---

### 📊 管理员统计（隶属 OrdersController）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|:--:|
| **GET** | **`/orders/statistics/today`** | 今日营业额（已结账订单总额） | 🔒 管理员(1) |

**响应**:
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "date": "2026-06-12",
    "totalRevenue": 3847.50
  }
}
```

**实现**: 通过 `order_status_log` 表统计今日结账的订单金额，确保跨日订单按实际结账时间计入：

```sql
SELECT COALESCE(SUM(o.total_amount), 0)
FROM orders o
JOIN order_status_log l ON o.id = l.order_id
WHERE l.to_status = 5 AND DATE(l.create_time) = CURDATE()
```

---

### 🔔 WebSocket 实时通知

| 端点 | 连接方式 | 接收的消息类型 |
|------|------|------|
| `ws://localhost:8080/ws/kitchen?role=kitchen&token=<JWT>` | 后厨显示屏连接 | `NEW_ORDER`（新订单）、`ADD_ITEMS`（加菜） |

**消息格式**:

新订单通知:
```json
{
  "type": "NEW_ORDER",
  "orderId": 42,
  "tableId": 1,
  "itemCount": 3,
  "message": "🆕 新订单 #42 桌号 1，共 3 个菜品"
}
```

加菜通知:
```json
{
  "type": "ADD_ITEMS",
  "orderId": 42,
  "tableId": 1,
  "itemCount": 2,
  "message": "➕ 加菜 订单 #42 桌号 1，新增 2 个菜品"
}
```

**频道隔离设计**: 连接时通过 `?role=kitchen&token=<JWT>` 参数指定频道和认证，服务端维护 `Map<String, Set<WebSocketSession>>`，握手时校验员工 JWT，厨房只收厨房的消息，未来可扩展 waiter/customer 频道。

---

## 🔐 JWT 认证与权限

### Token 生成流程

系统生成两种类型的 JWT Token，通过 `type` claim 区分：

**员工 Token**:
```
员工登录 POST /employees/login
→ EmployeeServiceImpl.login() 验证用户名密码(BCrypt)
→ JwtUtil.generateToken(employeeId, role) 生成Token
→ Token Payload: {sub: "1", type: "employee", role: 1, exp: +2h}
→ 返回给前端，前端存入 localStorage
```

**用户 Token**:
```
用户登录 POST /users/login
→ UserServiceImpl.login() 验证手机号密码(BCrypt)
→ JwtUtil.generateUserToken(userId) 生成Token
→ Token Payload: {sub: "1", type: "user", exp: +2h}
→ 返回给前端（无 role 字段，仅用于顾客端）
```

### Token 校验流程

系统使用**双拦截器**区分员工端和用户端，按 token 类型隔离：

```
请求到达 → 路径匹配
  ├── /employees/**, /orders/** ...
  │     → JwtInterceptor 校验 token 类型必须为 "employee"
  │     → 解析 employeeId + role → 存入 UserContext
  │
  ├── /users/**, /orders/scan-order, /orders/table/**
  │     → UserJwtInterceptor 校验 token 类型必须为 "user"
  │     → 解析 userId → 存入 UserContext
  │
  └── /users/login, /users/register, /employees/login, /ws/**, /doc.html ...
        → 直接放行（无需 Token）
```

**员工 Token 校验**:
```
请求到达 → JwtInterceptor.preHandle()
→ 从 Header 取 "Authorization: Bearer <token>"
→ JwtUtil.parseTokenType(token) 校验类型为 "employee"
→ JwtUtil.parseUserId(token) 解析员工ID
→ JwtUtil.parseRole(token) 解析角色
→ UserContext.setEmployeeId() + UserContext.setRole() 存入ThreadLocal
→ Controller/Service 通过 UserContext 获取当前用户信息
→ afterCompletion() 中 UserContext.clear() 清理
```

**用户 Token 校验**:
```
请求到达 → UserJwtInterceptor.preHandle()
→ 从 Header 取 "Authorization: Bearer <token>"
→ JwtUtil.parseTokenType(token) 校验类型为 "user"
→ JwtUtil.parseUserId(token) 解析用户ID
→ UserContext.setUserId() 存入ThreadLocal
→ afterCompletion() 中 UserContext.clear() 清理
```

### 角色定义

| 值 | 名称 | 权限范围 | 典型用户 |
|:--:|------|------|------|
| 1 | 管理员 | 全部操作 + 营业额统计 | 店长/老板 |
| 2 | 服务员 | 桌台管理、上菜(2→3)、结账(4→5) | 前台/服务员 |
| 3 | 后厨 | 查看订单、开始制作(1→2) | 厨师 |

### 路径拦截白名单

**员工拦截器** (`JwtInterceptor`) 覆盖 `/**`，排除以下路径：

| 排除路径 | 原因 |
|------|------|
| `/employees/login`, `/employees/login/**` | 员工登录 |
| `/users/**` | 用户端路径，由 UserJwtInterceptor 处理 |
| `/orders/scan-order` | 扫码点餐，顾客和员工均可访问 |
| `/orders/table/**` | 查询桌台活跃订单，顾客扫码后使用 |
| `/dishes/on-sale` | 顾客扫码后浏览在售菜品 |
| `/swagger-ui/**`, `/v3/api-docs/**`, `/doc.html`, `/webjars/**` | API 文档 |
| `/ws/**` | WebSocket 连接（握手时自行校验 JWT） |
| `/error` | Spring 错误页 |

**用户拦截器** (`UserJwtInterceptor`) 覆盖以下路径，排除 `/users/login` 和 `/users/register`：

| 覆盖路径 | 说明 |
|------|------|
| `/users/**` | 查询个人信息 `/users/me`（注册/登录已放行） |
| `/orders/scan-order` | 扫码点餐 |
| `/orders/table/**` | 查询桌台活跃订单 |

> 注意：`/orders/scan-order` 和 `/orders/table/**` 被员工拦截器排除、由用户拦截器接管，确保顾客（用户 token）可以正常扫码点餐。

---

## 🧪 测试

### 运行测试

```powershell
mvn test
```

### 测试策略

- **框架**: JUnit 5 + `@SpringBootTest` + `@Transactional`（自动回滚）
- **每个测试方法独立运行**，不依赖执行顺序，不污染数据库

### 测试覆盖清单

**TableInfoServiceTest（7个用例）**:

| 用例 | 验证点 |
|------|------|
| `shouldTransitionFromIdleToOccupied` | 0→1 正常流转 + version递增 |
| `shouldTransitionFromOccupiedToIdle` | 1→0 正常流转 + version累加 |
| `shouldThrowWhenIllegalTransition` | 相同状态流转拒绝 |
| `shouldThrowWhenOccupiedToReserved` | 占用→非法状态拒绝 |
| `shouldThrowWhenOptimisticLockConflict` | 过期version导致CAS失败 |
| `shouldVersionIncrementCorrectly` | 连续操作version正确累加 |
| `shouldThrowWhenTableNotExists` | 不存在的桌台抛异常 |

**OrdersServiceTest（20个用例）**:

| 用例分类 | 用例 | 验证点 |
|------|------|------|
| 首次点餐 | `shouldCreateOrderAndLockTable` | 创建订单 + 桌台占用 + 金额正确 |
| | `shouldRecalculateAmountCorrectly_MultipleItems` | 多菜品金额累加 |
| 加菜 | `shouldAddItemsToExistingOrder` | 同一订单追加 + 总价更新 |
| | `shouldAddItemsMultipleTimes` | 多次加菜累加正确 |
| | `multiplePeopleSameTableShouldAddToSameOrder` | 三人同桌共享订单 |
| 释放+再点 | `shouldReleaseTableAfterSettlement` | 结账→桌台释放 |
| | `shouldReleaseTableAfterCancel` | 取消→桌台释放 |
| | `shouldCreateNewOrderAfterPreviousSettled` | 释放后新客人建新订单 |
| 菜品校验 | `shouldFailWhenDishNotExists` | 不存在菜品拒绝 |
| | `shouldFailWhenDishOffSale` | 下架菜品拒绝（首次） |
| | `shouldFailWhenAddItemsWithOffSaleDish` | 下架菜品拒绝（加菜） |
| 角色权限 | `chefShouldTransitionFromPendingToCooking` | 后厨 1→2 |
| | `chefShouldNotTransitionToServing` | 后厨不能上菜 |
| | `waiterShouldTransitionFromCookingToServing` | 服务员 2→3 |
| | `waiterShouldCheckout` | 服务员 4→5 |
| | `waiterShouldNotStartCooking` | 服务员不能制作 |
| | `adminShouldHaveFullPermission` | 管理员全权限 |
| 安全 | `shouldIgnoreFrontendPrice` | 金额由后端重算（首次+加菜） |
| 取消 | `shouldAllowCancelFromAnyState` | 待制作→取消 |

---

## 🏗️ 技术深度 — 设计决策

### 1. 为什么桌台用乐观锁，加菜用悲观锁？

| 维度 | 桌台抢占（乐观锁） | 加菜（悲观锁） |
|------|------|------|
| 冲突概率 | 低（同一桌同时下单概率小） | 高（同一订单并发加菜频繁） |
| 锁粒度 | 单行 + version字段 | 单行 FOR UPDATE |
| 阻塞行为 | 不阻塞，冲突时抛异常让客户端重试 | 阻塞等待，保证串行化 |
| SQL | `UPDATE ... WHERE version=#{v}` | `SELECT ... FOR UPDATE` |
| 适用场景 | 读多写少 | 写操作需要严格顺序 |

**决策逻辑**：不是"乐观锁一定比悲观锁好"或反之，而是**根据实际冲突概率选择**。桌台冲突少→乐观锁减少锁开销；加菜必改同一行→悲观锁防丢失更新。

### 2. 为什么用 Cache-Aside 而不是 Spring @Cacheable？

| 维度 | Cache-Aside（手动） | @Cacheable（注解） |
|------|------|------|
| 缓存逻辑可见性 | 代码中显式读写缓存 | 隐藏在 AOP 切片中 |
| 穿透防护 | 手动实现（空值短TTL） | 需额外配置 |
| 失效策略 | 精确控制（add/update/delete时删key） | @CacheEvict 注解 |
| 调试难度 | 低，直接看代码 | 高，需跟踪代理 |

**决策**：Cache-Aside 让缓存逻辑对开发者完全可见，面试时可以逐行讲解。生产环境中对于简单场景 @Cacheable 更省代码，复杂场景 Cache-Aside 更可控。

### 3. 为什么金额在 Service 计算而不是 SQL 聚合？

- **安全性**：在 Service 层逐菜品校验（存在性、在售状态）后计算，可以在计算前拦截异常菜品
- **可测试性**：Service 层计算逻辑可直接单元测试，SQL 聚合较难脱离数据库测试
- **可扩展性**：未来加折扣、优惠券、会员价等逻辑，在 Service 层扩展更方便

---

## 🔒 安全设计

| 安全措施 | 实现方式 | 防护目标 |
|------|------|------|
| 金额后端重算 | DTO 不含 price 字段，查DB真实价格计算 | 防止前端篡改价格 |
| 密码加密 | BCrypt 哈希（每用户独立盐值） | 数据库泄露后密码不可逆 |
| 双 Token 类型隔离 | JWT 含 type claim（employee/user），拦截器交叉校验 | 防止用户 token 越权访问员工接口，反之亦然 |
| JWT 过期 | Token 有效期 2 小时 | 限制泄露 Token 影响时间 |
| 角色权限 | JWT 含 role + 业务层二次校验 | 防止越权操作 |
| 员工管理写操作鉴权 | 增删改员工时 Controller 层校验 role==1 | 防止服务员/后厨越权管理员工 |
| 用户信息查询防越权 | `GET /users/me` 从 JWT token 提取 userId，不接受前端传 ID | 防止用户查他人信息 |
| 订单列表分页 | `LIMIT offset, size` + 参数校验（size 上限 100） | 防止全量返回导致内存/网络压力 |
| 桌台并发 | CAS 乐观锁（version + WHERE 条件） | 防止重复占用 |
| 加菜并发 | SELECT FOR UPDATE 行锁 | 防止丢失更新 |
| 异常统一处理 | GlobalExceptionHandler | 不泄露内部错误细节 |
| 审计日志 | OrderStatusLog（订单状态流转） | 营业额统计依据 |

---

## 📊 数据库设计（核心表）

### table_info（桌台信息）

```sql
CREATE TABLE table_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,          -- 桌台名称 "A1"
    capacity INT DEFAULT 4,             -- 可容纳人数
    status INT DEFAULT 0,               -- 0空闲 1占用
    version INT DEFAULT 0,              -- 乐观锁版本号
    create_time DATETIME,
    update_time DATETIME
);
```

### orders（订单）

```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,                     -- 下单用户
    table_id BIGINT,                    -- 关联桌台
    status INT DEFAULT 1,               -- 0取消 1待制作 2制作中 3上菜 4用餐中 5已结账
    total_amount DECIMAL(10,2),         -- 订单总金额（后端重算）
    create_time DATETIME
);
```

### order_detail（订单明细）

```sql
CREATE TABLE order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,                    -- 关联订单
    dish_id BIGINT,                     -- 菜品ID
    amount INT,                         -- 数量
    price DECIMAL(10,2),                -- 下单时的菜品单价
    FOREIGN KEY (order_id) REFERENCES orders(id)
);
```

### 订单状态日志表

- `order_status_log`: order_id, from_status, to_status, operator_id, create_time
  - 用途：按结账时间（to_status=5）统计当日营业额，避免依赖订单创建时间造成的误差

---

## 📝 开发规范

- **三层架构**: Controller → Service(接口) → ServiceImpl → Mapper
- **面向接口编程**: Controller 注入 Service 接口，不依赖实现类
- **全注解 MyBatis**: 零 XML 配置，SQL 直接写在 `@Select`/`@Insert`/`@Update` 中
- **Lombok**: `@Data` 自动生成 getter/setter，减少样板代码
- **统一响应**: 所有接口返回 `Result<T>` 格式 `{code, msg, data}`
- **异常处理**: 业务异常抛 `BusinessException` → `GlobalExceptionHandler` 捕获
- **事务边界**: 核心下单操作标注 `@Transactional`，Service 层控制事务
- **包结构**: 按功能分包（controller/service/mapper/entity/dto/common），非按层分包

---

*项目版本: 0.0.1-SNAPSHOT — 2026年6月*
