# 官网 7.3.9.32 Hook 审计：修复前基线与实机复核

## 修复前静态结论（历史）

**核对时的旧代码**与该 APK 存在多处不兼容，不只是混淆类名变化；本节及下方原始目标表保留作偏移证据，当前实现与实测结果以“本轮修复与验证”节为准。

- 30 个缓存 target key：21 个能唯一定位，2 个存在多候选，7 个无有效目标（含上游缺失导致跳过）。**唯一定位不等于回调契约兼容。**
- 已确认“我的页搜索”定位到了 `onCreateContent(...) -> View`；现有 `DO_NOTHING` 会跳过整页初始化。
- 已确认章末热评定位到返回 `it6.l` 组件对象的方法，但 Hook 返回 `Boolean.FALSE`。
- 下载、底栏隐藏、底栏排序、顶部 Tab 精简、侧栏和部分弹窗 Hook 的写死成员已不成立。
- 上述结论为本次**静态核对时**的状态；下述实机修复记录覆盖“我的页搜索”项。

## 2026-10-04 实机修复：“我的”页搜索入口

- 原 `DO_NOTHING` 替换 `FanqieMineFragmentV2.onCreateContent(...)` 会返回 `null`，已在 RMX2117 复现 `AbsFragment.onCreateView` 空指针崩溃；改为保留原方法执行及返回的页面 View。
- 真机当前“我的”页搜索入口是 `com.dragon.read:id/g64` 的 `ComposeView`（在页面头部 `id/ixt` 内），不是 `SearchWordDisplayView`。之前从全局 `dumpsys activity top` 误取到的 COUI 搜索控件属于其他 Activity，不能用作番茄目标。
- Hook 仅在该 Fragment 内容树中按 `id/g64` 且类名包含 `ComposeView` 定位并设为 `INVISIBLE`，保留搜索框占位，避免扫描和夜间模式按钮被重新排版；布局监听处理延迟加入或重显。旧版独立构建方法仍走原 `DO_NOTHING` 路径；资源名是混淆名称，其他宿主版本未验证。
- `assembleDebug assembleRelease` 成功，Release 安装到 RMX2117 后冷启动 7.3.9.32：日志确认搜索入口隐藏，截图确认顶部搜索框消失、其他“我的”页内容正常，进程存活且原崩溃未复现。`INVISIBLE` 修复后扫描按钮 `id/ezb` 为 `[48,133][141,226]`、夜间模式按钮 `id/f0p` 为 `[786,130][879,223]`，与隐藏前两者的坐标完全一致。

## 本轮修复与验证（2026-10-04，RMX2117）

模块 Release 已仅在 RMX2117 安装；`assembleDebug assembleRelease --offline` 通过，宿主 7.3.9.32 冷启动进程存活，最终缓存快照发布 **26/30** 个 key。下表的“已安装”不是“已验证生效”。

