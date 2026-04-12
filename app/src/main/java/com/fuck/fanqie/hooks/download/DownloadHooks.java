package com.fuck.fanqie.hooks.download;

import android.app.Application;
import android.util.Log;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;
import com.fuck.fanqie.hooks.BaseHook;
import com.fuck.fanqie.hooks.HookUtils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DownloadHooks extends BaseHook {
    private static final String TAG = "FQHook";

    private final CachedTargets cachedTargets;
    private final DownloadCaptureState captureState = new DownloadCaptureState();
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
        applyChapterInfoHook();
        applyDecryptedContentHook();
        applyStatusDispatcherHook();
        logInfo("DownloadHooks: 初始化完成");
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
                    if (!captureState.isActiveBook(bookId)) {
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
                    saveChapter(bookId, bookName, chapterId, order, title, content);
                }
            });
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: Hook 章节明文失败: ", throwable);
        }
    }

    private void applyDecryptedContentHook() {
        try {
            Class<?> decodeUtilsClass = XposedHelpers.findClass(
                    "com.dragon.read.reader.utils.o",
                    hostClassLoader
            );
            Class<?> chapterInfoClass = XposedHelpers.findClass(
                    "com.dragon.read.reader.download.ChapterInfo",
                    hostClassLoader
            );
            Class<?> decryptKeyClass = XposedHelpers.findClass(
                    "com.dragon.read.reader.DecryptKey",
                    hostClassLoader
            );
            XposedHelpers.findAndHookMethod(decodeUtilsClass, "a", chapterInfoClass, decryptKeyClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object chapterInfo = param.args[0];
                    Object result = param.getResult();
                    String content = result instanceof String ? (String) result : null;
                    if (chapterInfo == null || content == null || content.length() == 0) {
                        return;
                    }

                    String bookId = getFieldSafely(chapterInfo, "bookId", null);
                    String chapterId = getFieldSafely(chapterInfo, "chapterId", null);
                    if (bookId == null || chapterId == null || !captureState.isActiveBook(bookId)) {
                        return;
                    }

                    String title = getFieldSafely(chapterInfo, "name", null);
                    String bookName = getFieldSafely(chapterInfo, "bookName", null);
                    String plainText = contentProcessor.extractPlainTextContent(chapterInfo, content);
                    saveChapter(bookId, bookName, chapterId, null, title, plainText);
                }
            });
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
                    if (status == null) {
                        return;
                    }

                    String statusName = String.valueOf(status);
                    if ("PENDING".equals(statusName) && taskKey != null) {
                        clearBookState(taskKey);
                        captureState.markActiveBook(taskKey);
                    } else if ("RUNNING".equals(statusName) && taskKey != null) {
                        captureState.markActiveBook(taskKey);
                    } else if ("ERROR".equals(statusName)
                            || "CANCEL".equals(statusName)
                            || "PAUSE".equals(statusName)) {
                        clearBookState(taskKey);
                    }

                    if (!"FINISH".equals(statusName)) {
                        clearFinishFlag(taskKey);
                        return;
                    }

                    String bookId = taskKey;
                    String message = bookId == null
                            ? "番茄小说下载完成"
                            : "番茄小说下载完成: " + bookId;
                    notifyFinish(taskKey, bookId, message);
                    exportBookAsync(bookId);
                }
            });
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: Hook 状态分发失败: ", throwable);
        }
    }

    private void saveChapter(String bookId, String bookName, String chapterId, String order, String title, String content) {
        captureState.saveChapter(bookId, bookName, chapterId, order, title, content);
    }

    private void exportBookAsync(final String bookId) {
        if (bookId == null || bookId.length() == 0) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800L);
                } catch (InterruptedException ignored) {
                }
                exportBook(bookId);
            }
        }, "FQHook-Export-" + bookId);
        thread.start();
    }

    private void exportBook(String bookId) {
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

        try {
            Application application = getCurrentApplication();
            if (application == null) {
                logInfo("DownloadHooks: currentApplication 为空，无法导出 TXT, bookId=" + bookId);
                return;
            }
            DownloadExporter.ExportPayload payload = exporter.buildPayload(bookId, snapshot, directorySnapshot);
            String outputPath = exporter.writeExportText(application, payload, exportLogger);

            logInfo("DownloadHooks: 已导出TXT, bookId=" + bookId
                    + ", chapterCount=" + payload.chapterCount
                    + ", path=" + outputPath);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+DownloadHooks: 导出 TXT 失败: ", throwable);
        } finally {
            clearBookState(bookId);
        }
    }

    private DownloadCaptureState.BookSnapshot detachBookSnapshot(String bookId) {
        return captureState.detachBookSnapshot(bookId);
    }

    private void notifyFinish(String taskKey, String bookId, String message) {
        if (!captureState.markFinishNotified(taskKey)) {
            return;
        }
        logInfo("DownloadHooks: 检测到下载完成, taskKey=" + taskKey + ", bookId=" + bookId);
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
