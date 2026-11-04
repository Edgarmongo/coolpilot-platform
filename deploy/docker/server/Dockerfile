# syntax=docker/dockerfile:1.6
# ============================================================
# blog-api 开发镜像：多阶段构建（Maven 编译 → JRE 运行）
# 构建上下文为仓库根目录，便于 docker-compose 统一引用
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 使用阿里云镜像加速依赖下载（不影响宿主机 Maven 配置）
COPY deploy/docker/server/settings.xml /build/settings.xml

# 先只拷 pom，利用层缓存避免每次改代码都重新下载依赖
COPY coolpilot-server/pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -s /build/settings.xml -DskipTests dependency:go-offline || true

COPY coolpilot-server/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /build/settings.xml -DskipTests package

# ---------------- 运行阶段 ----------------
FROM eclipse-temurin:21-jre

WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"

COPY --from=build /build/target/*.jar app.jar

# 上传目录（compose 挂载为持久卷）
RUN mkdir -p /data/upload

# 健康检查需要 curl（基础镜像里没有 wget/curl）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8091

# 存活探针只证明进程能响应，不查 MySQL/Redis；
# 依赖健康用 /health（返回 200/503），交给监控而不是容器重启策略
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8091/health/live || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
