#!/usr/bin/env bash
# 自动上线：发现比当前更新的 git tag，就拉镜像、重启、验证，失败自动回滚。
#
# 为什么用「服务器拉」而不是「GitHub Actions 推」：
# 推的模式要在仓库里放 SSH 私钥与 4 个 secret，密钥一旦配错，最早的信号是
# 一次红色的 workflow，站点却停在旧版本；拉的模式不需要任何 GitHub 凭据，
# 拉不到就下一轮再试，逻辑全部在一台你能 SSH 上去的机器上。
#
# 用法：
#   ./scripts/auto-deploy.sh             正常执行（systemd timer 每 3 分钟调一次）
#   ./scripts/auto-deploy.sh --dry-run   只打印判断结果，不动任何东西
#
# 幂等：TAG 已经是最新就直接退出，所以 timer 高频跑也不会反复重启。
# 时序：tag 推上去时镜像可能还在构建，pull 失败只记一行 WAITING，下一轮继续。
set -euo pipefail

cd "$(dirname "$0")/.."

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

COMPOSE="docker-compose.prod.yml"
ENV_FILE=".env"
# 回滚过的版本会被记在这里，避免每 3 分钟重试一次坏版本
BLOCK_FILE=".deploy-blocked"
# 公开仓库，匿名 ls-remote 即可，不需要 token
REPO_URL="${REPO_URL:-https://github.com/Edgarmongo/coolpilot-platform.git}"
APP_CONTAINERS="coolpilot-server coolpilot-web coolpilot-admin coolpilot-gateway"

log() { echo "[$(date '+%F %T')] $*"; }

[ -f "$ENV_FILE" ] || { log "ERROR 找不到 $ENV_FILE"; exit 1; }
[ -f "$COMPOSE" ] || { log "ERROR 找不到 $COMPOSE"; exit 1; }

CURRENT="$(grep -m1 '^TAG=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r" ')"
if [ -z "$CURRENT" ]; then
  # 没有 TAG 时不能自动回滚到任何版本，宁可不动作
  log "ERROR $ENV_FILE 里没有 TAG=，跳过"
  exit 1
fi

# 只认 vX.Y.Z，避免把 v1.2.3-rc1 之类排序搞乱；sort -V 处理 v1.3.10 > v1.3.9
LATEST="$(git ls-remote --tags --refs "$REPO_URL" 2>/dev/null \
  | awk '{print $2}' | sed 's|refs/tags/||' \
  | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1)"

if [ -z "$LATEST" ]; then
  log "WAITING 取不到远端 tag（网络或仓库不可达），下一轮再试"
  exit 0
fi

if [ "$LATEST" = "$CURRENT" ]; then
  # 正常情况：什么都不做，也不打日志，避免日志被“无变化”刷满
  exit 0
fi

# 这个版本之前回滚过，人工修好前不要反复重试
if [ -f "$BLOCK_FILE" ] && grep -qx "$LATEST" "$BLOCK_FILE" 2>/dev/null; then
  exit 0
fi

log "发现新版本：$CURRENT -> $LATEST"

if [ "$DRY_RUN" = "1" ]; then
  log "DRY-RUN 将执行：pull $LATEST → 写 .env → up -d → 健康检查"
  exit 0
fi

# 镜像可能还在构建：pull 失败不是错误，下一轮继续。
# 只留一行原因，不把 compose 的整段失败输出写进日志——「仍在构建」是常态，
# 日志里堆满 Error/Interrupted 会让人以为是真故障。
export TAG="$LATEST"
if ! pull_out="$(docker compose -f "$COMPOSE" pull 2>&1)"; then
  reason="$(echo "$pull_out" | grep -iE 'error|unknown|denied|unauthorized|timeout' | tail -1 | cut -c1-120)"
  log "WAITING ${LATEST} 镜像还拉不到（可能仍在构建）：${reason:-pull 失败}，下一轮再试"
  exit 0
fi

# 镜像确认存在后才落盘，避免 .env 指向一个不存在的 tag，把手工 up 也搞坏
sed -i "s/^TAG=.*/TAG=${LATEST}/" "$ENV_FILE"
log "已切换 TAG=${LATEST}，重启容器"

docker compose -f "$COMPOSE" up -d --remove-orphans 2>&1 | tail -6 || true

# 健康检查：4 个应用容器都 healthy，且本机回环能用真实 Host 头拿到 200
healthy_all() {
  for name in $APP_CONTAINERS; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo missing)"
    [ "$status" = "healthy" ] || return 1
  done
  return 0
}

ok=1
for _ in $(seq 1 36); do   # 36 × 5s = 3 分钟
  if healthy_all && curl -skf -o /dev/null -H "Host: coolskyai.com" https://127.0.0.1/; then
    ok=0
    break
  fi
  sleep 5
done

if [ "$ok" != "0" ]; then
  log "ROLLBACK $LATEST 启动后未通过健康检查，回退到 $CURRENT"
  sed -i "s/^TAG=.*/TAG=${CURRENT}/" "$ENV_FILE"
  docker compose -f "$COMPOSE" up -d --remove-orphans 2>&1 | tail -4
  # 记下坏版本，否则下一轮会再拉一次、再回滚一次，形成重启循环
  echo "$LATEST" >> "$BLOCK_FILE"
  log "ROLLBACK 已回退。修好后删掉 $BLOCK_FILE 里的 $LATEST 才会再次尝试"
  log "ROLLBACK 看日志：docker compose -f $COMPOSE logs --tail=200 api"
  exit 1
fi

docker image prune -f >/dev/null 2>&1 || true
log "DONE 线上已切到 $LATEST"
