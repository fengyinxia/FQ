# 项目接手文档

## 1. 项目定位

- 这是一个 Xposed 模块，目标宿主是 `com.dragon.read`
- 主入口是 `app/src/main/java/com/fuck/fanqie/MainHook.java`
- 当前能力主要分为五类：广告相关 Hook、功能行为 Hook、通用 UI 清理 Hook、底栏定制 Hook、下载导出 Hook
- 项目最近已经完成一轮较大的缓存架构重构，后续 AI 接手时不要再按旧的 `MethodCacheManager + SharedPreferences` 心智理解

## 2. 当前整体架构

### 2.1 初始化链路

- `MainHook` 只在宿主主进程初始化，`:push` 进程会直接终止，非主进程直接跳过
- 初始化时机是 `ContextWrapper.attachBaseContext` 后拿到 `Application` 的 `Context`
- 初始化主流程如下：

1. 创建 `TargetRepository`
2. 创建 `CachedTargets`
3. 创建 `HookApplier`
4. 比较宿主版本和模块指纹，决定是否重跑 DexKit
5. 如果需要，调用 `HookFinder.findTargets(...)` 扫描目标并通过 `TargetRepository.publishFreshScan(...)` 发布快照
6. 最后统一执行 `HookApplier.applyHooks()`

关键文件：
- `app/src/main/java/com/fuck/fanqie/MainHook.java`
- `app/src/main/java/com/fuck/fanqie/HookApplier.java`
- `app/src/main/java/com/fuck/fanqie/HookFinder.java`

### 2.2 Finder / Repository / Hook 三层分工

- `finders/` 只负责通过 DexKit 扫描目标，产出 `TargetScanResult`
- `cache/TargetRepository` 负责快照加载、快照发布、按 key 解析 `Method/Class<?>`
- `cache/CachedTargets` 负责 Hook 层读取 facade、运行时 memoization、失败抑制
- `hooks/` 只消费 `CachedTargets`，不再直接访问底层仓库

这是当前最重要的设计边界，后续不要再把 Finder 直接写缓存，也不要让 Hook 再直接碰仓库实现。

## 3. 目录速览

### 3.1 根功能文件

- `app/src/main/java/com/fuck/fanqie/MainHook.java`：模块入口与初始化流程
- `app/src/main/java/com/fuck/fanqie/HookApplier.java`：统一分发 `FrameworkHooks` / `FeatureHooks` / `AdHooks` / `BottomTabHooks` / `UIHooks` / `DownloadHooks`
- `app/src/main/java/com/fuck/fanqie/HookFinder.java`：统一组织各 Finder 进行 DexKit 扫描
- `app/src/main/java/com/fuck/fanqie/HookTargets.java`：所有缓存 key 协议常量

### 3.2 cache 包

缓存相关类已统一放入：
- `app/src/main/java/com/fuck/fanqie/cache/`

核心类职责：
- `TargetRepository`：缓存仓库；负责快照装载、发布、解析、版本/指纹对比字段读取
- `CachedTargets`：Hook 层只读入口；内存缓存 `Method/Class<?>`，并对缺失 key 做失败抑制
- `TargetScanResult`：DexKit 扫描阶段的中间结果对象
- `TargetEntry`：单个 target 的结构化表示，包含 `key/kind/serialized`
- `CacheSnapshot`：完整落盘快照，包含 `schemaVersion/hostVersion/moduleVersion/moduleFingerprint/entries`
- `TargetCacheStore`：底层存储接口
- `AtomicFileTargetCacheStore`：当前默认实现，使用 `AtomicFile + JSON`

### 3.3 finders 包

- `AdFinder`：广告、免广告、LuckyDog、弹窗相关目标
- `FeatureFinder`：ABTest、启动页跳转、更新检查、章节控制相关目标
- `UIFinder`：Tab、搜索、红点、我的页卡片、VIP、侧边栏相关目标
- `DownloadFinder`：下载导出相关目标，当前负责定位阅读器目录预加载服务类，以及基于字符串 `fail to execute percent change:` 定位下载状态分发方法

### 3.4 hooks 包

