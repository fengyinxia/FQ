# 官网 7.3.9.32 CommonAbResult 云控开关清单

## 范围与结论

- 样本沿用用户提供的 `番茄小说_官网_7.3.9.32.apk`，不代表官网未来版本。
- 本清单针对 FQ `KEY_ABTEST_METHOD` 命中的 `CommonAbResult.updateCommonAbResult(CommonAbResultData): void`，不是宿主全部远程配置的总表。
- `CommonAbResultData` 共 77 个声明字段，其中 3 个静态元数据字段、**74 个业务实例字段**；顶层 **28 个 boolean 标记**。boolean 中既有功能开关，也有用户分桶/权限/回退标记，不能一律理解为 true 开启功能。
- 更新函数直接读取 **45 个响应字段**，其中 **18 个顶层 boolean**，还读取嵌套开关与参数对象；同时序列化整包响应到 `key_common_ab_result_json`。没有逐字段读取，不代表完全不受缓存更新影响。
- 当前 FQ 在方法执行前 `setResult(null)`，跳过该次状态更新、缓存写入和末尾更新通知；**不会清除既有缓存，也不会拦截静态初始化、单独 setter 或独立 SsConfigMgr 云控**。
- 用途说明基于字段、赋值、已核对调用链，不等于每项消费者都做过行为验证；本轮未读取设备实际配置值。

## 该更新函数直接读取的 18 个顶层布尔标记

| 响应字段 | CommonAbResult 状态 | 用途/语义边界 |
| --- | --- | --- |
| `ecomRevert` | `isECRevert` | 电商回退标志 |
| `gameRevert` | `isGameRevert` | 游戏回退标志 |
| `vipRevert` | `isVipRevert` | VIP 回退标志，不是服务端付费权益 |
| `onlyNaturalEcomRevert` | `onlyNaturalEcomRevert` | 自然流量电商回退标志 |
| `mallEntranceRevert` | `mallEntranceRevert` | mall 入口回退标志 |
| `bookmallRevert` | `bookMallRevert` | 书城回退标志；我的页推荐门禁会读取它 |
| `enableDoubleBookName` | `enableDoubleBookName` | 双书名展示标志 |
| `useImgDiskCache` | `infiniteImgCustomCache` | 推荐图片自定义磁盘缓存 |
| `isEcomRelatedUser` | `isEComRelatedUser` | 电商相关用户分桶 |
| `continueWindowAfterBookmallTab` | `delayRecentWatchToSeriesMallFirstReq` | 最近观看/短剧书城首请求延迟标记 |
| `hasVideoFollowTab` | `hasCollectionChannel` | 视频关注/合集频道标记 |
| `isDefaultDoubleColUser` | `isDefaultDoubleColUser` | 默认双列用户分桶 |
| `isVideoSingleTagUser` | `isVideoSingleTagUser` | 视频单标签用户分桶 |
| `isVideoMuteUserGroup` | `isVideoMuteUserGroup` | 视频静音用户分桶 |
| `isVideoMute` | `isSingleBookDramaLandingMute` | 单书短剧落地静音标志 |
| `dislikeShowInAuthorPage` | `showAuthorDislike` | 作者主页“不感兴趣”入口 |
| `showAllTagsWhenDescExpand` | `showAllTagsWhenDescExpand` | 简介展开显示全部标签 |
| `innerShowAllTagsWhenDescExpand` | `innerShowAllTagsWhenDescExpand` | 内层简介展开显示全部标签 |

`revert` 的具体业务反向关系需看消费者，不能据字段名直接批量设 true/false。

## 响应另声明的 10 个顶层布尔项

这些字段没有在该函数中被逐项直接读取；响应整包仍参与序列化缓存，其他消费者也可能直接读取响应。

| 字段 | 字段层面的含义 |
| --- | --- |
| `audioReadHistorySquarePic` | 听书历史方图样式 |
| `audioSquarePic` | 听书方图样式 |
| `useRectLikeSquarePic` | 类方图矩形封面样式 |
| `dispatchPaidBook` | 付费书分发标志 |
| `enableBookHungerGuide` | 书荒引导标志 |
| `enterReader` | 进入阅读器标志 |
| `isPubPayUser` | 出版付费用户标记 |
| `novelHasWishListSchema` | 小说心愿单跳转标记 |
| `replaceCategoryTab` | 分类 Tab 替换标记 |
| `showVip` | VIP 展示标记 |

## 嵌套开关与布局参数

### 更新函数明确消费的嵌套布尔项

