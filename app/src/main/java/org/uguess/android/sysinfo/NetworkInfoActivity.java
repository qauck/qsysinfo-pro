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

import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.uguess.android.sysinfo.NetStateManager.IpInfo;
import org.uguess.android.sysinfo.PropertiesViewer.SafeTelephonyManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * NetworkInfoActivity
 */
public final class NetworkInfoActivity extends PopActivity {

    private static final char[] RF_CHARS = new char[]{'U', 'G', 'H', 'R', 'D', 'M'};

    private static final int[] RF_VALS = new int[]{0x01, 0x02, 0x04, 0x08, 0x10, 0x20};

    private static final String[] IFF_CHARS = new String[]{"up", //$NON-NLS-1$
            "broadcast", //$NON-NLS-1$
            "debug", //$NON-NLS-1$
            "loopback", //$NON-NLS-1$
            "point-to-point", //$NON-NLS-1$
            "running", //$NON-NLS-1$
            "noarp", //$NON-NLS-1$
            "multicast" //$NON-NLS-1$
    };

    private static final int[] IFF_VALS = new int[]{

            0x1, 0x2, 0x4, 0x8, 0x10, 0x40, 0x80, 0x1000};

    private static final String PUBLIC_ADDRESS = "public.address"; //$NON-NLS-1$

    private static final Method mtdGetAllCellInfo = Util.getMethod(TelephonyManager.class,
            "getAllCellInfo"); //$NON-NLS-1$

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ListView contentView = (ListView) findViewById(R.id.content_list);

        if (Util.SDK_VER < 11) {
            registerForContextMenu(contentView);
        }

        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(this, R.layout.sensor_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.sensor_item, contentView, false);

                final String[] item = getItem(position);

                TextView t1 = (TextView) v.findViewById(R.id.txt_head);
                TextView t2 = (TextView) v.findViewById(R.id.txt_msg);

                t1.setText(item[0]);
                t2.setText(item[1]);

                if (Util.SDK_VER >= 11) {
                    Util.setTextIsSelectable(t1, true);
                    Util.setTextIsSelectable(t2, true);
                }

                // update public address section
                if (PUBLIC_ADDRESS.equals(item[1])) {
                    t2.setVisibility(View.GONE);

                    LinearLayout cv =
                            (LinearLayout) getLayoutInflater()
                                    .inflate(R.layout.pub_info, (LinearLayout) v, false);
                    cv.setPadding(0, 0, 0, 0);

                    Button btn = (Button) cv.findViewById(R.id.btn_action);

                    if (NetStateManager.isProxiedNetwork(NetworkInfoActivity.this)) {
                        btn.setText(ResUtil.getString(NetworkInfoActivity.this, R.string.unavail));
                        btn.setEnabled(false);
                    } else {
                        btn.setText(ResUtil.getString(NetworkInfoActivity.this, R.string.check));
                        btn.setOnClickListener(new OnClickListener() {

                            public void onClick(View v) {
                                eventConsumed = true;

                                final ProgressDialog progress = new ProgressDialog(NetworkInfoActivity.this);
                                progress.setMessage(ResUtil
                                        .getString(NetworkInfoActivity.this, R.string.query_ip_msg));
                                progress.setIndeterminate(true);
                                progress.show();

                                new Thread(new Runnable() {

                                    public void run() {
                                        final IpInfo info = NetStateManager.getIpInfoDirect(null);

                                        contentView.post(new Runnable() {

                                            public void run() {
                                                Util.safeDismissDialog(progress);

                                                NetStateManager.showIpInfo(info, NetworkInfoActivity.this);

                                                if (info != null && !TextUtils.isEmpty(info.latitude)
                                                        && !TextUtils.isEmpty(info.longitude)) {
                                                    if (info.host == null) {
                                                        item[1] = info.ip;
                                                    } else {
                                                        item[1] = info.ip + '\n' + info.host;
                                                    }
                                                } else {
                                                    item[1] =
                                                            ResUtil
                                                                    .getString(NetworkInfoActivity.this, R.string.info_not_available);
                                                }

                                                ((ArrayAdapter<String[]>) contentView.getAdapter()).notifyDataSetChanged();
                                            }
                                        });
                                    }
                                }, "IpInfoRequester").start(); //$NON-NLS-1$
                            }
                        });
                    }

                    ((LinearLayout) v).addView(cv);
                }

