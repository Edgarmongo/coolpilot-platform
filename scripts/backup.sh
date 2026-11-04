#!/usr/bin/env bash
# 备份数据库 + 上传目录（本地存储方案下的必需品）
#
# 用法：./scripts/backup.sh          备份到 ./backups
#      ./scripts/backup.sh /mnt/nas  备份到指定目录
#
# 建议挂 crontab：0 3 * * * cd /path/to/coolpilot-platform && ./scripts/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."

OUT="${1:-backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
# shellcheck disable=SC1091
set -a; . ./.env; set +a

echo "==> 导出数据库"
docker exec nblog-mysql mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers --default-character-set=utf8mb4 nblog \
  | gzip > "${OUT}/db-${STAMP}.sql.gz"

echo "==> 打包上传目录"
# Git Bash 会把容器内路径改写成 Windows 路径，这里禁用转换
HOST_OUT="$(cygpath -w "$(cd "$OUT" && pwd)" 2>/dev/null || echo "$(cd "$OUT" && pwd)")"
MSYS_NO_PATHCONV=1 docker run --rm -v nblog_upload_data:/data:ro -v "${HOST_OUT}:/backup" alpine \
  tar czf "/backup/upload-${STAMP}.tar.gz" -C /data .

ls -lh "${OUT}" | tail -3
echo "备份完成：${OUT}/db-${STAMP}.sql.gz  ${OUT}/upload-${STAMP}.tar.gz"
