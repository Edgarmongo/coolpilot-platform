-- ============================================================
-- V30 两篇 P0 文章入库
--
-- 依据 docs/SEO-CONTENT-PLAN.md 第四节的选题矩阵：
--   hermes-agent-tutorial  —— 现有三篇 Hermes 文章全是进阶内容，缺「入口词」
--   crawl4ai-403-anti-bot  —— 具体故障词，竞争小、意图强
--
-- 内容写成迁移而不是后台粘贴，理由同 V29：可 review、可回滚、可复算。
-- 带 NOT EXISTS 判断，重复执行不会插出第二篇；标签同理。
--
-- 说明：first_picture 留空，前台由 AppCover 按 seed 生成封面，
--       不依赖外部图片，也不会因为图片挂了出现裂图。
-- ============================================================

-- hermes-agent-tutorial（4952 字 / 15 分钟）
INSERT INTO blog (title, slug, first_picture, content, description,
                  is_published, is_recommend, is_appreciation, is_comment_enabled,
                  create_time, update_time, views, words, read_time,
                  category_id, is_top, password, user_id)
SELECT 'Hermes Agent 入门：从构建 4.65GB 镜像到跑通第一个自动研究任务', 'hermes-agent-tutorial', '', '## 先说结论

装好 Hermes Agent 需要 **30 分钟**，跑通第一个真正干活的自动研究任务再 **30 分钟**。卡住新手的从来不是模型，是两个工程细节：

1. **主进程必须是网关（gateway）**，dashboard 只是它下面的一个受管服务，反过来启会得到一句 `Gateway inaccessible`；
2. **配置写在 `config.yaml`，不是环境变量**——它会把自己的配置写回文件，重启后覆盖你 `-e` 传的值。

下面按「能跑起来 → 能干活 → 能验证」三步走，每一步都给可复制的命令。

---

## 一、它到底是什么

一句话：**Hermes Agent 是一个带进程管理器的 Agent 运行时，不是一个聊天框。**

| 维度 | 普通聊天机器人 | Hermes Agent |
| --- | --- | --- |
| 交互 | 一问一答 | 一个任务 = 多轮工具调用，直到交付结果 |
| 能力 | 只会说话 | 70+ 工具、插件、技能库 |
| 执行环境 | 无 | 终端 6 种后端、浏览器 5 种、网页 4 种、MCP |
| 数据 | 无状态 | SQLite + FTS5、`/opt/data` 持久化 |
| 长期记忆 | 无 | 技能沉淀（把重复流程写成 SKILL.md） |

对你来说最实际的差别是第三行：**它能真的去执行命令、改文件、抓网页**，所以安装成本也比装个网页客户端高一个量级。

---

## 二、机器要求

| 项目 | 最低 | 说明 |
| --- | --- | --- |
| 磁盘 | 8 GB 可用 | 镜像本身 4.65 GB，加数据卷与日志 |
| 内存 | 4 GB | Chromium 是主要吃内存的一方 |
| Docker | 20.10+ | 需要 `--shm-size` 与自定义网络 |
| 出网 | github.com / pypi / ghcr.io | 构建时要拉 Python 3.14、Chromium、ffmpeg、Node、ripgrep |

Windows 用户注意：`docker run -v` 的路径要用 `D:/xxx` 这种写法并设 `MSYS_NO_PATHCONV=1`，否则 Git Bash 会把 `/d/xxx` 翻译成 PortableGit 目录，数据全写进匿名卷——容器里一切正常，你在宿主机什么都看不到。

---

## 三、第一步：构建镜像

官方**没有发布任何镜像**，`docker-compose.yml` 里写的是 `build: .`。所以只能拉源码自建：

```bash
git clone https://github.com/NousResearch/hermes-agent.git
cd hermes-agent
docker build -t hermes-agent:local .
```

实测产物 **4.65 GB**，机器上大约 **11 分钟**（系统包层有缓存的前提下）。

最常见的失败是 `curl exit 56 / 7 / 22`——`github.com` 的 release 下载不通，而 `codeload.github.com`、`api.github.com` 都是通的，所以源码能拉、构建却卡住。解法是把 s6-overlay 三个包下到本地再用 `COPY` 塞进构建上下文，**但 `sha256sum -c` 那行必须原样保留**：只改「怎么拿包」，不改「怎么校验」。完整步骤见 [从源码构建 4.65GB 镜像](/blog/hermes-agent-docker-build)。

---

## 四、第二步：启动（这一步最容易搞反）

```bash
docker run -d --name hermes \\
  -p 127.0.0.1:9119:9119 \\
  -v "D:/hermes-data:/opt/data" \\
  -e HERMES_DASHBOARD=1 \\
  -e HERMES_DASHBOARD_HOST=0.0.0.0 \\
  -e HERMES_DASHBOARD_PORT=9119 \\
  hermes-agent:local gateway run
```

