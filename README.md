# FuckTomato

FuckTomato 是一个面向番茄免费小说 `com.dragon.read` 的 Xposed 模块，用于精简界面、减少干扰内容，并保留常用阅读与下载体验。所有功能均可在宿主设置内的 **FQ 插件设置** 页面中开关。

## v2.4.8 更新

- 新增搜索分类及综合结果过滤、后台适配扫描和宿主主题同步。
- 补齐原生我的页预约、点赞、小游戏等动态入口的发现与过滤。
- 修复原生侧栏替换设置失效、首次进入我的页遍历 APK 导致卡顿。
- 修复 Release 混淆导致原生卡片和 KMP 头部 Hook 跳过；发布工作流校验 Function0/Function1 原名，失败时禁止发布。

## 功能特性

### 宿主内设置页

- 在番茄自身的设置列表中插入 **FQ 插件设置** 入口（原生与 KMP 设置页均支持）。
- 单页三组分类：**内容屏蔽 / UI 净化 / 拓展**，开关即时保存，重启番茄后生效；页面右上角提供一键重启。
- 配置与运行快照分离：保存失败自动回滚，单项损坏不阻断其他功能与设置入口。

### 内容屏蔽

- 广告拦截、书架短剧 Banner、运营弹窗（隐私弹窗放行）。
- **推荐流精简**：动态目录按业务类型发现项目（书荒入口、听书、短剧、漫画及各类普通卡片），勾选屏蔽，未识别类型默认放行。
- **搜索结果过滤**：动态发现搜索顶栏分类，勾选后隐藏对应 Tab，并从综合结果中移除已识别的所属内容；综合保留，未知内容放行，保存后重启生效。

### UI 净化

- **我的页**：动态多选隐藏钱包、会员、快捷功能、推荐流等卡片；原生与 KMP 页面均覆盖。
- 搜索净化（隐藏我的页搜索入口、清理搜索推荐词、隐藏搜索页热榜）、隐藏红点。
- **顶部频道精简**：动态目录发现书城顶部频道，勾选屏蔽，保留原选中项映射。
- 侧栏入口改为设置；封面使用完整书名文字；隐藏封面热门评论（含 EPUB）与章末社区区域（作者说、讨论、礼物）。

### 拓展

- **底栏自定义**：显示/隐藏、长按拖拽排序、主页选择同框编辑；赚钱 Tab 与金币福利门禁联动（隐藏赚钱即禁用福利入口，显示则完全恢复宿主原判断）。
- 下载与导出：下载时可选 **缓存 / 缓存并导出**，后者在完成后生成 TXT。
- 阅读器返回行为、屏蔽宿主更新检查、阻止 AB 配置更新、上报控制（行为/崩溃）、阻止推送进程、拦截 Forest 后台任务。

## 适用环境

