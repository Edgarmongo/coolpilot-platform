# 第三方组件与来源声明

本文件记录 `coolpilot-platform` 中包含的第三方源码及其许可要求。
它不覆盖通过包管理器引入的依赖（见 `pom.xml`、`package.json` 与 lock 文件）。

**为什么要单独写一份**：本项目的主体是专有软件（见 [LICENSE](LICENSE)），
但服务端基线派生自一个 MIT 协议的开放项目。MIT 要求「原版权声明与许可声明
必须包含在所有副本或实质部分中」—— 这条不能因为整体改成专有许可就省略。
省略会构成许可违约，也会让「本项目全部由我开发」这句话变成不实陈述。

---

## 一、NBlog（MIT）

| 项 | 内容 |
|---|---|
| 项目 | NBlog — Spring Boot + Vue 前后端分离博客系统 |
| 作者 | Naccl |
| 仓库 | <https://github.com/Naccl/NBlog> |
| 许可 | MIT |
| 版权行 | `Copyright (c) 2020 Naccl (https://naccl.top/)` |

### 本项目使用了它的哪些部分

| 部分 | 现状 |
|---|---|
| 服务端基础结构与遗留业务包 | **派生**。`controller/`、`service/`、`entity/`、`mapper/`、`util/` 等扁平平来自 NBlog 的 `top.naccl` 包，已迁移到 `com.coolskyai` 并按需修改 |
| 初始数据库结构 | **派生**。`V1__init_schema.sql` 在 NBlog 的 `nblog.sql` 基础上改造而来 |
| 构建配置 | 派生。`pom.xml` 的依赖选择沿用上游 |
| 前台（`coolpilot-web`） | **已全部重写**。Nuxt 3 + Vue 3 + TypeScript，不含上游 Vue 2 代码 |
| 后台（`coolpilot-admin`） | **已全部重写**。Vue 3 + Vite + Element Plus |
| v1.0.0 之后新增的业务 | **独立开发**。商品、交易、权益、技术服务、AI 问答、开源观察、专题、作品集等模块，以及 V3–V28 迁移 |
| 文档与视觉系统 | **独立开发** |

### MIT 许可全文（原文照录）

```
MIT License

Copyright (c) 2020 Naccl (https://naccl.top/)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### 它允许什么、不允许什么

- **允许**：闭源使用、修改、商用、再许可（sublicense）、销售 —— MIT 不要求衍生作品开源
- **要求**：保留上面的版权行与许可全文
- **不要求**：在界面上展示、公开修改内容、以相同协议发布

也就是说，把整体改成专有许可是 MIT 允许的；**唯一不能做的是删掉这段声明**。

---

## 二、运行时依赖

这些通过包管理器引入，未修改其源码，各自遵守原许可。不再逐条罗列版权行。

| 类别 | 主要组件 |
|---|---|
| 后端框架 | Spring Boot / Spring Security / Spring Data |
| 数据访问 | MyBatis、PageHelper、MySQL Connector/J |
| 工具库 | JJWT、commonmark-java、ip2region、Quartz、jsoup、Jackson |
| 前端框架 | Nuxt、Vue、Vue Router、Pinia、Vite、TypeScript |
| 后台 UI | Element Plus |
| 构建与部署 | Maven、npm、Docker、nginx（镜像） |

---

## 三、素材与内容

| 项 | 说明 |
|---|---|
| 站点文章与图片 | CoolSkyAI 自有，保留所有权利 |
| 封面插画（`AppCover`） | 程序生成的 SVG 几何图形，无第三方素材 |
| PDF 报告字体 | reportlab 内置 `UnicodeCIDFont("STSong-Light")`，仅用于本地生成报告，不随产品分发 |
| 图标 | `AppCover` / 页面内使用的 SVG 路径为手写，未使用图标库字体 |

> 如果后续引入第三方图标集、字体文件或配图，必须补进本表并核对商用条款 ——
> 这是最容易被忽略的一类许可风险。

---

## 四、维护约定

1. **合并上游代码前**先确认其许可是否允许闭源；GPL / AGPL 代码不得合入
2. 新增第三方源码（不是包依赖）时，必须在本文件补一节，附版权行与全文
3. 本文件与 `LICENSE` 中关于「哪些是自研、哪些是派生」的表述必须保持一致
4. 对外分发（哪怕是给客户部署）时，`LICENSE` 与 `THIRD-PARTY-NOTICES.md` 必须一起提供
