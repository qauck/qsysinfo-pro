/********************************************************************************
 * (C) Copyright 2000-2021, by Shawn Q.
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.os.StatFs;
import android.preference.Preference;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.uguess.android.sysinfo.QSystemInfo.ErrorHandler;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Util
 */
public final class Util implements Constants {

    public static final int SDK_VER = getSDKVersion();
    public static final boolean INVERT_TITLE_COLOR_DEFAULT_SETTING = SDK_VER > 10 && SDK_VER < 22;

    static final String DATA_PATH_SENDCONTENT = "/SENDCONTENT/";
    static final String DATA_KEY_EMAIL = "email";
    static final String DATA_KEY_SUBJECT = "subject";
    static final String DATA_KEY_CONTENT = "content";
    static final String DATA_KEY_COMPRESSED = "compressed";

    private static final String PREF_FILE_NAME = "qsysinfopro.preferences"; //$NON-NLS-1$
    private static final String PROP_VERSION = "version"; //$NON-NLS-1$
    private static final String PROP_TIMESTAMP = "stamp"; //$NON-NLS-1$

    private static String cachedWifiDevice = null;
    private static String cachedCellDevice = null;

    private static final Field fdTargetSdkVersion = getField(ApplicationInfo.class, "targetSdkVersion"); //$NON-NLS-1$
    private static final Field fdInstallLocation = getField(PackageInfo.class, "installLocation"); //$NON-NLS-1$
    private static final Field fdScreenLayout = getField(Configuration.class, "screenLayout"); //$NON-NLS-1$
    private static final Field fdNotificationPriority = getField(Notification.class, "priority"); //$NON-NLS-1$

    private static final Method mdStartForeground = getMethod(Service.class, "startForeground", //$NON-NLS-1$
            int.class, Notification.class);
    private static final Method mdStopForeground = getMethod(Service.class, "stopForeground", //$NON-NLS-1$
            boolean.class);
    private static final Method mdSetForeground = getMethod(Service.class, "setForeground", //$NON-NLS-1$
            boolean.class);
    private static final Method mdSetAllCaps = getMethod(TextView.class, "setAllCaps", //$NON-NLS-1$
            boolean.class);
    private static final Method mdSetTextIsSelectable = getMethod(TextView.class, "setTextIsSelectable", //$NON-NLS-1$
            boolean.class);
    private static final Method mdDebugGetPss = getMethod(Debug.class, "getPss", //$NON-NLS-1$
            int.class, long[].class);
    private static final Method mdSetShowAsAction = getMethod(MenuItem.class, "setShowAsAction", //$NON-NLS-1$
            int.class);
    private static final Method mdHasSystemFeature = getMethod(PackageManager.class, "hasSystemFeature", String.class);

    private static final Method mtdStartForegroundService = getMethod(26, true, Context.class,
            "startForegroundService", Intent.class);
    private static final Method mtdGetForegroundService = getMethod(26, true, PendingIntent.class,
            "getForegroundService", Context.class, int.class, Intent.class, int.class);

    private static UtilProxy utilProxy;

    static void setUtilProxy(UtilProxy impl) {
        utilProxy = impl;
    }

    static Class<?> getIntentProxyClz(Class<?> clz) {
        if (utilProxy != null) {
            return utilProxy.getIntentProxyClz(clz);
        }
        return clz;
    }

    static AlertDialogBuilder newAlertDialogBuilder(Context ctx) {
        if (utilProxy != null) {
            return utilProxy.newAlertDialogBuilder(ctx);
        }
        return new AlertDialogBuilderImpl(ctx);
    }

