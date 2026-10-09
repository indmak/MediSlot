# MediSlot 数据库设计

数据库名：`medislot_db`，关系库 MySQL 8.x，字符集 `utf8mb4`。

## 实体关系

```
科室 Department 1 ── N 医生 Doctor
用户 User       1 ── 1 医生 Doctor
医生 Doctor     1 ── N 排班 Schedule
排班 Schedule   1 ── N 预约 Appointment
患者 User       1 ── N 预约 Appointment
预约 Appointment 1 ── 1 支付 Payment
```

一句话：一个科室有多个医生，一个医生有多个排班，一个排班能被多个患者预约，一个预约对应一笔支付。

## 表结构

### user（用户）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| phone | VARCHAR(20) | UNIQUE, NOT NULL | 手机号，登录账号 |
| password | VARCHAR(100) | NOT NULL | BCrypt 加密后的密码 |
| name | VARCHAR(50) | NOT NULL | 姓名 |
| role | VARCHAR(20) | NOT NULL | `PATIENT` / `DOCTOR` / `ADMIN` |
| created_at | DATETIME | NOT NULL | 创建时间 |

### department（科室）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| name | VARCHAR(50) | UNIQUE, NOT NULL | 科室名称 |
| sort_order | INT | NOT NULL | 排序号 |

### doctor（医生）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| user_id | BIGINT | FK→user, UNIQUE | 关联登录账号 |
| department_id | BIGINT | FK→department | 所属科室 |
| title | VARCHAR(50) | | 职称 |
| bio | VARCHAR(1000) | | 简介 |
| avatar_url | VARCHAR(255) | | 头像地址 |
| rating | DECIMAL(2,1) | | 评分 |
| appointment_count | INT | NOT NULL, default 0 | 累计预约数（冗余统计） |
| registration_fee | DECIMAL(8,2) | NOT NULL, default 0 | 挂号费（元），每个医生单独设置 |

### schedule（排班 / 号源）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| doctor_id | BIGINT | FK→doctor | 所属医生 |
| date | DATE | NOT NULL | 出诊日期 |
| start_time | TIME | NOT NULL | 开始时间 |
| end_time | TIME | NOT NULL | 结束时间 |
| total_count | INT | NOT NULL | 总号源数 |
| booked_count | INT | NOT NULL, default 0 | 已约数 |
| version | BIGINT | NOT NULL, default 0 | 乐观锁版本号 |

> 号源扣减：`UPDATE ... SET booked_count = booked_count + 1, version = version + 1
> WHERE id = ? AND version = ? AND booked_count < total_count`
> （由 JPA `@Version` + 条件更新保证，不用悲观锁）

### appointment（预约）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| appointment_no | VARCHAR(32) | UNIQUE, NOT NULL | 预约编号，如 A20261015-003 |
| schedule_id | BIGINT | FK→schedule | 预约的号源 |
| patient_id | BIGINT | FK→user | 就诊患者 |
| status | VARCHAR(20) | NOT NULL | 见下方状态机 |
| reason | VARCHAR(500) | | 就诊原因（选填） |
| diagnosis_note | VARCHAR(500) | | 医生诊断备注 |
| registration_fee | DECIMAL(8,2) | | 挂号费**快照**（下单时医生的挂号费） |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | | 更新时间 |

### payment（支付订单）

一个预约对应一笔支付。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| payment_no | VARCHAR(32) | UNIQUE, NOT NULL | 支付单号，如 P... |
| appointment_id | BIGINT | FK→appointment, UNIQUE | 关联预约 |
| amount | DECIMAL(8,2) | NOT NULL | 金额快照 |
| status | VARCHAR(20) | NOT NULL | 见下方支付状态 |
| method | VARCHAR(20) | | 渠道：`MOCK`（模拟）/ `ALIPAY` |
| paid_at | DATETIME | | 支付时间 |
| expires_at | DATETIME | NOT NULL | 支付截止时间（默认下单 +30 分钟） |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | | 更新时间 |

**支付状态**：`UNPAID`（待支付）→ `PAID`（已支付）→ `REFUNDED`（已退款，终态）；`UNPAID` 超时 → `EXPIRED`（已失效，终态）。

> **超时作废**：定时任务每 60 秒扫描 `status=UNPAID AND expires_at < now` 的订单，
> 标记为 `EXPIRED`，把对应预约置为 `CANCELLED` 并释放号源。
> **取消预约**：已支付 → 标记 `REFUNDED`（自动退款）；未支付 → 标记 `EXPIRED`。

## 预约状态机

```
[创建] → PENDING（待就诊）
            ↓          ↓
      CHECKED_IN      CANCELLED（已取消，终态）
     （已到诊）
            ↓
       COMPLETED（已完成，终态）
```

- 只有 `PENDING` 可取消
- 只有 `PENDING` 可被医生标记 `CHECKED_IN`
- 只有 `CHECKED_IN` 可被标记 `COMPLETED`
- `CANCELLED` / `COMPLETED` 为终态
