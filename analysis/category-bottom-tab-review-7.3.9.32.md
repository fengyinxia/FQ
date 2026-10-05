# 官网 7.3.9.32 分类入口移到底栏：可行性与难度

## 结论

**可实现，推荐恢复原生 BookCategory Tab，难度中等偏低；配置兼容部分按中等工作量对待。**

这里不是把搜索旁的 View 移到另一个父布局，也不是新建模块自己的分类 Fragment。宿主原生已支持分类底栏、页面创建及导航识别，应利用已有机制。最初仅分析；用户随后批准恢复分类 Tab 并隐藏顶部入口，现已实现，详见末尾后续实现节。

## 已确认的原生能力

- `com.dragon.read.rpc.model.BottomTabBarItemType` 存在 `BookCategory`，不是需要动态构造的新枚举。
- `MainFragmentActivity.c2` 的 BookCategory 分支创建原生底栏按钮，绑定 `id/as4` 并设置原生分类标题。
- `MainFragmentActivity.u2(BottomTabBarItemType)` 按类型惰性取得/创建 Fragment；BookCategory 分支调用 `NsCategoryApi.IMPL.configService().a()`。因此有真正的主页面容器接入，不必把 Activity 跳转伪装成 Tab。
- 主界面现有映射、页面判定和导航处理也识别 BookCategory，不只是底栏里留了一个名字。
- 搜索旁分类按钮的原回调调用 `NsBookmallDepend.IMPL.turnToCategoryTab(context, true, true)`。
- `NewBookMallFragment.uh(...)` 以及后续更新逻辑检查 `NsBookmallDepend.IMPL.hasCategoryTab()`，证明宿主已有“顶部分类入口/底栏分类 Tab”相关布局分支。恢复时应沿已有分支核对顶部入口是否正确隐藏和搜索框是否扩展，不能只伪造判断为 true 后让分类入口丢失。

## FQ 已有可复用的切入点

`BottomTabHooks` 已通过 CachedTargets 使用：

- `KEY_TAB_ROUTE_HELPER_CLASS`：路由配置帮助类。
- `KEY_TAB_METHOD`：底栏构建方法。

分析时的旧代码在构建前处理帮助类静态类型列表 `e`，只过滤和排序，不新增分类。后续实现已在存在 BookStore 的路由中补 BookCategory 并去重，PREFERRED_ORDER 改为 BookCategory 排在 MyProfile 前。宿主自身负责按钮、布局及导航索引。

建议最终顺序：`书架 / 书城 / 分类 / 我的`，继续保持 VideoSeriesFeedTab 隐藏。实际排序是产品选择，不应把枚举 value 或缓存 key 含义改成索引。

## 真正的难点

### 1. 类型列表与配置刷新一致

路由帮助类维护 `BookStoreAlignmentData`，其更新逻辑：

1. 存在 `tabBarList` 时优先从条目提取 tabType，重建类型列表。
2. 否则才从 `tabBarTypes` 重建列表。
3. 类内还有按类型获取 BottomTabBarItemData 的路径，涉及标题/图标等元数据。

所以不能只修改 tabBarTypes、只 addView，或在启动时改一次静态列表便宣称稳定。应在配置归一化/底栏构建前统一保证 BookCategory 存在且不重复，同时核对原生图标缺元数据时的处理；对刷新或缓存恢复继续保持一致。

### 2. 初始化时序及导航状态

- 主页面已有惰性分类 Fragment 工厂，降低了新增页面的难度。
- 仍需保证路由类型、底栏按钮、选中态、已有页面映射在同一版本配置下工作。
- 不依赖“第三个按钮就是分类”等位置硬编码，避免书架/书城/我的点击错位。
- 不将原有短剧类型只改显示文案为分类，否则点击/深链仍可能打开短剧。

### 3. 真正“移动”顶部入口

先让底栏分类能正常创建并打开，再处理顶部入口：

- 优先利用宿主 hasCategoryTab 的既有布局分支。
- 如果当前分支不会在配置改变后重新布局，再局部隐藏分类入口及整理搜索框占用空间。
- 不能为了隐藏顶部按钮全局替换搜索框/整页方法。
- 分类页创建失败时保留原入口并记录诊断，避免所有分类入口都丢失。

### 4. 宿主版本兼容

原生枚举/服务类型相对稳定，但路由帮助类、更新方法及布局字段可能混淆变化。实际实现应沿现有 Finder/TargetRepository/CachedTargets 边界用 DexKit 定位新目标（如确需），唯一命中才安装；不改现有 target key 含义。

## 方案比较

