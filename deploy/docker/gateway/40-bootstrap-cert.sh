#!/bin/sh
# 首次启动没有证书时生成自签名，让容器先跑起来。
# 正式证书由 scripts/renew-cert.sh（certbot）签发后覆盖同名文件，再 reload nginx。
set -e

CERT_DIR=/etc/nginx/certs
DOMAIN="${SERVER_NAME%% *}"

if [ -z "$DOMAIN" ]; then
    DOMAIN=localhost
fi

if [ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ]; then
    echo "[gateway] 使用已有证书：$CERT_DIR/fullchain.pem"
    exit 0
fi

echo "[gateway] 未找到证书，生成自签名证书（CN=$DOMAIN，仅供首次启动/内网验证）"
mkdir -p "$CERT_DIR"
openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
    -keyout "$CERT_DIR/privkey.pem" \
    -out "$CERT_DIR/fullchain.pem" \
    -subj "/CN=$DOMAIN" \
    -addext "subjectAltName=DNS:$DOMAIN" 2>/dev/null
chmod 600 "$CERT_DIR/privkey.pem"
echo "[gateway] 自签名证书已生成。上线前请用真实证书替换 $CERT_DIR 下的同名文件。"
