package com.example.patientapp.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * ToastHelper - Centralized Toast Management for Android Java.
 * Handles UI thread safety, cancellation of active toasts, and memory leak prevention.
 */
public class ToastHelper {
    private static Toast currentToast;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Shows a toast message safely from any thread.
     * Automatically cancels the previous toast to avoid queuing.
     */
    public static void showToast(final Context context, final String message) {
        if (context == null || message == null) return;

        mainHandler.post(() -> {
            // Cancel existing toast if it's currently showing
            if (currentToast != null) {
                currentToast.cancel();
            }

            // Use Application Context to prevent activity-level memory leaks
            currentToast = Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT);
            currentToast.show();
        });
    }

    /**
     * Clears and cancels any active Toast. 
     * Recommended to be called in onDestroy() of the main activity or during app exit.
     */
    public static void clear() {
        mainHandler.post(() -> {
            if (currentToast != null) {
                currentToast.cancel();
                currentToast = null;
            }
        });
    }
}
