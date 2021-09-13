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

import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.text.Html;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import org.uguess.android.sysinfo.StatusUpdaterService.Graph;


/**
 * PopService
 */
public class PopService extends Service {

    static final int TEXT_COLOR_PRIMARY = 0xffeeeeee;
    static final int TEXT_COLOR_SECONDARY = 0xffcccccc;

    static volatile BaseUpdater cpuUpdater, memUpdater, netUpdater;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public synchronized void onStart(Intent intent, int startId) {
        int target = intent.getIntExtra(StatusUpdaterService.EXTRA_TARGET, 0);

        if ((target == StatusUpdaterService.TARGET_INFO && cpuUpdater == null)
                || (target == StatusUpdaterService.TARGET_TASK && memUpdater == null)
                || (target == StatusUpdaterService.TARGET_WIFI && netUpdater == null)) {
            if (Util.SDK_VER >= 23 && !Util.isSystemOpEnabled(this, "android:system_alert_window" /*AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW*/)) {
                Util.longToast(this, ResUtil.getString(this, R.string.alert_window_alt));
                Intent it = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION");
                it.setData(Uri.parse("package:" + getPackageName()));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Util.safeStartActivity(this, it, false);
            } else {
                SharedPreferences sp =
                        getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                if (sp != null) {
                    long delay = 0;

                    switch (target) {
                        case StatusUpdaterService.TARGET_INFO:

                            delay = StatusUpdaterService.getDelay(sp, Constants.PREF_KEY_REFRESH_INTERVAL_CPU);
                            break;
                        case StatusUpdaterService.TARGET_TASK:

                            delay = StatusUpdaterService.getDelay(sp, Constants.PREF_KEY_REFRESH_INTERVAL_MEM);
                            break;
                        case StatusUpdaterService.TARGET_WIFI:

                            delay = StatusUpdaterService.getDelay(sp, Constants.PREF_KEY_REFRESH_INTERVAL_NET);
                            break;
                    }

                    if (delay != 0) {
                        showPopup(getApplicationContext(), target, new Handler(), delay);
                    }
                }
            }
        }

        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        stopSelfResult(startId);
    }

    private static void showPopup(Context ctx, final int target, Handler handler, long delay) {
        final Dialog dlg = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);

        LayoutParams lp =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                        Util.SDK_VER < 26 ? LayoutParams.TYPE_SYSTEM_ALERT : (2038 /*TYPE_APPLICATION_OVERLAY = FIRST_SYSTEM_WINDOW + 38*/),
                        LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = 0;
        lp.y = (int) (100 * ctx.getResources().getDisplayMetrics().density);
        lp.alpha = 1;
        lp.windowAnimations = android.R.style.Animation_Translucent;

        dlg.getWindow().setAttributes(lp);

