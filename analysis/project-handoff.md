# 项目接手文档

## 项目定位

- 这是一个面向番茄免费小说 `com.dragon.read` 的 Xposed 模块。
- 主入口：`app/src/main/java/com/fuck/fanqie/MainHook.java`。
- 当前能力：广告/弹窗处理、功能行为调整、UI 精简、底栏定制、下载 TXT 导出。
- 当前缓存架构已从旧的 `MethodCacheManager + SharedPreferences` 收口为快照仓库模型。

## 初始化链路

- `MainHook` 只在宿主主进程初始化，`:push` 和其他非主进程会跳过。
- 初始化时机：`ContextWrapper.attachBaseContext` 后拿到 `Application Context`。
- 主流程：创建 `TargetRepository`、`CachedTargets`、`HookApplier`，判断是否重跑 DexKit，必要时由 `HookFinder` 扫描并发布快照，最后执行 `HookApplier.applyHooks()`。

关键文件：
- `MainHook.java`：入口与初始化流程。
- `HookFinder.java`：统一组织 Finder 扫描。
- `HookApplier.java`：统一分发各类 Hook。
- `HookTargets.java`：缓存 key 协议常量。

## 架构边界

- `finders/` 只负责 DexKit 扫描目标，产出 `TargetScanResult`。
- `cache/TargetRepository` 负责快照加载、发布、解析，以及宿主版本/模块指纹读取。
- `cache/CachedTargets` 是 Hook 层只读入口，负责运行时 memoization 和失败抑制。
- `hooks/` 只能通过 `CachedTargets` 读取目标，不要直接访问仓库或写缓存。
- 不要让 Finder 重新直接写缓存，也不要破坏现有 `HookTargets` key 含义。

## 目录速览

- `cache/`：`TargetRepository`、`CachedTargets`、`CacheSnapshot`、`TargetEntry`、`TargetScanResult`、`AtomicFileTargetCacheStore` 等缓存快照实现。
- `finders/AdFinder.java`：广告、免广告、LuckyDog、弹窗目标。
- `finders/FeatureFinder.java`：ABTest、启动页、更新检查、章节控制目标。
- `finders/UIFinder.java`：Tab、搜索、红点、我的页、VIP、侧边栏目标。
- `finders/DownloadFinder.java`：下载状态分发与阅读器目录预加载目标。
- `hooks/`：广告、功能、UI、底栏、框架基础 Hook。
- `hooks/download/`：下载内容捕获、章节解密、TXT 导出。

## 缓存机制

- 快照文件：`target-cache-snapshot.json`。
- 宿主私有目录：`/data/user/0/com.dragon.read/files/target-cache-snapshot.json`。
- 部分设备也可能显示为：`/data/data/com.dragon.read/files/target-cache-snapshot.json`。
- 刷新条件：宿主版本变化，或模块 APK 指纹变化。
- 模块指纹优先使用 `versionCode + file.length() + file.lastModified()`，无法读取 APK 时退回 `version:<versionCode>`。
- Finder 扫描结果由 `TargetRepository.publishFreshScan(...)` 一次性发布，避免半新半旧缓存。
- `CachedTargets` 通过 `snapshotGeneration` 感知快照变化，并清空运行时缓存。

## 当前能力

- 广告链路：屏蔽广告配置、强制部分免广告/VIP 判定、屏蔽 LuckyDog、Banner、弹窗和 `ClickAgent` 点击统计。
- 功能链路：禁用 ABTest、更新检查、作者说、热评、章末礼物/评论控件，调整启动页跳转和 ReaderActivity 返回行为。
- UI 链路：过滤我的页附加卡片、红点、VIP 入口、动态搜索入口、搜索提示词，精简首页/书城 Tab 和部分推荐流卡片。
- 底栏链路：通过底栏路由实验帮助类调整 Tab 顺序，并隐藏 `VideoSeriesFeedTab`。
- 下载链路：监听书籍下载状态，捕获/补回章节正文，下载结束后导出 TXT。

## 已知状态

- `applyRemoveRankHooks()` 代码仍保留，但未接入 `UIHooks.apply()`，当前排行榜移除 Hook 不生效。
- “我的”页搜索当前只处理动态入口；若宿主切到固定搜索入口，这部分不会生效。
- 阅读偏好与推荐流类型的对齐分析见 `analysis/read-preference-recommend-flow-review.md`，同步过滤方案已回滚。
- 听书解密/导出运行时代码已删除；若后续重启，应从 `analysis/audio-decrypt-review.md` 重新设计，不要回滚旧代码。

## 下载导出

- 下载目标由 `DownloadFinder` 生产，`DownloadHooks` 消费。
- 关键 key：`KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD`、`KEY_READER_DIRECTORY_PRELOAD_CLASS`。
- TXT 导出位置：`/storage/emulated/0/Download/FQ/<书名>.txt`。

## unidbg 子项目

- `unidbg/` 是独立中转项目，不属于主 Xposed 模块运行时。
- 详细交接文档：`analysis/unidbg-handoff.md`。
- `project-handoff.md` 只维护主 Xposed 模块状态，`unidbg` 细节统一维护到独立文档。

## 最近验证

- 2026-04-02：`./gradlew.bat assembleDebug` 通过。
- 2026-04-02：听书解密运行时代码移除后构建通过。
