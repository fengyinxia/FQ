package com.fuck.fanqie.hooks.download;

import android.app.Application;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DownloadExporter {
    public interface Logger {
        void log(String message);
    }

    public ExportPayload buildPayload(
            String bookId,
            DownloadCaptureState.BookSnapshot snapshot,
            DownloadContentProcessor.DirectorySnapshot directorySnapshot
    ) {
        String displayName = snapshot.bookName == null || snapshot.bookName.length() == 0 ? bookId : snapshot.bookName;
        String fileName = sanitizeFileName(displayName) + ".txt";
        List<DownloadCaptureState.ChapterSnapshot> chapters = new ArrayList<DownloadCaptureState.ChapterSnapshot>(snapshot.chapters.values());
        final DownloadContentProcessor.DirectorySnapshot finalDirectorySnapshot = directorySnapshot;
        Collections.sort(chapters, new Comparator<DownloadCaptureState.ChapterSnapshot>() {
            @Override
            public int compare(DownloadCaptureState.ChapterSnapshot left, DownloadCaptureState.ChapterSnapshot right) {
                int leftOrder = resolveChapterOrder(finalDirectorySnapshot, left);
                int rightOrder = resolveChapterOrder(finalDirectorySnapshot, right);
                return leftOrder != rightOrder ? leftOrder - rightOrder : left.chapterId.compareTo(right.chapterId);
            }
        });

        StringBuilder builder = new StringBuilder();
        for (DownloadCaptureState.ChapterSnapshot chapter : chapters) {
            String chapterTitle = resolveChapterTitle(finalDirectorySnapshot, chapter);
            builder.append(chapterTitle == null || chapterTitle.length() == 0 ? "章节 " + chapter.chapterId : chapterTitle)
                    .append('\n');
            builder.append(chapter.content).append("\n\n");
        }
        return new ExportPayload(fileName, builder.toString(), chapters.size());
    }

    public String writeExportText(Application application, ExportPayload payload, Logger logger) throws Exception {
        File directFile = writeExportTextToDownloadDirectory(payload.fileName, payload.content, logger);
        if (directFile != null) {
            return directFile.getAbsolutePath();
        }
        String mediaStorePath = writeExportTextToMediaStore(application, payload.fileName, payload.content, logger);
        if (mediaStorePath != null) {
            return mediaStorePath;
        }
        throw new IllegalStateException("无法写入 Download/FQ");
    }

    private File writeExportTextToDownloadDirectory(String fileName, String content, Logger logger) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir == null) {
                return null;
            }
            File exportDir = new File(downloadDir, "FQ");
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                logger.log("DownloadHooks: 创建 Download/FQ 目录失败, path=" + exportDir.getAbsolutePath());
                return null;
            }
            File outputFile = new File(exportDir, fileName);
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(new FileOutputStream(outputFile, false), StandardCharsets.UTF_8);
                writer.write(content);
                writer.flush();
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return outputFile;
        } catch (Throwable throwable) {
            logger.log("DownloadHooks: 直写 Download/FQ 失败, error=" + throwable.getClass().getSimpleName());
            return null;
        }
    }

    private String writeExportTextToMediaStore(Application application, String fileName, String content, Logger logger) {
        if (application == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        OutputStream outputStream = null;
        OutputStreamWriter writer = null;
        Uri uri = null;
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/FQ/";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = application.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return null;
            }
            outputStream = application.getContentResolver().openOutputStream(uri, "w");
            if (outputStream == null) {
                return null;
            }
            writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            writer.write(content);
            writer.flush();

            ContentValues finishValues = new ContentValues();
            finishValues.put(MediaStore.Downloads.IS_PENDING, 0);
            application.getContentResolver().update(uri, finishValues, null, null);
            return "/storage/emulated/0/" + relativePath + fileName;
        } catch (Throwable throwable) {
            logger.log("DownloadHooks: MediaStore 写入 Download/FQ 失败, error=" + throwable.getClass().getSimpleName());
            if (uri != null) {
                try {
                    application.getContentResolver().delete(uri, null, null);
                } catch (Throwable ignored) {
                }
            }
            return null;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            } else if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private int parseOrder(String order) {
        if (order == null || order.length() == 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(order);
        } catch (Throwable ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private int resolveChapterOrder(DownloadContentProcessor.DirectorySnapshot directorySnapshot, DownloadCaptureState.ChapterSnapshot chapter) {
        if (directorySnapshot != null) {
            Integer order = directorySnapshot.chapterOrder.get(chapter.chapterId);
            if (order != null) {
                return order.intValue();
            }
        }
        return parseOrder(chapter.order);
    }

    private String resolveChapterTitle(DownloadContentProcessor.DirectorySnapshot directorySnapshot, DownloadCaptureState.ChapterSnapshot chapter) {
        String title = chapter.title;
        if ((title == null || title.length() == 0) && directorySnapshot != null) {
            title = directorySnapshot.chapterTitles.get(chapter.chapterId);
        }
        return title;
    }

    private String sanitizeFileName(String text) {
        if (text == null || text.length() == 0) {
            return "book";
        }
        return text.replaceAll("[\\/:*?\"<>|\n\r\t]", "_");
    }

    public static final class ExportPayload {
        public final String fileName;
        public final String content;
        public final int chapterCount;

        ExportPayload(String fileName, String content, int chapterCount) {
            this.fileName = fileName;
            this.content = content;
            this.chapterCount = chapterCount;
        }
    }
}
