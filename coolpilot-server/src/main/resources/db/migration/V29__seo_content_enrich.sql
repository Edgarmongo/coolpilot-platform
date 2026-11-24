-- ============================================================
-- V29 SEO/GEO 内容改造：摘要、常见问题、正文内链
--
-- 依据 docs/SEO-CONTENT-PLAN.md（2026-11-24）落地，覆盖全部 9 篇文章：
--   1. 重写 description：原来存的是正文首段硬截断（80~150 字），
--      直接进 meta description 与 og:description，搜索结果里会被截掉半句。
--   2. 追加 H2「常见问题」+ 3~4 组 H3 问答：AI 引擎检索时匹配的是问句形式，
--      前端会据此输出 FAQPage 结构化数据（pages/blog/[id].vue）。
--   3. 正文内链：每篇至少 3 条锚文本链接，打通主题簇。
--
-- 说明：content 存的是 Markdown，读取时由 MarkdownUtils 转 HTML，
--       所以这里直接追加 Markdown 文本，不需要写 <h2>/<p>。
--       条件里带了 NOT LIKE 判断，重复执行不会叠加两份问答。
-- ============================================================

-- vps-ssh-hardening
UPDATE blog SET description = '53 天、44,256 次失败登录。这份实录给出正确的 SSH 加固顺序、sshd「先读到为准」的坑、fail2ban 白名单，以及三次把自己锁在门外的救援。', update_time = NOW()
WHERE slug = 'vps-ssh-hardening';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### 怎么看自己的 VPS 被暴力破解了多少次？

Ubuntu 上执行 `grep -c "Failed password" /var/log/auth.log`，或用 `lastb | wc -l` 看失败登录库。我这台跑了 53 天的机器是 44,256 次，前五名里四个 IP 同属一个 /24 网段，是典型的僵尸网络轮换地址。数字上万属于常态，不代表已被攻破，但说明「22 端口 + 密码登录」这个组合必须换掉。

### 禁用密码登录后怎么防止把自己锁死？

三条纪律：先跑 `ssh -o PreferredAuthentications=publickey -o PasswordAuthentication=no` 确认密钥真的能登，再关密码；动 sshd 之前先进一次 VNC 控制台确认备用通道可用；把 reload 放进 `if sshd -t` 的条件里，语法失败就删掉配置文件自动回滚。

### 改了 sshd 配置为什么没生效？

OpenSSH 多数指令是「先读到的值生效」，不是后者覆盖前者。`sshd_config` 第 12 行就 Include 了 `sshd_config.d/`，于是 `50-cloud-init.conf` 里的 `PasswordAuthentication yes` 先于你的 `99-*.conf` 被读到。用 `sshd -T` 看生效值，再把文件名改成 `00-` 开头。

### fail2ban 和 ufw 要一起用吗？

要，但不能在同一条命令里改——两者都会影响 SSH，同时变动就分不清是谁封的。另外 Docker 发布端口会直接写 iptables，绕过 ufw，需要 ufw-docker 才管得住，这一点在这台机器上部署 [Hermes Agent 的 Docker 镜像](/blog/hermes-agent-docker-build)时会直接撞上。

**延伸阅读**：[从源码构建 Hermes Agent 的 4.65GB 镜像](/blog/hermes-agent-docker-build)、[Hermes Agent 自进化为什么默认不触发](/blog/hermes-agent-skill-loop)、[Crawl4AI 也跑在这台机器上：0.9.5 操作手册](/blog/crawl4ai-handbook)'), update_time = NOW()
WHERE slug = 'vps-ssh-hardening' AND content NOT LIKE '%常见问题%';

-- crawl4ai-business-value
UPDATE blog SET description = 'Crawl4AI 的卖点不是爬虫本身。本文核验官方 Cloud 定价与许可证署名条款，拆解六种商业模式，给出 8.5/10 评分与 Java + Python 分层路径。', update_time = NOW()
WHERE slug = 'crawl4ai-business-value';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### Crawl4AI 免费吗？商业使用有 license 限制吗？

代码免费，但不是纯 Apache 2.0。仓库 LICENSE 在 Apache 2.0 之后追加了 Attribution Requirement，要求所有分发、公开发布与衍生产品在显著位置展示一句固定署名（含作者和项目地址）。做 SaaS 时最容易漏，建议放 About/Credits 页或 NOTICE 文件，商业分发前让法务确认。

