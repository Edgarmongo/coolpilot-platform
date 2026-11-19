# ADR-0005：仓库的授权方式

- 状态：**已采纳**（2026-11-20）
- 关联：`LICENSE`、`THIRD-PARTY-NOTICES.md`、`docs/BASELINE.md`

---

## 1. 背景

仓库自 2026-11-04 建立基线起就没有 `LICENSE`，`README.md` 写的是「具体授权方式尚未确定」，
`BASELINE.md` 引用了一份并不存在的 `docs/LICENSE-REVIEW.md`。

现在需要确定：这份代码是谁的资产、能不能对外、别人能用吗。

有几个事实必须先摆在前面，否则结论会错：

| 事实 | 依据 |
|---|---|
| 仓库最早提交是 2020-07-18，作者 `Naccl <admin@naccl.top>` | `git log --reverse` |
| 服务端基线派生自 [Naccl/NBlog](https://github.com/Naccl/NBlog) | 包结构、`pom.xml` 原坐标 `top.naccl`、`V1__init_schema.sql` 来源 |
| NBlog 是 **MIT** 协议 | 上游 `LICENSE` 原文 |
| 仓库历史中**从未**包含过上游 LICENSE 文件 | `git log --all --diff-filter=A --name-only -- "*LICENSE*"` 为空 |

所以「所有内容都是我全新开发」这句话，对 v1.0.0 之后的部分成立，对服务端基线不成立。

---

## 2. 决策

**采用专有许可，同时保留第三方声明。**

| 文件 | 内容 |
|---|---|
| `LICENSE` | 专有许可：除第三方组件外，著作权归 CoolSkyAI，保留所有权利，禁止未授权复制 / 分发 / 商用 |
| `THIRD-PARTY-NOTICES.md` | NBlog 的版权行 + MIT 全文 + **使用边界清单**（哪些派生、哪些自研） |
| `pom.xml` | `groupId` 由 `top.naccl` 改为 `com.coolskyai`，补 `<licenses><name>Proprietary</name>` |
| 前端 `package.json` | `"license": "UNLICENSED"`（npm 生态表达专有的标准写法） |

---

## 3. 为什么不直接写「全部归我、全部自研」

因为 MIT 的这条要求是硬约束：

> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.

MIT **允许**把衍生作品闭源、商用、再许可 —— 这一点没有争议，用户的目标是达成的。
MIT **要求**保留原版权声明 —— 删掉它构成许可违约。

更现实的风险是：仓库里 `controller/` `service/` `entity/` 等包仍是上游结构改造而来，
一旦声称「全部自研」，将来若发生权属争议，这份声明本身会成为不利证据。
**如实标注反而让主张更强**：边界清楚，自研部分的权利才站得住。

---

## 4. 被否的方案

| 方案 | 否决原因 |
|---|---|
| 继续不定 | 对外分发时无据可依；客户或合作方会要求明确授权 |
| 整体 MIT 开源 | 与「变成自己资产」的目标相反 |
| 整体 GPL / AGPL | 不满足需求，且会强制公开全部源码 |
| 删掉上游声明、只写自研 | 违约，且属于可被追溯的不实陈述 |
| 重写掉所有遗留包后再声明全部自研 | 成本高；且当前遗留包仍有维护价值，没必要为了声明去重写 |

---

## 5. 后果

**必须遵守**

1. 仓库保持 **Private**。要公开，先做一次代码考古，把仍属上游的部分剥离或改写
2. 对外交付（给客户部署、给合作方源码）时，`LICENSE` 与 `THIRD-PARTY-NOTICES.md` **必须一起给**
3. 新增第三方源码（不是包依赖）时，必须补进 `THIRD-PARTY-NOTICES.md`
4. 不得合入 GPL / AGPL 代码 —— 那会让整个仓库的授权模式失效

**不受影响**

- 站点内容（文章、图片、研究数据）本来就归自己，与代码许可分开
- 通过包管理器引入的依赖不需要逐条声明

---

## 6. 待办

| 项 | 说明 |
|---|---|
| 权利人名称 | `LICENSE` 现在写的是 `CoolSkyAI`。若要写个人真名或公司主体，改第 3 行即可 |
| 上游声明落地方式 | 当前放在 `THIRD-PARTY-NOTICES.md`。若将来做对外交付包，建议同时在前端页脚或"关于"页留一行 attribution |
| 代码考古 | 如果要公开仓库，需要逐包确认哪些仍是上游原文，做清单后再决定剥离还是保留声明 |
