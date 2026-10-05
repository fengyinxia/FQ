# 当前 FQ Hook 目标定位方式盘点

> 阅读顺序：下方原盘点/新旧矩阵属于迁移前历史；最新实现见文末“新版方法级 DexKit 迁移”。不能把旧行号或旧的“未迁移”结论套到当前源码。

## 范围与分类

- 范围为当前工作区 MainHook、HookApplier 调用的 Hook，以及下载 Hook；宿主样本沿用官网 7.3.9.32。
- 判断的是**挂钩目标从哪里来**，不是安装 API 名称。findAndHookMethod 也可能接收 DexKit 扫到的类；hookMethod 也可能接收纯反射得到的方法。
- A：挂钩目标本身没有经 Finder/CachedTargets；直接类名/方法名/构造签名，或在固定类内运行时反射匹配。
- B：类或主方法由 DexKit 缓存提供，但额外成员名/构造签名仍写死。
- C：类经 DexKit 定位，随后按运行时结构/签名挂钩相关成员，不等于完全绕过 DexKit。
- 以下是源码注册路径，不代表每项在当前设备上都安装或触发成功。此轮未修改源码、构建或安装。

## A：未经过 DexKit 的目标

| 功能 | 目标/获取方式 | 当前源码位置 |
| --- | --- | --- |
| 模块初始化 | Android ContextWrapper.attachBaseContext(Context)；固定框架 API | MainHook.java:66 |
| Forest 资源/上报调度 | 固定 com.bytedance.forest.utils.ThreadUtils；runInBackground(Runnable)、postInSingleThread 两重载、runInReportThread(Runnable)、postIdleTask(Runnable) | hooks/FrameworkHooks.java:25 |
| 埋点上报 | 固定 ReportManager；internalReport、onReport 三签名、postMsgAsync；Args 类型亦直接查找 | hooks/FrameworkHooks.java:42 |
| Crash 监控 | 固定 Npth；init/initSDK/initMiniApp/reportError/reportBizException/reportDartError/reportGameException/registerCrashCallback/setAttachUserData 按方法名挂所有重载；startOptMtkBuffer(int) 精确签名 | hooks/FrameworkHooks.java:60 |
| 弹窗补充开关 | com.dragon.read.pop.absettings.a.a() 写死；同函数的 KEY_POP_METHOD 主目标另经 DexKit | hooks/AdHooks.java:244 |
| 点击统计 | ClickAgent.onTabChanged(String)、onClick(View) 写死 | hooks/AdHooks.java:256 |
| 阅读器返回 | 固定 ReaderActivity.onBackPressed() | hooks/FeatureHooks.java:89 |
| PopProxy 弹窗 | 固定 PopProxy/IProperties/IListener 类；popup/enqueue 名称及五参数/返回契约运行时匹配，不经 DexKit | hooks/FeatureHooks.java:104 |
| 封面文字/图片书名 | 固定 bookcover.view.a.h(BookCoverInfo)、g(BookCoverInfo,boolean) | hooks/FeatureHooks.java:177 |
| 我的页侧栏配置 | 固定 GameRevisitPathV693Model；所有构造器及旧 b() Getter。单例 b/sideBarEnable 是配套字段读写，不是新增 Hook | hooks/UIHooks.java:205 |
| 我的页快捷入口聚合阶段 | 固定 mine.card.model.q1$a.b；再核对三参、List 返回。注册条件依赖上游 sd4.h 分支，但 q1$a 这个目标本身未经 DexKit | hooks/UIHooks.java:556 |
| 热词旧构造路径 | 固定 SearchCueWordExtend(SearchCueWord,String)；注意新加的原生/KMP 热词列表入口已由 DexKit 定位，不能把整个热词功能都算作未使用 DexKit | hooks/UIHooks.java:663 |
| 下载内容捕获 | 固定 ChapterInfo.a(readersaas...ItemContent,boolean) | hooks/download/DownloadHooks.java:46 |
| 解密结果捕获 | 固定 reader.utils.x.a(String,DecryptKey,boolean,String,String) | hooks/download/DownloadHooks.java:110 |

