# 历史迁移脚本（已归档）

**这里的脚本不再被任何流程自动执行。**

2026-11-05 起，表结构与基础数据由 **Flyway** 管理，位置：

```
coolpilot-server/src/main/resources/db/migration/
├── V1__init_schema.sql   # 39 张表的完整结构（当前生产快照）
└── V2__init_seed.sql     # 站点配置、管理员、关于页、定时任务、分类、标签
```

应用启动时 Flyway 按版本号顺序执行未应用的脚本，记录写在 `flyway_schema_history` 表，重复启动不会重跑。已有库首次接入会被标记为 baseline 2，不会重复建表。

## 新增变更怎么做

在 `db/migration/` 下加文件，版本号递增：

```
V3__add_service_domain.sql
V4__add_ai_conversation.sql
```

规则：

1. **已执行过的脚本不可修改**。`validate-on-migrate=true`，改了 checksum 会校验失败并阻止启动。要修错就再写一个 V(n+1)。
2. **一个脚本一件事**，命名用 `V{n}__{动词}_{对象}.sql`，下划线分隔。
3. **不要写 `USE` 或库名**，Flyway 用当前连接的数据源。
4. 涉及数据订正时，先备份，脚本里避免依赖具体自增 ID。
5. 写完在本地空库上跑一遍（见 `docs/CI-CD.md` 的验证方法），再提交。

## 本目录文件的定位

| 文件 | 用途 |
|---|---|
| `schema.sql` | 早期全量建表（19 表），已被 `V1__init_schema.sql` 取代 |
| `demo-data.sql` | 演示文章（5 篇）。**不进迁移**，需要时手动执行 |
| `2026-11-04-indexes-and-constraints.sql` | 索引与约束补齐，已并入 V1 |
| `2026-11-04-v1-0-community.sql` | 社区模块 4 表，已并入 V1 |
| `2026-11-05-v1-1a-file-product.sql` | 文件服务与知识商品 10 表，已并入 V1 |
| `2026-11-05-v1-1b-order-payment-entitlement.sql` | 交易与权益 6 表，已并入 V1 |
| `_backup_*.sql` | 本地备份，已在 `.gitignore` 中 |

保留它们只是为了追溯「当时为什么这么设计」，`docs/DATA-MODEL.md` 里有对应的论证过程。

演示商品（`deploy/mysql/07-seed-product.sql`）同理，不进迁移。