### 能替代哪些付费采集服务？

替代的是通用抓取层：单页转 Markdown、批量采集、结构化抽取。官方 Crawl4AI Cloud 的定价给了参照——Free 1,000 credits/月，Starter $10 给 10,000 credits，普通页 0.1 credit、真实浏览器页 0.5 credit，失败调用不收费。自建的价值在数据不出内网和定制清洗，不在单价。

### 适合做 RAG 的语料管道吗？

适合，它的主菜就是 Markdown 生成：一次抓取产出多份结果，配合 PruningContentFilter 去掉导航和页脚，直接喂给切分器。具体端点与过滤器参数见 [Crawl4AI 操作手册](/blog//crawl4ai-handbook)。要注意中文站大量用 div 排版，表格抽不出来时得换 LLM 抽取策略。

### 大规模采集的成本瓶颈在哪？

在浏览器，不在网络。真实浏览器页 0.5 credit、普通页 0.1 credit，差五倍；自建时瓶颈是 Chromium 的内存与并发，要给浏览器池设上限、用 cache_mode 复用、静态页走 HTTP 直取。合规退避与代理成本则常被低估。

**延伸阅读**：[Crawl4AI 操作手册（v0.9.5 实测）](/blog/crawl4ai-handbook)、[从跑起来到能改造的研究路线](/blog/crawl4ai-research-route)、[Crawl4AI + Hermes Agent 组合](/blog/crawl4ai-hermes-combo)'), update_time = NOW()
WHERE slug = 'crawl4ai-business-value' AND content NOT LIKE '%常见问题%';

-- crawl4ai-research-route
UPDATE blog SET description = '部署一个爬虫只要十分钟，真正掌握需要路线。八阶段研究法：Docker 基线 → 基础爬取 → 动态页 → 提取 → 深爬 → 源码，含 20 个源码研究对象。', update_time = NOW()
WHERE slug = 'crawl4ai-research-route';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### 读 Crawl4AI 源码该从哪个模块开始？

由外向内：先看项目目录与 Docker 启动入口，再进 API Server 和路由，然后才到核心 Crawler → Browser Manager → Page 加载 → 内容提取 → Markdown 生成。随机翻 Python 文件是效率最低的方式，因为同一个能力往往横跨三层。

### 二次开发要注意哪些耦合点？

三个：配置对象（BrowserConfig / CrawlerRunConfig 贯穿全链路，改字段等于改公共契约）、Markdown 生成器（下游清洗与分块都依赖它的输出形状）、浏览器生命周期（Browser Manager 管池化，绕开它自建浏览器会打乱并发控制）。

### 版本升级会破坏哪些自定义？

主要是三处：REST 端点的入参包装（`type/params` 约定）、默认过滤器行为（fit 剪枝阈值）、MCP 工具的参数名。0.9.x 里这些都有过调整，所以每个自定义都要配一个能一键重跑的验收脚本。

### 有没有必要 fork？

多数情况不必。先用 Hook 和自定义 extraction_strategy 覆盖；只有当你要改浏览器调度或 Markdown 输出结构时才值得 fork，因为这两处一动就跟上游分叉。许可证还有署名要求，fork 后要一并继承。

**延伸阅读**：[Crawl4AI 0.9.5 操作手册](/blog/crawl4ai-handbook)、[Crawl4AI 商业价值分析](/blog/crawl4ai-business-value)、[这套研究的整体设计：24 篇内容体系](/blog/web-intelligence-lab)'), update_time = NOW()
WHERE slug = 'crawl4ai-research-route' AND content NOT LIKE '%常见问题%';

-- crawl4ai-handbook
UPDATE blog SET description = 'Crawl4AI 0.9.5 实测手册：Docker 部署的三条硬约束、REST 端点全表、Markdown 生成与内容过滤器、结构化抽取与缓存策略，逐条标注实测。', update_time = NOW()
WHERE slug = 'crawl4ai-handbook';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### Crawl4AI 怎么安装？

最省事是 Docker：`unclecode/crawl4ai` 镜像，0.9.5 实测可用。必须设 `CRAWL4AI_API_TOKEN`——不设的时候服务只绑容器内回环地址，宿主端口映射返回 `Empty reply from server`，而 `docker ps` 还显示 healthy。`shm_size` 也要 ≥1G，否则 Chromium 标签页频繁崩。

### 抓取结果怎么转成 Markdown？

直接 `POST /md {"url":..., "f":"raw"}`，要全字段就 `POST /crawl`。`f=fit` 会剪枝，短页面容易剪成空；`f=bm25` 必须给 `q`，否则内容极短。所以调试期统一先用 `raw`，确认抓到了再上过滤器。

### 需要浏览器依赖吗？无头模式怎么配？

用 Docker 镜像就不用自己装 Chromium。库模式才需要本地浏览器，配置走 `BrowserConfig(headless=True)`。动态站还要配 `wait_for`、`scan_full_page`、`process_iframes`；分页必须用 `session_id` 加 `js_only=True`，否则永远只抓到第一页。

### 怎么避免被反爬？

先降特征再谈代理：`text_mode` / `light_mode` 提速、`cache_mode=ENABLED` 减少重复请求、按站点加退避。真要过强防护时，浏览器指纹和 IP 池才是瓶颈，而这一步已经进入合规边界——Crawl4AI 提升的是采集能力，不等于可以绕过访问控制。

**延伸阅读**：[Crawl4AI 值不值得商业化](/blog/crawl4ai-business-value)、[八个阶段的研究路线](/blog/crawl4ai-research-route)、[把采集接进 Agent：Crawl4AI + Hermes](/blog/crawl4ai-hermes-combo)'), update_time = NOW()
WHERE slug = 'crawl4ai-handbook' AND content NOT LIKE '%常见问题%';

-- crawl4ai-hermes-combo
UPDATE blog SET description = 'Crawl4AI 取数、Hermes Agent 用数。组合真正新增的能力只有批量、深爬、清洗、结构化四项，本文给出 MCP 接法与五个落地方向。', update_time = NOW()
WHERE slug = 'crawl4ai-hermes-combo';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### 两者怎么分工？谁负责采集、谁负责决策？

Crawl4AI 是采集引擎：拿 HTML、出 Markdown、做 CSS/XPath 或 LLM 结构化抽取。Hermes Agent 是执行引擎：规划任务、调工具、串多步、交付结果。分界线是「取数」与「用数」。采集侧的参数细节见 [Crawl4AI 操作手册](/blog//crawl4ai-handbook)。

### 组合后到底新增了什么能力？

新增的是工程能力，不是「能不能访问网页」。Hermes 本身就有 40+ 工具，含网页搜索与云浏览器。Crawl4AI 补的是批量、深爬、内容清洗、结构化抽取这四项；所以这个组合应该按这四项来定位，笼统说「增强网页能力」是站不住的。

### 端到端延迟和成本大概多少？

按本机实测口径：Hermes 纯问答约 6 秒、$0.00019（¥0.0014）；带 4 次工具调用约 11 秒、$0.00068（¥0.0049）。Crawl4AI 侧主要开销是浏览器启动与页面等待，单页通常 2–10 秒。真正贵的是 LLM 抽取，所以按页计费时要 CSS 优先、LLM 兜底。成本拆解见 [模型别名与计费口径](/blog//hermes-agent-model-cost)。

### 最需要警惕什么？

Prompt 注入。抓回来的网页内容会直接进入模型上下文，恶意页面可以埋「忽略之前的指令」。要做的隔离是：采集结果只作为数据字段、不作为指令；高危动作（写文件、发请求）加人工确认；对 URL 与输出做白名单校验。

**延伸阅读**：[Hermes Agent 自进化实测](/blog/hermes-agent-skill-loop)、[从源码构建 Hermes Agent 镜像](/blog/hermes-agent-docker-build)、[Crawl4AI 0.9.5 操作手册](/blog/crawl4ai-handbook)'), update_time = NOW()
WHERE slug = 'crawl4ai-hermes-combo' AND content NOT LIKE '%常见问题%';

-- web-intelligence-lab
UPDATE blog SET description = '用 24 篇文章把 Crawl4AI 与 Hermes Agent 研究透：九个研究问题、六阶段路线、十四组联合实验，以及一个改变原定路线的发现。', update_time = NOW()
WHERE slug = 'web-intelligence-lab';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### 这套研究的方法论是什么？

三步：先定研究问题（九个），再定证据标准（每条结论标注【实测】或【文档】，未验证的不冒充已验证），最后用 A/B/C 对照实验验证能力差异。产出不是「跑通了」，而是可复现的实验编号 + 源码位置 + 商业结论。

### 为什么选这两个项目？

一个负责取数（Crawl4AI），一个负责用数（Hermes Agent），正好覆盖「自动研究系统」的两端；而且两者都有可读源码和活跃生态，适合做源码级研究，而不是只会调 API。

### 24 篇文章是怎么排的？

按能力递进而非按功能罗列：部署基线 → 基础爬取 → 动态页 → 提取 → 深爬 → 源码 → 商业评估，每个阶段对应固定的文章数与验收清单，这样每篇都能独立成文，又互为上下游。

### 研究仓和网站仓为什么要分开？

研究仓放实验脚本、数据、失败记录，变动频繁且不宜对外；网站仓只放结论。混在一起时，一次实验失败就可能把线上内容搞脏，git 历史也会失去意义。

**延伸阅读**：[第一篇：VPS SSH 加固实录](/blog/vps-ssh-hardening)、[Hermes Agent 自进化为什么不触发](/blog/hermes-agent-skill-loop)、[deepseek-chat 与实际模型名的差异](/blog/hermes-agent-model-cost)、[Hermes Agent 镜像构建的四个坑](/blog/hermes-agent-docker-build)、[Crawl4AI 操作手册](/blog/crawl4ai-handbook)、[Crawl4AI 商业价值分析](/blog/crawl4ai-business-value)、[Crawl4AI 研究路线](/blog/crawl4ai-research-route)、[Crawl4AI + Hermes 组合](/blog/crawl4ai-hermes-combo)'), update_time = NOW()
WHERE slug = 'web-intelligence-lab' AND content NOT LIKE '%常见问题%';

-- hermes-agent-docker-build
UPDATE blog SET description = '官方没有 Hermes Agent 镜像。自建 4.65GB 镜像的四个坑：GitHub release 不通、主进程该是 gateway、路径被改写、改密码不生效。', update_time = NOW()
WHERE slug = 'hermes-agent-docker-build';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### Hermes Agent 有官方 Docker 镜像吗？

没有。官方 `docker-compose.yml` 写的是 `build: .`，CI 也只跑测试不推镜像。我试过三个可能的镜像名都不存在，唯一路径是拉源码自建，实测产物 4.65 GB、构建约 11 分钟。

### 构建时 GitHub release 下载失败怎么办？

典型报错是 curl exit 56 / 7 / 22，而 `codeload.github.com`、`api.github.com` 都通——卡住的只有 release 下载。解法是把 s6-overlay 三个包本地下载后用 `COPY` 进构建上下文，但 `sha256sum -c` 那行必须原样保留：只改「怎么拿包」，不改「怎么校验」。

### 只跑 dashboard 报 Gateway inaccessible 怎么修？

因为 dashboard 不是主进程。镜像里主进程是网关，dashboard 是 s6 的受管服务，由 `HERMES_DASHBOARD=1` 控制是否启动。启动命令要以 `gateway run` 作为主进程，再加 `HERMES_DASHBOARD=1` 和端口环境变量。

### 改密码为什么不生效？

配置文件优先于环境变量。Hermes 会把密码以 scrypt 哈希写进 `/opt/data/config.yaml`，重启后覆盖你 `-e` 传的值。要用它自己的 `hash_password` 写 config 再重启，明文密码不会被落盘。

**延伸阅读**：[Hermes Agent 自进化为什么默认不触发](/blog/hermes-agent-skill-loop)、[配置 deepseek-chat 实际跑的是什么模型](/blog/hermes-agent-model-cost)、[这台服务器的 SSH 加固实录](/blog/vps-ssh-hardening)'), update_time = NOW()
WHERE slug = 'hermes-agent-docker-build' AND content NOT LIKE '%常见问题%';

-- hermes-agent-model-cost
UPDATE blog SET description = '配置写 deepseek-chat，实际跑的是 deepseek-flash。讲清别名折叠、配置优先级、V4 thinking 导致的 400，与两次任务的 token 成本拆解。', update_time = NOW()
WHERE slug = 'hermes-agent-model-cost';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### deepseek-chat 和 deepseek-flash 是什么关系？

前者是已下线的旧 model id。DeepSeek 在 2026-07-24 下架 `deepseek-chat` 与 `deepseek-reasoner`，此后请求返回 400；Hermes 在 `model_normalize.py` 里把这两个名字折叠到 `deepseek-flash`，启动时会打印一行 Normalized 提示。

### 怎么确认线上实际调用的是哪个模型？

用 `hermes --usage-file /tmp/u.json -z "..."` 跑一次，然后看 JSON 里的 `model` 字段——那是实际调用的名字，不是配置里写的。注意 `--usage-file` 只在 `-z` 单次模式生效，`chat -q` 不写文件，容易读到上一次的旧数据。

### 模型别名会影响计费吗？

不影响单价，影响的是「以为在跑 A、其实在跑 B」带来的预算误判。实测两次任务：纯问答 22,592 input（98% 命中缓存）/ 95 output，$0.00019；带工具调用 46,876 input / 574 output，$0.00068。固定开销约 2.2 万 input token，每多一次 API 调用都要再付一遍。

### 换其他厂商要注意什么？

两件事：一是不要指望按格式推断模型名的白名单，Hermes 明确「其他 model id 原样发出」；二是 V4 系列默认开 thinking，必须显式传参并在后续轮次回传 `reasoning_content`，否则带工具调用的第二轮就 400。想关掉就把 effort 设成 `none`。

**延伸阅读**：[Hermes Agent 自进化的触发条件](/blog/hermes-agent-skill-loop)、[镜像构建与部署的四个坑](/blog/hermes-agent-docker-build)、[整套研究的设计：24 篇内容体系](/blog/web-intelligence-lab)'), update_time = NOW()
WHERE slug = 'hermes-agent-model-cost' AND content NOT LIKE '%常见问题%';

-- hermes-agent-skill-loop
UPDATE blog SET description = '自进化默认不会触发：阈值数的是单轮工具调用而非对话轮，复盘还跑在守护线程。附 9 轮 47 次调用的实测数据与 /refine 手动触发法。', update_time = NOW()
WHERE slug = 'hermes-agent-skill-loop';

UPDATE blog SET content = CONCAT(content, '

## 常见问题

### Hermes Agent 的自进化默认开启吗？

开启，但默认几乎不会触发。判定条件是 `_skill_nudge_interval >= 10`，而计数器 `_iters_since_skill` 数的是单轮内的工具调用迭代，不是对话轮数；一次普通任务通常只有 4 次工具调用。

### 需要多少轮对话才会触发技能沉淀？

不是按轮算的。计数在回合结束时判定，且只属于当前 agent 实例，会话一换就归零——所以「聊十轮」永远累积不到 10。要走通必须让单轮工具调用 ≥ 10 次，并使用长驻入口（网关）而不是一次性命令。

### 为什么用 `hermes chat -q` 跑完技能数一直不变？

复盘线程是 `daemon=True`（`run_agent.py:851`），主进程一退出就被杀掉，而复盘本身要跑一个约 3 万 token 的 fork；`chat -q` 在非 TTY 环境等价于 `--oneshot`，答完即退，线程根本没有机会开始。

### 手动触发技能沉淀的命令是什么？

走网关的 `/refine` 斜杠命令：向 `http://127.0.0.1:8642/v1/chat/completions` 发一条消息，内容写 `/refine 回顾刚才的任务，把可复用流程沉淀成技能`。实测一次请求产出一个 5,998 字节的 SKILL.md，并把新技能挂进已有技能的 `related_skills`。

**延伸阅读**：[Hermes Agent 镜像构建实录](/blog/hermes-agent-docker-build)、[实际调用的是哪个模型、花了多少钱](/blog/hermes-agent-model-cost)、[Crawl4AI + Hermes 的组合方式](/blog/crawl4ai-hermes-combo)'), update_time = NOW()
WHERE slug = 'hermes-agent-skill-loop' AND content NOT LIKE '%常见问题%';