### 源码存在但当前未接入的路径

`FrameworkHooks.applyThreadUtilsHooks()` 直接按固定 ThreadUtils 类名挂 postInBackground/postInForeground/postEmergencyTask/runInBackground/runInMain，当前 apply() 中调用被注释。其实现只是记录 Runnable，不是阻断；不应计为本轮默认启用的 Hook。

## B：DexKit 定位了一部分，额外成员仍写死

| 功能 | 经 DexKit 的部分 | 仍写死的部分 |
| --- | --- | --- |
| 广告免除额外判定 | KEY_AD_FREE_CLASS | isVip、hasNoAdFollAllScene、isAnyVip、hasNoAdPrivilege、adVipAvailable、isNoAd(String)；主 KEY_AD_FREE_METHOD 另是方法级定位 |
| 我的页旧版附加卡片 | KEY_FEATURE_LIST_LOAD_CLASS | 接收 List 的构造器；7.3.9.32 的 sd4.h 分支不走此旧构造路径 |
| 我的页新版快捷卡初始转换 | KEY_FEATURE_LIST_LOAD_CLASS | 明确判断类名 sd4.h，按名称挂 b；字段 a/b、四参契约亦是版本依赖 |
| EPUB 封面热门评论页 | KEY_COVER_HOT_COMMENT_METHOD 定位主 View 工厂及所在类 | 同类 P1(): kotlin.Pair 按名字/签名额外挂钩；P1 没有独立 Finder 结果 |

## C：基于 DexKit 类的运行时结构挂钩

- VIP 信息模型：KEY_VIP_INFO_MODEL_CLASS 经 DexKit 定位，随后 hookAllConstructors。构造参数位置仍写死，是契约风险，但不属于整类未经 DexKit。
- 底栏配置刷新：KEY_TAB_ROUTE_HELPER_CLASS 经 DexKit 定位，在类内按 BookStoreAlignmentData 单参数、void 返回筛选更新方法；不写死 p/f/s 混淆方法名。主底栏构建、分类门禁、MainActivity.onCreate 都另有方法级缓存结果。

## 不要误算成 Hook 的反射依赖

- DownloadContentProcessor 中 ChapterOriginalContentHelper.i/c、目录服务 d/c 等，是反射调用/数据获取，未注册额外 Xposed Hook，但同样有版本适配风险。
- RecommendFilterHelper 中 BookUtils.isShortStory、NsCommonDepend.isListenType 是判断函数调用，不是挂钩。
- 我的页搜索/钱包/菜单用到 g64、feq、he1、f0l/f05 等混淆资源名；对应页面 Hook 来自 CachedTargets，这些属于 UI 资源依赖，不是“Hook 没过 DexKit”。
- 反射读写 DTO 字段、构造设置监听器、监听布局事件也不应按 Xposed Hook 目标数量计算。

## 迁移优先级建议

1. 优先迁移混淆类/方法组合：reader.utils.x.a、bookcover.view.a.g/h、pop.absettings.a.a、q1$a.b，以及仍写死 b/P1 的额外成员。
2. ChapterInfo.a 的方法名虽短，所属类和参数较明确，可作为下一批方法级定位；需要确认返回/参数契约，不只替换名字。
3. 稳定 SDK/API 名称不必为了“全部 DexKit”而增加扫描：ContextWrapper.attachBaseContext 是扫描前的引导入口，通常应该保留固定 Android API 挂钩；ClickAgent、Npth、ReportManager 等可按是否真的出现漂移决定。
4. 类级 DexKit 不保证字段和构造参数稳定；迁移应同时验证运行时契约，不把缓存命中当成行为通过。

本清单仅盘点，不代表已经执行以上迁移。

## 新旧版兼容性核对（2026-10-05）

### 样本及证据范围

