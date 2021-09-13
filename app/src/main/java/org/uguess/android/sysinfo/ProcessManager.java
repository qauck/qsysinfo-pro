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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.uguess.android.sysinfo.ToolMenuDialog.ActionHint;
import org.uguess.android.sysinfo.ToolMenuDialog.IActionMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.ISortMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.IToggleMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.SortHint;
import org.uguess.android.sysinfo.ToolMenuDialog.ToggleHint;
import org.uguess.android.sysinfo.Util.EditorState;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * ProcessManager
 */
public class ProcessManager extends ListActivity implements Constants, IActionMenuProvider,
        ISortMenuProvider, IToggleMenuProvider {

    private static final int UNKNOWN_OOM = -100;

    private static final int MSG_REFRESH_PKG_DETAILS = MSG_PRIVATE + 1;

    private static final int MIN_MINUTES = 1; // 1 minute
    private static final int MAX_MINUTES = 24 * 60; // 1 day

    private static final String PREF_KEY_IGNORE_ACTION = "ignore_action"; //$NON-NLS-1$
    private static final String PREF_KEY_IGNORE_LIST = "ignore_list"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_MEM = "show_mem"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_PSS = "show_pss"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_CPU = "show_cpu"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_SYS_PROC = "show_sys_proc"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_KILL_WARN = "show_kill_warn"; //$NON-NLS-1$
    private static final String PREF_KEY_ENABLE_GUARD = "enable_guard"; //$NON-NLS-1$
    private static final String PREF_KEY_GUARD_LIST = "guard_list"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_SERVICE = "show_service"; //$NON-NLS-1$
    private static final String PREF_KEY_DISABLE_ROOT = "disable_root"; //$NON-NLS-1$

    private static final int ORDER_TYPE_NAME = 0;
    private static final int ORDER_TYPE_IMPORTANCE = 1;
    private static final int ORDER_TYPE_MEM = 2;
    private static final int ORDER_TYPE_CPU = 3;

    private static final int ACTION_MENU = 0;
    private static final int ACTION_SWITCH = 1;
    private static final int ACTION_END = 2;
    private static final int ACTION_END_OTHERS = 3;
    private static final int ACTION_IGNORE = 4;
    private static final int ACTION_DETAILS = 5;
    private static final int ACTION_GUARD = 6;
    private static final int ACTION_FORCE_STOP = 7;
    private static final int ACTION_VIEW_LOG = 8;
    private static final int ACTION_SEARCH = 9;

    private static final int IGNORE_ACTION_HIDDEN = 0;
    private static final int IGNORE_ACTION_PROTECTED = 1;

    static final Method mtdGetProcessMemoryInfo = Util.getMethod(ActivityManager.class,
            "getProcessMemoryInfo", //$NON-NLS-1$
            int[].class);

    private static final FileFilter procFileFilter = new FileFilter() {

        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                String fname = f.getName();
                if (fname.length() > 0) {
                    char first = fname.charAt(0);
                    if (first >= '0' && first <= '9') {
                        try {
                            int pid = Integer.parseInt(fname);
                            if (pid >= 0) {
                                return true;
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
            return false;
        }
    };

    ProcessCache procCache;

    long totalLoad, totalDelta, totalWork, workDelta;

    long totalUser, userDelta, totalNice, niceDelta, totalSys, sysDelta, totalIow, iowDelta,
            totalIrq, irqDelta;

    LinkedHashSet<String> ignoreList, guardList;

    ResourceUpdaterThread resUpdater;

    private byte[] buf = new byte[512];

    Handler handler = new ProcessHandler(this);

    private static final class ProcessHandler extends Handler {

        private WeakReference<ProcessManager> acRef;

        ProcessHandler(ProcessManager ac) {
            acRef = new WeakReference<ProcessManager>(ac);
        }

        public void handleMessage(android.os.Message msg) {
            ProcessManager host = acRef.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_INIT_OK:

                    if (host.resUpdater != null) {
                        host.resUpdater.aborted = true;
                    }

                    (host.resUpdater = new ResourceUpdaterThread(host, host.procCache, host.handler)).start();

                    ArrayAdapter<ProcessItem> adapter =
                            (ArrayAdapter<ProcessItem>) host.getListView().getAdapter();

                    adapter.setNotifyOnChange(false);

                    synchronized (host.procCache) {
                        adapter.clear();

                        ArrayList<ProcessItem> localList = host.procCache.procList;

                        for (int i = 0, size = localList.size(); i < size; i++) {
                            adapter.add(localList.get(i));
                        }
                    }

                    adapter.notifyDataSetChanged();

                    host.refreshHeader();

                    // for Oreo or later, the list is based on usage stats, we don't do auto-refresh
                    if (!needToUseUsageStats(host)) {
                        int interval =
                                Util.getIntOption(host, PROCESS_MANAGER_STORE_NAME, PREF_KEY_REFRESH_INTERVAL,
                                        REFRESH_LOW);

                        switch (interval) {
                            case REFRESH_HIGH:
                                postDelayed(host.task, 1000);
                                break;
                            case REFRESH_NORMAL:
                                postDelayed(host.task, 2000);
                                break;
                            case REFRESH_LOW:
                                postDelayed(host.task, 4000);
                                break;
                        }
                    }
                    break;
                case MSG_REFRESH_PKG_DETAILS:

                    adapter =
                            (ArrayAdapter<ProcessItem>) (ArrayAdapter<ProcessItem>) host.getListView()
                                    .getAdapter();

                    if (msg.arg1 == 1) {
                        host.refreshHeader();

                        adapter.setNotifyOnChange(false);

                        synchronized (host.procCache) {
                            adapter.clear();

                            ArrayList<ProcessItem> localList = host.procCache.procList;

                            for (int i = 0, size = localList.size(); i < size; i++) {
                                adapter.add(localList.get(i));
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();
                    break;
            }
        }
    }

    Runnable task = new Runnable() {

        public void run() {

            List<ProcessInfo> pis = getRunningProcessInfo(ProcessManager.this, true, true);

            SparseArray<List<RunningServiceInfo>> svcMap = null;

            if (pis != null
                    && Util.getBooleanOption(ProcessManager.this, PROCESS_MANAGER_STORE_NAME,
                    PREF_KEY_SHOW_SERVICE)) {

                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                List<RunningServiceInfo> svcs = am.getRunningServices(1000);

                if (svcs != null && svcs.size() > 0) {
                    svcMap = new SparseArray<List<RunningServiceInfo>>();

                    for (int i = 0, size = pis.size(); i < size; i++) {
                        ProcessInfo rap = pis.get(i);

                        svcMap.put(rap.pid, null);
                    }

                    for (int i = 0, size = svcs.size(); i < size; i++) {
                        RunningServiceInfo rsi = svcs.get(i);

                        if (rsi.pid <= 0 || !rsi.started || rsi.restarting > 0) {
                            continue;
                        }

                        if (svcMap.indexOfKey(rsi.pid) >= 0) {
                            List<RunningServiceInfo> rss = svcMap.get(rsi.pid);

                            if (rss == null) {
                                rss = new ArrayList<RunningServiceInfo>();
                                svcMap.put(rsi.pid, rss);
                            }

                            rss.add(rsi);
                        }
                    }
                }
            }

            updateProcess(pis, svcMap);

            handler.sendEmptyMessage(MSG_INIT_OK);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.proc_lst_view);

        // now show ad in parent activity
        // ProUtil.toggleAD( this );

        registerForContextMenu(getListView());

        procCache = new ProcessCache();

        ignoreList = new LinkedHashSet<String>();

        ArrayList<String> list =
                getIgnoreList(getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

        if (list != null) {
            ignoreList.addAll(list);
        }

        guardList = new LinkedHashSet<String>();

        list = getGuardList(getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE));

        if (list != null) {
            guardList.addAll(list);
        }

        View listHeader = findViewById(R.id.list_head);
        listHeader.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                boolean showWarning =
                        Util.getBooleanOption(ProcessManager.this, PROCESS_MANAGER_STORE_NAME,
                                PREF_KEY_SHOW_KILL_WARN);

                if (showWarning) {
                    OnClickListener listener = new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            endAllExcept(null);
                        }
                    };

                    Util.newAlertDialogBuilder(ProcessManager.this)
                            .setTitle(ResUtil.getString(ProcessManager.this, R.string.warning))
                            .setMessage(ResUtil.getString(ProcessManager.this, R.string.end_all_prompt))
                            .setPositiveButton(android.R.string.ok, listener)
                            .setNegativeButton(android.R.string.cancel, null).create().show();
                } else {
                    endAllExcept(null);
                }

            }
        });

        View memHeader = findViewById(R.id.txt_head_mem);
        memHeader.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (procCache.transientOrderType != ORDER_TYPE_MEM) {
                    procCache.transientOrderType = ORDER_TYPE_MEM;
                    procCache.transientOrderDirection = ORDER_DESC;
                } else if (procCache.transientOrderDirection == ORDER_DESC) {
                    procCache.transientOrderDirection = ORDER_ASC;
                } else {
                    procCache.transientOrderType = ProcessCache.NONE;
                    procCache.transientOrderDirection = ProcessCache.NONE;
                }

                procCache.reOrder(ProcessManager.this);

                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 1, 0));
            }
        });

        View cpuHeader = findViewById(R.id.txt_head_cpu);
        cpuHeader.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (procCache.transientOrderType != ORDER_TYPE_CPU) {
                    procCache.transientOrderType = ORDER_TYPE_CPU;
                    procCache.transientOrderDirection = ORDER_DESC;
                } else if (procCache.transientOrderDirection == ORDER_DESC) {
                    procCache.transientOrderDirection = ORDER_ASC;
                } else {
                    procCache.transientOrderType = ProcessCache.NONE;
                    procCache.transientOrderDirection = ProcessCache.NONE;
                }

                procCache.reOrder(ProcessManager.this);

                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 1, 0));
            }
        });

        getListView().setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ProcessItem rap = (ProcessItem) parent.getItemAtPosition(position);

                final int action =
                        Util.getIntOption(ProcessManager.this, PROCESS_MANAGER_STORE_NAME,
                                PREF_KEY_DEFAULT_TAP_ACTION, ACTION_MENU);

                if (action == ACTION_END || action == ACTION_IGNORE) {
                    if (ignoreList.contains(rap.procInfo.processName) || rap.sys) {
                        return;
                    }
                }

                if (action == ACTION_GUARD) {
                    if (guardList.contains(rap.procInfo.processName) || rap.sys) {
                        return;
                    }
                }

                if ((action == ACTION_END || action == ACTION_END_OTHERS)
                        && Util.getBooleanOption(ProcessManager.this, PROCESS_MANAGER_STORE_NAME,
                        PREF_KEY_SHOW_KILL_WARN)) {

                    OnClickListener listener = new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            handleAction(rap, action);
                        }
                    };

                    Util.newAlertDialogBuilder(ProcessManager.this)
                            .setTitle(ResUtil.getString(ProcessManager.this, R.string.warning))
                            .setMessage(
                                    ResUtil.getString(ProcessManager.this, action == ACTION_END ? R.string.end_prompt
                                            : R.string.end_other_prompt))
                            .setPositiveButton(android.R.string.ok, listener)
                            .setNegativeButton(android.R.string.cancel, null).create().show();
                } else {
                    handleAction(rap, action);
                }
            }
        });

        ArrayAdapter<ProcessItem> adapter = new ArrayAdapter<ProcessItem>(this, R.layout.proc_item) {

            public View getView(int position, View convertView,
                                android.view.ViewGroup parent) {
                View view;

                if (convertView == null) {
                    view = ProcessManager.this.getLayoutInflater().inflate(R.layout.proc_item, parent, false);
                } else {
                    view = convertView;
                }

                if (position >= getCount()) {
                    return view;
                }

                if (view.getTag() == null) {
                    view.setTag(new ProcViewHolder(view));
                }

                ProcViewHolder viewHolder = (ProcViewHolder) view.getTag();

                ProcessItem itm = getItem(position);

                SharedPreferences prefStore =
                        getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                boolean showMem = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_MEM);
                boolean showCpu = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_CPU);

                boolean guarded =
                        Util.getBooleanOption(prefStore, PREF_KEY_ENABLE_GUARD, false)
                                && guardList.contains(itm.procInfo.processName);

                String lb = itm.label == null ? itm.procInfo.processName : itm.label;
                if (itm.sys) {
                    lb += " *"; //$NON-NLS-1$
                } else if (ignoreList.contains(itm.procInfo.processName)) {
                    lb += " ~"; //$NON-NLS-1$
                }

                if (guarded) {
                    lb += " @"; //$NON-NLS-1$
                }

                viewHolder.txt_name.setText(lb);

                switch (itm.procInfo.importance) {
                    case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                        viewHolder.txt_name.setTextColor(getResources().getColor(R.color.clouds));
                        break;
                    case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                        viewHolder.txt_name.setTextColor(getResources().getColor(R.color.emerald));
                        break;
                    case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                        viewHolder.txt_name.setTextColor(getResources().getColor(R.color.peter_river));
                        break;
                    case RunningAppProcessInfo.IMPORTANCE_SERVICE:
                        viewHolder.txt_name.setTextColor(getResources().getColor(R.color.concrete));
                        break;
                    case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
                        viewHolder.txt_name.setTextColor(Color.YELLOW);
                        break;
                    case RunningAppProcessInfo.IMPORTANCE_EMPTY:
                    default:
                        viewHolder.txt_name.setTextColor(Color.WHITE);
                        break;
                }

                viewHolder.img_type.setImageDrawable(itm.icon);

                if (showMem) {
                    viewHolder.txt_mem.setVisibility(View.VISIBLE);
                    viewHolder.txt_mem.setText(itm.mem);
                } else {
                    viewHolder.txt_mem.setVisibility(View.GONE);
                }

                if (showCpu) {
                    viewHolder.txt_cpu.setVisibility(View.VISIBLE);

                    long delta = itm.lastcputime == 0 ? 0 : (itm.cputime - itm.lastcputime);
                    long cu = getPercentage(delta, totalDelta);

                    if (itm.procInfo.cpuPercent != -1) {
                        cu = itm.procInfo.cpuPercent;
                    }

                    viewHolder.txt_cpu.setText(String.valueOf(cu));
                } else {
                    viewHolder.txt_cpu.setVisibility(View.GONE);
                }

                if (itm.servicesLabel == null && itm.services != null) {
                    itm.servicesLabel =
                            ResourceUpdaterThread.getServicesLabel(getPackageManager(), itm.services, procCache);
                }

                if (itm.servicesLabel == null) {
                    viewHolder.txt_service.setVisibility(View.GONE);
                } else {
                    viewHolder.txt_service.setVisibility(View.VISIBLE);
                    viewHolder.txt_service.setText(itm.servicesLabel);
                }

                return view;
            }
        };

        getListView().setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        ((ArrayAdapter<ProcessItem>) getListView().getAdapter()).clear();

        procCache.clear();

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
    protected void onResume() {
        super.onResume();

        handler.post(task);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(task);
        handler.removeMessages(MSG_INIT_OK);

        if (resUpdater != null) {
            resUpdater.aborted = true;
            resUpdater = null;
        }

        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PREFERENCES && data != null) {
            EditorState es = Util.beginEditOption(this, PROCESS_MANAGER_STORE_NAME);

            Util.updateIntOption(data, es, PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW);
            Util.updateIntOption(data, es, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            Util.updateIntOption(data, es, PREF_KEY_SORT_DIRECTION, ORDER_ASC);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC);
            Util.updateIntOption(data, es, PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN);
            Util.updateIntOption(data, es, PREF_KEY_DEFAULT_TAP_ACTION, ACTION_MENU);
            Util.updateIntOption(data, es, PREF_KEY_RECENT_SCOPE, 60);

            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_MEM);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_PSS);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_CPU);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_SYS_PROC);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_SERVICE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_KILL_WARN);

            Util.updateBooleanOption(data, es, PREF_KEY_ENABLE_GUARD, false);
            Util.updateBooleanOption(data, es, PREF_KEY_DISABLE_ROOT, false);

            ArrayList<String> list = data.getStringArrayListExtra(PREF_KEY_GUARD_LIST);

            Util.updateStringListOption(es, PREF_KEY_GUARD_LIST, list);

            guardList.clear();

            if (list != null) {
                guardList.addAll(list);
            }

            list = data.getStringArrayListExtra(PREF_KEY_IGNORE_LIST);

            Util.updateStringListOption(es, PREF_KEY_IGNORE_LIST, list);

            ignoreList.clear();

            if (list != null) {
                ignoreList.addAll(list);
            }

            Util.endEditOption(es);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi = menu.add(Menu.NONE, MI_REFRESH, Menu.NONE, ResUtil.getString(this, R.string.refresh));
        mi.setIcon(android.R.drawable.ic_menu_rotate);

        mi = menu.add(Menu.NONE, MI_LIVE_MONITOR, Menu.NONE, ResUtil.getString(this, R.string.live_monitor));
        mi.setIcon(android.R.drawable.ic_menu_share);

        mi = menu.add(Menu.NONE, MI_PREFERENCE, Menu.NONE, ResUtil.getString(this, R.string.preference));
        mi.setIcon(android.R.drawable.ic_menu_preferences);

        mi = menu.add(Menu.NONE, MI_EXIT, Menu.NONE, ResUtil.getString(this, R.string.exit));
        mi.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onActionSelected(item.getItemId());
    }

    @Override
    public List<ActionHint> getActions() {
        List<ActionHint> hints = new ArrayList<ActionHint>();

        hints.add(new ActionHint(ResUtil.getString(this, R.string.refresh), MI_REFRESH));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.live_monitor), MI_LIVE_MONITOR));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.preference), MI_PREFERENCE));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.exit), MI_EXIT));

        return hints;
    }

    @Override
    public boolean onActionSelected(int action) {
        if (action == MI_REFRESH) {
            handler.removeCallbacks(task);
            handler.post(task);

            return true;
        } else if (action == MI_LIVE_MONITOR) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    int popTarget =
                            which == 0 ? StatusUpdaterService.TARGET_INFO : StatusUpdaterService.TARGET_TASK;

                    startService(new Intent(getApplicationContext(), PopService.class).putExtra(
                            StatusUpdaterService.EXTRA_TARGET, popTarget).setData(Uri.parse("target://" //$NON-NLS-1$
                            + popTarget)));
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.live_monitor))
                    .setItems(
                            new String[]{ResUtil.getString(this, R.string.cpu_usage),
                                    ResUtil.getString(this, R.string.memory_usage)}, listener).create().show();

            return true;
        } else if (action == MI_PREFERENCE) {
            Intent it = new Intent(this, Util.getIntentProxyClz(ProcessSettings.class));

            SharedPreferences prefStore =
                    getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            it.putExtra(PREF_KEY_REFRESH_INTERVAL,
                    Util.getIntOption(prefStore, PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW));
            it.putExtra(PREF_KEY_SORT_ORDER_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME));
            it.putExtra(PREF_KEY_SORT_DIRECTION,
                    Util.getIntOption(prefStore, PREF_KEY_SORT_DIRECTION, ORDER_ASC));
            it.putExtra(PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME));
            it.putExtra(PREF_KEY_SECONDARY_SORT_DIRECTION,
                    Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC));
            it.putExtra(PREF_KEY_IGNORE_ACTION,
                    Util.getIntOption(prefStore, PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN));
            it.putExtra(PREF_KEY_DEFAULT_TAP_ACTION,
                    Util.getIntOption(prefStore, PREF_KEY_DEFAULT_TAP_ACTION, ACTION_MENU));
            it.putStringArrayListExtra(PREF_KEY_IGNORE_LIST, getIgnoreList(prefStore));
            it.putExtra(PREF_KEY_ENABLE_GUARD,
                    Util.getBooleanOption(prefStore, PREF_KEY_ENABLE_GUARD, false));
            it.putStringArrayListExtra(PREF_KEY_GUARD_LIST, getGuardList(prefStore));
            it.putExtra(PREF_KEY_RECENT_SCOPE, Util.getIntOption(prefStore, PREF_KEY_RECENT_SCOPE, 60));
            it.putExtra(PREF_KEY_SHOW_MEM, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_MEM));
            it.putExtra(PREF_KEY_SHOW_PSS, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_PSS));
            it.putExtra(PREF_KEY_SHOW_CPU, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_CPU));
            it.putExtra(PREF_KEY_SHOW_SYS_PROC, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_SYS_PROC));
            it.putExtra(PREF_KEY_SHOW_SERVICE, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_SERVICE));
            it.putExtra(PREF_KEY_SHOW_KILL_WARN, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_KILL_WARN));
            it.putExtra(PREF_KEY_DISABLE_ROOT, Util.getBooleanOption(prefStore, PREF_KEY_DISABLE_ROOT, false));

            startActivityForResult(it, REQUEST_PREFERENCES);

            return true;
        } else if (action == MI_EXIT) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    Util.killSelf(handler, ProcessManager.this,
                            (ActivityManager) getSystemService(ACTIVITY_SERVICE), getPackageName());
                }
            };

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.prompt))
                    .setMessage(ResUtil.getString(this, R.string.exit_prompt))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();

            return true;
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (menuInfo == null) {
            return;
        }

        int pos = ((AdapterContextMenuInfo) menuInfo).position;

        if (pos < getListView().getCount()) {
            ProcessItem rap = (ProcessItem) getListView().getItemAtPosition(pos);

            menu.setHeaderTitle(rap.label == null ? rap.procInfo.processName : rap.label);

            menu.add(Menu.NONE, MI_DISPLAY, Menu.NONE, ResUtil.getString(this, R.string.switch_to));

            boolean useGuard = Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_ENABLE_GUARD, false);

            boolean nonend = ignoreList.contains(rap.procInfo.processName) || rap.sys
                            || (useGuard && !guardList.contains(rap.procInfo.processName));

            menu.add(Menu.NONE, MI_ENDTASK, Menu.NONE, ResUtil.getString(this, R.string.end_task)).setEnabled(!nonend);

            menu.add(Menu.NONE, MI_END_OTHERS, Menu.NONE, ResUtil.getString(this, R.string.end_others));

            boolean nonignore = ignoreList.contains(rap.procInfo.processName) || rap.sys;

            menu.add(Menu.NONE, MI_IGNORE, Menu.NONE, ResUtil.getString(this, R.string.ignore)).setEnabled(!nonignore);

            menu.add(Menu.NONE, MI_DETAILS, Menu.NONE, ResUtil.getString(this, R.string.details));

            boolean nonguard = guardList.contains(rap.procInfo.processName) || rap.sys;

            menu.add(Menu.NONE, MI_GUARD, Menu.NONE, ResUtil.getString(this, R.string.guard)).setEnabled(!nonguard);

            menu.add(Menu.NONE, MI_FORCE_STOP, Menu.NONE, ResUtil.getString(this, R.string.force_stop)).setEnabled(!rap.sys);

            menu.add(Menu.NONE, MI_VIEW_LOG, Menu.NONE, ResUtil.getString(this, R.string.view_logs));

            menu.add(Menu.NONE, MI_SEARCH, Menu.NONE, ResUtil.getString(this, R.string.search_market));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = ((AdapterContextMenuInfo) item.getMenuInfo()).position;

        if (pos < getListView().getCount()) {
            ProcessItem rap = (ProcessItem) getListView().getItemAtPosition(pos);

            if (item.getItemId() == MI_DISPLAY) {
                handleAction(rap, ACTION_SWITCH);

                return true;
            } else if (item.getItemId() == MI_ENDTASK) {
                handleAction(rap, ACTION_END);

                return true;
            } else if (item.getItemId() == MI_END_OTHERS) {
                handleAction(rap, ACTION_END_OTHERS);

                return true;
            } else if (item.getItemId() == MI_IGNORE) {
                handleAction(rap, ACTION_IGNORE);

                return true;
            } else if (item.getItemId() == MI_DETAILS) {
                handleAction(rap, ACTION_DETAILS);

                return true;
            } else if (item.getItemId() == MI_GUARD) {
                handleAction(rap, ACTION_GUARD);

                return true;
            } else if (item.getItemId() == MI_FORCE_STOP) {
                handleAction(rap, ACTION_FORCE_STOP);

                return true;
            } else if (item.getItemId() == MI_VIEW_LOG) {
                handleAction(rap, ACTION_VIEW_LOG);

                return true;
            } else if (item.getItemId() == MI_SEARCH) {
                handleAction(rap, ACTION_SEARCH);
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public SortHint getSort(boolean primary) {
        SortHint sh = new SortHint();

        sh.primary = primary;
        sh.sortByLabels =
                new String[]{ResUtil.getString(this, R.string.name),
                        ResUtil.getString(this, R.string.importance),
                        ResUtil.getString(this, R.string.memory_usage),
                        ResUtil.getString(this, R.string.cpu_usage),};

        if (primary) {
            sh.sortBy =
                    Util.getIntOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME);
            sh.ascending =
                    Util.getIntOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SORT_DIRECTION, ORDER_ASC) == ORDER_ASC;
        } else {
            sh.sortBy =
                    Util.getIntOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME);
            sh.ascending =
                    Util.getIntOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_DIRECTION,
                            ORDER_ASC) == ORDER_ASC;
        }

        return sh;
    }

    @Override
    public void updateSort(SortHint hint) {
        EditorState es = Util.beginEditOption(this, PROCESS_MANAGER_STORE_NAME);

        if (hint.primary) {
            Util.updateIntOption(es, PREF_KEY_SORT_ORDER_TYPE, hint.sortBy, ORDER_TYPE_NAME);
            Util.updateIntOption(es, PREF_KEY_SORT_DIRECTION, hint.ascending ? ORDER_ASC : ORDER_DESC,
                    ORDER_ASC);
        } else {
            Util.updateIntOption(es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, hint.sortBy, ORDER_TYPE_NAME);
            Util.updateIntOption(es, PREF_KEY_SECONDARY_SORT_DIRECTION, hint.ascending ? ORDER_ASC
                    : ORDER_DESC, ORDER_ASC);
        }

        Util.endEditOption(es);

        procCache.transientOrderType = ProcessCache.NONE;
        procCache.transientOrderDirection = ProcessCache.NONE;
        procCache.reOrder(this);

        handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 1, 0));
    }

    @Override
    public List<ToggleHint> getToggles() {
        List<ToggleHint> hints = new ArrayList<ToggleHint>();

        ToggleHint hint = new ToggleHint();
        hint.label = "MEM"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_memory_usage);
        hint.key = PREF_KEY_SHOW_MEM;
        hint.value = Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_MEM);
        hints.add(hint);

        if (supportPss()) {
            hint = new ToggleHint();
            hint.label = "PSS"; //$NON-NLS-1$
            hint.hint = ResUtil.getString(this, R.string.show_pss_memory);
            hint.key = PREF_KEY_SHOW_PSS;
            hint.value = Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_PSS);
            hints.add(hint);
        }

        hint = new ToggleHint();
        hint.label = "CPU"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_cpu_usage);
        hint.key = PREF_KEY_SHOW_CPU;
        hint.value = Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_CPU);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "SYS"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_sys_process);
        hint.key = PREF_KEY_SHOW_SYS_PROC;
        hint.value = Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_SYS_PROC);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "SVC"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_running_services);
        hint.key = PREF_KEY_SHOW_SERVICE;
        hint.value = Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_SERVICE);
        hints.add(hint);

        return hints;
    }

    @Override
    public void updateToggle(ToggleHint hint) {
        EditorState es = Util.beginEditOption(this, PROCESS_MANAGER_STORE_NAME);

        Util.updateBooleanOption(es, hint.key, hint.value, true);

        Util.endEditOption(es);

        handler.removeCallbacks(task);
        handler.removeMessages(MSG_INIT_OK);

        handler.post(task);
    }

    static ArrayList<String> getIgnoreList(SharedPreferences sp) {
        return getStringList(sp, PREF_KEY_IGNORE_LIST);
    }

    static ArrayList<String> getGuardList(SharedPreferences sp) {
        return getStringList(sp, PREF_KEY_GUARD_LIST);
    }

    static boolean guardEnabled(SharedPreferences sp) {
        if (sp != null) {
            return sp.getBoolean(PREF_KEY_ENABLE_GUARD, false);
        }
        return false;
    }

    static boolean supportPss() {
        return mtdGetProcessMemoryInfo != null;
    }

    static long getPercentage(long delta, long total) {
        if (total == 0) {
            return 0;
        }

        long pc = delta * 100 / total;

        if (pc == 0 && delta != 0) {
            pc = 1;
        }

        if (pc < 0) {
            pc = 0;
        }

        if (pc > 100) {
            pc = 100;
        }

        return pc;
    }

    private static String getImportanceDesc(int importance) {
        switch (importance) {
            case 50: // RunningAppProcessInfo.IMPORTANCE_PERSISTENT:
                return " (Persistent)"; //$NON-NLS-1$
            case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                return " (Foreground)"; //$NON-NLS-1$
            case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                return " (Visible)"; //$NON-NLS-1$
            case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                return " (Perceptible)"; //$NON-NLS-1$
            case 170: // RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE:
                return " (CantSaveState)"; //$NON-NLS-1$
            case RunningAppProcessInfo.IMPORTANCE_SERVICE:
                return " (Service)"; //$NON-NLS-1$
            case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
                return " (Background)"; //$NON-NLS-1$
            case RunningAppProcessInfo.IMPORTANCE_EMPTY:
                return " (Empty)"; //$NON-NLS-1$
        }

        return ""; //$NON-NLS-1$
    }

    private static String getOOMGroup(Context ctx, int oom) {
        if (oom == -17) {
            return "Keep Alive"; //$NON-NLS-1$
        } else if (oom >= -16 && oom <= 0) {
            return "Foreground"; //$NON-NLS-1$
        } else if (oom == 1) {
            return "Visible"; //$NON-NLS-1$
        } else if (oom == 2) {
            return "Secondary Server"; //$NON-NLS-1$
        } else if (oom >= 3 && oom <= 7) {
            return "Hidden"; //$NON-NLS-1$
        } else if (oom >= 8 && oom <= 14) {
            return "Content Provider"; //$NON-NLS-1$
        } else if (oom == 15) {
            return "Empty"; //$NON-NLS-1$
        }

        return ResUtil.getString(ctx, R.string.unknown);
    }

    private static ArrayList<String> getStringList(SharedPreferences sp, String prefKey) {
        if (sp == null) {
            return null;
        }

        String listVal = sp.getString(prefKey, null);

        if (listVal == null || listVal.length() == 0) {
            return null;
        }

        StringTokenizer tokenizer = new StringTokenizer(listVal);
        ArrayList<String> list = new ArrayList<String>();

        while (tokenizer.hasMoreTokens()) {
            list.add(tokenizer.nextToken());
        }

        return list.size() == 0 ? null : list;
    }

    void handleAction(final ProcessItem rap, int action) {
        switch (action) {
            case ACTION_END:

                if (!ignoreList.contains(rap.procInfo.processName) && !rap.sys) {
                    boolean useGuard =
                            Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_ENABLE_GUARD, false);

                    if (useGuard) {
                        // check guard list
                        if (!guardList.contains(rap.procInfo.processName)) {
                            break;
                        }
                    }

                    ActivityManager am =
                            (ActivityManager) ProcessManager.this.getSystemService(ACTIVITY_SERVICE);

                    String self = getPackageName();

                    if (self.equals(rap.procInfo.processName)) {
                        Util.killSelf(handler, ProcessManager.this, am, self);
                    } else {
                        endProcess(am, rap.procInfo.pkgList, self, useGuard);

                        handler.removeCallbacks(task);
                        handler.post(task);
                    }
                }

                break;
            case ACTION_END_OTHERS:

                endAllExcept(rap.procInfo.processName);

                break;
            case ACTION_SWITCH:

                String pkgName = rap.procInfo.processName;

                if (!pkgName.equals(this.getPackageName())) {
                    Util.launchPackage(this, pkgName, R.string.error_switch_task, true);
                }

                break;
            case ACTION_IGNORE:

                if (!ignoreList.contains(rap.procInfo.processName) && !rap.sys) {
                    ignoreList.add(rap.procInfo.processName);

                    EditorState es = Util.beginEditOption(this, PROCESS_MANAGER_STORE_NAME);
                    Util.updateStringListOption(es, PREF_KEY_IGNORE_LIST, ignoreList);
                    Util.endEditOption(es);

                    if (IGNORE_ACTION_HIDDEN == Util.getIntOption(this, PROCESS_MANAGER_STORE_NAME,
                            PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN)) {
                        handler.removeCallbacks(task);
                        handler.post(task);
                    }
                }

                break;
            case ACTION_DETAILS:

                String[] status = readProcStatus(rap.procInfo.pid);

                int oom = readProcessOOM(rap.procInfo.pid);

                StringBuffer sb =
                        new StringBuffer()
                                .append("<small>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.proc_name))
                                .append(": ") //$NON-NLS-1$
                                .append(rap.procInfo.processName)
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.pid))
                                .append(": ") //$NON-NLS-1$
                                .append(rap.procInfo.pid)
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.uid))
                                .append(": ") //$NON-NLS-1$
                                .append(status == null ? "" : status[1]) //$NON-NLS-1$
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.gid))
                                .append(": ") //$NON-NLS-1$
                                .append(status == null ? "" : status[2]) //$NON-NLS-1$
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.state))
                                .append(": ") //$NON-NLS-1$
                                .append(status == null ? "" : status[0]) //$NON-NLS-1$
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.threads))
                                .append(": ") //$NON-NLS-1$
                                .append(status == null ? "" : status[3]) //$NON-NLS-1$
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.started_time))
                                .append(": ") //$NON-NLS-1$
                                .append(readProcessStartedTime(this, rap.procInfo.pid, buf))
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.oom_priority))
                                .append(": ") //$NON-NLS-1$
                                .append(oom == UNKNOWN_OOM ? ResUtil.getString(this, R.string.unknown) : oom)
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.oom_group)).append(": ") //$NON-NLS-1$
                                .append(getOOMGroup(this, oom)).append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.importance)).append(": ") //$NON-NLS-1$
                                .append(rap.procInfo.importance).append(getImportanceDesc(rap.procInfo.importance))
                                .append("<br>LRU: ") //$NON-NLS-1$
                                .append(rap.procInfo.lru).append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.pkg_name)).append(": "); //$NON-NLS-1$

                if (rap.procInfo.pkgList != null) {
                    int i = 0;

                    for (String pkg : rap.procInfo.pkgList) {
                        if (pkg != null) {
                            if (i > 0) {
                                sb.append(", "); //$NON-NLS-1$
                            }
                            sb.append(pkg);
                            i++;
                        }
                    }
                }

                sb.append("</small>"); //$NON-NLS-1$

                Util.newAlertDialogBuilder(this)
                        .setTitle(rap.label == null ? rap.procInfo.processName : rap.label)
                        .setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setMessage(Html.fromHtml(sb.toString())).create().show();

                break;
            case ACTION_GUARD:

                if (!guardList.contains(rap.procInfo.processName) && !rap.sys) {
                    guardList.add(rap.procInfo.processName);

                    EditorState es = Util.beginEditOption(this, PROCESS_MANAGER_STORE_NAME);
                    Util.updateStringListOption(es, PREF_KEY_GUARD_LIST, guardList);
                    Util.endEditOption(es);
                }

                break;
            case ACTION_FORCE_STOP:

                if (!rap.sys) {
                    pkgName = resolvePackageName(this, rap.procInfo.processName);

                    if (pkgName != null) {
                        Util.showPackageDetails(this, pkgName);
                    } else {
                        Util.shortToast(this, R.string.force_stop_error);
                    }
                }

                break;
            case ACTION_VIEW_LOG:

                Intent it = new Intent(this, Util.getIntentProxyClz(LogViewer.class));
                it.putExtra(LogViewer.TARGET_PID, rap.procInfo.pid);
                it.putExtra(LogViewer.TARGET_TITLE, rap.label == null ? rap.procInfo.processName
                        : rap.label);

                startActivityForResult(it, REQUEST_INVOKE);

                break;
            case ACTION_SEARCH:

                it = new Intent(Intent.ACTION_VIEW);

                pkgName = resolvePackageName(this, rap.procInfo.processName);

                if (pkgName == null) {
                    pkgName = rap.procInfo.processName;
                }

                it.setData(Uri.parse("market://search?q=pname:" + pkgName)); //$NON-NLS-1$

                it = Intent.createChooser(it, null);

                Util.safeStartActivity(this, it, false);

                break;
            case ACTION_MENU:

                boolean useGuard =
                        Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_ENABLE_GUARD, false);

                boolean nonend =
                        ignoreList.contains(rap.procInfo.processName) || rap.sys
                                || (useGuard && !guardList.contains(rap.procInfo.processName));

                boolean nonignore = ignoreList.contains(rap.procInfo.processName) || rap.sys;

                boolean nonguard = guardList.contains(rap.procInfo.processName) || rap.sys;

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        // bypass the 'showMenu' action offset
                        int action = which + 1;

                        handleAction(rap, action);
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(rap.label == null ? rap.procInfo.processName : rap.label)
                        .setItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.switch_to),
                                        nonend ? Html.fromHtml("<font color=\"#848484\">" //$NON-NLS-1$
                                                + ResUtil.getString(this, R.string.end_task) + "</font>") //$NON-NLS-1$
                                                : ResUtil.getString(this, R.string.end_task),
                                        ResUtil.getString(this, R.string.end_others),
                                        nonignore ? Html.fromHtml("<font color=\"#848484\">" //$NON-NLS-1$
                                                + ResUtil.getString(this, R.string.ignore) + "</font>") //$NON-NLS-1$
                                                : ResUtil.getString(this, R.string.ignore),
                                        ResUtil.getString(this, R.string.details),
                                        nonguard ? Html.fromHtml("<font color=\"#848484\">" //$NON-NLS-1$
                                                + ResUtil.getString(this, R.string.guard) + "</font>") //$NON-NLS-1$
                                                : ResUtil.getString(this, R.string.guard),
                                        rap.sys ? Html.fromHtml("<font color=\"#848484\">" //$NON-NLS-1$
                                                + ResUtil.getString(this, R.string.force_stop) + "</font>") //$NON-NLS-1$
                                                : ResUtil.getString(this, R.string.force_stop),
                                        ResUtil.getString(this, R.string.view_logs),
                                        ResUtil.getString(this, R.string.search_market),}, listener).create().show();

                break;
        }
    }

    private static String resolvePackageName(Context ctx, String processName) {
        String pkgName = null;

        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(processName, 0);

            if (ai != null) {
                pkgName = processName;
            }
        } catch (NameNotFoundException e) {
            int idx = processName.indexOf(':');

            if (idx != -1) {
                String name = processName.substring(0, idx);

                try {
                    ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(name, 0);

                    if (ai != null) {
                        pkgName = name;
                    }
                } catch (NameNotFoundException e1) {
                    // ignore this exception
                }
            }
        }

        return pkgName;
    }

    private void endProcess(ActivityManager am, String[] pkgs, String self, boolean useGuard) {
        if (pkgs != null) {
            for (String pkg : pkgs) {
                if (pkg != null) {
                    int subKillType = Util.killable(pkg, self, ignoreList, useGuard, guardList);

                    if (subKillType == 0) {
                        am.restartPackage(pkg);
                    }
                }
            }
        }
    }

    void endAllExcept(String exception) {
        ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService(ACTIVITY_SERVICE);

        String self = this.getPackageName();

        ListView lstProcs = getListView();

        boolean useGuard =
                Util.getBooleanOption(this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_ENABLE_GUARD, false);

        // skip the dummy info
        for (int i = 1, size = lstProcs.getCount(); i < size; i++) {
            ProcessItem rap = (ProcessItem) lstProcs.getItemAtPosition(i);

            String procName = rap.procInfo.processName;

            if (!ignoreList.contains(procName) && !self.equals(procName) && !rap.sys
                    && !procName.equals(exception) && (!useGuard || guardList.contains(procName))) {
                endProcess(am, rap.procInfo.pkgList, self, useGuard);
            }
        }

        if (!ignoreList.contains(self) && !self.equals(exception)
                && (!useGuard || guardList.contains(self))) {
            Util.killSelf(handler, ProcessManager.this, am, self);
        } else {
            handler.removeCallbacks(task);
            handler.post(task);
        }
    }

    void refreshHeader() {
        TextView txt_head_mem = (TextView) findViewById(R.id.txt_head_mem);
        TextView txt_head_cpu = (TextView) findViewById(R.id.txt_head_cpu);

        boolean showMem =
                Util.getBooleanOption(ProcessManager.this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_MEM);
        boolean showCpu =
                Util.getBooleanOption(ProcessManager.this, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_CPU);

        StringBuilder totalString = null;

        if (showMem) {
            txt_head_mem.setVisibility(View.VISIBLE);

            if (procCache.transientOrderType == ORDER_TYPE_MEM) {
                if (procCache.transientOrderDirection == ORDER_ASC) {
                    txt_head_mem.setText("MEM↑"); //$NON-NLS-1$
                } else if (procCache.transientOrderDirection == ORDER_DESC) {
                    txt_head_mem.setText("MEM↓"); //$NON-NLS-1$
                } else {
                    txt_head_mem.setText("MEM"); //$NON-NLS-1$
                }
            } else {
                txt_head_mem.setText("MEM"); //$NON-NLS-1$
            }

            long[] mem = Util.getMemState(ProcessManager.this);

            if (mem != null) {
                totalString = new StringBuilder();
                totalString.append(ResUtil.getString(this, R.string.storage_summary,
                        Util.safeFormatFileSize(ProcessManager.this, mem[0]),
                        Util.safeFormatFileSize(ProcessManager.this, mem[2])));
            }
        } else {
            txt_head_mem.setVisibility(View.GONE);
        }

        if (showCpu) {
            txt_head_cpu.setVisibility(View.VISIBLE);

            if (procCache.transientOrderType == ORDER_TYPE_CPU) {
                if (procCache.transientOrderDirection == ORDER_ASC) {
                    txt_head_cpu.setText("CPU%↑"); //$NON-NLS-1$
                } else if (procCache.transientOrderDirection == ORDER_DESC) {
                    txt_head_cpu.setText("CPU%↓"); //$NON-NLS-1$
                } else {
                    txt_head_cpu.setText("CPU%"); //$NON-NLS-1$
                }
            } else {
                txt_head_cpu.setText("CPU%"); //$NON-NLS-1$
            }

            long uu = getPercentage(userDelta, totalDelta);

            long nu = getPercentage(niceDelta, totalDelta);

            long su = getPercentage(sysDelta, totalDelta);

            long iowu = getPercentage(iowDelta, totalDelta);

            long irqu = getPercentage(irqDelta, totalDelta);

            if (totalString == null) {
                totalString = new StringBuilder();
                totalString.append("User: ") //$NON-NLS-1$
                        .append(uu).append("% Nice: ") //$NON-NLS-1$
                        .append(nu).append("% Sys: ") //$NON-NLS-1$
                        .append(su).append("% IOW: ") //$NON-NLS-1$
                        .append(iowu).append("% IRQ: ") //$NON-NLS-1$
                        .append(irqu).append('%');
            } else {
                totalString.append('\n').append("User: ") //$NON-NLS-1$
                        .append(uu).append("% Nice: ") //$NON-NLS-1$
                        .append(nu).append("% Sys: ") //$NON-NLS-1$
                        .append(su).append("% IOW: ") //$NON-NLS-1$
                        .append(iowu).append("% IRQ: ") //$NON-NLS-1$
                        .append(irqu).append('%');
            }
        } else {
            txt_head_cpu.setVisibility(View.GONE);
        }

        View header = findViewById(R.id.list_head);

        if (totalString == null) {
            header.setVisibility(View.GONE);
        } else {
            TextView txt_head_total = (TextView) findViewById(R.id.txt_head_total);
            txt_head_total.setText(totalString);

            header.setVisibility(View.VISIBLE);
        }
    }

    void updateProcess(List<ProcessInfo> list, SparseArray<List<RunningServiceInfo>> serviceMap) {
        SharedPreferences prefStore =
                getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

        boolean showCpu = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_CPU);

        if (showCpu) {
            long[] loads = readCpuLoad();

            long newload = loads == null ? 0 : (loads[0] + loads[1]);
            if (totalLoad != 0) {
                totalDelta = newload - totalLoad;
            }
            totalLoad = newload;

            long newWork = loads == null ? 0 : loads[0];
            if (totalWork != 0) {
                workDelta = newWork - totalWork;
            }
            totalWork = newWork;

            long newUser = loads == null ? 0 : loads[2];
            if (totalUser != 0) {
                userDelta = newUser - totalUser;
            }
            totalUser = newUser;

            long newNice = loads == null ? 0 : loads[3];
            if (totalNice != 0) {
                niceDelta = newNice - totalNice;
            }
            totalNice = newNice;

            long newSys = loads == null ? 0 : loads[4];
            if (totalSys != 0) {
                sysDelta = newSys - totalSys;
            }
            totalSys = newSys;

            long newIow = loads == null ? 0 : loads[5];
            if (totalIow != 0) {
                iowDelta = newIow - totalIow;
            }
            totalIow = newIow;

            long newIrq = loads == null ? 0 : loads[6];
            if (totalIrq != 0) {
                irqDelta = newIrq - totalIrq;
            }
            totalIrq = newIrq;
        }

        synchronized (procCache) {
            procCache.procList.clear();

            if (list != null) {
                int ignoreAction =
                        Util.getIntOption(prefStore, PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN);
                boolean showMem = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_MEM);
                boolean showSys = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_SYS_PROC);
                boolean usePss = supportPss() && Util.getBooleanOption(prefStore, PREF_KEY_SHOW_PSS);

                String name;
                boolean isSys;

                for (int i = 0, size = list.size(); i < size; i++) {
                    ProcessInfo rap = list.get(i);

                    name = rap.processName;
                    isSys = Util.isSysProcess(name);

                    if (isSys && !showSys) {
                        continue;
                    }

                    if (ignoreAction == IGNORE_ACTION_HIDDEN && ignoreList.contains(name)) {
                        continue;
                    }

                    ProcessItem pi = procCache.getCache(name, rap.pid);

                    if (pi == null) {
                        pi = new ProcessItem();
                        pi.procInfo = rap;
                        pi.sys = isSys;
                    } else {
                        pi.procInfo = rap;
                        pi.sys = isSys;
                        pi.lastcputime = pi.cputime;
                    }

                    if (serviceMap != null) {
                        pi.services = serviceMap.get(rap.pid);
                    } else {
                        pi.services = null;
                    }

                    if (!usePss) {
                        pi.pss = 0;
                    }

                    pi.servicesLabel = null;

                    if (rap.pid != 0 && (showMem || showCpu)) {
                        readProcessStat(this, buf, pi, showMem, showCpu);
                    }

                    procCache.procList.add(pi);
                }

                if (showMem && usePss) {
                    final int[] pids = new int[procCache.procList.size()];
                    final String[] names = new String[pids.length];

                    for (int i = 0, size = pids.length; i < size; i++) {
                        ProcessItem pi = procCache.procList.get(i);
                        pids[i] = pi.procInfo.pid;
                        names[i] = pi.procInfo.processName;
                    }

                    new Thread("Pss Reader") { //$NON-NLS-1$

                        public void run() {
                            try {
                                Object result =
                                        mtdGetProcessMemoryInfo
                                                .invoke(getSystemService(Context.ACTIVITY_SERVICE), pids);

                                if (result != null && result.getClass().isArray() && Array.getLength(result) > 0
                                        && Array.get(result, 0) instanceof Debug.MemoryInfo) {
                                    boolean changed = false;

                                    synchronized (procCache) {
                                        for (int i = 0, size = Array.getLength(result); i < size; i++) {
                                            ProcessItem pi = procCache.getCache(names[i], pids[i]);

                                            if (pi != null) {
                                                Debug.MemoryInfo mi = (Debug.MemoryInfo) Array.get(result, i);

                                                long npss = mi.dalvikPss + mi.nativePss + mi.otherPss;

                                                if (npss != pi.pss || pi.mem == null) {
                                                    pi.pss = npss;

                                                    // pss value is in 1kb unit
                                                    pi.mem = Util.safeFormatFileSize(ProcessManager.this, pi.pss * 1024);

                                                    changed = true;
                                                }
                                            }
                                        }
                                    }

                                    if (changed) {
                                        handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 0, 0));
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
                            }
                        }

                        ;

                    }.start();
                }

                procCache.reOrder(this);
            }
        }
    }

    static List<ProcessInfo> getRunningProcessInfo(final Context ctx, boolean uiThread, boolean dialogAccess) {
        List<ProcessInfo> pis = null;

        if (Util.SDK_VER < 21) {
            // retrieve from activity manager
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> raps = am.getRunningAppProcesses();
            if (raps != null) {
                pis = new ArrayList<ProcessInfo>();
                for (RunningAppProcessInfo rap : raps) {
                    ProcessInfo pi = new ProcessInfo();
                    pi.processName = rap.processName;
                    pi.pid = rap.pid;
                    pi.pkgList = rap.pkgList;
                    pi.importance = rap.importance;
                    pi.lru = rap.lru;
                    pis.add(pi);
                }
            }
        } else if (Util.SDK_VER < 24) {
            // for Lollipop and Marshmallow (21,22,23), we need to read from local /proc folder due to new permission restriction
            pis = getLocalProcessInfo();
        } else if (Util.SDK_VER < 26) {
            // for Nougat(24,25), we read process info from RunningServices
            pis = getProcessInfoFromRunningService(ctx);
        } else {
            SharedPreferences prefStore = ctx.getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE);
            boolean disableRoot = Util.getBooleanOption(prefStore, PREF_KEY_DISABLE_ROOT, false);

            if (!disableRoot && RootUtil.rootAvailable()) {
                // use root to get process info
                pis = getLocalProcessInfoRoot(ctx);
            } else if (Util.isUsageStatsEnabled(ctx)) {
                // for Oreo and above (26+), we can only read process info from Package Usage Stats
                pis = getProcessInfoFromUsageStats(ctx);
            } else if (dialogAccess) {
                // ask for Usage Stats permission
                OnClickListener listener = new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Util.safeStartActivity(ctx, new Intent("android.settings.USAGE_ACCESS_SETTINGS"), false);
                    }
                };

                Util.newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.prompt))
                        .setMessage(ResUtil.getString(ctx, R.string.enable_usagestats_prompt))
                        .setPositiveButton(android.R.string.yes, listener)
                        .setNegativeButton(android.R.string.no, null).create().show();
            } else if (uiThread) {
                // ask for Usage Stats permission
                Util.longToast(ctx, ResUtil.getString(ctx, R.string.enable_usagestats_prompt2));
            }
        }

        return pis;
    }

    static List<ProcessInfo> getProcessInfoFromRunningService(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        List<RunningServiceInfo> svcs = am.getRunningServices(1000);
        if (svcs != null && svcs.size() > 0) {
            Map<Integer, ProcessInfo> pis = new HashMap();
            for (RunningServiceInfo rsi : svcs) {
                if (rsi.pid <= 0) {
                    continue;
                }

                if (pis.containsKey(rsi.pid)) {
                    ProcessInfo pi = pis.get(rsi.pid);
                    if (rsi.foreground && pi.importance != RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        pi.importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                    }
                } else {
                    ProcessInfo pi = new ProcessInfo();
                    pi.pid = rsi.pid;
                    pi.processName = rsi.process;
                    pi.pkgList = new String[]{pi.processName};
                    pi.importance = rsi.foreground ? RunningAppProcessInfo.IMPORTANCE_FOREGROUND : RunningAppProcessInfo.IMPORTANCE_BACKGROUND;

                    pis.put(pi.pid, pi);
                }
            }

            if (!pis.isEmpty()) {
                return new ArrayList<ProcessInfo>(pis.values());
            }
        }
        return null;
    }

    static List<ProcessInfo> getLocalProcessInfo() {
        File procFolder = new File("/proc"); //$NON-NLS-1$
        File[] procs = procFolder.listFiles(procFileFilter);
        if (procs != null) {
            List<ProcessInfo> pis = new ArrayList<ProcessInfo>();

            for (File f : procs) {
                try {
                    File cmdline = new File(f, "cmdline"); //$NON-NLS-1$
                    String pname = Util.readFileFirstLine(cmdline, 64);
                    if (!TextUtils.isEmpty(pname) && pname.charAt(0) != '/') {
                        int pid = Integer.parseInt(f.getName());

                        ProcessInfo pi = new ProcessInfo();
                        pi.pid = pid;
                        pi.processName = pname;
                        pi.pkgList = new String[]{pname};
                        pis.add(pi);
                    }
                } catch (Exception e) {
                    // continue
                }
            }

            return pis.size() == 0 ? null : pis;
        }
        return null;
    }

    static List<ProcessInfo> getLocalProcessInfoRoot(Context ctx) {
        PackageManager pm = ctx.getPackageManager();

        List<String> output = RootUtil.runRoot("ps -Ao PID,NAME");
        if (output != null && output.size() > 0) {

            Map<Integer, Integer> cpuUsage = new HashMap<Integer, Integer>();
            List<String> cpuOutput = RootUtil.runRoot("top -q -n 1 -o PID,%CPU");
            if (cpuOutput != null && cpuOutput.size() > 0) {
                for (String line : cpuOutput) {
                    line = line.trim();
                    if (line.length() > 0) {
                        char firstChar = line.charAt(0);
                        if (firstChar >= '0' && firstChar <= '9') {
                            int idx = line.indexOf(' ');
                            if (idx != -1) {
                                try {
                                    int pid = Integer.parseInt(line.substring(0, idx));
                                    if (pid >= 0) {
                                        float cpu = Float.parseFloat(line.substring(idx).trim());
                                        if (cpu >= 0) {
                                            cpuUsage.put(pid, Math.round(cpu));
                                        }
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }

            List<ProcessInfo> pis = new ArrayList<ProcessInfo>();

            String header = output.get(0);
            int nidx = header.indexOf("NAME");
            if (nidx != -1) {
                for (int i = 1; i < output.size(); i++) {
                    String line = output.get(i);
                    if (line.length() >= nidx) {
                        try {
                            int pid = Integer.parseInt(line.substring(0, nidx).trim());
                            if (pid >= 0) {
                                String pname = line.substring(nidx).trim();
                                if (!TextUtils.isEmpty(pname) && pname.charAt(0) != '/' && pname.charAt(0) != '[') {
                                    // try get android package info, only proceed if success
                                    pm.getPackageInfo(pname, 0);

                                    ProcessInfo pi = new ProcessInfo();
                                    pi.pid = pid;
                                    pi.processName = pname;
                                    pi.pkgList = new String[]{pname};
                                    if (cpuUsage.containsKey(pid)) {
                                        pi.cpuPercent = cpuUsage.get(pid);
                                    } else {
                                        pi.cpuPercent = -1;
                                    }
                                    pis.add(pi);
                                }
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
            return pis.size() == 0 ? null : pis;
        }
        return null;
    }

    static boolean needToUseUsageStats(Context ctx) {
        // oreo and above and not root
        return Util.SDK_VER >= 26 && !RootUtil.rootAvailable(); //&& !Util.isSysApp(ctx);
    }

    static List<ProcessInfo> getProcessInfoFromUsageStats(Context ctx) {
        try {
            Object usageStatsManager = ctx.getSystemService("usagestats"/*Context.USAGE_STATS_SERVICE*/);
            if (usageStatsManager != null) {
                Class clz = Class.forName("android.app.usage.UsageStatsManager");
                Method mtdQueryAndAggregateUsageStats = clz.getDeclaredMethod("queryAndAggregateUsageStats", long.class, long.class);

                SharedPreferences prefStore = ctx.getSharedPreferences(PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE);
                int recentMinutes = Util.getIntOption(prefStore, PREF_KEY_RECENT_SCOPE, 60);

                long now = System.currentTimeMillis();
                long begin = now - recentMinutes * 60 * 1000;

                Object result = mtdQueryAndAggregateUsageStats.invoke(usageStatsManager, begin, now);

                if (result instanceof Map) {
                    Class statClz = Class.forName("android.app.usage.UsageStats");
                    Method mtdGetLastTimeUsed = statClz.getDeclaredMethod("getLastTimeUsed");

                    List<ProcessInfo> pis = new ArrayList<ProcessInfo>();
                    String self = ctx.getPackageName();

                    for (Map.Entry<String, Object> entry : ((Map<String, Object>) result).entrySet()) {
                        ProcessInfo pi = new ProcessInfo();
                        pi.pid = -1;
                        pi.processName = String.valueOf(entry.getKey());
                        pi.pkgList = new String[]{pi.processName};
                        pi.importance = RunningAppProcessInfo.IMPORTANCE_BACKGROUND;

                        Object stat = entry.getValue();
                        Object lastTimeUsedResult = mtdGetLastTimeUsed.invoke(stat);
                        if (lastTimeUsedResult instanceof Long) {
                            long lastTimeUsed = ((Long) lastTimeUsedResult).longValue();
                            if (lastTimeUsed > System.currentTimeMillis() - 10000) {
                                pi.importance = RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE;
                            }
                        }

                        if (self.equals(pi.processName)) {
                            pi.importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                        }

                        pis.add(pi);
                    }

                    return pis;
                }
            }
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
        }
        return null;
    }

    /**
     * @return [[worktime, idletime],...]
     */
    static long[][] readAllCpuLoad(int count) {
        long[][] loads = new long[count][];

        List<String> loadLines = readCpuLoadLines();
        for (int i = 0; i < count; i++) {
            loads[i] = readCpuLoad("cpu" + i + ' ', loadLines); //$NON-NLS-1$
        }

        return loads;
    }

    /**
     * @return [worktime, idletime, usertime, nicetime, systime, iowtime, irqtime]
     */
    static long[] readCpuLoad() {
        return readCpuLoad("cpu ", readCpuLoadLines()); //$NON-NLS-1$
    }

    /**
     * @return [worktime, idletime, usertime, nicetime, systime, iowtime, irqtime]
     */
    private static long[] readCpuLoad(String magic, List<String> loadLines) {
        if (loadLines == null) {
            return null;
        }

        try {
            for (String line : loadLines) {
                if (line.startsWith(magic)) {
                    line = line.substring(magic.length()).trim();

                    StringTokenizer tokens = new StringTokenizer(line);

                    long totaltime = 0, idletime = 0;
                    long usertime = 0, nicetime = 0, systime = 0, iotime = 0, irqtime = 0;
                    int i = 0;
                    String tk;

                    while (tokens.hasMoreTokens() && i < 7) {
                        tk = tokens.nextToken();

                        if (i == 3) {
                            idletime = Long.parseLong(tk);
                        } else {
                            if (i == 0) {
                                usertime = Long.parseLong(tk);
                            } else if (i == 1) {
                                nicetime = Long.parseLong(tk);
                            } else if (i == 2) {
                                systime = Long.parseLong(tk);
                            } else if (i == 4) {
                                iotime = Long.parseLong(tk);
                            } else if (i == 5) {
                                irqtime = Long.parseLong(tk);
                            } else if (i == 6) {
                                irqtime += Long.parseLong(tk);
                            }

                            totaltime += Long.parseLong(tk);
                        }
                        i++;
                    }

                    return new long[]{totaltime, idletime, usertime, nicetime, systime, iotime, irqtime};
                }
            }
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage());
        }

        return null;
    }

    private static List<String> readCpuLoadLines() {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")), //$NON-NLS-1$
                    128);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("cpu")) {
                    lines.add(line);
                }
            }
        } catch (FileNotFoundException fe) {
            Log.e(ProcessManager.class.getName(), fe.getLocalizedMessage());

            if (RootUtil.rootAvailable()) {
                // try root read
                List<String> output = RootUtil.runRoot("cat /proc/stat");
                if (output != null && output.size() > 0) {
                    for (String line : output) {
                        if (line.startsWith("cpu")) {
                            lines.add(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        return lines;
    }

    private static void readProcessStat(Context ctx, byte[] buf, ProcessItem pi, boolean showMem,
                                        boolean showCpu) {
        if (pi.procInfo.pid < 0) {
            return;
        }

        InputStream is = null;
        try {
            is = new FileInputStream("/proc/" + pi.procInfo.pid + "/stat"); //$NON-NLS-1$

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            int len;

            while ((len = is.read(buf)) != -1) {
                output.write(buf, 0, len);
            }

            output.close();

            String line = output.toString();

            if (line != null) {
                line = line.trim();

                int idx = line.lastIndexOf(')');

                if (idx != -1) {
                    line = line.substring(idx + 1).trim();

                    StringTokenizer tokens = new StringTokenizer(line);

                    String rss = null;
                    String utime = null;
                    String stime = null;

                    long nrss;
                    int i = 0;
                    String tk;

                    // [11,12,21] for [utime,stime,rss]
                    while (tokens.hasMoreTokens()) {
                        tk = tokens.nextToken();

                        if (i == 11) {
                            utime = tk;
                        } else if (i == 12) {
                            stime = tk;
                        } else if (i == 21) {
                            rss = tk;
                        }

                        if (rss != null) {
                            break;
                        }

                        i++;
                    }

                    if (showCpu) {
                        if (utime != null) {
                            pi.cputime = Long.parseLong(utime);
                        }

                        if (stime != null) {
                            pi.cputime += Long.parseLong(stime);
                        }
                    }

                    if (showMem && rss != null && pi.pss == 0) {
                        // only when we don't have pss, we use rss

                        // rss value is in 4kb unit
                        nrss = Long.parseLong(rss);

                        if (pi.rss != nrss || pi.mem == null || pi.pss == 0) {
                            pi.rss = nrss;

                            pi.mem = Util.safeFormatFileSize(ctx, pi.rss * 4 * 1024);
                        }
                    }
                }
            }
        } catch (FileNotFoundException fe) {
            Log.e(ProcessManager.class.getName(), fe.getLocalizedMessage());
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    private static String readProcessStartedTime(Context ctx, int pid, byte[] buf) {
        if (pid < 0) {
            return ResUtil.getString(ctx, R.string.unknown);
        }

        String line = null;
        InputStream is = null;
        try {
            is = new FileInputStream("/proc/" + pid + "/stat"); //$NON-NLS-1$

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            int len;

            while ((len = is.read(buf)) != -1) {
                output.write(buf, 0, len);
            }

            output.close();

            line = output.toString();
        } catch (FileNotFoundException fe) {
            Log.e(ProcessManager.class.getName(), fe.getLocalizedMessage());

            if (RootUtil.rootAvailable()) {
                // try root read
                List<String> output = RootUtil.runRoot("cat /proc/" + pid + "/stat");
                if (output != null && output.size() > 0) {
                    line = output.get(0);
                }
            }
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        if (line != null) {
            try {
                line = line.trim();

                int idx = line.lastIndexOf(')');

                if (idx != -1) {
                    line = line.substring(idx + 1).trim();

                    StringTokenizer tokens = new StringTokenizer(line);

                    String starttime = null;

                    int i = 0;
                    String tk;

                    // [19] for [starttime]
                    while (tokens.hasMoreTokens()) {
                        tk = tokens.nextToken();

                        if (i == 19) {
                            starttime = tk;
                            break;
                        }

                        i++;
                    }

                    if (starttime != null) {
                        long sTime = Long.parseLong(starttime);

                        double upTime = Util.readFileDoubleFirstSection("/proc/uptime", //$NON-NLS-1$
                                false);

                        StringBuilder sb = new StringBuilder();

                        PropertiesViewer.formatElapsedTime(ctx, sb, (long) (upTime * 100 - sTime) / 100);

                        return sb.toString();
                    }
                }
            } catch (Exception e) {
                Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return ResUtil.getString(ctx, R.string.unknown);
    }

    private static int readProcessOOM(int pid) {
        if (pid < 0) {
            return UNKNOWN_OOM;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/" //$NON-NLS-1$
                    + pid + "/oom_adj")), //$NON-NLS-1$
                    8);

            String line = reader.readLine();

            if (line != null) {
                line = line.trim();

                return Integer.parseInt(line);
            }
        } catch (FileNotFoundException fe) {
            Log.e(ProcessManager.class.getName(), fe.getLocalizedMessage());

            if (RootUtil.rootAvailable()) {
                // try root read
                List<String> lines = RootUtil.runRoot("cat /proc/" + pid + "/oom_adj");
                if (lines != null && lines.size() > 0) {
                    try {
                        String line = lines.get(0).trim();
                        return Integer.parseInt(line);
                    } catch (Exception e) {
                        Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        return UNKNOWN_OOM;
    }

    /**
     * @return [State, UID, GID, Threads]
     */
    private static String[] readProcStatus(int pid) {
        if (pid < 0) {
            return null;
        }

        try {
            List<String> lines = new ArrayList<String>();

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/" //$NON-NLS-1$
                        + pid + "/status")), //$NON-NLS-1$
                        1024);

                String ln;
                while ((ln = reader.readLine()) != null) {
                    lines.add(ln);
                }
            } catch (FileNotFoundException fe) {
                Log.e(ProcessManager.class.getName(), fe.getLocalizedMessage());

                if (RootUtil.rootAvailable()) {
                    // try root read
                    lines = RootUtil.runRoot("cat /proc/" + pid + "/status");
                }
            } catch (Exception e) {
                Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ie) {
                        Log.e(ProcessManager.class.getName(), ie.getLocalizedMessage(), ie);
                    }
                }
            }

            if (lines == null || lines.size() == 0) {
                return null;
            }

            String stateMsg = ""; //$NON-NLS-1$
            String uidMsg = ""; //$NON-NLS-1$
            String gidMsg = ""; //$NON-NLS-1$
            String threadsMsg = ""; //$NON-NLS-1$

            for (String line : lines) {
                if (line.startsWith("State:")) //$NON-NLS-1$
                {
                    if (line.length() > 6) {
                        stateMsg = line.substring(6).trim();
                    }
                } else if (line.startsWith("Uid:")) //$NON-NLS-1$
                {
                    if (line.length() > 4) {
                        uidMsg = line.substring(4).trim();

                        int idx = uidMsg.indexOf('\t');
                        if (idx != -1) {
                            uidMsg = uidMsg.substring(0, idx);
                        } else {
                            idx = uidMsg.indexOf(' ');
                            if (idx != -1) {
                                uidMsg = uidMsg.substring(0, idx);
                            }
                        }
                    }
                } else if (line.startsWith("Gid:")) //$NON-NLS-1$
                {
                    if (line.length() > 4) {
                        gidMsg = line.substring(4).trim();

                        int idx = gidMsg.indexOf('\t');
                        if (idx != -1) {
                            gidMsg = gidMsg.substring(0, idx);
                        } else {
                            idx = gidMsg.indexOf(' ');
                            if (idx != -1) {
                                gidMsg = gidMsg.substring(0, idx);
                            }
                        }
                    }
                } else if (line.startsWith("Threads:")) //$NON-NLS-1$
                {
                    if (line.length() > 8) {
                        threadsMsg = line.substring(8).trim();
                    }
                }
            }

            return new String[]{stateMsg, uidMsg, gidMsg, threadsMsg};
        } catch (Exception e) {
            Log.e(ProcessManager.class.getName(), e.getLocalizedMessage(), e);
        }

        return null;
    }

    /**
     * ResourceUpdaterThread
     */
    private static final class ResourceUpdaterThread extends Thread {

        private Context ctx;
        private ProcessCache procCache;
        private Handler handler;

        volatile boolean aborted;

        ResourceUpdaterThread(Context ctx, ProcessCache procCache, Handler handler) {
            super("ProcessResourceUpdater"); //$NON-NLS-1$

            this.ctx = ctx;
            this.procCache = procCache;
            this.handler = handler;
        }

        static CharSequence getServicesLabel(PackageManager pm, List<RunningServiceInfo> svcs,
                                             ProcessCache cache) {
            if (svcs == null) {
                return null;
            }

            StringBuilder sb = new StringBuilder();

            boolean hasFormatting = false;

            for (int i = 0, size = svcs.size(); i < size; i++) {
                RunningServiceInfo rsi = svcs.get(i);

                if (rsi.service == null) {
                    continue;
                }

                String svcLabel = cache.getServiceLabel(rsi.service);

                if (svcLabel == null) {
                    try {
                        ServiceInfo si = pm.getServiceInfo(rsi.service, 0);

                        if (si != null && (si.labelRes != 0 || si.nonLocalizedLabel != null)) {
                            CharSequence label = si.loadLabel(pm);

                            if (label != null) {
                                svcLabel = label.toString();
                            }
                        }

                        if (svcLabel == null) {
                            svcLabel = rsi.service.getClassName();

                            int idx = svcLabel.lastIndexOf('.');
                            if (idx >= 0 && idx < (svcLabel.length() - 1)) {
                                svcLabel = svcLabel.substring(idx + 1);
                            }
                        }
                    } catch (NameNotFoundException e) {
                        // ignore
                    }

                    if (svcLabel == null) {
                        svcLabel = "?"; //$NON-NLS-1$
                    }

                    cache.putServiceLabel(rsi.service, svcLabel);
                }

                if (sb.length() > 0) {
                    sb.append('\n');
                }

                sb.append("- "); //$NON-NLS-1$

                if (rsi.foreground) {
                    hasFormatting = true;
                    sb.append("<font color=\"#00ff00\">") //$NON-NLS-1$
                            .append(svcLabel).append("</font>"); //$NON-NLS-1$
                } else {
                    sb.append(svcLabel);
                }
            }

            if (sb.length() > 0) {
                if (hasFormatting) {
                    return Html.fromHtml(sb.toString().replaceAll("\\n", "<br>")); //$NON-NLS-1$ //$NON-NLS-2$
                } else {
                    return sb.toString();
                }
            }

            return null;
        }

        public void run() {
            PackageManager pm = ctx.getPackageManager();

            boolean changed = false;

            ArrayList<ProcessItem> localList = procCache.generateLocalList();

            for (int i = 0, size = localList.size(); i < size; i++) {
                if (aborted) {
                    return;
                }

                ProcessItem proc = localList.get(i);

                String pname = proc.procInfo.processName;
                int pid = proc.procInfo.pid;

                if (procCache.getCache(pname, pid) != null) {
                    continue;
                }

                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pname, 0);

                    if (ai != null) {
                        CharSequence label = pm.getApplicationLabel(ai);

                        if (label != null) {
                            proc.label = label.toString();

                            changed = true;
                        }
                    }
                } catch (NameNotFoundException e) {
                    int idx = pname.indexOf(':');

                    if (idx != -1) {
                        String name = pname.substring(0, idx);

                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(name, 0);

                            if (ai != null) {
                                CharSequence label = pm.getApplicationLabel(ai);

                                if (label != null) {
                                    proc.label = label.toString() + pname.substring(idx);

                                    changed = true;
                                }
                            }
                        } catch (NameNotFoundException e1) {
                            // ignore this exception
                        }
                    }
                }

                procCache.putCache(pname, pid, proc);
            }

            if (changed) {
                // reorder by new names
                if (Util.getIntOption(ctx, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                        ORDER_TYPE_NAME) == ORDER_TYPE_NAME
                        || Util.getIntOption(ctx, PROCESS_MANAGER_STORE_NAME,
                        PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME) == ORDER_TYPE_NAME) {
                    procCache.reOrder(ctx);

                    handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 1, 0));
                } else {
                    handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 0, 0));
                }
            }

            changed = false;

            for (int i = 0, size = localList.size(); i < size; i++) {
                if (aborted) {
                    return;
                }

                ProcessItem proc = localList.get(i);

                String pname = proc.procInfo.processName;

                if (proc.icon != null) {
                    continue;
                }

                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pname, 0);

                    if (ai != null) {
                        try {
                            proc.icon = pm.getApplicationIcon(ai);

                            changed = true;
                        } catch (OutOfMemoryError oom) {
                            Log.e(ProcessManager.class.getName(), "OOM when loading icon: " //$NON-NLS-1$
                                    + ai.packageName, oom);
                        }
                    }
                } catch (NameNotFoundException e1) {
                    // ignore this exception
                }
            }

            if (changed) {
                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_DETAILS, 0, 0));
            }
        }
    }

    /**
     * ProcessSettings
     */
    public static class ProcessSettings extends PreferenceActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            super.onCreate(savedInstanceState);

            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));

            PreferenceCategory pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.preference));
            getPreferenceScreen().addPreference(pc);

            Preference perfInterval = new Preference(this);
            perfInterval.setKey(PREF_KEY_REFRESH_INTERVAL);
            perfInterval.setTitle(ResUtil.getString(this, R.string.update_speed));
            pc.addPreference(perfInterval);

            CheckBoxPreference perfShowMem = new CheckBoxPreference(this);
            perfShowMem.setKey(PREF_KEY_SHOW_MEM);
            perfShowMem.setTitle(ResUtil.getString(this, R.string.show_memory_usage));
            perfShowMem.setSummary(ResUtil.getString(this, R.string.show_memory_summary));
            pc.addPreference(perfShowMem);

            if (supportPss()) {
                CheckBoxPreference perfShowPss = new CheckBoxPreference(this);
                perfShowPss.setKey(PREF_KEY_SHOW_PSS);
                perfShowPss.setTitle(ResUtil.getString(this, R.string.show_pss_memory));
                perfShowPss.setSummary(ResUtil.getString(this, R.string.show_pss_summary));
                pc.addPreference(perfShowPss);
            }

            CheckBoxPreference perfShowCpu = new CheckBoxPreference(this);
            perfShowCpu.setKey(PREF_KEY_SHOW_CPU);
            perfShowCpu.setTitle(ResUtil.getString(this, R.string.show_cpu_usage));
            perfShowCpu.setSummary(ResUtil.getString(this, R.string.show_cpu_summary));
            pc.addPreference(perfShowCpu);

            CheckBoxPreference perfShowSys = new CheckBoxPreference(this);
            perfShowSys.setKey(PREF_KEY_SHOW_SYS_PROC);
            perfShowSys.setTitle(ResUtil.getString(this, R.string.show_sys_process));
            perfShowSys.setSummary(ResUtil.getString(this, R.string.show_sys_process_sum));
            pc.addPreference(perfShowSys);

            CheckBoxPreference perfShowService = new CheckBoxPreference(this);
            perfShowService.setKey(PREF_KEY_SHOW_SERVICE);
            perfShowService.setTitle(ResUtil.getString(this, R.string.show_running_services));
            perfShowService.setSummary(ResUtil.getString(this, R.string.show_running_services_sum));
            pc.addPreference(perfShowService);

            CheckBoxPreference perfKillWarn = new CheckBoxPreference(this);
            perfKillWarn.setKey(PREF_KEY_SHOW_KILL_WARN);
            perfKillWarn.setTitle(ResUtil.getString(this, R.string.end_task_warning));
            perfKillWarn.setSummary(ResUtil.getString(this, R.string.end_task_warning_sum));
            pc.addPreference(perfKillWarn);

            Preference perfDefaultAction = new Preference(this);
            perfDefaultAction.setKey(PREF_KEY_DEFAULT_TAP_ACTION);
            perfDefaultAction.setTitle(ResUtil.getString(this, R.string.default_tap_action));
            pc.addPreference(perfDefaultAction);

            if (needToUseUsageStats(this)) {
                Preference perfRecent = new Preference(this);
                perfRecent.setKey(PREF_KEY_RECENT_SCOPE);
                perfRecent.setTitle(ResUtil.getString(this, R.string.recent_usage_range));
                pc.addPreference(perfRecent);
            }

            if (Util.SDK_VER >= 26 && RootUtil.rootAvailable()) {
                CheckBoxPreference perfDiableRoot = new CheckBoxPreference(this);
                perfDiableRoot.setKey(PREF_KEY_DISABLE_ROOT);
                perfDiableRoot.setTitle(ResUtil.getString(this, R.string.disable_root));
                perfDiableRoot.setSummary(ResUtil.getString(this, R.string.disable_root_sum));
                pc.addPreference(perfDiableRoot);
            }

            pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.sort));
            getPreferenceScreen().addPreference(pc);

            Preference perfSortType = new Preference(this);
            perfSortType.setKey(PREF_KEY_SORT_ORDER_TYPE);
            perfSortType.setTitle(ResUtil.getString(this, R.string.prime_sort_type));
            pc.addPreference(perfSortType);

            Preference perfSortDirection = new Preference(this);
            perfSortDirection.setKey(PREF_KEY_SORT_DIRECTION);
            perfSortDirection.setTitle(ResUtil.getString(this, R.string.prime_sort_direction));
            pc.addPreference(perfSortDirection);

            Preference perfSecSortType = new Preference(this);
            perfSecSortType.setKey(PREF_KEY_SECONDARY_SORT_ORDER_TYPE);
            perfSecSortType.setTitle(ResUtil.getString(this, R.string.second_sort_type));
            pc.addPreference(perfSecSortType);

            Preference perfSecSortDirection = new Preference(this);
            perfSecSortDirection.setKey(PREF_KEY_SECONDARY_SORT_DIRECTION);
            perfSecSortDirection.setTitle(ResUtil.getString(this, R.string.second_sort_direction));
            pc.addPreference(perfSecSortDirection);

            pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.ignore));
            getPreferenceScreen().addPreference(pc);

            Preference perfIgnoreAction = new Preference(this);
            perfIgnoreAction.setKey(PREF_KEY_IGNORE_ACTION);
            perfIgnoreAction.setTitle(ResUtil.getString(this, R.string.ignored_as));
            pc.addPreference(perfIgnoreAction);

            Preference perfIgnoreList = new Preference(this);
            perfIgnoreList.setKey(PREF_KEY_IGNORE_LIST);
            perfIgnoreList.setTitle(ResUtil.getString(this, R.string.ignored_list));
            pc.addPreference(perfIgnoreList);

            pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.guard));
            getPreferenceScreen().addPreference(pc);

            CheckBoxPreference perfEnableWatch = new CheckBoxPreference(this);
            perfEnableWatch.setKey(PREF_KEY_ENABLE_GUARD);
            perfEnableWatch.setTitle(ResUtil.getString(this, R.string.enable_guard));
            perfEnableWatch.setSummary(ResUtil.getString(this, R.string.enable_guard_sum));
            pc.addPreference(perfEnableWatch);

            Preference perfGuardList = new Preference(this);
            perfGuardList.setKey(PREF_KEY_GUARD_LIST);
            perfGuardList.setTitle(ResUtil.getString(this, R.string.guard_list));
            pc.addPreference(perfGuardList);

            refreshInterval();
            refreshBooleanOption(PREF_KEY_SHOW_MEM, true);
            if (supportPss()) {
                refreshBooleanOption(PREF_KEY_SHOW_PSS, true);
            }
            refreshBooleanOption(PREF_KEY_SHOW_CPU, true);
            refreshBooleanOption(PREF_KEY_SHOW_SYS_PROC, true);
            refreshBooleanOption(PREF_KEY_SHOW_SERVICE, true);
            refreshBooleanOption(PREF_KEY_SHOW_KILL_WARN, true);
            refreshDefaultAction();
            refreshSortType(PREF_KEY_SORT_ORDER_TYPE);
            refreshSortDirection(PREF_KEY_SORT_DIRECTION);
            refreshSortType(PREF_KEY_SECONDARY_SORT_ORDER_TYPE);
            refreshSortDirection(PREF_KEY_SECONDARY_SORT_DIRECTION);
            refreshIgnoreAction();
            refreshProcessList(PREF_KEY_IGNORE_LIST, R.string.single_ignored, R.string.multi_ignored);
            refreshBooleanOption(PREF_KEY_ENABLE_GUARD, false);
            refreshProcessList(PREF_KEY_GUARD_LIST, R.string.single_guarded, R.string.multi_guarded);

            if (needToUseUsageStats(this)) {
                refreshRecentUsageRange();
            }

            if (Util.SDK_VER >= 26 && RootUtil.rootAvailable()) {
                refreshBooleanOption(PREF_KEY_DISABLE_ROOT, false);
            }

            setResult(RESULT_OK, getIntent());
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }

        void refreshRecentUsageRange() {
            int minutes = getIntent().getIntExtra(PREF_KEY_RECENT_SCOPE, 60);
            findPreference(PREF_KEY_RECENT_SCOPE).setSummary(getRecentUsageRangeText(minutes));
        }

        String getRecentUsageRangeText(int minutes) {
            int rm = minutes % 60;
            int hours = minutes / 60;

            StringBuilder summary = new StringBuilder();
            if (hours == 0) {
                if (rm == 1) {
                    summary.append("Past 1 minute");
                } else {
                    summary.append("Past ").append(rm).append(" minutes");
                }
            } else {
                if (hours == 1) {
                    summary.append("Past 1 Hour");
                } else {
                    summary.append("Past ").append(hours).append(" hours");
                }

                if (rm > 0) {
                    if (rm == 1) {
                        summary.append(" and 1 minute");
                    } else {
                        summary.append(" and ").append(rm).append(" minutes");
                    }
                }
            }
            return summary.toString();
        }

        void refreshInterval() {
            int interval = getIntent().getIntExtra(PREF_KEY_REFRESH_INTERVAL, REFRESH_NORMAL);

            CharSequence label = ResUtil.getString(this, R.string.normal);
            switch (interval) {
                case REFRESH_HIGH:
                    label = ResUtil.getString(this, R.string.high);
                    break;
                case REFRESH_LOW:
                    label = ResUtil.getString(this, R.string.low);
                    break;
                case REFRESH_PAUSED:
                    label = ResUtil.getString(this, R.string.paused);
                    break;
            }

            findPreference(PREF_KEY_REFRESH_INTERVAL).setSummary(label);
        }

        void refreshBooleanOption(String key, boolean defaultValue) {
            boolean val = getIntent().getBooleanExtra(key, defaultValue);

            ((CheckBoxPreference) findPreference(key)).setChecked(val);
        }

        void refreshDefaultAction() {
            int type = getIntent().getIntExtra(PREF_KEY_DEFAULT_TAP_ACTION, ACTION_MENU);

            String label = null;
            switch (type) {
                case ACTION_END:
                    label = ResUtil.getString(this, R.string.end_task);
                    break;
                case ACTION_END_OTHERS:
                    label = ResUtil.getString(this, R.string.end_others);
                    break;
                case ACTION_SWITCH:
                    label = ResUtil.getString(this, R.string.switch_to);
                    break;
                case ACTION_IGNORE:
                    label = ResUtil.getString(this, R.string.ignore);
                    break;
                case ACTION_DETAILS:
                    label = ResUtil.getString(this, R.string.details);
                    break;
                case ACTION_GUARD:
                    label = ResUtil.getString(this, R.string.guard);
                    break;
                case ACTION_FORCE_STOP:
                    label = ResUtil.getString(this, R.string.force_stop);
                    break;
                case ACTION_VIEW_LOG:
                    label = ResUtil.getString(this, R.string.view_logs);
                    break;
                case ACTION_SEARCH:
                    label = ResUtil.getString(this, R.string.search_market);
                    break;
                case ACTION_MENU:
                    label = ResUtil.getString(this, R.string.show_menu);
                    break;
            }

            findPreference(PREF_KEY_DEFAULT_TAP_ACTION).setSummary(label);
        }

        void refreshSortType(String key) {
            int type = getIntent().getIntExtra(key, ORDER_TYPE_NAME);

            String label = null;
            switch (type) {
                case ORDER_TYPE_NAME:
                    label = ResUtil.getString(this, R.string.name);
                    break;
                case ORDER_TYPE_IMPORTANCE:
                    label = ResUtil.getString(this, R.string.importance);
                    break;
                case ORDER_TYPE_MEM:
                    label = ResUtil.getString(this, R.string.memory_usage);
                    break;
                case ORDER_TYPE_CPU:
                    label = ResUtil.getString(this, R.string.cpu_usage);
                    break;
            }

            findPreference(key).setSummary(label);
        }

        void refreshSortDirection(String key) {
            int type = getIntent().getIntExtra(key, ORDER_ASC);

            String label =
                    ResUtil.getString(this, type == ORDER_ASC ? R.string.ascending : R.string.descending);

            findPreference(key).setSummary(label);
        }

        void refreshIgnoreAction() {
            int action = getIntent().getIntExtra(PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN);

            findPreference(PREF_KEY_IGNORE_ACTION).setSummary(
                    ResUtil.getString(this, action == IGNORE_ACTION_HIDDEN ? R.string.hidden
                            : R.string.protect));
        }

        void refreshProcessList(String prefKey, int singleResId, int multiResId) {
            ArrayList<String> list = getIntent().getStringArrayListExtra(prefKey);

            Preference pref = findPreference(prefKey);

            if (list == null || list.size() == 0) {
                pref.setSummary(ResUtil.getString(this, singleResId, 0));
                pref.setEnabled(false);
            } else {
                if (list.size() == 1) {
                    pref.setSummary(ResUtil.getString(this, singleResId, 1));
                } else {
                    pref.setSummary(ResUtil.getString(this, multiResId, list.size()));
                }

                pref.setEnabled(true);
            }
        }

        void editProcessList(final Intent it, final String prefKey, final int singleResId,
                             final int multiResId, int titleResId) {
            ArrayList<String> list = it.getStringArrayListExtra(prefKey);

            final boolean[] state = new boolean[list.size()];
            final String[][] items = new String[list.size()][2];

            PackageManager pm = getPackageManager();

            for (int i = 0, size = list.size(); i < size; i++) {
                String name = list.get(i);
                items[i][0] = name;
                items[i][1] = getProcessLabel(name, pm);
            }

            Arrays.sort(items, new Comparator<String[]>() {

                private Collator clt = Collator.getInstance();

                public int compare(String[] obj1, String[] obj2) {
                    return clt.compare(obj1[1], obj2[1]);
                }
            });

            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    ArrayList<String> nlist = new ArrayList<String>();

                    for (int i = 0, size = items.length; i < size; i++) {
                        if (!state[i]) {
                            nlist.add(items[i][0]);
                        }
                    }

                    if (nlist.size() == items.length) {
                        Util.shortToast(ProcessSettings.this, R.string.no_item_remove);
                    } else {
                        if (nlist.size() == 0) {
                            it.removeExtra(prefKey);
                        } else {
                            it.putStringArrayListExtra(prefKey, nlist);
                        }

                        dialog.dismiss();

                        refreshProcessList(prefKey, singleResId, multiResId);
                    }
                }
            };

            OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener() {

                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    state[which] = isChecked;
                }
            };

            String[] labels = new String[items.length];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = items[i][1];
            }

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, titleResId))
                    .setPositiveButton(ResUtil.getString(this, R.string.remove), listener)
                    .setNegativeButton(ResUtil.getString(this, R.string.close), null)
                    .setMultiChoiceItems(labels, state, multiListener).create().show();
        }

        private static String getProcessLabel(String name, PackageManager pm) {
            if (pm != null) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(name, 0);

                    if (ai != null) {
                        CharSequence label = pm.getApplicationLabel(ai);

                        if (label != null) {
                            name = label.toString();
                        }
                    }
                } catch (NameNotFoundException e) {
                    int idx = name.indexOf(':');

                    if (idx != -1) {
                        String prefix = name.substring(0, idx);

                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(prefix, 0);

                            if (ai != null) {
                                CharSequence label = pm.getApplicationLabel(ai);

                                if (label != null) {
                                    name = label.toString() + name.substring(idx);
                                }
                            }
                        } catch (NameNotFoundException e1) {
                            // ignore this exception
                        }
                    }
                }
            }

            return name;
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            final Intent it = getIntent();

            final String prefKey = preference.getKey();

            if (PREF_KEY_REFRESH_INTERVAL.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_REFRESH_INTERVAL, which);

                        dialog.dismiss();

                        refreshInterval();
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.update_speed))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.high),
                                        ResUtil.getString(this, R.string.normal),
                                        ResUtil.getString(this, R.string.low),
                                        ResUtil.getString(this, R.string.paused),},
                                it.getIntExtra(PREF_KEY_REFRESH_INTERVAL, REFRESH_NORMAL), listener).create()
                        .show();

                return true;
            } else if (PREF_KEY_SHOW_MEM.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_MEM,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_MEM)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_PSS.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_PSS,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_PSS)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_CPU.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_CPU,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_CPU)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_SYS_PROC.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_SYS_PROC,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_SYS_PROC)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_SERVICE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_SERVICE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_SERVICE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_KILL_WARN.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_KILL_WARN,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_KILL_WARN)).isChecked());

                return true;
            } else if (PREF_KEY_DISABLE_ROOT.equals(prefKey)) {
                it.putExtra(PREF_KEY_DISABLE_ROOT,
                        ((CheckBoxPreference) findPreference(PREF_KEY_DISABLE_ROOT)).isChecked());

                return true;
            } else if (PREF_KEY_DEFAULT_TAP_ACTION.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_DEFAULT_TAP_ACTION, which);

                        dialog.dismiss();

                        refreshDefaultAction();
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.default_tap_action))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.show_menu),
                                        ResUtil.getString(this, R.string.switch_to),
                                        ResUtil.getString(this, R.string.end_task),
                                        ResUtil.getString(this, R.string.end_others),
                                        ResUtil.getString(this, R.string.ignore),
                                        ResUtil.getString(this, R.string.details),
                                        ResUtil.getString(this, R.string.guard),
                                        ResUtil.getString(this, R.string.force_stop),
                                        ResUtil.getString(this, R.string.view_logs),
                                        ResUtil.getString(this, R.string.search_market),},
                                it.getIntExtra(PREF_KEY_DEFAULT_TAP_ACTION, ACTION_MENU), listener).create().show();

                return true;
            } else if (PREF_KEY_RECENT_SCOPE.equals(prefKey)) {
                View v = getLayoutInflater().inflate(R.layout.scope, null);

                final TextView txtInfo = (TextView) v.findViewById(R.id.txt_info);
                final SeekBar bar = (SeekBar) v.findViewById(R.id.bar_scope);
                bar.setMax(MAX_MINUTES - 1);

                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int minutes = progress + 1;
                        txtInfo.setText(getRecentUsageRangeText(minutes));
                    }
                });

                int minutes = it.getIntExtra(PREF_KEY_RECENT_SCOPE, 60);

                if (minutes < MIN_MINUTES) {
                    minutes = MIN_MINUTES;
                }

                if (minutes > MAX_MINUTES) {
                    minutes = MAX_MINUTES;
                }

                bar.setProgress(minutes - 1);
                txtInfo.setText(getRecentUsageRangeText(minutes));

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        int days = bar.getProgress() + 1;
                        it.putExtra(PREF_KEY_RECENT_SCOPE, days);
                        refreshRecentUsageRange();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.recent_usage_range))
                        .setView(v).setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).create().show();

                return true;
            } else if (PREF_KEY_SORT_ORDER_TYPE.equals(prefKey)
                    || PREF_KEY_SECONDARY_SORT_ORDER_TYPE.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(prefKey, which);

                        dialog.dismiss();

                        refreshSortType(prefKey);
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.sort_type))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.name),
                                        ResUtil.getString(this, R.string.importance),
                                        ResUtil.getString(this, R.string.memory_usage),
                                        ResUtil.getString(this, R.string.cpu_usage),},
                                it.getIntExtra(prefKey, ORDER_TYPE_NAME), listener).create().show();

                return true;
            } else if (PREF_KEY_SORT_DIRECTION.equals(prefKey)
                    || PREF_KEY_SECONDARY_SORT_DIRECTION.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(prefKey, which == 0 ? ORDER_ASC : ORDER_DESC);

                        dialog.dismiss();

                        refreshSortDirection(prefKey);
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.sort_direction))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.ascending),
                                        ResUtil.getString(this, R.string.descending),},
                                it.getIntExtra(prefKey, ORDER_ASC) == ORDER_ASC ? 0 : 1, listener).create().show();

                return true;
            } else if (PREF_KEY_IGNORE_ACTION.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_IGNORE_ACTION, which);

                        dialog.dismiss();

                        refreshIgnoreAction();
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.ignored_as))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.hidden),
                                        ResUtil.getString(this, R.string.protect),},
                                it.getIntExtra(PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN), listener).create()
                        .show();

                return true;
            } else if (PREF_KEY_IGNORE_LIST.equals(prefKey)) {
                editProcessList(it, PREF_KEY_IGNORE_LIST, R.string.single_ignored, R.string.multi_ignored,
                        R.string.ignored_list);

                return true;
            } else if (PREF_KEY_ENABLE_GUARD.equals(prefKey)) {
                it.putExtra(PREF_KEY_ENABLE_GUARD,
                        ((CheckBoxPreference) findPreference(PREF_KEY_ENABLE_GUARD)).isChecked());

                return true;
            } else if (PREF_KEY_GUARD_LIST.equals(prefKey)) {
                editProcessList(it, PREF_KEY_GUARD_LIST, R.string.single_guarded, R.string.multi_guarded,
                        R.string.guard_list);

                return true;
            }

            return false;
        }
    }

    /**
     * ProcViewHolder
     */
    private static final class ProcViewHolder {

        TextView txt_name, txt_mem, txt_cpu, txt_service;
        ImageView img_type;

        ProcViewHolder(View view) {
            img_type = (ImageView) view.findViewById(R.id.img_proc_icon);
            txt_name = (TextView) view.findViewById(R.id.txt_proc_name);
            txt_mem = (TextView) view.findViewById(R.id.txt_mem);
            txt_cpu = (TextView) view.findViewById(R.id.txt_cpu);
            txt_service = (TextView) view.findViewById(R.id.txt_service);
        }
    }

    /**
     * ProcessItem
     */
    private static final class ProcessItem {

        ProcessInfo procInfo;

        String label;

        Drawable icon;

        boolean sys;

        long rss;

        long pss;

        String mem;

        long cputime;

        long lastcputime;

        List<RunningServiceInfo> services;

        CharSequence servicesLabel;

        ProcessItem() {

        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ProcessItem)) {
                return false;
            }

            return this.procInfo.pid == ((ProcessItem) o).procInfo.pid;
        }
    }

    /**
     * ProcessInfo
     */
    static final class ProcessInfo {

        String processName;

        int pid;

        String[] pkgList;

        int importance;

        int lru;

        int cpuPercent = -1;

        ProcessInfo() {

        }
    }

    /**
     * ProcessCache
     */
    private static final class ProcessCache {

        static final int NONE = -2;

        /* value could be ProcessItem or SparseArray<ProcessItem> */
        private HashMap<String, Object> resCache;

        private HashMap<ComponentName, String> serviceLabelCache;

        ArrayList<ProcessItem> procList;

        int transientOrderType, transientOrderDirection;

        ProcessCache() {
            resCache = new HashMap<String, Object>();
            serviceLabelCache = new HashMap<ComponentName, String>();
            procList = new ArrayList<ProcessItem>();
            transientOrderType = NONE;
            transientOrderDirection = NONE;
        }

        synchronized String getServiceLabel(ComponentName cn) {
            return serviceLabelCache.get(cn);
        }

        synchronized void putServiceLabel(ComponentName cn, String label) {
            serviceLabelCache.put(cn, label);
        }

        ProcessItem getCache(String name, int pid) {
            Object cache = resCache.get(name);
            if (cache instanceof ProcessItem) {
                ProcessItem pi = (ProcessItem) cache;
                return pi.procInfo.pid == pid ? pi : null;
            } else if (cache instanceof SparseArray) {
                return ((SparseArray<ProcessItem>) cache).get(pid);
            }
            return null;
        }

        void putCache(String name, int pid, ProcessItem pi) {
            Object cache = resCache.get(name);

            if (cache == null) {
                resCache.put(name, pi);
            } else if (cache instanceof ProcessItem) {
                SparseArray<ProcessItem> buck = new SparseArray<ProcessItem>();
                resCache.put(name, buck);

                buck.put(((ProcessItem) cache).procInfo.pid, (ProcessItem) cache);
                buck.put(pid, pi);
            } else if (cache instanceof SparseArray) {
                SparseArray<ProcessItem> buck = (SparseArray<ProcessItem>) cache;
                buck.put(pid, pi);
            }
        }

        synchronized void clear() {
            resCache.clear();
            serviceLabelCache.clear();
            procList.clear();
            transientOrderType = NONE;
            transientOrderDirection = NONE;
        }

        synchronized ArrayList<ProcessItem> generateLocalList() {
            ArrayList<ProcessItem> local = new ArrayList<ProcessItem>();

            local.addAll(procList);

            return local;
        }

        synchronized void reOrder(Context ctx) {
            int type =
                    transientOrderType != NONE ? transientOrderType : Util.getIntOption(ctx,
                            PROCESS_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            int direction =
                    transientOrderDirection != NONE ? transientOrderDirection : Util.getIntOption(ctx,
                            PROCESS_MANAGER_STORE_NAME, PREF_KEY_SORT_DIRECTION, ORDER_ASC);

            int secondType =
                    Util.getIntOption(ctx, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME);
            int secondDirection =
                    Util.getIntOption(ctx, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_DIRECTION,
                            ORDER_ASC);
            boolean usePss =
                    supportPss() && Util.getBooleanOption(ctx, PROCESS_MANAGER_STORE_NAME, PREF_KEY_SHOW_PSS);

            Collections.sort(procList, new ProcessComparator(type, direction, secondType,
                    secondDirection, usePss));
        }
    }

    /**
     * ProcessComparator
     */
    private static final class ProcessComparator implements Comparator<ProcessItem> {

        int type, direction, secondType, secondDirection;
        boolean usePss;

        Collator clt = Collator.getInstance();

        ProcessComparator(int type, int direction, int secondType, int secondDirection, boolean usePss) {
            this.type = type;
            this.direction = direction;
            this.secondType = secondType;
            this.secondDirection = secondDirection;
            this.usePss = usePss;
        }

        public int compare(ProcessItem obj1, ProcessItem obj2) {
            int rlt = compare(clt, type, direction, obj1, obj2, usePss);

            if (rlt == 0) {
                rlt = compare(clt, secondType, secondDirection, obj1, obj2, usePss);
            }

            return rlt;
        }

        private static int compare(Collator clt, int type, int direction, ProcessItem obj1,
                                   ProcessItem obj2, boolean usePss) {
            switch (type) {
                case ORDER_TYPE_NAME:

                    String lb1 = obj1.label == null ? obj1.procInfo.processName : obj1.label;
                    String lb2 = obj2.label == null ? obj2.procInfo.processName : obj2.label;

                    return clt.compare(lb1, lb2) * direction;

                case ORDER_TYPE_IMPORTANCE:

                    // result should be reversed
                    return (obj2.procInfo.importance - obj1.procInfo.importance) * direction;

                case ORDER_TYPE_MEM:

                    if (usePss) {
                        return (obj1.pss == obj2.pss ? 0 : (obj1.pss < obj2.pss ? -1 : 1)) * direction;
                    }

                    return (obj1.rss == obj2.rss ? 0 : (obj1.rss < obj2.rss ? -1 : 1)) * direction;

                case ORDER_TYPE_CPU:

                    long c1 = obj1.lastcputime == 0 ? 0 : (obj1.cputime - obj1.lastcputime);
                    long c2 = obj2.lastcputime == 0 ? 0 : (obj2.cputime - obj2.lastcputime);
                    return (c1 == c2 ? 0 : (c1 < c2 ? -1 : 1)) * direction;
            }

            return 0;
        }
    }
}
