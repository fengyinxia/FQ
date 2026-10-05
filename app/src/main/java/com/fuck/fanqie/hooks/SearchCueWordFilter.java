package com.fuck.fanqie.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedHelpers;

/** 只清理搜索框推荐词数据，不触碰输入框或用户输入。 */
final class SearchCueWordFilter {
    private static final String CUE_WORD_CLASS = "com.dragon.read.rpc.model.SearchCueWord";
    private static final String[] DISPLAY_FIELDS = {"text", "prefixText", "displayText", "displayTextV2"};
    private final Set<String> reportedFailures = Collections.synchronizedSet(new HashSet<String>());

    int clearList(List<?> words) {
        int changed = 0;
        for (Object word : words) {
            if (clearWord(word)) {
                changed++;
            }
        }
        return changed;
    }

    boolean clearWord(Object word) {
        if (word == null) {
            return false;
        }
        Object cueWord = word;
        if (!CUE_WORD_CLASS.equals(word.getClass().getName())) {
            cueWord = null;
            // 原生包装器和 KMP 包装器的字段名不同，按实际 DTO 类型识别。
            for (Field field : word.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !CUE_WORD_CLASS.equals(field.getType().getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    cueWord = field.get(word);
                } catch (Throwable throwable) {
                    reportFailure(word.getClass().getName() + "." + field.getName(), throwable);
                }
                break;
            }
            if (cueWord == null) {
                reportFailure(word.getClass().getName(),
                        new IllegalStateException("未找到可用的搜索提示 DTO"));
                return false;
            }
        }
        try {
            // 宿主内置“搜索书名/作者”等通用提示，不是服务器推荐热词。
            if (XposedHelpers.getBooleanField(cueWord, "isDefault")) {
                return false;
            }
        } catch (Throwable throwable) {
            reportFailure(CUE_WORD_CLASS + ".isDefault", throwable);
            return false;
        }
        boolean changed = false;
        for (String name : DISPLAY_FIELDS) {
            try {
                Object value = XposedHelpers.getObjectField(cueWord, name);
                if (value instanceof String && !((String) value).isEmpty()) {
                    XposedHelpers.setObjectField(cueWord, name, "");
                    changed = true;
                }
            } catch (Throwable throwable) {
                reportFailure(CUE_WORD_CLASS + "." + name, throwable);
            }
        }
        return changed;
    }

    private void reportFailure(String target, Throwable throwable) {
        if (reportedFailures.add(target)) {
            HookUtils.logError("FQHook+SearchWord: 无法清理热词字段/类型 " + target + ": ", throwable);
        }
    }
}
