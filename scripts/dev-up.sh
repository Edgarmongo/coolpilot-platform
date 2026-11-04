#!/usr/bin/env bash
# 启动本地开发环境（MySQL / Redis / API / 前台 / 后台）
# 用法：./scripts/dev-up.sh            全量构建并启动
#      ./scripts/dev-up.sh --no-build  仅启动，不重建镜像
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "${1:-}" = "--no-build" ]; then
  docker compose up -d
else
  docker compose up -d --build
fi

docker compose ps
