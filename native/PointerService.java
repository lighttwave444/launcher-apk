package com.meuapp.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

public class PointerService extends AccessibilityService {
    static PointerService inst;
    private static final float GAIN = 2.5f;
    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View cursor, pad;
    private WindowManager.LayoutParams cLp;
    private float cx, cy, lx, ly, ox, oy, sx, sy, dens = 1;
    private int sw, sh;
    private boolean moved, dragging;

    private final Runnable longPress = new Runnable() {
        public void run() {
            dragging = true;
            sx = cx;
            sy = cy;
            if (cursor != null) cursor.setAlpha(0.5f);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
    }

    @Override
    public void onDestroy() {
        destroyPad();
        inst = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override
    public void onInterrupt() { }

    static boolean tap(float x, float y) {
        if (inst == null) return false;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 50);
        inst.dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
        return true;
    }

    static boolean swipe(float x1, float y1, float x2, float y2) {
        if (inst == null) return false;
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        double dist = Math.hypot(x2 - x1, y2 - y1);
        long dur = Math.max(400, (long) (dist * 1.2));
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, dur);
        inst.dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
        return true;
    }

    static boolean togglePad() {
        if (inst == null) return false;
        inst.h.post(new Runnable() {
            public void run() {
                if (inst == null) return;
                if (inst.pad == null) inst.buildPad(); else inst.destroyPad();
            }
        });
        return true;
    }

    private void moveCursor(float dx, float dy) {
        cx = Math.max(0, Math.min(sw - 1, cx + dx));
        cy = Math.max(0, Math.min(sh - 1, cy + dy));
        cLp.x = (int) cx;
        cLp.y = (int) cy;
        wm.updateViewLayout(cursor, cLp);
    }

    private void buildPad() {
        if (pad != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels;
        sh = dm.heightPixels;
        dens = dm.density;
        cx = sw / 2f;
        cy = sh / 2f;

        cursor = new View(this) {
            @Override
            protected void onDraw(Canvas c) {
                Path a = new Path();
                float w = getWidth(), hh = getHeight();
                a.moveTo(1, 1);
                a.lineTo(1, hh * 0.85f);
                a.lineTo(w * 0.3f, hh * 0.65f);
                a.lineTo(w * 0.55f, hh * 0.98f);
                a.lineTo(w * 0.72f, hh * 0.9f);
                a.lineTo(w * 0.48f, hh * 0.58f);
                a.lineTo(w * 0.9f, hh * 0.55f);
                a.close();
                Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                f.setColor(Color.WHITE);
                c.drawPath(a, f);
                Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
                s.setStyle(Paint.Style.STROKE);
                s.setStrokeWidth(3);
                s.setColor(Color.BLACK);
                c.drawPath(a, s);
            }
        };
        cLp = new WindowManager.LayoutParams((int) (26 * dens), (int) (38 * dens),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        cLp.gravity = Gravity.TOP | Gravity.LEFT;
        cLp.x = (int) cx;
        cLp.y = (int) cy;
        wm.addView(cursor, cLp);

        FrameLayout box = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x66000000);
        bg.setCornerRadius(16 * dens);
        bg.setStroke((int) Math.max(1, dens), 0x66FFFFFF);
        box.setBackground(bg);

        TextView hint = new TextView(this);
        hint.setText("toque = clique\nsegure e arraste = arrastar");
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint, new FrameLayout.LayoutParams(-1, -1));

        TextView x = new TextView(this);
        x.setText("\u00D7");
        x.setTextColor(Color.WHITE);
        x.setTextSize(22);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { destroyPad(); }
        });
        FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams((int) (40 * dens), (int) (40 * dens), Gravity.TOP | Gravity.RIGHT);
        box.addView(x, xl);

        box.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lx = ox = e.getRawX();
                        ly = oy = e.getRawY();
                        moved = false;
                        dragging = false;
                        h.postDelayed(longPress, 500);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - lx, dy = e.getRawY() - ly;
                        lx = e.getRawX();
                        ly = e.getRawY();
                        if (!moved && Math.hypot(lx - ox, ly - oy) > 12 * dens) {
                            moved = true;
                            if (!dragging) h.removeCallbacks(longPress);
                        }
                        if (moved || dragging) moveCursor(dx * GAIN, dy * GAIN);
                        break;
                    case MotionEvent.ACTION_UP:
                        h.removeCallbacks(longPress);
                        if (dragging) swipe(sx, sy, cx, cy);
                        else if (!moved) tap(cx, cy);
                        dragging = false;
                        cursor.setAlpha(1f);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(longPress);
                        dragging = false;
                        cursor.setAlpha(1f);
                        break;
                }
                return true;
            }
        });

        WindowManager.LayoutParams pLp = new WindowManager.LayoutParams((int) (240 * dens), (int) (170 * dens),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        pLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        pLp.x = (int) (8 * dens);
        pLp.y = (int) (100 * dens);
        pad = box;
        wm.addView(pad, pLp);
    }

    private void destroyPad() {
        h.removeCallbacks(longPress);
        try { if (pad != null) wm.removeView(pad); } catch (Throwable t) { }
        try { if (cursor != null) wm.removeView(cursor); } catch (Throwable t) { }
        pad = null;
        cursor = null;
    }
}
