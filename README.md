# FuckTomato

FuckTomato 是一个面向番茄免费小说 `com.dragon.read` 的 Xposed 模块，用于精简界面、减少干扰内容，并保留常用阅读与下载体验。

## 功能特性

- 处理广告配置、常见弹窗、书架短剧 Banner 与部分福利入口。
- 拦截 AB 配置更新、软件更新检查，提供作者说、封面热评及章末控件的隐藏处理。
- 精简书城推荐流，清理原生/KMP 搜索框推荐热词，保留用户输入及通用提示。
- 精简“我的”页：隐藏钱包福利卡、推荐流和搜索入口，菜单位置替换为设置入口，过滤快捷功能项。
- 底栏调整为 **书架｜书城｜分类｜我的**，隐藏短剧 Tab，分类使用宿主原生页面。
- 封面图片书名回退文字；封面渲染、章节解码和快捷入口转换/聚合使用方法级 DexKit 定位。
- 下载时选择 **缓存 / 缓存并导出**：缓存保留原下载；缓存并导出在下载完成后生成 TXT。

## 适用环境

- Android 6.0（API 23）及以上设备或模拟器，已安装可用的 Xposed / LSPosed 环境。
- 目标宿主：番茄免费小说 `com.dragon.read`，当前以 **官网 7.3.9.32** 为适配基线，不保证旧版或其他渠道兼容。
- 模块包含 `armeabi-v7a` / `arm64-v8a` 的 DexKit 原生库；宿主进程位数与模块库必须匹配。
- 构建环境：JDK 17+、Gradle Wrapper、Android SDK（compileSdk 33）。配置 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties` 的 `sdk.dir`。

## 构建方式

Windows：

```powershell
.\gradlew.bat assembleDebug
```

Linux / macOS：

```bash
./gradlew assembleDebug
```

构建产物默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装使用

1. 构建或获取模块 APK。
2. 在设备上安装 APK。
3. 在 LSPosed / Xposed 管理器中启用模块。
4. 勾选作用域 `com.dragon.read`。
5. 强制停止并重新打开番茄免费小说；更新模块后也需要重启宿主，才能加载新 Hook。
6. 点击书籍下载入口，选择“缓存”或“缓存并导出”；取消选择不会启动下载。

### 下载模式

| 选项 | 行为 |
| --- | --- |
| 缓存 | 原样执行宿主下载，不启用模块的章节捕获、缓存补回或 TXT 写入 |
| 缓存并导出 | 原样执行宿主下载，下载完成后捕获/补回已解密章节并导出 TXT |

两种模式都保留宿主原权限判断、参数及回调，不自行伪造下载授权。导出选择按书籍保存；暂停或宿主重启后保留，取消下载或成功导出后清除。已有 TXT 不会因选择“缓存”被删除。

## 项目结构

```text
app/src/main/java/com/fuck/fanqie/
├── MainHook.java          # 模块入口
├── HookApplier.java       # Hook 分发
├── HookFinder.java        # DexKit 扫描入口
├── HookTargets.java       # 目标 key 常量
├── cache/                 # 目标缓存与快照
├── finders/               # Hook 目标扫描
└── hooks/                 # 运行时 Hook 实现
```

## 缓存机制

需要版本适配的目标由 Finder 通过 DexKit 扫描，统一交由 TargetRepository 发布快照，Hook 层通过 CachedTargets 读取。快照位于宿主私有目录：

```text
/data/user/0/com.dragon.read/files/target-cache-snapshot.json
```

当宿主版本变化或模块 APK 指纹变化时，缓存会自动刷新。

## 正文导出位置

只有选择“缓存并导出”时，模块才在下载完成后将捕获/补回的已解密章节整理为 TXT，文件名通常为书名：

```text
/storage/emulated/0/Download/FQ/<书名>.txt
```

实现上会优先直接写入公共下载目录的 `FQ` 文件夹；如果直写失败，Android 10 及以上会尝试通过 MediaStore 写入同一目录。EPUB XHTML 内容会经过标签、段落和实体转换；旧版本生成的文件不会自动迁移。

## 注意事项

- 宿主版本变化可能导致目标、数据字段或 UI 资源失效；部分稳定 SDK 挂钩及混淆字段/模型依赖尚未全部迁移。
- 首次运行或缓存刷新时需要重新扫描目标，启动耗时可能增加；找不到或无法唯一定位的新目标会明确记录并跳过。
- AB 更新拦截不等于关闭全部云控；弹窗处理也不保证覆盖所有入口。
- 旧书城图片 Banner 清空 Hook、排行榜移除 Hook 已删除；书架短剧 Banner 过滤和推荐流中的 RankListBook 允许策略保留。
- 已有日志验证：封面/解码/快捷入口新增五个目标实际命中并挂载，封面回退文字、快捷源列表 4→2、聚合列表 7→3，以及一次缓存补回 13 章后的 TXT 导出记录。
- 最新下载模式选择已构建、安装，弹窗、取消、两种模式及暂停恢复仍待实机验收。未读取正文验证导出内容完整性；安装或挂载成功不等于所有功能生效。
- 已缓存书再次选择导出是否产生完成事件，由宿主原流程决定；本实现不跳过原流程直接导出。
- 听书解密和听书导出运行时代码已移除。

详细适配及验收记录：

- [项目交接](analysis/project-handoff.md)
- [7.3.9.32 Hook 修复记录](analysis/hook-offset-review-7.3.9.32.md)

## 免责声明

本项目仅用于学习和研究 Xposed Hook、Android 逆向分析与模块化工程实践。使用者应自行承担使用风险，并遵守当地法律法规及相关应用服务条款。
