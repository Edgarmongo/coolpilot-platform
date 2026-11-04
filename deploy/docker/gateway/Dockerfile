# ============================================================
# 统一入口（nginx 反向代理）
#
# 生产只暴露这一个容器：80 跳 443，443 按路径分发
#   /api/**   → api:8091
#   /image/** → api:8091（图片直链，数据库里存的是绝对地址）
#   /cms/**   → admin:80
#   /**       → web:8081（Nuxt SSR）
#
# 构建（上下文必须是仓库根目录）：
#   docker build -f deploy/docker/gateway/Dockerfile -t coolpilot-gateway .
# ============================================================

FROM nginx:1.27-alpine

# 首次启动时如果没有证书，用 openssl 生成自签名，保证容器能起来
RUN apk add --no-cache openssl

# 官方镜像启动时会用 envsubst 处理 /etc/nginx/templates/*.template
COPY deploy/docker/gateway/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/docker/gateway/40-bootstrap-cert.sh /docker-entrypoint.d/40-bootstrap-cert.sh
RUN chmod +x /docker-entrypoint.d/40-bootstrap-cert.sh

EXPOSE 80 443

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD wget -q --spider http://127.0.0.1/healthz || exit 1
