package com.fuck.fanqie;

import android.content.pm.PackageInfo;

final class PackageInfoCompat {
    private PackageInfoCompat() {
    }

    static long getLongVersionCode(PackageInfo packageInfo) {
        return packageInfo.getLongVersionCode();
    }
}