- 旧版：`base.apk`，Manifest 为 `com.dragon.read` / 7.1.5.32 / versionCode 71532；SHA-256 `85a475e9af4412fbc7f1ffd68feb8f3278c4dab23b438fe0373020767b5fd80b`。
- 新版：`番茄小说_官网_7.3.9.32.apk`，Manifest 为 `com.dragon.read` / 7.3.9.32 / versionCode 73932；SHA-256 `fe35ea812f35cb23c5cad83cbd06828dada0864fc078e12486a312c38828a13b`。
- 本次重新遍历旧版全部 25 个、新版全部 23 个 DEX，只保留指定类的声明、字段类型、调用引用。证据为 `.work/non-dexkit-compat/{7.1.5.32,7.3.9.32}.json`，生成器为同目录 `DirectHookInventory.java`。这里的 missing 是全 DEX 查找结果，不是旧清单未收录。
- 当前新版设备只读 LSPosed 既有日志，最近启动证据为 2026-10-05 14:13:42、PID 23397。未操作宿主页面、未构建/安装模块、未降级安装旧宿主、未读取书籍正文。
- 表中“签名兼容”只表示当前注册方法能够匹配、返回值/关键字段类型符合回调要求，不能代替实机行为验收。旧版全部属于静态证据。

### 逐项矩阵

| 目标 | 7.1.5.32（旧） | 7.3.9.32（新） | 功能/契约判断 |
| --- | --- | --- | --- |
| ContextWrapper.attachBaseContext(Context) | Android 框架 API，不在宿主 DEX 内 | 同左；本机模块已进入初始化 | 固定框架引导入口，宿主版本不是其匹配条件；旧宿主注入仍未实机验证 |
| Forest ThreadUtils 五个具体签名 | 全部存在，返回 void | 全部存在，返回 void；最新日志已注册 | 地址兼容；无 Runnable 类型筛选，会同时截断正常 Forest 资源/任务调度，不能当成纯埋点过滤 |
| ReportManager 五个具体签名 | 全部存在，返回 void | 全部存在，返回 void；最新日志已注册 | 地址兼容；日志“全量”只代表代码注册结束，不能由此推出宿主所有上报通路都已关闭 |
| Npth 九个方法名组 + startOptMtkBuffer(int) | 共 21 个方法名匹配重载 + 一个 int 方法存在 | 同样 22 个声明存在；最新日志已注册 | int 返回 0 的契约正确；其余大多 void，但 initSDK 返回 monitor.f，代码将其置 null。null 对引用类型合法，调用方是否依赖非空对象未验证，不应标成完全无风险 |
| ClickAgent.onTabChanged(String)/onClick(View) | 两个方法均存在，void | 同左；最新日志已注册 | 指定点击统计入口可挂，不代表所有 SDK 统计接口都覆盖 |
| ReaderActivity.onBackPressed() | 本类声明，void | 本类声明，void；最新日志已注册 | 不涉及继承方法误匹配；当前回调在原处理后 finish，页面返回与状态保存未重新实机验收 |
| pop.absettings.a.a() | 存在，实例 boolean 方法 | 存在，静态 boolean 方法 | findAndHookMethod 不要求固定 static 修饰，因此均可挂；但实际语义见下节，不是通用“禁用弹窗”开关 |
| PopProxy.popup/enqueue | 当前反射规则匹配 3 个入口 | 当前规则同样匹配 3 个；最新日志安装数=3 | popup 的第三参从 silk.road.subwindow.b 变为 th0.b，现规则不锁该混淆类型，因此两版兼容。getID/getPrivateName 都为 String，listener.intercept 为 void；隐私放行及完整队列行为未重新验收 |
| bookcover.view.a.h(BookCoverInfo)/g(BookCoverInfo,boolean) | **两个签名都不存在，当前封面控制挂钩失败** | 两个方法存在，void；最新日志已注册 | 新版文字/图片书名控制可挂；旧版不能沿用。旧 BookCoverInfo 仍有 getBookShortName/getBookNameUrl，当前代码未保留其旧版分支 |
| GameRevisitPathV693Model 构造器/单例/旧 b() | 3 个构造器、b() 返回模型、静态 b 模型、sideBarEnable:boolean 都存在 | 3 个构造器、静态 b 模型、sideBarEnable:boolean 存在；旧 b() 不存在 | 新版日志：单例=true、构造器=3、旧 Getter=0；0 是预期差异，不代表整项未挂。但新版无取得 AB 结果后再次清理的旧 Getter 路径，后续反序列化回填或替换配置是否恢复 true 未验证。此配置也不等于右上菜单按钮的全部控制 |
| mine.card.model.q1$a.b(...) | **q1/q1$a 和 sd4.h 均不存在；不走此新版分支** | b(sw3.d,FragmentActivity,zx6.c):List 存在，符合三参/List 检查；最新日志聚合入口已注册 | 这是新版专用链路，不应把缺少 q1 简化为旧版整个快捷入口功能失效；旧版是否由 DexKit 类的 List 构造器路径处理，需独立验证，不在本表宣称成功 |
| SearchCueWordExtend(SearchCueWord,String) | 构造器存在，五个关键字段类型一致 | 同左；最新日志构造器已注册 | text/prefixText/displayText/displayTextV2 都是 String，isDefault 是 boolean；清理器契约兼容。但 JSON/KMP 可能绕过该构造器，本构造 Hook 单独不能保证全部热词被清理 |
| ChapterInfo.a(ItemContent,boolean) | 方法存在，返回 ChapterInfo；回调关键字段类型一致 | 同左 | 此捕获入口本身签名兼容；旧版后续内容处理依赖不兼容，不能从这一项推出旧版 TXT 导出正常 |
| reader.utils.x.a(String,DecryptKey,boolean,String,String) | **此签名不存在，当前解密结果捕获无法安装** | 方法存在，返回 String；最新日志已接入 | 新版源码还确认 args[3]=bookId、args[4]=chapterId，当前回调索引正确。旧版实际入口为 reader.utils.o.a(ChapterInfo,DecryptKey):String，当前没有旧版分支 |