| 功能 | 当前结果与证据 | 判定 |
| --- | --- | --- |
| 我的页搜索 | `onCreateContent` 正常创建页面；`id/g64` ComposeView 设 `INVISIBLE`，扫描/夜间按钮坐标不变，截图 `.work/verify-search-invisible.png`。 | **实机通过**（仅本宿主版本） |
| 我的页快捷入口 | 源列表 4→2、聚合列表 7→3，截图 `.work/hook-repair-7.3.9.32/mine-aggregate-filtered.png` 只显示浏览历史、我的消息、我的下载。单钩 `QuickAccessCardRenderer.p(List)` 虽已安装，却因新版走 `q1` 实验分支未过滤完整；现对 `sd4.h.b` 和 `q1$a.b` 两阶段过滤。 | **实机通过** |
| 底栏与顶部 Tab | 底栏构建前过滤 `VideoSeriesFeedTab`，截图仅书架、书城、我的；书城顶部六项是当前白名单。 | 底栏**实机通过**；顶部仅画面核对 |
| 启动跳书架 | 真机日志出现 `K1: 已修改为跳转书架 tab`，冷启动显示书架。 | **实机通过** |
| 推荐流 VideoSeries | 运行日志出现 `groupType=VideoSeries` 实际过滤。其他推荐类型尚未逐项走查。 | **运行时命中** |
| 封面短书名/图片书名 | `BookCoverInfo` 旧 Getter 已无；改 Hook `reader.bookcover.view.a.g/h`，真机两次执行“封面书名已回退为文本”。 | **运行时命中**；未做画面对照 |
| PopProxy 弹窗 | 不再加载不存在的 SilkRoad 类型；真机挂载 popup/enqueue 共 3 个入口，隐私弹窗留原行为。 | **安装通过**；实际弹窗样本待测 |
| 章末热门评论 | `CommunityReaderDispatcher.j2` 已按对象返回类型置空。 | **安装通过**；章末页面待测 |
| 章末评论/礼物控件 | 原 Finder 构造器不可解析，现 Hook 该 View 的 `onAttachedToWindow` 后隐藏。 | **安装通过**；章末页面待测 |
| 更新弹窗/检查 | 新 `z$b.handleMessage(Message)` 和原 `z.R(boolean)` 两入口安装成功。 | **安装通过**；没有独立更新事件证据 |
| 书架短剧 Banner | 新目标 `g34.x.invoke(Object)` 安装成功，回调仅接受 `GetBookShelfBannerResponse`。 | **安装通过**；未遇到 `RelateVideo` 样本 |
| 广告配置/免广告、LuckyDog | 目标与 Hook 均安装；无独立广告播放/权益响应的前后对照。 | **安装通过，行为待测** |
| 我的页 VIP、VIPInfoModel | 目标/构造器 Hook 安装；未以付费服务端权益作为验证依据。 | **安装通过，行为待测** |
| 我的页侧栏 | `GameRevisitPathV693Model.sideBarEnable` 单例及构造器值已处理；我的页右上角菜单仍可见，可能不是同一侧栏功能。 | **配置写入通过，最终效果未证实** |
| 红点、搜索词、搜索页卡片、点击统计 | 红点目标可解析、代码会挂钩，但无独立执行日志；其余安装日志可见，均未逐页对照。 | **仅静态/安装证据** |
| ABTest、ReportManager/ThreadUtils、Reader 返回 | 静态签名及安装日志可见；没有对独立调用结果或 Reader 返回行为做验证。 | **仅静态/安装证据** |
| 下载与导出 | `ub6.c.b(String,Status)` 的真实 FINISH 已触发；`ml6.f0.d().c(bookId)`、`ChapterOriginalContentHelper.i/c` 补回本地 **813** 章并写入 Download/FQ。首次导出用的旧构建写出了完整 XHTML（12,611,030 字节，813 个章标题），**不是纯文本**。最后修订增加 XHTML body 解析、HTML 实体还原和段落处理，已构建并安装，尚未有第二次完成下载的最终 TXT 内容实测。加密原文现在不允许直接导出。 | **状态/补回/写入实机通过；最终纯文本仍缺运行时证据** |
| 封面热门评论 | 原记录为未完成；后续已按独立 View 工厂及 EPUB 页插入入口修复，详见下方后续修复节。本表 26/30 快照为修复前数据。 | **源码已修复；实机行为由用户验收** |
| 书城图片 Banner | 最终快照仍缺 `method_filter_banner`；没有安全证明可以用通用列表清空替代，未做可能破坏整页的盲钩。 | **仍失效，未完成** |
| `method_book_name_click`、`method_remove_rank` | 缓存 key 未命中，但前者没有 Hook 消费方，后者 `applyRemoveRankHooks()` 未接入 `UIHooks.apply()`，原来就不执行。 | **非本轮已验证功能** |

旧版独立更新字符串、`BookCoverInfo` 两个 getter 和下载 `reader.utils.o.a` 已移除；现更新处理、封面标题 UI 与 `reader.utils.x.a` 均改用实有签名。除书城图片 Banner 仍未恢复及封面热门评论后续修复待用户验收外，其余“已挂载”项目仍需按相应页面/事件补运行时验证。不要覆盖用户原有改动，也不要把前一次 XHTML 测试文件当成最终有效 TXT；该既有文件尚未迁移或删除。

## 后续修复：封面热门评论

- 失效原因：新版 `yw6.c.d2(NsReaderActivity, BookCoverInfo): ax6.y` 保留两次 BookUtils 调用，但不再含 `bookInfo` 字符串；原复合条件因此无命中。该方法仅创建热评 View，数据缺失时本来就返回 null，不是整页创建入口。
- Finder 使用 `book_cover_hot_comments_v617`、上述双调用及双参数签名定位；全 DEX 配置串交叉引用共 6 处，只有 `yw6.c.d2` 同时符合其余条件。仅唯一候选可缓存；无新版候选时保留旧版定位。
- View 路径按返回类型校验后返回 null，删除旧的盲改字段 `c` 和吞错逻辑。
- EPUB/Compose 路径另由同 dispatcher 的 `P1(): kotlin.Pair` 控制热评页插入；源码在 `isBookCommentCoverEnable()` 为 false 时返回 null。Hook 在该入口返回 null，不直接跳过 Compose 函数，不清空全局评论数据或干扰评分/目录书评功能。
- 只读反编译证据：`.work/cover-hot-comment/YwCoverComment.java`、`CoverDispatcherContract.java`；字节码交叉引用确认 View 调用方为 `sj6.b.j`，EPUB 插入调用方为 `an6.d0.P1`。
- 按用户要求，仅构建并安装模块，宿主页面操作和功能验收由用户执行。`assembleDebug --offline` 已通过（命令级指定 `ANDROID_HOME=E:/sdk`）。首次 USB 安装因 ADB 断连失败；随后按用户要求通过 mDNS 发现 RMX2117 无线调试地址并连接，`adb install -r app/build/outputs/apk/debug/app-debug.apk` 返回 `Success`，Debug 修复版已安装。宿主未启动或操作，封面热评实际效果仍待用户验收。不把 Hook 安装日志等同于热评消失。

## 后续修复：我的页书籍推荐瀑布流

