# MediSlot 数据库设计

数据库名：`medislot_db`，**PostgreSQL 16 + pgvector**（业务数据与 RAG 向量同库）。

## 实体关系

```
科室 Department 1 ── N 医生 Doctor
用户 User       1 ── 1 医生 Doctor
医生 Doctor     1 ── N 排班 Schedule
排班 Schedule   1 ── N 预约 Appointment
患者 User       1 ── N 预约 Appointment
预约 Appointment 1 ── 1 支付 Payment
预约 Appointment 1 ── 1 结构化症状 SymptomIntake
预约 Appointment 1 ── N 会话 Conversation
会话 Conversation 1 ── N 消息 ConversationMessage
消息 ConversationMessage 1 ── N 附件 MessageAttachment
```

一句话：一个科室有多个医生，一个医生有多个排班，一个排班能被多个患者预约，一个预约对应一笔支付。

## 表结构

### users（用户）

> 表名 `users`（`user` 是 PostgreSQL 保留字）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| phone | VARCHAR(20) | UNIQUE, NOT NULL | 手机号，登录账号 |
| password | VARCHAR(100) | NOT NULL | BCrypt 加密后的密码 |
| name | VARCHAR(50) | NOT NULL | 姓名 |
| role | VARCHAR(20) | NOT NULL | `PATIENT` / `DOCTOR` / `ADMIN` / `KB_MAINTAINER` |
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
| schedule_date | DATE | NOT NULL | 出诊日期 |
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

### conversation（会话）

一个预约有：一个群聊（`GROUP`：患者+医生+AI）与一个医生病例研究窗口（`DOCTOR_PRIVATE`：医生+AI，患者不可见）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| appointment_id | BIGINT | FK→appointment | 关联预约 |
| scope | VARCHAR(20) | NOT NULL | `GROUP` / `DOCTOR_PRIVATE` |
| doctor_id | BIGINT | FK→doctor | 归属医生 |
| status | VARCHAR(20) | NOT NULL | `ACTIVE` / `CLOSED` |
| ai_model | VARCHAR(50) | | 使用的模型 |
| summary | VARCHAR(2000) | | AI 生成的问诊摘要 |
| created_at / updated_at / closed_at | DATETIME | | |

唯一约束：`(appointment_id, scope)`。

### conversation_message（会话消息）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| conversation_id | BIGINT | FK→conversation | 所属会话 |
| sender_type | VARCHAR(20) | NOT NULL | `PATIENT` / `DOCTOR` / `AI` / `SYSTEM` |
| sender_id | BIGINT | | 人类发送者 user_id |
| message_type | VARCHAR(20) | NOT NULL | `CHAT` / `DIRECTIVE`（医生指挥 AI）/ `DRAFT`（AI 草稿） |
| content | VARCHAR(4000) | NOT NULL | 内容 |
| parent_message_id | BIGINT | | DRAFT 对应的 DIRECTIVE |
| review_status | VARCHAR(20) | | 草稿核实：`PENDING`/`APPROVED`/`REJECTED`/`ADJUSTED` |
| reviewed_by | BIGINT | | 核实的医生 |
| review_note | VARCHAR(500) | | 核实备注 |
| model / prompt_tokens / completion_tokens | | | AI 消息用量 |
| created_at | DATETIME | NOT NULL | |

### app_setting（系统设置）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| setting_key | VARCHAR(100) | PK | 如 `consultation.max-messages` |
| setting_value | VARCHAR(500) | | 值 |
| description | VARCHAR(255) | | 说明 |
| updated_at | DATETIME | | 更新时间 |

> 默认写入：`consultation.enabled=true`、`consultation.max-messages=30`、
> `consultation.rate-limit-seconds=3`、`consultation.max-history=20`。

### symptom_intake（结构化症状采集）

患者预填的问诊信息，与预约 1:1；会作为上下文注入 AI 系统提示词，并在群聊页展示给医生。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| appointment_id | BIGINT | FK→appointment, UNIQUE | 关联预约 |
| chief_complaint | VARCHAR(500) | | 主诉 |
| symptoms | VARCHAR(500) | | 症状（逗号分隔） |
| duration | VARCHAR(50) | | 病程 |
| severity | VARCHAR(20) | | `MILD` / `MODERATE` / `SEVERE` |
| accompanying | VARCHAR(500) | | 伴随症状 |
| past_history | VARCHAR(500) | | 既往史 |
| medications | VARCHAR(500) | | 近期用药 |
| allergies | VARCHAR(500) | | 过敏史 |
| temperature | DECIMAL(3,1) | | 体温（℃） |
| red_flags | VARCHAR(255) | | 危险信号（逗号分隔） |
| created_at / updated_at | DATETIME | | |

### message_attachment（消息附件）

图片 / 文件。上传时先落库（`message_id` 为空），发送消息时再关联。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| message_id | BIGINT | FK→conversation_message | 关联消息（可空） |
| conversation_id | BIGINT | FK→conversation, NOT NULL | 所属会话 |
| uploader_id | BIGINT | | 上传者 user_id |
| original_name | VARCHAR(255) | | 原始文件名 |
| stored_name | VARCHAR(100) | NOT NULL | 存储文件名（UUID） |
| content_type | VARCHAR(100) | | MIME 类型（图片 / PDF） |
| size | BIGINT | NOT NULL | 字节数（上限 5MB） |
| created_at | DATETIME | NOT NULL | |

> 文件落盘在 `medislot.upload.dir`（容器内 `/app/uploads`，映射到宿主机 `./data/uploads`）；
> 下载走 `GET /attachments/{id}`，仅会话参与方可访问。

### knowledge_document（知识库文档）

RAG 知识库的文档元数据。切分片段与向量由 Spring AI 的 `vector_store` 表管理
（metadata 含 `documentId` / `title` / `chunkIndex`），维度 1024（智谱 `embedding-3`）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, auto | 主键 |
| title | VARCHAR(200) | NOT NULL | 标题 |
| original_filename | VARCHAR(255) | | 原始文件名 |
| stored_name | VARCHAR(100) | NOT NULL | 存储文件名（UUID） |
| content_type | VARCHAR(100) | | MIME 类型 |
| size | BIGINT | NOT NULL | 字节数（上限 20MB） |
| status | VARCHAR(20) | NOT NULL | `PENDING`/`PROCESSING`/`READY`/`FAILED` |
| chunk_count | INT | | 切分片段数 |
| error_message | VARCHAR(500) | | 失败原因 |
| category | VARCHAR(50) | | 分类（如科室） |
| uploaded_by | BIGINT | | 上传者 user_id |
| created_at / updated_at | DATETIME | | |

> `vector_store` 由 Spring AI PgVectorStore 创建（`embedding vector(1024)` + HNSW 索引）。
> 原始文件落盘在 `medislot.kb.dir`（容器内 `/app/uploads/kb`）。
> 管理员可在「设置中心」用 `rag.enabled` / `rag.top-k` / `rag.max-context-chars` 控制检索增强。

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
