# FuckTomato

FuckTomato 是一个面向番茄免费小说 `com.dragon.read` 的 Xposed 模块，用于精简界面、减少干扰内容，并保留常用阅读与下载体验。

## 功能特性

- 处理广告配置、常见弹窗、书架短剧 Banner 与部分福利入口。
- 拦截 AB 配置更新与软件更新检查；提供作者说、封面热评及章末控件的隐藏处理。
- 精简书城推荐流，清理原生/KMP 搜索框推荐热词，保留用户输入及通用提示。
- 精简“我的”页：隐藏钱包福利卡、推荐流和搜索入口，菜单位置替换为设置入口，过滤快捷功能项。
- 底栏调整为 **书架｜书城｜分类｜我的**，隐藏短剧 Tab，分类使用宿主原生页面。
- 封面图片书名回退文字；封面渲染、章节解码和快捷入口转换/聚合使用方法级 DexKit 定位。
- 下载时可选 **缓存 / 缓存并导出**：缓存保留原下载流程；缓存并导出在下载完成后生成 TXT。

## 适用环境

- Android 6.0（API 23）及以上设备或模拟器，已安装可用的 Xposed / LSPosed 环境。
- 目标宿主：番茄免费小说 `com.dragon.read`，当前以 **官网 7.3.9.32** 为适配基线，不保证旧版或其他渠道兼容。
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

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 安装使用

1. 构建或获取模块 APK，安装到设备。
2. 在 LSPosed / Xposed 管理器中启用模块，勾选作用域 `com.dragon.read`。
3. 强制停止并重新打开番茄免费小说；更新模块后同样需要重启宿主才能加载新 Hook。
4. 点击书籍下载入口，选择“缓存”或“缓存并导出”；取消选择不会启动下载。

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
├── HookApplier.java         # Hook 分发
├── HookFinder.java          # DexKit 扫描入口
├── HookTargets.java         # 目标 key 协议
├── PackageInfoCompat.java   # 版本信息兼容
├── cache/                   # 目标缓存与快照（TargetRepository / CachedTargets）
├── finders/                 # Hook 目标扫描，产出 TargetScanResult
└── hooks/                   # 运行时 Hook 实现，统一经 CachedTargets 取目标
```

## 目标缓存机制

需要版本适配的目标由 `finders/` 通过 DexKit 扫描，统一交由 `cache/TargetRepository` 发布快照，Hook 层通过 `cache/CachedTargets` 读取，Finder 与 Hook 不互相越界访问。快照位于宿主私有目录：

```text
/data/user/0/com.dragon.read/files/target-cache-snapshot.json
```

宿主版本变化或模块 APK 指纹变化时，缓存会自动刷新；扫描不到或无法唯一定位的目标会记录并跳过。

## 正文导出位置

仅“缓存并导出”模式会在下载完成后，将捕获/补回的已解密章节整理为 TXT，文件名通常为书名：

```text
/storage/emulated/0/Download/FQ/<书名>.txt
```

实现上优先直接写入公共下载目录的 `FQ` 文件夹；直写失败时，Android 10 及以上回退 MediaStore 写入同一目录。EPUB XHTML 内容会经过标签、段落和实体转换。

## 注意事项

- 宿主版本变化可能导致目标、数据字段或 UI 资源失效；不保证旧版或其他渠道包可用。
- 首次运行或缓存刷新时需要重新扫描目标，启动耗时可能增加。
- AB 更新拦截不等于关闭全部云控；弹窗处理不保证覆盖所有入口。
- 模块仅作用于 `com.dragon.read`，请勿对无关应用启用。

## 免责声明

本项目仅用于学习和研究 Xposed Hook、Android 逆向分析与模块化工程实践。使用者应自行承担使用风险，并遵守当地法律法规及相关应用服务条款。
