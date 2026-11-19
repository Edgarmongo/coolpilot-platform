#!/usr/bin/env python3
"""代码来源相似度核查。

与基线 tag 逐文件比对，给出「仍是上游原文 / 已改写 / 已重写」的分布，
以及每个文件的最长连续相同行数。

用法：
    python docs/scripts/similarity.py                    # 默认基线 v0.1.0-baseline
    python docs/scripts/similarity.py --base v1.0.0      # 换基线
    python docs/scripts/similarity.py --verbose          # 列出每个文件
    python docs/scripts/similarity.py --only util        # 只看路径含 util 的

判定口径（与 CODE-PROVENANCE.md 一致）：
    相似度 >= 0.95  逐字一致（仍是上游原文）
    0.70 ~ 0.95     小幅修改（衍生）
    0.30 ~ 0.70     大幅改写
    < 0.30          重写（达标）

退出码：达标文件数未增加时返回 1，可用于 CI 卡口。
"""

import argparse
import collections
import difflib
import os
import subprocess
import sys

BASELINE_PREFIX = "blog-api/src/main/java/top/naccl/"
CURRENT_ROOT = "coolpilot-server/src/main/java"


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"git {' '.join(args)} 失败: {result.stderr.strip()}")
    return result.stdout


def baseline_files(base):
    listing = git("ls-tree", "-r", "--name-only", base).splitlines()
    return [p for p in listing if p.startswith(BASELINE_PREFIX) and p.endswith(".java")]


def current_files():
    found = []
    for root, _, names in os.walk(CURRENT_ROOT):
        for name in names:
            if name.endswith(".java"):
                found.append(os.path.join(root, name).replace("\\", "/"))
    return found


def longest_common_run(old_text, new_text):
    """最长连续相同行数，忽略空行、注释与 import。"""
    def clean(text):
        out = []
        for line in text.splitlines():
            s = line.strip()
            if not s or s.startswith(("import ", "package ", "*", "/*", "//", "*/")):
                continue
            out.append(s)
        return out

    matcher = difflib.SequenceMatcher(None, clean(old_text), clean(new_text))
    return max((b.size for b in matcher.get_matching_blocks()), default=0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="v0.1.0-baseline")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--only", default="")
    args = parser.parse_args()

    current = current_files()
    by_name = collections.defaultdict(list)
    for path in current:
        by_name[os.path.basename(path)].append(path)

    buckets = collections.OrderedDict(
        [("identical", []), ("minor", []), ("major", []), ("rewritten", []), ("removed", [])]
    )
    baseline_total_lines = 0

    for bp in baseline_files(args.base):
        rel = bp.replace(BASELINE_PREFIX, "")
        if args.only and args.only not in rel:
            continue
        src = git("show", f"{args.base}:{bp}")
        baseline_total_lines += src.count("\n")

        candidates = by_name.get(os.path.basename(bp), [])
        if not candidates:
            buckets["removed"].append((rel, src.count("\n"), 0.0, 0, "-"))
            continue

        best = (0.0, None, 0)
        for cp in candidates:
            cur_text = open(cp, encoding="utf-8", errors="ignore").read()
            ratio = difflib.SequenceMatcher(None, src, cur_text).ratio()
            if ratio > best[0]:
                best = (ratio, cp, longest_common_run(src, cur_text))

        ratio, path, run = best
        key = (
            "identical" if ratio >= 0.95
            else "minor" if ratio >= 0.70
            else "major" if ratio >= 0.30
            else "rewritten"
        )
        buckets[key].append((rel, src.count("\n"), ratio, run, path.replace(CURRENT_ROOT + "/", "")))

    labels = {
        "identical": "逐字一致（仍是上游原文）",
        "minor": "小幅修改（衍生）",
        "major": "大幅改写",
        "rewritten": "重写（达标）",
        "removed": "已删除",
    }
    print(f"基线: {args.base}\n")
    print(f"{'分类':<24}{'文件':>6}{'行数':>8}")
    print("-" * 40)
    for key, items in buckets.items():
        print(f"{labels[key]:<24}{len(items):>6}{sum(i[1] for i in items):>8}")
    print("-" * 40)
    print(f"{'合计':<24}{sum(len(v) for v in buckets.values()):>6}{baseline_total_lines:>8}")

    untouched = len(buckets["identical"])
    done = len(buckets["rewritten"])
    print(f"\n重写进度: {done} / {done + untouched + len(buckets['minor']) + len(buckets['major'])}")

    if args.verbose:
        for key in ("identical", "minor", "major"):
            if not buckets[key]:
                continue
            print(f"\n--- {labels[key]} ---")
            for rel, lines, ratio, run, path in sorted(buckets[key], key=lambda x: -x[1]):
                print(f"  {ratio:.2f}  连续{run:>3}行  {lines:>4}行  {rel}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
