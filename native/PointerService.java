package com.meuapp.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class PointerService extends AccessibilityService {
    static PointerService inst;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
    }

    @Override
    public void onDestroy() {
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
}
