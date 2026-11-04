#!/usr/bin/env bash
# 进入 MySQL 命令行（容器内），用于排查数据问题
# 用法：./scripts/db-shell.sh                进入 nblog 库
#      ./scripts/db-shell.sh "SHOW TABLES;"  直接执行一条 SQL
set -euo pipefail
cd "$(dirname "$0")/.."

# shellcheck disable=SC1091
set -a; . ./.env; set +a

if [ $# -gt 0 ]; then
  docker exec -i nblog-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" nblog -e "$1"
else
  docker exec -it nblog-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" nblog
fi
