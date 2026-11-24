---
slug: crawl4ai-403-anti-bot
title: Crawl4AI 被抓 403：先分清是哪一种，再决定改哪一层
description: 403 不是一种错误，而是五种：WAF 拦截、UA 黑名单、TLS 指纹、频率超限、IP 段封禁。按代价从低到高给出五种处置、判定方法与合规边界。
category: 开源技术实践
tags: [Crawl4AI,AI 数据采集,爬虫]
---

## 先说结论

**403 不是一种错误，是五种。** 判错类型就会一直做无用功：明明是 IP 段被封，你换十次 UA 也没用；明明是频率超限，你升级代理只是把被封的地方挪了个位置。

正确的顺序是「先看响应特征分类 → 再按代价从低到高处置」，而不是上来就上代理。本文按这个顺序给方法，并在最后划清合规边界——**能抓到和可以抓是两件事**。

> 说明：配置项与端点基于 Crawl4AI v0.9.5（本站 Docker 实例实测），不同站点返回的拦截页差异很大，判定部分请以你自己的抓取日志为准。

---

## 一、先把状态码分清楚

| 拿到的东西 | 含义 | 该动哪一层 |
| --- | --- | --- |
| `403` + 普通 HTML | 服务端明确拒绝（UA、路径、权限） | 请求上下文 |
| `403` + `cf-ray` 头 / `Just a moment...` | 边缘 WAF 挑战，JS 没过 | 浏览器能力 |
| `429` | 显式限速，带 `Retry-After` | 频率 |
| `503` + `x-datadome` / 类似头 | 反爬中间件判定 | 会话与指纹 |
| Cloudflare `1020` / `1015` | 访问策略拒绝 / 限速 | IP 与频率 |
| 200 但正文是验证页 | 软拦截，最容易被误判成成功 | 内容校验 |

最后一行是最坏的情况：**你拿到 200，写进库里的却是「请开启 JavaScript」**。所以抓取流程里必须有一道内容校验（正文长度、关键字命中、结构是否存在），否则脏数据会悄悄累积。

```python
# 软拦截的最小校验：这三条任意一条不过就当失败处理
assert len(markdown) > 800
assert "Just a moment" not in markdown and "Access denied" not in markdown
assert result.html.count("<a ") >= 3
```

---

## 二、五种成因与判定方法

**① UA / 请求头缺失**
最容易验证：把同样的 URL 用真实浏览器 UA 再抓一次，通就是它。要注意只改 UA 往往不够——很多站点同时校验 `Accept-Language`、`Sec-Fetch-*`。

**② 频率超限**
特征是「前几次成功、之后全部 403/429」，且换个时间点又好了。看 `Retry-After` 头，没有就自己退避。

**③ 边缘 WAF 挑战（Cloudflare / DataDome 这类）**
特征是响应头里有 `cf-ray` / `cf-mitigated`，正文是挑战页。这一类**不是配置问题**，需要真实的浏览器执行环境。

**④ IP 段被拉黑**
特征是换 UA、换会话都没用，但换出口 IP 立刻通。云厂商的机房段经常整段在黑名单里。

**⑤ 目标站本来就不允许抓**
`robots.txt` 的 `Disallow`、页面底部的 ToS 声明。这一类不该用技术手段处理，见第四节。

---

## 三、五种处置，按代价从低到高

### 1. 降频 + 缓存（成本最低，先做这个）

九成的 403 来自「抓太快」。Crawl4AI 自带缓存与限速，先把这两件事做对：

```python
from crawl4ai import CrawlerRunConfig, CacheMode

config = CrawlerRunConfig(
    cache_mode=CacheMode.ENABLED,   # 重复 URL 直接命中缓存，不再发请求
    page_timeout=60000,
    delay_before_return_html=0.5,   # 给页面一点渲染时间
)
```

单域名并发压到 1–2，失败后按 5/15/45 秒退避。**这一步不需要任何额外成本，却能解决大部分问题。**

### 2. 补全浏览器上下文（解决「看起来像脚本」）

```python
from crawl4ai import BrowserConfig, CrawlerRunConfig

browser = BrowserConfig(
    headless=True,
    text_mode=True,                  # 关图片，快且省资源
    light_mode=True,
    use_persistent_context=True,     # 复用配置目录，Cookie 与 localStorage 不丢
    user_agent_mode="random",        # 随机 UA，按站点决定是否开
)
```

