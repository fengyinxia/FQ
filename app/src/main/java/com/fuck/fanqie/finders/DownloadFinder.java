package com.fuck.fanqie.finders;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.TargetScanResult;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.UsingType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class DownloadFinder extends BaseFinder {
    public DownloadFinder(TargetScanResult scanResult) {
        super(scanResult);
    }

    @Override
    public void find(DexKitBridge bridge) {
        findReaderDirectoryPreloadClass(bridge);
        findStatusDispatcherMethod(bridge);
        findChapterDecryptMethod(bridge);
        findDownloadClickMethod(bridge);
    }

    private void findDownloadClickMethod(DexKitBridge bridge) {
        try {
            List<MethodData> candidates = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.user"})
                    .matcher(MethodMatcher.create()
                            .paramCount(9)
                            .returnType(Void.TYPE)
                            .addUsingString("点击下载权限判断: decision=")));
            List<MethodData> methods = new ArrayList<>();
            for (MethodData method : candidates) {
                List<String> types = method.getParamTypeNames();
                if (method.isMethod() && !Modifier.isStatic(method.getModifiers())
                        && "java.lang.String".equals(types.get(0))
                        && "android.content.Context".equals(types.get(1))
                        && "com.dragon.read.report.PageRecorder".equals(types.get(2))
                        && "java.lang.String".equals(types.get(3))
                        && "kotlin.jvm.functions.Function1".equals(types.get(4))
                        && "kotlin.jvm.functions.Function1".equals(types.get(5))
                        && "boolean".equals(types.get(7))) {
                    methods.add(method);
                }
            }
            if (methods.size() == 1) {
                cacheMethod(HookTargets.KEY_DOWNLOAD_CLICK_METHOD, methods.get(0));
            } else {
                log("下载点击入口无法唯一定位，候选数=" + methods.size());
            }
        } catch (Throwable throwable) {
            log("查找下载点击入口失败", throwable);
        }
    }

    private void findChapterDecryptMethod(DexKitBridge bridge) {
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.reader.utils"})
                    .matcher(MethodMatcher.create()
                            .paramTypes("java.lang.String", "com.dragon.read.reader.DecryptKey",
                                    "boolean", "java.lang.String", "java.lang.String")
                            .returnType(String.class)
                            .addUsingString("[ReaderSDKBiz] 解压章节内容耗时:")
                            .addUsingField("Lkotlin/text/Charsets;->UTF_8:Ljava/nio/charset/Charset;", UsingType.Read)
                            .addInvoke("Ljava/lang/String;-><init>([BLjava/nio/charset/Charset;)V")
                            .addInvoke(MethodMatcher.create()
                                    .paramTypes("java.lang.String", "com.dragon.read.reader.DecryptKey")
                                    .returnType("byte[]"))));
            if (methods.size() == 1 && methods.get(0).isMethod()
                    && Modifier.isStatic(methods.get(0).getModifiers())) {
                cacheMethod(HookTargets.KEY_CHAPTER_DECRYPT_METHOD, methods.get(0));
            } else {
                log("章节解码核心无法唯一定位或不是静态方法，候选数=" + methods.size());
            }
        } catch (Throwable throwable) {
            log("查找章节解码核心失败", throwable);
        }
    }

    private void findReaderDirectoryPreloadClass(DexKitBridge bridge) {
        try {
            ClassData classData = first(bridge.findClass(
                    FindClass.create().matcher(
                            ClassMatcher.create().usingStrings(new String[]{"阅读器目录预加载复用成功"})
                    )
            ));
            cacheClass(HookTargets.KEY_READER_DIRECTORY_PRELOAD_CLASS, classData);
        } catch (Throwable throwable) {
            log("查找阅读器目录预加载类失败", throwable);
        }
    }

    private void findStatusDispatcherMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().usingStrings(new String[]{"fail to execute percent change: "})
                    )
            ));
            cacheMethod(HookTargets.KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找下载状态分发方法失败", throwable);
        }
    }
}
