package com.fuck.fanqie.hooks.download;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class DownloadCaptureState {
    private final Set<String> notifiedTaskKeys = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> seenChapterKeys = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> activeBookIds = Collections.synchronizedSet(new HashSet<String>());
    private final Map<String, BookSnapshot> bookSnapshots = Collections.synchronizedMap(new HashMap<String, BookSnapshot>());

    public boolean isActiveBook(String bookId) {
        return bookId != null && activeBookIds.contains(bookId);
    }

    public void markActiveBook(String bookId) {
        if (bookId != null) {
            activeBookIds.add(bookId);
        }
    }

    public boolean markChapterSeen(String bookId, String chapterId) {
        if (bookId == null || chapterId == null) {
            return false;
        }
        return seenChapterKeys.add(bookId + ":" + chapterId);
    }

    public void saveChapter(String bookId, String bookName, String chapterId, String order, String title, String content) {
        synchronized (bookSnapshots) {
            BookSnapshot snapshot = bookSnapshots.get(bookId);
            if (snapshot == null) {
                snapshot = new BookSnapshot(bookId);
                bookSnapshots.put(bookId, snapshot);
            }
            saveChapter(snapshot, bookName, chapterId, order, title, content);
        }
    }

    public void saveChapter(BookSnapshot snapshot, String bookName, String chapterId, String order, String title, String content) {
        if (snapshot == null || chapterId == null || chapterId.length() == 0 || content == null || content.length() == 0) {
            return;
        }
        if (bookName != null && bookName.length() != 0) {
            snapshot.bookName = bookName;
        }
        ChapterSnapshot current = snapshot.chapters.get(chapterId);
        String mergedOrder = pickNonEmpty(current == null ? null : current.order, order);
        String mergedTitle = pickNonEmpty(current == null ? null : current.title, title);
        String mergedContent = pickNonEmpty(content, current == null ? null : current.content);
        snapshot.chapters.put(chapterId, new ChapterSnapshot(chapterId, mergedOrder, mergedTitle, mergedContent));
    }

    public BookSnapshot detachBookSnapshot(String bookId) {
        synchronized (bookSnapshots) {
            return bookSnapshots.remove(bookId);
        }
    }

    public boolean markFinishNotified(String taskKey) {
        return taskKey == null || notifiedTaskKeys.add(taskKey);
    }

    public void clearFinishFlag(String taskKey) {
        if (taskKey != null) {
            notifiedTaskKeys.remove(taskKey);
        }
    }

    public void clearBook(String bookId) {
        if (bookId == null) {
            return;
        }
        activeBookIds.remove(bookId);
        clearFinishFlag(bookId);
        synchronized (bookSnapshots) {
            bookSnapshots.remove(bookId);
        }
        synchronized (seenChapterKeys) {
            Iterator<String> iterator = seenChapterKeys.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key.startsWith(bookId + ":")) {
                    iterator.remove();
                }
            }
        }
    }

    private static String pickNonEmpty(String preferred, String fallback) {
        if (preferred != null && preferred.length() != 0) {
            return preferred;
        }
        return fallback;
    }

    public static final class ChapterSnapshot {
        public final String chapterId;
        public final String order;
        public final String title;
        public final String content;

        ChapterSnapshot(String chapterId, String order, String title, String content) {
            this.chapterId = chapterId;
            this.order = order;
            this.title = title;
            this.content = content;
        }
    }

    public static final class BookSnapshot {
        public final String bookId;
        public String bookName;
        public final Map<String, ChapterSnapshot> chapters = new HashMap<String, ChapterSnapshot>();

        public BookSnapshot(String bookId) {
            this.bookId = bookId;
        }
    }
}