注意最后那个 `gateway run`：**主进程是网关，dashboard 是被 s6 管理的子服务**，靠 `HERMES_DASHBOARD=1` 决定开不开。

如果写成 `... hermes-agent:local dashboard`，控制台能打开、能登录、界面正常，但一发消息就报：

```
Hermes couldn''t start the model connection: Gateway inaccessible:
service_manager_unavailable
```

因为它根本没有网关可连。日志里出现 `HERMES_DASHBOARD_READY port=9119` 才算起来——此时能看到 19 个工具、39 个技能和 20 多项侧栏功能。

---

## 五、第三步：接模型

配置分三层，优先级从高到低：

| 层 | 位置 | 放什么 |
| --- | --- | --- |
| 配置 | `~/.hermes/config.yaml` 的 `model` 段 | provider、默认模型 |
| 环境变量 | `DEEPSEEK_API_KEY` 等 | **只放密钥** |
| 命令行 | `hermes model` / 会话内 `/model` | 临时切换 |

最小配置就三行：

```yaml
model:
  provider: "deepseek"
  default: "deepseek-flash"
```

有个反直觉的点：**程序会把配置写回 `config.yaml`，而配置文件优先于环境变量**。所以你在 `-e` 里传的模型名、密码之类，重启后会被文件里的值覆盖。密钥走环境变量是对的（不该落盘），业务配置一律写文件。

另外，DeepSeek 在 2026-07-24 下架了 `deepseek-chat` 和 `deepseek-reasoner` 两个 model id，Hermes 做兼容折叠，启动时会打印一行 `Normalized model ''deepseek-chat'' to ''deepseek-flash''`。这不是你配错了。细节与实测成本见 [配置 deepseek-chat 实际跑 deepseek-flash](/blog/hermes-agent-model-cost)。

---

## 六、第四步：第一个任务

**走网关，不要用 `hermes chat -q`。** 后者在非 TTY 环境等价于 `--oneshot`，答完就退，后台复盘线程会被直接杀掉，你永远等不到技能沉淀。

```bash
curl -X POST http://127.0.0.1:8642/v1/chat/completions \\
  -H "Authorization: Bearer $API_SERVER_KEY" \\
  -H "Content-Type: application/json" \\
  -d ''{"model":"hermes-agent","messages":[{"role":"user",
       "content":"统计 /opt/data 下所有 .db 文件的数量和总大小，输出一份 Markdown 报告"}]}''
```

这是一个刻意选的小任务：它有明确的成功判据（数量、字节数）、需要多次工具调用、结果可核对。**第一次跑不要给它开放性问题**（「帮我研究一下 AI 行业」），你没法判断它做对没做对。

跑完你会拿到一份报告。这时它还是个「会调工具的模型」，还没体现 Agent 的价值——价值在下一步。

---

## 七、第五步：确认它真的在干活

三个验证，缺一个你都不知道它到底做了什么。

**① 实际调用的是哪个模型**

```bash
docker exec -u hermes -e HOME=/opt/data hermes \\
  hermes --usage-file /tmp/u.json -z "统计 /opt/data 下 .db 文件数量"
docker exec hermes cat /tmp/u.json
```

JSON 里的 `model` 字段是**实际调用**的名字，不是配置里写的那个。注意 `--usage-file` 只在 `-z` 单次模式生效，`chat -q` 不写文件，容易读到上一次的旧数据还以为没变。

**② 花了多少钱**

实测两个量级：纯问答 22,592 input / 95 output，**¥0.0014**；带 4 次工具调用 46,876 input / 574 output，**¥0.0049**。固定开销约 2.2 万 input token（系统提示 + 工具 schema + 技能索引），每多一次 API 调用都要再付一遍——所以「按 token 加价」这个生意不成立。

**③ 技能有没有沉淀**

```bash
docker exec hermes sh -lc ''find /opt/data/skills -name SKILL.md | wc -l''
```

这个数字不会自己变。**触发条件是单轮工具调用 ≥ 10 次**，而普通任务通常只有 4 次；日常问答永远不会沉淀。想立刻看到效果，用 `/refine` 手动触发：

```bash
curl -X POST http://127.0.0.1:8642/v1/chat/completions \\
  -H "Authorization: Bearer $API_SERVER_KEY" \\
  -d ''{"model":"hermes-agent","messages":[{"role":"user","content":"/refine 沉淀刚才的流程"}]}''
```

实测一次请求产出一个 5,998 字节的 `SKILL.md`，并把新技能挂进已有技能的 `related_skills` 里。为什么默认不触发、源码在哪两行，见 [自进化为什么不生效](/blog/hermes-agent-skill-loop)。

