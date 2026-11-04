# ============================================================
# 前端开发镜像（blog-view / blog-cms 共用）
# 依赖装在镜像里，源码运行时 bind mount 覆盖，实现热更新
# 构建参数 APP_DIR 选择具体应用目录
# ============================================================

FROM node:22-alpine

WORKDIR /app

# Windows 下 bind mount 的文件事件可能不触发，用轮询保证热更新可靠
ENV CHOKIDAR_USEPOLLING=true \
    TZ=Asia/Shanghai

ARG APP_DIR=coolpilot-web
COPY ${APP_DIR}/package*.json ./
RUN npm install --no-audit --no-fund

# 源码在运行时挂载；若未挂载则使用镜像内副本
COPY ${APP_DIR}/ ./

EXPOSE 8081 8079

CMD ["npx", "vue-cli-service", "serve", "--host", "0.0.0.0"]