        View contentView =
                ((LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
                        R.layout.history_graph, null);

        contentView.setBackgroundResource(R.drawable.bg_graph);

        dlg.setContentView(contentView);

        View btn = dlg.findViewById(R.id.pop);
        ((ImageView) btn).setImageResource(R.drawable.cross_light);
        btn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                switch (target) {
                    case StatusUpdaterService.TARGET_INFO:

                        if (cpuUpdater != null) {
                            cpuUpdater.dispose();
                            cpuUpdater = null;
                        }
                        break;
                    case StatusUpdaterService.TARGET_TASK:

                        if (memUpdater != null) {
                            memUpdater.dispose();
                            memUpdater = null;
                        }
                        break;
                    case StatusUpdaterService.TARGET_WIFI:

                        if (netUpdater != null) {
                            netUpdater.dispose();
                            netUpdater = null;
                        }
                        break;
                }

                dlg.dismiss();
            }
        });

        contentView.setOnTouchListener(new OnTouchListener() {

            boolean startMove = false;
            float prevX, prevY;

            public boolean onTouch(View v, MotionEvent evt) {
                switch (evt.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        startMove = true;
                        prevX = evt.getRawX();
                        prevY = evt.getRawY();

                        return true;
                    case MotionEvent.ACTION_MOVE:

                        if (startMove) {
                            float deltaX = evt.getRawX() - prevX;
                            float deltaY = evt.getRawY() - prevY;

                            prevX = evt.getRawX();
                            prevY = evt.getRawY();

                            Display dsp = dlg.getWindow().getWindowManager().getDefaultDisplay();

                            DisplayMetrics dm = new DisplayMetrics();
                            dsp.getMetrics(dm);

                            LayoutParams lp = dlg.getWindow().getAttributes();

                            lp.x += deltaX;
                            lp.y += deltaY;

                            if (lp.x < 0) {
                                lp.x = 0;
                            }

                            if (lp.x > dm.widthPixels) {
                                lp.x = dm.widthPixels;
                            }

                            if (lp.y < 0) {
                                lp.y = 0;
                            }

                            if (lp.y > dm.heightPixels) {
                                lp.y = dm.heightPixels;
                            }

                            dlg.getWindow().setAttributes(lp);
                        }

                        return true;
                    case MotionEvent.ACTION_UP:

                        startMove = false;

                        return true;
                }
                return false;
            }
        });

        switch (target) {
            case StatusUpdaterService.TARGET_INFO:

                cpuUpdater = new CpuUpdater(ctx, contentView, handler, delay);
                handler.post(cpuUpdater);
                break;
            case StatusUpdaterService.TARGET_TASK:

                memUpdater = new MemUpdater(ctx, contentView, handler, delay);
                handler.post(memUpdater);
                break;
            case StatusUpdaterService.TARGET_WIFI:

                netUpdater = new NetUpdater(ctx, contentView, handler, delay);
                handler.post(netUpdater);
                break;
        }

        dlg.show();
    }

    static void updateView(Context ctx, View contentView, int viewIcon, CharSequence title,
                           int titleColor, CharSequence text, int textColor, Graph[] graph, int[] percent, int color) {
        if (viewIcon != 0) {
            ((ImageView) contentView.findViewById(R.id.icon)).setImageResource(viewIcon);
        }

        if (title != null) {
            ((TextView) contentView.findViewById(R.id.title)).setText(title);
            ((TextView) contentView.findViewById(R.id.title)).setTextColor(titleColor);
        }

        if (text != null) {
            contentView.findViewById(R.id.text).setVisibility(View.VISIBLE);
            ((TextView) contentView.findViewById(R.id.text)).setText(text);
            ((TextView) contentView.findViewById(R.id.text)).setTextColor(textColor);
        } else {
            contentView.findViewById(R.id.text).setVisibility(View.GONE);
        }

        updateGraph(contentView, graph, percent, color, 0, R.id.graph);
        updateGraph(contentView, graph, percent, color, 1, R.id.graph1);
        updateGraph(contentView, graph, percent, color, 2, R.id.graph2);
        updateGraph(contentView, graph, percent, color, 3, R.id.graph3);
        updateGraph(contentView, graph, percent, color, 4, R.id.graph4);
        updateGraph(contentView, graph, percent, color, 5, R.id.graph5);
        updateGraph(contentView, graph, percent, color, 6, R.id.graph6);
        updateGraph(contentView, graph, percent, color, 7, R.id.graph7);
    }

    static void updateGraph(View contentView, Graph[] graph, int[] percent, int color, int idx,
                            int viewId) {
        if (graph != null && graph.length > idx && graph[idx] != null) {
            graph[idx].update(percent[idx], color);
            contentView.findViewById(viewId).setVisibility(View.VISIBLE);
            ((ImageView) contentView.findViewById(viewId)).setImageBitmap(graph[idx].bmp);
        } else {
            contentView.findViewById(viewId).setVisibility(View.GONE);
            ((ImageView) contentView.findViewById(viewId)).setImageBitmap(null);
        }
    }

    /**
     * BaseUpdater
     */
    static abstract class BaseUpdater implements Runnable {

        Context ctx;
        Handler handler;
        View contentView;

        long delay;

        boolean cancelled;

        BaseUpdater(Context ctx, View contentView, Handler handler, long delay) {
            this.ctx = ctx;
            this.contentView = contentView;
            this.handler = handler;
            this.delay = delay;

            OnLongClickListener listener = new OnLongClickListener() {

                public boolean onLongClick(View v) {
                    DialogInterface.OnClickListener listener =
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    BaseUpdater.this.delay = which == 0 ? 500 : (which == 3 ? 4000 : (which * 1000));
                                }
                            };

                    Dialog dlg = Util.newAlertDialogBuilder(BaseUpdater.this.ctx)
                            .setTitle(ResUtil.getString(BaseUpdater.this.ctx, R.string.update_speed))
                            //.setNeutralButton(ResUtil.getString(BaseUpdater.this.ctx, R.string.close), null)
                            .setSingleChoiceItems(
                                    new CharSequence[]{
                                            Html.fromHtml(ResUtil.getString(BaseUpdater.this.ctx, R.string.higher)
                                                    + "<br><small><small><font color=\"#ff0000\">" //$NON-NLS-1$
                                                    + ResUtil.getString(BaseUpdater.this.ctx, R.string.higher_warn)
                                                    + "</font></small></small>&nbsp;"), //$NON-NLS-1$
                                            ResUtil.getString(BaseUpdater.this.ctx, R.string.high),
                                            ResUtil.getString(BaseUpdater.this.ctx, R.string.normal),
                                            ResUtil.getString(BaseUpdater.this.ctx, R.string.low),},
                                    BaseUpdater.this.delay < 1000 ? 0 : (BaseUpdater.this.delay == 1000 ? 1
                                            : (BaseUpdater.this.delay == 2000 ? 2 : 3)), listener).create();

                    LayoutParams lp =
                            new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                                    Util.SDK_VER < 26 ? LayoutParams.TYPE_SYSTEM_ALERT : (2038 /*TYPE_APPLICATION_OVERLAY = FIRST_SYSTEM_WINDOW + 38*/),
                                    0, PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.CENTER;
                    lp.x = 0;
                    lp.y = 0;
                    lp.alpha = 1;
                    lp.windowAnimations = android.R.style.Animation_Dialog;

                    dlg.getWindow().setAttributes(lp);

                    dlg.show();

                    return true;
                }
            };

            contentView.findViewById(R.id.icon).setOnLongClickListener(listener);
        }

        abstract void update();

        int getRealGraphWidth(DisplayMetrics dm) {
            int usable = (int) (dm.widthPixels / dm.density - 8);
            return Math.min(StatusUpdaterService.GRAPH_WIDTH, usable);
        }

        public void run() {
            if (cancelled) {
                return;
            }

            update();

            if (delay > 0 && !cancelled) {
                handler.postDelayed(this, delay);
            }
        }

        public void dispose() {
            cancelled = true;
            handler.removeCallbacks(this);

            ((ImageView) contentView.findViewById(R.id.graph)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph1)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph2)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph3)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph4)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph5)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph6)).setImageBitmap(null);
            ((ImageView) contentView.findViewById(R.id.graph7)).setImageBitmap(null);
        }
    }

    /**
     * CpuUpdater
     */
    static final class CpuUpdater extends BaseUpdater {

        long[] lastLoad;
        long[][] lastAllLoad;

        CpuState cpuObj = new CpuState();

        Graph infoGraph;
        Graph[] extraInfoGraph;

        CpuUpdater(Context ctx, View contentView, Handler handler, long delay) {
            super(ctx, contentView, handler, delay);
        }

        @Override
        void update() {
            int cl = 1;
            int pl = 0;

            long[] load = ProcessManager.readCpuLoad();

            if (lastLoad != null && load != null) {
                long totaldelta = load[0] + load[1] - (lastLoad[0] + lastLoad[1]);

                if (totaldelta != 0) {
                    long workdelta = load[0] - lastLoad[0];

                    cl = (int) (workdelta * 100 / totaldelta);

                    if (cl < 0) {
                        cl = 0;
                    }

                    if (cl > 100) {
                        cl = 100;
                    }

                    pl = cl;

                    cl = (cl / 15) + 1;
                }
            }

            lastLoad = load;

            StringBuilder cpuState = new StringBuilder();

            cpuState.append(ResUtil.getString(ctx, R.string.cpu_usage));
            cpuState.append(": ").append(pl).append('%'); //$NON-NLS-1$

            String[] cs = cpuObj.getMips();
            if (cs != null && cs.length > 0) {
                if (cs.length == 1) {
                    if (cs[0] != null) {
                        cpuState.append(" (").append(cs[0]).append(')'); //$NON-NLS-1$
                    }
                } else {
                    cpuState.append(" ("); //$NON-NLS-1$

                    for (int i = 0; i < cs.length; i++) {
                        if (i > 0) {
                            cpuState.append(", "); //$NON-NLS-1$
                        }

                        cpuState.append(cs[i] != null ? cs[i] : "zZ"); //$NON-NLS-1$
                    }

                    cpuState.append(')');
                }
            }

            // if ( showCpuHistory )
            {
                int pc = Runtime.getRuntime().availableProcessors();

                if (pc > 1) {
                    long[][] allLoad = ProcessManager.readAllCpuLoad(pc);

                    int[] percents = new int[pc];

                    if (lastAllLoad != null && allLoad != null) {
                        for (int i = 0; i < pc; i++) {
                            if (allLoad[i] == null || i >= lastAllLoad.length || lastAllLoad[i] == null) {
                                continue;
                            }

                            long totaldelta =
                                    allLoad[i][0] + allLoad[i][1] - (lastAllLoad[i][0] + lastAllLoad[i][1]);

                            if (totaldelta != 0) {
                                long workdelta = allLoad[i][0] - lastAllLoad[i][0];

                                percents[i] = (int) (workdelta * 100 / totaldelta);

                                if (percents[i] < 0) {
                                    percents[i] = 0;
                                }

                                if (percents[i] > 100) {
                                    percents[i] = 100;
                                }
                            }
                        }
                    }

                    lastAllLoad = allLoad;

                    if (extraInfoGraph == null || extraInfoGraph.length != pc) {
                        if (extraInfoGraph != null) {
                            for (Graph g : extraInfoGraph) {
                                g.release();
                            }
                        }

                        extraInfoGraph = new Graph[pc];

                        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                        float scale = dm.density;

                        int width = (int) ((getRealGraphWidth(dm) - pc * 2 - 2) * scale / pc);

                        for (int i = 0; i < pc; i++) {
                            extraInfoGraph[i] =
                                    new Graph(width, (int) (StatusUpdaterService.GRAPH_HEIGHT * scale),
                                            (int) (StatusUpdaterService.GRAPH_BAR * scale),
                                            (int) (StatusUpdaterService.GRAPH_SPACE * scale),
                                            (int) (StatusUpdaterService.GRAPH_BORDER * scale));
                        }
                    }

                    updateView(ctx, contentView, R.drawable.icon, cpuState, TEXT_COLOR_PRIMARY, null, 0,
                            extraInfoGraph, percents, 0xff52b652);
                } else {
                    if (infoGraph == null) {
                        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                        float scale = dm.density;
                        infoGraph =
                                new Graph((int) (getRealGraphWidth(dm) * scale),
                                        (int) (StatusUpdaterService.GRAPH_HEIGHT * scale),
                                        (int) (StatusUpdaterService.GRAPH_BAR * scale),
                                        (int) (StatusUpdaterService.GRAPH_SPACE * scale),
                                        (int) (StatusUpdaterService.GRAPH_BORDER * scale));
                    }

                    updateView(ctx, contentView, R.drawable.icon, cpuState, TEXT_COLOR_PRIMARY, null, 0,
                            new Graph[]{infoGraph}, new int[]{pl}, 0xff52b652);
                }
            }
        }

        @Override
        public void dispose() {
            super.dispose();

            if (infoGraph != null) {
                infoGraph.release();
                infoGraph = null;
            }

            if (extraInfoGraph != null) {
                for (Graph g : extraInfoGraph) {
                    if (g != null) {
                        g.release();
                    }
                }

                extraInfoGraph = null;
            }
        }
    }

    /**
     * MemUpdater
     */
    static final class MemUpdater extends BaseUpdater {

        Graph taskGraph;

        MemUpdater(Context ctx, View contentView, Handler handler, long delay) {
            super(ctx, contentView, handler, delay);
        }

        @Override
        void update() {

            int cl = 1;
            int pl = 0;
            String size = ResUtil.getString(ctx, R.string.unknown);

            long[] mem = Util.getMemState(ctx);

            if (mem != null && mem[0] > 0) {
                long used = mem[0] - mem[2];

                if (used < 0) {
                    used = 0;
                }

                size = Util.safeFormatFileSize(ctx, used);

                cl = (int) (used * 100 / mem[0]);

                if (cl < 0) {
                    cl = 0;
                }

                if (cl > 100) {
                    cl = 100;
                }

                pl = cl;

                cl = (cl / 15) + 1;
            }

            // if ( showMemHistory )
            {
                if (taskGraph == null) {
                    DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    float scale = dm.density;
                    taskGraph =
                            new Graph((int) (getRealGraphWidth(dm) * scale),
                                    (int) (StatusUpdaterService.GRAPH_HEIGHT * scale),
                                    (int) (StatusUpdaterService.GRAPH_BAR * scale),
                                    (int) (StatusUpdaterService.GRAPH_SPACE * scale),
                                    (int) (StatusUpdaterService.GRAPH_BORDER * scale));
                }

                updateView(ctx, contentView, R.drawable.end, ResUtil.getString(ctx, R.string.memory_usage)
                                + ": " //$NON-NLS-1$
                                + pl + "% (" //$NON-NLS-1$
                                + size + ')', TEXT_COLOR_PRIMARY, null, 0, new Graph[]{taskGraph}, new int[]{pl},
                        0xff5275c6);
            }
        }

        @Override
        public void dispose() {
            super.dispose();

            if (taskGraph != null) {
                taskGraph.release();
                taskGraph = null;
            }
        }
    }

    /**
     * NetUpdater
     */
    static final class NetUpdater extends BaseUpdater {

        long lastRx, lastTx;

        NetUpdater(Context ctx, View contentView, Handler handler, long delay) {
            super(ctx, contentView, handler, delay);
        }

        @Override
        void update() {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm != null) {
                NetworkInfo nif = cm.getActiveNetworkInfo();

                if (nif != null && nif.isConnected()) {
                    String netDev = null;
                    String netType = nif.getSubtypeName();
                    boolean forceHideIcon = false;

                    nif = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                    if (nif != null && nif.isConnected()) {
                        netDev = Util.getActiveWifiDevice();
                        netType = "Wi-Fi"; //$NON-NLS-1$

                        // if ( showSsid )
                        {
                            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);

                            WifiInfo wi = wm.getConnectionInfo();

                            if (wi != null && wi.getSSID() != null) {
                                netType = wi.getSSID();
                            }
                        }
                    } else {
                        netDev = Util.getActiveCellDevice();
                        forceHideIcon = true;
                    }

                    if (netDev != null) {
                        long rx = Util.getRxBytes(netDev);
                        long tx = Util.getTxBytes(netDev);

                        long drx = 0;
                        long dtx = 0;

                        if (delay > 0) {
                            if (lastRx != 0) {
                                drx = rx - lastRx;

                                if (drx > 0) {
                                    drx = drx * 1000 / delay;
                                } else {
                                    drx = 0;
                                }
                            }

                            if (lastTx != 0) {
                                dtx = tx - lastTx;

                                if (dtx > 0) {
                                    dtx = dtx * 1000 / delay;
                                } else {
                                    dtx = 0;
                                }
                            }
                        }

                        lastRx = rx;
                        lastTx = tx;

                        String hint;

                        // if ( showRates )
                        {
                            hint = ResUtil.getString(ctx, R.string.rates) + ": Tx: " //$NON-NLS-1$
                                    + Util.safeFormatFileSize(ctx, dtx) + "/s, Rx: " //$NON-NLS-1$
                                    + Util.safeFormatFileSize(ctx, drx) + "/s"; //$NON-NLS-1$
                        }
                        // else
                        // {
                        // hint = getString( R.string.net_icon_hint );
                        // }

                        updateView(ctx, contentView,
                                forceHideIcon ? ("EDGE".equalsIgnoreCase(netType) ? R.drawable.mobile_edge_noop //$NON-NLS-1$
                                        : R.drawable.mobile_noop) : R.drawable.wifi_noop, (TextUtils.isEmpty(netType) ? "" //$NON-NLS-1$
                                        : netType) + " Tx: " //$NON-NLS-1$
                                        + Util.safeFormatFileSize(ctx, lastTx) + ", Rx: " //$NON-NLS-1$
                                        + Util.safeFormatFileSize(ctx, lastRx), TEXT_COLOR_PRIMARY, hint,
                                TEXT_COLOR_SECONDARY, null, null, 0);

                    }
                }
            }
        }
    }
}