    static String getTargetSdkVersion(Context ctx, ApplicationInfo ai) {
        if (fdTargetSdkVersion != null) {
            try {
                return String.valueOf(fdTargetSdkVersion.get(ai));
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return ResUtil.getString(ctx, R.string.unknown);
    }

    static int getInstallLocation(PackageInfo pi) {
        if (fdInstallLocation != null) {
            try {
                return ((Integer) fdInstallLocation.get(pi)).intValue();
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return -1;
    }

    static void startForeground(Service sc, int id, Notification nc) {
        if (mdStartForeground == null) {
            if (mdSetForeground != null) {
                try {
                    mdSetForeground.invoke(sc, true);
                } catch (Exception e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        } else {
            try {
                mdStartForeground.invoke(sc, id, nc);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static void stopForeground(Service sc) {
        if (mdStopForeground == null) {
            if (mdSetForeground != null) {
                try {
                    mdSetForeground.invoke(sc, false);
                } catch (Exception e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        } else {
            try {
                mdStopForeground.invoke(sc, Boolean.TRUE);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static int getScreenLayout(Configuration conf) {
        if (fdScreenLayout != null) {
            try {
                Object result = fdScreenLayout.get(conf);

                if (result instanceof Integer) {
                    return ((Integer) result).intValue();
                }
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return 0;
    }

    static void setScreenLayout(Configuration conf, int value) {
        if (fdScreenLayout != null) {
            try {
                fdScreenLayout.set(conf, value);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static void startForegroundService(Context ctx, Intent it, Intent fallback) {
        if (mtdStartForegroundService != null) {
            try {
                mtdStartForegroundService.invoke(ctx, it);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        } else if (fallback != null) {
            ctx.startService(fallback);
        }
    }

    static PendingIntent getForegroundServicePendingIntent(Context ctx, int requestCode, Intent it, int flags, Intent fallback) {
        if (mtdGetForegroundService != null) {
            try {
                // return foreground service pending intent
                return (PendingIntent) mtdGetForegroundService.invoke(null, ctx, requestCode, it, flags);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
        // fallback to normal service pending intent
        return PendingIntent.getService(ctx, requestCode, fallback, flags);
    }

    static void setNotificationPriority(Notification nc, int priority) {
        if (fdNotificationPriority != null) {
            try {
                fdNotificationPriority.set(nc, priority);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static void setDefaultNotificationChannel(Notification nc, Context ctx) {
        try {
            Class ncClz = Class.forName("android.app.NotificationChannel");
            Constructor newNc = ncClz.getDeclaredConstructor(String.class, CharSequence.class, int.class);
            Object channel = newNc.newInstance("main", "Monitor", 2/*IMPORTANCE_LOW*/);

            NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

            Method mtdCreateNotificationChannel = NotificationManager.class.getDeclaredMethod("createNotificationChannel", ncClz);
            mtdCreateNotificationChannel.invoke(notificationManager, channel);

            Field fdChannelId = Notification.class.getDeclaredField("mChannelId");
            fdChannelId.setAccessible(true);
            fdChannelId.set(nc, "main");
        } catch (Throwable e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    static void setLatestEventInfo(Notification nc, Context ctx, CharSequence contentTitle,
                                   CharSequence contentText, PendingIntent contentIntent) {
        if (SDK_VER >= 26) {
            nc.contentIntent = contentIntent;

            boolean invTitleColor = false;
            RemoteViews contentView = new RemoteViews(ctx.getPackageName(), invTitleColor ? R.layout.history_graph_simple : R.layout.history_graph_simple_light);

            contentView.setImageViewResource(R.id.icon, R.drawable.icon_m);

            contentView.setTextViewText(R.id.title, contentTitle);
            contentView.setTextColor(R.id.title, invTitleColor ? android.graphics.Color.WHITE : 0xde000000);

            contentView.setTextViewText(R.id.text, contentText);
            contentView.setTextColor(R.id.text, invTitleColor ? 0xb3ffffff : 0x8a000000);

            nc.contentView = contentView;
        } else {
            nc.setLatestEventInfo(ctx, contentTitle, contentText, contentIntent);
        }
    }

    static void setAllCaps(TextView tv, boolean value) {
        if (mdSetAllCaps != null) {
            try {
                mdSetAllCaps.invoke(tv, value);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static void setTextIsSelectable(TextView tv, boolean value) {
        if (mdSetTextIsSelectable != null) {
            try {
                mdSetTextIsSelectable.invoke(tv, value);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static void setShowAsAction(MenuItem mi, int flag) {
        if (mdSetShowAsAction != null) {
            try {
                mdSetShowAsAction.invoke(mi, flag);
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static long debugGetPss(int pid) {
        if (mdDebugGetPss != null) {
            try {
                Object result = mdDebugGetPss.invoke(null, pid, null);

                if (result instanceof Long) {
                    return ((Long) result).longValue();
                }
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return -1;
    }

    static void requestBackup(Context ctx) {
        if (SDK_VER < 8) {
            return;
        }

        return;
        // disalbe this capability for now.
        // try
        // {
        //			Class<?> clz = Class.forName( "android.app.backup.BackupManager" ); //$NON-NLS-1$
        //			Method mdDataChanged = clz.getDeclaredMethod( "dataChanged", (Class<?>[]) null ); //$NON-NLS-1$
        //
        // Object bm = clz.getConstructor( Context.class ).newInstance( ctx );
        // mdDataChanged.invoke( bm, (Object[]) null );
        // }
        // catch ( Exception e )
        // {
        // Log.e( Util.class.getName( ), e.getLocalizedMessage( ), e );
        // }
    }

    static void killSelf(Handler handler, Activity ac, ActivityManager am, String pkgName) {
        int sdkInt = 0;

        try {
            sdkInt = Integer.parseInt(Build.VERSION.SDK);
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }

        if (sdkInt < 8) {
            ac.finish();
            am.restartPackage(pkgName);
        } else {
            ac.finish();
            ac.stopService(new Intent(ac, StatusUpdaterService.class));

            handler.postDelayed(new Runnable() {

                public void run() {
                    Process.killProcess(Process.myPid());
                }
            }, 500);
        }
    }

    /**
     * @return -1->sys ignored, 1->user ignored, 0->killable
     */
    static int killable(String pkgName, String self, Collection<String> ignoreList, boolean useGuard,
                        Collection<String> guardList) {
        if (pkgName.equals(self) || isSysProcess(pkgName)) {
            return -1;
        }

        if (ignoreList != null && ignoreList.contains(pkgName)) {
            return 1;
        } else if (useGuard && (guardList == null || !guardList.contains(pkgName))) {
            return 1;
        }

        return 0;
    }

    static boolean isSysProcess(String pkgName) {
        return pkgName.equals("android") //$NON-NLS-1$
                || pkgName.startsWith("com.google.process") //$NON-NLS-1$
                || pkgName.startsWith("android.ext.") //$NON-NLS-1$
                || pkgName.startsWith("com.android.phone") //$NON-NLS-1$
                || pkgName.startsWith("android.process") //$NON-NLS-1$
                || pkgName.startsWith("system") //$NON-NLS-1$
                || pkgName.startsWith("zygote") //$NON-NLS-1$
                || pkgName.startsWith("com.android.bluetooth") //$NON-NLS-1$
                || pkgName.startsWith("com.android.nfc") //$NON-NLS-1$
                || pkgName.startsWith("com.android.inputmethod") //$NON-NLS-1$
                || pkgName.startsWith("com.android.alarmclock"); //$NON-NLS-1$
    }

    static boolean isSysApp(Context ctx) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), 0);
            if (ai != null) {
                return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
        return false;
    }

    static EditorState beginEditOption(Context ctx, String storeName) {
        EditorState state = new EditorState();
        state.ctx = ctx;
        state.storeName = storeName;
        state.changed = false;

        return state;
    }

    static void endEditOption(EditorState state) {
        if (state.changed && state.et != null) {
            state.et.commit();

            requestBackup(state.ctx);
        }
    }

    static int getIntOption(SharedPreferences sp, String key, int defValue) {
        if (sp != null) {
            return sp.getInt(key, defValue);
        }
        return defValue;
    }

    static int getIntOption(Context ctx, String storeName, String key, int defValue) {
        SharedPreferences sp = ctx.getSharedPreferences(storeName, Context.MODE_PRIVATE);
        if (sp != null) {
            return sp.getInt(key, defValue);
        }
        return defValue;
    }

    private static void setIntOption(EditorState state, String key, int val) {
        if (state.et == null) {
            state.et = state.ctx.getSharedPreferences(state.storeName, Context.MODE_PRIVATE).edit();
        }

        state.changed = true;
        state.et.putInt(key, val);
    }

    static boolean getBooleanOption(SharedPreferences sp, String key) {
        return getBooleanOption(sp, key, true);
    }

    static boolean getBooleanOption(SharedPreferences sp, String key, boolean defValue) {
        if (sp != null) {
            return sp.getBoolean(key, defValue);
        }
        return defValue;
    }

    static boolean getBooleanOption(Context ctx, String storeName, String key) {
        return getBooleanOption(ctx, storeName, key, true);
    }

    static boolean getBooleanOption(Context ctx, String storeName, String key, boolean defValue) {
        SharedPreferences sp = ctx.getSharedPreferences(storeName, Context.MODE_PRIVATE);
        if (sp != null) {
            return sp.getBoolean(key, defValue);
        }
        return defValue;
    }

    private static void setBooleanOption(EditorState state, String key, boolean val) {
        if (state.et == null) {
            state.et = state.ctx.getSharedPreferences(state.storeName, Context.MODE_PRIVATE).edit();
        }

        state.changed = true;
        state.et.putBoolean(key, val);
    }

    static String getStringOption(SharedPreferences sp, String key, String defValue) {
        if (sp != null) {
            return sp.getString(key, defValue);
        }
        return defValue;
    }

    public static String getStringOption(Context ctx, String storeName, String key, String defValue) {
        SharedPreferences sp = ctx.getSharedPreferences(storeName, Context.MODE_PRIVATE);
        if (sp != null) {
            return sp.getString(key, defValue);
        }
        return defValue;
    }

    public static void setStringOption(Context ctx, SharedPreferences sp, String key, String val) {
        if (sp != null) {
            Editor et = sp.edit();
            if (val == null) {
                et.remove(key);
            } else {
                et.putString(key, val);
            }
            et.commit();

            requestBackup(ctx);
        }
    }

    public static void setStringOption(Context ctx, String storeName, String key, String val) {
        SharedPreferences sp = ctx.getSharedPreferences(storeName, Context.MODE_PRIVATE);

        setStringOption(ctx, sp, key, val);
    }

    private static void setStringOption(EditorState state, String key, String val) {
        if (state.et == null) {
            state.et = state.ctx.getSharedPreferences(state.storeName, Context.MODE_PRIVATE).edit();
        }

        state.changed = true;

        if (val == null) {
            state.et.remove(key);
        } else {
            state.et.putString(key, val);
        }
    }

    public static void shortToast(Context context, int resId) {
        Toast.makeText(context, ResUtil.getString(context, resId), Toast.LENGTH_SHORT).show();
    }

    public static void shortToast(Context context, int resId, Object... formatArgs) {
        Toast.makeText(context, ResUtil.getString(context, resId, formatArgs), Toast.LENGTH_SHORT).show();
    }

    public static void shortToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void longToast(Context context, int resId) {
        Toast.makeText(context, ResUtil.getString(context, resId), Toast.LENGTH_LONG).show();
    }

    public static void longToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    public static void safeStartActivity(Context ctx, Intent it, boolean logOnly) {
        try {
            ctx.startActivity(it);
        } catch (Exception e) {
            Log.e(Util.class.getName(), "Failed to start activity for intent: " + it.toString(), //$NON-NLS-1$
                    e);

            if (!logOnly) {
                shortToast(ctx, R.string.run_failed);
            }
        }
    }

    public static void safeStartActivityForResult(Activity ac, Intent it, int request, boolean logOnly) {
        try {
            ac.startActivityForResult(it, request);
        } catch (Exception e) {
            Log.e(Util.class.getName(), "Failed to start activity for intent: " + it.toString(), //$NON-NLS-1$
                    e);

            if (!logOnly) {
                shortToast(ac, R.string.run_failed);
            }
        }
    }

    public static void safeDismissDialog(Dialog dialog) {
        try {
            dialog.dismiss();
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    static String safeFormatFileSize(Context ctx, long size) {
        try {
            return Formatter.formatFileSize(ctx, size);
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }

        return String.valueOf(size);
    }

    static boolean updateIntOption(Intent data, EditorState state, String key, int defValue) {
        if (data != null) {
            int t = data.getIntExtra(key, defValue);
            if (t != getIntOption(state.ctx, state.storeName, key, defValue)) {
                setIntOption(state, key, t);
                return true;
            }
        }
        return false;
    }

    static boolean updateIntOption(EditorState state, String key, int value, int defValue) {
        if (value != getIntOption(state.ctx, state.storeName, key, defValue)) {
            setIntOption(state, key, value);
            return true;
        }
        return false;
    }

    static boolean updateBooleanOption(Intent data, EditorState state, String key) {
        return updateBooleanOption(data, state, key, true);
    }

    static boolean updateBooleanOption(Intent data, EditorState state, String key, boolean defValue) {
        if (data != null) {
            boolean b = data.getBooleanExtra(key, defValue);
            if (b != getBooleanOption(state.ctx, state.storeName, key, defValue)) {
                setBooleanOption(state, key, b);
                return true;
            }
        }
        return false;
    }

    static boolean updateBooleanOption(EditorState state, String key, boolean value, boolean defValue) {
        if (value != getBooleanOption(state.ctx, state.storeName, key, defValue)) {
            setBooleanOption(state, key, value);
            return true;
        }
        return false;
    }

    static boolean updateBooleanOptionForce(EditorState state, String key, boolean value) {
        setBooleanOption(state, key, value);
        return true;
    }

    static boolean updateStringOption(Intent data, EditorState state, String key) {
        if (data != null) {
            String s = data.getStringExtra(key);

            if (s != null) {
                s = s.trim();

                if (s.length() == 0) {
                    s = null;
                }
            }

            if (!TextUtils.equals(s, getStringOption(state.ctx, state.storeName, key, null))) {
                setStringOption(state, key, s);
                return true;
            }
        }
        return false;
    }

    static boolean updateStringListOption(EditorState state, String key, Collection<String> list) {
        String saved = getStringOption(state.ctx, state.storeName, key, null);

        if (list == null || list.isEmpty()) {
            if (saved != null) {
                setStringOption(state, key, null);
                return true;
            }
        } else {
            StringBuffer sb = new StringBuffer();

            int i = 0;
            for (String s : list) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(s);

                i++;
            }

            String current = sb.toString();

            if (!TextUtils.equals(saved, current)) {
                setStringOption(state, key, current);
                return true;
            }
        }
        return false;
    }

    /**
     * @return Converts the input to strings like "0100011", each char stands for a boolean value.
     */
    static String getBitsString(boolean[] bits) {
        StringBuilder sb = new StringBuilder();

        for (boolean bit : bits) {
            sb.append(bit ? '1' : '0');
        }

        return sb.toString();
    }

    static boolean[] getBits(String val, int length, boolean defValue) {
        boolean[] bits = new boolean[length];
        Arrays.fill(bits, defValue);

        if (val != null) {
            for (int i = 0, size = Math.min(val.length(), length); i < size; i++) {
                bits[i] = val.charAt(i) == '1';
            }
        }

        return bits;
    }

    static void sendContent(Context ctx, String email, String subject, String content, boolean compressed) {
        if (utilProxy != null) {
            utilProxy.sendContent(ctx, email, subject, content, compressed);
            return;
        }

        Intent it = new Intent(Intent.ACTION_SEND);

        it.putExtra(Intent.EXTRA_SUBJECT, subject);

        if (email != null) {
            it.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        }

        if (compressed) {
            it.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(content)));
            it.putExtra(Intent.EXTRA_TEXT, subject);
            it.setType("application/zip"); //$NON-NLS-1$
        } else {
            it.putExtra(Intent.EXTRA_TEXT, content);
            it.setType("text/plain"); //$NON-NLS-1$
        }

        it = Intent.createChooser(it, null);

        it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);

        safeStartActivity(ctx, it, false);
    }

    static void handleMsgSendContentReady(String content, String prefix, Context ctx,
                                          boolean compressed) {
        if (content == null) {
            shortToast(ctx, R.string.no_content_sent);
        } else {
            String email = getStringOption(ctx, SYSINFO_MANAGER_STORE_NAME, PREF_KEY_DEFAULT_EMAIL,null);
            sendContent(ctx, email, prefix + new Date().toLocaleString(), content, compressed);
        }
    }

    static void checkForceCompression(final Handler handler, final Context ctx, final String content,
                                      final int format, final String title) {
        Log.d(Util.class.getName(), "VM Max size: " //$NON-NLS-1$
                + Runtime.getRuntime().maxMemory());

        Log.d(Util.class.getName(), "Sending content size: " //$NON-NLS-1$
                + content.length());

        if (content != null && content.length() > 250 * 1024) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    String sendContent = createCompressedContent(null, ctx, content, format, title);

                    handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, format, 1, sendContent));
                }
            };

            newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.warning))
                    .setMessage(ResUtil.getString(ctx, R.string.size_warning))
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();
        } else {
            handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, format, 0, content));
        }
    }

    static String createCompressedContent(Handler handler, Context ctx, String content, int format,
                                          String filePrefix) {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File path = Environment.getExternalStorageDirectory();

            File tf = new File(path, "logs"); //$NON-NLS-1$

            if (!tf.exists()) {
                if (!tf.mkdirs()) {
                    if (handler == null) {
                        shortToast(ctx, R.string.error_create_folder, tf.getAbsolutePath());
                    } else {
                        handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                                ResUtil.getString(ctx, R.string.error_create_folder, tf.getAbsolutePath())));
                    }

                    return null;
                }
            }

            File zf = new File(tf, filePrefix + Math.abs(System.currentTimeMillis()) + ".zip"); //$NON-NLS-1$

            ZipOutputStream zos = null;
            try {
                zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zf)));

                String ext = ".txt"; //$NON-NLS-1$

                switch (format) {
                    case HTML:
                        ext = ".html"; //$NON-NLS-1$
                        break;
                    case CSV:
                        ext = ".csv"; //$NON-NLS-1$
                        break;
                }

                zos.putNextEntry(new ZipEntry(filePrefix + ext));

                zos.write(content.getBytes());

                zos.closeEntry();

                return zf.getAbsolutePath();
            } catch (IOException e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            } finally {
                if (zos != null) {
                    try {
                        zos.close();
                    } catch (IOException e) {
                        Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                    }
                }
            }
        } else {
            if (handler == null) {
                shortToast(ctx, R.string.error_sdcard);
            } else {
                handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                        ResUtil.getString(ctx, R.string.error_sdcard)));
            }
        }

        return null;
    }

    public static synchronized void hookExceptionHandler(Context ctx) {
        UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        if (oldHandler == null
                || (!(oldHandler instanceof ErrorHandler || oldHandler.getClass().getName()
                .startsWith("com.flurry.android")))) //$NON-NLS-1$
        {
            Thread.setDefaultUncaughtExceptionHandler(new ErrorHandler(ctx, oldHandler));
        }
    }

    static Intent getSettingsIntent(PackageManager pm, String clzName) {
        Intent it = new Intent(Intent.ACTION_MAIN);
        it.setClassName("com.android.settings", clzName); //$NON-NLS-1$

        List<ResolveInfo> acts = pm.queryIntentActivities(it, 0);

        if (acts.size() > 0) {
            return it;
        }

        return null;
    }

    static String getPackageIssuer(Context ctx, String pkgName, String filePath) {
        try {
            PackageInfo pi = null;

            if (filePath != null) {
                pi = ctx.getPackageManager().getPackageArchiveInfo(filePath, PackageManager.GET_SIGNATURES);
            } else {
                pi = ctx.getPackageManager().getPackageInfo(pkgName, PackageManager.GET_SIGNATURES);
            }

            if (pi != null && pi.signatures != null && pi.signatures.length > 0) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509"); //$NON-NLS-1$

                Certificate cert =
                        cf.generateCertificate(new ByteArrayInputStream(pi.signatures[0].toByteArray()));

                if (cert instanceof X509Certificate) {
                    X509Certificate xcert = (X509Certificate) cert;

                    String issuer = xcert.getIssuerDN().getName();

                    if (issuer != null) {
                        return issuer;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }

        return ResUtil.getString(ctx, R.string.unknown);
    }

    static void showManageApps(Context ctx) {
        Intent it = new Intent("android.settings.APPLICATION_SETTINGS"); //$NON-NLS-1$
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        List<ResolveInfo> acts = ctx.getPackageManager().queryIntentActivities(it, 0);

        if (acts.size() > 0) {
            safeStartActivity(ctx, it, false);
        } else {
            Log.d(Util.class.getName(), "Failed to resolve activity for InstalledApps"); //$NON-NLS-1$
        }
    }

    static void showPackageDetails(Context ctx, String pkgName) {
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        it.setClassName("com.android.settings", //$NON-NLS-1$
                "com.android.settings.InstalledAppDetails"); //$NON-NLS-1$
        it.putExtra("com.android.settings.ApplicationPkgName", pkgName); //$NON-NLS-1$
        // this is for Froyo
        it.putExtra("pkg", pkgName); //$NON-NLS-1$

        List<ResolveInfo> acts = ctx.getPackageManager().queryIntentActivities(it, 0);

        if (acts.size() > 0) {
            ctx.startActivity(it);
        } else {
            // for ginger bread
            it = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", //$NON-NLS-1$
                    Uri.fromParts("package", pkgName, null)); //$NON-NLS-1$
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            acts = ctx.getPackageManager().queryIntentActivities(it, 0);

            if (acts.size() > 0) {
                ctx.startActivity(it);
            } else {
                Log.d(Util.class.getName(), "Failed to resolve activity for InstalledAppDetails"); //$NON-NLS-1$
                shortToast(ctx, "No application to handle this request");
            }
        }
    }

    static void checkStoragePermission(final Activity ctx) {
        if (SDK_VER >= 23 && ctx.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            OnClickListener listener = new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    showPackageDetails(ctx, ctx.getPackageName());
                }
            };

            Util.newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.prompt))
                    .setMessage(ResUtil.getString(ctx, R.string.storage_perm_alt))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();
        }
    }

    static void checkPhonePermission(final Activity ctx) {
        if (SDK_VER >= 23 && ctx.checkCallingOrSelfPermission("android.permission.READ_PHONE_STATE") != PackageManager.PERMISSION_GRANTED) {
            OnClickListener listener = new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    showPackageDetails(ctx, ctx.getPackageName());
                }
            };

            Util.newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.prompt))
                    .setMessage(ResUtil.getString(ctx, R.string.phone_perm_alt))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();
        }
    }

    synchronized static String getActiveWifiDevice() {
        if (cachedWifiDevice != null) {
            return cachedWifiDevice;
        }

        String[] infs = new String[]{"eth0", "tiwlan0", "wlan0", "athwlan0", "eth1" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        };

        for (String inf : infs) {
            if (isNetInterfaceUp(inf)) {
                cachedWifiDevice = inf;
                return inf;
            }
        }

        File buildFile = new File(Environment.getRootDirectory(), "build.prop"); //$NON-NLS-1$

        if (buildFile.exists() && buildFile.isFile() && buildFile.canRead()) {
            Properties props = new Properties();

            InputStream is = null;

            try {
                is = new FileInputStream(buildFile);
                props.load(is);

                String inf = props.getProperty("wifi.interface"); //$NON-NLS-1$

                if (!TextUtils.isEmpty(inf) && isNetInterfaceUp(inf)) {
                    cachedWifiDevice = inf;
                    return inf;
                }
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                    }
                }
            }
        }

        return null;
    }

    synchronized static String getActiveCellDevice() {
        if (cachedCellDevice != null) {
            return cachedCellDevice;
        }

        String[] infs = new String[]{"rmnet0", //$NON-NLS-1$
                "rmnet_usb0", //$NON-NLS-1$
                "pdp0", //$NON-NLS-1$
                "ppp0", //$NON-NLS-1$
                "vsnet0", //$NON-NLS-1$
                "pdp_ip0", //$NON-NLS-1$
                "rmnet_sdio0", //$NON-NLS-1$
                "rmnet_data0", //$NON-NLS-1$
                "wimax0", //$NON-NLS-1$
                "rmnet1", //$NON-NLS-1$
                "rmnet2", //$NON-NLS-1$
                "rmnet3", //$NON-NLS-1$
                "cdma_rmnet4", //$NON-NLS-1$
                "eth0", //$NON-NLS-1$
                "wlan0", //$NON-NLS-1$
                "ifb0", //$NON-NLS-1$
                "ifb1"
        };

        for (String inf : infs) {
            if (isNetInterfaceUp(inf)) {
                cachedCellDevice = inf;
                return inf;
            }
        }

        return null;
    }

    static long getRxBytes(String inf) {
        return readFileLong("/sys/class/net/" + inf + "/statistics/rx_bytes", false); //$NON-NLS-1$ //$NON-NLS-2$
    }

    static long getTxBytes(String inf) {
        return readFileLong("/sys/class/net/" + inf + "/statistics/tx_bytes", false); //$NON-NLS-1$ //$NON-NLS-2$
    }

    static String getIfAddress(String inf) {
        return readFileFirstLine("/sys/class/net/" + inf + "/address", 24); //$NON-NLS-1$ //$NON-NLS-2$
    }

    static String getIfFlags(String inf) {
        return readFileFirstLine("/sys/class/net/" + inf + "/flags", 16); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean isNetInterfaceUp(String inf) {
        return readFileLong("/sys/class/net/" + inf + "/carrier", true) > 0; //$NON-NLS-1$ //$NON-NLS-2$
    }

    static String readFileFirstLine(String fileName, int bufSize) {
        return readFileFirstLine(new File(fileName), bufSize);
    }

    static String readFileFirstLine(File f, int bufSize) {
        if (f != null && f.exists() && f.isFile() && f.canRead()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)), bufSize);

                String line = reader.readLine();

                if (line != null) {
                    line = line.trim();
                }

                return line;
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                    }
                }
            }
        }

        return null;
    }

    static long readFileLong(String fname, boolean checkExist) {
        if (checkExist) {
            File f = new File(fname);

            if (!f.exists() || !f.isFile() || !f.canRead()) {
                return -1;
            }
        }

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(fname)), 64);

            String line = reader.readLine();

            if (line != null) {
                line = line.trim();

                return Long.parseLong(line);
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        return 0;
    }

    /**
     * read the first section in line as double, divide by blank space. If file none accessible,
     * returns -1; if file format error, returns 0.
     */
    static double readFileDoubleFirstSection(String fname, boolean checkExist) {
        if (checkExist) {
            File f = new File(fname);

            if (!f.exists() || !f.isFile() || !f.canRead()) {
                return -1;
            }
        }

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(fname)), 64);

            String line = reader.readLine();

            if (line != null) {
                line = line.trim();

                int idx = line.indexOf(' ');

                if (idx != -1) {
                    line = line.substring(0, idx).trim();
                }

                return Double.parseDouble(line);
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        return 0;
    }

    private static int getSDKVersion() {
        try {
            return Integer.parseInt(Build.VERSION.SDK);
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    static boolean launchPackage(Context ctx, String pkgName, int errorMsgResId, boolean checkRecent) {
        Intent it = new Intent("android.intent.action.MAIN"); //$NON-NLS-1$
        it.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> acts = null;

        try {
            acts = ctx.getPackageManager().queryIntentActivities(it, 0);
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }

        boolean started = false;

        if (acts != null) {
            for (int i = 0, size = acts.size(); i < size; i++) {
                ResolveInfo ri = acts.get(i);

                if (pkgName.equals(ri.activityInfo.packageName)) {
                    it.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);

                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    if (checkRecent) {
                        it.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                    } else {
                        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    }

                    try {
                        ctx.startActivity(it);

                        started = true;
                    } catch (Exception e) {
                        Log.e(Util.class.getName(), "Cannot start activity: " + pkgName, //$NON-NLS-1$
                                e);
                    }

                    break;
                }
            }

            if (!started && errorMsgResId != 0) {
                shortToast(ctx, errorMsgResId);
            }
        }

        return started;
    }

    static String locale2String(Locale lc) {
        if (lc != null && !TextUtils.isEmpty(lc.getLanguage())) {
            if (!TextUtils.isEmpty(lc.getCountry())) {
                return lc.getLanguage() + '_' + lc.getCountry();
            } else {
                return lc.getLanguage();
            }
        }
        return null;
    }

    static Locale string2Locale(String val) {
        if (!TextUtils.isEmpty(val)) {
            int idx = val.indexOf('_');

            if (idx != -1) {
                return new Locale(val.substring(0, idx), val.substring(idx + 1));
            } else {
                return new Locale(val);
            }
        }
        return null;
    }

    static List<String> getStringList4JsonString(String jsonString) {
        List<String> tags = null;

        if (jsonString != null) {
            try {
                JSONArray ja = new JSONArray(jsonString);

                tags = new ArrayList<String>();

                for (int i = 0, size = ja.length(); i < size; i++) {
                    tags.add(ja.getString(i));
                }
            } catch (JSONException e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return tags;
    }

    static String getJsonString4Collection(Collection<String> data) {
        try {
            JSONStringer json = new JSONStringer();

            json.array();

            for (String s : data) {
                json.value(s);
            }

            json.endArray();

            return json.toString();
        } catch (JSONException e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }

        return null;
    }

    static void refreshTagVisibility(Context ctx, Intent it, Preference preference,
                                     String[] defaultTags) {
        List<String> tags = getStringList4JsonString(it.getStringExtra(PREF_KEY_CUSTOM_TAGS));
        List<String> visTags = getStringList4JsonString(it.getStringExtra(PREF_KEY_TAG_VISIBILITY));

        int len = 0;

        if (visTags != null && visTags.size() > 0) {
            Set<String> visSet = new HashSet<String>(visTags);

            if (defaultTags != null) {
                for (String tag : defaultTags) {
                    if (visSet.contains(tag)) {
                        len++;
                    }
                }
            }

            if (tags != null) {
                for (String tag : tags) {
                    if (visSet.contains(tag)) {
                        len++;
                    }
                }
            }
        }

        if (len == 0 && defaultTags != null) {
            len = defaultTags.length;
        }

        preference.setSummary(ResUtil.getString(ctx, R.string.sum_visible_tags, len));
    }

    static void refreshCustomTags(Context ctx, Intent it, Preference preference) {
        List<String> tags = getStringList4JsonString(it.getStringExtra(PREF_KEY_CUSTOM_TAGS));

        int len = tags == null ? 0 : tags.size();

        preference.setSummary(ResUtil.getString(ctx, R.string.sum_cust_tags, len));
    }

    static void editTagVisibility(final Context ctx, final Intent it,
                                  final Preference prefTagVisibility, final String[] defaultTags) {
        List<String> tags = getStringList4JsonString(it.getStringExtra(PREF_KEY_CUSTOM_TAGS));

        final List<String> list = new ArrayList<String>();

        if (tags != null) {
            list.addAll(tags);
        }

        Collections.sort(list, new StringComparator());

        if (defaultTags != null) {
            for (String tag : defaultTags) {
                list.add(0, tag);
            }
        }

        final boolean[] states = new boolean[list.size()];

        // init visibility
        String visData = it.getStringExtra(PREF_KEY_TAG_VISIBILITY);
        int vis = 0;

        if (visData != null) {
            try {
                JSONArray ja = new JSONArray(visData);

                for (int i = 0, size = ja.length(); i < size; i++) {
                    int idx = list.indexOf(ja.getString(i));

                    if (idx != -1) {
                        states[idx] = true;
                        vis++;
                    }
                }
            } catch (JSONException e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (vis == 0 && defaultTags != null) {
            for (int i = 0; i < defaultTags.length; i++) {
                states[i] = true;
            }
        }

        OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener() {

            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                states[which] = isChecked;
            }
        };

        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                ArrayList<String> nlist = new ArrayList<String>();

                for (int i = 0, size = list.size(); i < size; i++) {
                    if (states[i]) {
                        nlist.add(list.get(i));
                    }
                }

                if (nlist.size() == 0) {
                    it.removeExtra(PREF_KEY_TAG_VISIBILITY);
                } else {
                    it.putExtra(PREF_KEY_TAG_VISIBILITY, getJsonString4Collection(nlist));
                }

                refreshTagVisibility(ctx, it, prefTagVisibility, defaultTags);
            }
        };

        Util.newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.toggle_tags))
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, null)
                .setMultiChoiceItems(list.toArray(new String[list.size()]), states, multiListener).create()
                .show();

    }

