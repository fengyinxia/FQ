package com.fuck.fanqie.hooks;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.robv.android.xposed.XposedBridge;

public final class HookUtils {
    private HookUtils() {
    }

    public static int peekInt(ByteBuffer byteBuffer, ByteOrder order) {
        byteBuffer.order(order);
        return byteBuffer.getInt(byteBuffer.position());
    }

    public static void logError(String prefix, Throwable throwable) {
        XposedBridge.log(prefix + throwable.getMessage());
    }
}