- `FanqieMineFragmentV2.onCreateContent` 在 `com.dragon.read.base.ssconfig.template.e.d()` 返回 true 时，延迟创建 `NsBookmallApi.IMPL.uiService().i(...)` 推荐流及 `SuperSwipeRefreshLayout`；返回 false 时走宿主既有关闭分支，不置空页面 View。
- 该静态无参 boolean 方法读取 `mine_tab_staggered_feed_v649`；全 DEX 的 5 处直接调用位于我的页 Fragment、变体 Fragment 及我的页辅助链路，不是书城通用推荐过滤入口。关闭分支也用于我的页相关布局模式选择，快捷入口和历史卡片的最终布局仍需用户验收。
- 新增独立 `KEY_MY_PAGE_RECOMMEND_ENABLE_METHOD` / `method_my_page_recommend_enable`，`HookTargets.allKeys()` 同步登记（现共 31 个 key）。`UIFinder` 按配置包范围、配置字符串、`CommonAbResult.getBookMallRevert()` / `AppRunningMode.isFullMode()` 调用及 static/无参/boolean 签名定位，非唯一候选记录数量与成员并跳过；`UIHooks` 经 `CachedTargets` 读取并再次校验签名，返回 false，首次调用输出命中日志。不改既有 key 含义，不写混淆类名或方法名，不改全局隐私推荐设置。
- 第一版实机未生效：LSPosed 日志 2026-10-05 09:50:43 显示候选数=2，随后 `MyPageRecommend` 日志显示未找到开关并跳过；快照共 27 项，缺新增 key。第二候选为 `MineTabStaggeredFeed$a.a()`，同样静态无参 boolean 且引用同一配置串。原先仅靠字符串与签名的“唯一”推断不成立；本轮加入实际展示门禁独有的双调用，全 DEX 配置串与 `getBookMallRevert()` 交集仅 `e.d()`。修订版 `assembleDebug --offline` 已通过，无线安装 RMX2117 返回 `Success`；未操作宿主页面或核对修订后的运行命中，实际页面结果由用户复验。
- 只读依据：`.work/mine-recommend-flow/FanqieMineFragmentV2.java`、`MineFeedConfig.java`。`assembleDebug --offline` 已通过，Debug APK 已通过无线 ADB 安装到 RMX2117，返回 `Success`。未操作宿主页面，未读取新快照或将安装成功算作生效；“我的”页推荐消失、快捷入口及历史卡片正常由用户验收。

## 后续修复：我的页金币余额提现区域

- 既有真机 UI 树 `.work/verify-search-invisible.xml` 确认：金币 `id/frc`、余额 `id/fr5`、提现 `id/gou` 同在独立 `id/he1` 行内；外层 `id/feq` 同时含 `id/gos` 福利领取，原版本刻意只隐藏钱包行。用户反馈福利仍在并要求一并移除后，本轮改为校验钱包及福利两块后隐藏 `feq` 整卡。只提取控件结构，不读取或输出余额数值。
- 新增 `KEY_MY_PAGE_CONTENT_METHOD` / `method_my_page_content` 并登记 `allKeys()`（此入口新增时协议共 32 个 key，后续设置点击入口加入后为 33 个）。`UIFinder` 按稳定 Fragment 类型、覆写方法名及完整参数/返回签名唯一定位，经 `CachedTargets` 读取；Hook 在页面创建后处理返回 View，不替换整页创建。
- `UIHooks` 在该页面内容树中校验 `feq` 整卡内的钱包行 `he1`、三块子区域及福利领取 `gos` 后设为 `GONE`，通过布局监听处理延迟创建或重新显示；资源缺失/结构不符时记录诊断，不扩大到外层页面。不改真实金币余额、账户或提现服务，不影响搜索隐藏和推荐流开关。
- 菜单/设置入口（纠正需求误解）：用户要求是将侧栏菜单换成设置，不是把两者隐藏。曾显示 `id/f05` 设置控件并隐藏 `id/f0l` 菜单，但用户反馈设置与夜间按钮重叠。现改为在我的页内容树中按 ImageView 类型校验后复用 `id/f0l` 原控件、布局参数及位置，只替换设置图标并绑定宿主原生设置监听器，保持 VISIBLE；备用 `id/f05` 保持原生 INVISIBLE 占位，不移动夜间按钮。钱包资源缺失不会阻止按钮替换。
- 新增 `KEY_MY_PAGE_SETTINGS_CLICK_METHOD` / `method_my_page_settings_click` 并登记 allKeys（现 33 个）。DexKit 通过 `onClick(View): void`、设置字符串、`NsAppNavigator.openSetting(Context, PageRecorder)` 调用及所属类单参数 `FanqieMineFragmentV2` 构造器组合唯一定位，不写死混淆监听器类名。当前静态证据为 `.work/mine-recommend-flow/MineSettingsClick.java` 的 `od4.j8`：原回调执行设置点击统计及 `openSetting(view.getContext(), fragment.Xg())`。Hook 经 CachedTargets 取得监听器类并以当前 Fragment 实例化，保留原生页面来源与打开设置行为；无法定位/实例化时明确记录并保留原菜单，不显示不可点击的替代图标。
- 资源名称仅依据 7.3.9.32；上版整卡/按钮隐藏构建安装通过，但菜单需求被误解；本轮“菜单换设置”的纠正版 `assembleDebug --offline` 已通过，无线安装 RMX2117 返回 `Success`；该版本用户反馈设置与夜间按钮重叠；本轮已改为原菜单控件复用，布局纠正版 `assembleDebug --offline` 已通过，无线安装 RMX2117 返回 `Success`；未替用户操作页面，实际设置点击及位置由用户复验。未操作宿主页面或宣称已生效，页面效果及布局由用户验收。