- `FrameworkHooks`：一些全局基础屏蔽逻辑，比如上报/线程相关；`com.dragon.read.base.util.ThreadUtils` 日志 Hook 代码仍保留，但默认不启用
- `FeatureHooks`：功能行为类 Hook
- `AdHooks`：广告/弹窗/点击统计相关 Hook
- `BottomTabHooks`：底部 Tab 定制；当前通过底栏路由实验帮助类先改数据源顺序，再在布局层隐藏视频 Tab，不再 afterHook 后重建底栏 View
- `UIHooks`：首页、我的页、搜索、VIP、红点、顶部 Tab 等通用 UI 相关 Hook；当前只禁用“我的”页动态搜索入口
- `hooks/download/DownloadHooks`：下载内容捕获、章节解密、TXT 导出

## 4. 缓存执行方式

### 4.1 触发刷新条件

现在缓存刷新条件不是单纯依赖模块 `versionCode`。

当前判定条件：
- 宿主版本变化，或
- 模块 APK 指纹变化

位置：
- `app/src/main/java/com/fuck/fanqie/MainHook.java`

模块指纹由 `TargetRepository` 计算，优先使用模块 APK 文件的：
- `versionCode`
- `file.length()`
- `file.lastModified()`

如果无法拿到模块 APK，则退回 `version:<versionCode>`。

这样即使本地重复打包但没有手动修改 `versionCode`，只要 APK 文件变化，缓存仍会自动重建。

### 4.2 快照发布语义

- Finder 不直接写缓存
- `HookFinder` 会创建一个新的 `TargetScanResult`
- 各 Finder 把找到的目标写入 `TargetScanResult`
- 扫描结束后由 `TargetRepository.publishFreshScan(...)` 一次性发布完整快照

这解决了旧实现中的两个问题：
- 扫描失败时留下半新半旧状态
- 老 key 残留无法表达完整扫描结果

### 4.3 运行时读取方式

- Hook 统一通过 `CachedTargets.method(key)` 或 `CachedTargets.type(key)` 获取目标
- `CachedTargets` 内部维护：
  - 成功解析缓存 `methods`
  - 成功解析缓存 `types`
  - 失败抑制集合 `missingMethods`
  - 失败抑制集合 `missingTypes`
- 当 `TargetRepository` 发布新快照后，`snapshotGeneration` 会递增；`CachedTargets` 会检测变化并自动清空内存缓存

### 4.4 快照文件位置

- 文件名：`target-cache-snapshot.json`
- 代码位置：`app/src/main/java/com/fuck/fanqie/cache/TargetRepository.java`
- 实际存放在宿主 App 私有目录：

```text
/data/user/0/com.dragon.read/files/target-cache-snapshot.json
```

某些设备上也可能表现为：

```text
/data/data/com.dragon.read/files/target-cache-snapshot.json
```

注意：
- 这个文件不在仓库目录里
- 清空宿主数据会删除它
- 卸载宿主也会删除它

## 5. 当前 Hook 能力概览

### 5.1 广告链路

生产：`AdFinder`

消费：`AdHooks`

已在用的 key：
- `KEY_AD_CONFIG_METHOD`
- `KEY_AD_FREE_CLASS`
- `KEY_AD_FREE_METHOD`
- `KEY_BOOKSHELF_BANNER_RESPONSE_METHOD`
- `KEY_LUCKY_DOG_METHOD`
- `KEY_POP_METHOD`

实际效果包括：
- 屏蔽广告配置判定
- 强制免广告 / VIP 相关返回值
- 屏蔽 LuckyDog 福利
- 当前仍启用书城/首页 Banner 隐藏 Hook（`KEY_FILTER_BANNER_METHOD`）
- 过滤书架 `RelateVideo` 短剧 Banner
- 屏蔽弹窗
- 屏蔽字节点击统计 `ClickAgent`

### 5.2 功能链路

生产：`FeatureFinder`

消费：`FeatureHooks`