### 已确认的语义及关联问题

1. **弹窗补充开关不等于屏蔽弹窗**：两版 a() 都读取 `V597PopsMigrationHomepageDelay.enable`；新版直接读取 key `v597_pops_migration_homepage_delay_trigger`，旧版经模板 getter 取得同一配置。替换为 true 是启用该迁移/首页延迟实验。实际入口拦截依赖另外的 PopProxy/缓存主目标，不能把这一个方法称为“所有弹窗都禁用”。此外 `applyFloatingViewHooks` 在 catch 后无条件输出“已禁用弹窗”，这条日志本身不构成安装成功证据。
2. **旧版下载链路并不完整兼容**：除了 x.a 缺失，当前 DownloadContentProcessor 使用 ChapterOriginalContentHelper.i(String,String):ChapterInfo 与 c(ChapterInfo):Single，旧版这两个确切签名均不存在。旧版分别存在 h1(String,String):ChapterInfo、c0(ChapterInfo):Single（另有 b0(ChapterInfo):String）。这些是反射调用而非新增 Hook，却会影响捕获后的处理；不是恢复解密 Hook 一个地址就能保证完整旧版导出。
3. **新版侧栏配置仍有持续生效边界**：单例写入和构造器注册已有证据，但不能证明每个反序列化/云控替换后的模型最终字段都保持 false。旧 Getter 不存在是明确差异；右上菜单换设置另属页面级 DexKit Hook，不用菜单是否显示来简单判定本配置 Hook 的安装成败。

### 新版运行证据与未验证项

