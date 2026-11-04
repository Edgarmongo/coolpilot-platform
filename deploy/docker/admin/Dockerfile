# ============================================================
# 管理后台生产镜像：Vite 构建 → nginx 静态托管
#
# 与开发镜像（deploy/docker/web/Dockerfile）的区别：
#   开发镜像跑 vite dev server，靠它的 proxy 转发 /api；
#   生产镜像只有静态文件 + nginx 反代，没有 node 进程，内存和体积都小得多。
#
# 构建（上下文必须是仓库根目录）：
#   docker build -f deploy/docker/admin/Dockerfile -t coolpilot-admin .
# ============================================================

FROM node:22-alpine AS build
WORKDIR /app

# 先装依赖，利用层缓存：只改源码时不重复下载 node_modules
COPY coolpilot-admin/package*.json ./
RUN npm ci --no-audit --no-fund

COPY coolpilot-admin/ ./
RUN npm run build

# ---------------- 运行阶段 ----------------
FROM nginx:1.27-alpine

COPY deploy/docker/admin/nginx.conf /etc/nginx/conf.d/default.conf

# 产物放到 html/cms/ 下：vite 的 base 是 /cms/，
# 所以 URL /cms/assets/x.js 必须对应磁盘上的 html/cms/assets/x.js。
# 放在 html 根目录会导致 nginx 找不到 /cms/index.html 而内部重定向死循环（500）。
COPY --from=build /app/dist /usr/share/nginx/html/cms

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD wget -q --spider http://127.0.0.1/cms/ || exit 1
