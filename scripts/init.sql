-- MediSlot 数据库初始化脚本
-- 由 docker-compose 挂载到 MySQL 容器的 /docker-entrypoint-initdb.d/
-- 仅在数据卷首次创建时执行一次。
--
-- 说明：阶段一 docker profile 使用 Hibernate 的 ddl-auto=update 自动建表，
--      本脚本只负责确保库和字符集正确。等表结构稳定后再切换为
--      ddl-auto=validate + 完整的建表 SQL / Flyway 迁移。

CREATE DATABASE IF NOT EXISTS medislot_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE medislot_db;