## 后续只读排查：书城漫剧推荐仍出现

- 当前快照 `method_filter_data` 解析为 `c1.Y(CellViewData,int,int): List`，最新宿主日志仍出现 `RecommendFlow` 实际过滤 `VideoSeries`，所以不能归因于模块整体未注入或该 Hook 完全未挂载。
- `RecommendFilterHelper` 读取的是 `CellViewData.groupIdType: CandidateDataType`，不是另一个 GroupType 枚举。只允许 Book/RankListBook；APK 已有 DynamicComic/RankListDynamicComic 类型，若进入 Y 且字段可读取，本来就会被拦截。不能把问题简单归因为“没有给漫剧加黑名单”。
- 已证实覆盖缺口：原始成员清单显示 `c1.M(CellViewData,int,int,ClientTemplate,boolean): List` 除了经 D0/E0 转到 Y，还直接调用 `r0(CellViewData,int,boolean)`、`K0(int,CellViewData)`；这两条也处理 DynamicComic，但本模块未在其入口使用推荐过滤。另有 `c1.K(CellViewData)` 按 DynamicComic/VideoSeries 将 videoData 转为 I0 的视频卡片模型。
- 另一个字段级缺口：Book 在白名单内，helper 仅进一步检查 Listen/听书图标短篇。APK 的 `l0(CellViewData,int)` 会根据 ApiBookInfo 转换后的 `genreType` 调用 `BookUtils.isComicType` 创建 StaggeredComicModel；因此 Book 并不保证全部是文字小说。这说明漫画型子项可漏过，但不能据此断言用户看到的漫剧就是此类型。
- 当前只读 UI 快照未匹配到“漫剧/动态漫画”标签，运行日志没有该卡片实际 groupIdType/showType/子项元数据。上述是确认的代码覆盖缺口，不是该张卡片已完成运行时归因。后续应在用户所见位置补类型与入口证据，再对推荐区域真实转换入口补过滤；不读取书籍正文，不盲目在通用 M 入口清空所有非 Book 卡片以免破坏顶部分类/Banner 等非推荐内容。
- 本轮仅更新分析/交接文档，未修改源码、构建或安装模块。

## 后续排查与修复：搜索框热词屏蔽不完整

下列“只清空 text/未处理”等描述是修复前原因；当前实现见本节末尾修复记录。

- 模块 `UIHooks.applySearchWordHooks()` 只拦截 `SearchCueWordExtend(SearchCueWord,String)` 构造器，并仅将入参模型 `text` 清空。最新宿主日志显示安装成功，但没有证明所有热词都经过该构造器或实际展示消失。
- 已确认显示字段漏处理：原生 `com.dragon.read.component.biz.impl.bookmall.search.c.setData(SearchCueWordExtend)` 将 `prefixText`、`text`、`displayText` 拼接后 setText。仅清空 text 不足以清掉前缀和展示热词。原始字节码也确认 `SearchWordDisplayView.j/k`、`SearchWordDisplayViewKMP.u`、KMP 搜索辅助 `g.g` 读取 `displayTextV2`，该字段同样未处理。
- 已确认构造覆盖缺口：`SearchCueWordExtend.a(fl5.f)` 使用无参实例并以 `JSONUtils.fromJson(..., SearchCueWordExtend.class)` 从 KMP 模型转换，不能认为一定经过被 Hook 的双参构造器。反编译证据：`.work/search-cue-review/SearchCueTextView.java`、`SearchCueWordExtend.java`。
- `applySearchBarHooks()` 过滤的是 `FanqieSearchActivity` 中间页列表里的热搜/榜单等模型，不是书城顶部搜索框的展示文字，不能拿它的安装日志当作搜索框热词屏蔽证据。
- 当前设备读取到的全局搜索控件还包含启动器 COUI 搜索，不能用作番茄搜索框证据；本轮依据宿主字节码确认缺口，未宣称当前屏幕热词已做逐项视觉验收。修复应覆盖真实搜索提示数据入口/展示字段与 KMP 转换，保留搜索框及用户输入，不直接全局清空 TextView 或整个搜索页。
- 排查阶段只更新文档；用户随后要求修复。现新增 `SearchCueWordFilter`，同时清理推荐词 text/prefixText/displayText/displayTextV2；保留 isDefault=true 的宿主通用提示。只在推荐词模型/列表上处理，不清空用户输入框，不清空整个搜索页或列表。
- `UIFinder` 在原生 `SearchWordDisplayView` 和 `SearchWordDisplayViewKMP` 两个稳定类型内，以 static、单 List 参数、List 返回组合分别唯一定位到 `d(List)` 和 `q(List)`，不写死混淆方法名。新增 `KEY_SEARCH_CUE_LIST_METHOD` / `KEY_SEARCH_CUE_KMP_LIST_METHOD` 并登记 allKeys（当前协议共 35 个 key），Hook 经 CachedTargets 读取；原生入口服务 f/m/r/s 等数据更新，KMP 的 h/k/v 等列表更新调用 q，因此能在 JSON/缓存内容进入渲染前处理。
- 原有双参构造 Hook 同步改用同一清理逻辑；KMP 包装器的混淆字段不硬编码，而按字段 DTO 类型 `SearchCueWord` 识别。字段/类型失败仅抑制重复日志，不静默成功；日志只输出清理数量，不输出热词或用户内容。静态依据补充 `.work/search-cue-review/SearchWordDisplayViewKMP.java`。
- 本轮 `assembleDebug --offline` 已通过，Debug APK 无线安装 RMX2117 返回 `Success`；未操作宿主页面或宣称行为已通过，实际热词消失、默认提示及手动搜索由用户验收。

