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
- `finders/DownloadFinder.java`：用户下载入口、章节解码核心、下载状态分发与阅读器目录预加载目标。
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

以下为模块实现的功能清单，不代表官网最新宿主全部兼容；7.3.9.32 的静态核对结论见 `analysis/hook-offset-review-7.3.9.32.md`。

- 广告链路：屏蔽广告配置、强制部分免广告/VIP 判定、屏蔽 LuckyDog、书架短剧 Banner、弹窗和 `ClickAgent` 点击统计；旧书城 pictureData Banner 清空 Hook 已按用户要求移除。
- 功能链路：禁用 ABTest、更新检查、作者说、热评、章末礼物/评论控件，调整启动页跳转和 ReaderActivity 返回行为。
- UI 链路：过滤我的页附加卡片、红点、VIP 入口、动态搜索入口、搜索提示词，精简首页/书城 Tab 和部分推荐流卡片；新增我的页推荐瀑布流开关拦截（待用户实机验收）。
- 底栏链路：通过底栏路由实验帮助类调整 Tab 顺序，并隐藏 `VideoSeriesFeedTab`。
- 下载链路：下载点击入口先选择“缓存 / 缓存并导出”，再原样恢复宿主权限判断与原下载。仅为明确选择导出的书监听捕获/补回及 FINISH 导出；普通缓存不自动导出。选择按 bookId 持久化，暂停/重启恢复保留，取消/成功导出后清除。

## 已知状态

