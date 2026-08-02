# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 3.2 + MyBatis restaurant backend — QR-code ordering, kitchen collaboration, checkout. Java 17, Maven, MySQL + Redis + WebSocket. See `README.md` for full business context, API docs, and database schema.

## Build & run

Uses Maven Wrapper (no local Maven install needed — `mvnw`/`mvnw.cmd` auto-downloads it):

```powershell
# Compile
.\mvnw.cmd compile

# Run (needs MySQL + Redis; configure DB/Redis passwords in src/main/resources/application-local.yml)
.\mvnw.cmd spring-boot:run

# Run all tests
.\mvnw.cmd test

# Run a single test class
.\mvnw.cmd test -Dtest="OrdersServiceTest"

# Run a single test method
.\mvnw.cmd test -Dtest="OrdersServiceTest#shouldCreateOrderAndLockTable"
```

Note: tests are NOT @Transactional — `TableInfoServiceImpl.updateStatus` uses REQUIRES_NEW (CAS retry), so test data must commit to be visible; both test classes clean up their own data in `@AfterEach`.

The app starts on `http://localhost:8080`. API docs at `/doc.html` (Knife4j).

## Architecture

**Layered architecture** — Controller → Service (interface) → ServiceImpl → Mapper. Controllers inject Service interfaces, never implementations.

**Two JWT interceptors** (`WebConfig.java:24-44`):
- `JwtInterceptor` — employee auth, covers `/**` except login / Swagger / WebSocket / error paths
- `UserJwtInterceptor` — customer auth, covers `/users/**` except login/register only
- Both store current user in `UserContext` (ThreadLocal) and clear on `afterCompletion`

**Concurrency strategy** (the key design decision):
- Table occupancy uses **CAS optimistic locking** — `UPDATE table_info SET status=?, version=version+1 WHERE id=? AND version=?`. Low contention scenario; conflict → throw BusinessException, caller retries.
- Add-items-to-order uses **pessimistic locking** — `SELECT ... FOR UPDATE` on the order row. High contention (same table guests all hit the same order); the lock serializes access and prevents lost updates on `total_amount`.

**Cache strategy** (DishServiceImpl): manual Cache-Aside for listed dishes. Read: check Redis → if miss, query MySQL → write Redis (TTL 1h). Write: on add/update/delete, evict the cache key. Empty DB results cached for 60s (penetration guard). Not using `@Cacheable` — logic is explicit in code.

**Two token types** — `JwtUtil` issues separate tokens:
- Employee token: `{sub: employeeId, type: "employee", role: 1|2|3, exp: +2h}`
- User token: `{sub: userId, type: "user", exp: +2h}` (no role)

**Scan-order is the core flow** (`OrdersServiceImpl.placeOrder`):
1. Check if table has active order (status IN 1,2,3,4)
2. No active → create order + occupy table (optimistic lock) + WebSocket notify
3. Has active → add items (pessimistic lock on order row) + recalculate total + WebSocket notify
4. Prices are always recalculated from DB — the DTO has no `price` field

**Global exception handling** — `BusinessException` thrown in service layer, caught by `GlobalExceptionHandler` (`@RestControllerAdvice`), returns `Result.error(msg)`.

## Key patterns

- **Response format**: all endpoints return `Result<T>` — `{code: 1|0, msg: string, data: T}`. Use `Result.success(data)` / `Result.error(msg)`.
- **MyBatis zero-XML**: all SQL in `@Select`/`@Insert`/`@Update`/`@Delete` annotations on mapper interfaces
- **MyBatis config**: `map-underscore-to-camel-case: true` (DB `table_id` → Java `tableId`), SQL logging enabled via `StdOutImpl`
- **Status enums**: orders 0-cancelled/1-pending/2-cooking/3-serving/4-dining/5-settled; tables 0-idle/1-occupied
- **Role matrix**: 1-admin (full), 2-waiter (serve 2→3, dining 3→4, checkout 4→5), 3-chef (start-cooking 1→2)
- **`application-local.yml`** is git-ignored (contains passwords) — each developer creates their own
- **Database init script**: `src/main/resources/db/init.sql` — contains table DDL and seed data

## Testing conventions

- JUnit 5 + `@SpringBootTest` + `@Transactional` (auto-rollback after each test)
- Tests are in `src/test/java/org/example/restaurant/service/` — service-layer integration tests, no mocking
- Each test method is independent; no shared state between tests