## 样本与范围

- 核对时间：2026-10-04。
- 用户确认官网最新版样本：`番茄小说_官网_7.3.9.32.apk`；用户指定后未再查询官网或下载。
- APK 包名：`com.dragon.read`；`versionName=7.3.9.32`；`versionCode=73932`。
- 文件大小：137330535 字节。
- SHA-256：`fe35ea812f35cb23c5cad83cbd06828dada0864fc078e12486a312c38828a13b`。
- 扫描 APK 的全部 23 个 DEX：306128 个类、1264325 个方法。
- 比较基线是当前模块源码，不是另一份旧 APK；因此不能判定每项问题从哪个历史版本开始。
- 接手时 `analysis/cache-refactor-checkpoints.md` 和既有 `analysis/*review*.md` 不存在，本次以源码和 APK 字节码为准。

## 方法与证据

使用本机 jadx 1.5.5 JAR 内的 dexlib2，读取原始 DEX 类名、字段、方法描述符、字符串引用和调用引用，不使用反编译器重命名后的名称。

已通过 `app/libs/dexkit-2.0.7.aar` 的字节码确认：`usingStrings` / `addUsingString` 默认是区分大小写的 **Contains**，不是字符串全等。以下匹配结果按该语义静态重放；未在宿主运行原生 DexKit 或 Xposed。

可复用证据位于：

- `.work/hook-review-7.3.9.32/DexHookAudit.java`：只读 APK 扫描器。
- `.work/hook-review-7.3.9.32/dex-inventory.json`：目标及相关类型的原始成员清单。
- `.work/hook-review-7.3.9.32/dexkit-api.jar`：从现有 AAR 提取的 API，用于核对匹配默认值和反射解析行为。

清单只收录相关类型；未收录的类不能据此认定 APK 中不存在。写死类是否缺失以全 DEX 类索引核对结果为准。

## 高风险契约错误

### 1. “我的页搜索”误命中整页创建

- Finder：`finders/UIFinder.java:140-150`，条件是 `FanqieMineFragmentV2` 中同时调用 `UiUtils.setTopMargin` 和 `View.setOnClickListener`。
- 唯一命中：`com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2.onCreateContent(LayoutInflater, ViewGroup, Bundle): View`，位于 `classes3.dex`。
- Hook：`hooks/UIHooks.java:155-161`，直接应用 `XC_MethodReplacement.DO_NOTHING`。
- 该方法有 172 个不同调用引用、98 个不同字段引用，包含布局 inflate、控件查找及整页初始化。
- **影响：不是隐藏单个动态搜索入口，而是跳过整个“我的”页内容创建；存在空白页/崩溃风险。**

### 2. 章末热评返回对象，却替换为 boolean

- 命中：`CommunityReaderDispatcher.j2(String): it6.l`，位于 `classes9.dex`。
- `it6.l` 是组件类型，继承 `xk6.b`，具有 `D0(): View`、`getId(): String` 等成员，不是 boolean。
- Hook：`hooks/FeatureHooks.java:293-295`，返回 `Boolean.FALSE`。
- **影响：返回类型契约不成立；即使成功安装 Hook，也存在运行时类型/组件使用错误风险。**

### 3. 章末控件 Finder 命中构造方法

- `finders/FeatureFinder.java:76-85` 的包范围加 `ScreenUtils.getScreenWidth` 条件只命中 `com.dragon.read.social.comment.reader.d.<init>(Context, dispatcher.d$b, ReaderClient, String, String, boolean, boolean)`。
- `cache/TargetRepository.java:114` 使用 `DexMethod.getMethodInstance(...)` 解析成 `java.lang.reflect.Method`。
- DexKit 2.0.7 的 `InstanceUtil.getMethodInstance` 明确拒绝 `isMethod=false`，构造方法不能通过该入口解析。
- **影响：目标虽然能被扫描并记录，但解析失败，章末控件 Hook 无法安装。**

## 已确认的写死目标/成员不兼容

