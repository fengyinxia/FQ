package com.fuck.fanqie.hooks.download;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** User export intent only; independent of target cache and host download settings. */
public final class DownloadExportModes {
    public interface ExportWriter {
        String write() throws Exception;
    }

    private static final String PREFERENCES = "fq_download_export_requests";
    private final Map<String, Long> requests = new HashMap<>();
    private final Set<String> exporting = new HashSet<>();
    private SharedPreferences preferences;
    private long sequence = System.currentTimeMillis();

    public synchronized boolean bind(Context context) {
        if (preferences != null) {
            return true;
        }
        if (context == null) {
            return false;
        }
        Context application = context.getApplicationContext();
        SharedPreferences loaded = (application == null ? context : application)
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : loaded.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Long && (Long) value > 0) {
                requests.put(entry.getKey(), (Long) value);
                sequence = Math.max(sequence, (Long) value);
            }
        }
        preferences = loaded;
        return true;
    }

    public synchronized long select(String bookId, boolean export) {
        if (preferences == null || bookId == null || bookId.length() == 0) {
            throw new IllegalStateException("下载模式存储未就绪或书籍 ID 为空");
        }
        long request = export ? ++sequence : 0;
        SharedPreferences.Editor editor = preferences.edit();
        if (export) {
            editor.putLong(bookId, request);
        } else {
            editor.remove(bookId);
        }
        if (!editor.commit()) {
            throw new IllegalStateException("保存下载模式失败");
        }
        if (export) {
            requests.put(bookId, request);
        } else {
            requests.remove(bookId);
        }
        return request;
    }

    public synchronized long requestId(String bookId) {
        Long request = bookId == null ? null : requests.get(bookId);
        return request == null ? 0 : request;
    }

    public synchronized boolean isCurrent(String bookId, long request) {
        return request > 0 && requestId(bookId) == request;
    }

    public synchronized boolean claimExport(String bookId, long request) {
        return isCurrent(bookId, request) && exporting.add(bookId + ":" + request);
    }

    public synchronized void releaseExport(String bookId, long request) {
        exporting.remove(bookId + ":" + request);
    }

    /** Serializes the final write against a user switching this book to cache-only. */
    public synchronized String writeIfCurrent(String bookId, long request, ExportWriter writer) throws Exception {
        return isCurrent(bookId, request) ? writer.write() : null;
    }

    public synchronized void finishAttempt(String bookId, long request, boolean exported, Runnable clearCapture) {
        if (!isCurrent(bookId, request)) {
            return;
        }
        clearCapture.run();
        if (exported) {
            complete(bookId, request);
        }
    }

    public synchronized void complete(String bookId, long request) {
        if (!isCurrent(bookId, request)) {
            return;
        }
        if (!preferences.edit().remove(bookId).commit()) {
            throw new IllegalStateException("清理下载导出标记失败");
        }
        requests.remove(bookId);
    }
}
