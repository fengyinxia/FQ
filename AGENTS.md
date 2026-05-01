# AGENTS: 项目执行规则

## 目标

- 快速接手，准确执行，最小风险交付。
- 结论必须基于当前代码、文档或验证结果。
- 默认先做最小可验证闭环，再考虑扩展。

## 接手顺序

开始任务前先读文档，再决定是否扫代码：

1. `analysis/project-handoff.md`
2. `analysis/cache-refactor-checkpoints.md`
3. 最近的 `analysis/*review*.md`
4. 必要时再看 `analysis/*proposal*.md`

规则：
- 不要在预读前做无差别全量搜索。
- 文档足够时不要重复扩散搜索。
- 文档与代码冲突时，以当前代码和已验证结果为准，并同步修正文档。

## 搜索原则

- 优先用已有上下文、已知文件、已知目录、已知类名和 Hook 链路定位。
- 只有线索不足时才扩大搜索范围。
- 搜索和读取优先使用专用工具，避免无意义全仓扫描。

## 架构边界

- `finders/` 只负责扫描目标，产出 `TargetScanResult`。
- Finder 不得直接写缓存。
- `cache/TargetRepository` 负责快照加载、发布、解析。
- `cache/CachedTargets` 是 Hook 层统一读取入口，负责运行时 memoization 和失败抑制。
- `hooks/` 只通过 `CachedTargets` 取目标，不直接访问仓库实现。
- `HookTargets` 是 target key 协议，未经明确必要性不要改 key 含义。
- 修改上述边界时，必须同步更新 `analysis/project-handoff.md`。

## 执行原则

- 能直接解决就直接执行；高风险、破坏兼容、生产副作用或需求冲突时才最小澄清。
- 禁止静默兜底、吞错误、假装成功。
- 删除/移动文件、批量替换、核心依赖升级、生产 API 调用前，先说明影响和风险。
- 不要回滚用户或其他代理的改动，除非用户明确要求。

## 工程规范

- 遵循 `KISS`、`YAGNI`、`DRY`，必要时参考 `SOLID`。
- 未经明确批准，不破坏现有 API、CLI、数据格式、缓存 key 协议。
- 优先小改动；避免无关重构和风格噪音。
- 修 Bug 时优先补可复现测试；无法自动化时说明替代验证方式。
- 不硬编码密钥；外部输入必须校验；数据库必须参数化查询。

## 验证要求

改动 Java、Gradle、Hook 初始化、缓存、Finder、Hook 或导出逻辑后，默认执行：

```bash
./gradlew.bat assembleDebug
```

纯文档变更可不跑构建。若无法验证，最终说明未验证项、原因和补验方式。

## 文档维护

以下变化必须同步更新 `analysis/project-handoff.md`：

- 架构边界
- 初始化链路
- 缓存刷新策略
- 目录职责
- 构建方式
- 已知遗留问题

阶段性重构更新 checkpoint 文档；专项排查新增或更新 `analysis/*review*.md`。

## Git 规则

- 禁止使用：`git restore`、`git stash`、`git checkout`、`git worktree`。
- 未经用户明确要求，不创建 commit，不执行 push。
- 提交前确认只包含相关变更、已完成必要验证、相关文档已同步。

## 沟通与输出

- 默认简体中文。
- 简短直接，聚焦结果，不展开无关细节。
- 复杂、多步骤、跨文件或需要验证的任务使用 todo 跟踪。
- 最终输出包含可交付结果和简洁执行摘要。
