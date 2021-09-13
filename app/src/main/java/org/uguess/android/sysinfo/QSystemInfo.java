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
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TabHost;
import android.widget.TextView;

import org.uguess.android.sysinfo.ResUtil.ResourceListener;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;

/**
 * QSystemInfo
 */
public final class QSystemInfo extends TabActivity {

    public static final String EXTRA_TARGET_TAB = "TargetTab"; //$NON-NLS-1$

    private static final String PREF_KEY_LAST_ACTIVE = "last_active_tab"; //$NON-NLS-1$

    private GestureDetector gestureDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Util.hookExceptionHandler(getApplicationContext());

        ResUtil.checkResources(this, new ResourceListener() {

            @Override
            public void resourceChanged() {
                OnClickListener listener = new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();

                        Intent it =
                                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                        .setClass(QSystemInfo.this, QSystemInfo.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

                        PropertiesViewer.overridePendingTransition(QSystemInfo.this, 0, 0);

                        startActivity(it);
                    }
                };
                new AlertDialog.Builder(QSystemInfo.this)
                        .setTitle(ResUtil.getString(QSystemInfo.this, R.string.prompt))
                        .setMessage(ResUtil.getString(QSystemInfo.this, R.string.res_change_msg))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).create().show();
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                try {
                    float vThreshold = Math.abs(e1.getY() - e2.getY());
                    if (vThreshold > 250 || vThreshold < 80) {
                        return false;
                    }

                    TabHost th = getTabHost();
                    int targetTab;

                    if (e1.getX() - e2.getX() > 120 && Math.abs(velocityX) > 200) {
                        // left swipe
                        int tab = th.getCurrentTab();

                        if (tab < 3) {
                            targetTab = tab + 1;
                        } else {
                            targetTab = 0;
                        }

                        th.setCurrentTab(targetTab);

                        th.getCurrentView().startAnimation(
                                AnimationUtils.loadAnimation(QSystemInfo.this, R.anim.slide_in_right));

                        return true;
                    } else if (e2.getX() - e1.getX() > 120 && Math.abs(velocityX) > 200) {
                        // right swipe
                        int tab = th.getCurrentTab();

                        if (tab > 0) {
                            targetTab = tab - 1;
                        } else {
                            targetTab = 3;
                        }

                        th.setCurrentTab(targetTab);

                        th.getCurrentView().startAnimation(
                                AnimationUtils.loadAnimation(QSystemInfo.this, android.R.anim.slide_in_left));

                        return true;
                    }
                } catch (Exception e) {
                    // ignore
                }

                return false;
            }
        });

        TabHost th = getTabHost();

        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setClass(this, SysInfoManager.class);
        th.addTab(th
                .newTabSpec(SysInfoManager.class.getName())
                .setContent(it)
                .setIndicator(ResUtil.getString(this, R.string.tab_info),
                        getResources().getDrawable(R.drawable.info)));

        it = new Intent(Intent.ACTION_VIEW);
        it.setClass(this, ApplicationManager.class);
        th.addTab(th
                .newTabSpec(ApplicationManager.class.getName())
                .setContent(it)
                .setIndicator(ResUtil.getString(this, R.string.tab_apps),
                        getResources().getDrawable(R.drawable.applications)));

        it = new Intent(Intent.ACTION_VIEW);
        it.setClass(this, ProcessManager.class);
        th.addTab(th
                .newTabSpec(ProcessManager.class.getName())
                .setContent(it)
                .setIndicator(ResUtil.getString(this, R.string.tab_procs),
                        getResources().getDrawable(R.drawable.processes)));

        it = new Intent(Intent.ACTION_VIEW);
        it.setClass(this, NetStateManager.class);
        th.addTab(th
                .newTabSpec(NetStateManager.class.getName())
                .setContent(it)
                .setIndicator(ResUtil.getString(this, R.string.tab_netstat),
                        getResources().getDrawable(R.drawable.connection)));

        if (Util.SDK_VER > 10) {
            if (Util.SDK_VER >= 14) {
                overwriteCaps(th);
            }

            View sideMenu = getLayoutInflater().inflate(R.layout.side_menu, null);
            LayoutParams lp =
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT
                            | Gravity.BOTTOM);
            lp.bottomMargin = (int) (getResources().getDisplayMetrics().density * 72);
            sideMenu.setLayoutParams(lp);
            sideMenu.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    Activity cac = getCurrentActivity();
                    if (cac != null) {
                        if (ToolMenuDialog.support(cac)) {
                            ToolMenuDialog dlg = new ToolMenuDialog(cac);
                            dlg.setOwnerActivity(cac);
                            dlg.show();
                        } else {
                            Configuration conf = getResources().getConfiguration();
                            int old = Util.getScreenLayout(conf);
                            Util.setScreenLayout(conf, Configuration.SCREENLAYOUT_SIZE_NORMAL);
                            getCurrentActivity().openOptionsMenu();
                            Util.setScreenLayout(conf, old);
                        }
                    }
                }
            });
            th.addView(sideMenu);
        }

        startService(new Intent(this, StatusUpdaterService.class));

        int targetTab = getIntent().getIntExtra(EXTRA_TARGET_TAB, 0);

        if (targetTab > 0 && targetTab < 5) {
            th.setCurrentTab(targetTab - 1);
        } else {
            SharedPreferences sp =
                    getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            if (sp != null) {
                int tab = sp.getInt(SysInfoManager.PREF_KEY_DEFAULT_TAB, 0);

                if (tab == 0) {
                    tab = sp.getInt(PREF_KEY_LAST_ACTIVE, 1);
                }

                if (tab > 0 && tab < 5) {
                    th.setCurrentTab(tab - 1);
                }
            }
        }
    }

    private void overwriteCaps(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setSingleLine();
            Util.setAllCaps(tv, false);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;

            for (int i = 0; i < vg.getChildCount(); i++) {
                overwriteCaps(vg.getChildAt(i));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        int targetTab = intent.getIntExtra(EXTRA_TARGET_TAB, 0);

        if (targetTab > 0 && targetTab < 5) {
            getTabHost().setCurrentTab(targetTab - 1);
        }
    }

    @Override
    protected void onDestroy() {
        SharedPreferences sp =
                getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

        if (sp != null) {
            int tab = sp.getInt(SysInfoManager.PREF_KEY_DEFAULT_TAB, 0);

            if (tab == 0) {
                int currentActive = getTabHost().getCurrentTab() + 1;

                if (currentActive != sp.getInt(PREF_KEY_LAST_ACTIVE, 1)) {
                    Editor et = sp.edit();
                    et.putInt(PREF_KEY_LAST_ACTIVE, currentActive);
                    et.commit();
                }
            }

            if (!sp.getBoolean(SysInfoManager.PREF_KEY_PERSISTENT_ICON, true)) {
                stopService(new Intent(this, StatusUpdaterService.class));
            }
        }

        gestureDetector = null;
        getTabHost().clearAllTabs();

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // fix: https://code.google.com/p/android/issues/detail?id=19917
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * ErrorHandler
     */
    static final class ErrorHandler implements UncaughtExceptionHandler, Constants {

        private UncaughtExceptionHandler parentHandler;
        private Context ctx;

        ErrorHandler(Context ctx, UncaughtExceptionHandler parentHandler) {
            this.parentHandler = parentHandler;
            this.ctx = ctx;
        }

        public void uncaughtException(Thread thread, Throwable ex) {
            boolean shouldIgnore = shouldIgnore(thread);

            if (!shouldIgnore) {
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setClass(ctx, ErrorReportActivity.class);
                it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                it.putExtra("thread", thread.toString()); //$NON-NLS-1$
                it.putExtra("exception", Log.getStackTraceString(ex)); //$NON-NLS-1$

                PendingIntent pi = PendingIntent.getActivity(ctx, 0, it, 0);

                Notification nc = new Notification(R.drawable.icon, "Oops", //$NON-NLS-1$
                        System.currentTimeMillis());

                nc.flags |= Notification.FLAG_AUTO_CANCEL;

                if (Util.SDK_VER >= 26) {
                    Util.setDefaultNotificationChannel(nc, ctx);
                }

                Util.setLatestEventInfo(nc, ctx, ResUtil.getString(ctx, R.string.oops),
                        ResUtil.getString(ctx, R.string.oops_msg), pi);

                ((NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE)).notify(
                        NOTIFY_ERROR_REPORT, nc);

                if (parentHandler != null) {
                    parentHandler.uncaughtException(thread, ex);
                }
            }
        }

        private static boolean shouldIgnore(Thread thread) {
            if ("WebViewWorkerThread".equals(thread.getName()) //$NON-NLS-1$
                    || "WebViewCoreThread".equals(thread.getName())) //$NON-NLS-1$
            {
                // ignore errors from this thread, mostly ad thread
                return true;
            }
            return false;
        }

        private static String getHeader(String threadString) {
            StringBuilder sb = new StringBuilder();
            sb.append(Build.DEVICE).append('|');
            sb.append(Build.MODEL).append('|');
            sb.append(Build.BOARD).append('|');
            sb.append(Build.VERSION.RELEASE).append('|');
            sb.append(Build.VERSION.SDK).append('|');
            sb.append(Build.DISPLAY);
            sb.append('(').append(threadString).append(')');
            return sb.toString();
        }

        private static Throwable getRootCause(Throwable t) {
            while (t.getCause() != null) {
                t = t.getCause();
            }
            return t;
        }

        private static String getStackTraceString(Throwable t) {
            if (t == null) {
                return ""; //$NON-NLS-1$
            }

            StringBuilder sb = new StringBuilder();

            sb.append(t);

            StackTraceElement[] tracs = t.getStackTrace();

            if (tracs != null) {
                String lastPkgName = null;
                String lastClzName = null;
                String lastFileName = null;

                for (StackTraceElement trac : tracs) {
                    sb.append("\n@ "); //$NON-NLS-1$

                    String clzName = trac.getClassName();

                    String tmpPkgName = null;
                    String tmpClzName = clzName;

                    if (clzName != null) {
                        int idx = clzName.lastIndexOf('.');

                        if (idx > 0) {
                            tmpPkgName = clzName.substring(0, idx);
                            tmpClzName = clzName.substring(idx);
                        }
                    }

                    if (lastPkgName != null && lastPkgName.equals(tmpPkgName)) {
                        sb.append('^');

                        if (lastClzName != null && lastClzName.equals(tmpClzName)) {
                            sb.append('^');
                        } else {
                            sb.append(tmpClzName);
                        }
                    } else {
                        sb.append(clzName);
                    }

                    lastPkgName = tmpPkgName;
                    lastClzName = tmpClzName;

                    sb.append('.').append(trac.getMethodName());

                    String tmpFileName = null;

                    if (trac.isNativeMethod()) {
                        sb.append("(N.)"); //$NON-NLS-1$
                    } else if (trac.getFileName() != null) {
                        tmpFileName = trac.getFileName();

                        sb.append('(');

                        if (lastFileName != null && lastFileName.equals(tmpFileName)) {
                            sb.append("^"); //$NON-NLS-1$
                        } else {
                            String shortName = tmpFileName;

                            if (tmpFileName.endsWith(".java")) //$NON-NLS-1$
                            {
                                shortName = tmpFileName.substring(0, tmpFileName.length() - 5);
                            }

                            if (lastClzName != null) {
                                if (lastClzName.length() > 1 && lastClzName.charAt(0) == '.') {
                                    String tmpName = lastClzName.substring(1);

                                    if (shortName.equals(tmpName)) {
                                        shortName = "#"; //$NON-NLS-1$
                                    } else if (shortName.startsWith(tmpName)) {
                                        shortName = "#" //$NON-NLS-1$
                                                + shortName.substring(tmpName.length());
                                    }
                                } else {
                                    if (shortName.equals(lastClzName)) {
                                        shortName = "#"; //$NON-NLS-1$
                                    } else if (shortName.startsWith(lastClzName)) {
                                        shortName = "#" //$NON-NLS-1$
                                                + shortName.substring(lastClzName.length());
                                    }
                                }
                            }

                            sb.append(shortName);
                        }

                        if (trac.getLineNumber() >= 0) {
                            sb.append(':').append(trac.getLineNumber());
                        }

                        sb.append(')');
                    } else {
                        sb.append("(U.)"); //$NON-NLS-1$
                    }

                    lastFileName = tmpFileName;
                }
            }

            return sb.toString();
        }
    }

    /**
     * ErrorReportActivity
     */
    public static final class ErrorReportActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);

            Util.hookExceptionHandler(getApplicationContext());

            if (!getIntent().getBooleanExtra("signature", true)) //$NON-NLS-1$
            {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            Intent it = new Intent(Intent.ACTION_VIEW);
                            it.setData(Uri.parse("http://code.google.com/p/qsysinfo/")); //$NON-NLS-1$

                            Util.safeStartActivity(ErrorReportActivity.this, Intent.createChooser(it, null),
                                    false);
                        }

                        ErrorReportActivity.this.finish();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.warning))
                        .setMessage(ResUtil.getString(this, R.string.warning_tampered))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, listener).setCancelable(false).create()
                        .show();
            } else {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            sendBugReport();
                        }

                        ErrorReportActivity.this.finish();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.bug_title))
                        .setMessage(ResUtil.getString(this, R.string.bug_detail))
                        .setPositiveButton(ResUtil.getString(this, R.string.agree), listener)
                        .setNegativeButton(android.R.string.no, listener).setCancelable(false).create().show();
            }
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }

        void sendBugReport() {
            StringBuffer msg = new StringBuffer();
            Intent it = new Intent(Intent.ACTION_SENDTO);
            String content = null;

            try {
                String title = "Bug Report - " + new Date().toLocaleString(); //$NON-NLS-1$

                it.setData(Uri.parse("mailto:qauck.aa@gmail.com")); //$NON-NLS-1$

                it.putExtra(Intent.EXTRA_SUBJECT, title);

                Util.createTextHeader(this, msg, title);

                msg.append("\n-----THREAD-----\n") //$NON-NLS-1$
                        .append(getIntent().getStringExtra("thread")); //$NON-NLS-1$

                msg.append("\n\n-----EXCEPTION-----\n") //$NON-NLS-1$
                        .append(getIntent().getStringExtra("exception")); //$NON-NLS-1$

                // try get the intermediate report first
                content = msg.toString();

                msg.append("\n\n-----LOGCAT-----\n"); //$NON-NLS-1$

                Process proc = Runtime.getRuntime().exec("logcat -d -v time *:V"); //$NON-NLS-1$

                Util.readRawText(msg, proc.getInputStream(), 8192);

                msg.append("\n-----LOGCAT-END-----\n"); //$NON-NLS-1$
            } catch (Throwable e) {
                try {
                    msg.append("\n\n-----ERROR-COLLECT-REPORT-----\n"); //$NON-NLS-1$
                    msg.append(Log.getStackTraceString(e));
                    msg.append("\n-----ERROR-END-----\n"); //$NON-NLS-1$
                } catch (Throwable t) {
                    // must be OOM, doing nothing
                }
            } finally {
                try {
                    // get the final report
                    content = msg.toString();
                } catch (Throwable t) {
                    // mostly still be OOM, doing nothing
                } finally {
                    if (content != null) {
                        try {
                            it.putExtra(Intent.EXTRA_TEXT, content);

                            it = Intent.createChooser(it, null);

                            startActivity(it);

                            return;
                        } catch (Throwable t) {
                            // failed at last stage, log and give up
                            Log.e(getClass().getName(), t.getLocalizedMessage(), t);
                        }
                    }

                    Util.shortToast(this, R.string.bug_failed);
                }
            }
        }
    }

    /**
     * BootReceiver
     */
    public static final class BootReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences sp =
                    context.getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            if (sp != null && sp.getBoolean(SysInfoManager.PREF_KEY_PERSISTENT_ICON, true)
                    && sp.getBoolean(SysInfoManager.PREF_KEY_AUTO_START_ICON, false)) {
                if (Util.SDK_VER >= 26) {
                    Intent svcIntent = new Intent(context, ForegroundStarterService.class);
                    svcIntent.putExtra(Constants.EXTRA_SVC_CLZ_NAME, StatusUpdaterService.class.getName());
                    Util.startForegroundService(context, svcIntent, new Intent(context, StatusUpdaterService.class));
                } else {
                    context.startService(new Intent(context, StatusUpdaterService.class));
                }
            }
        }
    }

    /**
     * Only used to start foreground service since Oreo(>=26)
     */
    public static final class ForegroundStarterService extends Service {

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onStart(Intent intent, int startId) {
            Context ctx = getApplicationContext();

            Notification nc = new Notification(R.drawable.cpu_noop, null, System.currentTimeMillis());
            nc.flags = Notification.FLAG_AUTO_CANCEL;

            Util.setDefaultNotificationChannel(nc, ctx);
            Util.setLatestEventInfo(nc, ctx, ResUtil.getString(ctx, ProUtil.getId_appName()),
                    ResUtil.getString(ctx, R.string.info_icon_hint), null);

            Util.safeSet(StatusUpdaterService.fdNotificationGroupKey, nc, "info_icon");
            Util.startForeground(this, Constants.NOTIFY_BOOT, nc);

            String svcClzName = intent.getStringExtra(Constants.EXTRA_SVC_CLZ_NAME);
            if (svcClzName == null || svcClzName.length() == 0) {
                Log.e(ForegroundStarterService.class.getName(), "No target service name provided.");
            } else {
                Intent svcIntent = new Intent();
                svcIntent.setClassName(this, svcClzName);
                startService(svcIntent);
            }
            Util.stopForeground(this);
            stopSelfResult(startId);
        }
    }
}
