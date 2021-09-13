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
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
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
import android.widget.TextView;

import org.uguess.android.sysinfo.ToolMenuDialog.ActionHint;
import org.uguess.android.sysinfo.ToolMenuDialog.IActionMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.ISortMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.IToggleMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.SortHint;
import org.uguess.android.sysinfo.ToolMenuDialog.ToggleHint;
import org.uguess.android.sysinfo.Util.EditorState;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * NetStateManager
 */
public class NetStateManager extends ListActivity implements Constants, IActionMenuProvider,
        ISortMenuProvider, IToggleMenuProvider {

    private static final String PREF_KEY_REMOTE_QUERY = "remote_query"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_REMOTE_NAME = "show_remote_name"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_LOCAL_ADDRESS = "show_local_address"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_CONNECTION_SOURCE = "show_connection_src"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_SOURCE_TRAFFIC = "show_src_traffic"; //$NON-NLS-1$

    private static final int ORDER_TYPE_PROTO = 0;
    private static final int ORDER_TYPE_LOCAL = 1;
    private static final int ORDER_TYPE_REMOTE = 2;
    private static final int ORDER_TYPE_STATE = 3;
    private static final int ORDER_TYPE_SOURCE = 4;

    private static final int ENABLED = 0;
    private static final int DISABLED = 1;
    private static final int WIFI_ONLY = 2;

    private static final String[] SOCKET_STATES = new String[]{"ESTABLISHED", //$NON-NLS-1$
            "SYN_SENT", //$NON-NLS-1$
            "SYN_RECV", //$NON-NLS-1$
            "FIN_WAIT1", //$NON-NLS-1$
            "FIN_WAIT2", //$NON-NLS-1$
            "TIME_WAIT", //$NON-NLS-1$
            "CLOSE", //$NON-NLS-1$
            "CLOSE_WAIT", //$NON-NLS-1$
            "LAST_ACK", //$NON-NLS-1$
            "LISTEN", //$NON-NLS-1$
            "CLOSING" //$NON-NLS-1$
    };

    static final SparseArray<String> USER_MAP = new SparseArray<String>();

    static {
        USER_MAP.put(0, "root"); //$NON-NLS-1$
        USER_MAP.put(1000, "system"); //$NON-NLS-1$
        USER_MAP.put(1001, "radio"); //$NON-NLS-1$
        USER_MAP.put(1002, "bluetooth"); //$NON-NLS-1$
        USER_MAP.put(1003, "graphics"); //$NON-NLS-1$
        USER_MAP.put(1004, "input"); //$NON-NLS-1$
        USER_MAP.put(1005, "audio"); //$NON-NLS-1$
        USER_MAP.put(1006, "camera"); //$NON-NLS-1$
        USER_MAP.put(1007, "log"); //$NON-NLS-1$
        USER_MAP.put(1008, "compass"); //$NON-NLS-1$
        USER_MAP.put(1009, "mount"); //$NON-NLS-1$
        USER_MAP.put(1010, "wifi"); //$NON-NLS-1$
        USER_MAP.put(1011, "adb"); //$NON-NLS-1$
        USER_MAP.put(1012, "install"); //$NON-NLS-1$
        USER_MAP.put(1013, "media"); //$NON-NLS-1$
        USER_MAP.put(1014, "dhcp"); //$NON-NLS-1$
        USER_MAP.put(1015, "sdcard_rw"); //$NON-NLS-1$
        USER_MAP.put(1016, "vpn"); //$NON-NLS-1$
        USER_MAP.put(1017, "keystore"); //$NON-NLS-1$
        USER_MAP.put(1018, "usb"); //$NON-NLS-1$
        USER_MAP.put(1019, "drm"); //$NON-NLS-1$
        USER_MAP.put(1020, "available"); //$NON-NLS-1$
        USER_MAP.put(1021, "gps"); //$NON-NLS-1$
        USER_MAP.put(1022, "UNUSED1"); //$NON-NLS-1$
        USER_MAP.put(1023, "media_rw"); //$NON-NLS-1$
        USER_MAP.put(1024, "mtp"); //$NON-NLS-1$
        USER_MAP.put(1025, "nfc"); //$NON-NLS-1$
        USER_MAP.put(1026, "drmrpc"); //$NON-NLS-1$
        USER_MAP.put(2000, "shell"); //$NON-NLS-1$
        USER_MAP.put(2001, "cache"); //$NON-NLS-1$
        USER_MAP.put(2002, "diag"); //$NON-NLS-1$
        USER_MAP.put(3001, "net_bt_admin"); //$NON-NLS-1$
        USER_MAP.put(3002, "net_bt"); //$NON-NLS-1$
        USER_MAP.put(3003, "inet"); //$NON-NLS-1$
        USER_MAP.put(3004, "net_raw"); //$NON-NLS-1$
        USER_MAP.put(3005, "net_admin"); //$NON-NLS-1$
        USER_MAP.put(3006, "net_bw_stats"); //$NON-NLS-1$
        USER_MAP.put(3007, "net_bw_acct"); //$NON-NLS-1$
        USER_MAP.put(9998, "misc"); //$NON-NLS-1$
        USER_MAP.put(9999, "nobody"); //$NON-NLS-1$
    }

    HashMap<String, IpInfo> queryCache;

    HashMap<String, String> nameCache;

    ProgressDialog progress;

    volatile boolean aborted;

    Handler handler = new NetStateHandler(this);

    private static final class NetStateHandler extends Handler {

        private WeakReference<NetStateManager> acRef;

        NetStateHandler(NetStateManager ac) {
            acRef = new WeakReference<NetStateManager>(ac);
        }

        public void handleMessage(android.os.Message msg) {
            NetStateManager host = acRef.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_CONTENT_READY:

                    if (host.aborted) {
                        return;
                    }

                    showIpInfo((IpInfo) msg.obj, host);

                    this.removeCallbacks(host.task);
                    this.post(host.task);

                    break;
                case MSG_DISMISS_PROGRESS:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }
                    break;
            }
        }
    }

    Runnable task = new Runnable() {

        public void run() {
            refresh();

            int interval =
                    Util.getIntOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                            PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW);

            switch (interval) {
                case REFRESH_HIGH:
                    handler.postDelayed(this, 1000);
                    break;
                case REFRESH_NORMAL:
                    handler.postDelayed(this, 2000);
                    break;
                case REFRESH_LOW:
                    handler.postDelayed(this, 4000);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.net_lst_view);

        // now show ad in parent activity
        // ProUtil.toggleAD(this);

        ((TextView) findViewById(R.id.txt_proto_header)).setText(ResUtil.getString(this,
                R.string.protocol));
        ((TextView) findViewById(R.id.txt_ip_header)).setText(ResUtil.getString(this,
                R.string.local_remote_addr));
        ((TextView) findViewById(R.id.txt_state_header)).setText(ResUtil
                .getString(this, R.string.state));

        queryCache = new HashMap<String, IpInfo>();
        nameCache = new HashMap<String, String>();

        registerForContextMenu(getListView());

        getListView().setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int state =
                        Util.getIntOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                                PREF_KEY_REMOTE_QUERY, ENABLED);

                if (state == DISABLED) {
                    return;
                } else if (state == WIFI_ONLY) {
                    ConnectivityManager cm =
                            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

                    NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                    if (info == null || !info.isConnected()) {
                        return;
                    }
                }

                ConnectionItem itm = (ConnectionItem) parent.getItemAtPosition(position);

                String ip = getValidIP(itm.remote);

                if (!TextUtils.isEmpty(ip)) {
                    queryIPInfo(ip);
                } else {
                    Util.shortToast(NetStateManager.this, R.string.no_ip_info);
                }
            }
        });

        ArrayAdapter<ConnectionItem> adapter =
                new ArrayAdapter<ConnectionItem>(this, R.layout.net_item) {

                    public View getView(int position, View convertView,
                                        android.view.ViewGroup parent) {
                        View view;

                        if (convertView == null) {
                            view =
                                    NetStateManager.this.getLayoutInflater()
                                            .inflate(R.layout.net_item, parent, false);
                        } else {
                            view = convertView;
                        }

                        if (position >= getCount()) {
                            return view;
                        }

                        if (view.getTag() == null) {
                            view.setTag(new ConnectionViewHolder(view));
                        }

                        ConnectionViewHolder viewHolder = (ConnectionViewHolder) view.getTag();

                        ConnectionItem itm = getItem(position);

                        viewHolder.txt_proto.setText(itm.proto);
                        viewHolder.txt_state.setText(itm.state);

                        boolean showLocal =
                                Util.getBooleanOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                                        PREF_KEY_SHOW_LOCAL_ADDRESS);

                        viewHolder.txt_proto.setBackgroundResource("TCP".equals(itm.proto) ? R.drawable.bg_tcp //$NON-NLS-1$
                                : R.drawable.bg_udp);

                        if (showLocal) {
                            viewHolder.txt_ip.setText(itm.local + '\n'
                                    + (itm.remoteName == null ? itm.remote : itm.remoteName));
                        } else {
                            viewHolder.txt_ip.setText(itm.remoteName == null ? itm.remote : itm.remoteName);
                        }

                        boolean showSource =
                                Util.getBooleanOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                                        PREF_KEY_SHOW_CONNECTION_SOURCE);

                        if (!showSource || itm.uid < 0) {
                            viewHolder.procView.setVisibility(View.GONE);
                            viewHolder.procView2.setVisibility(View.GONE);
                        } else {
                            boolean sourcePrimary =
                                    Util.getIntOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                                            PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_SOURCE) == ORDER_TYPE_SOURCE;

                            StringBuilder sb = new StringBuilder();

                            PackageManager pm = getPackageManager();

                            String[] pkgs = pm.getPackagesForUid(itm.uid);

                            if (pkgs != null && pkgs.length > 0) {
                                for (String pkg : pkgs) {
                                    String s = nameCache.get(pkg);

                                    if (s == null) {
                                        s = pkg;

                                        try {
                                            ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);

                                            if (appInfo != null) {
                                                CharSequence label = pm.getApplicationLabel(appInfo);

                                                if (label != null) {
                                                    s = label.toString();

                                                    nameCache.put(pkg, s);
                                                }
                                            }
                                        } catch (NameNotFoundException e) {
                                            // ignore
                                        }
                                    }

                                    if (sb.length() > 0) {
                                        sb.append(", "); //$NON-NLS-1$
                                    }

                                    sb.append(s);
                                }
                            }

                            if (sb.length() == 0) {
                                String uname = USER_MAP.get(itm.uid);

                                sb.append(itm.uid).append('(').append(uname == null ? "?" : uname) //$NON-NLS-1$
                                        .append(')');
                            }

                            if (sourcePrimary) {
                                boolean showTraffic =
                                        Util.getBooleanOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                                                PREF_KEY_SHOW_SOURCE_TRAFFIC);

                                if (showTraffic) {
                                    long tcpSnd = Util.readFileLong("/proc/uid_stat/" //$NON-NLS-1$
                                                    + itm.uid + "/tcp_snd", //$NON-NLS-1$
                                            true);
                                    long udpSnd = Util.readFileLong("/proc/uid_stat/" //$NON-NLS-1$
                                                    + itm.uid + "/udp_snd", //$NON-NLS-1$
                                            true);

                                    long tx = 0;
                                    if (tcpSnd > 0) {
                                        tx += tcpSnd;
                                    }
                                    if (udpSnd > 0) {
                                        tx += udpSnd;
                                    }

                                    long tcpRcv = Util.readFileLong("/proc/uid_stat/" //$NON-NLS-1$
                                                    + itm.uid + "/tcp_rcv", //$NON-NLS-1$
                                            true);
                                    long udpRcv = Util.readFileLong("/proc/uid_stat/" //$NON-NLS-1$
                                                    + itm.uid + "/udp_rcv", //$NON-NLS-1$
                                            true);

                                    long rx = 0;
                                    if (tcpRcv > 0) {
                                        rx += tcpRcv;
                                    }
                                    if (udpRcv > 0) {
                                        rx += udpRcv;
                                    }

                                    if (tx > 0 || rx > 0) {
                                        viewHolder.txtTraffic.setText("Tx: " //$NON-NLS-1$
                                                + Util.safeFormatFileSize(NetStateManager.this, tx) + ", Rx: " //$NON-NLS-1$
                                                + Util.safeFormatFileSize(NetStateManager.this, rx));

                                        viewHolder.txtTraffic.setVisibility(View.VISIBLE);
                                    } else {
                                        viewHolder.txtTraffic.setVisibility(View.GONE);
                                    }
                                } else {
                                    viewHolder.txtTraffic.setVisibility(View.GONE);
                                }

                                viewHolder.txtProc.setText(sb.toString());
                                viewHolder.procView.setVisibility(View.VISIBLE);

                                viewHolder.procView2.setVisibility(View.GONE);
                            } else {
                                viewHolder.procView.setVisibility(View.GONE);

                                viewHolder.txtSrc.setText(sb.toString());
                                viewHolder.procView2.setVisibility(View.VISIBLE);
                            }
                        }

                        return view;
                    }
                };

        getListView().setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        ((ArrayAdapter<ConnectionItem>) getListView().getAdapter()).clear();

        queryCache.clear();
        nameCache.clear();

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
    protected void onStop() {
        if (progress != null) {
            Util.safeDismissDialog(progress);
            progress = null;
        }

        super.onStop();
    }

    @Override
    protected void onResume() {
        aborted = false;

        super.onResume();

        handler.post(task);
    }

    @Override
    protected void onPause() {
        aborted = true;

        handler.removeCallbacks(task);
        handler.removeMessages(MSG_CONTENT_READY);

        super.onPause();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MI_LIVE_MONITOR).setEnabled(hasNetConnection());

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi =
                menu.add(Menu.NONE, MI_REFRESH, Menu.NONE, ResUtil.getString(this, R.string.refresh));
        mi.setIcon(android.R.drawable.ic_menu_rotate);

        mi =
                menu.add(Menu.NONE, MI_LIVE_MONITOR, Menu.NONE,
                        ResUtil.getString(this, R.string.live_monitor));
        mi.setIcon(android.R.drawable.ic_menu_share);

        mi =
                menu.add(Menu.NONE, MI_PREFERENCE, Menu.NONE, ResUtil.getString(this, R.string.preference));
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

        if (hasNetConnection()) {
            hints.add(new ActionHint(ResUtil.getString(this, R.string.live_monitor), MI_LIVE_MONITOR));
        }

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
            startService(new Intent(getApplicationContext(), PopService.class).putExtra(
                    StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_WIFI).setData(
                    Uri.parse("target://" //$NON-NLS-1$
                            + StatusUpdaterService.TARGET_WIFI)));

            return true;
        } else if (action == MI_PREFERENCE) {
            Intent it = new Intent(this, Util.getIntentProxyClz(NetStateSettings.class));

            SharedPreferences prefStore =
                    getSharedPreferences(NETSTATE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            it.putExtra(PREF_KEY_REFRESH_INTERVAL,
                    Util.getIntOption(prefStore, PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW));
            it.putExtra(PREF_KEY_REMOTE_QUERY,
                    Util.getIntOption(prefStore, PREF_KEY_REMOTE_QUERY, ENABLED));
            it.putExtra(PREF_KEY_SHOW_REMOTE_NAME,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_REMOTE_NAME));
            it.putExtra(PREF_KEY_SHOW_LOCAL_ADDRESS,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_LOCAL_ADDRESS));
            it.putExtra(PREF_KEY_SHOW_CONNECTION_SOURCE,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_CONNECTION_SOURCE));
            it.putExtra(PREF_KEY_SHOW_SOURCE_TRAFFIC,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_SOURCE_TRAFFIC));
            it.putExtra(PREF_KEY_SORT_ORDER_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_SOURCE));
            it.putExtra(PREF_KEY_SORT_DIRECTION,
                    Util.getIntOption(prefStore, PREF_KEY_SORT_DIRECTION, ORDER_ASC));
            it.putExtra(PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_PROTO));
            it.putExtra(PREF_KEY_SECONDARY_SORT_DIRECTION,
                    Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC));

            startActivityForResult(it, REQUEST_PREFERENCES);

            return true;
        } else if (action == MI_EXIT) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    Util.killSelf(handler, NetStateManager.this,
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
        menu.setHeaderTitle(ResUtil.getString(this, R.string.actions));
        menu.add(ResUtil.getString(this, R.string.copy_ip));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = ((AdapterContextMenuInfo) item.getMenuInfo()).position;

        if (pos < getListView().getCount()) {
            ConnectionItem itm = (ConnectionItem) getListView().getItemAtPosition(pos);

            if (itm != null && !TextUtils.isEmpty(itm.remote)) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

                if (cm != null) {
                    cm.setText(itm.remoteName == null ? itm.remote : itm.remoteName);

                    Util.shortToast(this, R.string.copied_hint);
                }
            }

            return true;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PREFERENCES && data != null) {
            EditorState es = Util.beginEditOption(this, NETSTATE_MANAGER_STORE_NAME);

            Util.updateIntOption(data, es, PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW);
            Util.updateIntOption(data, es, PREF_KEY_REMOTE_QUERY, ENABLED);
            Util.updateIntOption(data, es, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_SOURCE);
            Util.updateIntOption(data, es, PREF_KEY_SORT_DIRECTION, ORDER_ASC);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_PROTO);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_REMOTE_NAME);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_LOCAL_ADDRESS);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_CONNECTION_SOURCE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_SOURCE_TRAFFIC);

            Util.endEditOption(es);
        }
    }

    @Override
    public SortHint getSort(boolean primary) {
        SortHint sh = new SortHint();

        sh.primary = primary;
        sh.sortByLabels =
                new String[]{ResUtil.getString(this, R.string.protocol),
                        ResUtil.getString(this, R.string.local_address),
                        ResUtil.getString(this, R.string.remote_address),
                        ResUtil.getString(this, R.string.state), ResUtil.getString(this, R.string.source),};

        if (primary) {
            sh.sortBy =
                    Util.getIntOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                            ORDER_TYPE_SOURCE);
            sh.ascending =
                    Util.getIntOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SORT_DIRECTION, ORDER_ASC) == ORDER_ASC;
        } else {
            sh.sortBy =
                    Util.getIntOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                            ORDER_TYPE_PROTO);
            sh.ascending =
                    Util.getIntOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_DIRECTION,
                            ORDER_ASC) == ORDER_ASC;
        }

        return sh;
    }

    @Override
    public void updateSort(SortHint hint) {
        EditorState es = Util.beginEditOption(this, NETSTATE_MANAGER_STORE_NAME);

        if (hint.primary) {
            Util.updateIntOption(es, PREF_KEY_SORT_ORDER_TYPE, hint.sortBy, ORDER_TYPE_SOURCE);
            Util.updateIntOption(es, PREF_KEY_SORT_DIRECTION, hint.ascending ? ORDER_ASC : ORDER_DESC,
                    ORDER_ASC);
        } else {
            Util.updateIntOption(es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, hint.sortBy, ORDER_TYPE_PROTO);
            Util.updateIntOption(es, PREF_KEY_SECONDARY_SORT_DIRECTION, hint.ascending ? ORDER_ASC
                    : ORDER_DESC, ORDER_ASC);
        }

        Util.endEditOption(es);

        refresh();
    }

    @Override
    public List<ToggleHint> getToggles() {
        List<ToggleHint> hints = new ArrayList<ToggleHint>();

        ToggleHint hint = new ToggleHint();
        hint.label = "RNM"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.display_remote_name);
        hint.key = PREF_KEY_SHOW_REMOTE_NAME;
        hint.value =
                Util.getBooleanOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SHOW_REMOTE_NAME);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "LCA"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_local_addr);
        hint.key = PREF_KEY_SHOW_LOCAL_ADDRESS;
        hint.value =
                Util.getBooleanOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SHOW_LOCAL_ADDRESS);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "CNS"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_conn_src);
        hint.key = PREF_KEY_SHOW_CONNECTION_SOURCE;
        hint.value =
                Util.getBooleanOption(this, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SHOW_CONNECTION_SOURCE);
        hints.add(hint);

        return hints;
    }

    @Override
    public void updateToggle(ToggleHint hint) {
        EditorState es = Util.beginEditOption(this, NETSTATE_MANAGER_STORE_NAME);

        Util.updateBooleanOption(es, hint.key, hint.value, true);

        Util.endEditOption(es);

        handler.removeCallbacks(task);
        handler.removeMessages(MSG_CONTENT_READY);

        handler.post(task);
    }

    private boolean hasNetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkInfo nif = cm.getActiveNetworkInfo();

            if (nif != null && nif.isConnected()) {
                return true;
            }
        }

        return false;
    }

    static String getValidIP(String ip) {
        if (ip != null) {
            int idx = ip.lastIndexOf(':');

            if (idx != -1) {
                ip = ip.substring(0, idx).trim();
            }

            if (!"0.0.0.0".equals(ip) && !"127.0.0.1".equals(ip)) //$NON-NLS-1$ //$NON-NLS-2$
            {
                return ip;
            }
        }

        return null;
    }

    void queryIPInfo(final String ip) {
        IpInfo info = queryCache.get(ip);

        if (info != null) {
            handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, info));
            return;
        }

        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.query_ip_msg));
        progress.setIndeterminate(true);
        progress.show();

        new Thread(new Runnable() {

            public void run() {
                IpInfo info = new IpInfo();
                info.ip = ip;

                info = getIpInfo(info);

                queryCache.put(ip, info);

                handler.sendEmptyMessage(MSG_DISMISS_PROGRESS);

                handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, info));
            }
        }).start();
    }

    protected IpInfo getIpInfo(IpInfo info) {
        return getIpInfoDirect(info);
    }

    static boolean isProxiedNetwork(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ani = cm.getActiveNetworkInfo();

        return ani != null && "PROXY".equals(ani.getTypeName());
    }

    void refresh() {
        boolean showLocal =
                Util.getBooleanOption(NetStateManager.this, NETSTATE_MANAGER_STORE_NAME,
                        PREF_KEY_SHOW_LOCAL_ADDRESS);

        ((TextView) findViewById(R.id.txt_ip_header)).setText(ResUtil.getString(NetStateManager.this,
                showLocal ? R.string.local_remote_addr : R.string.remote_address));

        ArrayList<ConnectionItem> items = readStatesRaw();

        if (items != null) {
            SharedPreferences prefStore =
                    getSharedPreferences(NETSTATE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            int primarySort = Util.getIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_SOURCE);

            Collections.sort(
                    items,
                    new ConnectionComparator(primarySort, Util.getIntOption(prefStore,
                            PREF_KEY_SORT_DIRECTION, ORDER_ASC), Util.getIntOption(prefStore,
                            PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_PROTO), Util.getIntOption(prefStore,
                            PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC)));

            if (primarySort == ORDER_TYPE_SOURCE) {
                // group items by source
                int lastUid = -1;

                for (ConnectionItem ci : items) {
                    if (ci.uid == lastUid) {
                        ci.uid = -1;
                    } else {
                        lastUid = ci.uid;
                    }
                }
            }
        }

        ArrayAdapter<ConnectionItem> adapter =
                (ArrayAdapter<ConnectionItem>) getListView().getAdapter();

        adapter.setNotifyOnChange(false);

        adapter.clear();

        if (items != null) {
            for (int i = 0, size = items.size(); i < size; i++) {
                adapter.add(items.get(i));
            }
        }

        adapter.notifyDataSetChanged();

        if (adapter.getCount() <= 1) {
            Log.d(NetStateManager.class.getName(), "No network traffic detected"); //$NON-NLS-1$
        }
    }

    private ArrayList<ConnectionItem> readStatesRaw() {
        ArrayList<ConnectionItem> items = new ArrayList<ConnectionItem>();

        parseRawData(items, this, queryCache, "TCP", "/proc/net/tcp", false); //$NON-NLS-1$ //$NON-NLS-2$
        parseRawData(items, this, queryCache, "UDP", "/proc/net/udp", true); //$NON-NLS-1$ //$NON-NLS-2$
        parseRawData(items, this, queryCache, "TCP", "/proc/net/tcp6", false); //$NON-NLS-1$ //$NON-NLS-2$
        parseRawData(items, this, queryCache, "UDP", "/proc/net/udp6", true); //$NON-NLS-1$ //$NON-NLS-2$

        return items;
    }

    private static void parseRawData(final ArrayList<ConnectionItem> items, Context ctx,
                                     HashMap<String, IpInfo> queryCache, String proto, String source, boolean ignoreState) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(source)), 2048);

            boolean first = true;
            int localOffset = -1, remOffset = -1, stateOffset = -1, stateEndOffset = -1, uidEndOffset =
                    -1;
            String line;

            boolean showRemoteName =
                    Util.getBooleanOption(ctx, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SHOW_REMOTE_NAME);
            boolean showSource =
                    Util.getBooleanOption(ctx, NETSTATE_MANAGER_STORE_NAME, PREF_KEY_SHOW_CONNECTION_SOURCE);

            String remoteIp;
            int portIdx;
            IpInfo remoteInfo;

            while ((line = reader.readLine()) != null) {
                if (first) {
                    localOffset = line.indexOf("local_address"); //$NON-NLS-1$
                    remOffset = line.indexOf("rem_address"); //$NON-NLS-1$
                    if (remOffset == -1) {
                        remOffset = line.indexOf("remote_address"); //$NON-NLS-1$
                    }
                    stateOffset = line.indexOf("st", remOffset); //$NON-NLS-1$
                    stateEndOffset = line.indexOf(' ', stateOffset);

                    uidEndOffset = line.indexOf("uid", stateOffset); //$NON-NLS-1$
                    if (uidEndOffset != -1) {
                        uidEndOffset += 2;
                    }

                    if (localOffset == -1 || remOffset == -1 || stateOffset == -1 || stateEndOffset == -1) {
                        Log.e(NetStateManager.class.getName(), "Unexpected " //$NON-NLS-1$
                                + proto + " header format: " //$NON-NLS-1$
                                + line);

                        break;
                    }

                    first = false;
                } else {
                    ConnectionItem ci = new ConnectionItem();

                    ci.proto = proto;

                    ci.local = parseRawIP(line.substring(localOffset, remOffset).trim());

                    ci.remote = parseRawIP(line.substring(remOffset, stateOffset).trim());

                    if (showRemoteName) {
                        remoteIp = getValidIP(ci.remote);

                        if (remoteIp != null) {
                            remoteInfo = queryCache.get(remoteIp);

                            if (remoteInfo != null && !TextUtils.isEmpty(remoteInfo.host)) {
                                portIdx = ci.remote.lastIndexOf(':');

                                if (portIdx != -1) {
                                    ci.remoteName = remoteInfo.host + ci.remote.substring(portIdx);
                                } else {
                                    ci.remoteName = remoteInfo.host;
                                }
                            }
                        }
                    }

                    if (!ignoreState) {
                        int st = Integer.parseInt(line.substring(stateOffset, stateEndOffset).trim(), 16);

                        ci.state = "Unknown"; //$NON-NLS-1$

                        if (st > 0 && st <= SOCKET_STATES.length) {
                            ci.state = SOCKET_STATES[st - 1];
                        }
                    }

                    if (showSource && uidEndOffset != -1) {
                        int start = line.lastIndexOf(' ', uidEndOffset);

                        if (start != -1) {
                            try {
                                ci.uid = Integer.parseInt(line.substring(start + 1, uidEndOffset + 1));
                            } catch (Exception e) {
                                ci.uid = -1;

                                Log.e(NetStateManager.class.getName(), "Parsing UID error: " + line); //$NON-NLS-1$
                            }
                        }
                    }

                    items.add(ci);
                }
            }
        } catch (FileNotFoundException fe) {
            Log.d(NetStateManager.class.getName(), "File not found: " + fe.getLocalizedMessage()); //$NON-NLS-1$
        } catch (Exception e) {
            Log.e(NetStateManager.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(NetStateManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    static String parseRawIP(String raw) {
        if (!TextUtils.isEmpty(raw)) {
            String port = null;
            String ip = null;

            int idx = raw.lastIndexOf(':');

            if (idx != -1) {
                port = raw.substring(idx + 1).trim();
                ip = raw.substring(0, idx).trim();

                try {
                    int pt = Integer.parseInt(port, 16);

                    if (pt == 0) {
                        port = "*"; //$NON-NLS-1$
                    } else {
                        port = String.valueOf(pt);
                    }
                } catch (Exception e) {
                    port = "?"; //$NON-NLS-1$

                    Log.e(NetStateManager.class.getName(), "Parsing raw port fail : " + raw); //$NON-NLS-1$
                }
            } else {
                ip = raw.trim();
            }

            if (ip.length() == 8) {
                try {
                    int n1 = Integer.parseInt(ip.substring(6), 16);
                    int n2 = Integer.parseInt(ip.substring(4, 6), 16);
                    int n3 = Integer.parseInt(ip.substring(2, 4), 16);
                    int n4 = Integer.parseInt(ip.substring(0, 2), 16);

                    ip = n1 + "." + n2 + "." + n3 + "." + n4; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (Exception e) {
                    ip = "?"; //$NON-NLS-1$

                    Log.e(NetStateManager.class.getName(), "Parsing raw ip4 fail : " + raw); //$NON-NLS-1$
                }
            } else if (ip.length() == 32) {
                try {
                    int n1 = Integer.parseInt(ip.substring(30), 16);
                    int n2 = Integer.parseInt(ip.substring(28, 30), 16);
                    int n3 = Integer.parseInt(ip.substring(26, 28), 16);
                    int n4 = Integer.parseInt(ip.substring(24, 26), 16);

                    ip = n1 + "." + n2 + "." + n3 + "." + n4; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (Exception e) {
                    ip = "?"; //$NON-NLS-1$

                    Log.e(NetStateManager.class.getName(), "Parsing raw ip6 fail : " + raw); //$NON-NLS-1$
                }
            } else {
                Log.e(NetStateManager.class.getName(), "Parsing raw ip fail : " + raw); //$NON-NLS-1$
            }

            if (port == null) {
                return ip;
            } else {
                return ip + ':' + port;
            }
        }

        return raw;
    }

    static void showIpInfo(final IpInfo info, final Context ctx) {
        if (info != null && !TextUtils.isEmpty(info.latitude) && !TextUtils.isEmpty(info.longitude)) {

            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    Intent it = new Intent(Intent.ACTION_VIEW);

                    it.setData(Uri.parse("geo:0,0?q=" //$NON-NLS-1$
                            + info.latitude + "," //$NON-NLS-1$
                            + info.longitude + "&z=8")); //$NON-NLS-1$

                    it = Intent.createChooser(it, null);

                    Util.safeStartActivity(ctx, it, false);
                }
            };

            TextView txt = new TextView(ctx);
            txt.setPadding(15, 0, 15, 0);
            txt.setTextAppearance(ctx, android.R.style.TextAppearance_Medium);

            txt.setText(Html.fromHtml(ResUtil.getString(ctx, R.string.location_info, info.ip,
                    info.host == null ? "" : ("<a href=\"http://" //$NON-NLS-1$ //$NON-NLS-2$
                            + info.host + "\">" //$NON-NLS-1$
                            + info.host + "</a><br>"), //$NON-NLS-1$
                    info.country == null ? "" : info.country, //$NON-NLS-1$
                    info.region == null ? "" : info.region, //$NON-NLS-1$
                    info.city == null ? "" : info.city))); //$NON-NLS-1$
            txt.setMovementMethod(LinkMovementMethod.getInstance());

            Util.newAlertDialogBuilder(ctx).setTitle(ResUtil.getString(ctx, R.string.ip_location))
                    .setPositiveButton(ResUtil.getString(ctx, R.string.view_map), listener)
                    .setNegativeButton(ResUtil.getString(ctx, R.string.close), null).setView(txt).create()
                    .show();
        } else {
            Util.shortToast(ctx, R.string.no_ip_info);
        }
    }

    static IpInfo getIpInfoDirect(IpInfo info) {
        if (info == null) {
            info = new IpInfo();
        }

        InputStream input = null;

        try {
            URL url;

            if (info.ip == null) {
                url =
                        new URL(
                                "http://api.ipinfodb.com/v3/ip-city/?key=a1d74831f68f12aa61307b387b0d17cf2501d9c368172a9c73ad120f149c73d4&format=xml"); //$NON-NLS-1$
            } else {
                url =
                        new URL(
                                "http://api.ipinfodb.com/v3/ip-city/?key=a1d74831f68f12aa61307b387b0d17cf2501d9c368172a9c73ad120f149c73d4&format=xml&ip=" //$NON-NLS-1$
                                        + info.ip);
            }

            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();

            input = url.openStream();

            parser.setInput(input, null);

            String name, value;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    name = parser.getName();

                    if (info.ip == null && "ipAddress".equals(name)) //$NON-NLS-1$
                    {
                        info.ip = parser.nextText();
                    } else if ("statusCode".equals(name)) //$NON-NLS-1$
                    {
                        value = parser.nextText();

                        if (!"OK".equals(value)) //$NON-NLS-1$
                        {
                            Log.d(NetStateManager.class.getName(), "Status returned: [" //$NON-NLS-1$
                                    + value + "] for ip: " //$NON-NLS-1$
                                    + info.ip);

                            break;
                        }
                    } else if ("countryName".equals(name)) //$NON-NLS-1$
                    {
                        value = parser.nextText();

                        if ("Reserved".equals(value)) //$NON-NLS-1$
                        {
                            Log.d(NetStateManager.class.getName(), "Reserved ip?: " + info.ip); //$NON-NLS-1$

                            break;
                        }

                        info.country = value;
                    } else if ("regionName".equals(name)) //$NON-NLS-1$
                    {
                        info.region = parser.nextText();
                    } else if ("cityName".equals(name)) //$NON-NLS-1$
                    {
                        info.city = parser.nextText();
                    } else if ("latitude".equals(name)) //$NON-NLS-1$
                    {
                        info.latitude = parser.nextText();
                    } else if ("longitude".equals(name)) //$NON-NLS-1$
                    {
                        info.longitude = parser.nextText();
                    }
                }
            }

            if (info.ip != null) {
                String host = InetAddress.getByName(info.ip).getHostName();

                if (!info.ip.equals(host)) {
                    info.host = host;
                }
            }
        } catch (Exception e) {
            Log.e(NetStateManager.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(NetStateManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        return info;
    }

    /**
     * NetStateSettings
     */
    public static class NetStateSettings extends PreferenceActivity {

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

            Preference perfRemote = new Preference(this);
            perfRemote.setKey(PREF_KEY_REMOTE_QUERY);
            perfRemote.setTitle(ResUtil.getString(this, R.string.remote_query));
            pc.addPreference(perfRemote);

            CheckBoxPreference perfRemoteName = new CheckBoxPreference(this);
            perfRemoteName.setKey(PREF_KEY_SHOW_REMOTE_NAME);
            perfRemoteName.setTitle(ResUtil.getString(this, R.string.display_remote_name));
            perfRemoteName.setSummary(ResUtil.getString(this, R.string.show_remote_msg));
            pc.addPreference(perfRemoteName);

            CheckBoxPreference perfShowLocal = new CheckBoxPreference(this);
            perfShowLocal.setKey(PREF_KEY_SHOW_LOCAL_ADDRESS);
            perfShowLocal.setTitle(ResUtil.getString(this, R.string.show_local_addr));
            perfShowLocal.setSummary(ResUtil.getString(this, R.string.show_local_addr_sum));
            pc.addPreference(perfShowLocal);

            CheckBoxPreference perfShowSource = new CheckBoxPreference(this);
            perfShowSource.setKey(PREF_KEY_SHOW_CONNECTION_SOURCE);
            perfShowSource.setTitle(ResUtil.getString(this, R.string.show_conn_src));
            perfShowSource.setSummary(ResUtil.getString(this, R.string.show_conn_src_sum));
            pc.addPreference(perfShowSource);

            CheckBoxPreference perfShowTraffic = new CheckBoxPreference(this);
            perfShowTraffic.setKey(PREF_KEY_SHOW_SOURCE_TRAFFIC);
            perfShowTraffic.setTitle(ResUtil.getString(this, R.string.show_source_traffic));
            perfShowTraffic.setSummary(ResUtil.getString(this, R.string.show_source_traffic_sum));
            pc.addPreference(perfShowTraffic);

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

            refreshInterval();
            refreshRemoteQuery();
            refreshBooleanOption(PREF_KEY_SHOW_REMOTE_NAME);
            refreshBooleanOption(PREF_KEY_SHOW_LOCAL_ADDRESS);
            refreshBooleanOption(PREF_KEY_SHOW_CONNECTION_SOURCE);
            refreshSortType(PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_SOURCE);
            refreshSortDirection(PREF_KEY_SORT_DIRECTION);
            refreshSortType(PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_PROTO);
            refreshSortDirection(PREF_KEY_SECONDARY_SORT_DIRECTION);
            refreshTraffic();

            setResult(RESULT_OK, getIntent());
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }

        void refreshTraffic() {
            boolean allowed =
                    getIntent().getBooleanExtra(PREF_KEY_SHOW_CONNECTION_SOURCE, true)
                            && getIntent().getIntExtra(PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_SOURCE) == ORDER_TYPE_SOURCE;

            CheckBoxPreference cbp = (CheckBoxPreference) findPreference(PREF_KEY_SHOW_SOURCE_TRAFFIC);

            if (allowed) {
                refreshBooleanOption(PREF_KEY_SHOW_SOURCE_TRAFFIC);
            } else {
                cbp.setChecked(false);
            }

            cbp.setEnabled(allowed);
        }

        void refreshInterval() {
            int interval = getIntent().getIntExtra(PREF_KEY_REFRESH_INTERVAL, REFRESH_NORMAL);

            int labelId = R.string.normal;
            switch (interval) {
                case REFRESH_HIGH:
                    labelId = R.string.high;
                    break;
                case REFRESH_LOW:
                    labelId = R.string.low;
                    break;
                case REFRESH_PAUSED:
                    labelId = R.string.paused;
                    break;
            }

            findPreference(PREF_KEY_REFRESH_INTERVAL).setSummary(ResUtil.getString(this, labelId));
        }

        void refreshRemoteQuery() {
            int state = getIntent().getIntExtra(PREF_KEY_REMOTE_QUERY, ENABLED);

            CharSequence label = ResUtil.getString(this, R.string.wifi_only);
            switch (state) {
                case DISABLED:
                    label = ResUtil.getString(this, R.string.disabled);
                    break;
                case ENABLED:
                    label = ResUtil.getString(this, R.string.enabled);
                    break;
            }

            findPreference(PREF_KEY_REMOTE_QUERY).setSummary(label);
        }

        void refreshBooleanOption(String key) {
            boolean val = getIntent().getBooleanExtra(key, true);

            ((CheckBoxPreference) findPreference(key)).setChecked(val);
        }

        void refreshSortType(String key, int defaultType) {
            int type = getIntent().getIntExtra(key, defaultType);

            String label = null;
            switch (type) {
                case ORDER_TYPE_PROTO:
                    label = ResUtil.getString(this, R.string.protocol);
                    break;
                case ORDER_TYPE_LOCAL:
                    label = ResUtil.getString(this, R.string.local_address);
                    break;
                case ORDER_TYPE_REMOTE:
                    label = ResUtil.getString(this, R.string.remote_address);
                    break;
                case ORDER_TYPE_STATE:
                    label = ResUtil.getString(this, R.string.state);
                    break;
                case ORDER_TYPE_SOURCE:
                    label = ResUtil.getString(this, R.string.source);
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
            } else if (PREF_KEY_REMOTE_QUERY.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_REMOTE_QUERY, which);

                        dialog.dismiss();

                        refreshRemoteQuery();
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.remote_query))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.enabled),
                                        ResUtil.getString(this, R.string.disabled),
                                        ResUtil.getString(this, R.string.wifi_only),},
                                it.getIntExtra(PREF_KEY_REMOTE_QUERY, ENABLED), listener).create().show();

                return true;
            } else if (PREF_KEY_SHOW_REMOTE_NAME.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_REMOTE_NAME,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_REMOTE_NAME)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_LOCAL_ADDRESS.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_LOCAL_ADDRESS,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_LOCAL_ADDRESS)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_CONNECTION_SOURCE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_CONNECTION_SOURCE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_CONNECTION_SOURCE)).isChecked());

                refreshTraffic();

                return true;
            } else if (PREF_KEY_SHOW_SOURCE_TRAFFIC.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_SOURCE_TRAFFIC,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_SOURCE_TRAFFIC)).isChecked());

                return true;
            } else if (PREF_KEY_SORT_ORDER_TYPE.equals(prefKey)
                    || PREF_KEY_SECONDARY_SORT_ORDER_TYPE.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(prefKey, which);

                        dialog.dismiss();

                        if (PREF_KEY_SORT_ORDER_TYPE.equals(prefKey)) {
                            refreshSortType(prefKey, ORDER_TYPE_SOURCE);
                            refreshTraffic();
                        } else {
                            refreshSortType(prefKey, ORDER_TYPE_PROTO);
                        }
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.sort_type))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.protocol),
                                        ResUtil.getString(this, R.string.local_address),
                                        ResUtil.getString(this, R.string.remote_address),
                                        ResUtil.getString(this, R.string.state),
                                        ResUtil.getString(this, R.string.source),},
                                it.getIntExtra(prefKey, ORDER_TYPE_PROTO), listener).create().show();

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
            }

            return false;
        }
    }

    /**
     * ConnectionViewHolder
     */
    private static final class ConnectionViewHolder {

        TextView txt_proto, txt_ip, txt_state, txtTraffic, txtProc, txtSrc;
        View procView, procView2;

        ConnectionViewHolder(View view) {
            txt_proto = (TextView) view.findViewById(R.id.txt_proto);
            txt_ip = (TextView) view.findViewById(R.id.txt_ip);
            txt_state = (TextView) view.findViewById(R.id.txt_state);
            procView = view.findViewById(R.id.ll_proc);
            txtProc = (TextView) procView.findViewById(R.id.txt_proc_name);
            txtTraffic = (TextView) procView.findViewById(R.id.txt_traffic);
            procView2 = view.findViewById(R.id.ll_proc2);
            txtSrc = (TextView) procView2.findViewById(R.id.txt_src_name);
        }
    }

    /**
     * ConnectionItem
     */
    private static final class ConnectionItem {

        String proto;
        String local;
        String remote;
        String remoteName;
        String state;
        int uid = -1;

        ConnectionItem() {

        }
    }

    /**
     * ConnectionComparator
     */
    private static final class ConnectionComparator implements Comparator<ConnectionItem> {

        int type, direction, secondType, secondDirection;

        Collator clt = Collator.getInstance();

        ConnectionComparator(int type, int direction, int secondType, int secondDirection) {
            this.type = type;
            this.direction = direction;
            this.secondType = secondType;
            this.secondDirection = secondDirection;
        }

        public int compare(ConnectionItem obj1, ConnectionItem obj2) {
            int rlt = compare(clt, type, direction, obj1, obj2);

            if (rlt == 0) {
                rlt = compare(clt, secondType, secondDirection, obj1, obj2);
            }

            return rlt;
        }

        private static int compare(Collator clt, int type, int direction, ConnectionItem obj1,
                                   ConnectionItem obj2) {
            switch (type) {
                case ORDER_TYPE_PROTO:

                    return clt.compare(obj1.proto, obj2.proto) * direction;

                case ORDER_TYPE_LOCAL:

                    return clt.compare(obj1.local, obj2.local) * direction;

                case ORDER_TYPE_REMOTE:

                    return clt.compare(obj1.remoteName == null ? obj1.remote : obj1.remoteName,
                            obj2.remoteName == null ? obj2.remote : obj2.remoteName) * direction;

                case ORDER_TYPE_STATE:

                    return clt.compare(obj1.state == null ? "" //$NON-NLS-1$
                            : obj1.state, obj2.state == null ? "" //$NON-NLS-1$
                            : obj2.state) * direction;

                case ORDER_TYPE_SOURCE:

                    // bigger id first
                    return (obj2.uid - obj1.uid) * direction;
            }

            return 0;
        }
    }

    /**
     * IpInfo
     */
    static final class IpInfo {

        String country, region, city;
        String latitude, longitude;
        String ip, host;
    }
}