已在用的 key：
- `KEY_ABTEST_METHOD`
- `KEY_SPLASH_K1_METHOD`
- `KEY_UPDATE_METHOD`
- `KEY_CHECK_UPDATE_METHOD`
- `KEY_AUTHOR_SAY_METHOD`
- `KEY_COVER_HOT_COMMENT_METHOD`
- `KEY_CHAPTER_END_HOT_COMMENT_METHOD`
- `KEY_CHAPTER_END_CONTROL_METHOD`

实际效果包括：
- 禁用 AB 测试
- 修改启动页默认跳转到书架；当前仅在 `tabName` 为空或为 `seriesmall` 时，改写为 `dragon1967://main?tabName=bookshelf`
- 屏蔽更新检查 / 更新消息
- 禁用作者说、封面热评、章末热评、章末礼物/评论控件
- ReaderActivity 返回行为调整
- PopProxy 弹窗条件拦截：放行 `privacy_dialog`，其余默认拦截并打印详细日志

### 5.3 UI 清理链路

生产：`UIFinder`

消费：`UIHooks`

已在用的 key：
- `KEY_FEATURE_LIST_LOAD_CLASS`
- `KEY_FILTER_DATA_METHOD`
- `KEY_MY_PAGE_SEARCH_BAR_METHOD`
- `KEY_MY_PAGE_VIP_ENTRANCE_METHOD`
- `KEY_RED_DOT_METHOD`
- `KEY_SEARCH_BAR_METHOD`
- `KEY_TOP_TAP_METHOD`
- `KEY_VIP_INFO_MODEL_CLASS`

实际效果包括：
- 过滤我的页面附加卡片
- 去掉红点
- 禁用我的页侧边栏配置，回退为设置按钮模式
- 禁用我的页动态搜索入口构建
- 去掉我的页 VIP 入口
- 自定义 VIP 信息构造参数
- 过滤搜索栏内容 / 搜索提示词
- 精简首页 / 书城 Tab
- 过滤书城推荐流数据类型；当前仅保留 `groupIdType=Book` 和 `groupIdType=RankListBook`，并额外过滤 `bookType=Listen` 或“短故事 + audioIconControl”的听书样式卡片

当前实验状态：
- `applyRemoveRankHooks()` 代码仍保留，但已从 `UIHooks.apply()` 接入点临时移除；当前排行榜移除 Hook 不生效
- “我的”页搜索当前只处理动态入口；若宿主切到固定搜索入口，这部分不会生效
- 阅读偏好与推荐流类型的对齐分析已完成，但同步过滤方案已回滚；结论见 `analysis/read-preference-recommend-flow-review.md`

### 5.4 底栏链路

生产：`UIFinder`

消费：`BottomTabHooks`

已在用的 key：
- `KEY_TAB_METHOD`
- `KEY_TAB_ROUTE_HELPER_CLASS`

实际效果包括：
- 通过底栏路由实验帮助类返回的 tab 类型列表前置调整底栏顺序
- 在宿主完成底栏构建后隐藏 `VideoSeriesFeedTab`

### 5.5 下载导出链路

生产：`DownloadFinder`

消费：`DownloadHooks`

已在用的 key：
- `KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD`
- `KEY_READER_DIRECTORY_PRELOAD_CLASS`

实际效果包括：
- 监听书籍下载状态
- 在下载时缓存章节正文
- 在下载完成后尝试补回本地缓存目录中的章节内容
- 下载结束时组装章节内容并导出 TXT

## 10. 2026-04 音频下载解密导出补充

- 2026-04-02 已完全移除听书解密实现：
  - `HookApplier` 不再分发 `AudioHooks`
  - `app/src/main/java/com/fuck/fanqie/hooks/audio/` 目录已删除
- 当前项目**不包含**任何听书下载解密/export 运行时代码。
- 本次专项分析结论已单独整理到：
  - `analysis/audio-decrypt-review.md`
- 若后续需要重启这条线，应以该报告为起点重新实现，而不是回滚旧代码。

### 10.1 当前状态

- 听书解密 Hook：已删除
- 听书自动导出：已删除
- 仅保留分析文档，不保留运行时代码

### 10.2 构建验证

- 2026-04-02 已执行：`./gradlew.bat assembleDebug`
- 结果：通过
