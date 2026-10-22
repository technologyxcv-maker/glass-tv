package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Points the remote's app buttons at apps of your choice (config.xml, app_keys), e.g. a Prime Video
 * button at another app. The key never reaches the TV's own hotkey handler, so the original app does not
 * open. Only the listed keys are consumed; every other key passes through untouched.
 * Opt-in: APP_KEY_SERVICE=1 build-and-install.sh (or -AppKeyService on Windows) enables it.
 */
public class AppKeyService extends AccessibilityService {
    private static final String TAG = "TvHome.AppKey";

    /** Key code to the package it opens. */
    private final SparseArray<String> apps = new SparseArray<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        for (String entry : getResources().getStringArray(R.array.app_keys)) {
            int eq = entry.indexOf('=');
            if (eq < 0) continue;
            int code = KeyEvent.keyCodeFromString(entry.substring(0, eq).trim());
            if (code == KeyEvent.KEYCODE_UNKNOWN) {
                Log.w(TAG, "unknown key in app_keys: " + entry);
                continue;
            }
            apps.put(code, entry.substring(eq + 1).trim());
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        String pkg = apps.get(event.getKeyCode());
        if (pkg == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            PackageManager pm = getPackageManager();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                Log.w(TAG, pkg + " has no launch activity");
            } else {
                try {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "could not open " + pkg, e);
                }
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