- Android 10（API 29）及以上设备或模拟器，已安装可用的 Xposed / LSPosed 环境。
- 目标宿主：番茄免费小说 `com.dragon.read`，当前以 **官网 7.4.3.32** 为适配基线，不保证旧版或其他渠道兼容。
- 模块包含 `armeabi-v7a` / `arm64-v8a` 的 DexKit 原生库；宿主进程位数与模块库必须匹配。
- 构建环境：JDK 17+、Gradle Wrapper、Android SDK（compileSdk 33）。配置 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties` 的 `sdk.dir`。

## 构建

Windows：

```powershell
.\gradlew.bat assembleDebug
```

Linux / macOS：

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。推送 `v*` 标签时 GitHub Actions 自动构建签名 release 包并发布到 Releases。

## 安装使用

1. 构建或获取模块 APK，安装到设备。
2. 在 LSPosed / Xposed 管理器中启用模块，勾选作用域 `com.dragon.read`。
3. 强制停止并重新打开番茄免费小说；更新模块后同样需要重启宿主才能加载新 Hook。
4. 打开 番茄 **设置 → FQ 插件设置**，按需开关；修改保存后通过页面右上角"重启"按钮或手动重启番茄生效。
5. 动态目录（推荐流/搜索结果/我的页/顶部频道/底栏）需先浏览对应页面完成项目发现，再回到设置勾选屏蔽；历史隐藏项始终可在目录中恢复。

### 下载模式

| 选项 | 行为 |
| --- | --- |
| 缓存 | 原样执行宿主下载，不启用模块的章节捕获、缓存补回或 TXT 写入 |
| 缓存并导出 | 原样执行宿主下载，下载完成后捕获/补回已解密章节并导出 TXT |

两种模式都保留宿主原权限判断、参数及回调，不伪造下载授权。导出选择按书籍保存；暂停或宿主重启后保留，取消下载或成功导出后清除。已有 TXT 不会因选择“缓存”被删除。

## 项目结构

```text
app/src/main/java/com/fuck/fanqie/
├── MainHook.java            # 模块入口
├── HookApplier.java         # Hook 分发（设置入口优先、业务组隔离安装）
├── HookFinder.java          # DexKit 扫描入口
├── HookTargets.java         # 目标 key 协议
├── PluginSettings.java      # 用户设置（独立私有偏好，运行快照冻结）
├── DynamicItems.java        # 动态项目目录（我的页/推荐/搜索/顶部频道发现与屏蔽）
├── NavigationSettings.java  # 底栏布局（显示/排序/主页原子保存）
├── RestartHostActivity.java # 独立进程重启承接（Binder 死亡握手）
├── cache/                   # 目标缓存与快照（TargetRepository / CachedTargets）
├── finders/                 # Hook 目标扫描，产出 TargetScanResult
└── hooks/                   # 运行时 Hook 实现，统一经 CachedTargets 取目标
    ├── PluginSettingsPage.java  # 宿主内单页设置
    ├── SettingsEntryHooks.java  # 宿主设置入口注入
    ├── MineItemFilter.java      # 我的页业务枚举识别
    └── NavigationEditor.java    # 底栏拖拽编辑器
```

## 目标缓存机制

需要版本适配的目标由 `finders/` 通过 DexKit 扫描，统一交由 `cache/TargetRepository` 发布快照，Hook 层通过 `cache/CachedTargets` 读取，Finder 与 Hook 不互相越界访问。用户配置（`PluginSettings` / `DynamicItems` / `NavigationSettings`）与目标缓存完全独立。快照位于宿主私有目录：

```text
/data/user/0/com.dragon.read/files/target-cache-snapshot.json
```

缓存有效时直接安装 Hook，不创建 DexKit。首次使用、缓存缺失、宿主版本或模块 APK 指纹变化时，进入前台自动显示扫描弹窗；扫描与原生资源释放均在后台线程完成，成功保存后点击“重启”生效，本次不混装新旧业务 Hook。失败可以重试，不清除用户设置；扫描不到或无法唯一定位的目标会记录并跳过。

## 正文导出位置

仅“缓存并导出”模式会在下载完成后，将捕获/补回的已解密章节整理为 TXT，文件名通常为书名：

```text
/storage/emulated/0/Download/FQ/<书名>.txt
```

实现上优先直接写入公共下载目录的 `FQ` 文件夹；直写失败时回退 MediaStore 写入同一目录。EPUB XHTML 内容会经过标签、段落和实体转换。

## 注意事项

- 宿主版本变化可能导致目标、数据字段或 UI 资源失效；不保证旧版或其他渠道包可用。
- 首次运行或缓存刷新时需要重新扫描目标，启动耗时可能增加。
- AB 更新拦截不等于关闭全部云控；弹窗处理不保证覆盖所有入口。
- 动态目录按稳定业务 ID 记录项目，不保存书名、正文或账号内容。
- 模块仅作用于 `com.dragon.read`，请勿对无关应用启用。

## 免责声明

本项目仅用于学习和研究 Xposed Hook、Android 逆向分析与模块化工程实践。使用者应自行承担使用风险，并遵守当地法律法规及相关应用服务条款。