---

## 八、五个必踩的坑

| 现象 | 原因 | 解法 |
| --- | --- | --- |
| `Gateway inaccessible` | 主进程启成了 dashboard | 用 `gateway run` 当主进程 |
| 构建 `curl exit 56/7/22` | github.com release 不通 | 本地下载 + SHA256 校验 + 只改取包方式 |
| 挂载目录里没数据 | Git Bash 改写了路径 | `MSYS_NO_PATHCONV=1` + `D:/` 写法 |
| 改密码不生效 | 配置文件优先于环境变量 | 用它自己的 `hash_password` 写 config |
| 技能数一直不变 | 阈值数的是单轮工具调用 | 单轮 ≥ 10 次调用，或 `/refine` |

---

## 九、什么时候别用它

诚实一点：如果你的需求是「问一句答一句」，它比网页版模型客户端贵、慢、还难装。它的成本只有在你需要**多步执行 + 可复用流程 + 本地数据**时才划算。

另一个边界是数据合规：它会执行命令、读写文件，跑在能访问内网的机器上就意味着一个额外的攻击面。生产环境别把 dashboard 裸露在公网——绑定 `0.0.0.0` 时它强制要求鉴权，没有免登录选项。

---

## 常见问题

### Hermes Agent 有官方 Docker 镜像吗？

没有。官方 `docker-compose.yml` 写 `build: .`，CI 也只跑测试不推镜像。必须拉源码自建，产物 4.65 GB、构建约 11 分钟。

### 装好之后打不开控制台怎么办？

先确认容器在跑 `gateway run` 而不是 `dashboard`，再来是 `HERMES_DASHBOARD=1` 有没有设。绑定 `0.0.0.0` 时必须配 Basic Auth 用户名密码，否则控制台不启动。

### 为什么它从来不自己生成新技能？

触发条件是「单轮工具调用迭代 ≥ 10 次」，且计数器在回合结束时判定、只属于当前实例，会话一换就归零。普通问答只有 4 次左右，永远到不了。用 `/refine` 手动触发最直接。

### 一次任务大概多少钱？

按本机实测：纯问答约 ¥0.0014（6 秒），带 4 次工具调用约 ¥0.0049（11 秒）。贵的不是单价而是固定开销——每次调用都要重新付约 2.2 万 input token 的系统提示与工具 schema。

**延伸阅读**：[从源码构建 Hermes Agent 的 4.65GB 镜像](/blog/hermes-agent-docker-build)、[Hermes Agent 自进化为什么默认不触发](/blog/hermes-agent-skill-loop)、[配置 deepseek-chat 实际跑 deepseek-flash](/blog/hermes-agent-model-cost)
', '官方没有镜像，从构建容器到跑通第一个自动研究任务的完整五步。含主进程必须是 gateway、配置优先于环境变量这两处最容易卡住的地方。',
       b'1', b'0', b'0', b'1',
       NOW(), NOW(), 0, 4952, 15,
       8, b'0', '', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM blog WHERE slug = 'hermes-agent-tutorial');

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, 59 FROM blog b
WHERE b.slug = 'hermes-agent-tutorial'
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = 59);

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, 73 FROM blog b
WHERE b.slug = 'hermes-agent-tutorial'
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = 73);

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, 60 FROM blog b
WHERE b.slug = 'hermes-agent-tutorial'
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = 60);

-- crawl4ai-403-anti-bot（3420 字 / 10 分钟）
INSERT INTO blog (title, slug, first_picture, content, description,
                  is_published, is_recommend, is_appreciation, is_comment_enabled,
                  create_time, update_time, views, words, read_time,
                  category_id, is_top, password, user_id)
SELECT 'Crawl4AI 被抓 403：先分清是哪一种，再决定改哪一层', 'crawl4ai-403-anti-bot', '', '## 先说结论

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
    js_code="document.querySelector(''.next'').click()",
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
', '403 不是一种错误，而是五种：WAF 拦截、UA 黑名单、TLS 指纹、频率超限、IP 段封禁。按代价从低到高给出五种处置、判定方法与合规边界。',
       b'1', b'0', b'0', b'1',
       NOW(), NOW(), 0, 3420, 10,
       8, b'0', '', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM blog WHERE slug = 'crawl4ai-403-anti-bot');

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, 47 FROM blog b
WHERE b.slug = 'crawl4ai-403-anti-bot'
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = 47);

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, 52 FROM blog b
WHERE b.slug = 'crawl4ai-403-anti-bot'
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = 52);

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, 51 FROM blog b
WHERE b.slug = 'crawl4ai-403-anti-bot'
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = 51);

