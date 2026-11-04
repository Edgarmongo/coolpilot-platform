#!/usr/bin/env bash
# 用 certbot 申请/续期 Let's Encrypt 证书，并让 gateway 重新加载
#
# 前置：
#   1. 域名已解析到本机，80 端口可从公网访问（HTTP-01 校验走 80）
#   2. gateway 已启动（docker compose -f docker-compose.prod.yml up -d）
#   3. 设置了 CERT_EMAIL（或作为第二个参数传入）
#
# 用法：
#   CERT_EMAIL=you@example.com ./scripts/renew-cert.sh            # 域名取 .env 的 SERVER_NAME
#   ./scripts/renew-cert.sh coolskyai.com you@example.com
#
# 建议挂 crontab（每月 1 号 04:30，certbot 自身也会判断是否快到期）：
#   30 4 1 * * cd /opt/coolpilot-platform && CERT_EMAIL=you@example.com ./scripts/renew-cert.sh >> logs/cert.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
    # shellcheck disable=SC1091
    set -a; . ./.env; set +a
fi

DOMAIN="${1:-${SERVER_NAME:-}}"
DOMAIN="${DOMAIN%% *}"
EMAIL="${2:-${CERT_EMAIL:-}}"

if [ -z "$DOMAIN" ]; then
    echo "用法：CERT_EMAIL=you@example.com $0 [域名] [邮箱]" >&2
    exit 1
fi
if [ -z "$EMAIL" ]; then
    echo "缺少邮箱：请设置 CERT_EMAIL 或作为第二个参数传入" >&2
    exit 1
fi

mkdir -p certs/letsencrypt certbot/www

echo "==> 申请/续期证书：$DOMAIN"
# 容器内路径不能让 Git Bash 改写，加 MSYS_NO_PATHCONV=1
MSYS_NO_PATHCONV=1 docker run --rm \
    -v "$PWD/certs/letsencrypt:/etc/letsencrypt" \
    -v "$PWD/certbot/www:/var/www/certbot" \
    certbot/certbot certonly --webroot -w /var/www/certbot \
    -d "$DOMAIN" \
    --email "$EMAIL" --agree-tos --no-eff-email --non-interactive --keep-until-expiring

LIVE="certs/letsencrypt/live/$DOMAIN"
if [ ! -f "$LIVE/fullchain.pem" ]; then
    echo "签发失败：$LIVE/fullchain.pem 不存在" >&2
    exit 1
fi

echo "==> 拷贝到 gateway 挂载目录"
cp "$LIVE/fullchain.pem" certs/fullchain.pem
cp "$LIVE/privkey.pem" certs/privkey.pem
chmod 600 certs/privkey.pem

echo "==> 重新加载 gateway"
docker exec coolpilot-gateway nginx -s reload

echo "完成：证书有效期至 $(openssl x509 -enddate -noout -in certs/fullchain.pem | cut -d= -f2)"
