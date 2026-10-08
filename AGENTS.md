# MediSlot 项目规则

> 诊所 / 社区门诊预约挂号系统。Monorepo：一个 Spring Boot 后端 + 多端客户端（Web 先行）。
> 本文件供 opencode 等 AI 工具读取，约束协作方式。

## 分层规则

- 业务逻辑一律放 `service/`，`web/` 和 `api/` 只做请求适配。
- `web/` 的 Controller：接收请求 → 调用 service → 填 Model → 返回视图名。
- `api/` 的 Controller：接收请求 → 调用 service → 返回 `ApiResponse`。
- 数据库操作只走 `repository/`，Controller 和 Service 不直接写 SQL。
- 实体放 `entity/`，接口出入参放 `dto/`，不要直接把实体暴露给客户端。

## 响应格式（API 阶段）

统一使用 `{ code, message, data }`：

- `code = 0` 表示成功
- `code != 0` 表示业务错误，`message` 为错误描述
- `data` 为具体业务数据

## 命名约定

- 实体：单数名词，如 `Appointment`
- Repository：`XxxRepository`
- Service：`XxxService`
- Controller：`XxxController`（web）/ `XxxApiController`（api）
- 模板：`templates/{模块}/{动作}.html`

## 事务

- 涉及多表写入的 Service 方法必须加 `@Transactional`。
- 号源扣减走乐观锁（`@Version` + 条件更新），不要用悲观锁。

## 分阶段

- 当前阶段只做 Web（Thymeleaf），**不提前抽 API**。`api/` 先留占位包。
- 新增业务逻辑先写 service，web 层只是调用者。
- `apps/mobile/`（Flutter）与 `apps/miniprogram/`（小程序）现在不建，等真要做时再创建。

## 技术栈（阶段一）

- Java 25（Temurin）、Spring Boot 4.1.x、Thymeleaf、Spring Data JPA（Hibernate）、MySQL 8.x
- Spring Security 表单登录 + 角色授权（`PATIENT` / `DOCTOR` / `ADMIN`）
- Bootstrap 5 + 自定义 CSS（见 `docs-zh/ui设计规划.md` 的落地实现 `static/css/`）
- Maven 构建；Docker Compose 部署（MySQL + 应用两个容器）

## 构建 / 运行 / 测试

```bash
# 构建（跳过测试）
apps/backend/mvnw.cmd -f apps/backend/pom.xml clean package -DskipTests   # Windows
apps/backend/mvnw     -f apps/backend/pom.xml clean package -DskipTests   # *nix

# 本地运行（默认 dev profile，需要本地 MySQL）
apps/backend/mvnw.cmd -f apps/backend/pom.xml spring-boot:run

# 单元测试（使用 H2 内存库，无需 MySQL）
apps/backend/mvnw.cmd -f apps/backend/pom.xml test

# 一键部署（MySQL + 应用）
docker compose up -d --build
```

## 网络约定

- 无外网时走本地代理 `127.0.0.1:3128`（Maven、npm、docker pull 均可配置该代理）。
