package com.fuck.fanqie;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/** 独立进程等待宿主Binder死亡后拉起；重建可恢复，不靠固定延时猜测退出。 */
public final class RestartHostActivity extends Activity {
    public static final long REQUEST_TIMEOUT_MS = 10000;
    private static final String HOST = "com.dragon.read";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private IBinder lifetime;
    private ResultReceiver ready;
    private boolean requested, launched, linked, hostGone;
    private long deadline;
    private final IBinder.DeathRecipient death = () -> handler.post(() -> {
        hostGone = true;
        launchIfReady();
    });
    private final Runnable timeout = () -> {
        if (lifetime != null && !lifetime.isBinderAlive()) {
            hostGone = true; launchIfReady();
        } else fail("宿主未退出，请返回番茄重试");
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!HOST.equals(getCallingPackage()) && (state == null || !state.getBoolean("verified"))) {
            finish(); return;
        }
        try {
            Bundle handshake = getIntent().getBundleExtra("restart_handshake");
            lifetime = handshake == null ? null : handshake.getBinder("host_lifetime");
            ready = getIntent().getParcelableExtra("restart_ready");
            if (lifetime == null || ready == null) { finish(); return; }
            requested = state != null && state.getBoolean("requested");
            launched = state != null && state.getBoolean("launched");
            deadline = state == null ? handshake.getLong("deadline") : state.getLong("deadline");
            if (deadline <= 0) { finish(); return; }
            if (launched) { finish(); return; }
            TextView message = new TextView(this);
            message.setGravity(Gravity.CENTER);
            message.setText("正在重启番茄…");
            setContentView(message);
            hostGone = !lifetime.isBinderAlive();
            if (!hostGone) {
                try { lifetime.linkToDeath(death, 0); linked = true; }
                catch (android.os.RemoteException dead) { hostGone = true; }
            }
        } catch (RuntimeException error) {
            android.util.Log.e("FQRestart", "重启握手读取失败", error);
            fail("重启失败，请手动打开番茄");
        }
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused || lifetime == null || launched || isFinishing()) return;
        hostGone = hostGone || !lifetime.isBinderAlive();
        if (!hostGone && android.os.SystemClock.elapsedRealtime() >= deadline) {
            fail("重启请求已超时，请返回番茄重试"); return;
        }
        if (!hostGone && !requested) {
            // 必须先完成死亡订阅且承接窗口已获得焦点，宿主才允许自行退出。
            requested = true;
            ready.send(1, null);
        }
        launchIfReady();
        if (!launched) {
            handler.removeCallbacks(timeout);
            handler.postDelayed(timeout, Math.max(0, deadline - android.os.SystemClock.elapsedRealtime()));
        }
    }
    private void launchIfReady() {
        if (!hostGone || launched || !hasWindowFocus() || isFinishing() || isDestroyed()) return;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(HOST);
            if (launch == null) throw new IllegalStateException("宿主入口缺失");
            launched = true;
            handler.removeCallbacks(timeout);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch);
            finish();
        } catch (RuntimeException error) {
            android.util.Log.e("FQRestart", "宿主重新启动失败", error);
            fail("重新启动失败，请手动打开番茄");
        }
    }
    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("verified", true);
        state.putBoolean("requested", requested);
        state.putBoolean("launched", launched);
        state.putLong("deadline", deadline);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (linked && lifetime != null) {
            try { lifetime.unlinkToDeath(death, 0); }
            catch (java.util.NoSuchElementException ignored) { /* 死亡通知后订阅已移除。 */ }
        }
        super.onDestroy();
    }
}
