#!/usr/bin/env bash
# 停止本地开发环境（保留数据卷）
# 用法：./scripts/dev-down.sh          停止容器
#      ./scripts/dev-down.sh --clean  停止并删除数据卷（会清空数据库，慎用）
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "${1:-}" = "--clean" ]; then
  echo "⚠️  将删除 mysql_data / upload_data 等数据卷，数据库会被清空。"
  read -r -p "确认继续？输入 yes：" answer
  if [ "$answer" != "yes" ]; then
    echo "已取消"
    exit 1
  fi
  docker compose down -v
else
  docker compose down
fi
