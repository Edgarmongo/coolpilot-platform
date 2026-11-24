#!/usr/bin/env bash
# 把站点 URL 推给 IndexNow（Bing / Yandex / Seznam / Naver 共用），不覆盖 Google。
#
# 为什么需要这个脚本：Google 与 Bing 的 sitemap ping 端点都已停用，
# 想让新内容当天被发现，要么在 Search Console 手工提交，要么走 IndexNow。
# IndexNow 不需要账号，只要站点根目录能访问到 key 文件即可。
#
# 用法：
#   ./scripts/indexnow.sh                 # 提交 sitemap 里的全部 URL
#   ./scripts/indexnow.sh <url> [url...]  # 只提交指定 URL
#
# 前置：coolpilot-web/public/<KEY>.txt 可被公网访问（内容就是 KEY 本身）
set -euo pipefail

HOST="coolskyai.com"
KEY="ce422a5aafd5dcc01f323b156624f3fa"
KEY_LOCATION="https://${HOST}/${KEY}.txt"

if [ "$#" -gt 0 ]; then
  URLS=("$@")
else
  # sitemap 是 URL 的唯一来源，避免手工维护第二份清单
  mapfile -t URLS < <(curl -fsSL "https://${HOST}/sitemap.xml" \
    | grep -oE '<loc>[^<]+</loc>' \
    | sed -e 's|<loc>||' -e 's|</loc>||')
fi

if [ "${#URLS[@]}" -eq 0 ]; then
  echo "没有取到任何 URL，先确认 sitemap 可访问" >&2
  exit 1
fi

echo "提交 ${#URLS[@]} 条 URL 到 IndexNow..."

# 拼 JSON 用 python，避免手写逗号拼串出错；Windows 的 Git Bash 只有 python
PY=$(command -v python3 || command -v python)
BODY=$(printf '%s\n' "${URLS[@]}" | "$PY" -c '
import json, sys
urls = [line.strip() for line in sys.stdin if line.strip()]
print(json.dumps({"host": sys.argv[1], "key": sys.argv[2],
                  "keyLocation": sys.argv[3], "urlList": urls}, ensure_ascii=False))
' "$HOST" "$KEY" "$KEY_LOCATION")

# 期望 200/202：202 表示已接收、等待校验 key 文件
code=$(curl -sS -o /tmp/indexnow.out -w '%{http_code}' \
  -X POST 'https://api.indexnow.org/indexnow' \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary "$BODY")

echo "HTTP ${code}"
cat /tmp/indexnow.out 2>/dev/null || true
[ "$code" = "200" ] || [ "$code" = "202" ] || exit 1