                return v;
            }
        };

        contentView.setAdapter(adapter);

        refresh();
    }

    private void refresh() {
        ArrayList<String[]> data = new ArrayList<String[]>();

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo ani = cm.getActiveNetworkInfo();

        StringBuilder sb = new StringBuilder();

        formatNetworkInfo(this, sb, ani);

        data.add(new String[]{ResUtil.getString(this, R.string.active_network),
                sb.length() == 0 ? ResUtil.getString(this, R.string.unknown) : sb.toString()});

        sb.delete(0, sb.length());

        if (ani != null && ani.isConnected() && ani.getType() == ConnectivityManager.TYPE_WIFI) {
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            WifiInfo wi = wm.getConnectionInfo();

            if (wi != null) {
                sb.append("SSID: ").append(wi.getSSID()).append('\n'); //$NON-NLS-1$
                sb.append("BSSID: ").append(wi.getBSSID()).append('\n'); //$NON-NLS-1$
                sb.append(ResUtil.getString(this, R.string.hidden_ssid)).append(": ") //$NON-NLS-1$
                        .append(wi.getHiddenSSID()).append('\n');
                sb.append(ResUtil.getString(this, R.string.network_id)).append(": ") //$NON-NLS-1$
                        .append(wi.getNetworkId()).append('\n');
                sb.append(ResUtil.getString(this, R.string.mac_addr)).append(": ") //$NON-NLS-1$
                        .append(wi.getMacAddress()).append('\n');
                sb.append(ResUtil.getString(this, R.string.link_speed)).append(": ") //$NON-NLS-1$
                        .append(wi.getLinkSpeed()).append(WifiInfo.LINK_SPEED_UNITS).append('\n');
                sb.append(ResUtil.getString(this, R.string.sig_strength)).append(": ") //$NON-NLS-1$
                        .append(wi.getRssi()).append('\n');
            }

            DhcpInfo di = wm.getDhcpInfo();

            if (di != null) {
                sb.append(ResUtil.getString(this, R.string.dhcp_srv));
                putAddress(sb, di.serverAddress);
                sb.append(ResUtil.getString(this, R.string.gateway));
                putAddress(sb, di.gateway);
                sb.append(ResUtil.getString(this, R.string.ip_addr));
                putAddress(sb, di.ipAddress);
                sb.append(ResUtil.getString(this, R.string.netmask));
                putAddress(sb, di.netmask);
                sb.append("DNS 1"); //$NON-NLS-1$
                putAddress(sb, di.dns1);
                sb.append("DNS 2"); //$NON-NLS-1$
                putAddress(sb, di.dns2);
                sb.append(ResUtil.getString(this, R.string.lease_duration)).append(": ") //$NON-NLS-1$
                        .append(di.leaseDuration).append(' ').append(ResUtil.getString(this, R.string.seconds));
            }

            data.add(new String[]{ResUtil.getString(this, R.string.wifi_state),
                    sb.length() == 0 ? ResUtil.getString(this, R.string.unknown) : sb.toString()});

            sb.delete(0, sb.length());
        }

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (tm != null) {

            CellLocation cl = new SafeTelephonyManager(tm).getCellLocation();

            if (cl != null) {
                sb.append(cl.getClass().getSimpleName()).append(' ').append(cl).append("\n\n"); //$NON-NLS-1$
            }

            if (mtdGetAllCellInfo != null) {
                try {
                    Object obj = mtdGetAllCellInfo.invoke(tm);

                    if (obj instanceof List) {
                        @SuppressWarnings("rawtypes")
                        List cells = (List) obj;
                        for (Object ci : cells) {
                            sb.append(ci).append("\n\n"); //$NON-NLS-1$
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }

            if (sb.length() > 1) {
                sb.delete(sb.length() - 2, sb.length());

                data.add(new String[]{ResUtil.getString(this, R.string.cellular_state), sb.toString()});

                sb.delete(0, sb.length());
            }
        }

        String localAddress = Util.getNetAddressInfo();

        data.add(new String[]{ResUtil.getString(this, R.string.local_address),
                localAddress == null ? ResUtil.getString(this, R.string.unknown) : localAddress});

        data.add(new String[]{ResUtil.getString(this, R.string.public_address), PUBLIC_ADDRESS});

        ArrayList<String[]> routes = parseRoute(this);

        if (routes != null) {
            sb.append(ResUtil.getString(this, R.string.route_header));

            for (String[] rt : routes) {
                sb.append("\n\n") //$NON-NLS-1$
                        .append(rt[0]).append("  ") //$NON-NLS-1$
                        .append(rt[1]).append("  ") //$NON-NLS-1$
                        .append(rt[2]).append("  ") //$NON-NLS-1$
                        .append(rt[3]).append("  ") //$NON-NLS-1$
                        .append(rt[4]).append("  ") //$NON-NLS-1$
                        .append(rt[5]);
            }

            data.add(new String[]{ResUtil.getString(this, R.string.route), sb.toString()});

            sb.delete(0, sb.length());
        }

        try {
            Enumeration<NetworkInterface> itfs = NetworkInterface.getNetworkInterfaces();

            if (itfs != null) {
                boolean first = true;

                while (itfs.hasMoreElements()) {
                    NetworkInterface intf = itfs.nextElement();

                    if (first) {
                        first = false;
                    } else {
                        sb.append("\n\n"); //$NON-NLS-1$
                    }

                    formatNetworkInterface(this, sb, intf);
                }

                data.add(new String[]{ResUtil.getString(this, R.string.intfs),
                        sb.length() == 0 ? ResUtil.getString(this, R.string.unknown) : sb.toString()});

                sb.delete(0, sb.length());
            }
        } catch (SocketException e) {
            Log.e(NetworkInfoActivity.class.getName(), e.getLocalizedMessage(), e);
        }

        NetworkInfo[] allNs = cm.getAllNetworkInfo();

        if (allNs != null) {
            boolean first = true;

            for (NetworkInfo ni : allNs) {
                if (ani != null && ani.getType() == ni.getType()) {
                    continue;
                }

                if (first) {
                    first = false;
                } else {
                    sb.append("\n\n"); //$NON-NLS-1$
                }

                formatNetworkInfo(this, sb, ni);
            }

            data.add(new String[]{ResUtil.getString(this, R.string.other_network),
                    sb.length() == 0 ? ResUtil.getString(this, R.string.unknown) : sb.toString()});

            sb.delete(0, sb.length());
        }

        ListView contentView = (ListView) findViewById(R.id.content_list);

        @SuppressWarnings("unchecked")
        ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter();

        adapter.setNotifyOnChange(false);

        adapter.clear();

        for (String[] d : data) {
            adapter.add(d);
        }

        adapter.notifyDataSetChanged();
    }

    private static ArrayList<String[]> parseRoute(Context ctx) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/net/route")), //$NON-NLS-1$
                    2048);

            boolean first = true;
            String line;

            ArrayList<String[]> routes = new ArrayList<String[]>();

            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }

                line = line.trim();

                String[] vals = line.split("\\s+"); //$NON-NLS-1$

                if (vals != null && vals.length > 7) {
                    String[] ent = new String[6];

                    String flags = parseRouteFlags(vals[3]);

                    if (flags != null) {
                        // desination
                        ent[0] = NetStateManager.parseRawIP(vals[1]);
                        // gateway
                        ent[1] = NetStateManager.parseRawIP(vals[2]);
                        // mask
                        ent[2] = NetStateManager.parseRawIP(vals[7]);
                        // flags
                        ent[3] = flags;
                        // metric
                        ent[4] = vals[6];
                        // iface
                        ent[5] = vals[0];

                        if ("0.0.0.0".equals(ent[0])) //$NON-NLS-1$
                        {
                            ent[0] = ResUtil.getString(ctx, R.string.default_);
                        }

                        if ("0.0.0.0".equals(ent[1])) //$NON-NLS-1$
                        {
                            ent[1] = "*"; //$NON-NLS-1$
                        }

                        routes.add(ent);
                    }
                }
            }

            return routes.size() == 0 ? null : routes;
        } catch (FileNotFoundException fe) {
            Log.d(NetworkInfoActivity.class.getName(), "File not found: " + fe.getLocalizedMessage()); //$NON-NLS-1$
        } catch (Exception e) {
            Log.e(NetworkInfoActivity.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(NetworkInfoActivity.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        return null;
    }

    private static String parseRouteFlags(String flags) {
        if (flags != null) {
            try {
                int f = Integer.parseInt(flags.trim(), 16);

                if ((f & RF_VALS[0]) != 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(RF_CHARS[0]);

                    for (int i = 1; i < RF_VALS.length; i++) {
                        if ((f & RF_VALS[i]) != 0) {
                            sb.append(RF_CHARS[i]);
                        }
                    }

                    return sb.toString();
                }
            } catch (Exception e) {
                Log.e(NetworkInfoActivity.class.getName(), "Cannot parse route flags: " + flags, //$NON-NLS-1$
                        e);
            }
        }

        return null;
    }

    private static void putAddress(StringBuilder buf, int addr) {
        buf.append(": ") //$NON-NLS-1$
                .append(addr & 0xff).append('.').append((addr >>>= 8) & 0xff).append('.')
                .append((addr >>>= 8) & 0xff).append('.').append((addr >>>= 8) & 0xff).append('\n');
    }

    private static void formatNetworkInterface(Context ctx, StringBuilder sb, NetworkInterface ni) {
        if (ni != null) {
            String ifname = ni.getName();

            sb.append(ni.getDisplayName()).append('\n');

            Enumeration<InetAddress> addrs = ni.getInetAddresses();

            if (addrs != null) {
                sb.append(ResUtil.getString(ctx, R.string.ip_addr)).append(':');

                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();

                    sb.append(' ').append(addr.getHostAddress());
                }

                sb.append('\n');
            }

            String mac = Util.getIfAddress(ifname);
            sb.append(ResUtil.getString(ctx, R.string.mac_addr)).append(": ") //$NON-NLS-1$
                    .append(mac == null ? "" : mac) //$NON-NLS-1$
                    .append('\n');

            String flags = Util.getIfFlags(ifname);
            sb.append(ResUtil.getString(ctx, R.string.flags)).append(": "); //$NON-NLS-1$
            formatInterfaceFlags(flags, sb);
            sb.append('\n');

            long tx = Util.getTxBytes(ifname);
            sb.append("Tx: ") //$NON-NLS-1$
                    .append(Util.safeFormatFileSize(ctx, tx)).append('\n');

            long rx = Util.getRxBytes(ifname);
            sb.append("Rx: ").append(Util.safeFormatFileSize(ctx, rx)); //$NON-NLS-1$
        }
    }

    private static void formatInterfaceFlags(String flags, StringBuilder sb) {
        if (flags != null) {
            try {
                if (flags.startsWith("0x") || flags.startsWith("0X")) //$NON-NLS-1$ //$NON-NLS-2$
                {
                    flags = flags.substring(2);
                }

                int f = Integer.parseInt(flags, 16);

                if ((f & IFF_VALS[0]) != 0) {
                    sb.append("up"); //$NON-NLS-1$
                } else {
                    sb.append("down"); //$NON-NLS-1$
                }

                for (int i = 1; i < IFF_VALS.length; i++) {
                    if ((f & IFF_VALS[i]) != 0) {
                        sb.append(' ').append(IFF_CHARS[i]);
                    }
                }
            } catch (Exception e) {
                Log.e(NetworkInfoActivity.class.getName(), "Cannot parse interface flags: " + flags, //$NON-NLS-1$
                        e);
            }
        }
    }

    private static void formatNetworkInfo(Context ctx, StringBuilder sb, NetworkInfo ni) {
        if (ni != null) {
            String type = ni.getTypeName();
            String subType = ni.getSubtypeName();
            String extra = ni.getExtraInfo();

            sb.append(TextUtils.isEmpty(subType) ? type : type + " (" //$NON-NLS-1$
                    + subType + ')');

            if (!TextUtils.isEmpty(extra)) {
                sb.append('\n').append(extra);
            }

            sb.append('\n').append(
                    ResUtil.getString(ctx, ni.isAvailable() ? R.string.avail : R.string.unavail));

            sb.append(", ") //$NON-NLS-1$
                    .append(
                            ResUtil.getString(ctx, ni.isConnected() ? R.string.connected : R.string.disconnected));

            sb.append('\n').append(
                    ResUtil.getString(ctx, ni.isRoaming() ? R.string.roaming : R.string.not_roaming));
        }
    }

}
