#!/usr/bin/env bash
# 前端依赖变更后重建（npm 包增删时必用）
#
# 背景：compose 用命名卷挂 node_modules，避免 Windows 下 bind mount 的跨平台二进制问题。
# 但命名卷**只在空卷时**从镜像复制内容，卷里留着旧依赖时，新镜像装的包会被遮蔽，
# 表现为 "Cannot find package 'xxx'"。所以这里先删容器、再删卷、再重建。
#
# 用法：./scripts/rebuild-web.sh cms
#      ./scripts/rebuild-web.sh view
#      ./scripts/rebuild-web.sh all
set -euo pipefail
cd "$(dirname "$0")/.."

target="${1:-all}"

rebuild_one() {
	local service="$1"
	echo "==> 重建 ${service}（先清理容器与 node_modules 卷）"
	docker compose rm -sf "${service}" >/dev/null 2>&1 || true
	docker volume rm "coolpilot_${service}_node_modules" >/dev/null 2>&1 || true
	docker compose up -d --build "${service}"
}

case "${target}" in
	cms|view) rebuild_one "${target}" ;;
	all)
		rebuild_one view
		rebuild_one cms
		;;
	*)
		echo "用法：$0 {cms|view|all}"
		exit 1
		;;
esac

docker compose ps