`use_persistent_context` 比每次新开上下文更像人：同一个站点第二次访问时带着 Cookie 与缓存。注意这个目录要落盘，否则重启就丢。

### 3. 会话复用（解决分页与多步交互）

翻页只抓到第一页，多半不是反爬而是没用 session：

```python
config = CrawlerRunConfig(
    session_id="list-page",
    js_only=True,
    js_code="document.querySelector('.next').click()",
)
```

同一 `session_id` 的多次抓取共享同一个浏览器上下文，代价是内存——一个 session 一个页面。用完要显式关闭，不然浏览器池会被慢慢占满。

### 4. 换出口（代价开始变高）

代理是有效手段，但成本不只是钱：延迟上升、失败率上升、日志里出现别人的历史行为记录。**先确认是 IP 段问题再用**，判定方法是同一 UA、同一会话、只换出口对比一次。

### 5. 退让：换数据来源（最被低估）

很多站点提供官方 API、RSS、sitemap，或者允许申请数据授权。**走官方渠道得到的字段更稳、不会被封、也省掉维护指纹的成本。** 当某个站点需要你持续对抗，通常说明这个数据源本身不适合这样做。

---

## 四、一个可执行的排查顺序

```
403 / 429 出现
  ├─ 看 robots.txt：Disallow 命中？（是 → 停手，走授权）
  ├─ 换真实浏览器 UA + 完整头再抓一次 → 通了 = 请求头问题
  ├─ 响应头有 cf-ray / x-datadome / 挑战页 → 需要浏览器执行环境（第 2、3 步）
  ├─ 前几次成功、之后全挂 → 频率问题（第 1 步）
  ├─ 换出口 IP 立刻通 → IP 段问题（第 4 步）
  └─ 200 但正文是验证页 → 内容校验缺失，按失败处理
```

这个顺序的价值是**每一步都能证伪**。反过来，一上来就上代理，你永远不知道真正的原因是什么。

---

## 五、合规边界

- **不绕过登录、验证码、访问控制。** Crawl4AI 提升的是采集能力，不是通行证。
- **遵守 `robots.txt` 与站点条款。** 它没有强制力，但它是「是否善意」的唯一明确信号。
- **控制速率。** 你的采集不该让对方的正常用户变慢。
- **注意许可证与再分发权。** 抓到 ≠ 有权转载。Crawl4AI 自身的 LICENSE 在 Apache 2.0 之外还追加了署名要求，详见 [Crawl4AI 商业价值分析](/blog/crawl4ai-business-value)。

写这套东西最容易越界的地方不是技术，是心态：把「被封」当成需要打败的对手。实际工程里，被封通常是一个信号——要么你太快，要么这个源不该这么用。

---

## 常见问题

### Crawl4AI 抓到 403 是反爬吗？

不一定。先分清是服务端拒绝（UA/路径）、边缘 WAF 挑战、频率超限还是 IP 段封禁。四种的解法完全不同，判错就会一直做无用功。判定方法看第二节。

### 换个 User-Agent 就能解决吗？

只能解决「UA 黑名单」这一类，而且往往不够——很多站点同时校验 `Accept-Language`、`Sec-Fetch-*` 和 TLS 特征。遇到挑战页（`Just a moment...`）时换 UA 完全无效，需要真实浏览器执行环境。

### 为什么我拿到了 200 但内容是空的？

这是软拦截：服务端返回 200，正文是「请开启 JavaScript」之类的占位页。抓取流程必须加内容校验（正文长度、关键字、结构节点），否则脏数据会静默入库。

### 代理能解决所有 403 吗？

不能，而且会把问题掩盖。代理只对「IP 段被封」有效；对频率限制、WAF 挑战、需要 JS 的站点无效。先用「同 UA、同会话、只换出口」对比一次，确认是 IP 问题再上。

**延伸阅读**：[Crawl4AI 操作手册（v0.9.5 实测）](/blog/crawl4ai-handbook)、[Crawl4AI 商业价值分析](/blog/crawl4ai-business-value)、[Crawl4AI + Hermes Agent 组合](/blog/crawl4ai-hermes-combo)