- 最新 PID 23397 日志包含 Forest、ReportManager、Npth、ClickAgent、ReaderBack、PopProxy 三入口、封面渲染、侧栏三构造器、q1 聚合、热词构造器和新版解密入口的安装记录；本次筛选未发现该进程上述 SDK/Reader/PopProxy 的 FQ 安装失败记录。没有观察到失败不等于不存在调用方异常。
- 14:13:52—53 出现“已清理 KMP 搜索框热词 3 项”的真实处理记录，但这属于另加的 DexKit 列表入口，**不能据此证明非 DexKit 构造器路径被触发**。
- 既有文档曾记录新版快捷入口源列表 4→2、聚合列表 7→3 的页面验证；这不是本轮重跑验收，也不能推广到旧版。
- 未验证：旧宿主全部实机注册/页面行为；新版返回状态保存、隐私弹窗放行/票据及队列行为、正常 Forest 资源任务是否受损、Npth initSDK 的调用方空值处理、侧栏后续配置更新、封面最终展示和完整导出。
- 本轮结论：**不能认定这批 Hook 全部同时兼容新旧版**。明确旧版失配为封面 g/h 与解密 x.a；q1 属新版专用链路。稳定 SDK/API 的地址大体兼容，但仍须区分绑定成功、作用范围和业务副作用。未修改模块源码，未执行兼容迁移。

## 新版方法级 DexKit 迁移（2026-10-05）

用户确认不维护旧版分支，随后要求执行迁移。本节描述当前实现，前文为历史证据。

### 目标与职责

| 新 key | Finder 特征 | 7.3.9.32 唯一静态候选 |
| --- | --- | --- |
| method_cover_text_render | BookCoverInfo 单参、void、读 bookName/bookShortName、调用 TextView.setText(CharSequence) | bookcover.view.a.h |
| method_cover_image_render | 与文字渲染同类、BookCoverInfo+boolean、void、读 bookNameUrl、调用已定位文字方法 | bookcover.view.a.g |
| method_chapter_decrypt | String/DecryptKey/boolean/String/String → String；解压日志、UTF_8 字段、字节转字符串及 String+DecryptKey→byte[] 调用 | reader.utils.x.a |
| method_quick_access_convert | FunctionItemConverter 类特征；四参/List，第二参 FragmentActivity、第四参 Function1 | sd4.h.b |
| method_quick_access_aggregate | 三参类型来自转换方法；List；调用转换方法、读 CardType.COMMON、调用 ArrayList.addAll | q1$a.b |

- FeatureFinder、DownloadFinder、UIFinder 仅写 TargetScanResult；不直接改仓库。5 个目标只允许唯一候选，缺失/歧义明确日志并跳过，不盲取 first。
- FeatureHooks、DownloadHooks、UIHooks 从 CachedTargets.method 取得 Method 并校验返回/参数契约，再用 hookMethod 注册。
- 封面图片路径通过缓存文字 Method.invoke 回退文字，不再 callMethod("h")；Hook 层不查混淆渲染类。
- 快捷入口不再以 sd4.h 类名分流，也不再 hookAllMethods("b")。源转换和聚合独立注册。移除旧 CardData 扫描回退与 List 构造器 Hook；原 class_feature_list_load key 保留，记录已找到的转换器类，不改变旧 key 语义。
- 目标协议 37→42。安装后模块 APK 大小/时间改变会改变 moduleFingerprint，正常流程重新扫描；不手工写快照、不修改宿主配置。

### 剩余边界

- 本轮迁移的是注册目标，不是全量去混淆：快捷数据字段 a/b、允许模型 de4.v0/de4.x0/de4.i/de4.p0 仍按已验证新版形态过滤。目标匹配不保证未来版本这些数据形态也兼容。
- ChapterInfo.a 和内容处理器 i/c 仍未迁移。只维护新版；不添加旧 Getter、旧双参数解密或旧导出处理分支。
- 旧版解密包装器 o.a 实际委托给同五参的 o.b，后者也有字节解码、UTF-8 等核心特征。通用定位可能顺带匹配旧核心，这不代表承诺旧版完整导出兼容，也没有刻意按版本号封锁它。

### 验证记录