| 路径 | 更新到的状态 | 功能 |
| --- | --- | --- |
| `mineEntranceData.showUgcVideoSeriesEntrance` | `isUploadVideoVisible` | 我的页 UGC 视频/短剧入口 |
| `statDataConfig.hidePlayCnt` | `hidePlayCount` | 隐藏播放量；getter `showPlayCount()` 返回其反值 |
| `statDataConfig.hideOtherCnt` | `hideOtherCount` | 隐藏其他统计量；getter `showOtherCount()` 返回其反值 |
| `ttsReverseAbData.disableTtsEntry` | `disableTts` | 听书入口禁用标志，不是统一禁止所有 TTS 请求 |

### 被该函数保存的参数对象中可见的开关

- `reader1colData.enable`、`reader1colData.useCardStyle`：阅读器单列配置、卡片样式；同时还有插入位置、阅读时长、起止/过期时间、请求数、推荐策略等参数。
- `reader1colDataV2.enable`、`autoPlayAudio`、`hasMoreRelatedVideoPanel`、`watchProgressAlignmentOutflow`：新版阅读器单列内容、音频自动播放、相关视频面板、播放进度对齐；同时有章节间隔、静态图片请求数量与策略优先级。
- `videoSeriesTabPreferenceInfo.enable`：短剧 Tab 偏好引导；另有引导次数、间隔、快速离开间隔及版本。
- `searchSideGoldedLineItem.isDisplayUserGuidance`：搜索侧边入口用户引导；另有图标、深色图标、样式、文案、跳转 schema、展示起止时间等。
- `storeUiConfigs.showAiSearchEntranceTabs`：哪些书城 Tab 展示 AI 搜索入口，是列表配置，不是单个布尔总开关。
- `readerIpBanner`、`lastViewAbData`、`freeGuideAbData`、`skipFeedbackConfig`、`creatorBindInfo` 等也被保存；本表不将所有对象展开为未经核对的开关。

响应另包含 `bookstoreAlignmentData`（如 `landingToLastTab`、`searchGeneralEnable`、底栏列表及落地 schema），但没有在此更新函数中逐项消费，不应混称为其直接更新的字段。

## 与近期漫剧/搜索问题的关系

- `dynamicComicTip` 是 `DynamicComicTip` 对象，已确认仅有 `text`、`version`，更新到 `dynamicTabGuide`：是引导文案/版本，**不是漫剧推荐总开关**。
- `userActiveData.motionComicActiveLevel30d`、`playletActiveLevel30d` 是字符串型用户活跃度，更新到相应状态：属于客群/推荐参数，不能当成关闭漫剧/短剧的布尔项。
- `videoSeriesTabPreferenceInfo` 是短剧 Tab 偏好引导配置，不等于把所有短剧/漫剧卡片从推荐列表移除。
- 此响应还有 AI 搜索入口、落地页标题/反馈等配置；顶部搜索热词的数据过滤仍需要独立 Hook，不能仅靠禁用 CommonAbResult 更新替代。

## 缓存与 Hook 的真实边界

1. `CommonAbResult` 静态初始化取得 SharedPreferences/MMKV，从 `key_common_ab_result_json` 及其他 key 读回旧配置。
2. `updateCommonAbResult` 更新内存字段，再用 `JSONUtils.safeJsonString(commonAbResultData)` 保存整个响应及一批独立 key。
3. 方法末尾发送 `BusProvider.post(new g())`、`dataUpdatedSubject.onNext(Boolean.TRUE)`；当前 FQ 提前返回会跳过这些通知。
4. 旧缓存仍能在冷启动时恢复；禁用“更新”不等于将所有字段恢复默认或设 false。
5. 明确存在独立配置通道，例如：`mine_tab_staggered_feed_v649`、`staggered_refresh_in_mine_v685`、`staggered_scroll_snap_top_v683`、`book_cover_hot_comments_v617`、`search_cue_word_config_v545`。它们由 SsConfigMgr/配置模板读取，不被本 Hook 统一接管。

## 证据与验证边界

- `.work/cloud-controls-7.3.9.32/CommonAbResult.java`：类初始化、getter、setter、更新和缓存保存；更新入口从第 542 行开始。
- `.work/cloud-controls-7.3.9.32/CommonAbResultData.java`：新版响应结构。
- `.work/hook-review-7.3.9.32/dex-inventory.json`：原始 DEX 类型、字段及方法引用；据此统计 74 个业务字段、28 个顶层 boolean、更新直接读取 45 个字段。
- `app/src/main/java/com/fuck/fanqie/hooks/FeatureHooks.java`：当前 applyAbtestHooks 的提前返回逻辑。
- 只做静态核对和文档整理，未修改源码、构建、安装、调用生产 API 或读取真实用户云控值。
