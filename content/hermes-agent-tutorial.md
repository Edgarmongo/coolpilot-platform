---
slug: hermes-agent-tutorial
title: Hermes Agent 入门：从构建 4.65GB 镜像到跑通第一个自动研究任务
description: 官方没有镜像，从构建容器到跑通第一个自动研究任务的完整五步。含主进程必须是 gateway、配置优先于环境变量这两处最容易卡住的地方。
category: 开源技术实践
tags: [Hermes Agent,AI Agent,Docker]
---

## 先说结论

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
docker run -d --name hermes \
  -p 127.0.0.1:9119:9119 \
  -v "D:/hermes-data:/opt/data" \
  -e HERMES_DASHBOARD=1 \
  -e HERMES_DASHBOARD_HOST=0.0.0.0 \
  -e HERMES_DASHBOARD_PORT=9119 \
  hermes-agent:local gateway run
```

注意最后那个 `gateway run`：**主进程是网关，dashboard 是被 s6 管理的子服务**，靠 `HERMES_DASHBOARD=1` 决定开不开。

如果写成 `... hermes-agent:local dashboard`，控制台能打开、能登录、界面正常，但一发消息就报：

```
Hermes couldn't start the model connection: Gateway inaccessible:
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

另外，DeepSeek 在 2026-07-24 下架了 `deepseek-chat` 和 `deepseek-reasoner` 两个 model id，Hermes 做兼容折叠，启动时会打印一行 `Normalized model 'deepseek-chat' to 'deepseek-flash'`。这不是你配错了。细节与实测成本见 [配置 deepseek-chat 实际跑 deepseek-flash](/blog/hermes-agent-model-cost)。

---

## 六、第四步：第一个任务

**走网关，不要用 `hermes chat -q`。** 后者在非 TTY 环境等价于 `--oneshot`，答完就退，后台复盘线程会被直接杀掉，你永远等不到技能沉淀。

```bash
curl -X POST http://127.0.0.1:8642/v1/chat/completions \
  -H "Authorization: Bearer $API_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"hermes-agent","messages":[{"role":"user",
       "content":"统计 /opt/data 下所有 .db 文件的数量和总大小，输出一份 Markdown 报告"}]}'
```

这是一个刻意选的小任务：它有明确的成功判据（数量、字节数）、需要多次工具调用、结果可核对。**第一次跑不要给它开放性问题**（「帮我研究一下 AI 行业」），你没法判断它做对没做对。

跑完你会拿到一份报告。这时它还是个「会调工具的模型」，还没体现 Agent 的价值——价值在下一步。

---

## 七、第五步：确认它真的在干活

三个验证，缺一个你都不知道它到底做了什么。

**① 实际调用的是哪个模型**

```bash
docker exec -u hermes -e HOME=/opt/data hermes \
  hermes --usage-file /tmp/u.json -z "统计 /opt/data 下 .db 文件数量"
docker exec hermes cat /tmp/u.json
```

JSON 里的 `model` 字段是**实际调用**的名字，不是配置里写的那个。注意 `--usage-file` 只在 `-z` 单次模式生效，`chat -q` 不写文件，容易读到上一次的旧数据还以为没变。

**② 花了多少钱**

实测两个量级：纯问答 22,592 input / 95 output，**¥0.0014**；带 4 次工具调用 46,876 input / 574 output，**¥0.0049**。固定开销约 2.2 万 input token（系统提示 + 工具 schema + 技能索引），每多一次 API 调用都要再付一遍——所以「按 token 加价」这个生意不成立。

**③ 技能有没有沉淀**

```bash
docker exec hermes sh -lc 'find /opt/data/skills -name SKILL.md | wc -l'
```

这个数字不会自己变。**触发条件是单轮工具调用 ≥ 10 次**，而普通任务通常只有 4 次；日常问答永远不会沉淀。想立刻看到效果，用 `/refine` 手动触发：

```bash
curl -X POST http://127.0.0.1:8642/v1/chat/completions \
  -H "Authorization: Bearer $API_SERVER_KEY" \
  -d '{"model":"hermes-agent","messages":[{"role":"user","content":"/refine 沉淀刚才的流程"}]}'
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

