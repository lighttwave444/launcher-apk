package com.meuapp.launcher;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

public class ShizukuInput {
    private static Object im;
    private static Method inject;
    private static boolean three;
    private static Handler h;
    private static boolean ok;
    private static long lastTry;
    private static long downTime;
    private static volatile boolean active;

    private static synchronized boolean init() {
        if (ok) return true;
        long now = SystemClock.uptimeMillis();
        if (lastTry != 0 && now - lastTry < 3000) return false;
        lastTry = now;
        try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return false;
            if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            try { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L"); } catch (Throwable t) { Log.e("ShizukuInput", "bypass", t); }
            IBinder b = rikka.shizuku.SystemServiceHelper.getSystemService("input");
            IBinder w = new rikka.shizuku.ShizukuBinderWrapper(b);
            Object obj = Class.forName("android.hardware.input.IInputManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, w);
            Class<?> iface = Class.forName("android.hardware.input.IInputManager");
            try {
                inject = iface.getMethod("injectInputEvent", InputEvent.class, int.class);
                three = false;
            } catch (NoSuchMethodException e) {
                inject = iface.getMethod("injectInputEventToTarget", InputEvent.class, int.class, int.class);
                three = true;
            }
            im = obj;
            HandlerThread ht = new HandlerThread("shizuku-input");
            ht.start();
            h = new Handler(ht.getLooper());
            ok = true;
        } catch (Throwable t) {
            Log.e("ShizukuInput", "init", t);
            ok = false;
        }
        return ok;
    }

    private static MotionEvent ev(int action, float x, float y, long t, int buttons) {
        MotionEvent.PointerProperties[] p = { new MotionEvent.PointerProperties() };
        p[0].id = 0;
        p[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] c = { new MotionEvent.PointerCoords() };
        c[0].x = x;
        c[0].y = y;
        c[0].pressure = buttons != 0 ? 1f : 0f;
        c[0].size = 1f;
        return MotionEvent.obtain(downTime, t, action, 1, p, c, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private static void send(final MotionEvent e, long delay) {
        h.postDelayed(new Runnable() {
            public void run() {
                try {
                    if (three) inject.invoke(im, e, 0, -1);
                    else inject.invoke(im, e, 0);
                } catch (Throwable t) {
                    Log.e("ShizukuInput", "inject", t);
                } finally {
                    e.recycle();
                }
            }
        }, delay);
    }

    public static boolean down(float x, float y) {
        if (!init()) return false;
        downTime = SystemClock.uptimeMillis();
        send(ev(MotionEvent.ACTION_DOWN, x, y, downTime, MotionEvent.BUTTON_PRIMARY), 0);
        active = true;
        return true;
    }

    public static void move(float x, float y) {
        if (!init()) return;
        long t = SystemClock.uptimeMillis();
        if (active) send(ev(MotionEvent.ACTION_MOVE, x, y, t, MotionEvent.BUTTON_PRIMARY), 0);
        else send(ev(MotionEvent.ACTION_HOVER_MOVE, x, y, t, 0), 0);
    }

    public static boolean up(float x, float y) {
        if (!active) return false;
        active = false;
        send(ev(MotionEvent.ACTION_UP, x, y, SystemClock.uptimeMillis(), 0), 0);
        return true;
    }

    public static boolean click(float x, float y) {
        if (!init()) return false;
        long t = SystemClock.uptimeMillis();
        downTime = t;
        send(ev(MotionEvent.ACTION_DOWN, x, y, t, MotionEvent.BUTTON_PRIMARY), 0);
        send(ev(MotionEvent.ACTION_UP, x, y, t + 40, 0), 40);
        return true;
    }
}