| 方案 | 难度 | 判断 |
| --- | --- | --- |
| 恢复原生 BookCategory Tab | 中等偏低；刷新兼容为中等 | 推荐；宿主已有按钮、页面、导航机制 |
| 手工加一个底栏快捷按钮，点击打开分类 | 低到中等，但布局/选中态仍需适配 | 不是真正 Tab，没必要绕开现成能力 |
| 动态造新枚举或模块自建 Fragment 注入 | 高 | 无必要；生命周期、索引和类加载风险更大 |

## 最小闭环与验收

1. 在底栏数据源中恢复 BookCategory，去重，设定四项顺序并保留短剧隐藏。
2. 用宿主原生按钮和惰性 Fragment 工厂确认分类能打开；不先删除顶部入口。
3. 确认顶部分类入口隐藏/搜索栏布局后，形成真正的入口迁移。
4. Java/Finder/Hook 修改后 assembleDebug；由用户验证四个 Tab 的点击、选中态、返回、分类列表、冷启动/重开、配置刷新及夜间样式。
5. 验证只限这些直接相关场景，不默认全仓 lint 或端到端扫描。

## 证据

- `.work/hook-review-7.3.9.32/dex-inventory.json`：BottomTabBarItemType 枚举。
- `.work/hook-repair-7.3.9.32/MainFragmentActivity.java`：BookCategory 按钮、u2 的分类 Fragment 工厂及主页面判定。
- `.work/hook-repair-7.3.9.32/NewBookMallFragment.java`：顶部分类回调及 hasCategoryTab 布局判断。
- `.work/hook-repair-7.3.9.32/TabRouteHelper.java`：tabBarList/tabBarTypes、类型列表重建及元数据读取。
- `app/src/main/java/com/fuck/fanqie/hooks/BottomTabHooks.java`：当前排序/过滤逻辑，仅处理已存在类型。

## 后续实现：恢复分类底栏并移除顶部分类入口

- 新增 `KEY_CATEGORY_TAB_DISABLED_METHOD` / `method_category_tab_disabled`：DexKit 在拥有零参 KmpCategoryFragment 工厂的类中，唯一定位零参 boolean 且调用 Resources.getBoolean(int) 的方法。7.3.9.32 为 `k74.a.g()`，其返回 true 时原生底栏工厂直接拒绝创建分类按钮，hasCategoryTab 也返回 false。Hook 仅让这道分类门禁返回 false，不全局修改 Resources 或其他云控。
- 新增 `KEY_MAIN_ACTIVITY_ON_CREATE_METHOD` / `method_main_activity_on_create`：DexKit 按主 Activity 稳定类型及 onCreate(Bundle): void 完整签名唯一定位。进入该方法时再安装配置刷新 Hook、归一化路由，避免在 attachBaseContext 阶段强行初始化需要宿主 Context 的路由帮助类。当前 target 协议共 37 个 key，既有 key 含义不变。
- BottomTabHooks 在主界面初始化前、底栏构建前、路由帮助类接收 BookStoreAlignmentData 的 void 单参数更新入口执行后，对静态类型列表 e 统一处理。跟随 tabBarList/tabBarTypes 的宿主重建结果，而非篡改整包配置或猜测新增图标元数据。补入 BookCategory、去重、按书架/书城/分类/我的排序，保留原短剧过滤；没有 BookStore 的路由不盲加分类。
- 已额外核对真实门禁：`NsBookmallDependImpl.hasCategoryTab()` 为 e.contains(BookCategory) 且分类门禁为 false；`MainBottomTabCenter` 的 BookCategory 分支在解除门禁后创建原生 dk7.j；分类工厂直接创建 KmpCategoryFragment。证据新增 `.work/category-bottom-tab/BookmallDependImpl.java`、`BottomTabFactory.java`、`CategoryConfig0.java`。
- 顶部入口不使用额外全局隐藏 Hook：在书城 NewBookMallFragment.uh 中，hasCategoryTab 为 true 时，原生代码执行 Hh() 后将分类入口设 GONE，负责搜索框布局。借助初始化前的路由归一化与分类门禁拦截走这一分支。
- 缺失/不兼容的门禁目标会明确记录，并不启用强制新增分类；配置刷新入口失败也输出方法诊断。安装成功仍不等于点击和页面行为验收。
- 本轮 `assembleDebug --offline` 已通过，Debug APK 无线安装 RMX2117 返回 `Success`；四个 Tab 的点击、分类数据、顶部入口消失、搜索布局及冷启动/刷新由用户验证，未替用户操作页面。