| 功能 | 当前代码依赖 | APK 实际情况与影响 |
| --- | --- | --- |
| PopProxy 条件弹窗拦截 | `FeatureHooks.java:109` 先加载 `com.bytedance.component.silk.road.subwindow.b` | 该类不在 APK；实际 popup 对应重载第三参是 `th0.b`。类加载发生在三次 hook 调用之前，因此同一 try 中的 enqueue/popup 拦截均不会安装。 |
| 解密结果捕获 | `DownloadHooks.java:107-119` 的 `reader.utils.o.a(ChapterInfo, DecryptKey)` | `reader.utils.o` 现在只有构造方法及 `call()`；不存在该 `a` 方法。全 DEX 也未发现原双参数、String 返回签名的直接替代方法。 |
| 下载目录读取 | `DownloadContentProcessor.java:31-35` 的预加载类 `j()`、`h(bookId)` | 仍能定位 `ml6.f0`，但其成员为静态 `d(): f0`、`a/b(String): Observable`、`c(String): Single`，没有 `j/h`。这些仅是新成员线索，尚未证明可直接替换。 |
| 本地章节补回/正文转换 | `DownloadContentProcessor.java:130-178,207` 的 `b0/h1/c0`、静态字段 `f192202c` | `ChapterOriginalContentHelper` 中均不存在这些成员；原始字段为 `c`。当前有 `i(String,String): ChapterInfo`、`c(ChapterInfo): Single`，但尚未验证完整替代调用链。 |
| 底栏排序 | `BottomTabHooks.java:56-61` 的帮助类 `z()` | 帮助类字符串匹配到 3 个类，3 个类均无 `z`。`hookAllMethods` 可能得到空集合，而现代码仍打印成功日志。 |
| 隐藏短剧底栏 | `BottomTabHooks.java:177` 用按钮 `f()` 读取枚举 | 按钮接口 `dk7.g.f(): boolean`；实际类型 getter 是 `a(): BottomTabBarItemType`。`MainFragmentActivity.c2` 也使用该接口，现读取结果不能匹配 `VideoSeriesFeedTab`。 |
| 顶部 Tab 精简 | `UIHooks.java:86` 的 `getBookMallTabDataList()` | `BookMallDefaultTabData` 为 Object 子类，保留 `bookMallTabDataList` / `selectIndex` 字段，但没有任何 `get*` 方法；该反射调用失败，过滤未执行。 |
| “我的”页侧栏 | `UIHooks.java:53-62` Hook 模型 `b()` | `GameRevisitPathV693Model` 仅有构造方法和类初始化，无 `b` 方法；伴生类 `$a.a()` 才返回模型。字段 `sideBarEnable` 本身还在。 |
| 封面短书名/书名链接 | `FeatureHooks.java:219-234` 的 `getBookShortName/getBookNameUrl` | `BookCoverInfo` 的父类为 Object，当前没有这两个 getter，因此两项 Hook 找不到方法。 |

下载链路并非每个挂钩都消失：`ChapterInfo.a(ItemContent, boolean)`、相关字段及下载状态分发方法仍在。但目录、解密捕获和补回调用已断，不能据此认定 TXT 导出完整可用。

另发现当前代码的独立契约风险：`FrameworkHooks.java:72` 将 `Npth.startOptMtkBuffer(int): int` 统一替换为 `null`，与原始类型返回不符；未证明该风险由本次版本更新引入。

## 全部缓存目标核对表（修复前静态扫描）

名称省略 `KEY_` 前缀；“唯一”仅表示满足当前 Finder 定位条件。

| Target | 候选数 | 当前命中 / 状态 |
| --- | ---: | --- |
| AD_CONFIG_METHOD | 1 | `eg3.a.checkAdAvailable(String,String): boolean` |
| AD_FREE_CLASS | 1 | `component.biz.impl.privilege.PrivilegeManager` |
| AD_FREE_METHOD | 1 | `PrivilegeManager.b(VipCommonSubType): boolean` |
| LUCKY_DOG_METHOD | 1 | `PolarisConfigCenter.isPolarisEnable(): boolean` |
| POP_METHOD | 1 | `ih6.g.a(Activity,IProperties): boolean` |
| AUTHOR_SAY_METHOD | 1 | `social.author.reader.f.a(): View` |
| COVER_HOT_COMMENT_METHOD | 0 | 无满足原字符串加双 BookUtils 调用条件的方法 |
| CHAPTER_END_CONTROL_METHOD | 1 | `social.comment.reader.d.<init>(...)`，不能解析为 Method |
| CHAPTER_END_HOT_COMMENT_METHOD | 1 | `CommunityReaderDispatcher.j2(String): it6.l`，返回替换不兼容 |
| SPLASH_K1_METHOD | 2 | `SplashActivity.startActivity(Intent,Bundle)`、`NormalAdLandingActivity.startActivity(Intent,Bundle)` |
| UPDATE_METHOD | 0 | 原两个消息处理日志定位串未命中 |
| CHECK_UPDATE_METHOD | 1 | `com.ss.android.update.z.R(boolean): boolean` |
| ABTEST_METHOD | 1 | `CommonAbResult.updateCommonAbResult(CommonAbResultData): void` |
| BOOKSHELF_BANNER_RESPONSE_METHOD | 0 | 原日志现在出现在 `g34.t.run(): void`，不满足单 Response 参数条件 |
| BOOK_NAME_CLICK_METHOD | 0 | 原 `ivBookName/tvBookName` 定位未命中；Hook 层未消费此 key |
| DYNAMIC_METHOD | 1 | `e04.z2.y3(InfiniteDynamicModel,int): void`；Hook 层未消费此 key |
| FEATURE_LIST_LOAD_CLASS | 0 | 原 `CardData(cardInfoList=` 定位串未命中 |
| FILTER_DATA_METHOD | 1 | `component.biz.impl.bookmall.c1.Y(CellViewData,int,int): List` |
| FILTER_BANNER_METHOD | 0 | 同类无原 `(CellViewData,int): BaseInfiniteModel` 方法 |
| REMOVE_RANK_METHOD | 0 | 上游 FILTER_BANNER 缺失，原 Finder 不再继续扫描；该 Hook 本来也未接入 apply |
| MY_PAGE_SEARCH_BAR_METHOD | 1 | `FanqieMineFragmentV2.onCreateContent(...)`，误拦整页 |
| RED_DOT_METHOD | 1 | `dk7.j.r(boolean): void` |
| SEARCH_BAR_METHOD | 1 | `FanqieSearchActivity.u2(List): void` |
| TAB_METHOD | 1 | `MainFragmentActivity.c2(BottomTabBarLayout,boolean): void`；按钮类型读取另有偏移 |
| TOP_TAP_METHOD | 1 | `NewBookMallFragment.Jh(BookMallDefaultTabData): void`；模型 getter 已失效 |
| TAB_ROUTE_HELPER_CLASS | 3 | `NsCommonDependImpl`、`pages.main.m4`、`xp6.e`；均无原 `z` 方法 |
| MY_PAGE_VIP_ENTRANCE_METHOD | 1 | `FanqieMineFragmentV2.sh(): void` |
| VIP_INFO_MODEL_CLASS | 1 | `user.model.VipInfoModel` |
| READER_DIRECTORY_PRELOAD_CLASS | 1 | `ml6.f0`；原目录 j/h 调用失效 |
| DOWNLOAD_STATUS_DISPATCHER_METHOD | 1 | `ub6.c.b(String,pages.download.Status): void` |

