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
import java.util.List;

public class FeatureFinder extends BaseFinder {
    public FeatureFinder(TargetScanResult scanResult) {
        super(scanResult);
    }

    @Override
    public void find(DexKitBridge bridge) {
        findSplashK1Method(bridge);
        findUpdateMethod(bridge);
        findAbtestMethod(bridge);
        findChapterControlMethod(bridge);
        findCoverTitleRenderMethods(bridge);
    }

    private void findCoverTitleRenderMethods(DexKitBridge bridge) {
        try {
            List<MethodData> textMethods = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.reader.bookcover"})
                    .matcher(MethodMatcher.create()
                            .paramTypes("com.dragon.read.reader.bookcover.BookCoverInfo")
                            .returnType(Void.TYPE)
                            .addUsingField("Lcom/dragon/read/reader/bookcover/BookCoverInfo;->bookName:Ljava/lang/String;", UsingType.Read)
                            .addUsingField("Lcom/dragon/read/reader/bookcover/BookCoverInfo;->bookShortName:Ljava/lang/String;", UsingType.Read)
                            .addInvoke("Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V")));
            if (textMethods.size() != 1 || !textMethods.get(0).isMethod()) {
                log("封面文字渲染无法唯一定位，候选数=" + textMethods.size());
                return;
            }
            MethodData textMethod = textMethods.get(0);
            cacheMethod(HookTargets.KEY_COVER_TEXT_RENDER_METHOD, textMethod);

            List<MethodData> imageMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(textMethod.getDeclaredClassName())
                            .paramTypes("com.dragon.read.reader.bookcover.BookCoverInfo", "boolean")
                            .returnType(Void.TYPE)
                            .addUsingField("Lcom/dragon/read/reader/bookcover/BookCoverInfo;->bookNameUrl:Ljava/lang/String;", UsingType.Read)
                            .addInvoke(textMethod.getDescriptor())));
            if (imageMethods.size() == 1 && imageMethods.get(0).isMethod()) {
                cacheMethod(HookTargets.KEY_COVER_IMAGE_RENDER_METHOD, imageMethods.get(0));
            } else {
                log("封面图片渲染无法唯一定位，候选数=" + imageMethods.size());
            }
        } catch (Throwable throwable) {
            log("查找新版封面书名渲染失败", throwable);
        }
    }

    private void findChapterControlMethod(DexKitBridge bridge) {
        try {
            ClassData authorSayClass = first(bridge.findClass(
                    FindClass.create()
                            .searchPackages(new String[]{"com.dragon.read.social.author.reader"})
                            .matcher(
                                    ClassMatcher.create()
                                            .fieldCount(30, 31)
                                            .addMethod(
                                                    MethodMatcher.create()
                                                            .name("getAuthorRedBackgroundColor")
                                                            .modifiers(Modifier.PRIVATE)
                                                            .paramCount(0)
                                                            .returnType("android.graphics.drawable.Drawable")
                                            )
                            )
            ));
            if (authorSayClass != null) {
                MethodData authorSayMethod = first(bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .declaredClass(authorSayClass.getName())
                                        .name("a")
                                        .paramCount(0)
                                        .returnType("android.view.View")
                        )
                ));
                cacheMethod(HookTargets.KEY_AUTHOR_SAY_METHOD, authorSayMethod);
            }
        } catch (Throwable throwable) {
            log("查找作者说方法失败", throwable);
        }

        try {
            // 新版移除了 bookInfo 检查字符串；专属 AB 配置串仍在热评 View 构建入口。
            List<MethodData> coverMethods = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .addUsingString("book_cover_hot_comments_v617")
                                    .paramTypes(new String[]{
                                            "com.dragon.read.component.biz.interfaces.NsReaderActivity",
                                            "com.dragon.read.reader.bookcover.BookCoverInfo"})
                                    .addInvoke("Lcom/dragon/read/util/BookUtils;->isPublishBook(Ljava/lang/String;)Z")
                                    .addInvoke("Lcom/dragon/read/util/BookUtils;->isPublishBookGenreType(Ljava/lang/String;)Z")
                    )
            );
            if (coverMethods.isEmpty()) {
                // 保留旧版定位条件；不对多候选盲取第一个。
                coverMethods = bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .usingStrings(new String[]{"bookInfo"})
                                        .addInvoke("Lcom/dragon/read/util/BookUtils;->isPublishBook(Ljava/lang/String;)Z")
                                        .addInvoke("Lcom/dragon/read/util/BookUtils;->isPublishBookGenreType(Ljava/lang/String;)Z")
                        )
                );
            }
            if (coverMethods.size() == 1 && coverMethods.get(0).isMethod()) {
                cacheMethod(HookTargets.KEY_COVER_HOT_COMMENT_METHOD, coverMethods.get(0));
            } else {
                log("封面热门评论入口无法唯一定位，候选数=" + coverMethods.size());
            }
        } catch (Throwable throwable) {
            log("查找封面热门评论控件失败", throwable);
        }

        try {
            MethodData chapterEndControlMethod = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read.social.comment.reader"})
                            .matcher(
                                    MethodMatcher.create()
                                            .addInvoke("Lcom/dragon/read/base/ui/util/ScreenUtils;->getScreenWidth(Landroid/content/Context;)I")
                            )
            ));
            if (chapterEndControlMethod != null && !chapterEndControlMethod.isMethod()) {
                // 7.3.9.32 命中的是章末控件 View 的构造器；缓存可 Hook 的附着方法。
                chapterEndControlMethod = first(bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .declaredClass(chapterEndControlMethod.getDeclaredClassName())
                                        .name("onAttachedToWindow")
                                        .paramCount(0)
                                        .returnType(Void.TYPE)
                        )
                ));
                if (chapterEndControlMethod == null) {
                    log("章末控件构造器所在类没有 onAttachedToWindow，跳过");
                }
            }
            cacheMethod(HookTargets.KEY_CHAPTER_END_CONTROL_METHOD, chapterEndControlMethod);
        } catch (Throwable throwable) {
            log("查找章末控件失败", throwable);
        }

        try {
            MethodData chapterEndHotCommentMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .usingStrings(new String[]{"章末数据未请求完成，先不展示章末热门书评"})
                                    .paramTypes(new String[]{"java.lang.String"})
                    )
            ));
            cacheMethod(HookTargets.KEY_CHAPTER_END_HOT_COMMENT_METHOD, chapterEndHotCommentMethod);
        } catch (Throwable throwable) {
            log("查找章末热评方法失败", throwable);
        }
    }

    private void findSplashK1Method(DexKitBridge bridge) {
        try {
            MethodData splashMethod = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read.pages.splash"})
                            .matcher(
                                    MethodMatcher.create()
                                            .declaredClass("com.dragon.read.pages.splash.SplashActivity")
                                            .paramTypes(new String[]{"android.content.Intent", "android.os.Bundle"})
                                            .returnType(Void.TYPE)
                                            .addInvoke("Landroid/app/Activity;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V")
                            )
            ));
            if (splashMethod == null) {
                log("未找到启动页跳转方法");
                return;
            }
            cacheMethod(HookTargets.KEY_SPLASH_K1_METHOD, splashMethod);
        } catch (Throwable throwable) {
            log("查找启动页跳转方法失败", throwable);
        }
    }

    private void findUpdateMethod(DexKitBridge bridge) {
        try {
            // 旧版 MSG_CANCEL_PROGRESS 字符串已移除，当前更新消息由 z$b.handleMessage 处理。
            MethodData updateMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("com.ss.android.update.z$b")
                                    .name("handleMessage")
                                    .paramTypes(new String[]{"android.os.Message"})
                                    .returnType(Void.TYPE)
                    )
            ));
            if (updateMethod == null) {
                updateMethod = first(bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .paramTypes(new String[]{"android.os.Message"})
                                        .addUsingString("from MSG_CANCEL_PROGRESS")
                                        .addUsingString("reason_alpha_pkg_update_bg_download")
                        )
                ));
            }
            cacheMethod(HookTargets.KEY_UPDATE_METHOD, updateMethod);
        } catch (Throwable throwable) {
            log("查找更新处理方法失败", throwable);
        }

        try {
            MethodData checkUpdateMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .paramTypes(new String[]{"boolean"})
                                    .returnType(Boolean.TYPE)
                                    .addUsingString("https://ichannel.snssdk.com/check_version/v7/")
                                    .addUsingString("check_fail")
                                    .addUsingString("check")
                    )
            ));
            cacheMethod(HookTargets.KEY_CHECK_UPDATE_METHOD, checkUpdateMethod);
        } catch (Throwable throwable) {
            log("查找检查更新方法失败", throwable);
        }
    }

    private void findAbtestMethod(DexKitBridge bridge) {
        try {
            MethodData abtestMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .paramTypes(new String[]{"com.dragon.read.rpc.model.CommonAbResultData"})
                                    .usingStrings(new String[]{"key_user_activity_level"})
                    )
            ));
            cacheMethod(HookTargets.KEY_ABTEST_METHOD, abtestMethod);
        } catch (Throwable throwable) {
            log("查找 abtest 方法失败", throwable);
        }
    }
}
