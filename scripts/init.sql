-- MediSlot 数据库初始化脚本（PostgreSQL + pgvector）
-- 由 docker-compose 挂载到 Postgres 容器的 /docker-entrypoint-initdb.d/
-- 仅在数据卷首次创建时执行一次；以 POSTGRES_USER（超级用户）身份执行。
--
-- 说明：阶段一 docker profile 使用 Hibernate 的 ddl-auto=update 自动建表，
--      本脚本负责启用向量扩展（RAG 需要）。

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
