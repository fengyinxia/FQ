# 下载方式选择：缓存 / 缓存并导出（7.3.9.32）

## 需求与范围

用户要求点击下载时弹出两个选项：缓存只走宿主原下载；缓存并导出走原下载加现有 TXT 导出。普通下载默认不自动导出，保留宿主权限判断、原参数、回调与原章节选择/缓存行为，不替换权益结果。

## 入口证据

- 新 key `KEY_DOWNLOAD_CLICK_METHOD` / `method_download_click`；由 DownloadFinder 扫描，只写 TargetScanResult，Hook 经 CachedTargets 解析。
- 特征：com.dragon.read.user 包下、实例方法、9 参数、void，包含“点击下载权限判断: decision=”日志；核对参数 0=String bookId、1=Context、2=PageRecorder、3=String、4/5=Function1、7=boolean。剩余类型不写死混淆名。
- 官网全 DEX 唯一候选：`com.dragon.read.user.n1.a(String,Context,PageRecorder,String,Function1,Function1,bn6.m0,boolean,detail.platform.j):void`。
- 方法内原调用 `NsVipApi.evaluateBookDownloadPrivilege`，再调用原用户操作/授权回调；本实现只延后执行它，不返回伪造授权结果。
- 全 DEX 直接调用点 3 个，来自 KMP 详情 `detail.platform.n`、原生详情 `pages.detail.fragment.d1`、阅读器依赖 `bn6.r0`。证据 `.work/download-mode/entry-audit.txt`；分析器 `DownloadModeAudit.java`。

## UI 和原调用恢复

- DownloadModeChooser 用有效 Activity 在主线程显示“下载方式”，选项“缓存 / 缓存并导出”，另有取消。
- 选择后用 `XposedBridge.invokeOriginalMethod` 恢复同一个接收者、原始参数副本；不重入选择 Hook，不自行启动替代下载器。
- 取消/返回/外部关闭不执行原下载；使用宿主下载弹窗同一取消回调传 false。无有效 Activity 时明确日志并保留原行为；弹窗显示失败记录错误并取消，不假装用户已选择。
- 同一书的重复点击不创建多重选择弹窗。候选缺失/签名不兼容时不装选择 Hook，导出入口默认关闭。

## 导出状态与隔离

- 新 DownloadExportModes 将用户意图按 bookId 存在宿主独立 SharedPreferences `fq_download_export_requests`，只存请求 ID，不存正文，不混入目标缓存或宿主下载设置。
- 每次选“缓存并导出”产生新请求 ID；“缓存”删除该书请求并清理本模块捕获状态。其他书的选择不变，也不删除已经存在的 TXT。
- ChapterInfo/解码捕获必须同时满足活跃书和有效导出选择；状态监听仅为已选择导出的书激活捕获、补回及启动导出。普通缓存的 FINISH 不进入本模块导出流程。
- PENDING 重置本次捕获、RUNNING 激活捕获；PAUSE/ERROR 清理临时捕获但保留意图用于恢复；CANCEL 清除意图。请求持久化支持宿主重启后继续完成。
- FINISH 通知去重与请求级导出线程 claim 防止重复启动；旧线程遇到重新选择的请求 ID 会停止，最终写入与模式切换串行，避免旧导出写入覆盖新的“仅缓存”选择。清理也核对请求 ID，不清掉新任务。
- 成功写入后清除导出请求；失败保留请求便于重试。沿用本地缓存补回/XHTML→纯文本转换/Download/FQ 路径，不读取正文做验收。

## 验证边界

- 新入口的等价静态定位唯一，三个直接调用点已核对。实际 Android DexKit 命中、选项 UI、取消回调、宿主权限分支、缓存-only 不写 TXT、缓存并导出写 TXT 仍需用户重启后验证。
- 新 key 使协议 40→41，原 key 含义不变；安装指纹改变后正常重新扫描。
- 此入口不替换下载管理中已存在任务的后台自动恢复，不承诺全部旁路入口都弹窗。暂停恢复保留原导出意图，不主动自动弹窗。
- 已缓存书再次点“缓存并导出”是否会重新产生 FINISH 由宿主原行为决定，目前不额外绕过原流程直接导出；需用户实际验证。
- 本轮不增加支付/账号/权限绕过，不改宿主原权限调用，不操作页面。
- `assembleDebug --offline` 构建成功（BUILD SUCCESSFUL，2s，SDK E:/sdk）。无线 192.168.2.91:40661 覆盖安装成功（Success）；未强停/启动宿主、未操作页面、未读正文。
- 待用户重启验收：点击下载应显示两选项；缓存不新建/覆盖 TXT，缓存并导出在 FINISH 后写 TXT；取消后原界面可再次下载；暂停恢复/重启、多书并行不会串模式。日志查看 method_download_click 命中、DownloadMode 弹窗/选择、导出链路，不把构建安装当成功能通过。
