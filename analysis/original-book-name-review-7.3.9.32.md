# 官网 7.3.9.32 原书名统一展示可行性

## 结论

- **技术上可实现：按书籍 ID 和真实原名字段，在书籍展示模型入口统一选择名称，而不是全局替换 TextView。**
- 已确认字段存在、书城公共转换入口和模型边界；但尚未抓到一部多书名实验书的实际字段对照，不能把 rawBookName 的业务语义或“全部 UI 已覆盖”当作已验证结果。
- 此轮仅做静态调查和文档整理，未修改源码、构建、安装或读取书籍正文。

## 已确认的名称及实验字段

主 RPC `com.dragon.read.rpc.model.ApiBookInfo` 声明：

| 字段 | 现有证据与处理边界 |
| --- | --- |
| `bookId` | 同一本书的身份依据，不能用展示名称做匹配 |
| `bookName` | 当前展示名；书城共享转换器直接复制到 ItemDataModel |
| `bookShortName` | 短书名；也被复制到展示模型，可影响最终书名文本 |
| `rawBookName` | 原始名称候选；已确认 RPC/PB/KMP 模型都有，需与一部 AB 测试书的真实返回对照，不能仅凭 raw 命名断言服务端一定返回实验前基线 |
| `originalBookName` | 原著/关联作品名称候选；消费者集中于 AdaptationPanelWorkSectionsKt、RelativeListFragment、关联卡片等，不能无条件当作当前这本书的 AB 基线名 |
| `bookNameIndex` / `bookNameGid` | 多书名实验元数据；BookUtils.getArgsForMultipleBookName 会读取并用于相关参数，不是原名字符串 |
| `aliasName` / `rawAlias` / `title` / `subTitle` | 另有别名、标题等字段，不能自动等同于书名或做全局覆盖 |

## PB、主 RPC、KMP、SaaS 模型不相同

全 DEX 字段扫描发现：

- 主 RPC `com.dragon.read.rpc.model.ApiBookInfo`：rawBookName、originalBookName。
- PB `com.dragon.read.pbrpc.ApiBookInfo`：raw_book_name、original_book_name；beancopy.ConvertHelp 负责复制到主 RPC 模型。
- KMP 三套 ApiBookInfo（community/model、model/api_virtual_ssrpcapi、model/toutiao_muye_app_node）：rawBookName、originalBookName。
- 对应 VideoData 也含 rawBookName，但不能因此直接把所有视频卡片标题改成书名。
- 独立 `readersaas.com.dragon.read.saas.rpc.model.ApiBookInfo` 只发现 originalBookName，没有 rawBookName。
- 扫描结果来自 field_ids 中的类型/字段引用；用于确认字段存在及模型差异，不代替每个业务接口都返回非空原名的证明。

## 已核对的展示转换链路

### 书城及部分榜单/搜索相关模型

`com.dragon.read.util.j4.f(RankBookModel.RankItemBook, ApiBookInfo): ItemDataModel`：

- 复制 bookId、bookName、bookShortName、bookNameGid、bookNameIndex。
- 没有把 rawBookName 复制进该展示模型。
- 多个 `c1` 卡片工厂（包括 M/X/Y/F/l0 等）调用该转换器，因此是高收益的统一名称切入点，不需要逐个 TextView 改文字。
- 这说明可在转换时选用已验证的原名，或把同 bookId 的原名供后续展示使用；不代表全部搜索、KMP、书架分支都经过 j4.f。

### 阅读器、阅读封面及持久化书籍模型

- `SaaSBookInfo.m0(readersaas...ApiBookInfo): SaaSBookInfo` 读取的是独立 SaaS 模型，而非上述主 RPC ApiBookInfo，复制 bookId/bookName/bookShortName。
- BookCoverInfo.Companion 从 SaaSBook/SaaSBookInfo 复制书名；BookCoverInfo 自身未发现 rawBookName/originalBookName 字段。
- 现有 FQ 封面 Hook 已关闭短书名/图片书名渲染，回退到文本，但**回退文本不等于回退 AB 原名**：它仍使用模型里的 bookName。
- 阅读器/已有书架记录缺少候选原名时，需要额外展示入口与同 bookId 名称映射；单改书城转换器不能让旧记录自动还原。

## 推荐实现策略

1. 先以一部确定存在多书名实验的书，对照 bookId、bookName、rawBookName、originalBookName。只需书籍元数据，不需要正文。
2. 对已确认携带当前书原名的数据，在转换到展示模型时选择该名称；原名缺失、空白或身份不能确认时，保留当前名称，不猜测。
3. 优先覆盖主 RPC 的公共转换入口，再补 PB→主 RPC、KMP 及 SaaS/书架展示入口。用 DexKit 产出目标，经 TargetRepository/CachedTargets 给 Hook 消费；不硬编码 j4.f 等混淆名，不复用既有 key 含义。
4. 对不携带原名的下游模型，可维护有容量上限的进程内 bookId→已验证原名映射；未见过原名的书不强行替换。本轮不自动调用额外生产 API，也不清空书架或宿主缓存。
5. 原名有效时，不再让 bookShortName 或单独的图片书名覆盖它。保持书籍 ID、章节标题、普通标签、用户输入不变；不要把 alias/originalBookName/视频标题当作无条件后备值。
6. 尽量在展示模型层处理，避免直接改整个网络 DTO 导致分享、导出文件名或持久化数据发生非 UI 副作用。若必须更上游处理，应单独说明并扩大验证范围。
7. 文字书名可统一；封面位图里已经印上的文字不能通过字段替换重绘，不能宣称连图片内标题都恢复。

## 最小闭环与验证边界

- 首轮可以先实现“有可靠原名的主 RPC 书籍，统一书城/相关展示模型名称”，保留无原名数据。
- 随后按书城、搜索结果、书架、详情页、阅读封面/标题分别验证相同 bookId 的名称及点击跳转；KMP 与已有缓存记录必须独立验收。
- Java/Finder/Hook 改动后按项目规则 assembleDebug，再由用户操作页面验收。此轮仅分析，没有进行以上构建或运行时验证。

## 静态证据

- `.work/hook-review-7.3.9.32/dex-inventory.json`：ApiBookInfo、BookUtils、SaaSBookInfo、BookCoverInfo 原始字段与方法引用。
- `.work/original-book-name-review/BookItemConverter.java`：共享 j4.f，书名及实验元数据复制。
- `.work/original-book-name-review/AdaptationWorkTitles.java`：originalBookName 用于改编作品区域的消费者。
- `.work/hook-repair-7.3.9.32/BookCoverInfoFactory.java`、`BookCoverView.java`：SaaS→封面与文本/图片书名行为。
