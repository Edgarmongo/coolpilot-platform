#!/usr/bin/env bash
# 只重建后端（改了 Java 代码后用），前端是 bind mount，热更新无需重建
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose up -d --build api
echo "--- 最近日志 ---"
docker compose logs --tail 30 api
