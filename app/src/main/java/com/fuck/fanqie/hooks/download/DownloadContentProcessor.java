package com.fuck.fanqie.hooks.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

public final class DownloadContentProcessor {
    private final ClassLoader hostClassLoader;

    public DownloadContentProcessor(ClassLoader hostClassLoader) {
        this.hostClassLoader = hostClassLoader;
    }

    public DirectorySnapshot loadDirectorySnapshot(String bookId) {
        if (bookId == null || bookId.length() == 0) {
            return null;
        }
        try {
            Class<?> serviceClass = XposedHelpers.findClass("zo4.b0", hostClassLoader);
            Object service = XposedHelpers.callStaticMethod(serviceClass, "j");
            if (service == null) {
                return null;
            }
            Object single = XposedHelpers.callMethod(service, "h", bookId);
            if (single == null) {
                return null;
            }
            Object data = XposedHelpers.callMethod(single, "blockingGet");
            if (data == null) {
                return null;
            }

            DirectorySnapshot snapshot = new DirectorySnapshot();
            snapshot.bookName = getStringFieldSafely(getObjectFieldSafely(data, "bookInfo"), "bookName");

            List<?> itemDataList = getListFieldSafely(data, "itemDataList");
            Map<String, String> titleMap = new HashMap<String, String>();
            if (itemDataList != null) {
                for (Object itemData : itemDataList) {
                    String chapterId = getStringFieldSafely(itemData, "itemId");
                    if (chapterId == null || chapterId.length() == 0) {
                        continue;
                    }
                    String title = getStringFieldSafely(itemData, "title");
                    if (title != null && title.length() != 0) {
                        titleMap.put(chapterId, title);
                    }
                }
            }

            List<?> itemList = getListFieldSafely(data, "itemList");
            if (itemList != null && !itemList.isEmpty()) {
                int index = 0;
                for (Object chapterIdObj : itemList) {
                    if (chapterIdObj != null) {
                        addDirectoryChapter(snapshot, String.valueOf(chapterIdObj), titleMap.get(String.valueOf(chapterIdObj)), index++);
                    }
                }
            } else if (itemDataList != null) {
                int index = 0;
                for (Object itemData : itemDataList) {
                    addDirectoryChapter(snapshot, getStringFieldSafely(itemData, "itemId"), getStringFieldSafely(itemData, "title"), index++);
                }
            }
            return snapshot.chapterIds.isEmpty() ? null : snapshot;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public int backfillCachedChapters(
            String bookId,
            DownloadCaptureState.BookSnapshot snapshot,
            DirectorySnapshot directorySnapshot,
            DownloadCaptureState captureState
    ) {
        if (bookId == null || snapshot == null || directorySnapshot == null || directorySnapshot.chapterIds.isEmpty()) {
            return 0;
        }
        int restoredCount = 0;
        for (String chapterId : directorySnapshot.chapterIds) {
            try {
                Object chapterInfo = loadCachedChapterInfo(bookId, chapterId);
                if (chapterInfo == null) {
                    continue;
                }
                Object decryptedChapterInfo = decryptChapterInfo(chapterInfo);
                String content = getStringFieldSafely(decryptedChapterInfo, "content");
                if (content == null || content.length() == 0) {
                    continue;
                }
                content = extractPlainTextContent(decryptedChapterInfo, content);
                String title = pickNonEmpty(getStringFieldSafely(decryptedChapterInfo, "name"), directorySnapshot.chapterTitles.get(chapterId));
                String bookName = pickNonEmpty(snapshot.bookName, getStringFieldSafely(decryptedChapterInfo, "bookName"));
                if ((bookName == null || bookName.length() == 0) && directorySnapshot.bookName != null) {
                    bookName = directorySnapshot.bookName;
                }
                Integer orderIndex = directorySnapshot.chapterOrder.get(chapterId);
                String order = orderIndex == null ? null : String.valueOf(orderIndex.intValue());
                boolean hadChapter = snapshot.chapters.containsKey(chapterId);
                captureState.saveChapter(snapshot, bookName, chapterId, order, title, content);
                if (!hadChapter) {
                    restoredCount++;
                }
            } catch (Throwable ignored) {
            }
        }
        if ((snapshot.bookName == null || snapshot.bookName.length() == 0) && directorySnapshot.bookName != null) {
            snapshot.bookName = directorySnapshot.bookName;
        }
        return restoredCount;
    }

    public String extractPlainTextContent(Object chapterInfo, String fallbackContent) {
        if (fallbackContent == null || fallbackContent.length() == 0) {
            return fallbackContent;
        }
        try {
            Class<?> helperClass = XposedHelpers.findClass("com.dragon.read.reader.utils.ChapterOriginalContentHelper", hostClassLoader);
            Object helper = XposedHelpers.newInstance(helperClass);
            String originalContent = getStringFieldSafely(chapterInfo, "content");
            boolean restoreNeeded = originalContent == null || !fallbackContent.equals(originalContent);
            if (restoreNeeded) {
                XposedHelpers.setObjectField(chapterInfo, "content", fallbackContent);
            }
            try {
                Object result = XposedHelpers.callMethod(helper, "b0", chapterInfo);
                if (result instanceof String && ((String) result).length() != 0) {
                    return (String) result;
                }
            } finally {
                if (restoreNeeded) {
                    XposedHelpers.setObjectField(chapterInfo, "content", originalContent);
                }
            }
        } catch (Throwable ignored) {
            return fallbackContent;
        }
        return fallbackContent;
    }

    private Object loadCachedChapterInfo(String bookId, String chapterId) {
        try {
            Class<?> helperClass = XposedHelpers.findClass("com.dragon.read.reader.utils.ChapterOriginalContentHelper", hostClassLoader);
            Object helper = XposedHelpers.newInstance(helperClass);
            Object result = XposedHelpers.callMethod(helper, "h1", bookId, chapterId);
            if (result != null) {
                return result;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object companion = getChapterOriginalContentHelper();
            return companion == null ? null : XposedHelpers.callMethod(companion, "d", bookId, chapterId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object decryptChapterInfo(Object chapterInfo) {
        if (chapterInfo == null) {
            return null;
        }
        try {
            Class<?> helperClass = XposedHelpers.findClass("com.dragon.read.reader.utils.ChapterOriginalContentHelper", hostClassLoader);
            Object helper = XposedHelpers.newInstance(helperClass);
            Object single = XposedHelpers.callMethod(helper, "c0", chapterInfo);
            if (single != null) {
                Object decrypted = XposedHelpers.callMethod(single, "blockingGet");
                if (decrypted != null) {
                    return decrypted;
                }
            }
        } catch (Throwable ignored) {
            return chapterInfo;
        }
        return chapterInfo;
    }

    private void addDirectoryChapter(DirectorySnapshot snapshot, String chapterId, String title, int index) {
        if (snapshot == null || chapterId == null || chapterId.length() == 0) {
            return;
        }
        if (!snapshot.chapterOrder.containsKey(chapterId)) {
            snapshot.chapterIds.add(chapterId);
        }
        snapshot.chapterOrder.put(chapterId, Integer.valueOf(index));
        if (title != null && title.length() != 0) {
            snapshot.chapterTitles.put(chapterId, title);
        }
    }

    private Object getChapterOriginalContentHelper() {
        try {
            Class<?> helperClass = XposedHelpers.findClass("com.dragon.read.reader.utils.ChapterOriginalContentHelper", hostClassLoader);
            return XposedHelpers.getStaticObjectField(helperClass, "f192202c");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object getObjectFieldSafely(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> getListFieldSafely(Object target, String fieldName) {
        Object value = getObjectFieldSafely(target, fieldName);
        return value instanceof List ? (List<?>) value : null;
    }

    private String getStringFieldSafely(Object target, String fieldName) {
        Object value = getObjectFieldSafely(target, fieldName);
        return value instanceof String ? (String) value : null;
    }

    private static String pickNonEmpty(String preferred, String fallback) {
        return preferred != null && preferred.length() != 0 ? preferred : fallback;
    }

    public static final class DirectorySnapshot {
        public String bookName;
        public final List<String> chapterIds = new ArrayList<String>();
        public final Map<String, Integer> chapterOrder = new HashMap<String, Integer>();
        public final Map<String, String> chapterTitles = new HashMap<String, String>();
    }
}
