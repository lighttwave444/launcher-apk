package com.meuapp.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lista os apps instalados (com icone) e abre qualquer um deles. */
@CapacitorPlugin(name = "Apps")
public class AppsPlugin extends Plugin {
    private static final int ICON_PX = 108;
    private BroadcastReceiver receiver;

    @Override
    public void load() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyListeners("changed", new JSObject());
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        getContext().registerReceiver(receiver, f);
    }

    @Override
    protected void handleOnDestroy() {
        if (receiver != null) {
            try { getContext().unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiver = null;
        }
    }

    @PluginMethod
    public void list(PluginCall call) {
        try {
            final PackageManager pm = getContext().getPackageManager();
            final String self = getContext().getPackageName();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(main, 0);

            final List<String[]> rows = new ArrayList<>();
            final List<ResolveInfo> keep = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (ResolveInfo r : found) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(self) || !seen.add(pkg)) continue;
                CharSequence lb = r.loadLabel(pm);
                rows.add(new String[]{lb == null ? pkg : lb.toString(), pkg});
                keep.add(r);
            }
            // ordena por nome
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) idx.add(i);
            Collections.sort(idx, (a, b) -> rows.get(a)[0].toLowerCase(Locale.ROOT)
                    .compareTo(rows.get(b)[0].toLowerCase(Locale.ROOT)));

            JSArray arr = new JSArray();
            for (int i : idx) {
                JSObject o = new JSObject();
                o.put("name", rows.get(i)[0]);
                o.put("pkg", rows.get(i)[1]);
                o.put("icon", iconB64(keep.get(i).loadIcon(pm)));
                arr.put(o);
            }
            JSObject res = new JSObject();
            res.put("apps", arr);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Nao consegui listar os apps: " + e.getMessage());
        }
    }

    @PluginMethod
    public void launch(PluginCall call) {
        String pkg = call.getString("pkg");
        if (pkg == null) { call.reject("pkg ausente"); return; }
        Intent i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { call.reject("App nao encontrado"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    private static final java.util.LinkedHashSet<String> OPEN = new java.util.LinkedHashSet<>();

    static void bringOpenToFront(final android.content.Context ctx) {
        if (OPEN.isEmpty()) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                for (String p : new java.util.ArrayList<>(OPEN)) {
                    try {
                        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(p);
                        if (i == null) continue;
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                    } catch (Throwable t) { }
                }
            }
        }, 200);
    }

    private static int cascade = 0;

    @PluginMethod
    public void launchWindow(PluginCall call) {
        String pkg = call.getString("pkg");
        if (pkg == null) { call.reject("pkg ausente"); return; }
        Intent i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { call.reject("App nao encontrado"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        android.util.DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int w = dm.widthPixels * 85 / 100;
        int h = dm.heightPixels * 60 / 100;
        int step = cascade++ % 5;
        int x = dm.widthPixels * 3 / 100 * step;
        int y = dm.heightPixels * (8 + 4 * step) / 100;
        android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
        o.setLaunchBounds(new android.graphics.Rect(x, y, x + w, y + h));
        try {
            o.getClass().getMethod("setLaunchWindowingMode", int.class).invoke(o, 5);
        } catch (Throwable t) { }
        OPEN.add(pkg);
        getContext().startActivity(i, o.toBundle());
        call.resolve();
    }

    private static String iconB64(Drawable d) {
        try {
            Bitmap bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, ICON_PX, ICON_PX);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
