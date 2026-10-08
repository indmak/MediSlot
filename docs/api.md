# MediSlot API 约定

> 阶段一 **不启用** 本文件描述的接口。`apps/backend/.../api/` 目前是占位包。
> 等网页版（Thymeleaf）跑通、接口需求被真实业务验证后，再在 `api/` 下实现。

## 统一响应格式

所有 REST 接口返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code = 0`：成功
- `code != 0`：业务错误，`message` 为错误描述
- `data`：具体业务数据

Java 侧对应 `com.medislot.dto.ApiResponse<T>`。

## 计划中的接口（阶段二）

| 方法 | 路径 | 说明 | 对应 service |
|------|------|------|--------------|
| POST | `/api/auth/login` | 登录，返回 token | `UserService` |
| POST | `/api/auth/register` | 注册 | `UserService` |
| GET | `/api/departments` | 科室列表 | `DoctorService` |
| GET | `/api/doctors?departmentId=` | 医生列表 | `DoctorService` |
| GET | `/api/doctors/{id}` | 医生详情 | `DoctorService` |
| GET | `/api/doctors/{id}/schedules?from=&to=` | 可预约排班 | `ScheduleService` |
| POST | `/api/appointments` | 提交预约 | `AppointmentService` |
| GET | `/api/appointments/mine` | 我的预约 | `AppointmentService` |
| POST | `/api/appointments/{id}/cancel` | 取消预约 | `AppointmentService` |

> 原则：`api/` 的 `@RestController` 与 `web/` 的 `@Controller` 复用**同一个** service，
> 业务逻辑只实现一遍。