另外，广告配置/权益类的原 Hook 方法签名、ReaderActivity.onBackPressed、ClickAgent、两组 ThreadUtils、ReportManager、搜索提示词双参数构造函数及 `SearchCueWord.text` 字段仍存在。这里只确认静态成员，不确认实机效果或服务端权益。

## 模拟器核对（修复前历史环境记录，2026-10-04）

目标机 `emulator-5554`（Android 14、x86_64 + ARM 转译）。番茄与 FQ 均已安装：

- 番茄 `com.dragon.read` 7.3.9.32（versionCode 73932），设备上 base.apk 的 SHA-256 与本地样本一致。
- FQ `com.fuck.fanqie` 2.4.5（versionCode 20405），LSPosed 中已启用且作用域含 com.dragon.read。

**核对结果：模块根本没有被注入，所有能力都未生效。** 这是环境阻断，不是某几条 Hook 漂移。

证据（可复现）：

1. 冷启动番茄后 logcat 无任何 `FQHook:` 输出，只有 `W XposedInit: init_v3 appBindData:...com.dragon.read`。
2. 宿主私有目录始终没有 `target-cache-snapshot.json`（`/data/user/0/com.dragon.read/files/` 下不存在，全盘查找也没有）。
3. 宿主进程 maps 中搜不到 lsposed/lspd/dexkit 等框架与模块映射。
4. `/proc/<pid>/exe -> /system/bin/app_process32`：番茄主进程是 32 位。
5. Zygisk 记录 `loaded 1 64bit zygisk module(s)` / `loaded 0 32bit zygisk module(s)`。

两处硬性阻断（均已从本机文件核实，互相独立）：

- **LSPosed 2.1.1 (7790) 不含 32 位 x86**：`LSPosed-v2.1.1-7790-release.zip` 只提供 `lib/arm64-v8a`、`lib/armeabi-v7a`、`lib/x86_64`；设备上模块目录只有 `zygisk/x86_64.so` 与 `lib/libpreload64.so`（日志另有 `libpreload32.so ... No such file or directory`）。模拟器上番茄进程是 32 位 x86，因此 zygisk 没有 32 位模块可加载。
- **模块只带 arm64 的 DexKit，而宿主是 32 位进程**：`app/build.gradle` 的 `ndk { abiFilters 'arm64-v8a' }` 使模块 APK 只含 `lib/arm64-v8a/libdexkit.so`（DexKit AAR 本身有 4 个 ABI）。而番茄官网 7.3.9.32 包的 115 个原生库全部在 `lib/armeabi-v7a`，`primaryCpuAbi=armeabi-v7a`，进程必然是 32 位。32 位进程无法加载 64 位 so，DexKit 只在 arm64 宿主进程里可用。

按**当时只打包 arm64** 的构建配置，无法在该 arm32 宿主进程中加载 DexKit；现 `app/build.gradle` 已同时打包 `arm64-v8a` 和 `armeabi-v7a`，RMX2117 上已实际注入并发布目标快照，不能沿用当时“任何设备都装不起来”的结论。

附带噪声：该模拟器为 x86_64 + ARM 转译，番茄的 Pitaya/NSLinker/libCepEngine 等 arm 库报 `is for EM_ARM (40) instead of EM_386 (3)` 加载失败；即使日后注入成功，下载/解密链路在 x86 模拟器上的结果也不可信。

**当时未验证**：模拟器因缺 32 位 x86 LSPosed 未注入；此限制仍适用于该模拟器，但现在使用的是支持 32 位注入的 RMX2117 真机。真机判定见上方修复状态表。

## 原始修复建议（历史）

