#!/usr/bin/env bash
# 备份数据库 + 上传目录（本地存储方案下的必需品）
#
# 用法：./scripts/backup.sh           备份到 ./backups
#      ./scripts/backup.sh /mnt/nas   备份到指定目录
#      KEEP_DAYS=30 ./scripts/backup.sh  只保留最近 30 天（默认 14）
#
# 建议挂 crontab：0 3 * * * cd /path/to/coolpilot-platform && ./scripts/backup.sh
# 或用仓库里的 systemd timer：deploy/systemd/coolpilot-backup.{service,timer}
set -euo pipefail
cd "$(dirname "$0")/.."

OUT="${1:-backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
# shellcheck disable=SC1091
set -a; . ./.env; set +a

echo "==> 导出数据库"
docker exec coolpilot-mysql mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers --default-character-set=utf8mb4 coolpilot \
  | gzip > "${OUT}/db-${STAMP}.sql.gz"

echo "==> 打包上传目录"
# Git Bash 会把容器内路径改写成 Windows 路径，这里禁用转换
HOST_OUT="$(cygpath -w "$(cd "$OUT" && pwd)" 2>/dev/null || echo "$(cd "$OUT" && pwd)")"
MSYS_NO_PATHCONV=1 docker run --rm -v coolpilot_upload_data:/data:ro -v "${HOST_OUT}:/backup" alpine \
  tar czf "/backup/upload-${STAMP}.tar.gz" -C /data .

echo "==> 清理 ${KEEP_DAYS} 天前的旧备份"
find "$OUT" -maxdepth 1 -type f -name 'db-*.sql.gz' -mtime "+${KEEP_DAYS}" -print -delete
find "$OUT" -maxdepth 1 -type f -name 'upload-*.tar.gz' -mtime "+${KEEP_DAYS}" -print -delete

ls -lh "${OUT}" | tail -3
echo "备份完成：${OUT}/db-${STAMP}.sql.gz  ${OUT}/upload-${STAMP}.tar.gz"
