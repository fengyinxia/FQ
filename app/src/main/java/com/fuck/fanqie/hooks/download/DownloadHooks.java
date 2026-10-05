package com.fuck.fanqie.hooks.download;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;
import com.fuck.fanqie.hooks.BaseHook;
import com.fuck.fanqie.hooks.HookUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DownloadHooks extends BaseHook {
    private static final String TAG = "FQHook";

    private final CachedTargets cachedTargets;
    private final DownloadCaptureState captureState = new DownloadCaptureState();
    private final DownloadExportModes exportModes = new DownloadExportModes();
    private volatile boolean modeHookInstalled;
    private final DownloadContentProcessor contentProcessor;
    private final DownloadExporter exporter;
    private final DownloadExporter.Logger exportLogger = new DownloadExporter.Logger() {
        @Override
        public void log(String message) {
            logInfo(message);
        }
    };

    public DownloadHooks(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        super(hostClassLoader);
        this.cachedTargets = cachedTargets;
        this.contentProcessor = new DownloadContentProcessor(cachedTargets, hostClassLoader);
        this.exporter = new DownloadExporter();
    }

    @Override
    public void apply() {
        applyDownloadModeHook();
        applyChapterInfoHook();
        applyDecryptedContentHook();
        applyStatusDispatcherHook();
        logInfo("DownloadHooks: 初始化完成");
    }

    private void applyDownloadModeHook() {
        try {
            Method entry = cachedTargets.method(HookTargets.KEY_DOWNLOAD_CLICK_METHOD);
            if (entry == null || entry.getReturnType() != Void.TYPE
                    || Modifier.isStatic(entry.getModifiers()) || entry.getParameterTypes().length != 9) {
                logInfo("DownloadMode: 未找到兼容的下载点击入口；默认仅缓存，不自动导出");
                return;
            }
            Class<?>[] types = entry.getParameterTypes();
            if (types[0] != String.class || types[1] != Context.class
                    || !"com.dragon.read.report.PageRecorder".equals(types[2].getName())
                    || types[3] != String.class
                    || !"kotlin.jvm.functions.Function1".equals(types[4].getName())
                    || !"kotlin.jvm.functions.Function1".equals(types[5].getName())
                    || types[7] != Boolean.TYPE) {
                logInfo("DownloadMode: 下载点击参数契约不匹配；默认仅缓存，不自动导出");
                return;
            }
            new DownloadModeChooser(exportModes, (bookId, export) -> {
                clearBookState(bookId);
                if (export) {
                    captureState.markActiveBook(bookId);
                }
            }).install(entry);
            modeHookInstalled = true;
            logInfo("DownloadMode: 已安装下载方式选择: " + entry);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadMode: 下载方式 Hook 失败: ", throwable);
        }
    }

    private boolean isExportRequested(String bookId) {
        if (!modeHookInstalled) {
            return false;
        }
        try {
            return exportModes.bind(getCurrentApplication()) && exportModes.requestId(bookId) != 0;
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadMode: 读取导出选择失败，不执行导出: ", throwable);
            return false;
        }
    }

    private boolean isExportCaptureBook(String bookId) {
        return captureState.isActiveBook(bookId) && isExportRequested(bookId);
    }

    private void applyChapterInfoHook() {
        try {
            Class<?> chapterInfoClass = XposedHelpers.findClass(
                    "com.dragon.read.reader.download.ChapterInfo",
                    hostClassLoader
            );
            Class<?> itemContentClass = XposedHelpers.findClass(
                    "readersaas.com.dragon.read.saas.rpc.model.ItemContent",
                    hostClassLoader
            );
            XposedHelpers.findAndHookMethod(chapterInfoClass, "a", itemContentClass, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object chapterInfo = param.getResult();
                    if (chapterInfo == null) {
                        return;
                    }

                    String bookId = getFieldSafely(chapterInfo, "bookId", null);
                    String chapterId = getFieldSafely(chapterInfo, "chapterId", null);
                    String content = getFieldSafely(chapterInfo, "content", null);
                    if (bookId == null || chapterId == null || content == null || content.length() == 0) {
                        return;
                    }
                    if (!isExportCaptureBook(bookId)) {
                        return;
                    }
                    // 此处是服务端原始内容；有密钥的章节须等解密成功，不能导出密文。
                    if (XposedHelpers.getIntField(chapterInfo, "keyVersion") != Integer.MIN_VALUE) {
                        return;
                    }
                    if (!captureState.markChapterSeen(bookId, chapterId)) {
                        return;
                    }

                    String title = getFieldSafely(chapterInfo, "name", null);
                    String bookName = getFieldSafely(chapterInfo, "bookName", null);
                    String order = null;

                    Object itemContent = param.args[0];
                    if (itemContent != null) {
                        try {
                            Object novelData = XposedHelpers.getObjectField(itemContent, "novelData");
                            if (novelData != null) {
                                order = getFieldSafely(novelData, "realChapterOrder", null);
                                if (title == null || title.length() == 0) {
                                    title = getFieldSafely(novelData, "originChapterTitle", null);
                                }
                                if (bookName == null || bookName.length() == 0) {
                                    bookName = getFieldSafely(novelData, "bookName", null);
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    saveChapter(bookId, bookName, chapterId, order, title,
                            contentProcessor.extractPlainTextContent(chapterInfo, content));
                }
            });
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: Hook 章节明文失败: ", throwable);
        }
    }

    private void applyDecryptedContentHook() {
        try {
            Method decodeMethod = cachedTargets.method(HookTargets.KEY_CHAPTER_DECRYPT_METHOD);
            if (decodeMethod == null || decodeMethod.getReturnType() != String.class
                    || !Modifier.isStatic(decodeMethod.getModifiers())) {
                XposedBridge.log("FQHook+DownloadHooks: 未找到兼容的章节解码核心，跳过");
                return;
            }
            Class<?>[] types = decodeMethod.getParameterTypes();
            if (types.length != 5 || types[0] != String.class
                    || !"com.dragon.read.reader.DecryptKey".equals(types[1].getName())
                    || types[2] != Boolean.TYPE || types[3] != String.class || types[4] != String.class) {
                XposedBridge.log("FQHook+DownloadHooks: 章节解码参数契约不匹配，跳过: " + decodeMethod);
                return;
            }
            XposedBridge.hookMethod(decodeMethod, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String content = param.getResult() instanceof String ? (String) param.getResult() : null;
                            String bookId = param.args[3] instanceof String ? (String) param.args[3] : null;
                            String chapterId = param.args[4] instanceof String ? (String) param.args[4] : null;
                            if (content == null || content.length() == 0 || chapterId == null
                                    || !isExportCaptureBook(bookId)) {
                                return;
                            }
                            saveChapter(bookId, null, chapterId, null, null,
                                    contentProcessor.extractPlainTextContent(null, content));
                        }
                    });
            XposedBridge.log("FQHook+DownloadHooks: 已接入章节解码核心: " + decodeMethod);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: Hook 解密结果失败: ", throwable);
        }
    }

    private void applyStatusDispatcherHook() {
        try {
            Method dispatcherMethod = cachedTargets.method(HookTargets.KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD);
            if (dispatcherMethod == null) {
                logInfo("DownloadHooks: 未找到下载状态分发方法，跳过 Hook");
                return;
            }
            XposedBridge.hookMethod(dispatcherMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String taskKey = param.args[0] instanceof String ? (String) param.args[0] : null;
                    Object status = param.args[1];
                    if (status == null || taskKey == null || taskKey.length() == 0) {
                        return;
                    }
                    if (!isExportRequested(taskKey)) {
                        clearBookState(taskKey);
                        return;
                    }

                    String statusName = String.valueOf(status);
                    if ("PENDING".equals(statusName) && taskKey != null) {
                        clearBookState(taskKey);
                        captureState.markActiveBook(taskKey);
                    } else if ("RUNNING".equals(statusName) && taskKey != null) {
                        captureState.markActiveBook(taskKey);
                    } else if ("ERROR".equals(statusName) || "PAUSE".equals(statusName)) {
                        clearBookState(taskKey);
                    } else if ("CANCEL".equals(statusName)) {
                        clearBookState(taskKey);
                        try {
                            exportModes.complete(taskKey, exportModes.requestId(taskKey));
                            logInfo("DownloadMode: 下载任务已取消，清除导出选择");
                        } catch (Throwable throwable) {
                            HookUtils.logError("FQHook+DownloadMode: 取消任务后清理选择失败: ", throwable);
                        }
                        return;
                    }

                    if (!"FINISH".equals(statusName)) {
                        clearFinishFlag(taskKey);
                        return;
                    }

                    if (notifyFinish(taskKey)) {
                        exportBookAsync(taskKey, exportModes.requestId(taskKey));
                    }
                }
            });
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: Hook 状态分发失败: ", throwable);
        }
    }

    private void saveChapter(String bookId, String bookName, String chapterId, String order, String title, String content) {
        captureState.saveChapter(bookId, bookName, chapterId, order, title, content);
    }

    private void exportBookAsync(final String bookId, final long request) {
        if (!exportModes.claimExport(bookId, request)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800L);
                    if (exportModes.isCurrent(bookId, request)) {
                        exportBook(bookId, request);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    logInfo("DownloadHooks: 导出等待被中断，保留导出选择");
                } finally {
                    exportModes.releaseExport(bookId, request);
                }
            }
        }, "FQHook-Export-" + bookId);
        thread.start();
    }

    private void exportBook(String bookId, long request) {
        boolean exported = false;
        try {
            if (!exportModes.isCurrent(bookId, request)) {
                return;
            }
            DownloadCaptureState.BookSnapshot snapshot = detachBookSnapshot(bookId);
            if (snapshot == null) {
                snapshot = new DownloadCaptureState.BookSnapshot(bookId);
            }
            DownloadContentProcessor.DirectorySnapshot directorySnapshot = contentProcessor.loadDirectorySnapshot(bookId);
            if (directorySnapshot != null && (snapshot.bookName == null || snapshot.bookName.length() == 0)) {
                snapshot.bookName = directorySnapshot.bookName;
            }
            int restoredCount = contentProcessor.backfillCachedChapters(bookId, snapshot, directorySnapshot, captureState);
            if (restoredCount > 0) {
                logInfo("DownloadHooks: 已从本地缓存补回章节, bookId=" + bookId + ", chapterCount=" + restoredCount);
            }
            if (snapshot.chapters.isEmpty()) {
                logInfo("DownloadHooks: 下载完成但未捕获到章节正文, bookId=" + bookId);
                return;
            }
            Application application = getCurrentApplication();
            if (application == null) {
                logInfo("DownloadHooks: currentApplication 为空，无法导出 TXT, bookId=" + bookId);
                return;
            }
            DownloadExporter.ExportPayload payload = exporter.buildPayload(bookId, snapshot, directorySnapshot);
            if (!exportModes.isCurrent(bookId, request)) {
                logInfo("DownloadHooks: 下载模式已改变，取消本次导出");
                return;
            }
            String outputPath = exportModes.writeIfCurrent(bookId, request,
                    () -> exporter.writeExportText(application, payload, exportLogger));
            if (outputPath == null) {
                logInfo("DownloadHooks: 下载模式已改变，未写入导出文件");
                return;
            }
            exported = true;
            logInfo("DownloadHooks: 已导出TXT, bookId=" + bookId
                    + ", chapterCount=" + payload.chapterCount + ", path=" + outputPath);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: 导出 TXT 失败: ", throwable);
        } finally {
            try {
                exportModes.finishAttempt(bookId, request, exported, () -> clearBookState(bookId));
            } catch (Throwable throwable) {
                HookUtils.logError("FQHook+DownloadMode: 清理导出选择失败: ", throwable);
            }
        }
    }

    private DownloadCaptureState.BookSnapshot detachBookSnapshot(String bookId) {
        return captureState.detachBookSnapshot(bookId);
    }

    private boolean notifyFinish(String bookId) {
        if (!captureState.markFinishNotified(bookId)) {
            return false;
        }
        logInfo("DownloadHooks: 检测到下载完成（缓存并导出）, bookId=" + bookId);
        return true;
    }

    private void clearFinishFlag(String taskKey) {
        captureState.clearFinishFlag(taskKey);
    }

    private void clearBookState(String bookId) {
        captureState.clearBook(bookId);
    }

    private Application getCurrentApplication() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication");
            Object value = currentApplicationMethod.invoke(null);
            return value instanceof Application ? (Application) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void logInfo(String message) {
        XposedBridge.log(TAG + ": " + message);
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