1. 先修正误拦整页及返回类型契约，避免把“没找到”变成页面破坏。
2. 按下载、弹窗、底栏、UI 分组重新定位，不直接批量替换混淆名；多个位置属于逻辑迁移，而不只是方法改名。
3. 为构造方法误命中、返回类型错误、多候选及 hookAllMethods 空集合添加可见诊断；保持现有 target key 协议和缓存架构。
4. 修复 Java/Finder/Hook 后按项目要求执行 `./gradlew.bat assembleDebug`，再实机核对“我的”页、章末、底栏和单书下载导出。

以上原始静态表只代表修复前基线；本轮已构建并进行上述限定范围的真机测试，但未验证的 Hook 和最终 XHTML→TXT 转换仍不能当作通过。

## 后续变更汇总（2026-10-05）

仓库分析目录只保留本文与 `project-handoff.md`；其他专项资料保留在本地、不随 GitHub 分发。本节汇总后续实现及验收边界，前面的修复前表格/旧快照仅为历史。

### 分类底栏

- 恢复原生 BookCategory，底栏目标顺序为书架/书城/分类/我的，继续隐藏短剧；不伪造短剧 Tab，不手工 addView。
- 新增分类门禁与 MainFragmentActivity.onCreate 方法 key。分类门禁通过 Resources.getBoolean 调用及 KmpCategoryFragment 工厂所在类定位并返回 false；Activity 初始化前、底栏构建前和路由配置更新后归一化类型列表。
- 借助宿主 hasCategoryTab 分支隐藏搜索旁入口并调整搜索空间。已构建安装，分类页面/顶部入口/刷新行为仍由用户验收。

### 方法级 DexKit 迁移与运行日志

- 新增五个独立方法 key：封面文字渲染、封面图片渲染、章节解码核心、快捷转换、快捷聚合。Finder 只写扫描结果；Hook 从 CachedTargets 取 Method，检查契约后注册。
- 文字方法按 BookCoverInfo 单参/void、bookName/bookShortName 字段读取及 TextView.setText 定位；图片方法按同类、BookCoverInfo+boolean/void、bookNameUrl 及文字方法调用定位。
- 解码核心按五参 String/DecryptKey/boolean/String/String→String、解压日志、UTF_8 及字节解码调用定位。不写死 x.a；旧 o.b 可能顺带匹配，但不维护旧版完整导出链。
- 快捷转换按 FunctionItemConverter 类特征、四参/List、FragmentActivity/Function1 定位；聚合按转换调用、前三参类型、CardType.COMMON 和 addAll 定位。不再写死 sd4.h/q1$a/b，移除旧 CardData/List 构造器回退。
- 7.3.9.32 全 DEX 静态五项唯一；重启后 PID 21843 日志证实 5/5 实际扫描、写入快照并挂载。封面两次回退文字，快捷源列表 4→2、聚合列表 7→3。
- 同一进程记录缓存补回 13 章并导出 TXT；没有读取正文，不能断言内容完整。解码回调没有独立触发日志，不能由缓存补回推定直接捕获路径已经单独验收。
- 当时快照 39/42，缺少书名点击、旧 Banner、排行榜三个目标。之后按用户要求删除旧书城 pictureData Banner 清空与未接入的排行榜 Hook，包括扫描、key 和函数；协议 42→40。书架短剧 Banner 和推荐流 RankListBook 策略保留。书名点击未修复。
- 保留风险：部分稳定 SDK/Reader/PopProxy/侧栏/热词构造器/ChapterInfo.a 等仍非方法级 DexKit；快捷 DTO 的 a/b 字段与 de4 模型白名单仍依赖新版形态。弹窗补充 a() 是 V597 首页延迟实验，不等于全局禁用；成功日志不能替代行为验证。

### 下载模式选择

- 新增 `method_download_click`，协议 40→41。按“点击下载权限判断: decision=”及实例九参/void 契约唯一定位公共下载入口；三个直接调用点为 KMP 详情、原生详情、阅读器。
- 主线程弹出“缓存 / 缓存并导出”，选择后通过 XposedBridge.invokeOriginalMethod 恢复同一接收者和全部原参数。保留宿主权限判断及回调，不伪造权益、不替换下载器；取消不启动原下载，并调用宿主取消回调。
- DownloadExportModes 使用独立 SharedPreferences `fq_download_export_requests`，仅保存按 bookId 的请求 ID，不存正文，不混入目标缓存或宿主下载设置。缓存删除该书导出意图，不捕获/补回/写 TXT，也不删除已存在的 TXT。
- 缓存并导出才允许激活捕获，在 FINISH 后执行现有补回、XHTML→纯文本和 TXT 写入；暂停/错误保留意图，取消/成功写入后清除。持久化支持进程重启恢复，多书分别记录。
- 请求 ID 与线程 claim 避免重复导出/旧任务覆盖新模式；最终写入及清理核对当前请求，与模式切换串行。入口缺失时默认仅缓存，不自动导出。
- 新入口静态唯一与三个调用点已核对；最新 assembleDebug --offline 成功（2s），无线覆盖安装 Success。弹窗、取消、两种模式、多书并行和暂停/重启恢复仍待用户验收，未代操作页面、未读取正文。
- 已缓存书再次选择导出是否产生 FINISH 由宿主原流程决定；不绕过原流程直接导出。后台已有任务恢复不自动弹窗。
