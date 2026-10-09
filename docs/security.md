# 登录与安全加固

MediSlot 的认证入口只有一个：`/login`（Spring Security 表单登录）。本页说明账号安全相关的实现。

## 密码策略

- 至少 **8 位**，且同时包含**字母**和**数字**。
- 由 `UserService.validatePassword` 在「患者注册」和「管理后台创建账号」时统一校验，违反抛 `BusinessException`，由 `GlobalExceptionHandler` 渲染友好错误页。
- 存储使用 BCrypt（`BCryptPasswordEncoder`）。

## 登录失败锁定

- 同一手机号连续失败 **5 次**后锁定 **15 分钟**。
- `LoginAttemptService` 进程内计数（`ConcurrentHashMap`，单实例部署足够；多实例需换 Redis）。
- `LoginLockFilter`（挂在 `UsernamePasswordAuthenticationFilter` 之前）在密码校验**之前**拦截被锁定的手机号，即使密码正确也拒绝；`MediSlotAuthFailureHandler` 记录失败，成功登录即清零。

## 两步验证（TOTP）

- **强制角色**：`ADMIN`、`KB_MAINTAINER`。其余角色可选（`/account/2fa`）。
- 算法：标准 TOTP（SHA1 / 6 位 / 30 秒，允许前后各 1 个周期偏差），兼容**腾讯身份验证器**、Google Authenticator 等任意标准 App。实现在 `TwoFactorService`（依赖 `dev.samstevens.totp`，二维码用 ZXing 生成 PNG Data URI）。

### 登录流程

```
POST /login（手机号 + 密码）
        │  密码正确
        ▼
  角色是否强制 2FA？
    ├─ 否 ─▶ 按角色跳转首页
    └─ 是
        ├─ 已绑定 ─▶ 降级为 ROLE_PRE_2FA ─▶ /login/2fa（输入 6 位动态码）─▶ 完成登录
        └─ 未绑定 ─▶ 降级为 ROLE_PRE_2FA ─▶ /account/2fa（扫码绑定 + 验证）─▶ 完成登录
```

- 登录成功后由 `MediSlotAuthSuccessHandler` 分流：强制角色把身份**降级**为只含 `ROLE_PRE_2FA`，并把手机号/模式写入 session。
- `PreTwoFactorFilter`（挂在 `AuthorizationFilter` 之前）让 `ROLE_PRE_2FA` 用户**只能**访问 `/login/2fa`、`/account/2fa`、`/logout` 与静态资源，其余请求一律重定向回验证/绑定页。
- 验证通过后 `TwoFactorController.completeLogin` 写入正式身份（`HttpSessionSecurityContextRepository`），并按角色跳转。
- 已绑定用户无法自行关闭 2FA（`/account/2fa/disable` 对强制角色拒绝）。**若丢失验证器**，需人工在数据库清空该用户的 `totp_secret` 并把 `totp_enabled` 置为 `false`，下次登录重新绑定。

### 相关字段（`users` 表）

| 字段 | 说明 |
|------|------|
| `totp_secret` | Base32 密钥，未启用时为空 |
| `totp_enabled` | 是否已启用 |

## 会话加固

`application.yml`：

```yaml
server:
  servlet:
    session:
      timeout: 30m
      cookie:
        name: MEDISLOT_SESSION
        http-only: true
        same-site: lax
```

`application-docker.yml` 额外开启 `cookie.secure: true`（全站 HTTPS）。登录成功时 Spring Security 默认会轮换 session id（防会话固定）。

## 关键类

| 关注点 | 类 |
|--------|----|
| 密码策略 / 账号 | `service/UserService` |
| 失败锁定计数 | `service/LoginAttemptService` |
| 锁定前置拦截 | `config/LoginLockFilter` |
| 登录成功分流 / 角色落地页 | `config/MediSlotAuthSuccessHandler` |
| 登录失败处理 | `config/MediSlotAuthFailureHandler` |
| 待 2FA 门禁 | `config/PreTwoFactorFilter` |
| TOTP 生成/校验/二维码 | `service/TwoFactorService` |
| 2FA 页面与流程 | `web/TwoFactorController` |
| 过滤器链 / 授权规则 | `config/SecurityConfig` |
