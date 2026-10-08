#!/usr/bin/env bash
# 启动后端（*nix）
# 用法：./scripts/start-backend.sh [profile]
set -euo pipefail
PROFILE="${1:-dev}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT/apps/backend/mvnw" -f "$ROOT/apps/backend/pom.xml" spring-boot:run "-Dspring-boot.run.profiles=$PROFILE"
