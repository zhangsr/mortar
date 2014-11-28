package com.cvte.mortar;

import android.util.Log;

import java.util.Locale;

/**
 * @description: Logging helper class.
 * @author: Saul
 * @date: 14-11-27
 * @version: 1.0
 */
public class MortarLog {
    private static String TAG = "Mortar";
    private static boolean DEBUG = true;

    public static void setTag(String tag) {
        d("Change log tag to %s", tag);
        TAG = tag;
    }

    public static void setEnable(boolean debuggable) {
        d("Disable debug log");
        DEBUG = debuggable;
    }

    public static void d(String format, Object... args) {
        if (DEBUG) {
            Log.d(TAG, buildMessage(format, args));
        }
    }

    public static void i(String format, Object... args) {
        Log.i(TAG, buildMessage(format, args));
    }

    public static void e(String format, Object... args) {
        Log.e(TAG, buildMessage(format, args));
    }

    /**
     * Formats the caller's provided message and prepends useful info like
     * calling thread ID and method name.
     */
    private static String buildMessage(String format, Object... args) {
        String msg = (args == null) ? format : String.format(Locale.US, format, args);
        StackTraceElement[] trace = new Throwable().fillInStackTrace().getStackTrace();

        String caller = "<unknown>";
        // Walk up the stack looking for the first caller outside of VolleyLog.
        // It will be at least two frames up, so start there.
        for (int i = 2; i < trace.length; i++) {
            Class<?> clazz = trace[i].getClass();
            if (!clazz.equals(MortarLog.class)) {
                String callingClass = trace[i].getClassName();
                callingClass = callingClass.substring(callingClass.lastIndexOf('.') + 1);
                callingClass = callingClass.substring(callingClass.lastIndexOf('$') + 1);

                caller = callingClass + "." + trace[i].getMethodName();
                break;
            }
        }
        return String.format(Locale.US, "[%d] %s: %s", Thread.currentThread().getId(), caller, msg);
    }
}
