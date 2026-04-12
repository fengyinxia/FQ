# FuckTomato

`FuckTomato` 是一个面向番茄免费小说 `com.dragon.read` 的 Xposed 模块，目标是减少干扰内容、精简界面，并保留核心阅读与下载体验。

## 当前能力

### 广告与弹窗

- 禁用广告配置与部分免广告判定
- 屏蔽 LuckyDog、常见弹窗、部分 Banner
- 拦截点击统计、埋点上报与崩溃监控初始化

### 阅读页与功能行为

- 禁用 ABTest
- 屏蔽更新检查与更新消息
- 禁用作者说、封面热评、章末热评
- 禁用章末礼物、评论等控件
- 调整部分阅读页返回行为

### 首页 / 书城 / 搜索

- 精简首页顶部频道
- 过滤书城推荐流中的听书样式卡片
- 清空搜索提示词
- 过滤搜索页部分热搜/推荐内容

### 我的页与底栏

- 过滤“我的”页附加功能卡片
- 去掉红点
- 禁用“我的”页侧边栏，回退为设置按钮模式
- 去掉“我的”页 VIP 入口，并改写部分 VIP 展示数据
- 禁用“我的”页动态搜索入口构建
- 调整底部 Tab 顺序，并隐藏视频 Tab

### 下载导出

- 监听下载状态
- 捕获下载章节正文与解密结果
- 下载完成后回填本地缓存目录内容
- 自动整理并导出 TXT

## 当前架构

项目已切到新的 target 缓存架构：

```text
MainHook
  -> HookFinder
  -> TargetRepository
  -> CachedTargets
  -> HookApplier
```

职责边界：

- `finders/`：只负责 DexKit 扫描，产出 `TargetScanResult`
- `cache/TargetRepository`：负责快照加载、发布、解析
- `cache/CachedTargets`：负责 Hook 层统一读取、运行时 memoization、失败抑制
- `hooks/`：只通过 `CachedTargets` 读取目标

## 缓存说明

- 快照文件名：`target-cache-snapshot.json`
- 宿主私有目录：

```text
/data/user/0/com.dragon.read/files/target-cache-snapshot.json
```

缓存刷新条件：

- 宿主版本变化，或
- 模块 APK 指纹变化

## 目录概览

- `app/src/main/java/com/fuck/fanqie/MainHook.java`：模块入口
- `app/src/main/java/com/fuck/fanqie/HookApplier.java`：统一分发各组 Hook
- `app/src/main/java/com/fuck/fanqie/HookFinder.java`：统一组织 DexKit 扫描
- `app/src/main/java/com/fuck/fanqie/HookTargets.java`：target key 协议
- `app/src/main/java/com/fuck/fanqie/finders/`：目标扫描
- `app/src/main/java/com/fuck/fanqie/cache/`：快照缓存
- `app/src/main/java/com/fuck/fanqie/hooks/`：运行时 Hook

## 构建

```bash
./gradlew.bat assembleDebug
```

## 当前已知限制

- 排行榜移除 Hook 代码仍在，但当前未接入 `UIHooks.apply()`
- “我的”页搜索当前只拦动态入口；若宿主切到固定搜索入口，这部分不会生效
- 听书解密 / 导出运行时代码已移除，仓库只保留相关分析文档