- 非 DexKit 盘点及迁移：见 `analysis/non-dexkit-hook-review-7.3.9.32.md`。2026-10-05 已将封面文字/图片渲染、章节解码核心、快捷入口转换/聚合迁移为 5 个独立方法级 DexKit key；Hook 只读 CachedTargets，且检查签名。不再写死这几处的 a.g/h、x.a、sd4.h.b、q1$a.b；移除相关旧列表构造器和扫描回退分支，仅维护新版。保留 class_feature_list_load 的原类 key 含义，新增方法 key 不复用旧 key。官网 7.3.9.32 全 23 DEX 的等价静态特征扫描中，5 个目标各只有一个候选；assembleDebug --offline 及无线覆盖安装均成功；重启后 PID 21843（16:13—16:14）日志确认新增 5/5 方法实际扫描、写入快照并挂载，封面两次回退文字，快捷源列表 4→2/聚合 7→3，下载从缓存补回 13 章后记录 TXT 导出完成。未读正文；解码直接捕获回调没有独立日志，不能由缓存导出推定其单独触发。新快照 39/42，缺少已有书名点击/Banner过滤/排行榜目标，本轮未修复；该进程日志未见异常。未迁移 Framework SDK、Reader、PopProxy、侧栏配置、热词构造器、ChapterInfo.a、广告额外成员及 EPUB P1。快捷过滤的 a/b 字段和 de4 模型白名单仍为新版依赖；内容处理器仍直接调用新版 ChapterOriginalContentHelper.i/c，不维护旧版导出链。弹窗 a() 的首页延迟实验语义、无条件成功日志和侧栏后续配置覆盖风险仍保留，详见专项审计。
- 分类入口迁移：见 `analysis/category-bottom-tab-review-7.3.9.32.md`。现已在 BottomTabHooks 中恢复原生 BookCategory，顺序书架/书城/分类/我的，继续隐藏短剧。新增 `KEY_CATEGORY_TAB_DISABLED_METHOD` 唯一定位分类资源门禁并返回 false；新增 `KEY_MAIN_ACTIVITY_ON_CREATE_METHOD`，在 Application 初始化后的主界面 onCreate 前归一化路由列表，避免 attach 时强行初始化路由类。底栏构建前及 BookStoreAlignmentData 刷新后重复归一化，补分类且去重；沿宿主 hasCategoryTab 分支隐藏搜索旁分类入口并调整搜索布局，不手工移动控件。安装与实际页面验收边界见专项记录。
- 原书名统一展示可行性：见 `analysis/original-book-name-review-7.3.9.32.md`。主 RPC/PB/KMP ApiBookInfo 存在 rawBookName（PB 为 raw_book_name）候选，originalBookName 另被原著/改编关联消费者使用，不可盲目混用。书城共享 j4.f 目前只复制展示名；阅读器使用独立 SaaS ApiBookInfo，缺少 rawBookName，故不能改一处即保证全部 UI。方案需先核对一部实验书的元数据，只按同 bookId 的可靠原名替换，缺值保留，不全局改 TextView 或章节标题。本轮尚未实现或运行验证。
- AB 云控清单：`analysis/common-ab-controls-review-7.3.9.32.md` 已核对 `CommonAbResultData` 的 74 个业务字段、28 个顶层布尔标记，以及更新入口直接读取的 45 个字段。当前 `applyAbtestHooks()` 仅跳过更新/缓存写入/通知，旧配置仍能在静态初始化时从本地恢复，不等于关闭所有开关；独立 SsConfigMgr 配置仍可生效。`dynamicComicTip` 仅是引导文案/版本，漫剧活跃度是用户参数，不是推荐总开关。
- 搜索框热词后续修复：原先仅在双参构造前清空 text，遗漏 prefixText/displayText/displayTextV2 和 JSON/KMP 路径。现新增 `SearchCueWordFilter`，统一清理推荐词的四个展示字段，保留 `isDefault=true` 的宿主通用提示，不触碰用户输入。`UIFinder` 唯一定位原生/KMP 搜索框静态 `List→List` 预处理入口，经新增 `KEY_SEARCH_CUE_LIST_METHOD` / `KEY_SEARCH_CUE_KMP_LIST_METHOD` 交给 `CachedTargets`，由 `UIHooks` 在接收词列表时清理，补回构造器绕过路径。列表长度和搜索框保留；类型/字段不兼容明确记录。构建安装及用户验收边界见专项审计，不将安装成功当作热词已消失。
- 书城漫剧漏过滤排查：现有推荐 Hook 只拦截 `c1.Y(CellViewData,int,int)`，日志仍实际过滤 `VideoSeries`，不是整个 Hook 失效。白名单仅保留 Book/RankListBook，DynamicComic/RankListDynamicComic 在该入口应被拦截；但宿主另有 `M→r0/K0` 等转换链路，Book 子项也可能按 genreType 渲染漫画，而当前 helper 只额外检查听书。已确认覆盖缺口，尚未捕获用户所见漫剧卡片的实际类型和入口，不把所有漫画等同于漫剧。本轮未修改源码，详情及后续定位边界见专项审计。
- 我的页金币/余额/提现区域：新增 `KEY_MY_PAGE_CONTENT_METHOD`（`method_my_page_content`），由 `UIFinder` 唯一定位 `FanqieMineFragmentV2.onCreateContent(LayoutInflater, ViewGroup, Bundle): View`，Hook 保留页面创建结果。仅在该内容树中校验 `id/feq` 含钱包行 `he1`（金币 `frc`、余额 `fr5`、提现 `gou`）及福利领取 `gos` 后，将整卡设为 `GONE`；侧栏菜单入口替换为设置入口：复用 `f0l` 原控件及布局参数，只换设置图标和原生点击回调，保持其 `VISIBLE`；备用 `f05` 保持 `INVISIBLE`。此前显示 `f05` 导致用户反馈与夜间按钮重叠，现不再显示它或移动夜间按钮。通过新增 `KEY_MY_PAGE_SETTINGS_CLICK_METHOD`（`method_my_page_settings_click`）唯一定位并实例化宿主原生设置监听器，点击执行 `openSetting`，不是仅换图标。原先把两按钮都隐藏属于需求误解，已纠正；定位失败明确记录并保留原菜单。布局监听处理异步创建和重新显示。原先仅隐藏 `he1` 会残留福利，本轮按用户要求改为整卡隐藏，不改变真实账户余额或提现 API。混淆资源名依据 7.3.9.32 既有真机 UI 树，其他版本未验证；本次页面效果待用户验收。当前 target 协议共 41 个 key（含热词列表、分类迁移、5 个方法级迁移及下载点击入口，已移除旧 Banner/排行榜两项）。
- 我的页推荐瀑布流：新增 `KEY_MY_PAGE_RECOMMEND_ENABLE_METHOD`（`method_my_page_recommend_enable`），不改变既有 key 含义。`UIFinder` 通过 `mine_tab_staggered_feed_v649` 配置串、`CommonAbResult.getBookMallRevert()` / `AppRunningMode.isFullMode()` 双调用、静态/无参/boolean 签名唯一定位，`UIHooks` 经 `CachedTargets` 返回 false；宿主因此跳过推荐流及其刷新容器创建，不拦截整页或全局推荐。7.3.9.32 静态目标为 `com.dragon.read.base.ssconfig.template.e.d()`；第一版定位遗漏调用约束，实机出现 2 个候选导致 Hook 跳过，用户反馈推荐仍在；现已收紧定位，最终效果待用户再次验收。此功能新增时协议为 31 个 key，历史 26/30 快照仅为旧基线；本次构建/安装结果见专项审计。
- 封面热门评论后续修复：Finder 改用专属 `book_cover_hot_comments_v617` 配置串、双 BookUtils 调用及参数签名唯一定位 `yw6.c.d2(...)`；Hook 按可空 View 契约返回 null，并拦截同类 `P1(): kotlin.Pair` 的 EPUB 热评页插入，不再盲改字段 `c`。保留旧版定位条件及现有 target key。构建/安装结果见专项审计，实际封面效果由用户验收；此前 26/30 是修复前实机快照，不能当作本次扫描结果。
- 2026-10-04：已针对官网 7.3.9.32 修复我的页搜索/快捷入口、底栏、弹窗挂载、章末返回契约、更新处理、封面标题及下载调用链；RMX2117 的最终快照有 26/30 个目标。旧的静态风险表是修复前基线，现状与逐项验证边界见 `analysis/hook-offset-review-7.3.9.32.md` 新增的修复状态表。
- 模拟器（emulator-5554，Android 14，x86_64）上 LSPosed 2.1.1 无 32 位 x86 库，不能用于验证该宿主。模块现已同时打包 `arm64-v8a` 和 `armeabi-v7a` DexKit 库；7.3.9.32 已在 RMX2117 真机验证。
- 2026-10-05 按用户要求彻底移除旧书城 Banner 与排行榜 Hook：删除 method_filter_banner/method_remove_rank、对应 Finder 扫描、AdHooks 的 pictureData 清空注册，以及未接入初始化的 applyRemoveRankHooks；协议 42→40。保留书架短剧 Banner 响应过滤、书城推荐流过滤（含 RankListBook 允许类型）、以及此前已验收的 5 个方法级迁移。之前的 39/42 快照为移除前历史记录；本轮未修复书名点击目标。源码残留引用检查为 0、assembleDebug --offline 及无线覆盖安装成功；用户重启后刷新快照，未代操作页面。
- 7.3.9.32 的 `KEY_MY_PAGE_SEARCH_BAR_METHOD` 仍定位 `FanqieMineFragmentV2.onCreateContent(...)`；Hook 现保留整页创建返回值，按实机确认的 `com.dragon.read:id/g64` 将搜索 ComposeView 设为 `INVISIBLE`（保留占位），并在后续布局中重新隐藏。该混淆资源名仅在 7.3.9.32 实机验证；旧版独立入口方法保留原替换路径。
- 阅读偏好与推荐流类型的对齐分析见 `analysis/read-preference-recommend-flow-review.md`，同步过滤方案已回滚。
- 听书解密/导出运行时代码已删除；若后续重启，应从 `analysis/audio-decrypt-review.md` 重新设计，不要回滚旧代码。