- `.work/non-dexkit-migration/MatchInventory.java` 按字段读取/调用/签名/字符串等价约束遍历官网 APK 全部 23 个 DEX，五项各唯一；结果 `.work/non-dexkit-migration/matches-7.3.9.32.txt`。这是静态证据，不冒充实际 Android DexKit 查询或 Hook 触发。
- DexKit 2.0.7 的 UsingType、addUsingField/addInvoke、MethodData 参数/声明类 API 已核对实际依赖。
- `ANDROID_HOME=E:/sdk ANDROID_SDK_ROOT=E:/sdk` 下 `gradlew.bat assembleDebug --offline` 成功（BUILD SUCCESSFUL，1s）。
- 无线 `192.168.2.91:40661` 覆盖安装 `app/build/outputs/apk/debug/app-debug.apk` 成功（Success）。未强停/启动宿主、未操作页面、未读取正文。
- 初次安装时页面及实际 DexKit 命中待验收；后续日志证据见下一节。

## 安装后日志验收（2026-10-05 16:13—16:14）

只读无线设备日志与快照，当前宿主主进程 PID 21843，hostVersion 73932，moduleFingerprint `20405:1543855:1791187842000`。未操作页面、未读取导出正文。

- **实际 DexKit 命中与挂载：5/5 通过。** 16:13:47—49 Finder 分别记录五个新增方法 key；新快照中五项 descriptor 与静态预期完全一致。16:13:50 有两个封面、快捷转换/聚合、章节解码核心的对应 Method 安装日志。这已超出此前纯静态证据。
- **封面：有真实触发。** 16:14:11.929、16:14:12.119 两次记录“封面书名已回退为文本”；证明图片 Hook 调用了缓存文字 Method，回调执行完成。最终视觉样式仍由用户判断。
- **快捷入口：有真实过滤。** 16:13:58 源列表候选 4、允许 2；聚合列表候选 7、保留 3。无“未识别允许项/保留原列表”记录。
- **下载：有完成/导出流程记录。** 16:14:15 检测下载完成，16:14:16 从本地缓存补回 13 章并记录 TXT 导出完成。未读文件正文，因此不判断章节内容完整性。解码核心有安装证据，但当前回调没有独立触发日志；“缓存补回并导出”不能当成直接捕获回调已触发的单独证明。
- 当前进程日志未见 FQ 失败、契约不匹配或异常记录；同时筛选该 PID 的其他 Exception/NoSuchMethod/NoSuchField/Error 摘要为 0。仅是本段日志观察，不代表全应用永远无异常。
- 快照 **39/42**：缺少已有 `method_book_name_click`、`method_filter_banner`、`method_remove_rank`，不是本次五个新 key 缺失。未在本轮修复这三个目标，不应宣称全部 Hook 已通过。

结论：本轮五项定位/挂载验收通过，封面和快捷入口有实际触发证据；下载流程已记录导出，但正文完整性和解码直接捕获路径仍未单独验证。

## 移除旧 Banner 与排行榜 Hook（2026-10-05）

用户明确要求移除两项，不是修复匹配条件：

- 删除 HookTargets 的 method_filter_banner、method_remove_rank 及 ALL_KEYS 条目，协议 42→40。
- UIFinder 不再扫描 CellViewData+int→BaseInfiniteModel 的旧 Banner 转换入口，也不再沿该入口查找 RankMixContentModel 工厂；保留三参数 CellViewData 的推荐流过滤目标。
- AdHooks 删除 applyHideBannerHooks 及初始化调用，不再清空旧书城卡片 pictureData。书架短剧 Banner 的独立 KEY_BOOKSHELF_BANNER_RESPONSE_METHOD 与过滤代码不变。
- UIHooks 删除此前未接入初始化的 applyRemoveRankHooks；不改推荐流 RankListBook 的允许策略，不删除宿主排行榜页面。
- 前文 39/42 的三项缺失属于移除前快照，不代表本次删除后仍存在 Banner/排行榜缺失问题。书名点击未在本轮修复。
- Java 源码范围内两项 key/函数的残留引用为 0；assembleDebug --offline 成功（1s）；无线 192.168.2.91:40661 覆盖安装成功（Success）。未操作宿主页面，删除后的实际快照由用户重启后更新，尚未观察。