    static void editCustomTags(final Context ctx, final Intent it, final Preference prefCustomTags,
                               final Preference prefTagVisibility, final String[] defaultTags) {
        editCustomTags(ctx, it, prefCustomTags, prefTagVisibility, defaultTags, null);
    }

    static void editCustomTags(final Context ctx, final Intent it, final Preference prefCustomTags,
                               final Preference prefTagVisibility, final String[] defaultTags,
                               OnClickListener addTagHandler) {
        List<String> tags = getStringList4JsonString(it.getStringExtra(PREF_KEY_CUSTOM_TAGS));

        final boolean[] states = tags == null ? null : new boolean[tags.size()];

        OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener() {

            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                states[which] = isChecked;
            }
        };

        final List<String> list = tags == null ? new ArrayList<String>() : new ArrayList<String>(tags);

        Collections.sort(list, new StringComparator());

        OnClickListener addListener = addTagHandler != null ? addTagHandler : new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                final EditText txt = new EditText(ctx);

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        String tag = txt.getText().toString();

                        tag = normalizeCustomTag(tag, ctx, list);

                        if (tag != null) {
                            addCustomTag(tag, ctx, list, it, prefCustomTags, prefTagVisibility, defaultTags);
                        }
                    }
                };

                new AlertDialog.Builder(ctx).setTitle(ResUtil.getString(ctx, R.string.new_tag))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).setView(txt).create().show();
            }
        };

        OnClickListener removeListener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                ArrayList<String> nlist = new ArrayList<String>();

                ArrayList<String> toRemove = new ArrayList<String>();

                for (int i = 0, size = list.size(); i < size; i++) {
                    if (!states[i]) {
                        nlist.add(list.get(i));
                    } else {
                        toRemove.add(list.get(i));
                    }
                }

                if (list.size() == nlist.size()) {
                    shortToast(ctx, R.string.no_item_remove);
                } else {
                    if (nlist.size() == 0) {
                        it.removeExtra(PREF_KEY_CUSTOM_TAGS);
                    } else {
                        it.putExtra(PREF_KEY_CUSTOM_TAGS, getJsonString4Collection(nlist));
                    }

                    // mark removed tags
                    ArrayList<String> removedTags = it.getStringArrayListExtra(KEY_REMOVED_TAGS);

                    if (removedTags == null) {
                        removedTags = new ArrayList<String>();
                    }

                    for (String tg : toRemove) {
                        if (!removedTags.contains(tg)) {
                            removedTags.add(tg);
                        }
                    }

                    it.putStringArrayListExtra(KEY_REMOVED_TAGS, removedTags);

                    refreshTagVisibility(ctx, it, prefTagVisibility, defaultTags);
                    refreshCustomTags(ctx, it, prefCustomTags);
                }
            }
        };

        Util.newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.manage_tags))
                .setPositiveButton(ResUtil.getString(ctx, R.string.add), addListener)
                .setNeutralButton(ResUtil.getString(ctx, R.string.remove), removeListener)
                .setMultiChoiceItems(list.toArray(new String[list.size()]), states, multiListener).create()
                .show();
    }

    static String normalizeCustomTag(String tag, Context ctx, List<String> existingTags) {
        if (tag != null) {
            tag = tag.trim();

            if (tag.length() == 0) {
                tag = null;
            }
        }

        if (tag == null) {
            shortToast(ctx, R.string.error_empty_tag);
        } else if (existingTags.contains(tag)) {
            tag = null;
            shortToast(ctx, R.string.error_dup_tag);
        }
        return tag;
    }

    static void addCustomTag(String tag, Context ctx, List<String> tagList, Intent it,
                             Preference prefCustomTags, Preference prefTagVisibility,
                             String[] defaultTags) {

        // check dup with default tags
        boolean dup = false;

        if (defaultTags != null) {
            for (String dt : defaultTags) {
                if (dt.equals(tag)) {
                    dup = true;
                    break;
                }
            }
        }

        if (dup) {
            shortToast(ctx, R.string.error_dup_tag);
        } else {
            tagList.add(tag);

            it.putExtra(PREF_KEY_CUSTOM_TAGS, getJsonString4Collection(tagList));

            // update visibility
            List<String> visTags = getStringList4JsonString(it.getStringExtra(PREF_KEY_TAG_VISIBILITY));

            if (visTags == null) {
                visTags = new ArrayList<String>();
            }

            if (!visTags.contains(tag)) {
                visTags.add(tag);
            }

            it.putExtra(PREF_KEY_TAG_VISIBILITY, getJsonString4Collection(visTags));

            refreshTagVisibility(ctx, it, prefTagVisibility, defaultTags);
            refreshCustomTags(ctx, it, prefCustomTags);
        }
    }

    static Set<String> getVisibleTags(SharedPreferences prefStore, List<String> tags,
                                      String... defaultTags) {
        Set<String> result = new HashSet<String>();

        List<String> vis = getStringList4JsonString(getStringOption(prefStore, PREF_KEY_TAG_VISIBILITY, null));

        if (vis != null && vis.size() > 0) {
            Set<String> visSet = new HashSet<String>(vis);

            if (defaultTags != null) {
                for (String tag : defaultTags) {
                    if (visSet.contains(tag)) {
                        result.add(tag);
                    }
                }
            }

            if (tags != null) {
                for (String tag : tags) {
                    if (visSet.contains(tag)) {
                        result.add(tag);
                    }
                }
            }
        }

        if (result.size() == 0 && defaultTags != null) {
            for (String tag : defaultTags) {
                result.add(tag);
            }
        }

        return result;
    }

    static Set<String> getCustomTagLinks(SharedPreferences prefStore, String tag) {
        Set<String> links = new HashSet<String>();

        List<String> pkgs = getStringList4JsonString(getStringOption(prefStore, PREF_KEY_TAG_LINKS_PREFIX + tag, null));
        if (pkgs != null) {
            links.addAll(pkgs);
        }

        return links;
    }

    static void updateCustomTagLinks(Context ctx, SharedPreferences prefStore, String tag,
                                     Set<String> links) {
        if (tag == null) {
            return;
        }

        String data = null;

        if (links != null && links.size() > 0) {
            data = getJsonString4Collection(links);
        }

        setStringOption(ctx, prefStore, PREF_KEY_TAG_LINKS_PREFIX + tag, data);
    }

    static void exportPreferences(Context ctx, Handler handler, String targetDir) {
        Writer wrt = null;

        try {
            JSONObject json = new JSONObject();

            json.put(PROP_VERSION, 1);
            json.put(PROP_TIMESTAMP, System.currentTimeMillis());

            boolean succ = true;

            succ &= exportAllPreferences(handler, json, SYSINFO_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE),
                            PREF_KEY_USER_NAME, PREF_KEY_USER_KEY);

            succ &= exportAllPreferences(handler, json, APPLICATION_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= exportAllPreferences(handler, json, PROCESS_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= exportAllPreferences(handler, json, NETSTATE_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(NETSTATE_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= exportAllPreferences(handler, json, RESTORE_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= exportAllPreferences(handler, json, LOG_VIEWER_STORE_NAME,
                            ctx.getSharedPreferences(LOG_VIEWER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= exportAllPreferences(handler, json, PROPERTIES_VIEWER_STORE_NAME,
                            ctx.getSharedPreferences(PROPERTIES_VIEWER_STORE_NAME, Context.MODE_PRIVATE));

            File dir = new File(targetDir);

            if (!dir.exists()) {
                boolean suc = dir.mkdirs();

                if (!suc) {
                    String error =
                            ResUtil.getString(ctx, R.string.error_create_folder, dir.getAbsolutePath());

                    if (handler != null) {
                        handler.sendMessage(handler.obtainMessage(MSG_TOAST, error));
                    }

                    Log.e(Util.class.getName(), error);

                    return;
                }
            } else if (!dir.isDirectory()) {
                String error = ResUtil.getString(ctx, R.string.error_create_folder, dir.getAbsolutePath());

                if (handler != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST, error));
                }

                Log.e(Util.class.getName(), error);

                return;
            }

            File prefFile = new File(dir, PREF_FILE_NAME);

            wrt = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(prefFile), "UTF-8"), //$NON-NLS-1$
                    1024);

            wrt.write(json.toString());

            wrt.flush();

            if (handler != null) {
                if (succ) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                            ResUtil.getString(ctx, R.string.export_pref_suc)));
                } else {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                            ResUtil.getString(ctx, R.string.export_pref_suc_err)));
                }
            }
        } catch (Exception e) {
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(MSG_TOAST, e.getLocalizedMessage()));
            }
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (wrt != null) {
                try {
                    wrt.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    private static boolean exportAllPreferences(Handler handler, JSONObject parent, String name,
                                                SharedPreferences sp, String... excludes) {
        if (sp == null) {
            return true;
        }

        Map<String, ?> allVals = sp.getAll();

        if (allVals == null || allVals.isEmpty()) {
            return true;
        }

        boolean succ = true;

        JSONObject sub = new JSONObject();

        LOOP:
        for (Entry<String, ?> ent : allVals.entrySet()) {
            String key = ent.getKey();

            if (key == null) {
                continue;
            }

            if (excludes != null) {
                for (int i = 0; i < excludes.length; i++) {
                    if (key.equals(excludes[i])) {
                        continue LOOP;
                    }
                }
            }

            try {
                sub.putOpt(key, ent.getValue());
            } catch (JSONException e) {
                succ = false;

                if (handler != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST, e.getLocalizedMessage()));
                }

                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (sub.length() > 0) {
            try {
                parent.put(name, sub);
            } catch (JSONException e) {
                succ = false;

                if (handler != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST, e.getLocalizedMessage()));
                }

                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return succ;
    }

    static void importPreferences(Context ctx, Handler handler, String sourceDir) {
        BufferedReader reader = null;

        try {
            File prefFile = new File(sourceDir, PREF_FILE_NAME);

            if (!prefFile.exists() || !prefFile.isFile() || !prefFile.canRead()) {
                String error = ResUtil.getString(ctx, R.string.error_read_file, prefFile.getAbsolutePath());

                if (handler != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST, error));
                }

                Log.e(Util.class.getName(), error);

                return;
            }

            reader = new BufferedReader(new InputStreamReader(new FileInputStream(prefFile), "UTF-8"), //$NON-NLS-1$
                    1024);

            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            JSONObject json = new JSONObject(sb.toString());

            // json.put( PROP_VERSION, 1 );
            // json.put( PROP_TIMESTAMP, System.currentTimeMillis( ) );

            boolean succ = true;

            succ &= importAllPreferences(ctx, handler, json, SYSINFO_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE),
                            PREF_KEY_USER_NAME, PREF_KEY_USER_KEY);

            succ &= importAllPreferences(ctx, handler, json, APPLICATION_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= importAllPreferences(ctx, handler, json, PROCESS_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= importAllPreferences(ctx, handler, json, NETSTATE_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(NETSTATE_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= importAllPreferences(ctx, handler, json, RESTORE_MANAGER_STORE_NAME,
                            ctx.getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= importAllPreferences(ctx, handler, json, LOG_VIEWER_STORE_NAME,
                            ctx.getSharedPreferences(LOG_VIEWER_STORE_NAME, Context.MODE_PRIVATE));

            succ &= importAllPreferences(ctx, handler, json, PROPERTIES_VIEWER_STORE_NAME,
                            ctx.getSharedPreferences(PROPERTIES_VIEWER_STORE_NAME, Context.MODE_PRIVATE));

            if (handler != null) {
                if (succ) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                            ResUtil.getString(ctx, R.string.import_pref_suc)));
                } else {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                            ResUtil.getString(ctx, R.string.import_pref_suc_err)));
                }
            }
        } catch (Exception e) {
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(MSG_TOAST, e.getLocalizedMessage()));
            }

            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    private static boolean importAllPreferences(Context ctx, Handler handler, JSONObject parent,
                                                String name, SharedPreferences sp, String... excludes) {
        if (sp == null) {
            return true;
        }

        Object obj = parent.opt(name);

        if (!(obj instanceof JSONObject)) {
            return true;
        }

        JSONObject sub = (JSONObject) obj;
        JSONArray names = sub.names();

        if (names == null || names.length() == 0) {
            return true;
        }

        boolean succ = true;

        Editor edt = sp.edit();

        LOOP:
        for (int i = 0, size = names.length(); i < size; i++) {
            String key = null;

            try {
                key = names.getString(i);
            } catch (JSONException e) {
                succ = false;

                if (handler != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_TOAST, e.getLocalizedMessage()));
                }

                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }

            if (key == null) {
                continue;
            }

            if (excludes != null) {
                for (int j = 0; j < excludes.length; j++) {
                    if (key.equals(excludes[j])) {
                        continue LOOP;
                    }
                }
            }

            Object val = sub.opt(key);

            if (val == null) {
                continue;
            }

            if (val instanceof Boolean) {
                edt.putBoolean(key, (Boolean) val);
            } else if (val instanceof Integer) {
                edt.putInt(key, (Integer) val);
            } else if (val instanceof String) {
                edt.putString(key, (String) val);
            } else if (val instanceof Float) {
                edt.putFloat(key, (Float) val);
            } else if (val instanceof Long) {
                edt.putLong(key, (Long) val);
            }
        }

        edt.commit();

        requestBackup(ctx);

        return succ;
    }

    static Field getField(Class<?> clz, String name) {
        return getField(0, false, clz, name);
    }

    static Field getField(int minSdk, boolean forceAccess, Class<?> clz, String name) {
        if (minSdk >= 0 && Util.SDK_VER < minSdk) {
            return null;
        }

        try {
            Field fd = clz.getDeclaredField(name);
            if (forceAccess) {
                fd.setAccessible(true);
            }
            return fd;
        } catch (Exception e) {
            Log.d(Util.class.getName(), "Current SDK version does not support '" + name + "' property."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return null;
    }

    static Method getMethod(Class<?> clz, String name, Class<?>... argTypes) {
        return getMethod(0, false, clz, name, argTypes);
    }

    static Method getMethod(int minSdk, boolean forceAccess, Class<?> clz, String name, Class<?>... argTypes) {
        if (minSdk >= 0 && Util.SDK_VER < minSdk) {
            return null;
        }

        try {
            Method mtd = clz.getDeclaredMethod(name, argTypes);
            if (forceAccess) {
                mtd.setAccessible(true);
            }
            return mtd;
        } catch (Exception e) {
            Log.d(Util.class.getName(), "Current SDK version does not support '" + name + "' method."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return null;
    }

    static Object safeInvoke(Method mtd, Object host, Object... args) {
        if (mtd != null) {
            try {
                return mtd.invoke(host, args);
            } catch (Exception e) {
                Log.d(Util.class.getName(), "Error invoke metehod '" + mtd.getName() + "' with args: " + args); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        return null;
    }

    static void safeSet(Field fd, Object host, Object value) {
        if (fd != null) {
            try {
                fd.set(host, value);
            } catch (Exception e) {
                Log.d(Util.class.getName(), "Error set field '" + fd.getName() + "' with value: " + value); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    static Object safeGet(Field fd, Object host) {
        if (fd != null) {
            try {
                return fd.get(host);
            } catch (Exception e) {
                Log.d(Util.class.getName(), "Error get field value: " + fd.getName());
            }
        }
        return null;
    }

    static Object safeGet(int minSdk, boolean forceAccess, Class<?> clz, String name, Object host) {
        Field fd = getField(minSdk, forceAccess, clz, name);
        return safeGet(fd, host);
    }

    static Object safeGetStatic(int minSdk, boolean forceAccess, Class<?> clz, String name) {
        Field fd = getField(minSdk, forceAccess, clz, name);
        return safeGet(fd, null);
    }

    static synchronized boolean adjustAsyncTaskPoolSize() {
        try {
            Field fd = AsyncTask.class.getField("THREAD_POOL_EXECUTOR"); //$NON-NLS-1$
            Object obj = fd.get(null);

            if (obj instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor executor = (ThreadPoolExecutor) obj;

                int max = executor.getMaximumPoolSize();

                // only increase if < 256
                if (max < 256) {
                    // increase by 1.5x
                    max = max * 3 / 2;

                    executor.setMaximumPoolSize(max);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
        return false;
    }

    static void printAllSystemServices() {
        try {
            String[] svcs =
                    (String[]) Class.forName("android.os.ServiceManager").getMethod("listServices")
                            .invoke(null);
            Log.e(Util.class.getName(), Arrays.toString(svcs));

        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    static void createTextHeader(Context ctx, StringBuffer sb, String title) {
        sb.append(title).append("\n\n"); //$NON-NLS-1$

        sb.append(ResUtil.getString(ctx, R.string.collector_head,
                ResUtil.getString(ctx, ProUtil.getId_appName()),
                getVersionName(ctx.getPackageManager(), ctx.getPackageName())));

        LinkedHashMap<String, String> headerProps = PropertiesViewer.getHeaderProps(ctx);

        for (Entry<String, String> entry : headerProps.entrySet()) {
            sb.append(entry.getKey()).append(": ") //$NON-NLS-1$
                    .append(entry.getValue()).append('\n');
        }

        sb.append('\n');

        try {
            readRawText(sb, new FileInputStream(F_VERSION), 1024);

            sb.append('\n');
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    static void createHtmlHeader(Context ctx, StringBuffer sb, String title) {
        sb.append("<html><head><title>") //$NON-NLS-1$
                .append(title)
                .append("</title><meta http-equiv=\"Content-type\" content=\"text/html;charset=UTF-8\"/></head>\n") //$NON-NLS-1$
                .append("<body bgcolor=FFFFFF><font face=\"Verdana\" color=\"#000000\">\n") //$NON-NLS-1$
                .append("<table border=0 width=\"100%\" cellspacing=\"2\" cellpadding=\"2\">\n") //$NON-NLS-1$
                .append("<tr align=\"left\">") //$NON-NLS-1$
                .append("<td colspan=5>") //$NON-NLS-1$
                .append("<table border=0 width=\"100%\" cellspacing=\"2\" cellpadding=\"2\">") //$NON-NLS-1$
                .append("<tr><td width=60>") //$NON-NLS-1$
                .append("<a href=\"http://code.google.com/p/qsysinfo/\">") //$NON-NLS-1$
                .append("<img src=\"http://code.google.com/p/qsysinfo/logo?logo_id=1261652286\" border=0></a>") //$NON-NLS-1$
                .append("</td><td valign=\"bottom\">") //$NON-NLS-1$
                .append("<h3>") //$NON-NLS-1$
                .append(title).append("</h3></td></tr></table></td></tr>\n"); //$NON-NLS-1$

        sb.append("<tr align=\"left\"><td colspan=5><font color=\"#a0a0a0\"><small>"); //$NON-NLS-1$
        sb.append(escapeHtml(ResUtil.getString(ctx, R.string.collector_head,
                ResUtil.getString(ctx, ProUtil.getId_appName()),
                getVersionName(ctx.getPackageManager(), ctx.getPackageName()))));
        sb.append("</small></font></td></tr>\n"); //$NON-NLS-1$

        sb.append(openHeaderRow).append(ResUtil.getString(ctx, R.string.device_info))
                .append(closeHeaderRow);

        LinkedHashMap<String, String> headerProps = PropertiesViewer.getHeaderProps(ctx);

        for (Entry<String, String> entry : headerProps.entrySet()) {
            sb.append(openRow).append(entry.getKey()).append(nextColumn4)
                    .append(escapeHtml(entry.getValue())).append(closeRow);
        }

        sb.append(emptyRow);

        sb.append(openHeaderRow).append(ResUtil.getString(ctx, R.string.sys_version)).append(closeHeaderRow);

        try {
            readRawHTML(sb, new FileInputStream(F_VERSION), 1024);
            sb.append(emptyRow);
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    static void readRawText(StringBuffer sb, InputStream input, int bufSize) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input), bufSize);

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    static List<String> readRawTextLines(InputStream input, int bufSize) {
        BufferedReader reader = null;
        List<String> lines = new ArrayList<String>();
        try {
            reader = new BufferedReader(new InputStreamReader(input), bufSize);

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
        return lines.size() == 0 ? null : lines;
    }

    static void readRawHTML(StringBuffer sb, InputStream input, int bufSize) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input), bufSize);

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(openFullRow).append(escapeHtml(line)).append(closeRow);
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    static String getVersionName(PackageManager pm, String pkgName) {
        String ver = null;

        try {
            ver = pm.getPackageInfo(pkgName, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }

        if (ver == null) {
            ver = ""; //$NON-NLS-1$
        }

        return ver;
    }

    static String escapeCsv(String str) {
        if (TextUtils.isEmpty(str) || containsNone(str, CSV_SEARCH_CHARS)) {
            return str;
        }

        StringBuffer sb = new StringBuffer();

        sb.append('"');
        for (int i = 0, size = str.length(); i < size; i++) {
            char c = str.charAt(i);
            if (c == '"') {
                sb.append('"'); // escape double quote
            }
            sb.append(c);
        }
        sb.append('"');

        return sb.toString();
    }

    static String escapeHtml(String str) {
        if (TextUtils.isEmpty(str) || containsNone(str, HTML_SEARCH_CHARS)) {
            return str;
        }

        str = TextUtils.htmlEncode(str);

        if (str.indexOf('\n') == -1) {
            return str;
        }

        StringBuffer sb = new StringBuffer();
        char c;
        for (int i = 0, size = str.length(); i < size; i++) {
            c = str.charAt(i);

            if (c == '\n') {
                sb.append("<br>"); //$NON-NLS-1$
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static boolean containsNone(String str, char[] invalidChars) {
        int strSize = str.length();
        int validSize = invalidChars.length;

        for (int i = 0; i < strSize; i++) {
            char ch = str.charAt(i);
            for (int j = 0; j < validSize; j++) {
                if (invalidChars[j] == ch) {
                    return false;
                }
            }
        }

        return true;
    }

    static long[] getStorageState(File path) {
        if (path != null) {
            try {
                StatFs stat = new StatFs(path.getAbsolutePath());
                long blockSize = stat.getBlockSize();

                long[] result = new long[2];
                result[0] = stat.getBlockCount() * blockSize;
                result[1] = stat.getAvailableBlocks() * blockSize;

                return result;
            } catch (Exception e) {
                Log.e(Util.class.getName(), "Cannot access path: " //$NON-NLS-1$
                        + path.getAbsolutePath(), e);
            }
        }

        return null;
    }

    /**
     * @return [total, idle, free]
     */
    static long[] getMemState(Context ctx) {
        BufferedReader reader = null;

        try {
            reader =
                    new BufferedReader(new InputStreamReader(new FileInputStream(new File(F_MEM_INFO))), 1024);

            String line;
            String totalMsg = null;
            String freeMsg = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal")) //$NON-NLS-1$
                {
                    totalMsg = line;
                } else if (line.startsWith("MemFree")) //$NON-NLS-1$
                {
                    freeMsg = line;
                }

                if (totalMsg != null && freeMsg != null) {
                    break;
                }
            }

            long[] mem = new long[3];

            mem[0] = extractMemCount(totalMsg);
            mem[1] = extractMemCount(freeMsg);

            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            mem[2] = mi.availMem;

            return mem;
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ie) {
                    Log.e(Util.class.getName(), ie.getLocalizedMessage(), ie);
                }
            }
        }

        return null;
    }

    static long extractMemCount(String line) {
        if (line != null) {
            int idx = line.indexOf(':');

            if (idx != -1) {
                line = line.substring(idx + 1).trim();

                idx = line.lastIndexOf(' ');

                if (idx != -1) {
                    String unit = line.substring(idx + 1);

                    try {
                        long size = Long.parseLong(line.substring(0, idx).trim());

                        if ("kb".equalsIgnoreCase(unit)) //$NON-NLS-1$
                        {
                            size *= 1024;
                        } else if ("mb".equalsIgnoreCase(unit)) //$NON-NLS-1$
                        {
                            size *= 1024 * 1024;
                        } else if ("gb".equalsIgnoreCase(unit)) //$NON-NLS-1$
                        {
                            size *= 1024 * 1024 * 1024;
                        } else {
                            Log.w(Util.class.getName(), "Unexpected mem unit format: " + line); //$NON-NLS-1$
                        }

                        return size;
                    } catch (Exception e) {
                        Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
                    }
                } else {
                    Log.e(Util.class.getName(), "Unexpected mem value format: " + line); //$NON-NLS-1$
                }
            } else {
                Log.e(Util.class.getName(), "Unexpected mem format: " + line); //$NON-NLS-1$
            }
        }

        return -1;
    }

    static String getNetAddressInfo() {
        try {
            StringBuffer sb = new StringBuffer();

            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                        .hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String addr = inetAddress.getHostAddress();

                        if (!TextUtils.isEmpty(addr)) {
                            if (sb.length() == 0) {
                                sb.append(addr);
                            } else {
                                sb.append(", ").append(addr); //$NON-NLS-1$
                            }
                        }
                    }
                }
            }

            String netAddress = sb.toString();

            if (!TextUtils.isEmpty(netAddress)) {
                return netAddress;
            }
        } catch (Exception e) {
            Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
        }

        return null;
    }

    static void finishStub(Context ctx, int pid) {
        if (pid != 0) {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();

            if (procs != null) {
                for (ActivityManager.RunningAppProcessInfo rap : procs) {
                    if (rap.pid == pid) {
                        // ensure we kill the correct process
                        if ((ctx.getPackageName() + ":remote").equals(rap.processName)) //$NON-NLS-1$
                        {
                            Process.killProcess(pid);
                        }

                        break;
                    }
                }
            }
        }
    }

    static boolean isWearable(Context ctx) {
        if (SDK_VER >= 5 && mdHasSystemFeature != null) {
            try {
                Object result = mdHasSystemFeature.invoke(ctx.getPackageManager(), "android.hardware.type.watch");
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
        return false;
    }

    static boolean isDebugPackage(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                    PackageManager.GET_SIGNATURES);

            if (pi != null && pi.signatures != null && pi.signatures.length > 0) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509"); //$NON-NLS-1$

                Certificate cert = cf.generateCertificate(new ByteArrayInputStream(pi.signatures[0].toByteArray()));

                if (cert instanceof X509Certificate) {
                    X509Certificate xcert = (X509Certificate) cert;

                    String issuer = xcert.getIssuerDN().getName();

                    if ("CN=Android Debug,O=Android,C=US".equals(issuer)) //$NON-NLS-1$
                    {
                        // debug version
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
        }
        return false;
    }

    static boolean isSystemOpEnabled(Context ctx, String opStr) {
        Object appOpsManager = ctx.getSystemService("appops"/*Context.APP_OPS_SERVICE*/);
        if (appOpsManager != null) {
            try {
                Class opsClz = Class.forName("android.app.AppOpsManager");
                Method mtdCheckOpNoThrow = opsClz.getDeclaredMethod("checkOpNoThrow", String.class, int.class, String.class);

                ApplicationInfo applicationInfo = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), 0);
                Object result = mtdCheckOpNoThrow.invoke(appOpsManager, opStr, applicationInfo.uid, applicationInfo.packageName);
                if (result instanceof Integer) {
                    return ((Integer) result).intValue() == 0; //AppOpsManager.MODE_ALLOWED;
                }
            } catch (Exception e) {
                Log.e(Util.class.getName(), e.getLocalizedMessage(), e);
            }
        }
        return false;
    }

    static boolean isUsageStatsEnabled(Context ctx) {
        return isSystemOpEnabled(ctx, "android:get_usage_stats"/*AppOpsManager.OPSTR_GET_USAGE_STATS*/);
    }

    static String joinString(String[] values, String delimiter) {
        if (values == null || values.length == 0) {
            return "";
        }
        if (values.length == 1) {
            return values[0];
        }
        StringBuilder sb = new StringBuilder();
        sb.append(values[0]);
        for (int i = 1; i < values.length; i++) {
            sb.append(delimiter).append(values[i]);
        }
        return sb.toString();
    }

    /**
     * EditorState
     */
    static final class EditorState {

        Context ctx;
        String storeName;
        Editor et;
        boolean changed;
    }

    interface UtilProxy {

        Class<?> getIntentProxyClz(Class<?> clz);

        void sendContent(Context ctx, String email, String subject, String content, boolean compressed);

        AlertDialogBuilder newAlertDialogBuilder(Context ctx);
    }

    interface AlertDialogBuilder {

        AlertDialogBuilder setTitle(int titleId);

        AlertDialogBuilder setTitle(CharSequence title);

        AlertDialogBuilder setMessage(int messageId);

        AlertDialogBuilder setMessage(CharSequence message);

        AlertDialogBuilder setIcon(int iconId);

        AlertDialogBuilder setIcon(Drawable icon);

        AlertDialogBuilder setPositiveButton(int textId, OnClickListener listener);

        AlertDialogBuilder setPositiveButton(CharSequence text, OnClickListener listener);

        AlertDialogBuilder setNegativeButton(int textId, OnClickListener listener);

        AlertDialogBuilder setNegativeButton(CharSequence text, OnClickListener listener);

        AlertDialogBuilder setNeutralButton(int textId, OnClickListener listener);

        AlertDialogBuilder setNeutralButton(CharSequence text, OnClickListener listener);

        AlertDialogBuilder setItems(CharSequence[] items, OnClickListener listener);

        AlertDialogBuilder setAdapter(ListAdapter adapter, OnClickListener listener);

        AlertDialogBuilder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems,
                                               OnMultiChoiceClickListener listener);

        AlertDialogBuilder setSingleChoiceItems(CharSequence[] items, int checkedItem, OnClickListener listener);

        AlertDialogBuilder setView(View view);

        AlertDialogBuilder setInverseBackgroundForced(boolean useInverseBackground);

        Dialog create();
    }

    static final class AlertDialogBuilderImpl implements AlertDialogBuilder {

        private AlertDialog.Builder builder;

        AlertDialogBuilderImpl(Context ctx) {
            builder = new AlertDialog.Builder(ctx);
        }

        @Override
        public AlertDialogBuilder setTitle(int titleId) {
            builder.setTitle(titleId);
            return this;
        }

        @Override
        public AlertDialogBuilder setTitle(CharSequence title) {
            builder.setTitle(title);
            return this;
        }

        @Override
        public AlertDialogBuilder setMessage(int messageId) {
            builder.setMessage(messageId);
            return this;
        }

        @Override
        public AlertDialogBuilder setMessage(CharSequence message) {
            builder.setMessage(message);
            return this;
        }

        @Override
        public AlertDialogBuilder setIcon(int iconId) {
            builder.setIcon(iconId);
            return this;
        }

        @Override
        public AlertDialogBuilder setIcon(Drawable icon) {
            builder.setIcon(icon);
            return this;
        }

        @Override
        public AlertDialogBuilder setPositiveButton(int textId, OnClickListener listener) {
            builder.setPositiveButton(textId, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setPositiveButton(CharSequence text, OnClickListener listener) {
            builder.setPositiveButton(text, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setNegativeButton(int textId, OnClickListener listener) {
            builder.setNegativeButton(textId, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setNegativeButton(CharSequence text, OnClickListener listener) {
            builder.setNegativeButton(text, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setNeutralButton(int textId, OnClickListener listener) {
            builder.setNeutralButton(textId, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setNeutralButton(CharSequence text, OnClickListener listener) {
            builder.setNeutralButton(text, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setItems(CharSequence[] items, OnClickListener listener) {
            builder.setItems(items, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setAdapter(ListAdapter adapter, OnClickListener listener) {
            builder.setAdapter(adapter, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems,
                                                      OnMultiChoiceClickListener listener) {
            builder.setMultiChoiceItems(items, checkedItems, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setSingleChoiceItems(CharSequence[] items, int checkedItem, OnClickListener listener) {
            builder.setSingleChoiceItems(items, checkedItem, listener);
            return this;
        }

        @Override
        public AlertDialogBuilder setView(View view) {
            builder.setView(view);
            return this;
        }

        @Override
        public AlertDialogBuilder setInverseBackgroundForced(boolean useInverseBackground) {
            builder.setInverseBackgroundForced(useInverseBackground);
            return this;
        }

        @Override
        public Dialog create() {
            return builder.create();
        }
    }

}