## 下载导出

- 下载方式选择实现见 `analysis/download-mode-review-7.3.9.32.md`：新增 method_download_click，按“点击下载权限判断”及九参契约唯一定位 n1.a，覆盖 KMP/原生详情及阅读器三个直接调用点。DownloadModeChooser 在主线程显示选择，XposedBridge.invokeOriginalMethod 恢复全部原参数，不改宿主权限结果。DownloadExportModes 使用独立 fq_download_export_requests 保存导出请求 ID；普通缓存不执行本模块捕获/补回/写 TXT，旧请求失效后不能写文件或清除新任务状态。协议 40→41；默认仅缓存，入口缺失不自动导出。全 DEX 入口唯一、三个调用点核对及 assembleDebug --offline 通过，无线覆盖安装 Success；弹窗显示/取消、两模式、多书与暂停恢复仍待用户重启实机验收。

- 下载目标由 `DownloadFinder` 生产，`DownloadHooks` 消费。
- 关键 key：`KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD`、`KEY_READER_DIRECTORY_PRELOAD_CLASS`。
- 当前 7.3.9.32 目录接口为 `ml6.f0.d().c(bookId)`，缓存章节走 `ChapterOriginalContentHelper.i/c`，解密 UTF-8 正文走 `reader.utils.x.a(...)`。只导出已解密的内容；遇到 EPUB XHTML 章节需先去除标签、还原段落与实体。
- TXT 导出位置：`/storage/emulated/0/Download/FQ/<书名>.txt`。一次真机下载已确认 FINISH、补回 813 章和写入路径，但那次使用的是**加入 XHTML 转换前**的构建，生成的文件仍是原始 XHTML；最终构建已安装，纯文本内容转换尚待再次完成下载后验收，不能宣称 TXT 完整正确。不要读取或公开书籍正文以证明结果。

## unidbg 子项目

- `unidbg/` 是独立中转项目，不属于主 Xposed 模块运行时。
- 详细交接文档：`analysis/unidbg-handoff.md`。
- `project-handoff.md` 只维护主 Xposed 模块状态，`unidbg` 细节统一维护到独立文档。

## 最近验证

- 2026-10-04：最终 `assembleDebug assembleRelease --offline` 通过；Release 仅安装到 `RMX2117`，冷启动番茄 7.3.9.32，宿主进程存活、快照发布 26 项，新版解密 Hook、更新处理、封面标题和书架 Banner 目标均安装成功。**安装成功不等于功能触发。**
- 2026-10-04：真机“我的”页快捷入口候选 4→2，聚合列表 7→3；截图仅见浏览历史、我的消息、我的下载；底栏仅书架/书城/我的，短剧不再显示。封面书名回退文本与推荐流 VideoSeries 过滤有实际运行日志；其他广告/VIP/红点/侧栏等缺独立行为证据。
- 2026-10-04：`assembleDebug assembleRelease` 通过；Release 安装到 RMX2117，冷启动 7.3.9.32，日志确认搜索入口隐藏，截图确认顶部搜索入口消失且“我的”页正常显示；改为 `INVISIBLE` 后扫描及夜间按钮坐标与隐藏前一致，宿主进程存活，未再出现原 `AbsFragment.onCreateView` 空返回崩溃。
- 2026-10-04：完成用户提供的官网 7.3.9.32 APK 全部 23 个 DEX 的静态目标/契约核对；当时仅更新分析文档，未执行构建或实机验证。
- 2026-04-02：`./gradlew.bat assembleDebug` 通过。
- 2026-04-02：听书解密运行时代码移除后构建通过。
