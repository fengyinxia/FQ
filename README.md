# FuckTomato

FuckTomato 是一个面向番茄免费小说 `com.dragon.read` 的 Xposed 模块，用于精简界面、减少干扰内容，并保留常用阅读与下载体验。

## 功能特性

- 屏蔽广告配置、常见弹窗、部分 Banner 与福利入口。
- 禁用 ABTest、更新检查、作者说、热评、章末礼物和评论控件。
- 精简首页、书城、搜索页和“我的”页的部分展示内容。
- 调整底部 Tab 顺序，并隐藏视频 Tab。
- 监听下载状态，捕获章节正文，下载完成后自动导出 TXT。

## 适用环境

- Android 设备或模拟器。
- 已安装可用的 Xposed / LSPosed 环境。
- 目标宿主：番茄免费小说 `com.dragon.read`。
- 构建环境：JDK 17+、Android Gradle Plugin、Gradle Wrapper。

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
5. 强制停止并重新打开番茄免费小说。

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

模块会通过 DexKit 扫描宿主中的目标类和方法，并将结果写入宿主私有目录下的快照文件：

```text
/data/user/0/com.dragon.read/files/target-cache-snapshot.json
```

当宿主版本变化或模块 APK 指纹变化时，缓存会自动刷新。

## 正文导出位置

下载完成后，模块会将捕获到的解密章节正文整理为 TXT 文件，文件名通常为书名：

```text
/storage/emulated/0/Download/FQ/<书名>.txt
```

实现上会优先直接写入公共下载目录的 `FQ` 文件夹；如果直写失败，Android 10 及以上会尝试通过 MediaStore 写入同一目录。

## 注意事项

- 本项目仅面向指定宿主包名，宿主版本变化可能导致部分 Hook 失效。
- 首次运行或缓存刷新时需要重新扫描目标，启动耗时可能增加。
- 排行榜移除 Hook 代码目前未接入运行流程。
- “我的”页搜索当前只处理动态入口，固定入口可能不受影响。
- 听书解密和听书导出运行时代码已移除。

## 免责声明

本项目仅用于学习和研究 Xposed Hook、Android 逆向分析与模块化工程实践。使用者应自行承担使用风险，并遵守当地法律法规及相关应用服务条款。
